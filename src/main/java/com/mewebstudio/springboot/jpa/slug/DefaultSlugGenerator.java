package com.mewebstudio.springboot.jpa.slug;

/**
 * Default implementation of the {@link ISlugGenerator} interface that generates slugs by transforming
 * the input string to a standardized format.
 *
 * <p>This implementation converts the input string to lowercase, removes any non-alphanumeric characters
 * (except for spaces and hyphens), replaces consecutive spaces or hyphens with a single hyphen,
 * and ensures the final slug has no extra hyphens.
 *
 * <p>Example:
 * <pre>{@code
 *     DefaultSlugGenerator generator = new DefaultSlugGenerator();
 *     String slug = generator.generate("Hello World! This is a test.");
 *     System.out.println(slug); // Output: "hello-world-this-is-a-test"
 * }</pre>
 */
public class DefaultSlugGenerator implements ISlugGenerator {
    /**
     * Constructs a new {@code DefaultSlugGenerator}.
     */
    public DefaultSlugGenerator() {
        // Default constructor
    }

    /**
     * Generates a slug from the given input string.
     *
     * <p>This method processes the input string by:
     * <ul>
     *   <li>Converting the string to lowercase</li>
     *   <li>Removing any characters that are not alphanumeric, spaces, or hyphens</li>
     *   <li>Replacing one or more spaces with a single hyphen</li>
     *   <li>Ensuring that a single hyphen replaces multiple hyphens</li>
     * </ul>
     *
     * @param input the input string to generate a slug from. May be {@code null}.
     * @return the generated slug, or {@code null} if the input is {@code null}.
     */
    @Override
    public String generate(String input) {
        if (input == null) return null;

        return input.toLowerCase()
            .replaceAll("[^a-z0-9\\s-]", "")
            .replaceAll("\\s+", "-")
            .replaceAll("-+", "-");
    }
}
