package com.smartlock.config;

import com.smartlock.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        log.info("JwtFilter - Request URI: {}, Auth header: {}", request.getRequestURI(), authHeader);
        
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            log.info("JwtFilter - Token: {}", token.substring(0, Math.min(20, token.length())) + "...");
            
            if (jwtUtil.validateToken(token)) {
                Long userId = jwtUtil.getUserIdFromToken(token);
                String username = jwtUtil.getUsernameFromToken(token);
                log.info("JwtFilter - userId: {}, username: {}", userId, username);
                
                request.setAttribute("userId", userId);
                request.setAttribute("username", username);
            } else {
                log.warn("JwtFilter - Token validation failed");
            }
        } else {
            log.warn("JwtFilter - No valid Authorization header");
        }
        
        filterChain.doFilter(request, response);
    }
}
