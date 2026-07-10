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
 */
@Entity
@Table(name = "analysis_result")
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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public AnalysisResult(AppUser user, LocalDate analysisDate, String featuresJson) {
        this.user = user;
        this.analysisDate = analysisDate;
        this.featuresJson = featuresJson;
        this.createdAt = LocalDateTime.now();
    }
}
