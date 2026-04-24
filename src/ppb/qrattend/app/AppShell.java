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
import java.util.Locale;
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
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.AppDomain;
import ppb.qrattend.model.AppDomain.AttendanceRecord;
import ppb.qrattend.model.AppDomain.AttendanceSession;
import ppb.qrattend.model.AppDomain.EmailDispatch;
import ppb.qrattend.model.AppDomain.ScheduleChangeRequest;
import ppb.qrattend.model.ModelUser;
import ppb.qrattend.qr.QrScannerDialog;

public class AppShell extends JPanel {

    private static final DateTimeFormatter TIME_INPUT_FORMAT = DateTimeFormatter.ofPattern("h:mm a");

    private final AppDataStore store;
    private final ModelUser user;
    private final Runnable onLogout;
    private final Map<String, String> navigation = new LinkedHashMap<>();
    private final Map<String, JButton> navButtons = new LinkedHashMap<>();

    private final JLabel titleLabel = new JLabel();
    private final JLabel subtitleLabel = new JLabel();
    private final JPanel bannerPanel = new JPanel(new BorderLayout());
    private final JPanel contentHost = new JPanel(new CardLayout());
    private final JPanel detailHost = new JPanel(new BorderLayout());

    private String selectedView;
    private String bannerMessage = "";
    private Color bannerColor = AppTheme.INFO;
    private String reportSubjectFilter = "All Subjects";
    private String reportPreview = "";

    public AppShell(AppDataStore store, ModelUser user, Runnable onLogout) {
        this.store = store;
        this.user = user;
        this.onLogout = onLogout;
        AppTheme.install();
        setLayout(new BorderLayout());
        setBackground(AppTheme.BACKGROUND);
        buildNavigation();
        buildLayout();
        store.addListener(this::refreshSelectedView);
        selectedView = navigation.keySet().iterator().next();
        refreshSelectedView();
    }

    private void buildNavigation() {
        navigation.clear();
        if (user.isAdmin()) {
            navigation.put("dashboard", "Dashboard");
            navigation.put("teachers", "Teachers");
            navigation.put("students", "Students");
            navigation.put("schedules", "Schedules");
            navigation.put("requests", "Requests");
            navigation.put("reports", "Reports");
        } else {
            navigation.put("dashboard", "Dashboard");
            navigation.put("attendance", "Attendance");
            navigation.put("students", "My Roster");
            navigation.put("schedule", "My Schedule");
            navigation.put("reports", "Reports");
        }
    }

    private void buildLayout() {
        add(buildSidebar(), BorderLayout.WEST);
        add(buildMainPane(), BorderLayout.CENTER);
        add(buildDetailPane(), BorderLayout.EAST);
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
        sidebar.setPreferredSize(new Dimension(232, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(24, 18, 20, 18));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JLabel brand = new JLabel("QR Attend");
        brand.setFont(AppTheme.headlineFont(22));
        brand.setForeground(Color.WHITE);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(brand);
        sidebar.add(Box.createVerticalStrut(4));

        JLabel subtitle = new JLabel("School attendance");
        subtitle.setFont(AppTheme.bodyFont(11));
        subtitle.setForeground(new Color(168, 205, 181));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(subtitle);
        sidebar.add(Box.createVerticalStrut(18));

        JLabel pill = AppTheme.createPill(user.getRole().getLabel(), new Color(255, 255, 255, 28), new Color(220, 240, 228));
        pill.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(pill);
        sidebar.add(Box.createVerticalStrut(6));

        JLabel nameTag = new JLabel(user.getFullName());
        nameTag.setFont(AppTheme.bodyFont(12));
        nameTag.setForeground(new Color(200, 225, 210));
        nameTag.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(nameTag);
        sidebar.add(Box.createVerticalStrut(22));

        JLabel navSection = new JLabel("MENU");
        navSection.setFont(AppTheme.bodyFont(10));
        navSection.setForeground(new Color(130, 170, 150));
        navSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(navSection);
        sidebar.add(Box.createVerticalStrut(8));

        for (Map.Entry<String, String> entry : navigation.entrySet()) {
            JButton button = new JButton(entry.getValue());
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            button.addActionListener(event -> {
                selectedView = entry.getKey();
                refreshSelectedView();
            });
            navButtons.put(entry.getKey(), button);
            sidebar.add(button);
            sidebar.add(Box.createVerticalStrut(4));
        }

        sidebar.add(Box.createVerticalGlue());

        JPanel divider = new JPanel();
        divider.setOpaque(false);
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        divider.setBackground(new Color(255, 255, 255, 30));
        divider.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(255, 255, 255, 30)));
        divider.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(divider);
        sidebar.add(Box.createVerticalStrut(14));

        JButton logout = new JButton("Sign Out");
        logout.setAlignmentX(Component.LEFT_ALIGNMENT);
        logout.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        AppTheme.styleNavButton(logout, false);
        logout.addActionListener(event -> onLogout.run());
        sidebar.add(logout);
        return sidebar;
    }

    private JComponent buildMainPane() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 16));
        panel.add(buildHeader(), BorderLayout.NORTH);
        panel.add(contentHost, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 12));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));

        JPanel textBlock = new JPanel();
        textBlock.setOpaque(false);
        textBlock.setLayout(new BoxLayout(textBlock, BoxLayout.Y_AXIS));
        titleLabel.setFont(AppTheme.headlineFont(28));
        titleLabel.setForeground(AppTheme.TEXT_PRIMARY);
        subtitleLabel.setFont(AppTheme.bodyFont(13));
        subtitleLabel.setForeground(AppTheme.TEXT_MUTED);
        textBlock.add(titleLabel);
        textBlock.add(Box.createVerticalStrut(3));
        textBlock.add(subtitleLabel);
        header.add(textBlock, BorderLayout.NORTH);

        bannerPanel.setOpaque(false);
        header.add(bannerPanel, BorderLayout.SOUTH);
        return header;
    }

    private JComponent buildDetailPane() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setPreferredSize(new Dimension(300, 0));
        wrapper.setBorder(BorderFactory.createEmptyBorder(24, 0, 24, 20));
        wrapper.setOpaque(false);
        wrapper.add(detailHost, BorderLayout.CENTER);
        return wrapper;
    }

    private void refreshSelectedView() {
        updateNavigationStyles();
        updateHeader();
        updateBanner();
        contentHost.removeAll();
        contentHost.add(AppTheme.wrapScrollable(buildViewContent()), "selected");
        ((CardLayout) contentHost.getLayout()).show(contentHost, "selected");
        refreshDetailPane();
        revalidate();
        repaint();
    }

    private void updateNavigationStyles() {
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            AppTheme.styleNavButton(entry.getValue(), entry.getKey().equals(selectedView));
        }
    }

    private void updateHeader() {
        String title;
        String subtitle;
        switch (selectedView) {
            case "dashboard" -> {
                title = user.isAdmin() ? "Admin Dashboard" : "Teacher Dashboard";
                subtitle = user.isAdmin()
                        ? "See classes happening now, requests that need approval, and attendance updates."
                        : "See your classes today and the latest attendance updates.";
            }
            case "teachers" -> {
                title = "Teachers";
                subtitle = "Add teachers, send passwords again, and check if the email was sent.";
            }
            case "students" -> {
                title = user.isAdmin() ? "Students" : "My Class List";
                subtitle = user.isAdmin()
                        ? "Add students by section, choose their teacher, and send their QR code."
                        : "Check your class list and ask the admin to remove a student if needed.";
            }
            case "schedules" -> {
                title = "Class Schedule";
                subtitle = "Set class days and times, and avoid overlapping classes.";
            }
            case "requests" -> {
                title = "Requests";
                subtitle = "Review schedule changes and student removal requests.";
            }
            case "attendance" -> {
                title = "Take Attendance";
                subtitle = "Scan QR codes, open a temporary class if needed, or mark attendance without QR.";
            }
            case "schedule" -> {
                title = "My Schedule";
                subtitle = "Check your approved class schedule and ask for changes if something is wrong.";
            }
            case "reports" -> {
                title = "Reports";
                subtitle = "Filter attendance records and review the class summary.";
            }
            default -> {
                title = "Dashboard";
                subtitle = "Review school attendance updates.";
            }
        }
        titleLabel.setText(title);
        subtitleLabel.setText(subtitle);
    }

    private void updateBanner() {
        bannerPanel.removeAll();
        if (bannerMessage == null || bannerMessage.isBlank()) {
            return;
        }

        JPanel bannerCard = new AppTheme.RoundedPanel(AppTheme.RADIUS_SM, bannerColor);
        bannerCard.setLayout(new BorderLayout());
        JLabel label = new JLabel("  " + bannerMessage);
        label.setOpaque(false);
        label.setForeground(Color.WHITE);
        label.setFont(AppTheme.bodyFont(13));
        label.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        bannerCard.add(label, BorderLayout.CENTER);
        bannerPanel.add(bannerCard, BorderLayout.CENTER);
    }

    private JPanel buildViewContent() {
        return switch (selectedView) {
            case "dashboard" -> user.isAdmin() ? AdminDashboardScreen.build(this) : TeacherDashboardScreen.build(this);
            case "teachers" -> TeachersScreen.build(this);
            case "students" -> user.isAdmin() ? AdminStudentsScreen.build(this) : TeacherRosterScreen.build(this);
            case "schedules" -> AdminSchedulesScreen.build(this);
            case "requests" -> RequestsScreen.build(this);
            case "attendance" -> AttendanceScreen.build(this);
            case "schedule" -> TeacherScheduleScreen.build(this);
            case "reports" -> ReportsScreen.build(this);
            default -> user.isAdmin() ? AdminDashboardScreen.build(this) : TeacherDashboardScreen.build(this);
        };
    }

    private void refreshDetailPane() {
        detailHost.removeAll();

        JPanel panel = new AppTheme.RoundedPanel(AppTheme.RADIUS_MD, AppTheme.SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new AppTheme.RoundedBorder(AppTheme.RADIUS_MD, AppTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(20, 18, 18, 18)
        ));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        panel.add(detailBlock("Your Account", List.of(
                user.getFullName(),
                user.getEmail(),
                "Account type: " + user.getRole().getLabel()
        )));
        panel.add(Box.createVerticalStrut(16));

        if (user.isAdmin()) {
            panel.add(detailBlock("Quick Look", List.of(
                    "Teachers: " + store.getTeacherCount(),
                    "Need approval: " + store.getPendingRequestCount(),
                    "Email issues: " + store.getFailedEmailCount(),
                    "Classes happening now: " + store.getActiveClassCount()
            )));
        } else {
            AttendanceSession session = store.getSessionForTeacher(user.getUserId());
            panel.add(detailBlock("Class Right Now", List.of(
                    "Status: " + session.getState().getLabel(),
                    "Subject: " + session.getSubjectName(),
                    "Room: " + session.getRoom(),
                    "Note: " + (session.getNote() == null || session.getNote().isBlank() ? "None" : session.getNote())
            )));
        }
        panel.add(Box.createVerticalStrut(16));

        List<String> recentEmailLines = new ArrayList<>();
        for (EmailDispatch dispatch : store.getRecentEmailDispatches(4)) {
            recentEmailLines.add(dispatch.getRecipient() + " | " + dispatch.getStatus().getLabel());
        }
        panel.add(detailBlock("Recent Emails", recentEmailLines.isEmpty() ? List.of("No emails yet.") : recentEmailLines));
        panel.add(Box.createVerticalStrut(16));

        if ("attendance".equals(selectedView) || "reports".equals(selectedView)) {
            List<String> attendanceLines = new ArrayList<>();
            for (AttendanceRecord record : store.getRecentAttendanceRecords(4, user.isTeacher() ? user.getUserId() : null)) {
                attendanceLines.add(record.getStudentName() + " | " + record.getSubjectName() + " | " + record.getSource().getLabel());
            }
            panel.add(detailBlock("Recent Attendance", attendanceLines.isEmpty() ? List.of("No attendance yet.") : attendanceLines));
        } else if ("requests".equals(selectedView) || "schedule".equals(selectedView)) {
            List<String> requestLines = new ArrayList<>();
            List<ScheduleChangeRequest> requests = user.isAdmin()
                    ? store.getScheduleRequests()
                    : store.getScheduleRequestsForTeacher(user.getUserId());
            for (int index = 0; index < Math.min(4, requests.size()); index++) {
                ScheduleChangeRequest request = requests.get(index);
                requestLines.add("#" + request.getId() + " | " + request.getStatus().getLabel() + " | " + request.getRequester());
            }
            panel.add(detailBlock("Requests", requestLines.isEmpty() ? List.of("No requests yet.") : requestLines));
        } else {
            panel.add(detailBlock("Helpful Notes", List.of(
                    "Use the menu on the left to open the page you need.",
                    "The newest updates appear here after you save changes.",
                    "Check the banner at the top for success or warning messages."
            )));
        }

        detailHost.add(panel, BorderLayout.CENTER);
    }

    void showMessage(String message, Color color) {
        bannerMessage = message;
        bannerColor = color;
    }

    void showResult(AppDataStore.ActionResult result) {
        if (result == null) {
            return;
        }
        if (result.isWarning()) {
            showMessage(result.getMessage(), AppTheme.WARNING);
        } else if (result.isSuccess()) {
            showMessage(result.getMessage(), AppTheme.SUCCESS);
        } else {
            showMessage(result.getMessage(), AppTheme.DANGER);
        }
    }

    JPanel createSection(String title, String subtitle, JComponent body) {
        JPanel section = AppTheme.createSection(title, subtitle);
        section.add(body, BorderLayout.CENTER);
        return section;
    }

    JPanel createTeacherAssistantSection(String scopeKey, String title, String subtitle, String suggestedQuestion) {
        JTextArea conversationArea = newTextArea();
        conversationArea.setEditable(false);
        conversationArea.setText(store.getTeacherAssistantConversation(user.getUserId(), scopeKey));

        JTextArea questionArea = newTextArea();
        questionArea.setRows(4);
        questionArea.setText(suggestedQuestion);

        JButton askButton = new JButton("Ask AI");
        JButton clearButton = new JButton("Clear");
        AppTheme.stylePrimaryButton(askButton);
        AppTheme.styleSecondaryButton(clearButton);

        askButton.addActionListener(event -> {
            showResult(store.askTeacherAssistant(user.getUserId(), scopeKey, questionArea.getText()));
            refreshView();
        });
        clearButton.addActionListener(event -> {
            showResult(store.clearTeacherAssistantConversation(user.getUserId(), scopeKey));
            refreshView();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(askButton);
        actions.add(clearButton);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(new JScrollPane(conversationArea), BorderLayout.CENTER);

        JPanel promptArea = new JPanel(new BorderLayout(0, 12));
        promptArea.setOpaque(false);
        promptArea.add(labeledField("Ask AI", new JScrollPane(questionArea)), BorderLayout.CENTER);
        promptArea.add(actions, BorderLayout.SOUTH);
        body.add(promptArea, BorderLayout.SOUTH);
        return createSection(title, subtitle, body);
    }

    String friendlyStatus(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return switch (value.trim().toUpperCase(Locale.ENGLISH)) {
            case "ACTIVE" -> "Active";
            case "APPROVED" -> "Approved";
            case "INACTIVE" -> "Inactive";
            default -> value;
        };
    }

    JPanel wrapTable(JTable table) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        AppTheme.styleTable(table);
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(AppTheme.wrapScrollable(table), BorderLayout.CENTER);
        return body;
    }

    JPanel createMetricsRow(JPanel... cards) {
        JPanel row = new JPanel(new GridLayout(1, cards.length, 14, 0));
        row.setOpaque(false);
        for (JPanel card : cards) {
            row.add(card);
        }
        return row;
    }

    DefaultTableModel createTableModel(String... headers) {
        return new DefaultTableModel(headers, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    JPanel labeledField(String labelText, JComponent input) {
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

    JTextField newTextField() {
        JTextField field = new JTextField();
        AppTheme.styleField(field);
        return field;
    }

    JTextArea newTextArea() {
        JTextArea area = new JTextArea(4, 18);
        AppTheme.styleTextArea(area);
        return area;
    }

    JComboBox<String> newTimeCombo() {
        JComboBox<String> combo = new JComboBox<>(buildTimeOptions().toArray(String[]::new));
        AppTheme.styleCombo(combo);
        return combo;
    }

    JComboBox<DayOfWeek> newDayCombo() {
        JComboBox<DayOfWeek> combo = new JComboBox<>(DayOfWeek.values());
        AppTheme.styleCombo(combo);
        return combo;
    }

    LocalTime parseTimeValue(String value) {
        try {
            return LocalTime.parse(value, TIME_INPUT_FORMAT);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    void openQrScannerFor(JTextField qrField) {
        QrScannerDialog.open(this, qrValue -> {
            qrField.setText(qrValue);
            showResult(store.markAttendanceFromQr(user.getUserId(), qrValue));
            refreshView();
        });
    }

    AppDataStore getStore() {
        return store;
    }

    ModelUser getCurrentUser() {
        return user;
    }

    String getReportSubjectFilter() {
        return reportSubjectFilter;
    }

    void setReportSubjectFilter(String reportSubjectFilter) {
        this.reportSubjectFilter = reportSubjectFilter == null ? "All Subjects" : reportSubjectFilter;
    }

    String getReportPreview() {
        return reportPreview;
    }

    void setReportPreview(String reportPreview) {
        this.reportPreview = reportPreview == null ? "" : reportPreview;
    }

    void refreshView() {
        refreshSelectedView();
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

    private JPanel detailBlock(String title, List<String> lines) {
        JPanel block = new JPanel();
        block.setOpaque(false);
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));

        JPanel headRow = new JPanel();
        headRow.setOpaque(false);
        headRow.setLayout(new BoxLayout(headRow, BoxLayout.X_AXIS));
        JLabel dot = new JLabel("> ");
        dot.setFont(AppTheme.bodyFont(10));
        dot.setForeground(AppTheme.BRAND);
        headRow.add(dot);
        JLabel heading = new JLabel(title.toUpperCase(Locale.ENGLISH));
        heading.setFont(AppTheme.bodyFont(10));
        heading.setForeground(AppTheme.TEXT_MUTED);
        headRow.add(heading);
        block.add(headRow);
        block.add(Box.createVerticalStrut(8));

        for (String line : lines) {
            JLabel value = new JLabel("<html>" + line + "</html>");
            value.setFont(AppTheme.bodyFont(12));
            value.setForeground(AppTheme.TEXT_PRIMARY);
            block.add(value);
            block.add(Box.createVerticalStrut(4));
        }
        return block;
    }
}
