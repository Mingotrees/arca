package com.popman.arca.repository;

import com.popman.arca.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, Long> {
    @Query("SELECT s FROM Subject s JOIN s.listDepartments d WHERE s.id IN :subjectIds AND d.id = :departmentId")
    List<Subject> findSubjectByIdsAndDepartment(@Param("subjectIds") List<Long> subjectIds, @Param("departmentId") Long departmentId);
    boolean existsByCode(String code);
    boolean existsByName(String name);
}
