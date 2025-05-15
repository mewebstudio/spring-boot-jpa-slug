# Slug Generator for Spring Boot

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![Maven badge](https://maven-badges.herokuapp.com/maven-central/com.mewebstudio/spring-boot-jpa-slug/badge.svg?style=flat)](https://central.sonatype.com/artifact/com.mewebstudio/spring-boot-jpa-slug)
[![javadoc](https://javadoc.io/badge2/com.mewebstudio/spring-boot-jpa-slug/javadoc.svg)](https://javadoc.io/doc/com.mewebstudio/spring-boot-jpa-slug)

A simple and customizable slug generation solution for Spring Boot applications, designed to easily create and manage slugs for entities. This package integrates with JPA entities and provides a flexible way to generate unique slugs for your models.

---

## ✅ Features

- **Customizable Slug Generation**: Provides an interface for defining custom slug generation logic using `ISlugGenerator`.
- **Automatic Slug Assignment**: Automatically generates and assigns slugs to entities upon creation or update.
- **Unique Slug Enforcement**: Ensures that slugs are unique across entities, retrying with suffixes if needed.
- **Integration with Spring Boot**: Easily integrates with Spring Boot using `@EnableSlug` and `SlugRegistry`.

## 📥 Installation

#### for Maven users

Add the following dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>com.mewebstudio</groupId>
  <artifactId>spring-boot-jpa-slug</artifactId>
  <version>0.1.3</version>
</dependency>
```

#### for Gradle users
```groovy
implementation 'com.mewebstudio:spring-boot-jpa-slug:0.1.3'
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

```java
import com.mewebstudio.springboot.jpa.slug.ISlugSupport;
import com.mewebstudio.springboot.jpa.slug.SlugField;
import com.mewebstudio.springboot.jpa.slug.SlugListener;

import javax.persistence.Entity;

@Entity
@EntityListeners(SlugListener.class)
public class MyEntity implements ISlugSupport<Long> {
    @Id
    private Long id;

    @SlugField
    private String title;

    private String slug;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public String getSlug() {
        return slug;
    }

    @Override
    public void setSlug(String slug) {
        this.slug = slug;
    }

    // Getters and setters for other fields
}
```

### 4. Handling Slug Generation

Slugs are automatically generated when entities are created or updated, and they can be customized using the logic provided in the ISlugProvider. The system will also ensure uniqueness by checking against the existing slugs in the database.

## 📘 API Overview

`EnableSlug`

Annotation to enable slug generation in your Spring Boot application. You can specify a custom slug generator by providing the `generator` attribute.

```java
public @interface EnableSlug {
    Class<? extends ISlugGenerator> generator() default DefaultSlugGenerator.class;
}
```

`ISlugSupport`

Interface for entities that support slug generation. Implement this interface in your entity classes to enable slug functionality.

```java
public interface ISlugSupport<ID> {
    ID getId();

    String getSlug();

    void setSlug(String slug);
}
```

`ISlugGenerator`

The interface for implementing custom slug generators.
```java
public interface ISlugGenerator {
    String generate(String input);
}
```

`SlugUtil`

Utility class for managing the global slug generator and slug creation.
```java
public class SlugUtil {
    public static void setGenerator(ISlugGenerator generator);

    public static ISlugGenerator getGenerator();

    public static String slugify(String input);
}
```

`SlugRegistry`

A registry to manage the global `ISlugProvider` instance.
```java
public class SlugRegistry {
    public static void setSlugProvider(ISlugProvider provider);

    public static ISlugProvider getSlugProvider();
}
```

`ISlugProvider`

An interface for generating slugs based on an entity and a base slug string.
```java
public interface ISlugProvider {
    String generateSlug(Object entity, String slug);
}
```

`SlugListener`

A listener that automatically generates slugs for entities before they are persisted or updated in the database.
```java
public class SlugListener {
    @PrePersist
    @PreUpdate
    public void handle(Object entity);
}
```

`SlugOperationException`

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
- Spring Data JPA

---

## 🔁 Other Implementations

[Spring Boot JPA Slug (Kotlin Maven Package)](https://github.com/mewebstudio/spring-boot-jpa-slug-kotlin)

## 🤝 Contributing
We welcome contributions! Please fork this repository, make your changes, and submit a pull request. If you're fixing a bug, please provide steps to reproduce the issue and the expected behavior.

## 📄 License
This project is licensed under the MIT License - see the LICENSE file for details.

## 💡 Example Implementations

[Spring Boot JPA Slug - Java Implementation](https://github.com/mewebstudio/spring-boot-jpa-slug-java-impl)

[Spring Boot JPA Slug - Kotlin Implementation](https://github.com/mewebstudio/spring-boot-jpa-slug-kotlin-impl)
