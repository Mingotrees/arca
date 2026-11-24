package com.popman.arca.service;

import com.popman.arca.entity.Post;
import com.popman.arca.entity.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/** Shared, stateless parent-post policy. Actor arguments must come from the authenticated principal. */
public final class PostAccessPolicy {
    public boolean canRead(Post post) {
        UserPrincipal actor = currentActor();
        return actor != null && canRead(post, actor.getId(), actor.isAdmin());
    }

    private boolean canRead(Post post, Long actorId, boolean admin) {
        return actorId != null && post != null && !post.isDeleted()
                && ("APPROVED".equals(post.getStatus()) || admin || actorId.equals(post.getUserId()));
    }

    public void requireReadable(Post post) {
        if (!canRead(post)) throw notFound();
    }

    public void requireReadable(Post post, Long actorId, boolean admin) {
        if (!canRead(post, actorId, admin)) throw notFound();
    }

    public void requireMutable(Post post, Long actorId, boolean admin) {
        requireReadable(post, actorId, admin);
        if (!admin && !actorId.equals(post.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only post owner or admin may modify this post");
        }
    }

    public void requireDeletable(Post post, Long actorId, boolean admin) {
        // Retain owner/admin idempotency after deletion, without revealing deleted rows to others.
        if (post != null && actorId != null && (admin || actorId.equals(post.getUserId()))) return;
        requireMutable(post, actorId, admin);
    }

    public boolean currentActorIsAdmin() {
        UserPrincipal actor = currentActor();
        return actor != null && actor.isAdmin();
    }

    private UserPrincipal currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal actor ? actor : null;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
    }
}
