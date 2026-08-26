package com.roberthevesi.cryptoshred_health.util;

import java.security.SecureRandom;
import java.util.List;

/**
 * Utility to generate readable, high-entropy temporary passwords for newly provisioned patient accounts.
 * Format: <Word>-<4 random digits><Special Symbol><Word> (e.g., Care-8429!Blue)
 */
public final class TemporaryPasswordGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final List<String> PREFIX_WORDS = List.of(
            "Care", "Health", "Pulse", "Cure", "Apex",
            "Nova", "Safe", "Life", "Shield", "Vital",
            "Hope", "Glow", "Echo", "Wave", "Beam",
            "True", "Bold", "Pure", "Star", "Mint"
    );

    private static final List<String> SUFFIX_WORDS = List.of(
            "Blue", "Green", "Teal", "Gold", "Ruby",
            "Pine", "Sage", "Sky", "Lake", "Rose",
            "Peak", "Dawn", "Breeze", "Haven", "Fern",
            "Coral", "Calm", "Leaf", "Stone", "Moon"
    );

    private static final char[] SPECIAL_SYMBOLS = {'!', '@', '#', '$', '%', '&', '*'};

    private TemporaryPasswordGenerator() {
        // Prevent instantiation
    }

    /**
     * Generates a readable, secure temporary password meeting standard complexity requirements.
     *
     * @return Generated password string
     */
    public static String generate() {
        String prefix = PREFIX_WORDS.get(RANDOM.nextInt(PREFIX_WORDS.size()));
        String suffix = SUFFIX_WORDS.get(RANDOM.nextInt(SUFFIX_WORDS.size()));
        int digits = 1000 + RANDOM.nextInt(9000); // 4-digit number between 1000 and 9999
        char symbol = SPECIAL_SYMBOLS[RANDOM.nextInt(SPECIAL_SYMBOLS.length)];

        return prefix + "-" + digits + symbol + suffix;
    }
}
