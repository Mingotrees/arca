package com.popman.arca.service.impl;

import com.popman.arca.dto.v1.department.DepartmentDetailResponse;
import com.popman.arca.dto.v1.department.DepartmentRequest;
import com.popman.arca.entity.Department;
import com.popman.arca.entity.School;
import com.popman.arca.repository.DepartmentRepository;
import com.popman.arca.repository.SchoolRepository;
import com.popman.arca.service.DepartmentService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DepartmentServiceImplementation implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final SchoolRepository schoolRepository;

    public DepartmentServiceImplementation(DepartmentRepository departmentRepository, SchoolRepository schoolRepository) {
        this.departmentRepository = departmentRepository;
        this.schoolRepository = schoolRepository;
    }

    @Override
    public DepartmentDetailResponse getDepartmentV1(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found with ID: " + id));
        return mapToDepartmentDetailResponse(department);
    }

    @Override
    public List<DepartmentDetailResponse> getAllDepartmentsV1() {
        return departmentRepository.findAll()
                .stream()
                .map(this::mapToDepartmentDetailResponse)
                .collect(Collectors.toList());
    }

    @Override
    public DepartmentDetailResponse createDepartmentV1(DepartmentRequest departmentRequest) {
        School school = schoolRepository.findById(departmentRequest.getSchoolId())
                .orElseThrow(() -> new RuntimeException("School not found with ID: " + departmentRequest.getSchoolId()));

        Department department = new Department();
        department.setName(departmentRequest.getName());
        department.setSchool(school);

        Department saved = departmentRepository.save(department);
        return mapToDepartmentDetailResponse(saved);
    }

    @Override
    public DepartmentDetailResponse updateDepartmentV1(Long id, DepartmentRequest departmentRequest) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found with ID: " + id));

        department.setName(departmentRequest.getName());

        if (departmentRequest.getSchoolId() != null &&
                !department.getSchool().getId().equals(departmentRequest.getSchoolId())) {
            School school = schoolRepository.findById(departmentRequest.getSchoolId())
                    .orElseThrow(() -> new RuntimeException("School not found with ID: " + departmentRequest.getSchoolId()));
            department.setSchool(school);
        }

        Department updated = departmentRepository.save(department);
        return mapToDepartmentDetailResponse(updated);
    }

    @Override
    public void deleteDepartmentV1(Long departmentId) {
        departmentRepository.deleteById(departmentId);
    }

    private DepartmentDetailResponse mapToDepartmentDetailResponse(Department department) {
        return new DepartmentDetailResponse(
                department.getId(),
                department.getName(),
                department.getSchool() != null ? department.getSchool().getId() : null,
                department.getSchool() != null ? department.getSchool().getName() : null
        );
    }
}
