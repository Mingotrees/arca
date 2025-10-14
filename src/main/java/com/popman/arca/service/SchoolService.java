package com.popman.arca.service;

import com.popman.arca.dto.school.SchoolRequest;
import com.popman.arca.dto.school.SchoolResponse;
import com.popman.arca.entity.School;

import java.util.List;

public interface SchoolService {
    SchoolResponse addSchool(SchoolRequest request);
    SchoolResponse getSchool(Long id);
    List<SchoolResponse> getAllSchool();
    SchoolResponse editSchool(Long id, SchoolRequest request);
    void deleteSchool(Long id);
}
