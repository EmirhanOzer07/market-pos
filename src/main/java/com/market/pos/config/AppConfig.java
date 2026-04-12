package com.market.pos.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Uygulama geneli bean tanımları ve önbellekleme yapılandırması.
 *
 * <p>BCrypt şifre kodlayıcısını ve Caffeine tabanlı önbellek yöneticisini kaydeder.</p>
 */
@Configuration
@EnableCaching
public class AppConfig {


    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}