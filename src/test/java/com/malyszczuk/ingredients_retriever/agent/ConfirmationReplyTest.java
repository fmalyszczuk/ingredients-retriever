package com.malyszczuk.ingredients_retriever.agent;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmationReplyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "yes", "Yes.", "YES!", "yep", "yeah", "sure", "ok", "okay", "Yes, I'm sure.", "yes please",
            "go ahead", "do it", "Yes, clear it", "confirm", "I'm sure, go ahead", "Yes I'm sure"
    })
    void isConfirmation_acceptsAPlainYes(String message) {
        assertTrue(ConfirmationReply.isConfirmation(message), message);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "no", "No, never mind.", "nope", "don't", "Don't do it", "do not", "cancel", "stop",
            "not sure", "I'm not sure", "wait", "yes but only the milk", "yes, and add eggs",
            "actually, keep the eggs", "maybe", "yes or no?", "hmm", "what is on my list?",
            "yes, but first show me what is on the list please"
    })
    void isConfirmation_rejectsNegationsQualifiersAndOtherMessages(String message) {
        assertFalse(ConfirmationReply.isConfirmation(message), message);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "...", "!!!"})
    void isConfirmation_rejectsEmptyMessages(String message) {
        assertFalse(ConfirmationReply.isConfirmation(message));
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes I want to clear everything in my whole shopping list now"})
    void isConfirmation_rejectsLongMessages(String message) {
        assertFalse(ConfirmationReply.isConfirmation(message));
    }
}
