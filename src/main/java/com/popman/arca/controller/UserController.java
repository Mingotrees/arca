package com.popman.arca.controller;

import com.popman.arca.entity.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserController {
    User user;

    @GetMapping("/api/test")
    public String getString(){
        return "Whats up mga real niggas";
    }
}
