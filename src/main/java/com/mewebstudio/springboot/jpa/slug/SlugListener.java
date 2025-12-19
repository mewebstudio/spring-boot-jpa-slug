package com.mewebstudio.springboot.jpa.slug;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * JPA entity listener for generating and updating slugs on entities
 * that implement the {@link ISlugSupport} interface.
 * <p>
 * Uses the field annotated with {@link SlugField} as the source for slug generation.
 * Slug will only be updated if the source field has changed or is initially empty.
 */
public class SlugListener {
    /**
     * The entity manager used for database operations.
     * This is injected by the Spring container.
     */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Default constructor for SlugListener.
     */
    public SlugListener() {
    }

    /**
     * JPA callback method invoked before persist or update operations.
     * Handles slug generation and assignment for entities that support slugs.
     *
     * @param entity The entity object being persisted or updated.
     */
    @PrePersist
    @PreUpdate
    public void handle(Object entity) {
        if (!(entity instanceof ISlugSupport<?> slugEntity)) {
            return;
        }

        try {
            String sourceValue = findSlugFieldValue(entity);
            if (sourceValue == null || sourceValue.isBlank()) {
                return;
            }

            if (slugEntity.getSlug() != null && !isSlugSourceChanged(entity, sourceValue)) {
                return;
            }

            String slug = SlugUtil.generate(sourceValue);
            if (slug == null || slug.isBlank()) {
                throw new SlugOperationException("Generated base slug is null or blank for value: " + sourceValue);
            }

            ISlugProvider provider = SlugRegistry.getSlugProvider();
            if (provider == null) {
                throw new SlugOperationException("No ISlugProvider registered in SlugRegistry.");
            }

            // Extract composite unique constraint fields (e.g., locale for locale+slug uniqueness)
            Map<String, Object> constraintFields = getCompositeUniqueConstraintFields(entity);

            String generatedSlug = provider.generateSlug(entity, slug, constraintFields);
            if (generatedSlug == null || generatedSlug.isBlank()) {
                throw new SlugOperationException("Generated slug is blank for base: " + slug);
            }

            slugEntity.setSlug(generatedSlug);
        } catch (Exception e) {
            throw new SlugOperationException("SlugListener failed: " + e.getMessage(), e);
        }
    }

    /**
     * Checks whether the slug source field value has changed compared to the original persisted entity.
     *
     * @param entity         The current entity instance.
     * @param newSourceValue The current value of the slug source field.
     * @return true if the source value has changed, false otherwise.
     */
    private boolean isSlugSourceChanged(Object entity, String newSourceValue) {
        try {
            Object originalEntity = entityManager.find(entity.getClass(), ((ISlugSupport<?>) entity).getId());
            for (Field field : originalEntity.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(SlugField.class)) {
                    field.setAccessible(true);
                    Object originalValue = field.get(originalEntity);
                    return !newSourceValue.equals(originalValue);
                }
            }
            return true;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Finds the value of the field annotated with {@link SlugField} on the given entity.
     *
     * @param entity The entity to inspect.
     * @return The string value of the slug source field, or null if not found or empty.
     */
    private String findSlugFieldValue(Object entity) {
        for (Field field : entity.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(SlugField.class)) {
                field.setAccessible(true);
                try {
                    Object value = field.get(entity);
                    if (value instanceof String s && !s.isBlank()) {
                        return s;
                    }
                } catch (IllegalAccessException e) {
                    throw new SlugOperationException("Unable to access @SlugField: " + field.getName(), e);
                }
            }
        }

        return null;
    }

    /**
     * Extracts composite unique constraint field values from the entity.
     * For example, if there's a unique constraint on (locale, slug), this will return {"locale": "en-US"}.
     *
     * @param entity The entity to inspect.
     * @return Map of field names to their current values that are part of composite unique constraints with slug.
     */
    private Map<String, Object> getCompositeUniqueConstraintFields(Object entity) {
        Map<String, Object> result = new HashMap<>();

        try {
            Table tableAnnotation = entity.getClass().getAnnotation(Table.class);

            if (tableAnnotation != null) {
                // Find unique constraints that include "slug"
                for (var constraint : tableAnnotation.uniqueConstraints()) {
                    String[] columnNames = constraint.columnNames();

                    if (containsSlug(columnNames) && columnNames.length > 1) {
                        // This is a composite constraint with slug, extract other field values
                        for (String columnName : columnNames) {
                            if (!"slug".equals(columnName)) {
                                // Find the field with this column name
                                Object fieldValue = findFieldValueByColumnName(entity, columnName);
                                if (fieldValue != null) {
                                    result.put(columnName, fieldValue);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // If we can't determine composite constraints, return an empty map (fallback to old behavior)
        }

        return result;
    }

    /**
     * Checks if the array contains a "slug" column.
     *
     * @param columnNames Array of column names
     * @return true if contains "slug", false otherwise
     */
    private boolean containsSlug(String[] columnNames) {
        for (String name : columnNames) {
            if ("slug".equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds a field value by its column name from @Column annotation.
     *
     * @param entity     The entity to inspect.
     * @param columnName The database column name.
     * @return The field value, or null if not found.
     */
    private Object findFieldValueByColumnName(Object entity, String columnName) {
        for (Field field : entity.getClass().getDeclaredFields()) {
            field.setAccessible(true);

            // Check @Column annotation
            Column columnAnnotation = field.getAnnotation(Column.class);
            if (columnAnnotation != null && columnName.equals(columnAnnotation.name())) {
                try {
                    return field.get(entity);
                } catch (Exception e) {
                    return null;
                }
            }

            // Fallback: if field name matches column name (snake_case vs. camelCase)
            if (field.getName().equalsIgnoreCase(columnName) || toSnakeCase(field.getName()).equals(columnName)) {
                try {
                    return field.get(entity);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Converts camelCase to snake_case.
     *
     * @param str The camelCase string
     * @return The snake_case string
     */
    private String toSnakeCase(String str) {
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}

