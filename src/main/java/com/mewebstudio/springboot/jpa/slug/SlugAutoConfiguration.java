package com.mewebstudio.springboot.jpa.slug;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.orm.jpa.JpaBaseConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

/**
 * Auto-configuration class for enabling slug generation in JPA entities.
 *
 * <p>This configuration is activated automatically when a bean annotated with {@link EnableSlug}
 * is present in the application context and when JPA is available.</p>
 *
 * <p>It initializes and registers the {@link ISlugGenerator} implementation defined in the
 * {@code @EnableSlug(generator = ...)} annotation and sets up a {@link SlugProvider}
 * for managing unique slug generation with collision handling.</p>
 *
 * <p>The configuration ensures slugs are unique per entity type by checking the database
 * using a {@link TransactionTemplate}.</p>
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
@AutoConfigureAfter(JpaBaseConfiguration.class)
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
     * The transaction manager used for database operations.
     * This is injected by the Spring container.
     */
    private final PlatformTransactionManager transactionManager;

    /**
     * Constructs the slug auto-configuration with required dependencies.
     *
     * @param context            the application context used to locate beans and annotations
     * @param transactionManager the transaction manager used for database operations
     */
    public SlugAutoConfiguration(ApplicationContext context, PlatformTransactionManager transactionManager) {
        this.context = context;
        this.transactionManager = transactionManager;
    }

    /**
     * Initializes slug generation support after the application context is loaded.
     *
     * <p>This method scans for beans annotated with {@link EnableSlug}, retrieves the
     * configured {@link ISlugGenerator}, and registers a {@link SlugProvider}
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

        SlugRegistry.setSlugProvider((entity, newSlug) -> {
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

                String entityId = entity instanceof ISlugSupport<?>
                    ? (String) ((ISlugSupport<?>) entity).getId()
                    : null;

                int attempt = 0;

                while (slugExists(entity.getClass(), slug, entityId)) {
                    if (attempt++ >= MAX_ATTEMPTS) {
                        throw new SlugOperationException(
                            "Unable to generate unique slug for: " + base + ", after " + MAX_ATTEMPTS + " attempts");
                    }
                    slug = base + "-" + i++;
                }

                return slug;
            } catch (Exception e) {
                throw new SlugOperationException("SlugProvider failed: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Checks whether a given slug already exists in the database for the specified entity type.
     *
     * @param entityClass the entity class to check for slug collisions
     * @param slug        the slug candidate to test
     * @param entityId    the ID of the current entity (to exclude itself during updates)
     * @return {@code true} if the slug already exists for another entity, {@code false} otherwise
     */
    protected boolean slugExists(Class<?> entityClass, String slug, String entityId) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            try {
                if (slug == null || slug.isBlank()) {
                    return false;
                }

                CriteriaBuilder cb = entityManager.getCriteriaBuilder();
                CriteriaQuery<Long> query = cb.createQuery(Long.class);
                Root<?> root = query.from(entityClass);

                if (entityId != null && !entityId.isBlank()) {
                    query.select(cb.count(root))
                        .where(
                            cb.and(
                                cb.equal(cb.lower(root.get("slug")), slug.toLowerCase()),
                                cb.notEqual(root.get("id"), entityId)
                            )
                        );
                } else {
                    query.select(cb.count(root))
                        .where(cb.equal(cb.lower(root.get("slug")), slug.toLowerCase()));
                }

                return entityManager.createQuery(query).getSingleResult() > 0;
            } catch (Exception e) {
                return false;
            }
        }));
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
