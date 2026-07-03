package com.mewebstudio.springboot.jpa.slug;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;

import java.util.List;

/**
 * Hibernate {@link PostUpdateEventListener} that cascades slug regeneration to dependent entities.
 *
 * <p>When an entity is updated (e.g. a {@code Category}), this listener finds all
 * {@link ISlugSupport} entities that reference it via a dot-notation {@link SlugField} path
 * (e.g. {@code @SlugField(fields = {"category.name", "title"})}) and updates their slugs
 * via a JPQL bulk UPDATE — which does not re-trigger JPA lifecycle callbacks.</p>
 *
 * <p>Registered programmatically by {@link SlugAutoConfiguration} — no {@code @EntityListeners}
 * needed on the intermediate entity (e.g. {@code Category}).</p>
 */
public class SlugCascadeListener implements PostUpdateEventListener {
    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        Object updatedEntity = event.getEntity();
        List<SlugRegistry.CascadeDependency> dependents = SlugRegistry.getCascadeDependents(updatedEntity.getClass());
        if (dependents.isEmpty()) return;

        // event.getSession() implements EntityManager in Hibernate 6
        EntityManager em = (EntityManager) event.getSession();

        for (SlugRegistry.CascadeDependency dep : dependents) {
            // Both values validated at registration time; double-check here for defense-in-depth
            if (!Constants.SAFE_IDENTIFIER.matcher(dep.entityName()).matches()
                || !Constants.SAFE_IDENTIFIER.matcher(dep.fieldName()).matches()) {
                continue;
            }

            try {
                // Find all dependent entities referencing the updated entity
                String selectJpql = "SELECT e FROM " + dep.entityName()
                    + " e WHERE e." + dep.fieldName() + " = :ref";
                List<?> results = em.createQuery(selectJpql)
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

                    // JPQL bulk UPDATE — does not trigger PreUpdate, no infinite loop
                    String updateJpql = "UPDATE " + dep.entityName()
                        + " e SET e.slug = :slug WHERE e.id = :id";
                    em.createQuery(updateJpql)
                        .setParameter("slug", newSlug)
                        .setParameter("id", dependent.getId())
                        .executeUpdate();
                }
            } catch (Exception e) {
                // Cascade failure must not block the original update
            }
        }
    }

}
