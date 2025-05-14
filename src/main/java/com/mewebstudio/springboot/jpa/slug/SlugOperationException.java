package com.mewebstudio.springboot.jpa.slug;

/**
 * Exception thrown when an error occurs during slug generation or slug-related operations.
 * <p>
 * This exception is typically thrown when a slug operation fails, such as when a duplicate slug is found,
 * when the slug cannot be generated, or any other slug-related error occurs during the process.
 * </p>
 *
 * @see SlugUtil
 * @see SlugRegistry
 */
public class SlugOperationException extends RuntimeException {
    /**
     * Default constructor that initializes the exception with a default error message.
     */
    public SlugOperationException() {
        super("An error occurred during slug operation.");
    }

    /**
     * Constructor that initializes the exception with a specific error message.
     *
     * @param message the detail message explaining the cause of the exception
     */
    public SlugOperationException(String message) {
        super(message);
    }

    /**
     * Constructor that initializes the exception with a specific error message and a cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param e       the cause of the exception (usually another exception that triggered this one)
     */
    public SlugOperationException(String message, Exception e) {
        super(message, e);
    }
}
