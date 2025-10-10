package com.popman.arca.service.impl;

import com.popman.arca.entity.User;
import com.popman.arca.repository.UserRepository;
import com.popman.arca.service.UserService;
import jakarta.transaction.Transactional;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class UserServiceImplementation implements UserService {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceImplementation.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImplementation(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User getUser(Long userId) {
        try{
            logger.debug("Fetching user with ID: {}", userId);
            return userRepository.findById(userId)
                    .orElseThrow(()-> new NoSuchElementException("User not found with ID: " + userId));

        }catch(IllegalArgumentException | NoSuchElementException e){
            logger.error("Error fetching user: {}", e.getMessage());throw e;
        }
    }

    @Override
    public List<User> getAllUser() {
        try{
            logger.debug("Fetching all users");
            return userRepository.findAll();
        }catch(Exception e){
            logger.error("Error fetching all users: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch users", e);
        }
    }

    @Override
    @Transactional
    public String createUser(User user) {
        try{
        logger.debug("Creating new user: {}", user);
            validateUser(user);
            user.setId(null);

            String encrypt = passwordEncoder.encode(user.getPassword());
            user.setPassword(encrypt);
            User savedUser = userRepository.save(user);
            logger.info("User created successfully with ID: {}", savedUser.getId());

            return "User created successfully with ID: " + savedUser.getId();
        }catch (IllegalArgumentException e){
            logger.error("Validation error creating user: {}", e.getMessage());
            throw e;
        }catch (Exception e){
            logger.error("Error creating user: {}", e.getMessage());
            throw new RuntimeException("Error creating user: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public String updateUser(User user) {
        try {
            logger.debug("Updating user with ID: {}", user.getId());
            validateId(user.getId());

            if(!userRepository.existsById(user.getId())){
                throw new NoSuchElementException("User not found with ID: " + user.getId());
            }

            if (user.getPassword() != null && !user.getPassword().trim().isEmpty()) {
                    validatePassword(user.getPassword());
                    user.setPassword(passwordEncoder.encode(user.getPassword()));
            }

            userRepository.save(user);
            logger.info("User with ID: {} updated successfully", user.getId());

            return "User updated successfully with ID " + user.getId();

        } catch (IllegalArgumentException | NoSuchElementException e) {
            logger.error("Error updating user: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error updating user: {}", e.getMessage());
            throw new RuntimeException("Failed to update user", e);
        }
    }

    @Override
    @Transactional
    public String deleteUser(Long userId) {
        try{
            logger.debug("Deleting user with Id: {}", userId);
            validateId(userId);
            if(!userRepository.existsById(userId)) {
                throw new IllegalArgumentException("User does not exist or is already deleted");
            }

            userRepository.deleteById(userId);
            logger.info("User with ID {} deleted successfully", userId);
            return "User deleted successfully";

        }catch(IllegalArgumentException e){
            logger.error("Error deleting user {}", userId);
            throw e;
        }
    }

    //validation methods
    private void validateUser(User user){
        if(user == null){
            throw new IllegalArgumentException("User cannot be null");
        }
        if(user.getEmail() == null || user.getEmail().trim().isEmpty()){
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        if(user.getFirstName() == null ||  user.getFirstName().trim().isEmpty() && user.getLastName() == null || user.getLastName().trim().isEmpty()){
            throw new IllegalArgumentException("First or Last Name cannot be null or empty");
        }

        validatePassword(user.getPassword());
    }

    private void validateId(Long userId){
        if(userId == null){
            throw new IllegalArgumentException("Id cannot be null");
        }
    }

    private void validatePassword(String password){
        if(password == null || password.trim().isEmpty()){
            throw new IllegalArgumentException("Password cannot be empty");
        }else if(password.length() < 8){
            throw new IllegalArgumentException("Password too short");
        }else if(!password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*") || !password.matches(".*\\d.*")){
            throw new IllegalArgumentException("Password must contain at least one capital letter, and one number");
        }
    }
}
