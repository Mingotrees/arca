package com.popman.arca.service;

import com.popman.arca.entity.User;

import java.util.List;

public interface UserService {
    User getUser(Long userId);
    List<User> getAllUser();
    String createUser(User user);
    String updateUser(User user);
    String deleteUser(Long userId);

    String createAdmin(User user);
    String promoteToAdmin(Long userId);
    String demoteFromAdmin(Long userId);
    String addRoleToUser(Long userId, String role);
    String removeRoleFromUser(Long userId, String role);
}
