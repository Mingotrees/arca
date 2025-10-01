package com.popman.arca.controller;

import com.popman.arca.service.DepartmentService;
import com.popman.arca.entity.Department;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/department")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService){
        this.departmentService = departmentService;
    }
   @GetMapping("{departmentId}")
    public Department getDepartmentDetails(@PathVariable("departmentId") Long departmentId){
        return departmentService.getDepartment(departmentId);
    }
    @GetMapping
    public List<Department> getAllDepartment(){
        return departmentService.getAllDepartment();
    }

    @PostMapping
    public Department createDepartment(@RequestBody Department department){
        return departmentService.createDepartment(department);
    }

    @PutMapping("{departmentId}")
    public Department updateDepartment(@PathVariable("departmentId") Long departmentId, @RequestBody Department department){
        department.setId(departmentId);
        return departmentService.updateDepartment(department);
    }

    @DeleteMapping("{departmentId}")
    public void deleteDepartment(@PathVariable("departmentId") Long departmentId){
        departmentService.deleteDepartment(departmentId);
    }

}
