package com.popman.arca.controller.v1;

import com.popman.arca.dto.v1.vote.VoteRequest;
import com.popman.arca.dto.v1.vote.VoteResponse;
import com.popman.arca.entity.User;
import com.popman.arca.entity.UserPrincipal;
import com.popman.arca.service.VoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VoteControllerTest {

    @Mock
    private VoteService voteService;

    private VoteController controller;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        controller = new VoteController(voteService);
        User user = new User();
        user.setId(4L);
        principal = new UserPrincipal(user);
    }

    @Test
    void returnsNoContentWhenToggleRemovesVote() {
        VoteRequest request = new VoteRequest(9L, "UPVOTE");
        when(voteService.createOrUpdateVoteV1(request, 4L)).thenReturn(null);

        ResponseEntity<VoteResponse> response = controller.vote(request, principal);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void returnsNoContentWhenUserHasNoVote() {
        when(voteService.getUserVoteForPostV1(9L, 4L)).thenReturn(null);

        ResponseEntity<VoteResponse> response = controller.getMyVote(9L, principal);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void returnsExistingVoteWithOkStatus() {
        VoteRequest request = new VoteRequest(9L, "UPVOTE");
        VoteResponse vote = new VoteResponse(2L, 9L, 4L, "UPVOTE");
        when(voteService.createOrUpdateVoteV1(request, 4L)).thenReturn(vote);

        ResponseEntity<VoteResponse> response = controller.vote(request, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(vote, response.getBody());
    }
}
