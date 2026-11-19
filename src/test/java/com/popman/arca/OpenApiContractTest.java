package com.popman.arca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class OpenApiContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void checkedInDeletionContractMatchesGeneratedContractAndLegacyOperationsRemain() throws Exception {
        JsonNode generated = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        if (Boolean.getBoolean("arca.openapi.export")) {
            Files.writeString(Path.of("target/openapi-generated.json"), generated.toPrettyString());
            Files.writeString(Path.of("target/openapi-generated.yaml"), new Yaml().dump(mapper.convertValue(generated, java.util.Map.class)));
        }
        JsonNode checked;
        try (var stream = Files.newInputStream(Path.of("openapi.yaml"))) {
            checked = mapper.valueToTree(new Yaml().load(stream));
        }
        JsonNode deletion = generated.at("/paths/~1api~1v1~1posts~1{rowId}/delete");
        for (String path : new String[]{"/api/v1/admin/announcements", "/api/v1/admin/announcements/{id}", "/api/v1/announcements/active", "/api/v1/posts/trending"}) {
            assertFalse(generated.path("paths").path(path).isMissingNode());
            assertEquals(generated.path("paths").path(path), checked.path("paths").path(path), "Homepage operation drift: " + path);
        }
        for (String schema : new String[]{"AnnouncementRequest", "AnnouncementResponse", "AdminAnnouncementResponse", "AnnouncementPage", "TrendingResponse", "TrendingPostResponse"})
            assertEquals(generated.path("components").path("schemas").path(schema), checked.path("components").path("schemas").path(schema), "Homepage schema drift: " + schema);
        assertEquals(Set.of("204", "401", "403", "404"), names(deletion.path("responses")));
        assertFalse(deletion.path("responses").path("204").has("content"));
        assertEquals(checked.at("/paths/~1api~1v1~1posts~1{rowId}/delete"), deletion);
        assertFalse(generated.at("/components/schemas/PostResponse/properties").has("is_deleted"));
        assertEquals(names(checked.at("/components/schemas/PostResponse/properties")),
                names(generated.at("/components/schemas/PostResponse/properties")));
        for (String schema : new String[]{"PostRequest", "PostUpdateRequest", "VoteRequest", "VaultRequest", "EditVaultLabelRequest", "PasswordRecoveryRequest", "PasswordResetRequest", "RefreshTokenRequest"}) {
            assertEquals(checked.path("components").path("schemas").path(schema),
                    generated.path("components").path("schemas").path(schema), "Request contract drift: " + schema);
        }
        assertEquals(generated.path("paths").path("/api/v1/posts/search"), checked.path("paths").path("/api/v1/posts/search"),
                "Search operation must match the generated contract");
        assertEquals(generated.at("/components/schemas/PostSearchResponse"), checked.at("/components/schemas/PostSearchResponse"));
        assertEquals(generated.at("/components/schemas/ApiErrorResponse"), checked.at("/components/schemas/ApiErrorResponse"));
        assertEquals(Set.of("content", "page", "size", "total_elements", "total_pages", "sort"),
                names(generated.at("/components/schemas/PostSearchResponse/properties")));
        for (String path : new String[]{"/api/v1/auth/password-recovery", "/api/v1/auth/password-reset"}) {
            assertEquals(generated.path("paths").path(path), checked.path("paths").path(path), "Recovery contract drift: " + path);
            assertTrue(generated.path("paths").path(path).path("post").path("security").isArray());
            assertEquals(0, generated.path("paths").path(path).path("post").path("security").size(), "Recovery operations must explicitly document anonymous access");
        }
        for (String path : new String[]{"/api/v1/user/{userId}/stats", "/api/v1/subject/{subjectId}/stats"}) {
            assertEquals(generated.path("paths").path(path), checked.path("paths").path(path), "Statistics contract drift: " + path);
        }
        for (String schema : new String[]{"UserResponse", "UserSummaryResponse", "SubjectResponse", "SubjectRequest", "UserStatsResponse", "SubjectStatsResponse"}) {
            assertEquals(generated.path("components").path("schemas").path(schema), checked.path("components").path("schemas").path(schema), "Profile schema drift: " + schema);
        }
        assertFalse(generated.at("/components/schemas/User/properties").has("timestamps_approximate"));
        assertFalse(generated.at("/components/schemas/User/properties").has("timestampsApproximate"));
        assertEquals(generated.path("paths").path("/api/v1/user/{userId}/cover-picture"),
                checked.path("paths").path("/api/v1/user/{userId}/cover-picture"));
        assertEquals(0, generated.path("paths").path("/api/v1/user/{userId}/cover-picture").path("get").path("security").size());
        assertEquals(generated.at("/components/schemas/CoverPictureResponse"), checked.at("/components/schemas/CoverPictureResponse"));
        assertFalse(generated.path("paths").path("/api/v1/admin/audit-events").isMissingNode());
        assertEquals(generated.path("paths").path("/api/v1/admin/audit-events"), checked.path("paths").path("/api/v1/admin/audit-events"));
        for (String schema : new String[]{"AuditEventResponse", "AuditEventPage"})
            assertEquals(generated.path("components").path("schemas").path(schema), checked.path("components").path("schemas").path(schema), "Audit schema drift: " + schema);
        assertFalse(generated.path("paths").path("/api/v1/admin/dashboard").isMissingNode());
        assertEquals(generated.path("paths").path("/api/v1/admin/dashboard"), checked.path("paths").path("/api/v1/admin/dashboard"));
        for (String schema : new String[]{"AdminDashboardResponse", "Users", "Posts", "Moderation", "Engagement"}) {
            assertEquals(generated.path("components").path("schemas").path(schema), checked.path("components").path("schemas").path(schema), "Dashboard schema drift: " + schema);
        }
        for (String schema : new String[]{"PostLinkRequest", "PostLinkResponse"}) {
            assertEquals(generated.path("components").path("schemas").path(schema), checked.path("components").path("schemas").path(schema), "Link schema drift: " + schema);
        }
        for (String schema : new String[]{"PostLinkRequest", "PostLinkResponse", "PostRequest", "PostUpdateRequest"}) {
            String property = schema.startsWith("PostLink") ? "label" : "links";
            JsonNode type = generated.path("components").path("schemas").path(schema).path("properties").path(property).path("type");
            assertTrue(type.isArray(), schema + "." + property + " must support null");
            assertTrue(mapper.convertValue(type, java.util.List.class).contains("null"));
        }

        var paths = checked.path("paths").fields();
        while (paths.hasNext()) {
            var path = paths.next();
            var operations = path.getValue().fields();
            while (operations.hasNext()) {
                var operation = operations.next();
                if (!Set.of("get", "post", "put", "patch", "delete", "options", "head").contains(operation.getKey())) continue;
                assertEquals(operation.getValue().path("operationId"),
                        generated.path("paths").path(path.getKey()).path(operation.getKey()).path("operationId"),
                        path.getKey() + " " + operation.getKey());
                if (path.getKey().startsWith("/api/v1/files") || path.getKey().startsWith("/api/v1/votes")
                        || path.getKey().startsWith("/api/v1/vaults") || path.getKey().equals("/api/v1/posts/{rowId}")
                        || path.getKey().equals("/api/v1/posts/{postId}/approve")
                        || path.getKey().equals("/api/v1/user/{userId}/profile-picture")
                        || path.getKey().equals("/api/v1/posts/history/{postId}")) {
                    assertEquals(operation.getValue(), generated.path("paths").path(path.getKey()).path(operation.getKey()),
                            "Related-resource contract drift: " + path.getKey() + " " + operation.getKey());
                }
            }
        }
    }

    private Set<String> names(JsonNode node) {
        Set<String> result = new HashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }
}
