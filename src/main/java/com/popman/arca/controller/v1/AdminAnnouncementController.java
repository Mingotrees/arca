package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.announcement.*;
import com.popman.arca.dto.v1.common.ApiErrorResponse;
import com.popman.arca.service.AnnouncementService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController
@RequestMapping("/api/v1/admin/announcements")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "400", description = "Invalid announcement or bounds", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "Admin required", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
@ApiResponse(responseCode = "404", description = "Module disabled or announcement missing/deleted", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
public class AdminAnnouncementController {
    private final AnnouncementService announcements;
    public AdminAnnouncementController(AnnouncementService announcements) { this.announcements = announcements; }
    @PostMapping
    @Operation(summary = "Create global announcement", description = "Admin only. Requires ANNOUNCEMENTS_ENABLED, default false. Title max128, plain-text body max4096 or validated HTTP(S) URL max2048 required. Priority defaults0, active defaultsfalse. UTC starts_at inclusive/ends_at exclusive, nullable bounds, microsecond precision, years1000–9999. No URL fetching. Creation and redacted audit event commit atomically; actor comes from authenticated account.")
    @ApiResponse(responseCode = "201", description = "Announcement created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminAnnouncementResponse.class)))
    public ResponseEntity<AdminAnnouncementResponse> createAnnouncement(@Valid @RequestBody AnnouncementRequest request) {
        var response = announcements.create(request);
        return ResponseEntity.created(URI.create("/api/v1/admin/announcements/" + response.id())).body(response);
    }
    @GetMapping
    @Operation(summary = "List announcements for administration", description = "Includes active, inactive, expired and future global announcements; excludes deleted. Stable created_at DESC,id DESC. Page defaults0/max10000, size defaults20/max100. Requires ANNOUNCEMENTS_ENABLED.")
    @ApiResponse(responseCode = "200", description = "Admin announcement page", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AnnouncementPage.class)))
    public AnnouncementPage listAnnouncements(
            @Parameter(schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "10000")) @RequestParam(required = false) Integer page,
            @Parameter(schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100")) @RequestParam(required = false) Integer size) {
        return announcements.page(page, size);
    }
    @GetMapping("/{id}")
    @Operation(summary = "Read announcement for administration")
    @ApiResponse(responseCode = "200", description = "Admin announcement", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminAnnouncementResponse.class)))
    public AdminAnnouncementResponse getAnnouncement(@PathVariable long id) { return announcements.get(id); }
    @PutMapping("/{id}")
    @Operation(summary = "Replace announcement", description = "Full replacement, not PATCH: omitted active/priority/bounds become false/0/null. Existing creation metadata is retained. Serializes with deletion; update and audit event commit atomically.")
    @ApiResponse(responseCode = "200", description = "Announcement replaced", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminAnnouncementResponse.class)))
    public AdminAnnouncementResponse replaceAnnouncement(@PathVariable long id, @Valid @RequestBody AnnouncementRequest request) { return announcements.replace(id, request); }
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete announcement", description = "Soft deletion, no restore API. Retains row and actor snapshots. Repeated deletion of existing row returns204 without another audit event; missing row404.")
    @ApiResponse(responseCode = "204", description = "Announcement deleted", content = @Content)
    public ResponseEntity<Void> deleteAnnouncement(@PathVariable long id) { announcements.delete(id); return ResponseEntity.noContent().build(); }
}
