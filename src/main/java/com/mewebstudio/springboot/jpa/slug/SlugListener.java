package com.mewebstudio.springboot.jpa.slug;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA entity listener for generating and updating slugs on entities
 * that implement the {@link ISlugSupport} interface.
 *
 * <p>Supports three slug source modes (evaluated in priority order):
 * <ol>
 *   <li>Class-level {@code @SlugField(fields = {"f1", "f2"})} — joins multiple field values
 *       (dot-notation supported for related entity fields).</li>
 *   <li>Field-level {@code @SlugField} — backward-compatible single-field mode.</li>
 * </ol>
 *
 * <p>Slug is regenerated only when the slug source value changes.
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

            Map<String, Object> constraintFields = getCompositeUniqueConstraintFields(entity);
            String generatedSlug = SlugRegistry.getSlugProvider().generateSlug(entity, slug, constraintFields);
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
            String originalValue = findSlugFieldValue(originalEntity);
            return !newSourceValue.equals(originalValue);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Extracts field values for composite unique constraints that include the {@code slug} column.
     *
     * @param entity The entity to inspect.
     * @return A map of column name to field value for each non-slug column in a composite unique constraint.
     */
    private Map<String, Object> getCompositeUniqueConstraintFields(Object entity) {
        Map<String, Object> result = new HashMap<>();

        try {
            Table tableAnnotation = entity.getClass().getAnnotation(Table.class);
            if (tableAnnotation == null) return result;

            // Find unique constraints that include "slug"
            for (var constraint : tableAnnotation.uniqueConstraints()) {
                String[] columnNames = constraint.columnNames();

                // This is a composite constraint with slug, extract other field values
                if (containsSlug(columnNames) && columnNames.length > 1) {
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
        } catch (Exception e) {
            // If we can't determine composite constraints, return empty map
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
            if ("slug".equals(name)) return true;
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
                try { return field.get(entity); } catch (Exception e) { return null; }
            }
            if (field.getName().equalsIgnoreCase(columnName) || toSnakeCase(field.getName()).equals(columnName)) {
                try { return field.get(entity); } catch (Exception e) { return null; }
            }
        }
        return null;
    }

    /**
     * Converts a camelCase string to snake_case.
     *
     * @param str The camelCase string to convert.
     * @return The converted snake_case string.
     */
    private String toSnakeCase(String str) {
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    // -------------------------------------------------------------------------
    // Static helpers — also used by SlugCascadeListener
    // -------------------------------------------------------------------------

    /**
     * Resolves the slug source string from an entity.
     *
     * <p>Priority:
     * <ol>
     *   <li>Class-level {@code @SlugField(fields = [...])} — joins resolved field values.</li>
     *   <li>Field-level {@code @SlugField} (no fields) — returns that field's value.</li>
     * </ol>
     *
     * @param entity The entity from which to resolve the slug source.
     * @return The resolved slug source string, or {@code null} if not found.
     */
    public static String findSlugFieldValue(Object entity) {
        // 1. Class-level @SlugField with named fields
        SlugField classAnnotation = entity.getClass().getAnnotation(SlugField.class);
        if (classAnnotation != null && classAnnotation.fields().length > 0) {
            List<String> parts = new ArrayList<>();
            for (String path : classAnnotation.fields()) {
                String value = resolveFieldByPath(entity, path);
                if (value != null && !value.isBlank()) {
                    parts.add(value);
                }
            }
            return parts.isEmpty() ? null : String.join(classAnnotation.separator(), parts);
        }

        // 2. Backward compat: field-level @SlugField (search class hierarchy)
        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(SlugField.class)) {
                    field.setAccessible(true);
                    try {
                        Object value = field.get(entity);
                        if (value instanceof String s && !s.isBlank()) return s;
                    } catch (IllegalAccessException e) {
                        throw new SlugOperationException("Unable to access @SlugField: " + field.getName(), e);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    /**
     * Resolves a dot-notation field path (e.g. {@code "category.name"}) on an entity
     * via getter/field reflection.
     *
     * <ul>
     *   <li>If a segment (getter or field) does not exist, throws {@link SlugOperationException}.</li>
     *   <li>If a segment exists but its runtime value is {@code null}, returns {@code null}
     *       so the caller can skip this path.</li>
     * </ul>
     *
     * @param entity The root entity to start traversal from.
     * @param path   Dot-notation path such as {@code "title"} or {@code "category.name"}.
     * @return The resolved string value, or {@code null} if an intermediate value is {@code null}.
     * @throws SlugOperationException if any segment is not found in the class hierarchy.
     */
    public static String resolveFieldByPath(Object entity, String path) {
        Object current = entity;
        for (String part : path.split("\\.")) {
            current = resolveSegment(current, part, path);
            if (current == null) return null;
        }
        return current instanceof String s ? s : null;
    }

    /**
     * Resolves a single path segment on {@code obj}.
     *
     * <p>Tries the Java-style getter first (safe for Hibernate proxies),
     * then falls back to direct field access.
     * Returns {@code null} when the property exists but its value is {@code null} at runtime.
     * Throws {@link SlugOperationException} when neither a getter nor a field with {@code name} can be found.
     *
     * @param obj      The object to resolve the segment on.
     * @param name     The property name (single segment, no dots).
     * @param fullPath The full dot-notation path, used only for error messages.
     * @return The resolved value, or {@code null} if the property value is {@code null} at runtime.
     * @throws SlugOperationException if the property is not found or cannot be accessed.
     */
    private static Object resolveSegment(Object obj, String name, String fullPath) {
        String getterName = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);

        // Try getter first — triggers lazy-load initialization on Hibernate proxies
        try {
            Method getter = obj.getClass().getMethod(getterName);
            return getter.invoke(obj);
        } catch (NoSuchMethodException ignored) {
            // No getter found, fall through to direct field access
        } catch (InvocationTargetException e) {
            String cause = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new SlugOperationException(
                "@SlugField path '" + fullPath + "': '" + getterName + "' threw on "
                    + obj.getClass().getSimpleName() + ": " + cause, e);
        } catch (Exception e) {
            throw new SlugOperationException(
                "@SlugField path '" + fullPath + "': error invoking '" + getterName + "' on "
                    + obj.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }

        // Fall back to direct field access
        Field field = findFieldInHierarchy(obj.getClass(), name);
        if (field == null) {
            throw new SlugOperationException(
                "@SlugField path '" + fullPath + "': property '" + name
                    + "' not found on " + obj.getClass().getSimpleName());
        }
        field.setAccessible(true);
        try {
            return field.get(obj);
        } catch (IllegalAccessException e) {
            throw new SlugOperationException(
                "@SlugField path '" + fullPath + "': unable to access '" + name
                    + "' on " + obj.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Finds a field by name, searching up the class hierarchy.
     *
     * @param clazz The class to start searching from.
     * @param name  The field name.
     * @return The {@link Field}, or {@code null} if not found.
     */
    public static Field findFieldInHierarchy(Class<?> clazz, String name) {
        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) { }
            c = c.getSuperclass();
        }
        return null;
    }
}
