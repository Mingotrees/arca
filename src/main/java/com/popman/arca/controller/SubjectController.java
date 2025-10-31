package com.popman.arca.controller;
import com.popman.arca.dto.subject.SubjectResponse;
import com.popman.arca.entity.Subject;
import com.popman.arca.service.SubjectService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/subject")
public class SubjectController {

    private final SubjectService subjectService;

   public SubjectController(SubjectService subjectService){
       this.subjectService = subjectService;
   }
    @GetMapping
    public List<SubjectResponse> getAllSubjects(){
       return subjectService.getAllSubjects().stream().map(subjectService::mapToResponse).toList();
    }

   @GetMapping("{subjectId}")
    public SubjectResponse getSubjectDetails(@PathVariable("subjectId") Long subjectId){
       Subject subject = subjectService.getSubjectById(subjectId);

       return subjectService.mapToResponse(subject);
    }

    @PostMapping
    public SubjectResponse createSubject(@RequestBody Subject subject){
       Subject created = subjectService.createSubject(subject);
       return subjectService.mapToResponse(created);
    }

    @PutMapping("{subjectId}")
    public SubjectResponse updateSubject(@PathVariable("subjectId") Long subjectId, @RequestBody Subject updatedSubject){
        Subject updated = subjectService.updateSubject(subjectId, updatedSubject);
       return subjectService.mapToResponse(updated);
    }

    @DeleteMapping("{subjectId}")
    public void deleteSubject(@PathVariable("subjectId") Long subjectId){
       subjectService.deleteSubject(subjectId);
    }

}
