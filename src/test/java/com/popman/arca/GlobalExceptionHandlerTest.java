package com.popman.arca;

import com.popman.arca.exceptions.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void preservesExplicitResponseStatus() {
        var response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Not yours"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Not yours", body(response).get("message"));
    }

    @Test
    void mapsMissingResourcesToNotFound() {
        var response = handler.handleNotFound(new NoSuchElementException("Missing"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Missing", body(response).get("message"));
    }

    @Test
    void mapsStateConflictsToConflict() {
        var response = handler.handleConflict(new IllegalStateException("Already approved"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void mapsUnexpectedErrorsToInternalServerErrorWithoutLeakingDetails() {
        var response = handler.handleAllExceptions(new RuntimeException("database password"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred", body(response).get("message"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(org.springframework.http.ResponseEntity<Object> response) {
        return (Map<String, Object>) response.getBody();
    }
}
