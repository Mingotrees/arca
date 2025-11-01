package com.popman.arca.service;

import com.popman.arca.dto.subject.SubjectRequest;
import com.popman.arca.dto.subject.SubjectResponse;
import com.popman.arca.entity.Subject;

import java.util.List;

public interface SubjectService {
    List<Subject> getAllSubjects();
    Subject getSubjectById(Long id);
    Subject createSubject(SubjectRequest subject);
    Subject updateSubject(Long id, SubjectRequest updatedSubject);
    void deleteSubject(Long id);

    SubjectResponse mapToResponse(Subject subject);
}
