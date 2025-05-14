package com.mewebstudio.springboot.jpa.slug;

/**
 * Interface to be implemented by custom slug generators.
 * <p>
 * Provides logic for generating unique slugs based on an entity and a base slug string.
 */
public interface SlugProvider {
    /**
     * Generates a slug for the given entity using the provided base slug.
     * <p>
     * Implementations may apply additional rules such as uniqueness checks, suffixes, or normalization.
     *
     * @param entity The entity for which the slug is being generated.
     * @param slug   The base slug string derived from the entity's annotated {@link SlugField}.
     * @return A valid and unique slug string to be assigned to the entity.
     */
    String generateSlug(Object entity, String slug);
}
