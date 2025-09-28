package com.popman.arca.service;

import com.popman.arca.entity.Department;
import java.util.List;

public interface DepartmentService {
    public Department getDepartment(String id);
    public List<Department> getAllDepartment();
    public String createDepartment(Department department);
    public String updateDepartment(Department department);
    public String deleteDepartment(String departmentId);
}
