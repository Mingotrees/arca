package com.popman.arca.controller;

import com.popman.arca.dto.school.SchoolRequest;
import com.popman.arca.dto.school.SchoolResponse;
import com.popman.arca.entity.School;
import com.popman.arca.repository.SchoolRepository;
import com.popman.arca.service.SchoolService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/schools")
public class SchoolController {

    private final SchoolService schoolService;

    public SchoolController(SchoolService schoolService){
        this.schoolService = schoolService;
    }


    @PostMapping("/add")
    public ResponseEntity<?> addSchool(@RequestBody SchoolRequest request){
        try{
            SchoolResponse created = schoolService.addSchool(request);
            return ResponseEntity.ok(created);
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.internalServerError().body("An unexpected error occured");
        }


    }

    @GetMapping
    public ResponseEntity<?> getAllSchools(){
        try{
            List<SchoolResponse> schools = schoolService.getAllSchool();
            if(schools.isEmpty()){
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(schools);
        }catch (Exception e){
            return ResponseEntity.internalServerError().body("Error fetching schools");
        }
    }

    @PutMapping("/edit/{id}")
    public ResponseEntity<?> editSchool(@PathVariable Long id, @RequestBody SchoolRequest request){

        try {
            SchoolResponse updateSchool = schoolService.editSchool(id, request);
            return ResponseEntity.ok(updateSchool);
        }catch (RuntimeException e){
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }


    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteSchool(@PathVariable Long id){
        try {

            schoolService.deleteSchool(id);
            return ResponseEntity.ok("School deleted successfully");
        }catch (RuntimeException e){
            return ResponseEntity.badRequest().body(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.internalServerError().body("An error has occurred");
        }
    }


}
