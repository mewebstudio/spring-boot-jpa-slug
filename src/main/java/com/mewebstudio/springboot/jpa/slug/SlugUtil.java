package com.mewebstudio.springboot.jpa.slug;

/**
 * Utility class for handling slug generation operations.
 * <p>
 * This class provides static methods for setting and getting a global {@link ISlugGenerator} and for generating slugs.
 * The {@link ISlugGenerator} is used to transform input strings into slugs based on specific rules.
 */
public class SlugUtil {
    /**
     * The global {@link ISlugGenerator} instance.
     * <p>
     * This instance is used for generating slugs across the application.
     */
    private static ISlugGenerator generator;

    /**
     * Private constructor to prevent instantiation.
     */
    private SlugUtil() {
    }

    /**
     * Sets the {@link ISlugGenerator} to be used globally for slug generation.
     *
     * @param slugGenerator The {@link ISlugGenerator} instance to set.
     * @throws SlugOperationException if the provided generator is null.
     */
    public static void setGenerator(ISlugGenerator slugGenerator) {
        if (slugGenerator == null) {
            throw new SlugOperationException("SlugGenerator cannot be null");
        }

        generator = slugGenerator;
    }

    /**
     * Retrieves the currently set {@link ISlugGenerator}.
     *
     * @return The current {@link ISlugGenerator}.
     * @throws SlugOperationException if no {@link ISlugGenerator} has been set.
     */
    public static ISlugGenerator getGenerator() {
        if (generator == null) {
            throw new SlugOperationException("SlugGenerator not set");
        }

        return generator;
    }

    /**
     * Converts the input string into a slug using the globally set {@link ISlugGenerator}.
     * <p>
     * The generated slug will follow the rules defined by the {@link ISlugGenerator} implementation.
     *
     * @param input The input string to be converted into a slug.
     * @return The generated slug.
     */
    public static String generate(String input) {
        return getGenerator().generate(input);
    }
}
