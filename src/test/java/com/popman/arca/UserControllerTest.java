package com.popman.arca;

import com.popman.arca.controller.v1.UserController;
import com.popman.arca.dto.v1.common.MessageResponse;
import com.popman.arca.dto.v1.user.UserUpdateRequest;
import com.popman.arca.entity.User;
import com.popman.arca.entity.UserPrincipal;
import com.popman.arca.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserControllerTest {

    @Test
    void updateWrapsServiceMessageInResponseObject() {
        UserService userService = mock(UserService.class);
        UserUpdateRequest request = new UserUpdateRequest();
        when(userService.updateUserV1(3L, request)).thenReturn("User updated successfully");

        ResponseEntity<MessageResponse> response = new UserController(userService).updateUser(3L, request);

        assertEquals("User updated successfully", response.getBody().message());
    }

    @Test
    void deleteWrapsServiceMessageInResponseObject() {
        UserService userService = mock(UserService.class);
        when(userService.deleteUserv1(3L)).thenReturn("User deleted successfully");

        ResponseEntity<MessageResponse> response = new UserController(userService).deleteUser(3L);

        assertEquals("User deleted successfully", response.getBody().message());
    }

    @Test
    void profilePictureUpdateRejectsDifferentNonAdminPrincipal() {
        UserService userService = mock(UserService.class);
        UserController controller = new UserController(userService);
        MultipartFile file = mock(MultipartFile.class);
        User principalUser = new User();
        principalUser.setId(2L);
        principalUser.setRoles(Set.of("ROLE_USER"));

        ResponseEntity<?> response = controller.updateProfilePicture(
                1L, file, new UserPrincipal(principalUser));

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(userService);
    }
}
