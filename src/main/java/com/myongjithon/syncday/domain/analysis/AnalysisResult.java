package com.myongjithon.syncday.domain.analysis;

import com.myongjithon.syncday.domain.user.AppUser;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AI 서비스(F2)가 반환한 하루치 features 결과 저장용 엔티티.
 * featuresJson은 ai-service의 DayFeatures를 그대로 직렬화한 JSON 문자열이다
 * (F3/F4/F6 호출 시 다시 역직렬화해서 그대로 넘기면 된다).
 *
 * (user_id, analysis_date) 유니크 제약: "분석하기"가 짧은 간격으로 중복 호출돼도
 * (더블클릭·네트워크 재시도 등) 같은 유저·날짜에 행이 2개 생기지 않도록 막는다.
 * 이 제약이 없으면 조회 쿼리(findByUser_UserIdAndAnalysisDate)가 2건을 만나
 * NonUniqueResultException으로 이후 조회가 영구적으로 실패한다.
 */
@Entity
@Table(
        name = "analysis_result",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_analysis_user_date",
                columnNames = {"user_id", "analysis_date"}
        )
)
@Getter
@NoArgsConstructor
public class AnalysisResult {

    @Id
    @GeneratedValue
    private UUID analysisId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "analysis_date", nullable = false)
    private LocalDate analysisDate;

    @Column(name = "features_json", columnDefinition = "TEXT", nullable = false)
    private String featuresJson;

    /**
     * 매칭 참여 결정(F3 게이트1). 분석 직후에는 NONE 이고, 유저가 "매칭 수락/거부"를 누르면 바뀐다.
     * 매칭 후보 쿼리는 ACCEPTED 만 고른다. (컬럼은 nullable — 이 필드 도입 이전 행은 null 이며, null 은 NONE 과 동일하게 취급된다)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "match_decision", length = 20)
    private MatchDecision matchDecision = MatchDecision.NONE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public AnalysisResult(AppUser user, LocalDate analysisDate, String featuresJson) {
        this.user = user;
        this.analysisDate = analysisDate;
        this.featuresJson = featuresJson;
        this.matchDecision = MatchDecision.NONE;
        this.createdAt = LocalDateTime.now();
    }

    /** 매칭 수락(opt-in). 이 분석의 유저를 매칭 후보 풀에 넣는다. */
    public void acceptMatching() {
        this.matchDecision = MatchDecision.ACCEPTED;
    }

    /** 매칭 거부. 후보 풀에서 빠진다. */
    public void declineMatching() {
        this.matchDecision = MatchDecision.DECLINED;
    }

    /** 매칭을 수락한 상태인지. (null=이전 데이터 → 미수락으로 본다) */
    public boolean hasAcceptedMatching() {
        return this.matchDecision == MatchDecision.ACCEPTED;
    }
}
