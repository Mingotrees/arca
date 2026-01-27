package com.popman.arca.service;

import com.popman.arca.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UserService {
    User getUserV1(Long userId);
    List<User> getAllUserV1();
    String createUserV1(User user);
    String updateUserV1(User user);
    String deleteUserv1(Long userId);

    String createAdminV1(User user);
    String promoteToAdminV1(Long userId);
    String demoteFromAdminV1(Long userId);
    String addRoleToUserV1(Long userId, String role);
    String removeRoleFromUserV1(Long userId, String role);

    String updateProfilePictureV1(Long userId, MultipartFile file);
}
