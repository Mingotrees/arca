package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.post.TrendingResponse;
import com.popman.arca.dto.v1.common.ApiErrorResponse;
import com.popman.arca.service.TrendingService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/posts/trending")
@SecurityRequirement(name = "bearerAuth")
public class TrendingController {
    private final TrendingService trending;
    public TrendingController(TrendingService trending) { this.trending = trending; }
    @GetMapping
    @Operation(summary = "Read trending post summaries", description = "Authenticated user/admin. TRENDING_ENABLED defaultsfalse; disabled404. Window default7d, supported24h/7d/30d, limit default10/max50. "
            + "Highest approved/latest/non-deleted version per logical post, known approved_at within [calculated_at-window,calculated_at). "
            + "Counts currently held votes on that version with changed_at in same UTC window; legacy-null/future dates excluded. "
            + "Score=(1+recent_upvotes)/((1+recent_downvotes)*POWER(2+age_hours,1.5)), age since UTC approval. Order score DESC,approved_at DESC,id DESC. "
            + "No score, private body, files or entity graphs returned; no view tracking, URL fetching, cache or search-sort alias. Vote toggles cannot accumulate duplicates; not a complete anti-abuse system.")
    @ApiResponse(responseCode = "200", description = "Trending summaries", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TrendingResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid window or limit", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "User/admin role required", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Module disabled", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public TrendingResponse trendingPosts(@Parameter(schema = @Schema(allowableValues = {"24h","7d","30d"})) @RequestParam(required = false) String window,
            @Parameter(schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "50")) @RequestParam(required = false) Integer limit) {
        return trending.trending(window, limit);
    }
}
