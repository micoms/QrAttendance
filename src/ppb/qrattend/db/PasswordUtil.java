package ppb.qrattend.db;

public final class PasswordUtil {

    private PasswordUtil() {
    }

    public static String hashPassword(String plainTextPassword) {
        return SecurityUtil.sha256Hex(plainTextPassword);
    }
}
