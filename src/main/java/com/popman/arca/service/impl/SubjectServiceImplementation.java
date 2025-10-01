package com.popman.arca.service.impl;
import com.popman.arca.entity.Department;
import com.popman.arca.entity.Subject;
import com.popman.arca.repository.DepartmentRepository;
import com.popman.arca.repository.SubjectRepository;
import com.popman.arca.service.SubjectService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SubjectServiceImplementation implements SubjectService{

    private final SubjectRepository subjectRepository;
    private final DepartmentRepository departmentRepository;


    public SubjectServiceImplementation(SubjectRepository subjectRepository, DepartmentRepository departmentRepository) {
        this.subjectRepository = subjectRepository;
        this.departmentRepository = departmentRepository;
    }

    @Override
    public List<Subject> getAllSubjects() {
        return subjectRepository.findAll();
    }

    @Override
    public Subject getSubjectById(Long id) {
        return subjectRepository.findById(id).orElseThrow(() -> new RuntimeException("Subject not found by id: " + id));
    }

    @Override
    public Subject createSubject(Subject subject) {
        List<Department> attachedDepartments = new ArrayList<>();

        for(Department dept : subject.getListDepartments()){
            Department existingDep = departmentRepository.findById(dept.getId()).orElseThrow(() -> new RuntimeException("Department not found: " + dept.getId()));
            attachedDepartments.add(existingDep);
        }

        subject.setListDepartments(attachedDepartments);
        return subjectRepository.save(subject);
    }

    @Override
    public Subject updateSubject(Long id, Subject updatedSubject) {
        Subject existingSubject = getSubjectById(id);

        existingSubject.setCode(updatedSubject.getCode());
        existingSubject.setName(updatedSubject.getName());
        existingSubject.setAliases(updatedSubject.getAliases());

        List<Department> attachedDepartments = new ArrayList<>();
        for (Department dept : updatedSubject.getListDepartments()) {
            Department existingDep = departmentRepository.findById(dept.getId())
                    .orElseThrow(() -> new RuntimeException("Department not found: " + dept.getId()));
            attachedDepartments.add(existingDep);
        }

        existingSubject.setListDepartments(attachedDepartments);

        return subjectRepository.save(existingSubject);
    }

    @Override
    public void deleteSubject(Long id) {
        if(!subjectRepository.existsById(id)){
            throw new RuntimeException("Subject not found by id: " + id);
        }
        subjectRepository.deleteById(id);
    }
}
