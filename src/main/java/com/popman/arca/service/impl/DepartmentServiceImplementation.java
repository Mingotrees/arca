package com.popman.arca.service.impl;

import com.popman.arca.dto.v1.department.DepartmentDetailResponse;
import com.popman.arca.dto.v1.department.DepartmentRequest;
import com.popman.arca.entity.Department;
import com.popman.arca.entity.School;
import com.popman.arca.repository.DepartmentRepository;
import com.popman.arca.repository.SchoolRepository;
import com.popman.arca.service.DepartmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DepartmentServiceImplementation implements DepartmentService {

    private static final Logger logger = LoggerFactory.getLogger(DepartmentServiceImplementation.class);
    private static final String DEPARTMENT_BY_ID_CACHE = "departments.byId";
    private static final String DEPARTMENT_LIST_CACHE = "departments.all";

    private final DepartmentRepository departmentRepository;
    private final SchoolRepository schoolRepository;
    private final CacheManager cacheManager;

    public DepartmentServiceImplementation(DepartmentRepository departmentRepository, SchoolRepository schoolRepository, CacheManager cacheManager) {
        this.departmentRepository = departmentRepository;
        this.schoolRepository = schoolRepository;
        this.cacheManager = cacheManager;
    }

    @Override
    public DepartmentDetailResponse getDepartmentV1(Long id) {
        String cacheKey = cacheKey(id);
        DepartmentDetailResponse cached = getCachedValue(DEPARTMENT_BY_ID_CACHE, cacheKey, DepartmentDetailResponse.class);
        if (cached != null) {
            logger.debug("Cache hit: {}", cacheKey);
            return cached;
        }

        logger.debug("Cache miss: {}", cacheKey);
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found with ID: " + id));
        DepartmentDetailResponse response = mapToDepartmentDetailResponse(department);
        putCacheValue(DEPARTMENT_BY_ID_CACHE, cacheKey, response);
        return response;
    }

    @Override
    public List<DepartmentDetailResponse> getAllDepartmentsV1() {
        List<DepartmentDetailResponse> cached = getCachedDepartmentList();
        if (cached != null) {
            logger.debug("Cache hit: {}", DEPARTMENT_LIST_CACHE);
            return cached;
        }

        logger.debug("Cache miss: {}", DEPARTMENT_LIST_CACHE);
        List<DepartmentDetailResponse> departments = departmentRepository.findAll()
                .stream()
                .map(this::mapToDepartmentDetailResponse)
                .collect(Collectors.toList());
        putCacheValue(DEPARTMENT_LIST_CACHE, "all", List.copyOf(departments));
        return departments;
    }

    @Override
    public DepartmentDetailResponse createDepartmentV1(DepartmentRequest departmentRequest) {
        School school = schoolRepository.findById(departmentRequest.getSchoolId())
                .orElseThrow(() -> new RuntimeException("School not found with ID: " + departmentRequest.getSchoolId()));

        Department department = new Department();
        department.setName(departmentRequest.getName());
        department.setSchool(school);

        Department saved = departmentRepository.save(department);
        evictCaches();
        return mapToDepartmentDetailResponse(saved);
    }

    @Override
    public DepartmentDetailResponse updateDepartmentV1(Long id, DepartmentRequest departmentRequest) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Department not found with ID: " + id));

        department.setName(departmentRequest.getName());

        if (departmentRequest.getSchoolId() != null &&
                !department.getSchool().getId().equals(departmentRequest.getSchoolId())) {
            School school = schoolRepository.findById(departmentRequest.getSchoolId())
                    .orElseThrow(() -> new RuntimeException("School not found with ID: " + departmentRequest.getSchoolId()));
            department.setSchool(school);
        }

        Department updated = departmentRepository.save(department);
        evictCaches();
        return mapToDepartmentDetailResponse(updated);
    }

    @Override
    public void deleteDepartmentV1(Long departmentId) {
        departmentRepository.deleteById(departmentId);
        evictCaches();
    }

    private DepartmentDetailResponse mapToDepartmentDetailResponse(Department department) {
        return new DepartmentDetailResponse(
                department.getId(),
                department.getName(),
                department.getSchool() != null ? department.getSchool().getId() : null,
                department.getSchool() != null ? department.getSchool().getName() : null
        );
    }

    private String cacheKey(Long id) {
        return String.valueOf(id);
    }

    private <T> T getCachedValue(String cacheName, String key, Class<T> type) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return null;
        }
        return cache.get(key, type);
    }

    @SuppressWarnings("unchecked")
    private List<DepartmentDetailResponse> getCachedDepartmentList() {
        Cache cache = cacheManager.getCache(DEPARTMENT_LIST_CACHE);
        if (cache == null) {
            return null;
        }
        Cache.ValueWrapper value = cache.get("all");
        return value == null ? null : (List<DepartmentDetailResponse>) value.get();
    }

    private void putCacheValue(String cacheName, String key, Object value) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    private void evictCaches() {
        Optional.ofNullable(cacheManager.getCache(DEPARTMENT_BY_ID_CACHE)).ifPresent(Cache::clear);
        Optional.ofNullable(cacheManager.getCache(DEPARTMENT_LIST_CACHE)).ifPresent(Cache::clear);
        logger.debug("Evicted department caches after write operation");
    }
}
