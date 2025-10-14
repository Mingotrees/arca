package com.popman.arca.controller;

import com.popman.arca.dto.department.DepartmentDetailResponse;
import com.popman.arca.dto.department.DepartmentRequest;
import com.popman.arca.dto.department.DepartmentResponse;
import com.popman.arca.service.DepartmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<DepartmentDetailResponse> getDepartment(@PathVariable Long id) {
        return ResponseEntity.ok(departmentService.getDepartment(id));
    }

    @GetMapping
    public ResponseEntity<List<DepartmentDetailResponse>> getAllDepartments() {
        return ResponseEntity.ok(departmentService.getAllDepartments());
    }

    @PostMapping("/add")
    public ResponseEntity<DepartmentDetailResponse> createDepartment(@RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(departmentService.createDepartment(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DepartmentDetailResponse> updateDepartment(@PathVariable Long id, @RequestBody DepartmentRequest request) {
        return ResponseEntity.ok(departmentService.updateDepartment(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDepartment(@PathVariable Long id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }
}
