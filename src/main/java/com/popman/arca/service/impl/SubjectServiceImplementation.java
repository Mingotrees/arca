package com.popman.arca.service.impl;
import com.popman.arca.dto.department.DepartmentResponse;
import com.popman.arca.dto.subject.SubjectResponse;
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
        if(subjectRepository.existsByCode(subject.getCode())){
            throw new RuntimeException("Subject with code : " + subject.getCode() + " already exists");
        }

        if(subjectRepository.existsByName(subject.getName())){
            throw new RuntimeException("Subject with name : " + subject.getName() + " already exists");
        }

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

    @Override
    public SubjectResponse mapToResponse(Subject subject){

        SubjectResponse response =  new SubjectResponse();
        response.setId(subject.getId());
        response.setCode(subject.getCode());
        response.setName(subject.getName());
        response.setAliases(subject.getAliases());

        List<DepartmentResponse> departmentResponses = new ArrayList<>();
        for(Department dept : subject.getListDepartments()){
            DepartmentResponse deptRes = new DepartmentResponse();
            deptRes.setId(dept.getId());
            deptRes.setName(dept.getName());
            departmentResponses.add(deptRes);
        }
        response.setDepartments(departmentResponses);
        return response;
    }
}
