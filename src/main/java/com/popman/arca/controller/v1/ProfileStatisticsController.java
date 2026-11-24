package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.common.ApiErrorResponse;
import com.popman.arca.dto.v1.user.UserStatsResponse;
import com.popman.arca.dto.v1.subject.SubjectStatsResponse;
import com.popman.arca.service.ProfileStatisticsService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "400", description = "Invalid profile ID", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "Profile access denied", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "Profile missing or user deleted", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
public class ProfileStatisticsController {
    private final ProfileStatisticsService statistics;
    public ProfileStatisticsController(ProfileStatisticsService statistics) { this.statistics = statistics; }

    @GetMapping("/user/{userId}/stats")
    @Operation(summary = "Read user profile statistics", description = "Same public aggregate set for all authenticated roles: approved/latest/non-deleted version rows. "
            + "Post count deduplicates logical post_id; votes are counted on eligible version row IDs, including retained votes by deleted voters. Deleted user profiles return 404.",
            parameters = @Parameter(name = "X-Request-ID", in = ParameterIn.HEADER, description = "Optional safe correlation ID; invalid values are replaced by a UUID", schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "200", description = "User aggregate statistics", headers = @Header(name = "X-Request-ID", description = "Safe request correlation ID", schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserStatsResponse.class)))
    public UserStatsResponse userStats(@Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable Long userId) {
        return statistics.userStats(userId);
    }

    @GetMapping("/subject/{subjectId}/stats")
    @Operation(summary = "Read subject profile statistics", description = "Counts distinct logical post_id among linked approved/latest/non-deleted rows. "
            + "last_approved_post_at is MAX of eligible Post.updated_at, using its existing stored clock; null when empty. "
            + "Active posts by deleted authors remain counted, consistent with existing post visibility. Owners/admins receive no private moderation counts.",
            parameters = @Parameter(name = "X-Request-ID", in = ParameterIn.HEADER, description = "Optional safe correlation ID; invalid values are replaced by a UUID", schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "200", description = "Subject aggregate statistics", headers = @Header(name = "X-Request-ID", description = "Safe request correlation ID", schema = @Schema(type = "string")),
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SubjectStatsResponse.class)))
    public SubjectStatsResponse subjectStats(@Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) @PathVariable Long subjectId) {
        return statistics.subjectStats(subjectId);
    }
}
