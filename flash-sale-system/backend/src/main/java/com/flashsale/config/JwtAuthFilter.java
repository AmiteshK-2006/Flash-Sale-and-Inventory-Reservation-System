package com.flashsale.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.Claims;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
                String email = claims.getSubject();
                String role = claims.get("role", String.class);
                Object rawUserId = claims.get("userId");
                Long userId = 1L;
                if (rawUserId instanceof Number) {
                    userId = ((Number) rawUserId).longValue();
                } else if (rawUserId != null) {
                    try {
                        userId = Long.parseLong(rawUserId.toString());
                    } catch (NumberFormatException ignored) {}
                }
                
                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    CustomUserDetails userDetails = new CustomUserDetails(userId, email, role != null ? role : "USER");
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + (role != null ? role : "USER")))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (Exception e) {
                // invalid token or parse error
            }
        }
        filterChain.doFilter(request, response);
    }
}
