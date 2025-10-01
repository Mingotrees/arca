package com.popman.arca.service.impl;

import com.popman.arca.entity.Department;
import com.popman.arca.repository.DepartmentRepository;
import com.popman.arca.service.DepartmentService;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class DepartmentServiceImplementation implements DepartmentService{
    private final DepartmentRepository departmentRepository;

    public DepartmentServiceImplementation(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Override
    public Department getDepartment(Long departmentId) {
        return departmentRepository.findById(departmentId).orElseThrow(() -> new RuntimeException("Department not found: " + departmentId));
    }

    @Override
    public List<Department> getAllDepartment() {
        return departmentRepository.findAll();
    }

    @Override
    public Department createDepartment(Department department) {
        return departmentRepository.save(department);
    }

    @Override
    public Department updateDepartment(Department department) {
        Department existing = getDepartment(department.getId());
        existing.setName(department.getName());
        existing.setId(department.getId());

        return departmentRepository.save(existing);
    }

    @Override
    public void deleteDepartment(Long departmentId) {
        departmentRepository.deleteById(departmentId);
    }
}
