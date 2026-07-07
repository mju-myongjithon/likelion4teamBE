package com.myongjithon.syncday.domain.match;

import com.myongjithon.syncday.domain.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.UUID;

/**
 * F3 유사도 매칭 결과. 같은 날짜의 반대 캠퍼스 두 유저를 잇는다.
 *
 * (user_a, user_b) 는 UUID 오름차순으로 정규화 저장해 (A,B)/(B,A) 중복 행을 막는다.
 * 공개 플래그(revealedTo*)는 항상 false 로 시작하며, 실제 공개 게이팅은 F5가 담당한다.
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

    @Builder(access = lombok.AccessLevel.PRIVATE)
    private Match(AppUser userA, AppUser userB, LocalDate date, int similarityScore, String scoreBreakdown) {
        this.userA = userA;
        this.userB = userB;
        this.date = date;
        this.similarityScore = similarityScore;
        this.scoreBreakdown = scoreBreakdown;
        this.revealedToA = false;
        this.revealedToB = false;
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
}
