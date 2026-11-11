package com.personnel.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private final SecretKey signingKey;

    public JwtUtil(@Value("${personnel.jwt-secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            signingKey = Jwts.SIG.HS384.key().build();
            log.warn("未设置PERSONNEL_JWT_SECRET，使用本次进程临时签名密钥，重启后需重新登录");
        } else {
            signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
    }

    public String createToken(Long userId, String roleCode) {
        return createToken(userId, roleCode, 0L);
    }

    public String createToken(Long userId, String roleCode, long authVersion) {
        Date now = new Date();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("role", roleCode)
            .claim("authVersion", authVersion)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + 86_400_000L))
            .signWith(signingKey)
            .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }
}
