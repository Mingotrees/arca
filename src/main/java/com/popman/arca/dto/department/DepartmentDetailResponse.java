package com.popman.arca.dto.department;

public class DepartmentDetailResponse {
    private Long id;
    private String name;
    private Long school_id;
    private String school_name;

    public DepartmentDetailResponse() {
    }

    public DepartmentDetailResponse(Long id, String name, Long school_id, String school_name) {
        this.id = id;
        this.name = name;
        this.school_id = school_id;
        this.school_name = school_name;
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
        return school_id;
    }

    public void setSchoolId(Long school_id) {
        this.school_id = school_id;
    }

    public String getSchoolName() {
        return school_name;
    }

    public void setSchoolName(String school_name) {
        this.school_name = school_name;
    }
}
