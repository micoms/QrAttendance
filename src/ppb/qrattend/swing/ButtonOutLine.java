package ppb.qrattend.swing;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.border.EmptyBorder;

public class ButtonOutLine extends JButton {

    public ButtonOutLine() {
        setContentAreaFilled(false);
        setBorderPainted(false);
        setBorder(new EmptyBorder(12, 24, 12, 24));
        setBackground(Color.WHITE);
        setForeground(Color.WHITE);
        setFont(new Font("Segoe UI", Font.PLAIN, 13));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setFocusPainted(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        int width  = getWidth();
        int height = getHeight();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Fill with semi-transparent white on hover/press
        if (getModel().isPressed()) {
            g2.setColor(new Color(255, 255, 255, 50));
            g2.fillRoundRect(0, 0, width, height, 10, 10);
        } else if (getModel().isRollover()) {
            g2.setColor(new Color(255, 255, 255, 30));
            g2.fillRoundRect(0, 0, width, height, 10, 10);
        }

        // Outline
        g2.setColor(new Color(255, 255, 255, 180));
        g2.drawRoundRect(1, 1, width - 2, height - 2, 10, 10);
        g2.dispose();
        super.paintComponent(g);
    }
}
