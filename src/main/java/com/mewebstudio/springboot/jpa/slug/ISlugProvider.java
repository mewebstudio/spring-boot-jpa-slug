package com.mewebstudio.springboot.jpa.slug;

import java.util.Collections;
import java.util.Map;

/**
 * Interface to be implemented by custom slug generators.
 * <p>
 * Provides logic for generating unique slugs based on an entity and a base slug string.
 */
public interface ISlugProvider {
    /**
     * Generates a slug for the given entity using the provided base slug.
     * <p>
     * Implementations may apply additional rules such as uniqueness checks, suffixes, or normalization.
     *
     * @param entity                    The entity for which the slug is being generated.
     * @param slug                      The base slug string derived from the entity's annotated {@link SlugField}.
     * @param compositeConstraintFields Map of column names to values that are part of composite unique constraints with slug.
     *                                  For example, if there's a unique constraint on (locale, slug), this map will contain
     *                                  {"locale": "en-US"}. This allows the slug generator to check uniqueness within the
     *                                  scope of these constraint fields.
     * @return A valid and unique slug string to be assigned to the entity.
     */
    String generateSlug(Object entity, String slug, Map<String, Object> compositeConstraintFields);

    /**
     * Default implementation for backward compatibility.
     * Calls the new method with an empty map.
     *
     * @param entity The entity for which the slug is being generated.
     * @param slug   The base slug string derived from the entity's annotated {@link SlugField}.
     * @return A valid and unique slug string to be assigned to the entity.
     */
    default String generateSlug(Object entity, String slug) {
        return generateSlug(entity, slug, Collections.emptyMap());
    }
}
