package com.mewebstudio.springboot.jpa.slug;

/**
 * Interface to be implemented by entities that support slugs.
 *
 * <p>This interface defines the contract for entities that require a slug field,
 * typically for use in SEO-friendly URLs or human-readable identifiers.</p>
 *
 * <p>Implementing this interface allows the slug generation mechanism
 * (e.g., via {@link ISlugGenerator}) to interact with the entity's
 * identifier and slug field in a consistent way.</p>
 *
 * <p><strong>Usage example:</strong></p>
 * <pre>{@code
 * @Entity
 * public class Article implements ISlugSupport<Long> {
 *     private Long id;
 *     private String title;
 *     private String slug;
 *
 *     @Override
 *     public Long getId() {
 *         return id;
 *     }
 *
 *     @Override
 *     public String getSlug() {
 *         return slug;
 *     }
 *
 *     @Override
 *     public void setSlug(String slug) {
 *         this.slug = slug;
 *     }
 * }
 * }</pre>
 *
 * @param <ID> the type of the entity's identifier (e.g., {@code Long}, {@code UUID})
 * @see ISlugGenerator
 * @see EnableSlug
 */
public interface ISlugSupport<ID> {
    /**
     * Returns the unique identifier of the entity.
     *
     * @return the entity ID
     */
    ID getId();

    /**
     * Returns the current slug value of the entity.
     *
     * @return the slug
     */
    String getSlug();

    /**
     * Sets the slug value of the entity.
     *
     * @param slug the slug to set
     */
    void setSlug(String slug);
}
