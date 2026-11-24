package com.popman.arca.dto.v1.post;

import io.swagger.v3.oas.annotations.media.Schema;

public record PostLinkResponse(String url, @Schema(types = {"string", "null"}) String label,
                               @Schema(minimum = "0", maximum = "9", description = "Zero-based submitted order within this version") int position) {}
