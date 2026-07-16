package com.myongjithon.syncday.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.myongjithon.syncday.global.exception.GlobalErrorCode.INVALID_REQUEST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// @Valid 검증 실패 → GlobalExceptionHandler가 {code, message}로 통일하는 흐름은
// 스프링 디스패처를 실제로 타야만 검증되므로(UserControllerTest는 컨트롤러 메서드를 직접 호출해
// 이 흐름을 거치지 않는다), MockMvc로 진짜 HTTP 요청을 보내는 슬라이스 테스트로 따로 검증한다.
@WebMvcTest(UserController.class)
class UserControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("닉네임 누락 시 400과 INVALID_REQUEST를 반환한다")
    void missingNicknameReturnsInvalidRequest() throws Exception {
        String body = """
                {"campus": "NATURAL"}
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST.name()));
    }

    @Test
    @DisplayName("campus에 정의되지 않은 값이 오면 400과 INVALID_REQUEST를 반환한다")
    void invalidCampusReturnsInvalidRequest() throws Exception {
        String body = """
                {"nickname": "서연", "campus": "잘못된값"}
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(INVALID_REQUEST.name()));
    }
}
