package com.popman.arca.dto.v1.department;

public class DepartmentRequest {

    private Long id;
    private String name;
    private Long schoolId;
    private String schoolName;

    public DepartmentRequest() {
    }

    public DepartmentRequest(Long id, String name, Long schoolId, String schoolName) {
        this.id = id;
        this.name = name;
        this.schoolId = schoolId;
        this.schoolName = schoolName;
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

    public Long getSchoolId() {
        return schoolId;
    }

    public void setSchoolId(Long schoolId) {
        this.schoolId = schoolId;
    }

    public String getSchoolName() {
        return schoolName;
    }

    public void setSchoolName(String schoolName) {
        this.schoolName = schoolName;
    }
}
