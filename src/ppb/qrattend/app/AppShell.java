package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.ModelUser;
import ppb.qrattend.qr.QrScannerDialog;

public class AppShell extends JPanel {

    private static final DateTimeFormatter TIME_INPUT_FORMAT = DateTimeFormatter.ofPattern("h:mm a");
    private static final int DEFAULT_TEXT_FIELD_WIDTH = 220;

    private final AppStore store;
    private final ModelUser user;
    private final Runnable onLogout;
    private final Map<String, String> navigation = new LinkedHashMap<>();
    private final Map<String, JButton> navButtons = new LinkedHashMap<>();

    private final JLabel titleLabel = new JLabel();
    private final JLabel subtitleLabel = new JLabel();
    private final JPanel bannerPanel = new JPanel(new BorderLayout());
    private final JPanel contentHost = new JPanel(new CardLayout());
    private final Timer attendanceTimer;

    private Timer bannerClearTimer;
    private boolean mustChangePassword;

    private String selectedView;
    private String bannerMessage = "";
    private Color bannerColor = AppTheme.INFO;

    private Integer reportTeacherFilter;
    private Integer reportSectionFilter;
    private Integer reportSubjectFilter;

    public AppShell(AppStore store, ModelUser user, Runnable onLogout) {
        this.store = store;
        this.user = user;
        this.onLogout = onLogout;
        AppTheme.install();
        setLayout(new BorderLayout());
        setBackground(AppTheme.BACKGROUND);
        buildNavigation();
        buildLayout();
        store.addListener(this::refreshSelectedView);
        selectedView = "home";
        mustChangePassword = user.isMustChangePassword();
        if (mustChangePassword) {
            selectedView = "password_change";
        }
        // Refresh the attendance page every 30 seconds so it can open the teacher's
        // current class as soon as the saved class time starts.
        attendanceTimer = new Timer(30000, event -> {
            if ("attendance".equals(selectedView)) {
                refreshSelectedView();
            }
        });
        attendanceTimer.start();
        refreshSelectedView();
    }

    private void buildNavigation() {
        navigation.clear();
        navigation.put("home", "Home");
        if (user.isAdmin()) {
            navigation.put("teachers", "Teachers");
            navigation.put("setup", "School Lists");
            navigation.put("students", "Students");
            navigation.put("schedules", "Schedule");
            navigation.put("requests", "Requests");
            navigation.put("reports", "Reports");
        } else {
            navigation.put("attendance", "Attendance");
            navigation.put("students", "Class List");
            navigation.put("schedule", "My Schedule");
            navigation.put("reports", "Reports");
        }
    }

    private void buildLayout() {
        add(buildSidebar(), BorderLayout.WEST);
        add(buildMainPane(), BorderLayout.CENTER);
    }

    private JComponent buildSidebar() {
        JPanel sidebar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, AppTheme.BRAND_DARK, 0, getHeight(), AppTheme.BRAND_DARKER);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        sidebar.setOpaque(false);
        sidebar.setPreferredSize(new Dimension(210, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(22, 16, 22, 16));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JLabel brand = new JLabel("QR Attend");
        brand.setFont(AppTheme.headlineFont(24));
        brand.setForeground(Color.WHITE);
        sidebar.add(brand);
        sidebar.add(Box.createVerticalStrut(4));

        JLabel sub = new JLabel("Simple school attendance");
        sub.setFont(AppTheme.bodyFont(11));
        sub.setForeground(new Color(190, 222, 198));
        sidebar.add(sub);
        sidebar.add(Box.createVerticalStrut(18));

        JLabel role = AppTheme.createPill(user.getRole().getLabel(), new Color(255, 255, 255, 28), new Color(220, 240, 228));
        role.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(role);
        sidebar.add(Box.createVerticalStrut(8));

        JLabel name = new JLabel(user.getFullName());
        name.setFont(AppTheme.bodyFont(12));
        name.setForeground(new Color(217, 235, 222));
        sidebar.add(name);
        sidebar.add(Box.createVerticalStrut(18));

        for (Map.Entry<String, String> entry : navigation.entrySet()) {
            JButton button = new JButton(entry.getValue());
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            button.addActionListener(event -> openView(entry.getKey()));
            navButtons.put(entry.getKey(), button);
            sidebar.add(button);
            sidebar.add(Box.createVerticalStrut(6));
        }

        sidebar.add(Box.createVerticalGlue());

        JButton logout = new JButton("Sign Out");
        logout.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        AppTheme.styleNavButton(logout, false);
        logout.addActionListener(event -> {
            // Stop the refresh timer before handing control back to the login screen.
            // Without this, multiple timers accumulate across login/logout cycles.
            attendanceTimer.stop();
            if (bannerClearTimer != null) {
                bannerClearTimer.stop();
                bannerClearTimer = null;
            }
            onLogout.run();
        });
        sidebar.add(logout);
        return sidebar;
    }

    private JComponent buildMainPane() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        panel.add(buildHeader(), BorderLayout.NORTH);
        panel.add(contentHost, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 12));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        titleLabel.setFont(AppTheme.headlineFont(30));
        titleLabel.setForeground(AppTheme.TEXT_PRIMARY);
        subtitleLabel.setFont(AppTheme.bodyFont(13));
        subtitleLabel.setForeground(AppTheme.TEXT_MUTED);

        text.add(titleLabel);
        text.add(Box.createVerticalStrut(4));
        text.add(subtitleLabel);
        header.add(text, BorderLayout.NORTH);

        bannerPanel.setOpaque(false);
        header.add(bannerPanel, BorderLayout.SOUTH);
        return header;
    }

    private void refreshSelectedView() {
        updateNav();
        updateHeader();
        updateBanner();
        contentHost.removeAll();
        contentHost.add(AppTheme.wrapScrollable(buildView()), "selected");
        ((CardLayout) contentHost.getLayout()).show(contentHost, "selected");
        revalidate();
        repaint();
    }

    private void updateNav() {
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            AppTheme.styleNavButton(entry.getValue(), entry.getKey().equals(selectedView));
        }
    }

    private void updateHeader() {
        if ("home".equals(selectedView)) {
            titleLabel.setText("Home");
            subtitleLabel.setText(user.isAdmin()
                    ? "Pick the next school setup task."
                    : "Pick what you want to do next.");
        } else if ("teachers".equals(selectedView)) {
            titleLabel.setText("Teachers");
            subtitleLabel.setText("Add teachers and resend password emails.");
        } else if ("setup".equals(selectedView)) {
            titleLabel.setText("School Lists");
            subtitleLabel.setText("Save school lists once, then reuse them everywhere.");
        } else if ("students".equals(selectedView)) {
            titleLabel.setText(user.isAdmin() ? "Students" : "My Class List");
            subtitleLabel.setText(user.isAdmin()
                    ? "Import students by section or add one student at a time."
                    : "These students come from the sections in your schedule.");
        } else if ("schedules".equals(selectedView)) {
            titleLabel.setText("Schedule");
            subtitleLabel.setText("Build classes using saved teachers, sections, subjects, and rooms.");
        } else if ("attendance".equals(selectedView)) {
            titleLabel.setText("Attendance");
            subtitleLabel.setText("Scan QR first. If that fails, click the student name.");
        } else if ("schedule".equals(selectedView)) {
            titleLabel.setText("My Schedule");
            subtitleLabel.setText("Check your classes and ask for changes when needed.");
        } else if ("requests".equals(selectedView)) {
            titleLabel.setText("Requests");
            subtitleLabel.setText("Check pending requests first.");
        } else if ("reports".equals(selectedView)) {
            titleLabel.setText("Reports");
            subtitleLabel.setText("Read the summary, then ask AI if you need help.");
        } else {
            titleLabel.setText("QR Attend");
            subtitleLabel.setText("");
        }
    }

    private void updateBanner() {
        bannerPanel.removeAll();
        if (bannerMessage.isBlank()) {
            return;
        }
        JPanel banner = new AppTheme.RoundedPanel(AppTheme.RADIUS_SM, bannerColor);
        banner.setLayout(new BorderLayout());
        JLabel label = new JLabel("  " + bannerMessage);
        label.setForeground(Color.WHITE);
        label.setFont(AppTheme.bodyFont(13));
        label.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        banner.add(label, BorderLayout.CENTER);
        bannerPanel.add(banner, BorderLayout.CENTER);
    }

    private JPanel buildView() {
        if ("password_change".equals(selectedView)) {
            return PasswordChangeScreen.build(this);
        }
        if ("home".equals(selectedView)) {
            return user.isAdmin() ? AdminDashboardScreen.build(this) : TeacherDashboardScreen.build(this);
        } else if ("teachers".equals(selectedView)) {
            return TeachersScreen.build(this);
        } else if ("setup".equals(selectedView)) {
            return SectionsScreen.build(this);
        } else if ("students".equals(selectedView)) {
            return user.isAdmin() ? AdminStudentsScreen.build(this) : TeacherRosterScreen.build(this);
        } else if ("schedules".equals(selectedView)) {
            return AdminSchedulesScreen.build(this);
        } else if ("attendance".equals(selectedView)) {
            return AttendanceScreen.build(this);
        } else if ("schedule".equals(selectedView)) {
            return TeacherScheduleScreen.build(this);
        } else if ("requests".equals(selectedView)) {
            return RequestsScreen.build(this);
        } else if ("reports".equals(selectedView)) {
            return ReportsScreen.build(this);
        } else {
            return user.isAdmin() ? AdminDashboardScreen.build(this) : TeacherDashboardScreen.build(this);
        }
    }

    public void openView(String viewKey) {
        if (mustChangePassword) {
            return;  // block all navigation until password is changed
        }
        if (!navigation.containsKey(viewKey)) {
            return;
        }
        selectedView = viewKey;
        refreshSelectedView();
    }

    public void onPasswordChanged() {
        mustChangePassword = false;
        selectedView = "home";
        refreshSelectedView();
    }

    public void showMessage(String message, Color color) {
        bannerMessage = message == null ? "" : message.trim();
        bannerColor = color == null ? AppTheme.INFO : color;

        if (bannerClearTimer != null) {
            bannerClearTimer.stop();
            bannerClearTimer = null;
        }

        if (!bannerMessage.isBlank()) {
            bannerClearTimer = new Timer(4000, event -> {
                bannerMessage = "";
                bannerClearTimer = null;
                updateBanner();
                revalidate();
                repaint();
            });
            bannerClearTimer.setRepeats(false);
            bannerClearTimer.start();
        }
    }

    public void showResult(AppStore.ActionResult result) {
        if (result == null) {
            return;
        }
        if (result.isSuccess()) {
            showMessage(result.getMessage(), AppTheme.SUCCESS);
        } else if (result.isWarning()) {
            String full = result.getDetail().isBlank()
                    ? result.getMessage()
                    : result.getMessage() + " " + result.getDetail();
            showMessage(full, AppTheme.WARNING);
        } else {
            showMessage(result.getMessage(), AppTheme.DANGER);
        }
    }

    public void refreshView() {
        refreshSelectedView();
    }

    public JPanel createSection(String title, String subtitle, JComponent body) {
        JPanel section = AppTheme.createSection(title, subtitle);
        section.add(body, BorderLayout.CENTER);
        return section;
    }

    public JPanel createMetricsRow(JPanel... cards) {
        JPanel row = new JPanel(new GridLayout(1, cards.length, 14, 0));
        row.setOpaque(false);
        for (JPanel card : cards) {
            row.add(card);
        }
        return row;
    }

    public JPanel wrapTable(JTable table) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        AppTheme.styleTable(table);
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(AppTheme.wrapScrollable(table), BorderLayout.CENTER);
        return body;
    }

    public DefaultTableModel createTableModel(String... headers) {
        return new DefaultTableModel(headers, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    public JPanel labeledField(String labelText, JComponent input) {
        JPanel group = new JPanel();
        group.setOpaque(false);
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(labelText);
        label.setFont(AppTheme.bodyFont(12));
        label.setForeground(AppTheme.TEXT_MUTED);
        group.add(label);
        group.add(Box.createVerticalStrut(6));
        group.add(input);
        return group;
    }

    public JTextField newTextField() {
        JTextField field = new JTextField();
        field.setColumns(18);
        AppTheme.styleField(field);
        Dimension size = field.getPreferredSize();
        field.setPreferredSize(new Dimension(DEFAULT_TEXT_FIELD_WIDTH, size.height));
        field.setMinimumSize(new Dimension(140, size.height));
        return field;
    }

    public JTextArea newTextArea() {
        JTextArea area = new JTextArea(5, 18);
        AppTheme.styleTextArea(area);
        return area;
    }

    public JComboBox<String> newTimeCombo() {
        List<String> timeOptions = buildTimeOptions();
        String[] timeArray = new String[timeOptions.size()];
        for (int i = 0; i < timeOptions.size(); i++) {
            timeArray[i] = timeOptions.get(i);
        }
        JComboBox<String> combo = new JComboBox<>(timeArray);
        AppTheme.styleCombo(combo);
        return combo;
    }

    public JComboBox<DayOfWeek> newDayCombo() {
        JComboBox<DayOfWeek> combo = new JComboBox<>(DayOfWeek.values());
        AppTheme.styleCombo(combo);
        return combo;
    }

    public LocalTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value, TIME_INPUT_FORMAT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    public void openQrScannerFor(JTextField targetField) {
        QrScannerDialog.open(this, targetField::setText);
    }

    public JPanel createTeacherAiPanel(String scopeKey, String subtitle, String questionHint) {
        JTextArea conversationArea = newTextArea();
        conversationArea.setEditable(false);
        conversationArea.setText(store.getAiConversation(user.getUserId(), scopeKey));

        JTextArea questionArea = newTextArea();
        questionArea.setRows(3);
        questionArea.setText(questionHint);

        JButton askButton = new JButton("Ask AI");
        JButton clearButton = new JButton("Clear");
        AppTheme.stylePrimaryButton(askButton);
        AppTheme.styleSecondaryButton(clearButton);

        askButton.addActionListener(event -> {
            showResult(store.askAi(user.getUserId(), scopeKey, questionArea.getText()));
            refreshView();
        });
        clearButton.addActionListener(event -> {
            showResult(store.clearAiConversation(user.getUserId(), scopeKey));
            refreshView();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(askButton);
        actions.add(clearButton);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(new JScrollPane(conversationArea), BorderLayout.CENTER);
        body.add(AppTheme.stack(new JScrollPane(questionArea), actions), BorderLayout.SOUTH);
        return createSection("Ask AI", subtitle, body);
    }

    public String friendlyYesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    public AppStore getStore() {
        return store;
    }

    public ModelUser getCurrentUser() {
        return user;
    }

    public Integer getReportTeacherFilter() {
        return reportTeacherFilter;
    }

    public void setReportTeacherFilter(Integer reportTeacherFilter) {
        this.reportTeacherFilter = reportTeacherFilter;
    }

    public Integer getReportSectionFilter() {
        return reportSectionFilter;
    }

    public void setReportSectionFilter(Integer reportSectionFilter) {
        this.reportSectionFilter = reportSectionFilter;
    }

    public Integer getReportSubjectFilter() {
        return reportSubjectFilter;
    }

    public void setReportSubjectFilter(Integer reportSubjectFilter) {
        this.reportSubjectFilter = reportSubjectFilter;
    }

    private List<String> buildTimeOptions() {
        List<String> values = new ArrayList<>();
        LocalTime time = LocalTime.of(7, 0);
        while (!time.isAfter(LocalTime.of(21, 0))) {
            values.add(time.format(TIME_INPUT_FORMAT));
            time = time.plusMinutes(30);
        }
        return values;
    }
}
