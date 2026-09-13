package com.popman.arca;

import com.popman.arca.dto.v1.post.PostCreateResponse;
import com.popman.arca.dto.v1.post.PostRequest;
import com.popman.arca.dto.v1.post.PostUpdateRequest;
import com.popman.arca.dto.v1.post.PostUpdateResponse;
import com.popman.arca.entity.Department;
import com.popman.arca.entity.Post;
import com.popman.arca.entity.Subject;
import com.popman.arca.entity.User;
import com.popman.arca.repository.DepartmentRepository;
import com.popman.arca.repository.PostRepository;
import com.popman.arca.repository.SubjectRepository;
import com.popman.arca.repository.UserRepository;
import com.popman.arca.service.impl.PostServiceImplementation;
import com.popman.arca.service.impl.VoteServiceImplementation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceImplementationTest {
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private VoteServiceImplementation voteServiceImplementation;

    @InjectMocks
    private PostServiceImplementation postService;

    @Test
    void createUsesAuthenticatedActorAndReturnsBothIds() {
        User actor = new User();
        actor.setId(7L);
        Department department = new Department();
        department.setId(3L);
        PostRequest request = new PostRequest();
        request.setUserId(999L);
        request.setDepartmentId(3L);
        request.setTitle("Title");
        request.setContent("Content");

        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        when(departmentRepository.findById(3L)).thenReturn(Optional.of(department));
        when(postRepository.getNextPostId()).thenReturn(12);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            saved.setId(42L);
            return saved;
        });

        PostCreateResponse response = postService.createPostV1(request, 7L);

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        assertEquals(7L, postCaptor.getValue().getUserId());
        assertEquals(42L, response.getId());
        assertEquals(12, response.getPostId());
        assertEquals(7L, response.getUserId());
    }

    @Test
    void updateChecksOwnerAndCopiesSubjectsToNewVersion() {
        Subject subject = new Subject();
        subject.setId(2L);
        Set<Subject> originalSubjects = new HashSet<>();
        originalSubjects.add(subject);
        Post current = approvedPost(4L, 7L);
        current.setSubjects(originalSubjects);

        when(postRepository.findById(4L)).thenReturn(Optional.of(current));
        when(postRepository.findMaxVersionByPostId(20)).thenReturn(1);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        PostUpdateResponse response = postService.updatePostV1(new PostUpdateRequest(), 4L, 7L, false);

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        Post newVersion = postCaptor.getValue();
        assertNotSame(originalSubjects, newVersion.getSubjects());
        assertEquals(originalSubjects, newVersion.getSubjects());
        assertEquals(5L, response.getId());
        assertEquals(20, response.getPostId());
        assertEquals(2, response.getVersion());
        assertEquals("PENDING_APPROVAL", response.getStatus());
    }

    @Test
    void updateRejectsNonOwnerBeforeCreatingVersion() {
        when(postRepository.findById(4L)).thenReturn(Optional.of(approvedPost(4L, 7L)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> postService.updatePostV1(new PostUpdateRequest(), 4L, 8L, false));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(postRepository, never()).findMaxVersionByPostId(any());
    }

    @Test
    void ownerPassesDeleteAccessValidation() {
        when(postRepository.findById(4L)).thenReturn(Optional.of(approvedPost(4L, 7L)));

        assertDoesNotThrow(() -> postService.validateDeleteAccessV1(4L, 7L, false));
    }

    private Post approvedPost(Long rowId, Long userId) {
        Post post = new Post();
        post.setId(rowId);
        post.setPost_id(20);
        post.setVersion(1);
        post.setTitle("Title");
        post.setContent("Content");
        post.setStatus("APPROVED");
        post.setUserId(userId);
        post.setDepartmentId(3L);
        post.setIsLatestVersion(true);
        return post;
    }
}
