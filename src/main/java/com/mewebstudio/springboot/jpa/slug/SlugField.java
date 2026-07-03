package com.mewebstudio.springboot.jpa.slug;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity field or class as the source for slug generation.
 *
 * <p><strong>Field-level (backward compatible):</strong></p>
 * <pre>{@code
 * @SlugField
 * private String title;
 * }</pre>
 * The annotated field's value is used as the slug source.
 *
 * <p><strong>Class-level (multi-field):</strong></p>
 * <pre>{@code
 * @SlugField(fields = {"title", "description"})
 * public class Article implements ISlugSupport<Long> { ... }
 * }</pre>
 * The listed field values are joined with {@link #separator()} before slug generation.
 *
 * <p><strong>Dot-notation (related entity field):</strong></p>
 * <pre>{@code
 * @SlugField(fields = {"category.name", "title"})
 * public class Article implements ISlugSupport<Long> { ... }
 * }</pre>
 * Traverses the object graph via getter/field reflection. Null intermediate values skip that path.
 *
 * <p>When both a class-level and a field-level {@code @SlugField} are present, class-level takes priority.</p>
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SlugField {
    /**
     * Field paths to use as slug source. Empty means field-level (backward-compatible) mode.
     * Supports dot-notation for related entity fields (e.g. {@code "category.name"}).
     */
    String[] fields() default {};

    /**
     * Separator used to join multiple field values before slug generation.
     * Default is a space, which becomes a hyphen after slug generation.
     */
    String separator() default " ";
}
