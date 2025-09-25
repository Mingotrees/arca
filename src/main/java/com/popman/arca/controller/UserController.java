package com.popman.arca.controller;

import com.popman.arca.entity.User;
import com.popman.arca.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserController {
    UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("{userId}")
    public User getUserDetails(@PathVariable("userId") String userId){
        return userService.getUser(userId);
    }

    public List<User> getAllUser(){
        return userService.getAllUser();
    }

    public String createUser(User user){
        return userService.createUser(user);
    }

    public String updateUser(User user){
        return userService.updateUser(user);
    }

    public String deleteUser(String userId){
        return userService.deleteUser(userId);
    }

}
