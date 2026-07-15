package com.myongjithon.syncday.domain.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository appUserRepository;

    @Transactional
    public AppUser create(Campus campus, String nickname) {
        AppUser user = new AppUser(campus, nickname);
        return appUserRepository.save(user);
    }
}