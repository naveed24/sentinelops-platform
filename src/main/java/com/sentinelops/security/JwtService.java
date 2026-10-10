package com.sentinelops.security;

import com.sentinelops.identity.UserAccount;
import com.sentinelops.identity.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HEADER =
            Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
                            .getBytes(StandardCharsets.UTF_8));

    private final ObjectMapper objectMapper;
    private final SecretKeySpec signingKey;
    private final String issuer;
    private final Duration ttl;

    public JwtService(
            ObjectMapper objectMapper,
            @Value("${sentinelops.security.jwt.secret}") String secret,
            @Value("${sentinelops.security.jwt.issuer:sentinelops}") String issuer,
            @Value("${sentinelops.security.jwt.ttl:PT1H}") String ttl) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "sentinelops.security.jwt.secret must contain at least 32 UTF-8 bytes");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("sentinelops.security.jwt.issuer must not be blank");
        }

        Duration parsedTtl = Duration.parse(ttl);
        if (parsedTtl.isZero() || parsedTtl.isNegative()) {
            throw new IllegalStateException("sentinelops.security.jwt.ttl must be positive");
        }

        this.objectMapper = objectMapper;
        this.signingKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        this.issuer = issuer;
        this.ttl = parsedTtl;
    }

    public IssuedToken issue(UserAccount user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("sub", user.getEmail());
        payload.put("uid", user.getId());
        payload.put("role", user.getRole().name());
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        try {
            String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(payload));
            String signingInput = HEADER + "." + encodedPayload;
            String signature = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(sign(signingInput.getBytes(StandardCharsets.UTF_8)));

            return new IssuedToken(signingInput + "." + signature, expiresAt);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to issue access token", ex);
        }
    }

    public TokenClaims parseAndValidate(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || !HEADER.equals(parts[0])) {
                throw new IllegalArgumentException("Invalid token structure");
            }

            byte[] providedSignature = Base64.getUrlDecoder().decode(parts[2]);
            byte[] expectedSignature = sign(
                    (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));

            if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
                throw new IllegalArgumentException("Invalid token signature");
            }

            JsonNode payload = objectMapper.readTree(
                    Base64.getUrlDecoder().decode(parts[1]));

            String tokenIssuer = requiredText(payload, "iss");
            String subject = requiredText(payload, "sub");
            String roleName = requiredText(payload, "role");
            long userId = requiredLong(payload, "uid");
            long expiresEpochSecond = requiredLong(payload, "exp");

            if (!issuer.equals(tokenIssuer)) {
                throw new IllegalArgumentException("Invalid token issuer");
            }

            Instant expiresAt = Instant.ofEpochSecond(expiresEpochSecond);
            if (!expiresAt.isAfter(Instant.now())) {
                throw new IllegalArgumentException("Access token has expired");
            }

            return new TokenClaims(
                    subject,
                    userId,
                    UserRole.valueOf(roleName),
                    expiresAt
            );
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid access token", ex);
        }
    }

    private byte[] sign(byte[] input) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(signingKey);
            return mac.doFinal(input);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate token signature", ex);
        }
    }

    private String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing token claim: " + field);
        }
        return value.asText();
    }

    private long requiredLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isIntegralNumber()) {
            throw new IllegalArgumentException("Missing token claim: " + field);
        }
        return value.asLong();
    }

    public record IssuedToken(String value, Instant expiresAt) {}

    public record TokenClaims(
            String subject,
            long userId,
            UserRole role,
            Instant expiresAt
    ) {}
}
