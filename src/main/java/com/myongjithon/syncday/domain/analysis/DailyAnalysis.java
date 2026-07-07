package com.myongjithon.syncday.domain.analysis;

import com.myongjithon.syncday.domain.match.similarity.AnalysisFeatures;
import com.myongjithon.syncday.domain.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * F2(AI 하루 분석)의 산출물 테이블 매핑. 유저 × 날짜 1건.
 *
 * ⚠ 실제 소유는 F2. F3가 선행 개발을 위해 ERD 기준으로 잠정 정의한 읽기 모델이며,
 *   F2 구현 병합 시 하나로 일원화해야 한다. (docs/f3-handoff-from-f2.md §6)
 */
@Entity
@Table(name = "daily_analysis")
@Getter
@NoArgsConstructor
public class DailyAnalysis {

    @Id
    @GeneratedValue
    private UUID analysisId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false)
    private LocalDate date;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "scene_tags", columnDefinition = "text[]")
    private List<String> sceneTags;

    @Column(name = "time_of_day")
    private String timeOfDay;

    @Column
    private String mood;

    @Column(name = "dominant_color")
    private String dominantColor;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "activity_tags", columnDefinition = "text[]")
    private List<String> activityTags;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_ai_response", columnDefinition = "jsonb")
    private String rawAiResponse;

    @Builder
    public DailyAnalysis(AppUser user, LocalDate date, List<String> sceneTags, String timeOfDay,
                         String mood, String dominantColor, List<String> activityTags, String rawAiResponse) {
        this.user = user;
        this.date = date;
        this.sceneTags = sceneTags;
        this.timeOfDay = timeOfDay;
        this.mood = mood;
        this.dominantColor = dominantColor;
        this.activityTags = activityTags;
        this.rawAiResponse = rawAiResponse;
    }

    /** 유사도 계산기 입력으로 변환. 엔티티(F2 소유)와 계산 로직(F3 소유)의 결합을 끊는 지점. */
    public AnalysisFeatures toFeatures() {
        return new AnalysisFeatures(sceneTags, timeOfDay, mood, dominantColor, activityTags);
    }
}
