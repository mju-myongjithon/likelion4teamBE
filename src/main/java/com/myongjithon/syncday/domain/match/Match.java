package com.myongjithon.syncday.domain.match;

import com.myongjithon.syncday.domain.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * F3 유사도 매칭 결과. 같은 날짜의 반대 캠퍼스 두 유저를 잇는다.
 *
 * (user_a, user_b) 는 UUID 오름차순으로 정규화 저장해 (A,B)/(B,A) 중복 행을 막는다.
 *
 * <p>게이트2(채팅 참여): 매칭 성사 후 각 유저가 채팅을 열지 정한다({@link Gate2Decision}).
 * 양쪽 모두 ACCEPTED 되면 {@code connectedAt} 이 1회 기록되고, 이 값이 F5가 채팅방을 여는 유일한 신호다.
 * 한 명이라도 REJECTED 면 그날 매칭은 종료(ENDED)되며, 연결/종료는 되돌릴 수 없다.
 *
 * <p>기존 {@code revealedTo*} 플래그는 게이트2 도입 전 F5용으로 예약됐던 값으로, 현재 응답 게이팅은
 * 상태(CONNECTED)로 대체됐다. 소유·의미 재합의는 설계 문서의 열린 항목 #3 참고.
 */
@Entity
@Table(
        name = "match",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_match_pair_date",
                columnNames = {"user_a_id", "user_b_id", "date"}
        )
)
@Getter
@NoArgsConstructor
public class Match {

    @Id
    @GeneratedValue
    private UUID matchId;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_a_id", nullable = false)
    private AppUser userA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_b_id", nullable = false)
    private AppUser userB;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "similarity_score", nullable = false)
    private int similarityScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "score_breakdown", columnDefinition = "jsonb")
    private String scoreBreakdown;

    @Column(name = "is_revealed_to_a", nullable = false)
    private boolean revealedToA;

    @Column(name = "is_revealed_to_b", nullable = false)
    private boolean revealedToB;

    // 게이트2 결정. 기존 행/신규 컬럼 호환을 위해 nullable 로 두고, 읽을 때 null 을 PENDING 으로 정규화한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "chat_decision_a")
    private Gate2Decision chatDecisionA;

    @Enumerated(EnumType.STRING)
    @Column(name = "chat_decision_b")
    private Gate2Decision chatDecisionB;

    /** 양쪽 게이트2 ACCEPTED 가 된 시각. null 이면 미연결. F5가 채팅방을 여는 유일한 신호. */
    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    /** F4(ai-service)가 생성한 유사도 코멘트. 연결(CONNECTED) 시점에 1회 생성되며 그 전엔 null. */
    @Column(name = "ai_comment", columnDefinition = "TEXT")
    private String aiComment;

    @Builder(access = lombok.AccessLevel.PRIVATE)
    private Match(AppUser userA, AppUser userB, LocalDate date, int similarityScore, String scoreBreakdown) {
        this.userA = userA;
        this.userB = userB;
        this.date = date;
        this.similarityScore = similarityScore;
        this.scoreBreakdown = scoreBreakdown;
        this.revealedToA = false;
        this.revealedToB = false;
        this.chatDecisionA = Gate2Decision.PENDING;
        this.chatDecisionB = Gate2Decision.PENDING;
    }

    /**
     * 두 유저의 매칭을 생성한다. user_a/user_b 를 UUID 오름차순으로 정규화해
     * (A,B)와 (B,A)가 서로 다른 행으로 저장되는 것을 방지한다.
     */
    public static Match create(AppUser one, AppUser other, LocalDate date,
                               int similarityScore, String scoreBreakdown) {
        boolean oneFirst = one.getUserId().compareTo(other.getUserId()) <= 0;
        AppUser a = oneFirst ? one : other;
        AppUser b = oneFirst ? other : one;
        return Match.builder()
                .userA(a)
                .userB(b)
                .date(date)
                .similarityScore(similarityScore)
                .scoreBreakdown(scoreBreakdown)
                .build();
    }

    /** 뷰어가 userA 인지. 뷰어 관점(내 결정/상대 결정)을 계산하는 기준. */
    public boolean isUserA(UUID viewerId) {
        return userA.getUserId().equals(viewerId);
    }

    /** 뷰어 본인의 게이트2 결정(null 은 PENDING 으로 정규화). */
    public Gate2Decision chatDecisionOf(UUID viewerId) {
        return normalize(isUserA(viewerId) ? chatDecisionA : chatDecisionB);
    }

    /** 뷰어 상대의 게이트2 결정(null 은 PENDING 으로 정규화). */
    public Gate2Decision partnerChatDecisionOf(UUID viewerId) {
        return normalize(isUserA(viewerId) ? chatDecisionB : chatDecisionA);
    }

    /** 양쪽 모두 채팅을 수락해 연결됐는지. */
    public boolean isConnected() {
        return connectedAt != null;
    }

    /**
     * 뷰어의 게이트2 결정을 반영한다. 양쪽이 ACCEPTED 가 되면 connectedAt 을 1회 기록한다.
     * 이미 연결(CONNECTED)됐거나 종료(누군가 REJECTED)된 뒤에는 결정을 바꿀 수 없다(멱등·터미널).
     */
    public void applyChatDecision(UUID viewerId, Gate2Decision decision) {
        if (isConnected() || isEnded()) {
            return; // 터미널 상태: 더 이상 바꾸지 않음
        }
        if (isUserA(viewerId)) {
            this.chatDecisionA = decision;
        } else {
            this.chatDecisionB = decision;
        }
        if (normalize(chatDecisionA) == Gate2Decision.ACCEPTED
                && normalize(chatDecisionB) == Gate2Decision.ACCEPTED) {
            this.connectedAt = LocalDateTime.now();
        }
    }

    /** F4가 생성한 AI 코멘트를 부여한다(연결 시점 1회). */
    public void assignAiComment(String comment) {
        this.aiComment = comment;
    }

    /** 게이트2에서 한 명이라도 거부해 종료됐는지. */
    public boolean isEnded() {
        return normalize(chatDecisionA) == Gate2Decision.REJECTED
                || normalize(chatDecisionB) == Gate2Decision.REJECTED;
    }

    private static Gate2Decision normalize(Gate2Decision decision) {
        return decision == null ? Gate2Decision.PENDING : decision;
    }
}
