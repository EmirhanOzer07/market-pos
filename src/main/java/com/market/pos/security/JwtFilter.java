package com.market.pos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import com.market.pos.security.MarketFilterAspect;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import io.jsonwebtoken.Claims;
import java.util.Collections;
import java.util.List;


@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired private JwtUtil jwtUtil;
    @Autowired private TokenKaraListesi karaListesi; // ✅ YENİ

    // JwtFilter.java içinde — finally bloğuna taşı ki hata olsa da temizlensin
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String header = request.getHeader("Authorization");

            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7);

                if (karaListesi.gecersizMi(token)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                if (jwtUtil.tokenGecerliMi(token)) {
                    Claims claims = jwtUtil.tokenCoz(token);
                    String kullaniciAdi = claims.getSubject();
                    String rol = claims.get("rol", String.class);

                    List<GrantedAuthority> authorities = Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_" + rol)
                    );

                    UsernamePasswordAuthenticationToken yetki =
                            new UsernamePasswordAuthenticationToken(
                                    kullaniciAdi, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(yetki);
                }
            }

            filterChain.doFilter(request, response);

        } finally {
            MarketFilterAspect.filtreTemizle(); // ✅ Her request bitişinde ThreadLocal temizlenir
        }
    }
}