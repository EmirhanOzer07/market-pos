package com.market.pos.controller;

import com.market.pos.dto.KasiyerEkleIstegi;
import com.market.pos.dto.SifreDegistirIstek;
import com.market.pos.entity.Kullanici;
import com.market.pos.entity.Market;
import com.market.pos.entity.Satis;
import com.market.pos.repository.KullaniciRepository;
import com.market.pos.repository.MarketRepository;
import com.market.pos.repository.SatisDetayRepository;
import com.market.pos.repository.SatisRepository;
import com.market.pos.security.AuditLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kullanicilar")
public class KullaniciController {

    @Autowired private KullaniciRepository kullaniciRepository;
    @Autowired private MarketRepository marketRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private SatisDetayRepository satisDetayRepository;
    @Autowired private SatisRepository satisRepository;
    @Autowired private AuditLogger auditLogger;

    private Kullanici getAktifKullanici() {
        String kullaniciAdi = SecurityContextHolder.getContext().getAuthentication().getName();
        return kullaniciRepository.findByKullaniciAdi(kullaniciAdi);
    }

    @GetMapping("/liste/{marketId}")
    @PreAuthorize("isAuthenticated()")    public List<Kullanici> personelleriGetir(@PathVariable Long marketId) {
        Kullanici aktif = getAktifKullanici();
        if (!aktif.getMarket().getId().equals(marketId)) {
            throw new SecurityException("Başka marketin personelini göremezsiniz!");
        }
        return kullaniciRepository.findAllByMarketId(marketId);
    }

    @PostMapping("/ekle/{marketId}")
    @PreAuthorize("hasRole('ADMIN')")    public String kasiyerEkle(@PathVariable Long marketId, @RequestBody KasiyerEkleIstegi istek) {

        Kullanici aktif = getAktifKullanici();

        if (!aktif.getMarket().getId().equals(marketId)) {
            throw new SecurityException("Başka markete kasiyer ekleyemezsiniz!");
        }
        if (istek.getKullaniciAdi() == null || istek.getKullaniciAdi().isBlank()) {
            throw new IllegalArgumentException("Kullanıcı adı boş olamaz!");
        }
        if (istek.getSifre() == null || istek.getSifre().length() < 8) {
            throw new IllegalArgumentException("Şifre en az 8 karakter olmalı!");
        }
        if (kullaniciRepository.findByKullaniciAdi(istek.getKullaniciAdi()) != null) {
            throw new IllegalArgumentException("Bu kullanıcı adı zaten alınmış!");
        }

        Market market = marketRepository.findById(marketId).orElseThrow();

        Kullanici yeniKasiyer = new Kullanici();
        yeniKasiyer.setKullaniciAdi(istek.getKullaniciAdi());
        yeniKasiyer.setSifre(passwordEncoder.encode(istek.getSifre()));
        yeniKasiyer.setMarket(market);
        yeniKasiyer.setRol("KASIYER");

        kullaniciRepository.save(yeniKasiyer);

        auditLogger.logUserCreation(yeniKasiyer.getKullaniciAdi(), aktif.getKullaniciAdi());

        return "Başarılı";
    }

    @PutMapping("/sifre-degistir")
    @PreAuthorize("isAuthenticated()")
    public String sifreDegistir(@RequestBody SifreDegistirIstek istek) {
        Kullanici aktif = getAktifKullanici();

        if (istek.getEskiSifre() == null || !passwordEncoder.matches(istek.getEskiSifre(), aktif.getSifre())) {
            throw new IllegalArgumentException("Mevcut şifre hatalı!");
        }
        if (istek.getYeniSifre() == null || istek.getYeniSifre().length() < 8) {
            throw new IllegalArgumentException("Yeni şifre en az 8 karakter olmalı!");
        }

        aktif.setSifre(passwordEncoder.encode(istek.getYeniSifre()));
        kullaniciRepository.save(aktif);
        auditLogger.logUserCreation(aktif.getKullaniciAdi() + " [şifre değiştirdi]", aktif.getKullaniciAdi());
        return "Başarılı";
    }

    @DeleteMapping("/sil/{id}")
    @PreAuthorize("hasRole('ADMIN')")    public String personelSil(@PathVariable Long id) {

        Kullanici aktif = getAktifKullanici();

        Kullanici silinecek = kullaniciRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı!"));

        if (!silinecek.getMarket().getId().equals(aktif.getMarket().getId())) {
            throw new SecurityException("Başka marketin personelini silemezsiniz!");
        }
        if (silinecek.getId().equals(aktif.getId())) {
            throw new IllegalArgumentException("Kendinizi silemezsiniz!");
        }

        List<Satis> satirlari = satisRepository.findAllByKullaniciId(id);
        for (Satis satis : satirlari) {
            satisDetayRepository.deleteBySatisId(satis.getId());
        }

        satisRepository.deleteByKullaniciId(id);
        kullaniciRepository.deleteById(id);

        auditLogger.logUserDeletion(id, aktif.getKullaniciAdi());

        return "Silindi";
    }
}