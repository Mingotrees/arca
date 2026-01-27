package com.popman.arca.service;

import com.popman.arca.dto.v1.department.DepartmentDetailResponse;
import com.popman.arca.dto.v1.department.DepartmentRequest;

import java.util.List;

public interface DepartmentService {
    DepartmentDetailResponse getDepartmentV1(Long id);
    List<DepartmentDetailResponse> getAllDepartmentsV1();
    DepartmentDetailResponse createDepartmentV1(DepartmentRequest departmentRequest);
    DepartmentDetailResponse updateDepartmentV1(Long id, DepartmentRequest departmentRequest);
    void deleteDepartmentV1(Long departmentId);
}
