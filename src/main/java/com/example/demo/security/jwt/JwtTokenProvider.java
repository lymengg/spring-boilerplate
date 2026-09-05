package com.example.demo.security.jwt;

import com.example.demo.config.JwtConfig;
import com.example.demo.security.service.CustomUserDetailsService;
import com.example.demo.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtConfig jwtConfig;
    private final CustomUserDetailsService userDetailsService;

    private volatile SecretKey key;

    public SecretKey getKey() {
        SecretKey k = key;
        if (k == null) {
            synchronized (this) {
                k = key;
                if (k == null) {
                    k = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
                    key = k;
                }
            }
        }
        return k;
    }

    public String generateAccessToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        Long userId = null;
        Long tenantId = null;
        Long departmentId = null;
        if (userDetails instanceof User user) {
            userId = user.getId();
            if (user.getTenant() != null) {
                tenantId = user.getTenant().getId();
            }
            if (user.getDepartment() != null) {
                departmentId = user.getDepartment().getId();
            }
        }

        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getAccessTokenExpiration());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userDetails.getUsername())
                .claim("roles", roles)
                .claim("userId", userId)
                .claim("tenantId", tenantId)
                .claim("departmentId", departmentId)
                .issuedAt(now)
                .expiration(expiry)
                .issuer(jwtConfig.getIssuer())
                .audience().add(jwtConfig.getAudience()).and()
                .signWith(getKey(), Jwts.SIG.HS512)
                .compact();
    }

    public String generateRefreshToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getRefreshTokenExpiration());

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .issuer(jwtConfig.getIssuer())
                .audience().add(jwtConfig.getAudience()).and()
                .signWith(getKey(), Jwts.SIG.HS512)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(getKey()).build().parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    public boolean validateAccessToken(String token) {
        try {
            Claims claims = parseClaims(token);
            boolean isRefresh = "refresh".equals(claims.get("type", String.class));
            boolean audienceValid = jwtConfig.getAudience().equals(claims.getAudience().iterator().next());
            return !isRefresh && audienceValid;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token);
            boolean isRefresh = "refresh".equals(claims.get("type", String.class));
            boolean audienceValid = jwtConfig.getAudience().equals(claims.getAudience().iterator().next());
            return isRefresh && audienceValid;
        } catch (Exception e) {
            return false;
        }
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        String username = claims.getSubject();
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public Long getUserIdFromToken(String token) {
        return parseClaims(token).get("userId", Long.class);
    }

    public List<String> getRolesFromToken(String token) {
        Claims claims = parseClaims(token);
        Object roles = claims.get("roles");
        if (roles instanceof List) {
            return ((List<?>) roles).stream().map(Object::toString).toList();
        }
        return List.of();
    }

    public Long getTenantIdFromToken(String token) {
        return parseClaims(token).get("tenantId", Long.class);
    }

    public Long getDepartmentIdFromToken(String token) {
        return parseClaims(token).get("departmentId", Long.class);
    }

    public String getIdFromToken(String token) {
        return parseClaims(token).getId();
    }

    public long getRemainingExpiration(String token) {
        Claims claims = parseClaims(token);
        Date expiration = claims.getExpiration();
        return expiration.getTime() - System.currentTimeMillis();
    }

    public String generateMfaPendingToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getAccessTokenExpiration());

        return Jwts.builder()
                .subject(username)
                .claim("type", "mfa_pending")
                .issuedAt(now)
                .expiration(expiry)
                .issuer(jwtConfig.getIssuer())
                .audience().add(jwtConfig.getAudience()).and()
                .signWith(getKey(), Jwts.SIG.HS512)
                .compact();
    }

    public boolean validateMfaPendingToken(String token) {
        try {
            Claims claims = parseClaims(token);
            boolean isMfaPending = "mfa_pending".equals(claims.get("type", String.class));
            boolean audienceValid = jwtConfig.getAudience().equals(claims.getAudience().iterator().next());
            return isMfaPending && audienceValid;
        } catch (Exception e) {
            return false;
        }
    }

    public String getUsernameFromMfaPendingToken(String token) {
        return parseClaims(token).getSubject();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}