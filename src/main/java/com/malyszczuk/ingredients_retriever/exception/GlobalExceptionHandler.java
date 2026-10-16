package com.malyszczuk.ingredients_retriever.exception;

import com.malyszczuk.ingredients_retriever.auth.EmailAlreadyRegisteredException;
import com.malyszczuk.ingredients_retriever.auth.InvalidCredentialsException;
import com.malyszczuk.ingredients_retriever.auth.TooManyAttemptsException;
import com.malyszczuk.ingredients_retriever.auth.WrongPasswordException;
import com.malyszczuk.ingredients_retriever.extraction.RecipeExtractionException;
import com.malyszczuk.ingredients_retriever.extraction.file.UnsupportedFileTypeException;
import com.malyszczuk.ingredients_retriever.agent.OllamaResponseException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

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

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<String> handleInvalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(exception.getMessage());
    }

    @ExceptionHandler(WrongPasswordException.class)
    public ResponseEntity<String> handleWrongPassword(WrongPasswordException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<String> handleTooManyAttempts(TooManyAttemptsException exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(exception.retryAfterSeconds()))
                .body(exception.getMessage());
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<String> handleEmailAlreadyRegistered(EmailAlreadyRegisteredException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }

    // Plain text like every other error, naming the fields that were wrong. It never echoes the rejected values
    // (they may be passwords), and replaces Spring's default JSON error body, which can include a stack trace.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(MethodArgumentNotValidException exception) {
        String problems = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problems.isEmpty() ? "The request is not valid" : problems);
    }

    // Two requests colliding in the database (the same new item added at the same moment, or a deadlock between
    // overlapping recipe imports). The database stays consistent; the loser can simply be retried.
    @ExceptionHandler({DataIntegrityViolationException.class, PessimisticLockingFailureException.class})
    public ResponseEntity<String> handleConcurrentChange(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("That change conflicts with another change made at the same moment. Please try again.");
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
