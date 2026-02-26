package com.example.backend.feed;

import com.example.backend.auth.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class FeedController {
    private final FeedService feedService;
    private final JwtService jwtService;
    public FeedController(FeedService feedService, JwtService jwtService) {
        this.feedService = feedService;
        this.jwtService = jwtService;
    }

    @GetMapping("/api/challenges/{challengeId}/feed")
    public List<FeedPostDto> list(@RequestHeader("Authorization") String authorizationHeader, @PathVariable Long challengeId) {
        jwtService.validateAndGetUserId(authorizationHeader);
        return feedService.list(challengeId);
    }

    @PostMapping("/api/challenges/{challengeId}/feed")
    public ResponseEntity<FeedPostDto> create(@RequestHeader("Authorization") String authorizationHeader,
                                              @PathVariable Long challengeId,
                                              @RequestBody FeedPostRequestDto request) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(feedService.create(userId, challengeId, request));
    }

    @DeleteMapping("/api/feed/{postId}")
    public ResponseEntity<Void> delete(@RequestHeader("Authorization") String authorizationHeader, @PathVariable Long postId) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        feedService.delete(userId, postId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/feed/{postId}/comments")
    public List<FeedCommentDto> listComments(@RequestHeader("Authorization") String authorizationHeader, @PathVariable Long postId) {
        jwtService.validateAndGetUserId(authorizationHeader);
        return feedService.listComments(postId);
    }

    @PostMapping("/api/feed/{postId}/comments")
    public ResponseEntity<FeedCommentDto> createComment(@RequestHeader("Authorization") String authorizationHeader,
                                                        @PathVariable Long postId,
                                                        @RequestBody FeedCommentRequestDto request) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(feedService.createComment(userId, postId, request));
    }

    @DeleteMapping("/api/feed/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@RequestHeader("Authorization") String authorizationHeader, @PathVariable Long commentId) {
        Long userId = jwtService.validateAndGetUserId(authorizationHeader);
        feedService.deleteComment(userId, commentId);
        return ResponseEntity.noContent().build();
    }
}
