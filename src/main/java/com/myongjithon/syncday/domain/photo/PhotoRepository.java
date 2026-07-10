package com.myongjithon.syncday.domain.photo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<Photo, UUID> {
    List<Photo> findByUser_UserIdAndUploadedAtBetween(
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