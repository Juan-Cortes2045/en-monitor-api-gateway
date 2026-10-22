package com.energymonitor.gateway;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Access tokens for the tests, signed like the security service signs them, with a key pair made
 * for the test run. {@link #publicKeyFile()} is what the gateway is given.
 */
public final class TestTokens {

    public static final String ISSUER = "https://energy-monitor-backend";

    private static final KeyPair SERVICE_KEYS = generate();
    private static final KeyPair OTHER_KEYS = generate();
    private static final Path PUBLIC_KEY_FILE = writePublicKey(SERVICE_KEYS);

    private TestTokens() {
    }

    public static String publicKeyFile() {
        return PUBLIC_KEY_FILE.toString();
    }

    /** A valid token of {@code subject}. */
    public static String valid(String subject) {
        return sign(SERVICE_KEYS, ISSUER, subject, Instant.now().plus(Duration.ofMinutes(15)));
    }

    public static String expired(String subject) {
        return sign(SERVICE_KEYS, ISSUER, subject, Instant.now().minus(Duration.ofMinutes(5)));
    }

    public static String fromAnotherIssuer(String subject) {
        return sign(SERVICE_KEYS, "https://someone-else", subject,
                Instant.now().plus(Duration.ofMinutes(15)));
    }

    public static String signedWithAnotherKey(String subject) {
        return sign(OTHER_KEYS, ISSUER, subject, Instant.now().plus(Duration.ofMinutes(15)));
    }

    private static String sign(KeyPair keys, String issuer, String subject, Instant expiresAt) {
        RSAKey key = new RSAKey.Builder((RSAPublicKey) keys.getPublic())
                .privateKey((RSAPrivateKey) keys.getPrivate())
                .build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(subject)
                .issuedAt(expiresAt.minus(Duration.ofMinutes(15)))
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path writePublicKey(KeyPair keys) {
        try {
            Path file = Files.createTempFile("gateway-test-public", ".pem");
            file.toFile().deleteOnExit();
            String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keys.getPublic().getEncoded());
            Files.writeString(file, "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n");
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
