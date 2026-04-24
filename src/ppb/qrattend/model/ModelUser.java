package ppb.qrattend.model;

public class ModelUser {

    private int userId;
    private String fullName;
    private String email;
    private AppDomain.UserRole role;

    public ModelUser() {
    }

    public ModelUser(int userId, String email, AppDomain.UserRole role) {
        this(userId, "", email, role);
    }

    public ModelUser(int userId, String email, String role) {
        this(userId, "", email, AppDomain.UserRole.valueOf(role.toUpperCase()));
    }

    public ModelUser(int userId, String fullName, String email, AppDomain.UserRole role) {
        this.userId = userId;
        this.fullName = fullName;
        this.email = email;
        this.role = role;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return "";
    }

    public void setPassword(String password) {
        // Session users no longer keep readable passwords in memory.
    }

    public AppDomain.UserRole getRole() {
        return role;
    }

    public void setRole(AppDomain.UserRole role) {
        this.role = role;
    }

    public boolean isAdmin() {
        return AppDomain.UserRole.ADMIN == role;
    }

    public boolean isTeacher() {
        return AppDomain.UserRole.TEACHER == role;
    }
}
