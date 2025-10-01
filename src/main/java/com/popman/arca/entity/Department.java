package com.popman.arca.entity;


import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="departments")
public class Department {



    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 34)
    private String name;

    @ManyToMany(mappedBy = "listDepartments")

    @JsonIgnore
    private List<Subject> subjects = new ArrayList<>();

    public Department(){

    }
    public Department(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<Subject> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<Subject> subjects) {
        this.subjects = subjects;
    }

    public void addSubject(Subject subject){
        subjects.add(subject);
        subject.getListDepartments().add(this);
    }

    public void removeSubject(Subject subject){
        subjects.remove(subject);
        subject.getListDepartments().remove(this);
    }

}
