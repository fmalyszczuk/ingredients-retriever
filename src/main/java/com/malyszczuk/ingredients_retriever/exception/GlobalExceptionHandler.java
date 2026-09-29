package com.malyszczuk.ingredients_retriever.exception;

import com.malyszczuk.ingredients_retriever.extraction.RecipeExtractionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @ExceptionHandler(RecipeExtractionException.class)
    public ResponseEntity<String> handleRecipeExtraction(RecipeExtractionException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(exception.getMessage());
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<String> handleOllamaUnreachable(ResourceAccessException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("The language model (Ollama) is not reachable. Make sure it is running.");
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<String> handleOllamaError(RestClientException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("The language model (Ollama) returned an error: " + exception.getMessage());
    }
}
