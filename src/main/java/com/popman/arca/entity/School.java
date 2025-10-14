package com.popman.arca.entity;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "school")
public class School {

 @Id
 @GeneratedValue(strategy = GenerationType.IDENTITY)
 private Long id;

 private String name;

 @OneToMany(mappedBy = "school", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
 private List<Department> departments;

    public School() {
    }

    public School(Long id, String name, List<Department> departments) {
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

    public List<Department> getDepartments() {
        return departments;
    }

    public void setDepartments(List<Department> departments) {
        this.departments = departments;
    }
}

