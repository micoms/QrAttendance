/*
 * Modern rounded text field with icon support and focus ring.
 */
package ppb.qrattend.swing;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTextField;

public class MyTextField extends JTextField {

    private static final Color BG_NORMAL  = new Color(242, 248, 244);
    private static final Color BG_FOCUS   = new Color(255, 255, 255);
    private static final Color BORDER_NORMAL = new Color(210, 225, 214);
    private static final Color BORDER_FOCUS  = new Color(52, 168, 100);

    private Icon   prefixIcon;
    private Icon   suffixIcon;
    private String hint = "";
    private boolean focused = false;

    public MyTextField() {
        setBackground(new Color(0, 0, 0, 0));
        setForeground(new Color(28, 42, 34));
        setCaretColor(new Color(52, 168, 100));
        setFont(new java.awt.Font("Segoe UI", 0, 13));
        setSelectionColor(new Color(52, 168, 100, 80));
        updateBorder();

        addFocusListener(new FocusStateListener(this));
    }

    public String getHint()                 { return hint; }
    public void   setHint(String hint)      { this.hint = hint; }
    public Icon   getPrefixIcon()           { return prefixIcon; }
    public Icon   getSuffixIcon()           { return suffixIcon; }

    public void setPrefixIcon(Icon icon) {
        this.prefixIcon = icon;
        updateBorder();
    }

    public void setSuffixIcon(Icon icon) {
        this.suffixIcon = icon;
        updateBorder();
    }

    private void updateBorder() {
        int left  = prefixIcon != null ? prefixIcon.getIconWidth() + 18 : 14;
        int right = suffixIcon != null ? suffixIcon.getIconWidth() + 18 : 14;
        setBorder(javax.swing.BorderFactory.createEmptyBorder(10, left, 10, right));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Background
        g2.setColor(focused ? BG_FOCUS : BG_NORMAL);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

        // Border ring
        g2.setColor(focused ? BORDER_FOCUS : BORDER_NORMAL);
        g2.setStroke(new java.awt.BasicStroke(focused ? 1.6f : 1f));
        g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 10, 10);

        paintIcons(g2);
        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        if (getText().isEmpty()) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Insets ins = getInsets();
            FontMetrics fm = g.getFontMetrics();
            int h = getHeight();
            g2.setColor(new Color(180, 195, 185));
            g2.drawString(hint, ins.left, h / 2 + fm.getAscent() / 2 - 2);
        }
    }

    private void paintIcons(Graphics2D g2) {
        if (prefixIcon != null) {
            Image img = ((ImageIcon) prefixIcon).getImage();
            int y = (getHeight() - prefixIcon.getIconHeight()) / 2;
            g2.drawImage(img, 10, y, this);
        }
        if (suffixIcon != null) {
            Image img = ((ImageIcon) suffixIcon).getImage();
            int y = (getHeight() - suffixIcon.getIconHeight()) / 2;
            g2.drawImage(img, getWidth() - suffixIcon.getIconWidth() - 10, y, this);
        }
    }

    private static final class FocusStateListener implements FocusListener {

        private final MyTextField field;

        private FocusStateListener(MyTextField field) {
            this.field = field;
        }

        @Override
        public void focusGained(FocusEvent event) {
            field.focused = true;
            field.repaint();
        }

        @Override
        public void focusLost(FocusEvent event) {
            field.focused = false;
            field.repaint();
        }
    }
}
