package com.mewebstudio.springboot.jpa.slug;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SlugUtilTest {

    private final ISlugGenerator slugGenerator = new DefaultSlugGenerator();

    @BeforeEach
    void setUp() {
        // Set the generator before each test
        SlugUtil.setGenerator(slugGenerator);
    }

    @Test
    void testSetGenerator_withNullGenerator() {
        // Test that setting a null generator throws an exception
        assertThrows(SlugOperationException.class, () -> SlugUtil.setGenerator(null));
    }

    @Test
    void testGenerate_withValidInput() {
        // Test that generate method works with valid input
        String input = "Hello World!";
        String expectedSlug = "hello-world";

        String generatedSlug = SlugUtil.generate(input);

        assertNotNull(generatedSlug);
        assertEquals(expectedSlug, generatedSlug);
    }

    @Test
    void testGenerate_withNullInput() {
        // Test that generate method works with null input
        String input = null;

        String generatedSlug = SlugUtil.generate(input);

        assertNull(generatedSlug);
    }

    @Test
    void testGenerate_withEmptyInput() {
        // Test that generate method works with empty input
        String input = "";

        String generatedSlug = SlugUtil.generate(input);

        assertEquals("", generatedSlug);
    }

    @Test
    void testGenerate_withInputContainingSpecialCharacters() {
        // Test generate with special characters
        String input = "This is a #slug@test!";
        String expectedSlug = "this-is-a-slugtest";  // Adjusted expected slug to reflect special character removal

        String generatedSlug = SlugUtil.generate(input);

        assertNotNull(generatedSlug);
        assertEquals(expectedSlug, generatedSlug);
    }

    @Test
    void testGenerate_withInputContainingSpaces() {
        // Test generate with input containing multiple spaces
        String input = "This   is   a   test";

        String generatedSlug = SlugUtil.generate(input);

        assertNotNull(generatedSlug);
        assertEquals("this-is-a-test", generatedSlug);
    }
}
