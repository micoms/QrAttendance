package ppb.qrattend.swing;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;

public class Button extends JButton {

    private static final Color BG        = new Color(52, 168, 100);
    private static final Color BG_HOVER  = new Color(40, 142, 84);
    private static final Color BG_PRESS  = new Color(28, 112, 64);

    public Button() {
        setContentAreaFilled(false);
        setBorder(new EmptyBorder(10, 20, 10, 20));
        setBackground(BG);
        setForeground(Color.WHITE);
        setFont(new Font("Segoe UI", Font.PLAIN, 14));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setFocusPainted(false);
        setBorderPainted(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color bg = BG;
        if (getModel().isPressed()) {
            bg = BG_PRESS;
        } else if (getModel().isRollover()) {
            bg = BG_HOVER;
        }
        g2.setColor(bg);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        g2.dispose();
        super.paintComponent(g);
    }
}
