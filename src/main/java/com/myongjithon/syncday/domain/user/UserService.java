package com.myongjithon.syncday.domain.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final AppUserRepository appUserRepository;

    public UserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public AppUser create(Campus campus, String nickname) {
        AppUser user = new AppUser(campus, nickname);
        return appUserRepository.save(user);
    }
}