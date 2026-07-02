package com.myongjithon.syncday.domain.photo;

import org.springframework.data.jpa.repository.JpaRepository;

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
}