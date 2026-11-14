package com.popman.arca.exceptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared JSON envelope for MVC and security-filter errors. */
public final class ApiErrors {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private ApiErrors() {}

    public static Map<String, Object> body(int status, String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        return body;
    }

    public static void write(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        MAPPER.writeValue(response.getWriter(), body(status, error, message));
    }
}
