package com.mewebstudio.springboot.jpa.slug;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Autoconfiguration class for enabling slug generation in JPA entities.
 *
 * <p>This configuration is activated automatically when a bean annotated with {@link EnableSlug}
 * is present in the application context and when JPA is available.</p>
 *
 * <p>It initializes and registers the {@link ISlugGenerator} implementation defined in the
 * {@code @EnableSlug(generator = ...)} annotation and sets up a {@link ISlugProvider}
 * for managing unique slug generation with collision handling.</p>
 *
 * <p>The configuration ensures slugs are unique per entity type by checking the database
 * using the current EntityManager session.</p>
 *
 * <p>Slug creation logic is executed during application startup, leveraging a {@code @PostConstruct}
 * lifecycle method.</p>
 *
 * @see EnableSlug
 * @see ISlugGenerator
 * @see ISlugSupport
 * @see SlugRegistry
 * @see SlugUtil
 */
@Configuration
@ConditionalOnClass(EntityManager.class)
public class SlugAutoConfiguration {
    /**
     * Maximum number of attempts to generate a unique slug.
     * If exceeded, an exception is thrown.
     */
    private static final int MAX_ATTEMPTS = 100;

    /**
     * The entity manager used for database operations.
     * This is injected by the Spring container.
     */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * The application context used to locate beans and annotations.
     * This is injected by the Spring container.
     */
    private final ApplicationContext context;

    /**
     * Constructs the slug autoconfiguration with required dependencies.
     *
     * @param context the application context used to locate beans and annotations
     */
    public SlugAutoConfiguration(ApplicationContext context) {
        this.context = context;
    }

    /**
     * Initializes slug generation support after the application context is loaded.
     *
     * <p>This method scans for beans annotated with {@link EnableSlug}, retrieves the
     * configured {@link ISlugGenerator}, and registers a {@link ISlugProvider}
     * responsible for generating unique slugs for entities implementing {@link ISlugSupport}.</p>
     *
     * @throws Exception if the slug generator cannot be instantiated
     */
    @PostConstruct
    @Transactional
    public void configureSlugSupport() throws Exception {
        Map<String, Object> beans = context.getBeansWithAnnotation(EnableSlug.class);
        if (beans.isEmpty()) {
            return;
        }

        Class<? extends ISlugGenerator> generatorClass = resolveGeneratorClass();
        ISlugGenerator generator = generatorClass.getDeclaredConstructor().newInstance();
        SlugUtil.setGenerator(generator);

        SlugRegistry.setSlugProvider((entity, newSlug, compositeConstraintFields) -> {
            try {
                if (newSlug == null || newSlug.isBlank()) {
                    throw new SlugOperationException("Base slug cannot be null or blank");
                }

                String base = SlugUtil.generate(newSlug);
                if (base == null || base.isBlank()) {
                    throw new SlugOperationException("Slugified base is null or blank: " + newSlug);
                }

                String slug = base;
                int i = 2;

                Object entityId = entity instanceof ISlugSupport<?>
                    ? ((ISlugSupport<?>) entity).getId()
                    : null;

                int attempt = 0;
                while (slugExists(entity.getClass(), slug, entityId, compositeConstraintFields)) {
                    if (attempt++ >= MAX_ATTEMPTS) {
                        throw new SlugOperationException(
                            "Unable to generate unique slug for: " + base + ", after " + MAX_ATTEMPTS + " attempts");
                    }
                    slug = base + "-" + i++;
                }

                return slug;
            } catch (Exception e) {
                throw new SlugOperationException("ISlugProvider failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Checks whether a given slug already exists in the database for the specified entity type.
     *
     * @param entityClass               the entity class to check for slug collisions
     * @param slug                      the slug candidate to test
     * @param entityId                  the ID of the current entity (to exclude itself during updates)
     * @param compositeConstraintFields Map of column names to values that are part of composite unique constraints.
     *                                  For example, if there's a unique constraint on (locale, slug), this map will contain
     *                                  {"locale": "en-US"}. The slug existence check will be scoped to these values.
     * @return {@code true} if the slug already exists for another entity, {@code false} otherwise
     */
    protected boolean slugExists(Class<?> entityClass, String slug, Object entityId, Map<String, Object> compositeConstraintFields) {
        try {
            if (slug == null || slug.isBlank()) {
                return false;
            }

            // Flush pending changes to make them visible to this query
            entityManager.flush();

            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<?> root = query.from(entityClass);

            // Build predicates list
            List<Predicate> predicates = new ArrayList<>();

            // Add slug equality predicate
            predicates.add(cb.equal(cb.lower(root.get("slug")), slug.toLowerCase()));

            // Add entity ID exclusion predicate (for updates)
            if (entityId != null) {
                predicates.add(cb.notEqual(root.get("id"), entityId));
            }

            // Add composite constraint field predicates
            if (compositeConstraintFields != null) {
                for (Map.Entry<String, Object> entry : compositeConstraintFields.entrySet()) {
                    String columnName = entry.getKey();
                    Object value = entry.getValue();
                    if (value != null) {
                        // Find the field name by column name (handle both snake_case and camelCase)
                        String fieldName = findFieldNameByColumnName(entityClass, columnName);
                        if (fieldName != null) {
                            predicates.add(cb.equal(root.get(fieldName), value));
                        }
                    }
                }
            }

            query.select(cb.count(root)).where(predicates.toArray(new Predicate[0]));

            Long count = entityManager.createQuery(query).getSingleResult();
            return count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Overloaded method for backward compatibility.
     *
     * @param entityClass the entity class to check for slug collisions
     * @param slug        the slug candidate to test
     * @param entityId    the ID of the current entity (to exclude itself during updates)
     * @return {@code true} if the slug already exists for another entity, {@code false} otherwise
     */
    protected boolean slugExists(Class<?> entityClass, String slug, Object entityId) {
        return slugExists(entityClass, slug, entityId, Collections.emptyMap());
    }

    /**
     * Finds the field name in an entity class by its column name.
     *
     * @param entityClass The entity class to inspect
     * @param columnName  The database column name
     * @return The field name, or null if not found
     */
    private String findFieldNameByColumnName(Class<?> entityClass, String columnName) {
        for (Field field : entityClass.getDeclaredFields()) {
            // Check @Column annotation
            Column columnAnnotation = field.getAnnotation(Column.class);
            if (columnAnnotation != null && columnName.equals(columnAnnotation.name())) {
                return field.getName();
            }

            // Fallback: if field name matches column name (handling snake_case conversion)
            if (field.getName().equalsIgnoreCase(columnName) || toSnakeCase(field.getName()).equals(columnName)) {
                return field.getName();
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

    /**
     * Resolves the {@link ISlugGenerator} implementation class from the {@link EnableSlug} annotation.
     *
     * @return the class of the slug generator to use
     * @throws SlugOperationException if no valid generator is defined
     */
    private Class<? extends ISlugGenerator> resolveGeneratorClass() {
        Map<String, Object> beans = context.getBeansWithAnnotation(EnableSlug.class);
        for (Object bean : beans.values()) {
            EnableSlug enableSlug = bean.getClass().getAnnotation(EnableSlug.class);
            if (enableSlug != null && !enableSlug.generator().equals(ISlugGenerator.class)) {
                return enableSlug.generator();
            }
        }
        throw new SlugOperationException("No slug generator defined in @EnableSlug annotation.");
    }
}
