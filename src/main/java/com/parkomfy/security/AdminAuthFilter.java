package com.parkomfy.security;

import com.parkomfy.service.AuthService;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Protects /api/v1/admin/* endpoints. Requires X-Auth-Token from a successful ADMIN login.
 */
public class AdminAuthFilter implements Filter {

    public static final String AUTH_HEADER = "X-Auth-Token";

    private final AuthService authService;

    public AdminAuthFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;

        String token = req.getHeader(AUTH_HEADER);
        if (!authService.validateAdminToken(token)) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json;charset=UTF-8");
            res.getOutputStream().write(
                "{\"success\":false,\"message\":\"Yetkisiz erişim. Admin girişi gerekli.\"}"
                    .getBytes(StandardCharsets.UTF_8)
            );
            return;
        }
        chain.doFilter(request, response);
    }
}
