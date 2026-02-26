package com.example.backend.challenge;

import com.example.backend.auth.UserEntity;
import com.example.backend.auth.UserRepository;
import com.example.backend.feed.FeedCommentRepository;
import com.example.backend.feed.FeedPostEntity;
import com.example.backend.feed.FeedPostRepository;
import com.example.backend.notification.NotificationService;
import com.example.backend.simulation.PortfolioResponseDto;
import com.example.backend.simulation.SimLeagueStateDto;
import com.example.backend.simulation.SimulationService;
import com.example.backend.simulation.TradeExecutionDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class ChallengeService {
    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository challengeParticipantRepository;
    private final UserRepository userRepository;
    private final SimulationService simulationService;
    private final NotificationService notificationService;
    private final FeedPostRepository feedPostRepository;
    private final FeedCommentRepository feedCommentRepository;

    public ChallengeService(ChallengeRepository challengeRepository,
                            ChallengeParticipantRepository challengeParticipantRepository,
                            UserRepository userRepository,
                            SimulationService simulationService,
                            NotificationService notificationService,
                            FeedPostRepository feedPostRepository,
                            FeedCommentRepository feedCommentRepository) {
        this.challengeRepository = challengeRepository;
        this.challengeParticipantRepository = challengeParticipantRepository;
        this.userRepository = userRepository;
        this.simulationService = simulationService;
        this.notificationService = notificationService;
        this.feedPostRepository = feedPostRepository;
        this.feedCommentRepository = feedCommentRepository;
    }

    @Transactional
    public ChallengeDto create(Long userId, ChallengeCreateRequestDto request) {
        if (request == null) throw new IllegalArgumentException("Challenge request is empty.");
        ChallengeEntity e = new ChallengeEntity();
        e.setOwnerUserId(userId);
        e.setTitle(requireText(request.getTitle(), 2, 100, "title"));
        e.setDescription(requireText(request.getDescription(), 1, 1000, "description"));
        String goalType = normalizeGoalType(request.getGoalType());
        e.setGoalType(goalType);
        if ("RETURN_RATE".equals(goalType)) {
            if (request.getTargetValue() == null || request.getTargetValue() <= 0) throw new IllegalArgumentException("targetValue must be greater than 0.");
            e.setTargetValue(request.getTargetValue());
            e.setHabitCode(null);
            e.setHabitDailyBuyQuantity(null);
            e.setHabitRequiredDays(null);
        } else {
            e.setTargetValue(request.getTargetValue() == null ? 0 : request.getTargetValue());
            String habitCode = normalizeHabitCode(request.getHabitCode());
            int dailyQty = request.getHabitDailyBuyQuantity() == null ? 1 : request.getHabitDailyBuyQuantity();
            int requiredDays = request.getHabitRequiredDays() == null ? 10 : request.getHabitRequiredDays();
            if (dailyQty < 1 || dailyQty > 100000) throw new IllegalArgumentException("habitDailyBuyQuantity out of range.");
            if (requiredDays < 1 || requiredDays > 365) throw new IllegalArgumentException("habitRequiredDays out of range.");
            e.setHabitCode(habitCode);
            e.setHabitDailyBuyQuantity(dailyQty);
            e.setHabitRequiredDays(requiredDays);
        }
        String visibility = normalizeVisibility(request.getVisibility());
        e.setVisibility(visibility);
        e.setPrivatePassword(normalizePrivatePassword(visibility, request.getPrivatePassword()));
        int maxParticipants = request.getMaxParticipants() == null ? 100 : request.getMaxParticipants();
        if (maxParticipants < 1 || maxParticipants > 10000) throw new IllegalArgumentException("maxParticipants out of range.");
        e.setMaxParticipants(maxParticipants);

        LocalDate start = parseDate(request.getStartDate(), "startDate");
        LocalDate end = parseDate(request.getEndDate(), "endDate");
        if (end.isBefore(start)) throw new IllegalArgumentException("endDate must be on or after startDate.");
        e.setStartDate(start.toString());
        e.setEndDate(end.toString());
        e.setCreatedAt(System.currentTimeMillis());

        ChallengeEntity saved = challengeRepository.save(e);
        autoJoinOwner(saved, userId);
        notificationService.createForUser(userId, "CHALLENGE_CREATED", "챌린지 생성 완료",
                "'" + saved.getTitle() + "' 챌린지가 생성되었습니다.", "CHALLENGE", saved.getId());
        return toDto(saved, userId, resolveReferenceDate(userId));
    }

    @Transactional(readOnly = true)
    public List<ChallengeDto> list(Long userId) {
        LocalDate refDate = resolveReferenceDate(userId);
        List<ChallengeEntity> rows = challengeRepository.findAll();
        rows.sort(Comparator.comparing(ChallengeEntity::getCreatedAt).reversed());
        List<ChallengeDto> out = new ArrayList<>();
        for (ChallengeEntity row : rows) {
            out.add(toDto(row, userId, refDate));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public ChallengeDto get(Long userId, Long challengeId, String password) {
        ChallengeEntity challenge = getChallenge(challengeId);
        validatePrivateAccess(userId, challenge, password);
        return toDto(challenge, userId, resolveReferenceDate(userId));
    }

    @Transactional
    public ChallengeParticipantDto join(Long userId, Long challengeId, String password) {
        ChallengeEntity challenge = getChallenge(challengeId);
        validatePrivateAccess(userId, challenge, password);
        LocalDate refDate = resolveReferenceDate(userId);
        if (refDate.isAfter(LocalDate.parse(challenge.getEndDate()))) {
            throw new IllegalArgumentException("Ended challenge cannot be joined.");
        }
        if (challengeParticipantRepository.findByChallengeIdAndUserId(challengeId, userId).isPresent()) {
            throw new IllegalArgumentException("Already joined.");
        }
        if (challengeParticipantRepository.countByChallengeId(challengeId) >= challenge.getMaxParticipants()) {
            throw new IllegalArgumentException("Challenge is full.");
        }
        PortfolioResponseDto pf = simulationService.getPortfolio(userId);
        ChallengeParticipantEntity p = new ChallengeParticipantEntity();
        p.setChallengeId(challengeId);
        p.setUserId(userId);
        p.setJoinedAt(System.currentTimeMillis());
        p.setBaselineTotalValue(pf.getTotalValue());
        p.setBaselineDate(pf.getValuationDate() == null ? LocalDate.now().toString() : pf.getValuationDate());
        ChallengeParticipantEntity saved = challengeParticipantRepository.save(p);

        UserEntity user = getUser(userId);
        notificationService.createForUser(userId, "CHALLENGE_JOINED", "챌린지 참여 완료",
                "'" + challenge.getTitle() + "' 챌린지에 참여했습니다.", "CHALLENGE", challengeId);
        if (!Objects.equals(challenge.getOwnerUserId(), userId)) {
            notificationService.createForUser(challenge.getOwnerUserId(), "CHALLENGE_NEW_PARTICIPANT", "챌린지 새 참여자",
                    user.getName() + "님이 참여했습니다.", "CHALLENGE", challengeId);
        }
        return new ChallengeParticipantDto(userId, user.getName(), saved.getJoinedAt(), round2(saved.getBaselineTotalValue()), saved.getBaselineDate());
    }

    @Transactional
    public void leave(Long userId, Long challengeId) {
        ChallengeEntity challenge = getChallenge(challengeId);
        LocalDate refDate = resolveReferenceDate(userId);
        if (!refDate.isBefore(LocalDate.parse(challenge.getStartDate()))) {
            throw new IllegalArgumentException("Cannot leave after challenge start.");
        }
        challengeParticipantRepository.deleteByChallengeIdAndUserId(challengeId, userId);
    }

    @Transactional
    public void delete(Long userId, Long challengeId) {
        ChallengeEntity challenge = getChallenge(challengeId);
        if (!Objects.equals(challenge.getOwnerUserId(), userId)) {
            throw new IllegalArgumentException("Only challenge owner can delete challenge.");
        }
        for (FeedPostEntity post : feedPostRepository.findByChallengeId(challengeId)) {
            if (post.getId() != null) {
                feedCommentRepository.deleteByPostId(post.getId());
            }
        }
        feedPostRepository.deleteByChallengeId(challengeId);
        challengeParticipantRepository.deleteByChallengeId(challengeId);
        challengeRepository.delete(challenge);
    }

    @Transactional(readOnly = true)
    public List<ChallengeParticipantDto> participants(Long requesterUserId, Long challengeId, String password) {
        ChallengeEntity challenge = getChallenge(challengeId);
        validatePrivateAccess(requesterUserId, challenge, password);
        return challengeParticipantRepository.findByChallengeIdOrderByJoinedAtAsc(challengeId).stream()
                .map(p -> new ChallengeParticipantDto(p.getUserId(), getUser(p.getUserId()).getName(), p.getJoinedAt(), round2(p.getBaselineTotalValue()), p.getBaselineDate()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ChallengeProgressDto progress(Long requesterUserId, Long challengeId, Long targetUserId, String password) {
        ChallengeEntity challenge = getChallenge(challengeId);
        validatePrivateAccess(requesterUserId, challenge, password);
        ChallengeParticipantEntity p = challengeParticipantRepository.findByChallengeIdAndUserId(challengeId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Participant not found."));
        PortfolioResponseDto pf = simulationService.getPortfolio(targetUserId);
        return toProgress(challenge, p, pf, getUser(targetUserId).getName());
    }

    @Transactional(readOnly = true)
    public List<ChallengeHabitDayDto> habitCalendar(Long requesterUserId, Long challengeId, Long targetUserId, String password) {
        ChallengeEntity challenge = getChallenge(challengeId);
        validatePrivateAccess(requesterUserId, challenge, password);
        if (!"DAILY_BUY_QUANTITY".equalsIgnoreCase(challenge.getGoalType())) return List.of();
        challengeParticipantRepository.findByChallengeIdAndUserId(challengeId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Participant not found."));
        return buildHabitCalendarDays(targetUserId, challenge);
    }

    @Transactional(readOnly = true)
    public List<ChallengeLeaderboardRowDto> leaderboard(Long requesterUserId, Long challengeId, String password) {
        ChallengeEntity challenge = getChallenge(challengeId);
        validatePrivateAccess(requesterUserId, challenge, password);
        List<RowCalc> rows = new ArrayList<>();
        for (ChallengeParticipantEntity p : challengeParticipantRepository.findByChallengeIdOrderByJoinedAtAsc(challengeId)) {
            PortfolioResponseDto pf = simulationService.getPortfolio(p.getUserId());
            ChallengeProgressDto progress = toProgress(challenge, p, pf, getUser(p.getUserId()).getName());
            rows.add(new RowCalc(progress, Objects.equals(requesterUserId, p.getUserId())));
        }
        rows.sort(Comparator
                .comparing((RowCalc r) -> r.progress.isAchieved()).reversed()
                .thenComparing((RowCalc r) -> r.progress.getAchievementRate()).reversed()
                .thenComparing((RowCalc r) -> r.progress.getReturnRate()).reversed()
                .thenComparing((RowCalc r) -> r.progress.getUserName(), String.CASE_INSENSITIVE_ORDER));

        List<ChallengeLeaderboardRowDto> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ChallengeProgressDto p = rows.get(i).progress;
            out.add(new ChallengeLeaderboardRowDto(i + 1, p.getUserId(), p.getUserName(), rows.get(i).me, p.isAchieved(),
                    p.getReturnRate(), p.getAchievementRate(), p.getPnl(), p.getCurrentTotalValue(), p.getValuationDate()));
        }
        return out;
    }

    private ChallengeProgressDto toProgress(ChallengeEntity challenge, ChallengeParticipantEntity p, PortfolioResponseDto pf, String userName) {
        double baseline = p.getBaselineTotalValue();
        double current = pf.getTotalValue();
        double pnl = current - baseline;
        double returnRate = baseline == 0 ? 0 : (pnl / baseline) * 100.0;
        double achievementRate;
        boolean achieved;
        Integer habitAchievedDays = null;
        Integer habitRequiredDays = challenge.getHabitRequiredDays();
        if ("DAILY_BUY_QUANTITY".equalsIgnoreCase(challenge.getGoalType())) {
            habitAchievedDays = calcHabitAchievedDays(p.getUserId(), challenge);
            int required = habitRequiredDays == null || habitRequiredDays <= 0 ? 1 : habitRequiredDays;
            achievementRate = (habitAchievedDays * 100.0) / required;
            achieved = habitAchievedDays >= required;
        } else {
            achievementRate = challenge.getTargetValue() == 0 ? 0 : (returnRate / challenge.getTargetValue()) * 100.0;
            achieved = returnRate >= challenge.getTargetValue();
        }
        return new ChallengeProgressDto(challenge.getId(), p.getUserId(), userName,
                round2(baseline), round2(current), round2(pnl), round2(returnRate),
                round2(challenge.getTargetValue()), round2(achievementRate),
                achieved, pf.getValuationDate(),
                challenge.getHabitCode(), challenge.getHabitDailyBuyQuantity(), challenge.getHabitRequiredDays(), habitAchievedDays);
    }

    private ChallengeDto toDto(ChallengeEntity e, Long currentUserId, LocalDate refDate) {
        ChallengeDto dto = new ChallengeDto();
        dto.setId(e.getId());
        dto.setOwnerUserId(e.getOwnerUserId());
        dto.setOwnerUserName(getUser(e.getOwnerUserId()).getName());
        dto.setTitle(e.getTitle());
        dto.setDescription(e.getDescription());
        dto.setGoalType(e.getGoalType());
        dto.setTargetValue(round2(e.getTargetValue()));
        dto.setHabitCode(e.getHabitCode());
        dto.setHabitDailyBuyQuantity(e.getHabitDailyBuyQuantity());
        dto.setHabitRequiredDays(e.getHabitRequiredDays());
        dto.setVisibility(e.getVisibility());
        dto.setMaxParticipants(e.getMaxParticipants());
        dto.setParticipantCount(challengeParticipantRepository.countByChallengeId(e.getId()));
        dto.setStartDate(e.getStartDate());
        dto.setEndDate(e.getEndDate());
        dto.setStatus(resolveStatus(e.getStartDate(), e.getEndDate(), refDate));
        dto.setJoined(challengeParticipantRepository.findByChallengeIdAndUserId(e.getId(), currentUserId).isPresent());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }

    private String resolveStatus(String startDate, String endDate, LocalDate refDate) {
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        if (refDate.isBefore(start)) return "UPCOMING";
        if (refDate.isAfter(end)) return "ENDED";
        return "ONGOING";
    }

    private LocalDate resolveReferenceDate(Long userId) {
        try {
            SimLeagueStateDto leagueState = simulationService.getLeagueState(userId);
            if (leagueState != null && leagueState.getCurrentDate() != null && !leagueState.getCurrentDate().isBlank()) {
                return LocalDate.parse(leagueState.getCurrentDate().trim());
            }
        } catch (Exception ignored) {
        }
        return LocalDate.now();
    }

    private ChallengeEntity getChallenge(Long id) {
        return challengeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Challenge not found."));
    }
    private UserEntity getUser(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found."));
    }
    private String requireText(String s, int min, int max, String field) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(field + " is required.");
        String t = s.trim();
        if (t.length() < min || t.length() > max) throw new IllegalArgumentException(field + " length is invalid.");
        return t;
    }
    private String normalizeGoalType(String s) {
        String v = s == null ? "" : s.trim().toUpperCase(Locale.ROOT);
        if (!"RETURN_RATE".equals(v) && !"DAILY_BUY_QUANTITY".equals(v)) {
            throw new IllegalArgumentException("Supported goalType: RETURN_RATE, DAILY_BUY_QUANTITY.");
        }
        return v;
    }
    private String normalizeHabitCode(String code) {
        if (code == null || !code.trim().matches("\\d{6}")) throw new IllegalArgumentException("habitCode must be 6 digits.");
        return code.trim();
    }
    private String normalizeVisibility(String s) {
        String v = s == null ? "PUBLIC" : s.trim().toUpperCase(Locale.ROOT);
        if (!"PUBLIC".equals(v) && !"PRIVATE".equals(v)) throw new IllegalArgumentException("visibility must be PUBLIC or PRIVATE.");
        return v;
    }
    private String normalizePrivatePassword(String visibility, String password) {
        if (!"PRIVATE".equalsIgnoreCase(visibility)) return null;
        if (password == null || password.isBlank()) throw new IllegalArgumentException("privatePassword is required for PRIVATE challenge.");
        String v = password.trim();
        if (v.length() < 4 || v.length() > 50) throw new IllegalArgumentException("privatePassword length must be 4~50.");
        return v;
    }
    private LocalDate parseDate(String s, String field) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException(field + " is required.");
        try { return LocalDate.parse(s.trim()); } catch (Exception e) { throw new IllegalArgumentException(field + " must be yyyy-MM-dd."); }
    }
    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }

    private void autoJoinOwner(ChallengeEntity challenge, Long ownerUserId) {
        if (challenge == null || challenge.getId() == null || ownerUserId == null) return;
        if (challengeParticipantRepository.findByChallengeIdAndUserId(challenge.getId(), ownerUserId).isPresent()) return;
        PortfolioResponseDto pf = simulationService.getPortfolio(ownerUserId);
        ChallengeParticipantEntity p = new ChallengeParticipantEntity();
        p.setChallengeId(challenge.getId());
        p.setUserId(ownerUserId);
        p.setJoinedAt(System.currentTimeMillis());
        p.setBaselineTotalValue(pf.getTotalValue());
        p.setBaselineDate(pf.getValuationDate() == null ? resolveReferenceDate(ownerUserId).toString() : pf.getValuationDate());
        challengeParticipantRepository.save(p);
    }

    private void validatePrivateAccess(Long userId, ChallengeEntity challenge, String password) {
        if (!"PRIVATE".equalsIgnoreCase(challenge.getVisibility())) return;
        if (Objects.equals(challenge.getOwnerUserId(), userId)) return;
        if (challengeParticipantRepository.findByChallengeIdAndUserId(challenge.getId(), userId).isPresent()) return;
        String expected = challenge.getPrivatePassword();
        if (expected != null && password != null && expected.equals(password.trim())) return;
        throw new IllegalArgumentException("비공개 챌린지 비밀번호가 필요합니다.");
    }

    private int calcHabitAchievedDays(Long userId, ChallengeEntity challenge) {
        return (int) buildHabitCalendarDays(userId, challenge).stream().filter(ChallengeHabitDayDto::isAchieved).count();
    }

    private List<ChallengeHabitDayDto> buildHabitCalendarDays(Long userId, ChallengeEntity challenge) {
        String habitCode = challenge.getHabitCode();
        int dailyQty = challenge.getHabitDailyBuyQuantity() == null ? 1 : challenge.getHabitDailyBuyQuantity();
        if (habitCode == null || habitCode.isBlank()) return List.of();
        LocalDate start = LocalDate.parse(challenge.getStartDate());
        LocalDate end = LocalDate.parse(challenge.getEndDate());
        LocalDate refDate = resolveReferenceDate(userId);
        LocalDate effectiveEnd = refDate.isBefore(end) ? refDate : end;
        if (effectiveEnd.isBefore(start)) return List.of();

        Map<String, Integer> buyQtyByDate = new HashMap<>();
        for (TradeExecutionDto row : simulationService.getTradeExecutions(userId)) {
            if (!"BUY".equalsIgnoreCase(row.getSide())) continue;
            if (!habitCode.equals(row.getCode())) continue;
            String d = row.getValuationDate();
            if (d == null || d.isBlank()) continue;
            LocalDate vd;
            try { vd = LocalDate.parse(d); } catch (Exception e) { continue; }
            if (vd.isBefore(start) || vd.isAfter(effectiveEnd)) continue;
            buyQtyByDate.merge(d, row.getQuantity(), Integer::sum);
        }
        List<ChallengeHabitDayDto> out = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            String date = cursor.toString();
            int qty = buyQtyByDate.getOrDefault(date, 0);
            boolean future = cursor.isAfter(effectiveEnd);
            boolean achieved = !future && qty >= dailyQty;
            out.add(new ChallengeHabitDayDto(date, qty, dailyQty, achieved, future));
            cursor = cursor.plusDays(1);
        }
        return out;
    }

    private static class RowCalc {
        private final ChallengeProgressDto progress;
        private final boolean me;
        private RowCalc(ChallengeProgressDto progress, boolean me) { this.progress = progress; this.me = me; }
    }
}
