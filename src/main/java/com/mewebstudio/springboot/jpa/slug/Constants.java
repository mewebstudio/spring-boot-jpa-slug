package com.mewebstudio.springboot.jpa.slug;

import java.util.regex.Pattern;

/**
 * Package-level constants shared across slug generation components.
 */
public final class Constants {
    private Constants() {
    }

    /**
     * Matches valid Java/JPA identifiers — ensures entity and field names are safe for JPQL interpolation.
     */
    public static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
}
