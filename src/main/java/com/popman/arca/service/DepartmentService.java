package com.popman.arca.service;

import com.popman.arca.dto.department.DepartmentDetailResponse;
import com.popman.arca.dto.department.DepartmentRequest;
import com.popman.arca.dto.department.DepartmentResponse;
import com.popman.arca.entity.Department;
import java.util.List;

public interface DepartmentService {
    DepartmentDetailResponse getDepartment(Long id);
    List<DepartmentDetailResponse> getAllDepartments();
    DepartmentDetailResponse createDepartment(DepartmentRequest departmentRequest);
    DepartmentDetailResponse updateDepartment(Long id, DepartmentRequest departmentRequest);
    void deleteDepartment(Long departmentId);
}
