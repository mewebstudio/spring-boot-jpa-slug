package com.mewebstudio.springboot.jpa.slug;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark a field in a class that should be processed for slug generation.
 * <p>
 * This annotation is typically used to indicate which fields in an entity or model class
 * need to be automatically transformed into slugs when saved or processed.
 * It can be applied to fields that contain text-based data that should be converted into a slug
 * (e.g., a title or name field).
 * </p>
 * <p>
 * Example usage:
 * <pre>
 *     public class BlogPost {
 *         {@literal @}SlugField
 *         private String title;
 *     }
 * </pre>
 * <p>
 * The field marked with {@link SlugField} will be eligible for slug generation based on
 * the configured {@link ISlugGenerator} or slugging strategy.
 * </p>
 *
 * <p>
 * This annotation is retained at runtime, allowing reflection-based slug generation or
 * processing to take place at runtime.
 * </p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SlugField {
}
