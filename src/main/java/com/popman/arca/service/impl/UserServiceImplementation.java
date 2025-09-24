package com.popman.arca.service.impl;

import com.popman.arca.entity.User;
import com.popman.arca.repository.UserRepository;
import com.popman.arca.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;
/*
*   Need to update with corresponding validations.
*
* */
@Service
public class UserServiceImplementation implements UserService {
    UserRepository userRepository;

    @Override
    public User getUser(String userId) {
        return userRepository.findById(userId).get();
    }

    @Override
    public List<User> getAllUser() {
        return userRepository.findAll();
    }

    @Override
    public String createUser(User user) {
        userRepository.save(user);
        return "User created successfully";
    }

    @Override
    public String updateUser(User user) {
        userRepository.save(user);
        return "User updated successfully";
    }

    @Override
    public String deleteUser(String userId) {
        userRepository.deleteById(userId);
        return "User deleted successfully";
    }
}
