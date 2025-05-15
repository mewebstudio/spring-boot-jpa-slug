package com.mewebstudio.springboot.jpa.slug;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.lang.reflect.Field;

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

            String generatedSlug = provider.generateSlug(entity, slug);
            if (generatedSlug == null || generatedSlug.isBlank()) {
                throw new SlugOperationException("Generated slug is null or blank for base: " + slug);
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
}
