package ppb.qrattend.app;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;

public final class AppTheme {

    // --- Colour palette (same hues, slightly refined) ---
    public static final Color BRAND       = new Color(52, 168, 100);
    public static final Color BRAND_HOVER = new Color(40, 142, 84);
    public static final Color BRAND_DARK  = new Color(25, 72, 48);
    public static final Color BRAND_DARKER= new Color(18, 52, 34);
    public static final Color BACKGROUND  = new Color(243, 246, 244);
    public static final Color SURFACE     = new Color(255, 255, 255);
    public static final Color SURFACE_ALT = new Color(236, 244, 239);
    public static final Color BORDER      = new Color(210, 225, 214);
    public static final Color TEXT_PRIMARY= new Color(28, 40, 32);
    public static final Color TEXT_MUTED  = new Color(96, 115, 101);
    public static final Color SUCCESS     = new Color(40, 148, 82);
    public static final Color WARNING     = new Color(194, 128, 22);
    public static final Color DANGER      = new Color(172, 60, 54);
    public static final Color INFO        = new Color(44, 105, 190);

    // Rounded corner radius constants
    public static final int RADIUS_SM = 8;
    public static final int RADIUS_MD = 12;
    public static final int RADIUS_LG = 16;

    private AppTheme() {}

    public static void install() {
        UIManager.put("Panel.background", BACKGROUND);
        UIManager.put("OptionPane.background", SURFACE);
        UIManager.put("OptionPane.messageForeground", TEXT_PRIMARY);
        UIManager.put("ScrollBar.width", 6);
        UIManager.put("ScrollBar.thumb", BORDER);
        UIManager.put("ScrollBar.track", BACKGROUND);
    }

    public static Font headlineFont(float size) {
        return new Font("Segoe UI Semibold", Font.PLAIN, Math.round(size));
    }

    public static Font bodyFont(float size) {
        return new Font("Segoe UI", Font.PLAIN, Math.round(size));
    }

    // -------------------------------------------------------
    //  Page / Section containers
    // -------------------------------------------------------

    public static JPanel createPage() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(0, 0, 0, 0));
        return panel;
    }

    public static JPanel createSection(String title, String subtitle) {
        JPanel container = new RoundedPanel(RADIUS_MD, SURFACE);
        container.setLayout(new BorderLayout(0, 14));
        container.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(RADIUS_MD, BORDER, 1),
                new EmptyBorder(18, 20, 18, 20)
        ));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(headlineFont(15));
        titleLabel.setForeground(TEXT_PRIMARY);
        header.add(titleLabel);

        if (subtitle != null && !subtitle.isBlank()) {
            header.add(Box.createVerticalStrut(3));
            JLabel subtitleLabel = new JLabel(subtitle);
            subtitleLabel.setFont(bodyFont(12));
            subtitleLabel.setForeground(TEXT_MUTED);
            header.add(subtitleLabel);
        }
        container.add(header, BorderLayout.NORTH);
        return container;
    }

    // -------------------------------------------------------
    //  Stat cards
    // -------------------------------------------------------

    public static JPanel createStatCard(String label, String value, Color accent) {
        JPanel card = new RoundedPanel(RADIUS_MD, SURFACE);
        card.setLayout(new BorderLayout());
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(RADIUS_MD, BORDER, 1),
                new EmptyBorder(16, 18, 16, 18)
        ));

        // Left accent stripe
        JPanel stripe = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(accent);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), RADIUS_SM, RADIUS_SM);
                g2.dispose();
            }
        };
        stripe.setOpaque(false);
        stripe.setPreferredSize(new Dimension(4, 0));
        card.add(stripe, BorderLayout.WEST);

        JPanel inner = new JPanel();
        inner.setOpaque(false);
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBorder(new EmptyBorder(0, 14, 0, 0));

        JLabel labelText = new JLabel(label.toUpperCase());
        labelText.setFont(bodyFont(10));
        labelText.setForeground(TEXT_MUTED);
        inner.add(labelText);
        inner.add(Box.createVerticalStrut(8));

        JLabel valueText = new JLabel(value);
        valueText.setFont(headlineFont(28));
        valueText.setForeground(TEXT_PRIMARY);
        inner.add(valueText);

        card.add(inner, BorderLayout.CENTER);
        return card;
    }

    // -------------------------------------------------------
    //  Pill badge
    // -------------------------------------------------------

    public static JLabel createPill(String text, Color background, Color foreground) {
        JLabel label = new JLabel(text, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(background);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setOpaque(false);
        label.setForeground(foreground);
        label.setBorder(new EmptyBorder(5, 14, 5, 14));
        label.setFont(bodyFont(11));
        return label;
    }

    // -------------------------------------------------------
    //  Button styles  (painted via custom JButton subclass)
    // -------------------------------------------------------

    public static void stylePrimaryButton(AbstractButton button) {
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setBackground(BRAND);
        button.setForeground(Color.WHITE);
        button.setFont(bodyFont(13));
        button.setBorder(new EmptyBorder(10, 18, 10, 18));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        applyRoundedButtonPainter(button, BRAND, BRAND_HOVER, Color.WHITE, RADIUS_SM);
    }

    public static void styleSecondaryButton(AbstractButton button) {
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setBackground(SURFACE_ALT);
        button.setForeground(TEXT_PRIMARY);
        button.setFont(bodyFont(13));
        button.setBorder(new EmptyBorder(10, 18, 10, 18));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        applyRoundedButtonPainter(button, SURFACE_ALT, BORDER, TEXT_PRIMARY, RADIUS_SM);
    }

    public static void styleDangerButton(AbstractButton button) {
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        Color bg = new Color(252, 232, 230);
        Color bgHover = new Color(247, 215, 212);
        button.setBackground(bg);
        button.setForeground(DANGER);
        button.setFont(bodyFont(13));
        button.setBorder(new EmptyBorder(10, 18, 10, 18));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        applyRoundedButtonPainter(button, bg, bgHover, DANGER, RADIUS_SM);
    }

    public static void styleNavButton(AbstractButton button, boolean active) {
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFont(bodyFont(13));
        button.setBorder(new EmptyBorder(10, 14, 10, 14));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        if (active) {
            Color activeBg   = new Color(52, 168, 100, 255);
            Color activeFg   = Color.WHITE;
            button.setBackground(activeBg);
            button.setForeground(activeFg);
            applyRoundedButtonPainter(button, activeBg, activeBg.darker(), activeFg, RADIUS_SM);
        } else {
            Color inactiveBg = new Color(255, 255, 255, 18);
            Color hoverBg    = new Color(255, 255, 255, 40);
            Color fg         = new Color(210, 230, 216);
            button.setBackground(inactiveBg);
            button.setForeground(fg);
            applyRoundedButtonPainter(button, inactiveBg, hoverBg, fg, RADIUS_SM);
        }
    }

    // Internal helper: replaces the UI painter with a rounded-rect version
    private static void applyRoundedButtonPainter(AbstractButton button,
            Color normal, Color hover, Color fg, int arc) {
        // Remove old painters first
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                AbstractButton b = (AbstractButton) c;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = b.getModel().isRollover() || b.getModel().isPressed() ? hover : normal;
                if (b.getModel().isPressed()) {
                    bg = bg.darker();
                }
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, b.getWidth(), b.getHeight(), arc * 2, arc * 2);
                g2.dispose();
                super.paint(g, c);
            }
        });
    }

    // -------------------------------------------------------
    //  Form fields
    // -------------------------------------------------------

    public static void styleField(JTextField field) {
        field.setFont(bodyFont(13));
        field.setForeground(TEXT_PRIMARY);
        field.setBackground(SURFACE);
        field.setCaretColor(BRAND);
        field.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(RADIUS_SM, BORDER, 1),
                new EmptyBorder(9, 12, 9, 12)
        ));
    }

    public static void styleTextArea(JTextArea area) {
        area.setFont(bodyFont(13));
        area.setForeground(TEXT_PRIMARY);
        area.setBackground(SURFACE);
        area.setCaretColor(BRAND);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(10, 12, 10, 12));
    }

    public static void styleCombo(JComboBox<?> combo) {
        combo.setFont(bodyFont(13));
        combo.setBackground(SURFACE);
        combo.setForeground(TEXT_PRIMARY);
        combo.setBorder(new RoundedBorder(RADIUS_SM, BORDER, 1));
    }

    // -------------------------------------------------------
    //  Table
    // -------------------------------------------------------

    public static void styleTable(JTable table) {
        table.setRowHeight(36);
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(230, 238, 232));
        table.setSelectionBackground(new Color(212, 240, 224));
        table.setSelectionForeground(TEXT_PRIMARY);
        table.setForeground(TEXT_PRIMARY);
        table.setBackground(SURFACE);
        table.setFont(bodyFont(12));
        table.setIntercellSpacing(new Dimension(0, 0));

        // Alternating row striping via custom renderer
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setBorder(new EmptyBorder(0, 10, 0, 10));
                if (!isSelected) {
                    setBackground(row % 2 == 0 ? SURFACE : new Color(247, 251, 248));
                    setForeground(TEXT_PRIMARY);
                }
                return this;
            }
        });

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setBackground(SURFACE_ALT);
        header.setForeground(TEXT_MUTED);
        header.setFont(bodyFont(11));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));

        DefaultTableCellRenderer headerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setBackground(SURFACE_ALT);
                setForeground(TEXT_MUTED);
                setFont(bodyFont(11));
                setBorder(new EmptyBorder(0, 10, 0, 10));
                setText(value == null ? "" : value.toString().toUpperCase());
                return this;
            }
        };
        for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setHeaderRenderer(headerRenderer);
        }
    }

    public static JScrollPane wrapScrollable(Component component) {
        JScrollPane scrollPane = new JScrollPane(component);
        scrollPane.setBorder(new RoundedBorder(RADIUS_SM, BORDER, 1));
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(SURFACE);
        scrollPane.getVerticalScrollBar().setUI(new SlimScrollBarUI());
        return scrollPane;
    }

    // -------------------------------------------------------
    //  Layout helpers
    // -------------------------------------------------------

    public static JPanel stack(Component... components) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (int i = 0; i < components.length; i++) {
            panel.add(components[i]);
            if (i < components.length - 1) {
                panel.add(Box.createVerticalStrut(16));
            }
        }
        return panel;
    }

    public static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(bodyFont(12));
        label.setForeground(TEXT_MUTED);
        return label;
    }

    public static JLabel bodyLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(bodyFont(13));
        label.setForeground(TEXT_PRIMARY);
        return label;
    }

    public static JPanel row(Component... components) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        for (int i = 0; i < components.length; i++) {
            panel.add(components[i]);
            if (i < components.length - 1) {
                panel.add(Box.createHorizontalStrut(10));
            }
        }
        return panel;
    }

    public static void setMaxHeight(JComponent component, int height) {
        Dimension preferred = component.getPreferredSize();
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(height, preferred.height)));
    }

    // -------------------------------------------------------
    //  Inner helpers: rounded panel, border, slim scrollbar
    // -------------------------------------------------------

    /** A JPanel that paints itself with rounded corners. */
    public static class RoundedPanel extends JPanel {
        private final int arc;
        private final Color bg;

        public RoundedPanel(int arc, Color bg) {
            this.arc = arc;
            this.bg  = bg;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc * 2, arc * 2);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A rounded-rect border with configurable radius, colour and thickness. */
    public static class RoundedBorder extends AbstractBorder {
        private final int arc;
        private final Color color;
        private final int thickness;

        public RoundedBorder(int arc, Color color, int thickness) {
            this.arc       = arc;
            this.color     = color;
            this.thickness = thickness;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));
            g2.drawRoundRect(x + thickness / 2, y + thickness / 2,
                    w - thickness, h - thickness, arc * 2, arc * 2);
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(thickness + 2, thickness + 2, thickness + 2, thickness + 2);
        }
    }

    /** Thin, minimal scrollbar UI. */
    public static class SlimScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        private static final int THUMB_W = 5;

        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(180, 200, 188);
            trackColor = new Color(243, 246, 244); // matches BACKGROUND, avoid alpha
        }

        @Override
        protected javax.swing.JButton createDecreaseButton(int orientation) {
            return createZeroButton();
        }

        @Override
        protected javax.swing.JButton createIncreaseButton(int orientation) {
            return createZeroButton();
        }

        private javax.swing.JButton createZeroButton() {
            javax.swing.JButton b = new javax.swing.JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, java.awt.Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(thumbColor);
            int x = r.x + (r.width - THUMB_W) / 2;
            g2.fillRoundRect(x, r.y + 2, THUMB_W, r.height - 4, THUMB_W, THUMB_W);
            g2.dispose();
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle r) {
            // no track painting
        }
    }
}
