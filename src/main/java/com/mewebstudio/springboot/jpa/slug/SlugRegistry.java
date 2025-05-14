package com.mewebstudio.springboot.jpa.slug;

/**
 * Registry for managing the global {@link SlugProvider}.
 * <p>
 * This class is used to register and retrieve the {@link SlugProvider} used for generating slugs.
 * The {@link SlugProvider} is intended to be set at the application startup and used throughout the lifecycle
 * of slug generation operations.
 */
public class SlugRegistry {
    /**
     * The global {@link SlugProvider} instance.
     * <p>
     * This instance is used for generating slugs across the application.
     */
    private static SlugProvider slugProvider;

    /**
     * Private constructor to prevent instantiation.
     */
    private SlugRegistry() {
    }

    /**
     * Sets the {@link SlugProvider} to be used globally for slug generation.
     *
     * @param provider The {@link SlugProvider} instance to set.
     * @throws IllegalArgumentException if the provider is null.
     */
    public static void setSlugProvider(SlugProvider provider) {
        slugProvider = provider;
    }

    /**
     * Retrieves the currently set {@link SlugProvider}.
     *
     * @return The current {@link SlugProvider}.
     * @throws SlugOperationException if no {@link SlugProvider} has been set.
     */
    public static SlugProvider getSlugProvider() {
        if (slugProvider == null) {
            throw new SlugOperationException("SlugProvider not set");
        }

        return slugProvider;
    }
}
