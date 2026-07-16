package com.myongjithon.syncday.domain.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(appUserRepository);
    }

    @Test
    @DisplayName("닉네임·캠퍼스로 게스트 유저를 생성해 저장한다")
    void createsAndSavesUser() {
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser created = userService.create(Campus.HUMANITIES, "지훈");

        assertThat(created.getCampus()).isEqualTo(Campus.HUMANITIES);
        assertThat(created.getNickname()).isEqualTo("지훈");

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(saved.capture());
        assertThat(saved.getValue().getNickname()).isEqualTo("지훈");
    }
}
