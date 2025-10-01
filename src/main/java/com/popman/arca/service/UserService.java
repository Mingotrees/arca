package com.popman.arca.service;

import com.popman.arca.entity.User;

import java.util.List;

public interface UserService {
    public User getUser(Long userId);
    public List<User> getAllUser();
    public String createUser(User user);
    public String updateUser(User user);
    public String deleteUser(Long userId);
}
