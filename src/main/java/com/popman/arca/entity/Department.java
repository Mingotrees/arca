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
    private List<Subject> subjects = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @OneToMany(mappedBy = "department", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Post> posts;
    public Department(){
    }

    public Department(Long id, String name, List<Subject> subjects, School school, List<Post> posts) {
        this.id = id;
        this.name = name;
        this.subjects = subjects;
        this.school = school;
        this.posts = posts;
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

    public List<Subject> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<Subject> subjects) {
        this.subjects = subjects;
    }

    public School getSchool() {
        return school;
    }

    public void setSchool(School school) {
        this.school = school;
    }

    public List<Post> getPosts() {
        return posts;
    }

    public void setPosts(List<Post> posts) {
        this.posts = posts;
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
