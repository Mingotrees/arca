package com.popman.arca.dto.v1.post;

import io.swagger.v3.oas.annotations.media.Schema;

public record PostLinkRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 2048, description = "Absolute HTTP(S) display URL; no credentials, controls or whitespace") String url,
        @Schema(types = {"string", "null"}, maxLength = 200, description = "Optional plain-text label; blank becomes null") String label) {}
