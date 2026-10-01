package com.paytm.project.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;

    @Value("${app.jwt.expiration-seconds:86400}")
    private long expirationSeconds;

    @Value("${app.jwt.issuer:paytmProject}")
    private String issuer;

    public String generateToken(String username) {
        return generateToken(username, Collections.emptyMap());
    }

    public String generateToken(String username, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirationSeconds);

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(username)
                .claim("scope", "ROLE_USER");

        if (extraClaims != null) {
            extraClaims.forEach(claimsBuilder::claim);
        }

        JwtClaimsSet claims = claimsBuilder.build();
        JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claims)).getTokenValue();
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }
}
