package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.admin.AuditEventPage;
import com.popman.arca.dto.v1.common.ApiErrorResponse;
import com.popman.arca.service.AuditQueryService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/admin/audit-events")
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class AdminAuditController {
    private final AuditQueryService queries;
    public AdminAuditController(AuditQueryService queries) { this.queries = queries; }
    @GetMapping
    @Operation(summary = "Read audit events", description = "Admin-only paged export/query. Stable occurred_at DESC,id DESC. Page 0–10000, size 1–100 (defaults 0/20). "
            + "Exact allowlisted action/outcome/target_type and positive actor_id/target_id filters; target_id requires target_type. "
            + "UTC [from,to), positive maximum 90 days, default trailing 90 days, offsets accepted, microsecond precision, years 1000–9999. "
            + "Numeric actor/target IDs survive deletion; metadata contains only validated role for role changes, otherwise an empty object. No credentials, addresses, content or file paths. "
            + "Events begin at deployment; one-year retention is manually managed by database operator. No update/delete API or cross-request export snapshot.")
    @ApiResponse(responseCode = "200", description = "Audit page", content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuditEventPage.class)))
    @ApiResponse(responseCode = "400", description = "Invalid bounds or filters", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Admin required", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiErrorResponse.class)))
    public AuditEventPage query(@Parameter(schema = @Schema(type = "integer", format = "int32", minimum = "0", maximum = "10000")) @RequestParam(required = false) Integer page,
            @Parameter(schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "100")) @RequestParam(required = false) Integer size,
            @RequestParam(required = false) com.popman.arca.service.audit.AuditAction action,
            @Parameter(schema = @Schema(allowableValues = {"SUCCESS", "DENIED", "ERROR", "ACCEPTED"})) @RequestParam(required = false) String outcome,
            @Parameter(schema = @Schema(allowableValues = {"USER", "SECURITY", "BANNED_EMAIL", "POST", "POST_VERSION", "SCHOOL", "DEPARTMENT", "SUBJECT", "ANNOUNCEMENT"})) @RequestParam(name = "target_type", required = false) String targetType,
            @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) @RequestParam(name = "actor_id", required = false) Long actorId,
            @Parameter(schema = @Schema(type = "integer", format = "int64", minimum = "1")) @RequestParam(name = "target_id", required = false) Long targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return queries.query(page, size, action == null ? null : action.name(), outcome, targetType, actorId, targetId, from, to);
    }
}
