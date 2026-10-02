package com.malyszczuk.ingredients_retriever.exception;

import com.malyszczuk.ingredients_retriever.extraction.RecipeExtractionException;
import com.malyszczuk.ingredients_retriever.extraction.file.UnsupportedFileTypeException;
import com.malyszczuk.ingredients_retriever.agent.OllamaResponseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
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

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<String> handleUnsupportedFileType(UnsupportedFileTypeException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(exception.getMessage());
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<String> handleOllamaUnreachable(ResourceAccessException exception) {
        if (isReadTimeout(exception)) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body("The language model (Ollama) took too long to answer. Please try again.");
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("The language model (Ollama) is not reachable. Make sure it is running.");
    }

    @ExceptionHandler(OllamaResponseException.class)
    public ResponseEntity<String> handleOllamaEmptyResponse(OllamaResponseException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(exception.getMessage());
    }

    // A connect timeout means nothing is listening (not reachable); only a timeout while waiting for the answer
    // means the model is just slow.
    private boolean isReadTimeout(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpConnectTimeoutException) {
                return false;
            }
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<String> handleOllamaError(RestClientException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("The language model (Ollama) returned an error: " + exception.getMessage());
    }
}
