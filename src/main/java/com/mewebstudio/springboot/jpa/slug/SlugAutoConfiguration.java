package com.mewebstudio.springboot.jpa.slug;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceUnit;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.Type;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Autoconfiguration class for enabling slug generation in JPA entities.
 *
 * <p>Activated when a bean annotated with {@link EnableSlug} is present and JPA is on the classpath.</p>
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Registers the {@link ISlugGenerator} and {@link ISlugProvider} for unique slug generation.</li>
 *   <li>Scans JPA entity metadata to build a cascade dependency map for dot-notation {@link SlugField} paths.</li>
 *   <li>Registers {@link SlugCascadeListener} with Hibernate so that updating an intermediate entity
 *       (e.g. {@code Category}) automatically refreshes the slug of dependent entities (e.g. {@code Article}).</li>
 * </ul>
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
    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

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
        if (beans.isEmpty()) return;

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
                Object entityId = entity instanceof ISlugSupport<?> s ? s.getId() : null;
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

        buildCascadeMap();
        registerCascadeListener();
    }

    /**
     * Checks whether a given slug already exists in the database for the specified entity type.
     *
     * @param entityClass               the entity class to check for slug existence
     * @param slug                      the slug to check for uniqueness
     * @param entityId                  the ID of the entity to ignore in the uniqueness check (can be null)
     * @param compositeConstraintFields a map of additional fields to consider for uniqueness (can be null)
     * @return true if the slug exists for another entity of the same type, false otherwise
     */
    protected boolean slugExists(Class<?> entityClass, String slug, Object entityId,
                                 Map<String, Object> compositeConstraintFields) {
        try {
            if (slug == null || slug.isBlank()) return false;

            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Long> query = cb.createQuery(Long.class);
            Root<?> root = query.from(entityClass);

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(cb.lower(root.get("slug")), slug.toLowerCase()));

            if (entityId != null) {
                predicates.add(cb.notEqual(root.get("id"), entityId));
            }

            if (compositeConstraintFields != null) {
                for (Map.Entry<String, Object> entry : compositeConstraintFields.entrySet()) {
                    if (entry.getValue() != null) {
                        String fieldName = findFieldNameByColumnName(entityClass, entry.getKey());
                        if (fieldName != null) {
                            predicates.add(cb.equal(root.get(fieldName), entry.getValue()));
                        }
                    }
                }
            }

            query.select(cb.count(root)).where(predicates.toArray(new Predicate[0]));

            Long count;
            try {
                var typedQuery = entityManager.createQuery(query);
                typedQuery.setFlushMode(jakarta.persistence.FlushModeType.COMMIT);
                count = typedQuery.getSingleResult();
            } catch (Exception ex) {
                count = 0L;
            }
            return count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks whether a given slug already exists in the database for the specified entity type,
     * ignoring the entity with the provided ID.
     *
     * @param entityClass the entity class to check for slug existence
     * @param slug        the slug to check for uniqueness
     * @param entityId    the ID of the entity to ignore in the uniqueness check (can be null)
     * @return true if the slug exists for another entity of the same type, false otherwise
     */
    @SuppressWarnings("unused")
    protected boolean slugExists(Class<?> entityClass, String slug, Object entityId) {
        return slugExists(entityClass, slug, entityId, Collections.emptyMap());
    }

    /**
     * Scans the JPA metamodel for {@link ISlugSupport} entities that have class-level {@link SlugField}
     * with dot-notation paths and builds a reverse cascade dependency map in {@link SlugRegistry}.
     *
     * <p>Example: {@code @SlugField(fields = {"category.name", "title"})} on {@code Article} registers:
     * {@code Category → CascadeDependency("category", "Article")}</p>
     */
    private void buildCascadeMap() {
        SlugRegistry.clearCascadeDependents();

        Set<Class<?>> entityClasses = entityManagerFactory.getMetamodel().getEntities()
            .stream()
            .map(Type::getJavaType)
            .collect(Collectors.toSet());

        for (Class<?> entityClass : entityClasses) {
            if (!ISlugSupport.class.isAssignableFrom(entityClass)) continue;

            SlugField classAnnotation = entityClass.getAnnotation(SlugField.class);
            if (classAnnotation == null) continue;

            for (String fieldPath : classAnnotation.fields()) {
                if (!fieldPath.contains(".")) continue;

                String firstSegment = fieldPath.substring(0, fieldPath.indexOf('.'));
                Class<?> intermediateType;
                try {
                    Field field = SlugListener.findFieldInHierarchy(entityClass, firstSegment);
                    if (field == null) continue;
                    intermediateType = field.getType();
                } catch (Exception e) {
                    continue;
                }

                // Only register cascade for actual JPA entities, not embeddable
                if (entityClasses.stream().noneMatch(c -> c == intermediateType)) continue;

                String entityName = resolveEntityName(entityClass);
                SlugRegistry.registerCascadeDependent(
                    intermediateType,
                    new SlugRegistry.CascadeDependency(firstSegment, entityName)
                );
            }
        }
    }

    /**
     * Registers {@link SlugCascadeListener} with Hibernate's {@link EventListenerRegistry} so it fires
     * on every entity PostUpdate — no {@code @EntityListeners} needed on intermediate entities.
     */
    private void registerCascadeListener() {
        try {
            SessionFactoryImplementor sessionFactory =
                entityManagerFactory.unwrap(SessionFactoryImplementor.class);
            EventListenerRegistry registry =
                sessionFactory.getServiceRegistry().getService(EventListenerRegistry.class);
            if (registry != null) {
                PlatformTransactionManager transactionManager = context.getBean(PlatformTransactionManager.class);
                registry.appendListeners(
                    EventType.POST_UPDATE, new SlugCascadeListener(transactionManager, entityManager));
            }
        } catch (Exception e) {
            // If Hibernate is not the JPA provider, cascade slug updates are silently skipped
        }
    }

    /**
     * Returns the JPQL entity name — respects {@code @Entity(name = "...")} if set.
     *
     * @param clazz the entity class
     * @return the JPQL entity name
     */
    private String resolveEntityName(Class<?> clazz) {
        Entity entityAnnotation = clazz.getAnnotation(Entity.class);
        if (entityAnnotation != null && !entityAnnotation.name().isBlank()) {
            return entityAnnotation.name();
        }
        return clazz.getSimpleName();
    }

    /**
     * Finds the field name in the entity class that corresponds to the given column name.
     *
     * <p>This method checks for the {@link Column} annotation's name attribute, as well as
     * the field name itself (case-insensitive) and its snake_case equivalent.</p>
     *
     * @param entityClass the entity class to inspect
     * @param columnName  the database column name to match
     * @return the corresponding field name, or {@code null} if no match is found
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
     * Converts a camelCase string to snake_case.
     *
     * @param str the camelCase string
     * @return the snake_case equivalent of the input string
     */
    private String toSnakeCase(String str) {
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    /**
     * Resolves the slug generator class from the {@link EnableSlug} annotation on any bean in the application context.
     * If no custom generator is specified, it defaults to {@link ISlugGenerator}.
     *
     * <p>This method scans all beans annotated with {@code @EnableSlug} and retrieves the generator class specified in
     * the annotation.</p>
     *
     * @return the class of the slug generator to be used for generating slugs
     * @throws SlugOperationException if no slug generator is defined in the {@code @EnableSlug} annotation
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
