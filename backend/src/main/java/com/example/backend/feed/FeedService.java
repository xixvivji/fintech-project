package com.example.backend.feed;

import com.example.backend.auth.UserEntity;
import com.example.backend.auth.UserRepository;
import com.example.backend.challenge.ChallengeParticipantRepository;
import com.example.backend.challenge.ChallengeRepository;
import com.example.backend.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FeedService {
    private final FeedPostRepository feedPostRepository;
    private final FeedCommentRepository feedCommentRepository;
    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository challengeParticipantRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public FeedService(FeedPostRepository feedPostRepository, FeedCommentRepository feedCommentRepository, ChallengeRepository challengeRepository,
                       ChallengeParticipantRepository challengeParticipantRepository, UserRepository userRepository,
                       NotificationService notificationService) {
        this.feedPostRepository = feedPostRepository;
        this.feedCommentRepository = feedCommentRepository;
        this.challengeRepository = challengeRepository;
        this.challengeParticipantRepository = challengeParticipantRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<FeedPostDto> list(Long challengeId) {
        challengeRepository.findById(challengeId).orElseThrow(() -> new IllegalArgumentException("Challenge not found."));
        return feedPostRepository.findByChallengeIdAndDeletedYnFalseOrderByCreatedAtDesc(challengeId).stream().map(this::toDto).toList();
    }

    @Transactional
    public FeedPostDto create(Long userId, Long challengeId, FeedPostRequestDto request) {
        challengeRepository.findById(challengeId).orElseThrow(() -> new IllegalArgumentException("Challenge not found."));
        challengeParticipantRepository.findByChallengeIdAndUserId(challengeId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Only participants can post."));
        if (request == null || request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("content is required.");
        }
        String content = request.getContent().trim();
        if (content.length() > 2000) throw new IllegalArgumentException("content too long.");
        FeedPostEntity e = new FeedPostEntity();
        e.setChallengeId(challengeId);
        e.setUserId(userId);
        e.setContent(content);
        e.setCreatedAt(System.currentTimeMillis());
        e.setDeletedYn(false);
        FeedPostEntity saved = feedPostRepository.save(e);
        Long ownerId = challengeRepository.findById(challengeId).orElseThrow().getOwnerUserId();
        if (!ownerId.equals(userId)) {
            UserEntity writer = userRepository.findById(userId).orElseThrow();
            notificationService.createForUser(ownerId, "CHALLENGE_FEED_POST", "챌린지 새 피드",
                    writer.getName() + "님이 챌린지 피드를 작성했습니다.", "CHALLENGE", challengeId);
        }
        return toDto(saved);
    }

    @Transactional
    public void delete(Long userId, Long postId) {
        FeedPostEntity e = feedPostRepository.findById(postId).orElseThrow(() -> new IllegalArgumentException("Post not found."));
        if (!e.getUserId().equals(userId)) throw new IllegalArgumentException("Not allowed.");
        e.setDeletedYn(true);
        feedPostRepository.save(e);
    }

    @Transactional(readOnly = true)
    public List<FeedCommentDto> listComments(Long postId) {
        feedPostRepository.findById(postId).orElseThrow(() -> new IllegalArgumentException("Post not found."));
        return feedCommentRepository.findByPostIdAndDeletedYnFalseOrderByCreatedAtAsc(postId).stream().map(this::toCommentDto).toList();
    }

    @Transactional
    public FeedCommentDto createComment(Long userId, Long postId, FeedCommentRequestDto request) {
        FeedPostEntity post = feedPostRepository.findById(postId).orElseThrow(() -> new IllegalArgumentException("Post not found."));
        challengeParticipantRepository.findByChallengeIdAndUserId(post.getChallengeId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Only participants can comment."));
        if (request == null || request.getContent() == null || request.getContent().isBlank()) {
            throw new IllegalArgumentException("content is required.");
        }
        String content = request.getContent().trim();
        if (content.length() > 1000) throw new IllegalArgumentException("content too long.");
        FeedCommentEntity c = new FeedCommentEntity();
        c.setPostId(postId);
        c.setUserId(userId);
        c.setContent(content);
        c.setCreatedAt(System.currentTimeMillis());
        c.setDeletedYn(false);
        FeedCommentEntity saved = feedCommentRepository.save(c);
        if (!post.getUserId().equals(userId)) {
            UserEntity writer = userRepository.findById(userId).orElseThrow();
            notificationService.createForUser(post.getUserId(), "CHALLENGE_FEED_COMMENT", "피드 댓글",
                    writer.getName() + "님이 내 피드에 댓글을 남겼습니다.", "CHALLENGE", post.getChallengeId());
        }
        return toCommentDto(saved);
    }

    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        FeedCommentEntity c = feedCommentRepository.findById(commentId).orElseThrow(() -> new IllegalArgumentException("Comment not found."));
        if (!c.getUserId().equals(userId)) throw new IllegalArgumentException("Not allowed.");
        c.setDeletedYn(true);
        feedCommentRepository.save(c);
    }

    private FeedPostDto toDto(FeedPostEntity e) {
        String userName = userRepository.findById(e.getUserId()).map(UserEntity::getName).orElse("Unknown");
        return new FeedPostDto(e.getId(), e.getChallengeId(), e.getUserId(), userName, e.getContent(), e.getCreatedAt());
    }

    private FeedCommentDto toCommentDto(FeedCommentEntity c) {
        String userName = userRepository.findById(c.getUserId()).map(UserEntity::getName).orElse("Unknown");
        return new FeedCommentDto(c.getId(), c.getPostId(), c.getUserId(), userName, c.getContent(), c.getCreatedAt());
    }
}
