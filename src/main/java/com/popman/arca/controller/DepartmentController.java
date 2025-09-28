package com.popman.arca.controller;

import com.popman.arca.service.DepartmentService;
import com.popman.arca.entity.Department;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("api/department")
public class DepartmentController {

    DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService){
        this.departmentService = departmentService;
    }
   @GetMapping("{departmentId}")
    public Department getDepartmentDetails(@PathVariable("departmentId") String departmentId){
        return departmentService.getDepartment(departmentId);
    }
    @GetMapping
    public List<Department> getAllDepartment(){
        return departmentService.getAllDepartment();
    }

    public String createUser(Department department){
        return departmentService.createDepartment(department);
    }

    public String updateUser(Department department){
        return departmentService.updateDepartment(department);
    }

    public String deleteUser(String departmentId){
        return departmentService.deleteDepartment(departmentId);
    }

}
