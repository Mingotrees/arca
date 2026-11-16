package com.popman.arca.service.impl;


import com.popman.arca.dto.v1.file.FileResponse;
import com.popman.arca.dto.v1.post.PostApprovalRequest;
import com.popman.arca.dto.v1.post.PostRequest;
import com.popman.arca.dto.v1.post.PostResponse;
import com.popman.arca.dto.v1.post.PostSubjectResponse;
import com.popman.arca.dto.v1.post.PostUpdateRequest;
import com.popman.arca.dto.v1.post.PostCreateResponse;
import com.popman.arca.dto.v1.post.PostUpdateResponse;
import com.popman.arca.entity.Post;
import com.popman.arca.entity.PostIdentity;
import com.popman.arca.repository.PostIdentityRepository;
import com.popman.arca.entity.Subject;
import com.popman.arca.repository.DepartmentRepository;
import com.popman.arca.repository.PostRepository;
import com.popman.arca.repository.SubjectRepository;
import com.popman.arca.repository.UserRepository;
import com.popman.arca.service.PostService;
import com.popman.arca.service.PostAccessPolicy;
import com.popman.arca.dto.v1.post.PostSearchCriteria;
import com.popman.arca.dto.v1.post.PostSearchResponse;
import com.popman.arca.repository.PostSearchRepository;
import com.popman.arca.repository.VoteRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PostServiceImplementation implements PostService {

    private final PostAccessPolicy accessPolicy = new PostAccessPolicy();
    private final com.popman.arca.service.links.PostLinkValidator linkValidator = new com.popman.arca.service.links.PostLinkValidator();

    @Autowired
    private PostSearchRepository searchRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PostSearchResponse searchPostsV1(PostSearchCriteria criteria, boolean isAdmin) {
        if (criteria.status() != null && !isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Status filtering requires admin access");
        }
        var result = searchRepository.search(criteria);
        List<PostResponse> content = List.of();
        if (!result.ids().isEmpty()) {
            var postsById = postRepository.findSearchPageDetails(result.ids()).stream()
                    .collect(Collectors.toMap(Post::getId, post -> post));
            postRepository.fetchSearchPageFiles(result.ids());
            postRepository.fetchSearchPageLinks(result.ids());
            var votes = voteRepository.countSearchPageVotes(result.ids()).stream()
                    .collect(Collectors.toMap(VoteRepository.PostVoteCounts::getPostId, counts -> counts));
            content = result.ids().stream().map(id -> {
                Post post = postsById.get(id);
                accessPolicy.requireReadable(post);
                var counts = votes.get(id);
                return mapToResponse(post, counts == null ? 0 : Math.toIntExact(counts.getUpvotes()),
                        counts == null ? 0 : Math.toIntExact(counts.getDownvotes()));
            }).toList();
        }
        long pages = result.total() / criteria.size() + (result.total() % criteria.size() == 0 ? 0 : 1);
        return new PostSearchResponse(content, criteria.page(), criteria.size(), result.total(), pages, criteria.sort());
    }

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostIdentityRepository postIdentityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private VoteServiceImplementation voteServiceImplementation;

    @Autowired
    private SubjectRepository subjectRepository;

    @Override
    public PostResponse getPostV1(Long rowId, Long actorId, boolean isAdmin) {
        Post post = postRepository.findById(rowId)
                .orElseThrow(() -> new ResponseStatusException(
                         HttpStatus.NOT_FOUND, "Post not found"));
        accessPolicy.requireReadable(post, actorId, isAdmin);
        return mapToResponse(post);
    }

    @Override
    public List<PostResponse> getAllPostsV1() {
        return postRepository.findLatestApprovedPosts().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PostResponse> getPostsBySubjectV1(Long subjectId) {
        return postRepository.findLatestApprovedPostsBySubjectId(subjectId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PostResponse> getMyPostsV1(Long actorId, String status) {
        List<Post> posts = status == null || status.isBlank()
                ? postRepository.findAllPostsByUserId(actorId)
                : postRepository.findAllPostsByUserIdAndStatus(actorId, status.toUpperCase(Locale.ROOT));
        return posts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PostResponse> getAllUserPostV1(Long userId) {
        List<Post> posts = postRepository.findLatestApprovedPostsByUserId(userId);
        return posts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<PostResponse> getAllDepartmentPostV1(Long departmentId) {
        List<Post> posts = postRepository.findLatestApprovedPostsByDepartmentId(departmentId);
        return posts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PostCreateResponse createPostV1(PostRequest request, Long actorId) {
        var links = linkValidator.validate(request.getLinks());
        userRepository.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("User not found with Id " + actorId));

        departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(()-> new NoSuchElementException("Department not found with id " + request.getDepartmentId()));

        Integer nextPostId = postIdentityRepository.saveAndFlush(new PostIdentity(actorId)).getId();

        Post post = new Post();
        post.setPost_id(nextPostId);
        post.setContent(request.getContent());
        post.setUserId(actorId);
        post.setTitle(request.getTitle());
        post.setVersion(1);
        post.setDepartmentId(request.getDepartmentId());
        post.setIsLatestVersion(true);
        post.setStatus("PENDING_APPROVAL");
        post.setUpdatedAt(LocalDateTime.now());
        post.setCreatedAt(LocalDateTime.now());
        links.forEach(post::addLink);

        if(request.getPostTag() != null && !request.getPostTag().isEmpty()){
            Subject subject = subjectRepository.findById(Long.parseLong(request.getPostTag()))
                    .orElseThrow(() -> new NoSuchElementException("Subject not found with id: " + request.getPostTag()));
            
            boolean belongsToDepartment = subject.getListDepartments().stream()
                    .anyMatch(dept -> dept.getId().equals(request.getDepartmentId()));
            
            if(!belongsToDepartment){
                throw new IllegalArgumentException("Subject does not belong to the department id " + request.getDepartmentId());
            }
            Set<Subject> subjects = new HashSet<>();
            subjects.add(subject);
            post.setSubjects(subjects);
        }
        postRepository.save(post);

        String message = "Post created successfully with Id " + post.getId() + ". Awaiting admin approval";
        return new PostCreateResponse(post.getId(), actorId, post.getPost_id(), message);
    }

    @Override
    @Transactional
    public PostUpdateResponse updatePostV1(
            PostUpdateRequest updateRequest, Long rowId, Long actorId, boolean isAdmin) {
        postRepository.lockLogicalPostByRowId(rowId);
        Post currentPost = getOwnedOrAdminPost(rowId, actorId, isAdmin);

        if (!"APPROVED".equals(currentPost.getStatus())) {
            throw new IllegalStateException("Only approved posts can be updated. Current status: " + currentPost.getStatus());
        }

        var links = updateRequest.getLinks() == null
                ? currentPost.getLinks().stream().map(com.popman.arca.entity.PostLink::copy).toList()
                : linkValidator.validate(updateRequest.getLinks());

        Integer maxVersion = postRepository.findMaxVersionByPostId(currentPost.getPost_id());
        postRepository.updateIsLatestVersionByPostId(currentPost.getPost_id(), false);

        Post newVersion = new Post();
        newVersion.setPost_id(currentPost.getPost_id());
        newVersion.setVersion(maxVersion + 1);
        newVersion.setTitle(updateRequest.getTitle() != null ? updateRequest.getTitle() : currentPost.getTitle());
        newVersion.setContent(updateRequest.getContent() != null ? updateRequest.getContent() : currentPost.getContent());
        newVersion.setStatus("PENDING_APPROVAL");
        newVersion.setUserId(currentPost.getUserId());
        newVersion.setDepartmentId(currentPost.getDepartmentId());
        newVersion.setIsLatestVersion(true);
        newVersion.setCreatedAt(LocalDateTime.now());
        newVersion.setUpdatedAt(LocalDateTime.now());
        links.forEach(newVersion::addLink);

        if (updateRequest.getPostTag() != null && !updateRequest.getPostTag().isEmpty()) {
            Subject subject = subjectRepository.findById(Long.parseLong(updateRequest.getPostTag()))
                    .orElseThrow(() -> new NoSuchElementException("Subject not found with id: " + updateRequest.getPostTag()));
            
            boolean belongsToDepartment = subject.getListDepartments().stream()
                    .anyMatch(dept -> dept.getId().equals(currentPost.getDepartmentId()));
            
            if(!belongsToDepartment){
                throw new IllegalArgumentException("Subject does not belong to the department id " + currentPost.getDepartmentId());
            }
            Set<Subject> subjects = new HashSet<>();
            subjects.add(subject);
            newVersion.setSubjects(subjects);
        } else {
            newVersion.setSubjects(currentPost.getSubjects() == null
                    ? new HashSet<>()
                    : new HashSet<>(currentPost.getSubjects()));
        }

        postRepository.save(newVersion);

        String message = "Post update submitted successfully. Version " + newVersion.getVersion()
                + " is pending admin approval.";
        return new PostUpdateResponse(
                newVersion.getId(),
                newVersion.getPost_id(),
                newVersion.getVersion(),
                newVersion.getStatus(),
                message);
    }

    @Override
    public List<PostResponse> getAllPendingApprovalPostsV1() {
        List<Post> pendingPost = postRepository.findAllPendingApprovalPosts();
        return pendingPost.stream()
                .map(this::mapToResponse)   
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public String approvePostV1(PostApprovalRequest approvalRequest, Long postId) {
        postRepository.lockLogicalPostByRowId(postId);
        Post post = postRepository.findByIdForUpdate(postId)
                .orElseThrow(() -> new NoSuchElementException("Post not found with id: " + postId));
        requireActive(post);

        // Validate that the post is pending approval
        if (!"PENDING_APPROVAL".equals(post.getStatus())) {
            throw new IllegalStateException("Post is not pending approval. Current status: " + post.getStatus());
        }

        if (approvalRequest.getApproved()) {
            post.setStatus("APPROVED");
            post.setApprovedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));

            if (post.getVersion() > 1) {
                Post previousApproved = postRepository
                        .findPreviousApprovedVersion(post.getPost_id(), post.getVersion());

                if (previousApproved != null) {
                    previousApproved.setStatus("SUPERSEDED");
                    previousApproved.setIsLatestVersion(false);
                    postRepository.save(previousApproved);
                }
            }

            post.setUpdatedAt(LocalDateTime.now());
            postRepository.save(post);

            return "Post approved successfully. Now visible to users.";

        } else {
            post.setStatus("REJECTED");
            post.setIsLatestVersion(false);

            if (approvalRequest.getRejectionReason() != null) {
                post.setRejectionReason(approvalRequest.getRejectionReason());
            }

            if (post.getVersion() > 1) {
                Post previousApproved = postRepository
                        .findPreviousApprovedVersion(post.getPost_id(), post.getVersion());

                if (previousApproved != null) {
                    previousApproved.setIsLatestVersion(true);
                    postRepository.save(previousApproved);
                }
            }

            post.setUpdatedAt(LocalDateTime.now());
            postRepository.save(post);

            return "Post rejected. " +
                    (approvalRequest.getRejectionReason() != null ?
                            "Reason: " + approvalRequest.getRejectionReason() : "No reason given");
        }
    }

    @Override
    public List<PostResponse> getPostHistoryV1(Integer postId) {
        List<Post> versions = postRepository.findAllVersionsByPostId(postId);
        return versions.stream().filter(accessPolicy::canRead).map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void validateDeleteAccessV1(Long rowId, Long actorId, boolean isAdmin) {
        getOwnedOrAdminPost(rowId, actorId, isAdmin);
    }

    @Override
    @Transactional
    public void deletePostV1(Long rowId, Long actorId, boolean isAdmin) {
        postRepository.lockLogicalPostByRowId(rowId);
        Post post = postRepository.findByIdForUpdate(rowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
        accessPolicy.requireDeletable(post, actorId, isAdmin);
        postRepository.softDeleteLogicalPost(post.getPost_id(), actorId, LocalDateTime.now());
    }

    @Override
    public List<PostResponse> getPendingPostsByDepartmentV1(Long departmentId){
        List<Post> posts = postRepository.getPendingPostsByDepartment(departmentId);
        return posts.stream().map(this::mapToResponse).collect(Collectors.toList());
    }


    private PostResponse mapToResponse(Post post) {
        int upvotes = 0;
        int downvotes = 0;
        try {
            upvotes = voteServiceImplementation.getUpvoteCountV1(post.getId());
            downvotes = voteServiceImplementation.getDownvoteCountV1(post.getId());
        } catch (Exception e) {
            // Preserve legacy list mapping behavior; search supplies explicit batched counts instead.
            upvotes = 0;
            downvotes = 0;
        }
        return mapToResponse(post, upvotes, downvotes);
    }

    private PostResponse mapToResponse(Post post, int upvotes, int downvotes) {
        PostResponse response = new PostResponse();
        response.setId(post.getId());
        response.setPostId(post.getPost_id());
        response.setVersion(post.getVersion());
        response.setTitle(post.getTitle());
        response.setContent(post.getContent());
        response.setStatus(post.getStatus());
        response.setIsLatestVersion(post.getIsLatestVersion());
        response.setCreatedAt(post.getCreatedAt());
        response.setUpdatedAt(post.getUpdatedAt());
        response.setLinks(post.getLinks().stream().map(link -> new com.popman.arca.dto.v1.post.PostLinkResponse(
                link.getUrl(), link.getLabel(), link.getPosition())).toList());

        List<PostSubjectResponse> subjects = post.getSubjects() == null
                ? List.of()
                : post.getSubjects().stream()
                        .sorted(Comparator.comparing(Subject::getId))
                        .map(subject -> new PostSubjectResponse(subject.getId(), subject.getName()))
                        .collect(Collectors.toList());
        response.setSubjects(subjects);
        response.setPostTag(subjects.stream().findFirst().map(PostSubjectResponse::getName).orElse(null));

        if (post.getUser() != null) {
            response.setUserId(post.getUser().getId());
            response.setFirstName(post.getUser().getFirstName());
            response.setLastName(post.getUser().getLastName());
        } else {
            response.setUserId(post.getUserId());
        }

        if (post.getDepartment() != null) {
            response.setDepartmentId(post.getDepartment().getId());
            response.setDepartmentName(post.getDepartment().getName());
        } else {
            response.setDepartmentId(post.getDepartmentId());
        }

        if (post.getRejectionReason() != null) {
            response.setRejectionReason(post.getRejectionReason());
        }

        response.setUpvoteCount(upvotes);
        response.setDownvoteCount(downvotes);

        if (post.getFiles() != null && !post.getFiles().isEmpty()) {
                List<FileResponse> fileResponses = post.getFiles().stream()
                    .map(file -> new FileResponse(
                        file.getId(),
                        file.getFileName(),
                        file.getFileType(),
                        file.getFileSize()
                    ))
                    .collect(Collectors.toList());
            response.setFiles(fileResponses);
        }

        return response;
    }

    private Post getOwnedOrAdminPost(Long rowId, Long actorId, boolean isAdmin) {
        Post post = postRepository.findByIdForUpdate(rowId)
                .orElseThrow(() -> new ResponseStatusException(
                         HttpStatus.NOT_FOUND, "Post not found"));
        accessPolicy.requireMutable(post, actorId, isAdmin);
        return post;
    }

    private void requireActive(Post post) {
        if (post.isDeleted()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }
    }


}
