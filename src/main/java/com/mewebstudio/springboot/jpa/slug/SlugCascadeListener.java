package com.mewebstudio.springboot.jpa.slug;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import org.hibernate.event.spi.PostUpdateEvent;
import org.hibernate.event.spi.PostUpdateEventListener;

import java.lang.reflect.Method;
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
    /**
     * Cached reflective handle to {@code PostUpdateEvent.getSession()}, resolved by name so it works
     * regardless of the return type declared by the Hibernate version on the classpath
     * (Hibernate 6 → {@code EventSource}, Hibernate 7 → {@code SharedSessionContractImplementor}).
     */
    private static final Method SESSION_ACCESSOR;

    static {
        try {
            SESSION_ACCESSOR = PostUpdateEvent.class.getMethod("getSession");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // The EntityManager is the event's active Hibernate session — it is borrowed, not owned, and must
    // not be closed here (doing so would break the surrounding transaction), so the resource warning is
    // intentionally suppressed.
    @SuppressWarnings("resource")
    @Override
    public void onPostUpdate(PostUpdateEvent event) {
        Object updatedEntity = event.getEntity();
        List<SlugRegistry.CascadeDependency> dependents = SlugRegistry.getCascadeDependents(updatedEntity.getClass());
        if (dependents.isEmpty()) return;

        // Resolve the session reflectively: PostUpdateEvent.getSession()'s declared return type changed
        // between Hibernate 6 (EventSource) and Hibernate 7 (SharedSessionContractImplementor). Calling
        // it directly binds the bytecode to one return-type descriptor and throws NoSuchMethodError on
        // the other. Reflection resolves the method by name; the concrete session is an EntityManager
        // on both versions.
        EntityManager em = resolveEntityManager(event);

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

    private static EntityManager resolveEntityManager(PostUpdateEvent event) {
        try {
            return (EntityManager) SESSION_ACCESSOR.invoke(event);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to resolve Hibernate session from PostUpdateEvent", e);
        }
    }

}
