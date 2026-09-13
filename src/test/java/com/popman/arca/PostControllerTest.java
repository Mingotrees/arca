package com.popman.arca;

import com.popman.arca.controller.v1.PostController;
import com.popman.arca.dto.v1.common.MessageResponse;
import com.popman.arca.dto.v1.post.PostApprovalRequest;
import com.popman.arca.entity.UserPrincipal;
import com.popman.arca.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostControllerTest {
    @Test
    void approvalWrapsServiceMessageInResponseObject() {
        PostService postService = mock(PostService.class);
        PostApprovalRequest request = new PostApprovalRequest();
        when(postService.approvePostV1(request, 4L)).thenReturn("Post approved");

        ResponseEntity<MessageResponse> response = new PostController(postService).approvePost(request, 4L);

        assertEquals("Post approved", response.getBody().message());
    }

    @Test
    void deleteReturnsNotImplementedAfterAccessValidation() {
        PostService postService = mock(PostService.class);
        UserPrincipal principal = mock(UserPrincipal.class);
        when(principal.getId()).thenReturn(7L);
        when(principal.hasRole("ROLE_ADMIN")).thenReturn(false);
        PostController controller = new PostController(postService);

        ResponseEntity<Void> response = controller.deletePost(4L, principal);

        verify(postService).validateDeleteAccessV1(4L, 7L, false);
        assertEquals(HttpStatus.NOT_IMPLEMENTED, response.getStatusCode());
    }
}
