package ppb.qrattend.db;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class SecurityUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecurityUtil() {
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(safe(value).getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime.", ex);
        }
    }

    public static String generateOpaqueToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return "QRATTEND-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String safePreview(String subject) {
        String text = safe(subject);
        return text.isBlank() ? "Activity saved." : text;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String toHex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte item : value) {
            builder.append(String.format("%02x", item));
        }
        return builder.toString();
    }
}
