package com.mewebstudio.springboot.jpa.slug;

/**
 * Registry for managing the global {@link ISlugProvider}.
 * <p>
 * This class is used to register and retrieve the {@link ISlugProvider} used for generating slugs.
 * The {@link ISlugProvider} is intended to be set at the application startup and used throughout the lifecycle
 * of slug generation operations.
 */
public class SlugRegistry {
    /**
     * The global {@link ISlugProvider} instance.
     * <p>
     * This instance is used for generating slugs across the application.
     */
    private static ISlugProvider ISlugProvider;

    /**
     * Private constructor to prevent instantiation.
     */
    private SlugRegistry() {
    }

    /**
     * Sets the {@link ISlugProvider} to be used globally for slug generation.
     *
     * @param provider The {@link ISlugProvider} instance to set.
     * @throws IllegalArgumentException if the provider is null.
     */
    public static void setSlugProvider(ISlugProvider provider) {
        ISlugProvider = provider;
    }

    /**
     * Retrieves the currently set {@link ISlugProvider}.
     *
     * @return The current {@link ISlugProvider}.
     * @throws SlugOperationException if no {@link ISlugProvider} has been set.
     */
    public static ISlugProvider getSlugProvider() {
        if (ISlugProvider == null) {
            throw new SlugOperationException("ISlugProvider not set");
        }

        return ISlugProvider;
    }
}
