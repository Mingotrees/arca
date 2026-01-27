package com.popman.arca.controller.v1;
import com.popman.arca.dto.v1.subject.SubjectRequest;
import com.popman.arca.dto.v1.subject.SubjectResponse;
import com.popman.arca.entity.Subject;
import com.popman.arca.service.SubjectService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/subject")
public class SubjectController {

    private final SubjectService subjectService;

   public SubjectController(SubjectService subjectService){
       this.subjectService = subjectService;
   }
    @GetMapping
    public List<SubjectResponse> getAllSubjects(){
       return subjectService.getAllSubjectsV1().stream().map(subjectService::mapToResponseV1).toList();
    }

   @GetMapping("{subjectId}")
    public SubjectResponse getSubjectDetails(@PathVariable("subjectId") Long subjectId){
       Subject subject = subjectService.getSubjectByIdV1(subjectId);

       return subjectService.mapToResponseV1(subject);
    }

    @PostMapping
    public SubjectResponse createSubject(@RequestBody SubjectRequest request){
       Subject created = subjectService.createSubjectV1(request);
       return subjectService.mapToResponseV1(created);
    }


    @PutMapping("{subjectId}")
    public SubjectResponse updateSubject(@PathVariable("subjectId") Long subjectId, @RequestBody SubjectRequest request){
        Subject updated = subjectService.updateSubjectV1(subjectId, request);
       return subjectService.mapToResponseV1(updated);
    }

    @DeleteMapping("{subjectId}")
    public void deleteSubject(@PathVariable("subjectId") Long subjectId){
       subjectService.deleteSubjectV1(subjectId);
    }

}
