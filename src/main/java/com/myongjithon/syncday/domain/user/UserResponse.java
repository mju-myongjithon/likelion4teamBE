package com.myongjithon.syncday.domain.user;

import java.util.UUID;

public record UserResponse(UUID userId, String nickname, Campus campus) {
    public static UserResponse of(AppUser user) {
        return new UserResponse(user.getUserId(), user.getNickname(), user.getCampus());
    }
}

