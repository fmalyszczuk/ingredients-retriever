package com.malyszczuk.ingredients_retriever.agent;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides, in code, whether a user's message is a plain "yes" to a destructive action. This must not be left to
 * the model: in testing, the model called the clear tool again after the user answered "No, never mind".
 * Deliberately strict: a negation or a qualifier ("yes, but only the milk") is not a confirmation, so the worst
 * case of a wrong guess is being asked again.
 */
final class ConfirmationReply {

    private static final int MAX_WORDS = 6;

    private static final Set<String> AFFIRMATIVE_WORDS = Set.of(
            "yes", "yep", "yeah", "yup", "sure", "ok", "okay", "confirm", "confirmed", "proceed",
            "absolutely", "definitely", "affirmative");

    private static final List<String> AFFIRMATIVE_PHRASES = List.of("go ahead", "do it");

    private static final Set<String> BLOCKING_WORDS = Set.of(
            "no", "nope", "nah", "not", "never", "cancel", "stop", "wait", "but", "only", "except",
            "just", "instead", "actually", "also", "and", "first", "keep", "unless", "maybe");

    private ConfirmationReply() {
    }

    static boolean isConfirmation(String message) {
        if (message == null) {
            return false;
        }

        String lower = message.toLowerCase(Locale.ROOT).replace('’', '\'');
        if (lower.contains("n't")) {
            return false; // don't, can't, won't, ...
        }

        String normalized = lower.replaceAll("[^a-z ]", " ").trim();
        String[] words = normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
        if (words.length == 0 || words.length > MAX_WORDS || Arrays.stream(words).anyMatch(BLOCKING_WORDS::contains)) {
            return false;
        }

        String sentence = String.join(" ", words);
        return Arrays.stream(words).anyMatch(AFFIRMATIVE_WORDS::contains)
                || AFFIRMATIVE_PHRASES.stream().anyMatch(sentence::contains);
    }
}
