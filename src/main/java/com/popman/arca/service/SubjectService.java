package com.popman.arca.service;

import com.popman.arca.entity.Subject;

import java.util.List;

public interface SubjectService {
    List<Subject> getAllSubjects();
    Subject getSubjectById(Long id);
    Subject createSubject(Subject subject);
    Subject updateSubject(Long id, Subject updatedSubject);
    void deleteSubject(Long id);
}
