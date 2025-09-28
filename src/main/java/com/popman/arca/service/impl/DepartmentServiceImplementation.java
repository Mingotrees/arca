package com.popman.arca.service.impl;

import com.popman.arca.entity.Department;
import com.popman.arca.repository.DepartmentRepository;
import com.popman.arca.service.DepartmentService;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class DepartmentServiceImplementation implements DepartmentService{
    DepartmentRepository departmentRepository;

    @Override
    public Department getDepartment(String departmentId) {
        return departmentRepository.findById(departmentId).get();
    }

    @Override
    public List<Department> getAllDepartment() {
        return departmentRepository.findAll();
    }

    @Override
    public String createDepartment(Department department) {
        departmentRepository.save(department);
        return "Department Added";
    }

    @Override
    public String updateDepartment(Department department) {
        departmentRepository.save(department);
        return "Department Updated";
    }

    @Override
    public String deleteDepartment(String departmentId) {
        departmentRepository.deleteById(departmentId);
        return "Department deleted";
    }
}
