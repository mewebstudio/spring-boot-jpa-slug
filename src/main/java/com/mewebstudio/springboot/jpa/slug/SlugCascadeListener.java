package com.mewebstudio.springboot.jpa.slug;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Hibernate {@link PostUpdateEventListener} that cascades slug regeneration to dependent entities.
 *
 * <p>When an entity is updated (e.g. a {@code Category}), this listener finds all
 * {@link ISlugSupport} entities that reference it via a dot-notation {@link SlugField} path
 * (e.g. {@code @SlugField(fields = {"category.name", "title"})}) and refreshes their slugs via a
 * JPQL bulk {@code UPDATE}.</p>
 *
 * <p>The refresh is intentionally <strong>deferred until after the surrounding transaction
 * commits</strong> and then runs in a fresh transaction. {@code onPostUpdate} fires while Hibernate
 * is executing the flush action queue; issuing DML there would mutate that queue mid-iteration and
 * throw {@link java.util.ConcurrentModificationException} at commit. Running it post-commit — via the
 * injected {@link EntityManager} and {@link PlatformTransactionManager} rather than the event's own
 * session — also keeps the listener free of the Hibernate {@code PostUpdateEvent.getSession()} API,
 * whose return type differs between Hibernate 6 and 7.</p>
 *
 * <p>Registered programmatically by {@link SlugAutoConfiguration} — no {@code @EntityListeners}
 * needed on the intermediate entity.</p>
 */
public class SlugCascadeListener implements PostUpdateEventListener {
    private final PlatformTransactionManager transactionManager;
    private final EntityManager entityManager;

    public SlugCascadeListener(PlatformTransactionManager transactionManager, EntityManager entityManager) {
        this.transactionManager = transactionManager;
        this.entityManager = entityManager;
    }

    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        Object updatedEntity = event.getEntity();
        List<SlugRegistry.CascadeDependency> dependents = SlugRegistry.getCascadeDependents(updatedEntity.getClass());
        if (dependents.isEmpty()) return;

        // Cannot defer without an active synchronization; skip rather than risk running DML now.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    refreshDependentSlugs(updatedEntity, dependents);
                }
            }
        });
    }

    /**
     * Refresh the slugs of the entities that depend on {@code updatedEntity}, in a fresh transaction.
     * Runs after the original transaction has committed, so it is clear of the flush action queue and
     * the parent's row locks have been released.
     */
    private void refreshDependentSlugs(Object updatedEntity, List<SlugRegistry.CascadeDependency> dependents) {
        new TransactionTemplate(transactionManager).executeWithoutResult(txStatus -> {
            for (SlugRegistry.CascadeDependency dep : dependents) {
                // Both values validated at registration time; double-check here for defense-in-depth
                if (!Constants.SAFE_IDENTIFIER.matcher(dep.entityName()).matches()
                    || !Constants.SAFE_IDENTIFIER.matcher(dep.fieldName()).matches()) {
                    continue;
                }

                try {
                    String selectJpql = "SELECT e FROM " + dep.entityName()
                        + " e WHERE e." + dep.fieldName() + " = :ref";
                    List<?> results = entityManager.createQuery(selectJpql)
                        .setParameter("ref", updatedEntity)
                        .setFlushMode(FlushModeType.COMMIT)
                        .getResultList();

                    for (Object obj : results) {
                        if (!(obj instanceof ISlugSupport<?> dependent)) continue;

                        String sourceValue = SlugListener.findSlugFieldValue(obj);
                        if (sourceValue == null || sourceValue.isBlank()) continue;

                        String base = SlugUtil.generate(sourceValue);
                        if (base == null || base.isBlank()) continue;

                        String newSlug = SlugRegistry.getSlugProvider().generateSlug(dependent, base);

                        String updateJpql = "UPDATE " + dep.entityName()
                            + " e SET e.slug = :slug WHERE e.id = :id";
                        entityManager.createQuery(updateJpql)
                            .setParameter("slug", newSlug)
                            .setParameter("id", dependent.getId())
                            .executeUpdate();
                    }
                } catch (Exception e) {
                    // Cascade failure must not affect the (already committed) original update
                }
            }
        });
    }
}
