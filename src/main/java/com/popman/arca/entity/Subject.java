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

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.LocalDateTime createdAt;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "updated_at", nullable = false)
    private java.time.LocalDateTime updatedAt;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "timestamps_approximate", nullable = false, updatable = false)
    private boolean timestampsApproximate;

    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public java.time.LocalDateTime getUpdatedAt() { return updatedAt; }
    public boolean isTimestampsApproximate() { return timestampsApproximate; }

    public void touchUpdatedAt() {
        if (updatedAt == null) return;
        var now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        updatedAt = now.isAfter(updatedAt) ? now : updatedAt.plusNanos(1000);
    }

    @PrePersist
    private void initializeProfileTimestamps() {
        createdAt = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        updatedAt = createdAt;
        timestampsApproximate = false;
    }

    @PreUpdate
    private void updateProfileTimestamp() { touchUpdatedAt(); }

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
        if (!java.util.Objects.equals(this.aliases, aliases)) touchUpdatedAt();
        this.aliases = aliases;
    }

    public List<Department> getListDepartments() {
        return listDepartments;
    }

    public void setListDepartments(List<Department> listDepartments) {
        if (!java.util.Objects.equals(this.listDepartments, listDepartments)) touchUpdatedAt();
        this.listDepartments = listDepartments;
    }

    public void addDepartment(Department department) {
        if (!listDepartments.contains(department)) {
            touchUpdatedAt();
            listDepartments.add(department);
            department.getSubjects().add(this);
        }
    }

    public void removeDepartment(Department department) {
        if (listDepartments.contains(department)) {
            touchUpdatedAt();
            listDepartments.remove(department);
            department.getSubjects().remove(this);
        }
    }

}
