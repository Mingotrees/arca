package com.popman.arca.dto.v1.school;

public class SchoolRequest {


    private String name;

    public SchoolRequest() {
    }

    public SchoolRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
