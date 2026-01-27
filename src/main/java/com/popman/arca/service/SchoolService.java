package com.popman.arca.service;

import com.popman.arca.dto.v1.school.SchoolRequest;
import com.popman.arca.dto.v1.school.SchoolResponse;

import java.util.List;

public interface SchoolService {
    SchoolResponse addSchoolV1(SchoolRequest request);
    SchoolResponse getSchoolV1(Long id);
    List<SchoolResponse> getAllSchoolV1();
    SchoolResponse editSchoolV1(Long id, SchoolRequest request);
    void deleteSchoolV1(Long id);
}
