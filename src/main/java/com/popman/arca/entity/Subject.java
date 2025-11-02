package com.popman.arca.entity;


import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name="subjects", uniqueConstraints = {
        @UniqueConstraint(columnNames = "code"),
        @UniqueConstraint(columnNames = "name")
}, indexes = {
        @Index(name = "idx_subject_id", columnList = "id")
})
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

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "department_subjects",
            joinColumns = @JoinColumn(name = "subject_id"),
            inverseJoinColumns = @JoinColumn(name = "department_id")
    )
    private List<Department> listDepartments = new ArrayList<>();

    @ManyToMany(mappedBy = "subjects", fetch = FetchType.LAZY)
    private Set<Post> posts = new HashSet<>();

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
