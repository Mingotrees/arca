package com.popman.arca.dto.v1.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** Wire contract shared by MVC and security-filter JSON errors. */
public record ApiErrorResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String timestamp,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String error,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {}
