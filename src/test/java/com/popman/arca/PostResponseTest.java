package com.popman.arca;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.popman.arca.dto.v1.post.PostResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostResponseTest {
    @Test
    void serializesOnlyCanonicalLatestVersionProperty() {
        PostResponse response = new PostResponse();
        response.setIsLatestVersion(true);

        JsonNode json = new ObjectMapper().valueToTree(response);

        assertTrue(json.has("is_latest_version"));
        assertFalse(json.has("latestVersion"));
        assertFalse(json.has("isLatestVersion"));
    }
}
