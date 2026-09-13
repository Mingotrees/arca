package com.popman.arca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popman.arca.dto.v1.user.UserResponse;
import com.popman.arca.dto.v1.user.UserSummaryResponse;
import com.popman.arca.entity.User;
import com.popman.arca.entity.UserPrincipal;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void responseUsesSnakeCaseAndOmitsSensitiveFields() throws Exception {
        User user = new User(3L, "First", "Last", "secret", "user@example.com",
                "Course", "Department", "Bio", "profile-pictures/user.png");
        user.setRoles(Set.of("ROLE_USER", "ROLE_ADMIN"));
        user.setDeleted(true);
        user.setDeletedAt(LocalDateTime.now());

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(new UserResponse(user)));

        assertEquals("First", json.get("first_name").asText());
        assertEquals("Last", json.get("last_name").asText());
        assertEquals("profile-pictures/user.png", json.get("profile_picture").asText());
        assertEquals("ROLE_ADMIN", json.get("roles").get(0).asText());
        assertFalse(json.has("password"));
        assertFalse(json.has("deleted"));
        assertFalse(json.has("deleted_at"));
    }

    @Test
    void principalRecognizesPrefixedAdminRole() {
        User user = new User();
        user.setRoles(Set.of("ROLE_USER", "ROLE_ADMIN"));

        assertTrue(new UserPrincipal(user).isAdmin());
    }

    @Test
    void searchSummaryOmitsEmailAndRoles() throws Exception {
        User user = new User(3L, "First", "Last", "secret", "user@example.com",
                "Course", "Department", "Bio", "profile-pictures/user.png");
        user.setRoles(Set.of("ROLE_ADMIN"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsBytes(new UserSummaryResponse(user)));

        assertEquals("First", json.get("first_name").asText());
        assertFalse(json.has("email"));
        assertFalse(json.has("roles"));
        assertFalse(json.has("password"));
    }
}
