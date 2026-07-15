package com.myongjithon.syncday.domain.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "F0. 게스트 프로필", description = "회원가입 없이 닉네임·캠퍼스만으로 임시 유저를 생성합니다")
public class UserController {

    private final UserService userService;

    @Operation(summary = "게스트 유저 생성", description = """
            로그인/회원가입 없이 닉네임·캠퍼스만 받아 유저를 하나 새로 만듭니다. 중복 닉네임도 허용됩니다.
            FE는 응답의 userId를 세션 동안만(예: sessionStorage) 들고 있다가 이후 모든 API 호출에 사용하면 됩니다 —
            탭을 닫으면 그 userId를 아는 곳이 없어지므로 사실상 그 유저는 다시 접근할 수 없게 됩니다.""")
    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        AppUser user = userService.create(request.campus(), request.nickname());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.of(user));
    }
}