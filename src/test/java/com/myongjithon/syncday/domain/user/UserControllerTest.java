package com.myongjithon.syncday.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @Test
    @DisplayName("게스트 유저 생성: 201과 함께 userId·닉네임·캠퍼스를 반환한다")
    void createUserReturnsCreated() {
        UUID userId = UUID.randomUUID();
        AppUser created = mock(AppUser.class);
        when(created.getUserId()).thenReturn(userId);
        when(created.getNickname()).thenReturn("서연");
        when(created.getCampus()).thenReturn(Campus.NATURAL);
        when(userService.create(eq(Campus.NATURAL), eq("서연"))).thenReturn(created);

        ResponseEntity<UserResponse> response =
                userController.createUser(new UserCreateRequest("서연", Campus.NATURAL));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().userId()).isEqualTo(userId);
        assertThat(response.getBody().nickname()).isEqualTo("서연");
        assertThat(response.getBody().campus()).isEqualTo(Campus.NATURAL);
    }
}
