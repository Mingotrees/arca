package com.popman.arca.service.impl;

import com.popman.arca.dto.v1.vote.VoteRequest;
import com.popman.arca.entity.Post;
import com.popman.arca.entity.User;
import com.popman.arca.entity.Vote;
import com.popman.arca.repository.PostRepository;
import com.popman.arca.repository.UserRepository;
import com.popman.arca.repository.VoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteServiceImplementationTest {

    @Mock
    private VoteRepository voteRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;

    private VoteServiceImplementation service;

    @BeforeEach
    void setUp() {
        service = new VoteServiceImplementation();
        ReflectionTestUtils.setField(service, "voteRepository", voteRepository);
        ReflectionTestUtils.setField(service, "postRepository", postRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
    }

    @Test
    void rejectsVoteOnNonApprovedPost() {
        Post post = new Post();
        post.setStatus("PENDING_APPROVAL");
        VoteRequest request = new VoteRequest(5L, "UPVOTE");
        when(postRepository.findById(5L)).thenReturn(Optional.of(post));

        assertThrows(IllegalArgumentException.class,
                () -> service.createOrUpdateVoteV1(request, 3L));

        verify(userRepository, never()).findById(3L);
        verify(voteRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sameVoteStillTogglesOff() {
        Post post = new Post();
        post.setStatus("APPROVED");
        User user = new User();
        Vote vote = new Vote();
        vote.setId(8L);
        vote.setPost(post);
        vote.setUser(user);
        vote.setVoteType("UPVOTE");
        VoteRequest request = new VoteRequest(5L, "UPVOTE");
        when(postRepository.findById(5L)).thenReturn(Optional.of(post));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(voteRepository.findByPostIdAndUserId(5L, 3L)).thenReturn(Optional.of(vote));

        assertNull(service.createOrUpdateVoteV1(request, 3L));

        verify(voteRepository).delete(vote);
        verify(voteRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
