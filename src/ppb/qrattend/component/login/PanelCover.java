package ppb.qrattend.component.login;

import java.awt.BorderLayout;
import java.awt.Color;
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
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import ppb.qrattend.model.AppDomain;
import ppb.qrattend.swing.ButtonOutLine;

public class PanelCover extends JPanel {

    private final JLabel roleLabel  = new JLabel();
    private final JLabel titleLabel = new JLabel();
    private final JLabel bodyLabel  = new JLabel();
    private final JButton toggleButton = new ButtonOutLine();

    public PanelCover() {
        setOpaque(false);
        setLayout(new BorderLayout(0, 0));
        setBorder(new EmptyBorder(44, 40, 36, 40));
        buildContent();
        setRole(AppDomain.UserRole.TEACHER);
    }

    private void buildContent() {
        // ---- TOP: Brand ----
        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JLabel brand = new JLabel("QR Attend");
        brand.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 26));
        brand.setForeground(Color.WHITE);
        top.add(brand);
        top.add(Box.createVerticalStrut(4));

        JLabel tagline = new JLabel("Easy attendance for schools.");
        tagline.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tagline.setForeground(new Color(190, 228, 208));
        top.add(tagline);
        add(top, BorderLayout.NORTH);

        // ---- CENTER: Role info + features ----
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        center.add(Box.createVerticalStrut(8));
        roleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        roleLabel.setForeground(new Color(160, 222, 188));
        center.add(roleLabel);
        center.add(Box.createVerticalStrut(10));

        titleLabel.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 34));
        titleLabel.setForeground(Color.WHITE);
        center.add(titleLabel);
        center.add(Box.createVerticalStrut(14));

        bodyLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        bodyLabel.setForeground(new Color(210, 238, 222));
        center.add(bodyLabel);
        center.add(Box.createVerticalStrut(32));

        // Feature list - ASCII only
        String[] features = {
            "Scan student QR codes in class",
            "Mark attendance without QR when needed",
            "Keep class schedules clear and organized"
        };
        for (String feature : features) {
            JPanel row = new JPanel();
            row.setOpaque(false);
            row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
            JLabel bullet = new JLabel("--  ");
            bullet.setFont(new Font("Segoe UI", Font.BOLD, 12));
            bullet.setForeground(new Color(130, 220, 168));
            row.add(bullet);
            JLabel text = new JLabel(feature);
            text.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            text.setForeground(new Color(220, 242, 230));
            row.add(text);
            center.add(row);
            center.add(Box.createVerticalStrut(10));
        }

        add(center, BorderLayout.CENTER);

        // ---- BOTTOM: Toggle button ----
        toggleButton.setForeground(Color.WHITE);
        toggleButton.setBackground(Color.WHITE);
        toggleButton.setText("Go to Admin Sign In");
        add(toggleButton, BorderLayout.SOUTH);
    }

    public void setToggleAction(java.awt.event.ActionListener listener) {
        for (java.awt.event.ActionListener al : toggleButton.getActionListeners()) {
            toggleButton.removeActionListener(al);
        }
        toggleButton.addActionListener(listener);
    }

    public void setRole(AppDomain.UserRole role) {
        if (role == AppDomain.UserRole.ADMIN) {
            roleLabel.setText("ADMIN");
            titleLabel.setText("<html>Manage the<br>school.</html>");
            bodyLabel.setText("<html>Add teachers and students,<br>set class schedules, and review requests.</html>");
            toggleButton.setText("Go to Teacher Sign In");
        } else {
            roleLabel.setText("TEACHER");
            titleLabel.setText("<html>Take attendance<br>with ease.</html>");
            bodyLabel.setText("<html>Scan QR codes, check your class list,<br>and ask for help when something needs approval.</html>");
            toggleButton.setText("Go to Admin Sign In");
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Rich diagonal gradient — same green palette
        GradientPaint gp = new GradientPaint(
                0, 0,              new Color(45, 152, 95),
                getWidth(), getHeight(), new Color(18, 60, 38)
        );
        g2.setPaint(gp);
        g2.fillRect(0, 0, getWidth(), getHeight());

        // Decorative large circle top-right
        g2.setColor(new Color(255, 255, 255, 14));
        int r1 = (int) (getWidth() * 0.85);
        g2.fillOval(getWidth() - r1 + 40, -r1 / 3, r1, r1);

        // Decorative small circle bottom-left
        g2.setColor(new Color(255, 255, 255, 10));
        int r2 = (int) (getWidth() * 0.6);
        g2.fillOval(-r2 / 4, getHeight() - r2 + 60, r2, r2);

        g2.dispose();
        super.paintComponent(g);
    }
}
