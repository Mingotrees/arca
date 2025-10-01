package com.popman.arca.service;

import com.popman.arca.entity.Department;
import java.util.List;

public interface DepartmentService {
    public Department getDepartment(Long id);
    public List<Department> getAllDepartment();
    public Department createDepartment(Department department);
    public Department updateDepartment(Department department);
    public void deleteDepartment(Long departmentId);
}
