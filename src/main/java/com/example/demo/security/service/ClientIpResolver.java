package com.example.demo.security.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    public String resolveClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
