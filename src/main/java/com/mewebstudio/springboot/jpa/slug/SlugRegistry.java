package com.mewebstudio.springboot.jpa.slug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing the global {@link ISlugProvider} and cascade slug dependencies.
 */
public class SlugRegistry {
    /**
     * Describes a dependent entity whose slug must be refreshed when an intermediate entity updates.
     *
     * <p>For example, if {@code Article} has {@code @SlugField(fields = {"category.name", "title"})},
     * a {@code CascadeDependency("category", "Article")} is registered under {@code Category.class}.</p>
     *
     * @param fieldName  The field on the dependent entity referencing the intermediate entity.
     * @param entityName The JPQL entity name for the dependent entity.
     */
    public record CascadeDependency(String fieldName, String entityName) {
        /**
         * Validates that {@code fieldName} and {@code entityName} are valid JPA identifiers.
         *
         * @throws IllegalArgumentException if {@code fieldName} or {@code entityName} are not valid JPA identifiers.
         */
        public CascadeDependency {
            if (!Constants.SAFE_IDENTIFIER.matcher(fieldName).matches()) {
                throw new IllegalArgumentException("Invalid field name for slug cascade: " + fieldName);
            }
            if (!Constants.SAFE_IDENTIFIER.matcher(entityName).matches()) {
                throw new IllegalArgumentException("Invalid entity name for slug cascade: " + entityName);
            }
        }
    }

    private static ISlugProvider slugProvider;

    /**
     * Key: intermediate entity class (e.g. {@code Category.class})
     * Value: dependent entities whose slugs reference that intermediate entity
     */
    private static final Map<Class<?>, List<CascadeDependency>> cascadeDependents = new ConcurrentHashMap<>();

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private SlugRegistry() {
    }

    /**
     * Sets the global {@link ISlugProvider} implementation to be used for slug generation.
     *
     * @param provider The {@link ISlugProvider} implementation to set.
     * @throws IllegalArgumentException if {@code provider} is null.
     */
    public static void setSlugProvider(ISlugProvider provider) {
        slugProvider = provider;
    }

    /**
     * Returns the global {@link ISlugProvider} implementation.
     *
     * @throws SlugOperationException if the {@link ISlugProvider} has not been set.
     */
    public static ISlugProvider getSlugProvider() {
        if (slugProvider == null) {
            throw new SlugOperationException("ISlugProvider not set");
        }
        return slugProvider;
    }

    /**
     * Registers a cascade dependency: when {@code intermediateClass} is updated,
     * the slugs of all dependent entities described by {@code dep} will be refreshed.
     *
     * @param intermediateClass The intermediate entity class whose updates trigger slug refreshes.
     * @param dep               The {@link CascadeDependency} describing the dependent entity and its slug
     *                          relationship to the intermediate entity.
     */
    public static void registerCascadeDependent(Class<?> intermediateClass, CascadeDependency dep) {
        cascadeDependents.computeIfAbsent(intermediateClass, k -> new ArrayList<>()).add(dep);
    }

    /**
     * Returns the list of cascade dependencies for the given intermediate entity class.
     *
     * @param clazz The intermediate entity class.
     * @return A list of {@link CascadeDependency} instances representing dependent entities whose slugs
     * should be refreshed when the intermediate entity updates. Returns an empty list if none are registered.
     */
    public static List<CascadeDependency> getCascadeDependents(Class<?> clazz) {
        return cascadeDependents.getOrDefault(clazz, Collections.emptyList());
    }

    /**
     * Clears all registered cascade dependents. Primarily for testing and re-initialization.
     */
    public static void clearCascadeDependents() {
        cascadeDependents.clear();
    }
}
