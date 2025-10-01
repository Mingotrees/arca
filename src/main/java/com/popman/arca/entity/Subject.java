package com.popman.arca.entity;


import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name="subjects")
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 10)
    private String code;

    @Column
    private String name;

    @ElementCollection
    @CollectionTable(
            name = "subject_aliases",
            joinColumns = @JoinColumn(name = "subject_id")
    )
    @Column(name = "alias")
    private List<String> aliases = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "department_subjects",
            joinColumns = @JoinColumn(name = "subject_id"),
            inverseJoinColumns = @JoinColumn(name = "department_id")
    )
    private List<Department> listDepartments = new ArrayList<>();

    public Subject() {
    }

    public Subject(Long id, String code, String name, List<String> aliases, List<Department> listDepartments) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.aliases = aliases;
        this.listDepartments = listDepartments;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases;
    }

    public List<Department> getListDepartments() {
        return listDepartments;
    }

    public void setListDepartments(List<Department> listDepartments) {
        this.listDepartments = listDepartments;
    }

    public void addDepartment(Department department) {
        if (!listDepartments.contains(department)) {
            listDepartments.add(department);
            department.getSubjects().add(this);
        }
    }

    public void removeDepartment(Department department) {
        if (listDepartments.contains(department)) {
            listDepartments.remove(department);
            department.getSubjects().remove(this);
        }
    }

}
