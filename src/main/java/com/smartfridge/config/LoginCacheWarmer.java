package com.smartfridge.config;

import com.smartfridge.entity.FridgeItem;
import com.smartfridge.entity.User;
import com.smartfridge.service.FridgeCacheService;
import com.smartfridge.service.FridgeService;
import com.smartfridge.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LoginCacheWarmer implements ApplicationListener<AuthenticationSuccessEvent> {

    private final UserService userService;
    private final FridgeService fridgeService;
    private final FridgeCacheService cacheService;

    @Override
    @Async
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        User user = userService.findByUsername(event.getAuthentication().getName()).orElseThrow();
        List<FridgeItem> items = fridgeService.findByUser(user);
        cacheService.warmCache(user.getId(), items);
    }
}
