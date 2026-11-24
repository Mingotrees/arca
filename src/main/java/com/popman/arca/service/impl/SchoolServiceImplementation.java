package com.popman.arca.service.impl;

import com.popman.arca.dto.v1.school.SchoolRequest;
import com.popman.arca.dto.v1.school.SchoolResponse;
import com.popman.arca.dto.v1.department.DepartmentResponse;
import com.popman.arca.entity.School;
import com.popman.arca.repository.SchoolRepository;
import com.popman.arca.service.SchoolService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class SchoolServiceImplementation implements SchoolService {
    private final com.popman.arca.service.audit.AuditService audit;

    private final SchoolRepository schoolRepository;

    public SchoolServiceImplementation(SchoolRepository schoolRepository, com.popman.arca.service.audit.AuditService audit) {
        this.schoolRepository = schoolRepository;
        this.audit = audit;
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public SchoolResponse addSchoolV1(SchoolRequest request) {
        School school = new School();
        school.setName(request.getName());
        School saved = schoolRepository.save(school);
        audit.success(com.popman.arca.service.audit.AuditAction.SCHOOL_CREATED, saved.getId());
        return mapToResponse(saved);
    }

    @Override
    public SchoolResponse getSchoolV1(Long id) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("School not found with ID: " + id));
        return mapToResponse(school);
    }

    @Override
    public List<SchoolResponse> getAllSchoolV1() {
        return schoolRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public SchoolResponse editSchoolV1(Long id, SchoolRequest request) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("School not found with ID: " + id));
        school.setName(request.getName());
        audit.success(com.popman.arca.service.audit.AuditAction.SCHOOL_UPDATED, id);
        return mapToResponse(schoolRepository.save(school));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void deleteSchoolV1(Long id) {
        if (!schoolRepository.existsById(id)) throw new NoSuchElementException("School not found");
        schoolRepository.deleteById(id);
        audit.success(com.popman.arca.service.audit.AuditAction.SCHOOL_DELETED, id);
    }

    private SchoolResponse mapToResponse(School school) {
        List<DepartmentResponse> deptResponses = school.getDepartments() != null
                ? school.getDepartments().stream()
                .map(dept -> new DepartmentResponse(dept.getId(), dept.getName()))
                .collect(Collectors.toList())
                : List.of();

        return new SchoolResponse(school.getId(), school.getName(), deptResponses);
    }
}
