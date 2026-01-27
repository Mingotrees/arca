package com.popman.arca.dto.v1.school;

import com.popman.arca.dto.v1.department.DepartmentResponse;

import java.util.List;

public class SchoolResponse {

    private Long id;
    private String name;
    private List<DepartmentResponse> departments;

    public SchoolResponse() {
    }

    public SchoolResponse(Long id, String name, List<DepartmentResponse> departments) {
        this.id = id;
        this.name = name;
        this.departments = departments;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<DepartmentResponse> getDepartments() {
        return departments;
    }

    public void setDepartments(List<DepartmentResponse> departments) {
        this.departments = departments;
    }
}
