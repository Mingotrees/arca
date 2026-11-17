package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.admin.AdminDashboardResponse;
import com.popman.arca.dto.v1.common.ApiErrorResponse;
import com.popman.arca.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class AdminDashboardController {
    private final AdminDashboardService dashboard;
    public AdminDashboardController(AdminDashboardService dashboard) { this.dashboard = dashboard; }

    @GetMapping
    @Operation(summary = "Read admin dashboard aggregates", description = "ROLE_ADMIN only. Current counts are not window-filtered. "
            + "Current logical status uses highest version, including rejected rows without a latest flag; deleted logical posts are separate. "
            + "Period creations count version 1; approvals count version rows with recorded approved_at, including superseded/deleted rows. "
            + "Legacy approval dates remain unknown and are reported separately. Engagement counts approved/latest/non-deleted rows, including activity by deleted users. "
            + "Attachment bytes are known nonnegative metadata, not disk usage; unknown sizes are reported separately. "
            + "UTC [from,to), positive range at most 90 days; default to=calculation time, from=to minus 90 days. "
            + "Offsets accepted; maximum microsecond precision and UTC years 1000–9999. Legacy creation times retain their original stored clock. "
            + "Seven aggregate queries in one read-only repeatable-read transaction; no cache.")
    @ApiResponse(responseCode = "200", description = "Admin aggregates", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminDashboardResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid date window", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Admin role required", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public AdminDashboardResponse dashboard(
            @Parameter(description = "Inclusive ISO offset date-time, default to minus 90 days")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Exclusive ISO offset date-time, default calculation time")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return dashboard.dashboard(from, to);
    }
}
