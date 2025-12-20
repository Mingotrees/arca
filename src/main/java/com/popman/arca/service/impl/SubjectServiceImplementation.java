package com.popman.arca.service.impl;
import com.popman.arca.dto.department.DepartmentResponse;
import com.popman.arca.dto.subject.SubjectRequest;
import com.popman.arca.dto.subject.SubjectResponse;
import com.popman.arca.entity.Department;
import com.popman.arca.entity.Subject;
import com.popman.arca.repository.DepartmentRepository;
import com.popman.arca.repository.SubjectRepository;
import com.popman.arca.service.SubjectService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    public Subject createSubject(SubjectRequest request) {
        if(subjectRepository.existsByCode(request.getCode())){
            throw new RuntimeException("Subject with code : " + request.getCode() + " already exists");
        }

        if(subjectRepository.existsByName(request.getName())){
            throw new RuntimeException("Subject with name : " + request.getName() + " already exists");
        }
        Subject subject = new Subject();
        subject.setCode(request.getCode());
        subject.setName(request.getName());
        subject.setAliases(request.getAliases());

        List<Department> attachedDepartments = request.getDepartmentIds().stream()
            .map(id -> departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found with id: " + id)))
            .collect(Collectors.toList());

        subject.setListDepartments(attachedDepartments);
        return subjectRepository.save(subject);
    }

    @Override
    public Subject updateSubject(Long id, SubjectRequest request) {
        Subject existingSubject = getSubjectById(id);

        existingSubject.setCode(request.getCode());
        existingSubject.setName(request.getName());
        existingSubject.setAliases(request.getAliases());

        List<Department> attachedDepartments = new ArrayList<>();
        for (Long deptId : request.getDepartmentIds()) {
            Department existingDep = departmentRepository.findById(deptId)
                    .orElseThrow(() -> new RuntimeException("Department not found: " + deptId));
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
