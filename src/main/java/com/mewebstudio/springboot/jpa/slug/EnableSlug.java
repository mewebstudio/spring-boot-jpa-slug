package com.mewebstudio.springboot.jpa.slug;

import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @EnableSlug} is a configuration annotation that enables automatic slug generation
 * in a Spring Boot application.
 *
 * <p>This annotation imports {@link SlugAutoConfiguration} into the Spring application context
 * and optionally allows specifying a custom {@link ISlugGenerator} implementation.</p>
 *
 * <p>By default, {@link ISlugGenerator} is used as a placeholder. To provide custom slug generation
 * logic, supply your own implementation of the {@code ISlugGenerator} interface.</p>
 *
 * <p><strong>Example usage:</strong></p>
 * <pre>{@code
 * @EnableSlug(generator = CustomSlugGenerator.class)
 * @SpringBootApplication
 * public class MyApplication {
 *     public static void main(String[] args) {
 *         SpringApplication.run(MyApplication.class, args);
 *     }
 * }
 * }</pre>
 *
 * @see ISlugGenerator
 * @see DefaultSlugGenerator
 * @see SlugAutoConfiguration
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import(SlugAutoConfiguration.class)
public @interface EnableSlug {
    /**
     * Specifies the custom slug generator class to use.
     * If not provided, no custom slug generation logic is applied.
     *
     * @return the class implementing {@link ISlugGenerator}
     */
    Class<? extends ISlugGenerator> generator() default DefaultSlugGenerator.class;
}
