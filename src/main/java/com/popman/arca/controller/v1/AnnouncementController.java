package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.announcement.AnnouncementResponse;
import com.popman.arca.dto.v1.common.ApiErrorResponse;
import com.popman.arca.service.AnnouncementService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/announcements")
@SecurityRequirement(name = "bearerAuth")
public class AnnouncementController {
    private final AnnouncementService announcements;
    public AnnouncementController(AnnouncementService announcements) { this.announcements = announcements; }
    @GetMapping("/active")
    @Operation(summary = "Read active global announcements", description = "Authenticated users/admins, global audience. Active/non-deleted and starts_at<=now (or null), ends_at>now (or null), UTC. Priority DESC,COALESCE(starts_at,created_at) DESC,id DESC. Limit default10/max50. Plain-text fields require safe client rendering; URLs are never fetched. ANNOUNCEMENTS_ENABLED defaultsfalse; disabled404. No cache or scheduler.")
    @ApiResponse(responseCode = "200", description = "Active announcements", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = AnnouncementResponse.class))))
    @ApiResponse(responseCode = "400", description = "Invalid limit", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "User/admin role required", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Module disabled", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public List<AnnouncementResponse> activeAnnouncements(
            @Parameter(schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "50")) @RequestParam(required = false) Integer limit) {
        return announcements.active(limit);
    }
}
