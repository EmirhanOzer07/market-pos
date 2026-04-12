package com.market.pos.controller;

import com.market.pos.dto.LoginIstegi;
import com.market.pos.dto.KayitIstegi;
import com.market.pos.entity.Kullanici;
import com.market.pos.entity.Market;
import com.market.pos.entity.DavetiyeKodu;
import com.market.pos.repository.KullaniciRepository;
import com.market.pos.repository.MarketRepository;
import com.market.pos.repository.DavetiyeKoduRepository;
import com.market.pos.security.JwtUtil;
import com.market.pos.security.TokenKaraListesi;
import com.market.pos.security.AuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDate;


@RestController
@RequestMapping("/api/auth")
public class LoginController {

    @Autowired private AuditLogger auditLogger;
    @Autowired private KullaniciRepository kullaniciRepository;
    @Autowired private MarketRepository marketRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TokenKaraListesi karaListesi;
    @Autowired private DavetiyeKoduRepository davetiyeKoduRepository;

    @PostMapping("/kayit")
    @Transactional
    public String kayitOl(@Valid @RequestBody KayitIstegi istek) {

        // 1. Davetiye kodu var mı?
        DavetiyeKodu davetiye = davetiyeKoduRepository.findByKod(istek.getDavetiyeKodu().trim());
        if (davetiye == null) {
            throw new IllegalArgumentException("Girdiğiniz davetiye kodu sistemde bulunamadı!");
        }

        // Davetiye süresi dolmuş mu?
        if (davetiye.getSonKullanmaTarihi() != null
                && LocalDate.now().isAfter(davetiye.getSonKullanmaTarihi())) {
            throw new IllegalArgumentException("Bu davetiye kodunun süresi dolmuştur!");
        }

        // ✅ DÜZELTİLDİ: Tek atomik sorguda hem kontrol hem işaretleme
        // Aynı anda gelen iki kayıt isteği artık aynı kodu paylaşamaz
        int guncellenen = davetiyeKoduRepository.kullanildiOlarakIsaretle(istek.getDavetiyeKodu().trim());
        if (guncellenen == 0) {
            throw new IllegalArgumentException("Bu davetiye kodu daha önce başka bir market tarafından kullanılmış!");
        }

        // 2. Kullanıcı adı daha önce alınmış mı?
        if (kullaniciRepository.findByKullaniciAdi(istek.getKullaniciAdi().trim()) != null) {
            // Kullanıcı enumeration koruması: davetiye hatasıyla aynı mesaj
            throw new IllegalArgumentException("Girdiğiniz davetiye kodu sistemde bulunamadı!");
        }

        // 3. Market ve kullanıcıyı kaydet
        Market yeniMarket = new Market();
        yeniMarket.setMarketAdi(istek.getMarketAdi().trim());
        yeniMarket.setLisansBitisTarihi(LocalDate.now().plusYears(1)); // ✅ EKLENDİ
        marketRepository.save(yeniMarket);

        Kullanici yeniAdmin = new Kullanici();
        yeniAdmin.setKullaniciAdi(istek.getKullaniciAdi().trim());
        yeniAdmin.setSifre(passwordEncoder.encode(istek.getSifre().trim()));
        yeniAdmin.setRol("ADMIN");
        yeniAdmin.setMarket(yeniMarket);
        kullaniciRepository.save(yeniAdmin);

        return "Başarılı";
    }

    @PostMapping("/giris")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> girisYap(@Valid @RequestBody LoginIstegi istek,
                                        HttpServletRequest request) {

        String ip = request.getRemoteAddr();

        Kullanici bulunanKullanici = kullaniciRepository
                .findByKullaniciAdi(istek.getKullaniciAdi().trim());

        boolean success = bulunanKullanici != null &&
                passwordEncoder.matches(istek.getSifre().trim(), bulunanKullanici.getSifre());

        auditLogger.logLoginAttempt(istek.getKullaniciAdi(), success, ip);

        if (!success) {
            throw new IllegalArgumentException("Hatalı kullanıcı adı veya şifre!");
        }

        // Lisans sona ermiş mi?
        java.time.LocalDate bitisTarihi = bulunanKullanici.getMarket().getLisansBitisTarihi();
        if (bitisTarihi != null && java.time.LocalDate.now().isAfter(bitisTarihi)) {
            throw new IllegalArgumentException(
                    "Lisansınız " + bitisTarihi + " tarihinde sona erdi. Yenileme için iletişime geçin.");
        }

        String token = jwtUtil.tokenOlustur(
                bulunanKullanici.getKullaniciAdi(),
                bulunanKullanici.getRol(),
                bulunanKullanici.getMarket().getId()
        );

        Map<String, Object> yanit = new HashMap<>();
        yanit.put("mesaj", "Başarılı");
        yanit.put("token", token);
        yanit.put("rol", bulunanKullanici.getRol());
        yanit.put("marketId", bulunanKullanici.getMarket().getId());
        // Lisans bitiş tarihi — istemci 30 gün uyarısı için kullanır
        yanit.put("lisansBitisTarihi", bulunanKullanici.getMarket().getLisansBitisTarihi().toString());

        return yanit;
    }

    @PostMapping("/cikis")
    public String cikisYap(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            karaListesi.ekle(token);
        }
        return "Başarılı";
    }
}