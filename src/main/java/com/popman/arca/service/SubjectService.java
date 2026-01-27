package com.popman.arca.service;

import com.popman.arca.dto.v1.subject.SubjectRequest;
import com.popman.arca.dto.v1.subject.SubjectResponse;
import com.popman.arca.entity.Subject;

import java.util.List;

public interface SubjectService {
    List<Subject> getAllSubjectsV1();
    Subject getSubjectByIdV1(Long id);
    Subject createSubjectV1(SubjectRequest subject);
    Subject updateSubjectV1(Long id, SubjectRequest updatedSubject);
    void deleteSubjectV1(Long id);

    SubjectResponse mapToResponseV1(Subject subject);
}
