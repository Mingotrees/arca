package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.department.DepartmentDetailResponse;
import com.popman.arca.dto.v1.department.DepartmentRequest;
import com.popman.arca.service.DepartmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepartmentDetailResponse> getDepartment(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.getDepartmentV1(id));
    }

    @GetMapping
    public ResponseEntity<List<DepartmentDetailResponse>> getAllDepartments() {
        return ResponseEntity.ok(departmentService.getAllDepartmentsV1());
    }

    @PostMapping
    public ResponseEntity<DepartmentDetailResponse> createDepartment(@RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(departmentService.createDepartmentV1(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentDetailResponse> updateDepartment(@PathVariable Long id, @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(departmentService.updateDepartmentV1(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartmentV1(id);
        return ResponseEntity.noContent().build();
    }
}
