package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

final class AppFlowPanels {

    private AppFlowPanels() {
    }

    static JPanel createActionTile(String step, String title, String text, String buttonText, Runnable action) {
        JPanel panel = new AppTheme.RoundedPanel(AppTheme.RADIUS_MD, AppTheme.SURFACE);
        panel.setLayout(new BorderLayout(0, 14));
        panel.setBorder(BorderFactory.createCompoundBorder(
                new AppTheme.RoundedBorder(AppTheme.RADIUS_MD, AppTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)
        ));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

        JLabel stepLabel = AppTheme.createPill(step, new Color(232, 242, 236), AppTheme.BRAND_DARK);
        stepLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        top.add(stepLabel);
        top.add(Box.createVerticalStrut(10));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AppTheme.headlineFont(18));
        titleLabel.setForeground(AppTheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        top.add(titleLabel);
        top.add(Box.createVerticalStrut(6));

        JLabel textLabel = new JLabel("<html>" + text + "</html>");
        textLabel.setFont(AppTheme.bodyFont(13));
        textLabel.setForeground(AppTheme.TEXT_MUTED);
        textLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        top.add(textLabel);

        panel.add(top, BorderLayout.CENTER);

        JButton button = new JButton(buttonText);
        AppTheme.stylePrimaryButton(button);
        button.setPreferredSize(new Dimension(140, 44));
        button.addActionListener(event -> action.run());
        panel.add(button, BorderLayout.SOUTH);
        return panel;
    }

    static JPanel createTileRow(JPanel... tiles) {
        JPanel row = new JPanel(new GridLayout(1, tiles.length, 14, 0));
        row.setOpaque(false);
        for (JPanel tile : tiles) {
            row.add(tile);
        }
        return row;
    }

    static JPanel createSimpleList(String title, List<String> lines) {
        JPanel panel = new AppTheme.RoundedPanel(AppTheme.RADIUS_MD, AppTheme.SURFACE_ALT);
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                new AppTheme.RoundedBorder(AppTheme.RADIUS_MD, AppTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)
        ));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AppTheme.headlineFont(14));
        titleLabel.setForeground(AppTheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        body.add(titleLabel);
        body.add(Box.createVerticalStrut(8));

        if (lines == null || lines.isEmpty()) {
            JLabel emptyLabel = new JLabel("Nothing here yet.");
            emptyLabel.setFont(AppTheme.bodyFont(12));
            emptyLabel.setForeground(AppTheme.TEXT_MUTED);
            emptyLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            body.add(emptyLabel);
        } else {
            for (String line : lines) {
                JLabel item = new JLabel("<html>" + line + "</html>");
                item.setFont(AppTheme.bodyFont(12));
                item.setForeground(AppTheme.TEXT_PRIMARY);
                item.setAlignmentX(JComponent.LEFT_ALIGNMENT);
                body.add(item);
                body.add(Box.createVerticalStrut(4));
            }
        }

        panel.add(body, BorderLayout.CENTER);
        return panel;
    }
}
