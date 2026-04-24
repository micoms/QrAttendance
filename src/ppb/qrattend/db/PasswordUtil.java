package ppb.qrattend.db;

public final class PasswordUtil {

    private PasswordUtil() {
        // Mini-code guide:
        // 1. Prevent instantiation because this is a static utility class.
    }

    public static String hashPassword(String plainTextPassword) {
        return SecurityUtil.sha256Hex(plainTextPassword);
    }
}
