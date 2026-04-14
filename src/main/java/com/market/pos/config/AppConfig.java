package com.market.pos.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.concurrent.TimeUnit;

/**
 * Uygulama geneli bean tanımları ve önbellekleme yapılandırması.
 *
 * <p>BCrypt şifre kodlayıcısını ve her cache için ayrı boyut/TTL ile
 * Caffeine tabanlı önbellek yöneticisini kaydeder.</p>
 */
@Configuration
@EnableCaching
public class AppConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        // Kullanıcı bilgisi — her kullanıcı için 30 sn, en fazla 500 giriş
        manager.registerCustomCache("kullaniciBilgisi",
                Caffeine.newBuilder().maximumSize(500).expireAfterWrite(30, TimeUnit.SECONDS).build());
        // Ürün listesi — market başına, en fazla 50 market
        manager.registerCustomCache("urunListesi",
                Caffeine.newBuilder().maximumSize(50).expireAfterWrite(30, TimeUnit.SECONDS).build());
        // Satış özeti — market başına, 30 sn TTL (satış kaydında evict edilir)
        manager.registerCustomCache("satisOzeti",
                Caffeine.newBuilder().maximumSize(50).expireAfterWrite(30, TimeUnit.SECONDS).build());
        return manager;
    }
}