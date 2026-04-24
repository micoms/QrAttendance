package ppb.qrattend.component.login;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import ppb.qrattend.model.AppDomain;
import ppb.qrattend.swing.Button;
import ppb.qrattend.swing.MyPasswordField;
import ppb.qrattend.swing.MyTextField;

public class PanelLogin extends JPanel {

    public interface LoginHandler {
        void loginRequested(String email, String password, AppDomain.UserRole role);
    }

    private final JLabel           titleLabel    = new JLabel();
    private final JLabel           subtitleLabel = new JLabel();
    private final JLabel           statusLabel   = new JLabel();
    private final MyTextField      emailField    = new MyTextField();
    private final MyPasswordField  passwordField = new MyPasswordField();
    private final Button           signInButton  = new Button();

    private AppDomain.UserRole role = AppDomain.UserRole.TEACHER;
    private LoginHandler loginHandler;
    public PanelLogin() {
        setOpaque(true);
        setBackground(Color.WHITE);
        // GridBagLayout centres its child both horizontally and vertically
        setLayout(new GridBagLayout());
        buildForm();
        setRole(AppDomain.UserRole.TEACHER);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Very subtle warm-white gradient
        Graphics2D g2 = (Graphics2D) g.create();
        GradientPaint gp = new GradientPaint(0, 0, Color.WHITE, 0, getHeight(), new Color(238, 248, 242));
        g2.setPaint(gp);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }

    private void buildForm() {
        // The form card — fixed max width so it looks good on wide windows
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(new EmptyBorder(0, 0, 0, 0));

        // Green accent bar at top
        JPanel bar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(45, 152, 95));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setMaximumSize(new java.awt.Dimension(52, 4));
        bar.setPreferredSize(new java.awt.Dimension(52, 4));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(bar);
        form.add(Box.createVerticalStrut(22));

        // Title
        titleLabel.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 30));
        titleLabel.setForeground(new Color(20, 36, 26));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(titleLabel);
        form.add(Box.createVerticalStrut(6));

        // Subtitle
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitleLabel.setForeground(new Color(100, 125, 110));
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(subtitleLabel);
        form.add(Box.createVerticalStrut(32));

        // Email
        form.add(fieldLabel("Email address"));
        form.add(Box.createVerticalStrut(6));
        emailField.setPrefixIcon(new ImageIcon(getClass().getResource("/ppb/qrattend/icon/mail.png")));
        emailField.setHint("Enter your email");
        emailField.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 46));
        emailField.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(emailField);
        form.add(Box.createVerticalStrut(16));

        // Password
        form.add(fieldLabel("Password"));
        form.add(Box.createVerticalStrut(6));
        passwordField.setPrefixIcon(new ImageIcon(getClass().getResource("/ppb/qrattend/icon/pass.png")));
        passwordField.setHint("Enter your password");
        passwordField.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 46));
        passwordField.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(passwordField);
        form.add(Box.createVerticalStrut(24));

        // Sign-In button
        signInButton.setText("Sign In");
        signInButton.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 14));
        signInButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        signInButton.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 46));
        signInButton.addActionListener(e -> {
            if (loginHandler != null) {
                loginHandler.loginRequested(
                    emailField.getText(),
                    String.valueOf(passwordField.getPassword()),
                    role);
            }
        });
        form.add(signInButton);
        form.add(Box.createVerticalStrut(20));

        // Thin separator
        JPanel sep = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(210, 228, 216));
                g2.setStroke(new BasicStroke(1));
                g2.drawLine(0, getHeight() / 2, getWidth(), getHeight() / 2);
                g2.dispose();
            }
        };
        sep.setOpaque(false);
        sep.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 10));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(sep);
        form.add(Box.createVerticalStrut(14));

        // Status
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(172, 60, 54));
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(statusLabel);

        // Place form in the centre of this panel via GridBagLayout
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx      = 0;
        gbc.gridy      = 0;
        gbc.weightx    = 1.0;
        gbc.weighty    = 1.0;
        gbc.fill       = GridBagConstraints.HORIZONTAL;
        gbc.anchor     = GridBagConstraints.CENTER;
        gbc.insets     = new Insets(0, 52, 0, 52);
        add(form, gbc);

        // Footer
        JLabel footer = new JLabel("Use your school email and password to sign in.");
        footer.setHorizontalAlignment(SwingConstants.CENTER);
        footer.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        footer.setForeground(new Color(170, 190, 178));

        GridBagConstraints footerGbc = new GridBagConstraints();
        footerGbc.gridx   = 0;
        footerGbc.gridy   = 1;
        footerGbc.weightx = 1.0;
        footerGbc.weighty = 0;
        footerGbc.fill    = GridBagConstraints.HORIZONTAL;
        footerGbc.insets  = new Insets(0, 0, 18, 0);
        add(footer, footerGbc);
    }

    private JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        label.setForeground(new Color(75, 100, 85));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    public void setLoginHandler(LoginHandler loginHandler) {
        this.loginHandler = loginHandler;
    }

    public void setRole(AppDomain.UserRole role) {
        this.role = role;
        if (role == AppDomain.UserRole.ADMIN) {
            titleLabel.setText("Admin Sign In");
            subtitleLabel.setText("Manage teachers, students, schedules, and reports.");
        } else {
            titleLabel.setText("Teacher Sign In");
            subtitleLabel.setText("Take attendance, check your class list, and review reports.");
        }
        clearStatus();
    }

    public void showStatus(String message, boolean error) {
        statusLabel.setForeground(error ? new Color(172, 60, 54) : new Color(40, 148, 82));
        statusLabel.setText(message);
    }

    public void clearStatus() {
        statusLabel.setText(" ");
    }

    public void resetFields() {
        emailField.setText("");
        passwordField.setText("");
        clearStatus();
    }
}
