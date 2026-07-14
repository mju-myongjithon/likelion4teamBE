package com.myongjithon.syncday.domain.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UserCreateRequest(
        @NotBlank @Size(max = 50) String nickname,
        @NotNull Campus campus
) {}

