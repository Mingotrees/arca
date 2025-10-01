package com.popman.arca.controller;
import com.popman.arca.entity.Subject;
import com.popman.arca.service.SubjectService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/subject")
public class SubjectController {

   SubjectService subjectService;

   public SubjectController(SubjectService subjectService){
       this.subjectService = subjectService;
   }
    @GetMapping
    public List<Subject> getAllSubjects(){
       return subjectService.getAllSubjects();
    }

   @GetMapping("{subjectId}")
    public Subject getSubjectDetails(@PathVariable("subjectId") Long subjectId){
       return subjectService.getSubjectById(subjectId);
    }

    @PostMapping
    public Subject createSubject(@RequestBody Subject subject){
       return subjectService.createSubject(subject);
    }

    @PutMapping("{subjectId}")
    public Subject updateSubject(@PathVariable("subjectId") Long subjectId, @RequestBody Subject updatedSubject){

       return subjectService.updateSubject(subjectId, updatedSubject);
    }

    @DeleteMapping("{subjectId}")
    public void deleteSubject(@PathVariable("subjectId") Long subjectId){
       subjectService.deleteSubject(subjectId);
    }

}
