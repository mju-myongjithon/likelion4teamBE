package com.myongjithon.syncday.domain.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, UUID> {
    Optional<AnalysisResult> findByUser_UserIdAndAnalysisDate(UUID userId, LocalDate analysisDate);
}
