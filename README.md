# Slug Generator for Spring Boot

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Maven Central](https://img.shields.io/maven-central/v/com.mewebstudio/spring-boot-jpa-slug)](https://central.sonatype.com/artifact/com.mewebstudio/spring-boot-jpa-slug)
[![Javadoc](https://javadoc.io/badge2/com.mewebstudio/spring-boot-jpa-slug/javadoc.svg)](https://javadoc.io/doc/com.mewebstudio/spring-boot-jpa-slug)

A simple and customizable slug generation solution for Spring Boot applications, designed to easily create and manage slugs for entities. This package integrates with JPA entities and provides a flexible way to generate unique slugs for your models.

---

## ✅ Features

- **Customizable Slug Generation**: Provides an interface for defining custom slug generation logic using `ISlugGenerator`.
- **Automatic Slug Assignment**: Automatically generates and assigns slugs to entities upon creation or update.
- **Unique Slug Enforcement**: Ensures that slugs are unique across entities, retrying with suffixes if needed.
- **Multi-Field Slug Sources**: Combine multiple fields into a single slug using `@SlugField(fields = {"title", "description"})` at class level.
- **Dot-Notation for Related Entities**: Reference fields from related entities with dot-notation: `@SlugField(fields = {"category.name", "title"})`.
- **Cascade Slug Updates**: When a related entity (e.g. `Category`) is updated, slugs of dependent entities (e.g. `Article`) are automatically refreshed — no `@EntityListeners` needed on the related entity.
- **Composite Unique Constraint Support**: Automatically detects and respects composite unique constraints (e.g., `locale + slug`) for multi-locale entities.
- **Integration with Spring Boot**: Easily integrates with Spring Boot using `@EnableSlug` and `SlugRegistry`.

## 📥 Installation

#### for Maven users

Add the following dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>com.mewebstudio</groupId>
  <artifactId>spring-boot-jpa-slug</artifactId>
  <version>0.1.6</version>
</dependency>
```

#### for Gradle users
```groovy
implementation 'com.mewebstudio:spring-boot-jpa-slug:0.1.6'
```

## 🚀 Usage

### 1. Add the `@EnableSlug` annotation to your Spring Boot application class:

```java
import com.mewebstudio.springboot.jpa.slug.EnableSlug;

@SpringBootApplication
@EnableSlug // Specify your custom generator if needed: @EnableSlug(generator = CustomSlugGenerator.class) 
public class SlugJavaImplApplication {
    public static void main(String[] args) {
        SpringApplication.run(SlugJavaImplApplication.class, args);
    }
}
```

### 2. Implement a Custom Slug Generator (Optional)

```java
import com.mewebstudio.springboot.jpa.slug.ISlugGenerator;

public class CustomSlugGenerator implements ISlugGenerator {
    @Override
    public String generate(String input) {
        // Implement your slug generation logic
        return input.toLowerCase().replaceAll("[^a-z0-9]", "-");
    }
}
```

### 3. Add Slug Field to Your Entity

#### Single field (field-level annotation — backward compatible)

```java
import com.mewebstudio.springboot.jpa.slug.ISlugSupport;
import com.mewebstudio.springboot.jpa.slug.SlugField;
import com.mewebstudio.springboot.jpa.slug.SlugListener;

@Entity
@EntityListeners(SlugListener.class)
public class Category implements ISlugSupport<Long> {
    @Id
    private Long id;

    @SlugField
    private String name;

    @Column(name = "slug", unique = true, nullable = false)
    private String slug;

    @Override public Long getId() { return id; }
    @Override public String getSlug() { return slug; }
    @Override public void setSlug(String slug) { this.slug = slug; }
}
```

#### Multiple fields (class-level annotation)

Place `@SlugField` on the class and list the field names to combine. Values are joined with a space (configurable via `separator`) before slug generation.

```java
@Entity
@EntityListeners(SlugListener.class)
@SlugField(fields = {"title", "description"})
public class Article implements ISlugSupport<Long> {
    @Id
    private Long id;

    private String title;
    private String description;

    @Column(name = "slug", unique = true, nullable = false)
    private String slug;

    @Override public Long getId() { return id; }
    @Override public String getSlug() { return slug; }
    @Override public void setSlug(String slug) { this.slug = slug; }
}
// title="Hello World", description="A great article"
// → slug = "hello-world-a-great-article"
```

Custom separator example:

```java
@SlugField(fields = {"brand", "model"}, separator = " - ")
```

#### Dot-notation for related entity fields

Reference a field on a related entity using dot-notation. The library automatically navigates the object graph at persist/update time.

```java
@Entity
@EntityListeners(SlugListener.class)
@SlugField(fields = {"category.name", "title"})
public class Article implements ISlugSupport<Long> {
    @Id
    private Long id;

    @ManyToOne
    private Category category;

    private String title;

    @Column(name = "slug", unique = true, nullable = false)
    private String slug;

    @Override public Long getId() { return id; }
    @Override public String getSlug() { return slug; }
    @Override public void setSlug(String slug) { this.slug = slug; }
}
// category.name="Tech", title="AI News"
// → slug = "tech-ai-news"
```

> **Cascade updates:** When a `Category` is updated (e.g. its `name` changes), the library automatically re-generates the slugs of all `Article` entities that reference that category — without any additional `@EntityListeners` on `Category`.  
> Deep traversal is supported: `"a.b.c"` resolves `entity.a.b.c` via getter/field reflection.

### 4. Using Composite Unique Constraints (Optional)

For multi-locale entities, you can define composite unique constraints on `(locale, slug)`. The slug generator will automatically detect and respect these constraints:

```java
@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"locale", "slug"})
})
@EntityListeners(SlugListener.class)
public class ProductTranslation implements ISlugSupport<Long> {
    @Id
    private Long id;

    @Column(name = "locale")
    private String locale;

    @SlugField
    @Column(name = "title")
    private String title;

    @Column(name = "slug")
    private String slug;

    // The slug generator will automatically ensure uniqueness within each locale
    // Same slug can exist in different locales
}
```

### 5. Handling Slug Generation

Slugs are automatically generated when entities are created or updated, and they can be customized using the logic provided in the ISlugProvider. The system will also ensure uniqueness by checking against the existing slugs in the database.

## 📘 API Overview

### `@SlugField`

Marks the slug source on a field or class.

```java
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SlugField {
    String[] fields() default {};   // empty = field-level (backward compat)
    String separator() default " "; // joined before slug generation
}
```

| Usage | Behavior |
|---|---|
| `@SlugField` on a field | Uses that field's value as slug source (backward compatible) |
| `@SlugField(fields = {"f1", "f2"})` on a class | Joins `f1` and `f2` values with `separator` |
| `@SlugField(fields = {"rel.field", "f2"})` on a class | Resolves `rel.field` via object graph traversal |
| Class-level + field-level both present | Class-level takes priority |

### `EnableSlug`

Annotation to enable slug generation in your Spring Boot application. You can specify a custom slug generator by providing the `generator` attribute.

```java
public @interface EnableSlug {
    Class<? extends ISlugGenerator> generator() default DefaultSlugGenerator.class;
}
```

### `ISlugSupport`

Interface for entities that support slug generation. Implement this interface in your entity classes to enable slug functionality.

```java
public interface ISlugSupport<ID> {
    ID getId();
    String getSlug();
    void setSlug(String slug);
}
```

### `ISlugGenerator`

The interface for implementing custom slug generators.
```java
public interface ISlugGenerator {
    String generate(String input);
}
```

### `SlugUtil`

Utility class for managing the global slug generator and slug creation.
```java
public class SlugUtil {
    public static void setGenerator(ISlugGenerator generator);
    public static ISlugGenerator getGenerator();
    public static String generate(String input);
}
```

### `SlugRegistry`

A registry to manage the global `ISlugProvider` instance and cascade slug dependency map.
```java
public class SlugRegistry {
    public static void setSlugProvider(ISlugProvider provider);
    public static ISlugProvider getSlugProvider();
    public static void registerCascadeDependent(Class<?> intermediateClass, CascadeDependency dep);
    public static List<CascadeDependency> getCascadeDependents(Class<?> clazz);
}
```

### `ISlugProvider`

An interface for generating slugs based on an entity and a base slug string. Supports composite unique constraints.
```java
public interface ISlugProvider {
    String generateSlug(Object entity, String slug, Map<String, Object> compositeConstraintFields);

    // Default method for backward compatibility
    default String generateSlug(Object entity, String slug) {
        return generateSlug(entity, slug, Collections.emptyMap());
    }
}
```

### `SlugListener`

A JPA entity listener that automatically generates slugs for entities before they are persisted or updated. Add via `@EntityListeners(SlugListener.class)` on your entity.

```java
public class SlugListener {
    @PrePersist
    @PreUpdate
    public void handle(Object entity);
}
```

### `SlugCascadeListener`

A Hibernate `PostUpdateEventListener` registered automatically at startup by `SlugAutoConfiguration`. Refreshes slugs of dependent entities when an intermediate entity (e.g. `Category`) is updated. No manual configuration required.

### `SlugOperationException`

Custom exception thrown when errors occur during slug generation.
```java
public class SlugOperationException extends RuntimeException {
    public SlugOperationException(String message);
    public SlugOperationException(String message, Throwable cause);
}
```

---

## 🛠 Requirements

- Java 17+
- Spring Boot 3.x
- Spring Data JPA (Hibernate as JPA provider)

---

## 🔁 Other Implementations

[Spring Boot JPA Slug (Kotlin Maven Package)](https://github.com/mewebstudio/spring-boot-jpa-slug-kotlin)

## 🤝 Contributing
I welcome contributions! Please fork this repository, make your changes, and submit a pull request. If you're fixing a bug, please provide steps to reproduce the issue and the expected behavior.

## 📄 License
This project is licensed under the MIT License - see the LICENSE file for details.

## 💡 Example Implementations

[Spring Boot JPA Slug - Java Implementation](https://github.com/mewebstudio/spring-boot-jpa-slug-java-impl)

[Spring Boot JPA Slug - Kotlin Implementation](https://github.com/mewebstudio/spring-boot-jpa-slug-kotlin-impl)
