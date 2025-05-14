package com.mewebstudio.springboot.jpa.slug;

/**
 * Strategy interface for generating slugs from input strings.
 *
 * <p>Implementations of this interface define custom logic to convert
 * a given input string (e.g., a title or name) into a URL-friendly slug.</p>
 *
 * <p>This interface is intended to be used in conjunction with the
 * {@link EnableSlug} annotation to plug in custom slug generation behavior
 * within a Spring Boot application.</p>
 *
 * <p><strong>Example:</strong></p>
 * <pre>{@code
 * public class CustomSlugGenerator implements ISlugGenerator {
 *     @Override
 *     public String generate(String input) {
 *         return input.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
 *     }
 * }
 * }</pre>
 *
 * @see EnableSlug
 */
public interface ISlugGenerator {
    /**
     * Generates a slug from the given input string.
     *
     * @param input the original string (e.g., a title or name)
     * @return a URL-friendly slug
     */
    String generate(String input);
}
