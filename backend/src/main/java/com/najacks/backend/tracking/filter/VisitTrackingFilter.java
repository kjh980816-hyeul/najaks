package com.najacks.backend.tracking.filter;

import com.najacks.backend.auth.CustomUserPrincipal;
import com.najacks.backend.tracking.service.VisitTrackingService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class VisitTrackingFilter extends OncePerRequestFilter {

    private final VisitTrackingService visitTrackingService;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
            throws IOException, ServletException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null
                    && auth.isAuthenticated()
                    && auth.getPrincipal() instanceof CustomUserPrincipal user) {
                visitTrackingService.recordVisit(user.getId());
            }
        } catch (Exception e) {
            log.warn("VisitTrackingFilter 예외", e);
        }
        chain.doFilter(req, res);
    }
}
