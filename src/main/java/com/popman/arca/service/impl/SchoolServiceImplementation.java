package com.popman.arca.service.impl;

import com.popman.arca.dto.school.SchoolRequest;
import com.popman.arca.dto.school.SchoolResponse;
import com.popman.arca.dto.department.DepartmentResponse;
import com.popman.arca.entity.School;
import com.popman.arca.repository.SchoolRepository;
import com.popman.arca.service.SchoolService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class SchoolServiceImplementation implements SchoolService {

    private final SchoolRepository schoolRepository;

    public SchoolServiceImplementation(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }

    @Override
    public SchoolResponse addSchool(SchoolRequest request) {
        School school = new School();
        school.setName(request.getName());
        School saved = schoolRepository.save(school);
        return mapToResponse(saved);
    }

    @Override
    public SchoolResponse getSchool(Long id) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("School not found with ID: " + id));
        return mapToResponse(school);
    }

    @Override
    public List<SchoolResponse> getAllSchool() {
        return schoolRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public SchoolResponse editSchool(Long id, SchoolRequest request) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("School not found with ID: " + id));
        school.setName(request.getName());
        return mapToResponse(schoolRepository.save(school));
    }

    @Override
    public void deleteSchool(Long id) {
        schoolRepository.deleteById(id);
    }

    private SchoolResponse mapToResponse(School school) {
        List<DepartmentResponse> deptResponses = school.getDepartments() != null
                ? school.getDepartments().stream()
                .map(dept -> new DepartmentResponse(dept.getId(), dept.getName()))
                .collect(Collectors.toList())
                : null;

        return new SchoolResponse(school.getId(), school.getName(), deptResponses);
    }
}
