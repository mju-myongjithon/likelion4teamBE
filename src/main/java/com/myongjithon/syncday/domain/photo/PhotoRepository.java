package com.myongjithon.syncday.domain.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<Photo, UUID> {
    // 업로드 순서로 정렬 — AI가 매기는 photoIndex(요청에 담긴 사진 순서)와 FE가 보여주는 사진 순서를
    // 같은 기준으로 맞추기 위해 순서를 명시한다(정렬 없이 반환되면 두 순서가 우연히만 일치했음).
    List<Photo> findByUser_UserIdAndUploadedAtBetweenOrderByUploadedAtAsc(
            UUID userId, LocalDateTime start, LocalDateTime end
    );

    int countByUser_UserIdAndUploadedAtBetween(
            UUID userId, LocalDateTime start, LocalDateTime end
    );

    // F2 분석(AnalysisResult)과 사진을 연결한다. 호출부(AnalysisService)에서 @Transactional 필요.
    @Modifying
    @Query("UPDATE Photo p SET p.analysisId = :analysisId WHERE p.photoId IN :photoIds")
    void linkAnalysis(@Param("analysisId") UUID analysisId, @Param("photoIds") List<UUID> photoIds);
}