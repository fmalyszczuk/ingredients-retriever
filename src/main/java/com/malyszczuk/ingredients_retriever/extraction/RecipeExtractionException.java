package com.malyszczuk.ingredients_retriever.extraction;

public class RecipeExtractionException extends RuntimeException {

    public RecipeExtractionException(String message) {
        super(message);
    }

    public RecipeExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
