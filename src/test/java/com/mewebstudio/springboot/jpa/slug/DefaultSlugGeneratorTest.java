package com.mewebstudio.springboot.jpa.slug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultSlugGeneratorTest {

    private final ISlugGenerator slugGenerator = new DefaultSlugGenerator();

    @Test
    void testGenerate_withValidInput() {
        // Test with valid input
        String input = "Hello World! This is a test.";
        String expectedSlug = "hello-world-this-is-a-test";

        String generatedSlug = slugGenerator.generate(input);

        assertNotNull(generatedSlug);
        assertEquals(expectedSlug, generatedSlug);
    }

    @Test
    void testGenerate_withNullInput() {
        // Test with null input
        String input = null;

        String generatedSlug = slugGenerator.generate(input);

        assertNull(generatedSlug);
    }

    @Test
    void testGenerate_withEmptyInput() {
        // Test with empty input
        String input = "";

        String generatedSlug = slugGenerator.generate(input);

        assertEquals("", generatedSlug);
    }

    @Test
    void testGenerate_withInputContainingSpecialCharacters() {
        // Test with input containing special characters
        String input = "This is #1! @Slug-test.";
        String expectedSlug = "this-is-1-slug-test";

        String generatedSlug = slugGenerator.generate(input);

        assertNotNull(generatedSlug);
        assertEquals(expectedSlug, generatedSlug);
    }

    @Test
    void testGenerate_withInputContainingMultipleSpaces() {
        // Test with input containing multiple spaces
        String input = "This   is  a   test";

        String generatedSlug = slugGenerator.generate(input);

        assertNotNull(generatedSlug);
        assertEquals("this-is-a-test", generatedSlug);
    }

    @Test
    void testGenerate_withInputContainingMultipleHyphens() {
        // Test with input containing multiple hyphens
        String input = "This---is---a----test";

        String generatedSlug = slugGenerator.generate(input);

        assertNotNull(generatedSlug);
        assertEquals("this-is-a-test", generatedSlug);
    }
}
