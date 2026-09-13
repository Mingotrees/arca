package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.common.MessageResponse;
import com.popman.arca.dto.v1.school.SchoolRequest;
import com.popman.arca.dto.v1.school.SchoolResponse;
import com.popman.arca.service.SchoolService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/v1/schools")
public class SchoolController {

  private final SchoolService schoolService;

  public SchoolController(SchoolService schoolService) {
    this.schoolService = schoolService;
  }

  @PostMapping
  public ResponseEntity<SchoolResponse> addSchool(@RequestBody SchoolRequest request) {
    return ResponseEntity.ok(schoolService.addSchoolV1(request));
  }

  @GetMapping
  public ResponseEntity<List<SchoolResponse>> getAllSchools() {
    return ResponseEntity.ok(schoolService.getAllSchoolV1());
  }

  @PutMapping("/edit/{id}")
  public ResponseEntity<SchoolResponse> editSchool(@PathVariable Long id, @RequestBody SchoolRequest request) {
    return ResponseEntity.ok(schoolService.editSchoolV1(id, request));
  }

  @DeleteMapping("/delete/{id}")
  public ResponseEntity<MessageResponse> deleteSchool(@PathVariable Long id) {
    schoolService.deleteSchoolV1(id);
    return ResponseEntity.ok(new MessageResponse("School deleted successfully"));
  }

}
