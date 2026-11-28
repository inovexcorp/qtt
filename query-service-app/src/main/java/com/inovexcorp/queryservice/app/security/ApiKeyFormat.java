package com.inovexcorp.queryservice.app.security;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text form of a QTT API key: {@code qtt_<id>_<secret>}. The id (12 base62 characters) names the key in lists and
 * logs; the secret (32 random bytes in base62) is what proves possession, and only its SHA-256 is stored. A plain
 * hash is enough here: the secret is random with 256 bits of entropy, so there is nothing to brute-force.
 */
final class ApiKeyFormat {

    static final String PREFIX = "qtt_";
    static final int ID_LENGTH = 12;
    private static final int SECRET_BYTES = 32;
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final Pattern KEY = Pattern.compile("qtt_([0-9A-Za-z]{" + ID_LENGTH + "})_([0-9A-Za-z]{20,64})");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** A parsed key. */
    record Parsed(String id, String secret) {
    }

    /** A newly generated key: the full text (shown once), its id and the hash to store. */
    record Generated(String key, String id, String secretHash) {
    }

    private ApiKeyFormat() {
    }

    /** Whether an Authorization bearer value looks like a QTT API key (as opposed to an OIDC token). */
    static boolean looksLikeKey(String token) {
        return token != null && token.startsWith(PREFIX);
    }

    /** The key's parts, or null when it is not well formed. */
    static Parsed parse(String token) {
        if (token == null) {
            return null;
        }
        Matcher m = KEY.matcher(token);
        return m.matches() ? new Parsed(m.group(1), m.group(2)) : null;
    }

    static Generated generate() {
        String id = randomBase62(ID_LENGTH);
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        String secret = base62(bytes);
        return new Generated(PREFIX + id + "_" + secret, id, hash(secret));
    }

    static String hash(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Constant-time comparison of a presented secret with a stored hash. */
    static boolean matches(String secret, String storedHash) {
        if (secret == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(hash(secret).getBytes(StandardCharsets.US_ASCII),
                storedHash.getBytes(StandardCharsets.US_ASCII));
    }

    private static String randomBase62(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(BASE62.charAt(RANDOM.nextInt(BASE62.length())));
        }
        return sb.toString();
    }

    private static String base62(byte[] bytes) {
        BigInteger n = new BigInteger(1, bytes);
        BigInteger base = BigInteger.valueOf(62);
        StringBuilder sb = new StringBuilder();
        while (n.signum() > 0) {
            BigInteger[] qr = n.divideAndRemainder(base);
            sb.append(BASE62.charAt(qr[1].intValue()));
            n = qr[0];
        }
        while (sb.length() < 43) {
            sb.append('0'); // keep a fixed length (32 bytes need at most 43 base62 digits)
        }
        return sb.reverse().toString();
    }
}
