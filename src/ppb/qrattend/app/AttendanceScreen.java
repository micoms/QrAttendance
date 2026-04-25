package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.CoreModels.AttendanceRecord;
import ppb.qrattend.model.CoreModels.AttendanceSession;
import ppb.qrattend.model.CoreModels.Schedule;
import ppb.qrattend.model.CoreModels.Section;
import ppb.qrattend.model.CoreModels.SessionStatus;
import ppb.qrattend.model.CoreModels.Student;
import ppb.qrattend.model.CoreModels.Subject;
import ppb.qrattend.util.AppClock;

final class AttendanceScreen {

    private AttendanceScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        AttendanceSession session = shell.getStore().getCurrentSessionForTeacher(shell.getCurrentUser().getUserId());

        page.add(buildStatusRow(shell, session));
        page.add(Box.createVerticalStrut(16));
        page.add(buildClassSection(shell, session));
        page.add(Box.createVerticalStrut(16));
        page.add(buildScanSection(shell, session));
        page.add(Box.createVerticalStrut(16));
        page.add(buildManualSection(shell, session));
        page.add(Box.createVerticalStrut(16));
        page.add(buildRecentSection(shell));
        return page;
    }

    private static JPanel buildStatusRow(AppShell shell, AttendanceSession session) {
        int currentStudents = shell.getStore().getCurrentClassStudents(shell.getCurrentUser().getUserId()).size();
        return shell.createMetricsRow(
                AppTheme.createStatCard("Class Status", session.status().getLabel(), AppTheme.BRAND),
                AppTheme.createStatCard("Current Subject", session.subjectName(), AppTheme.INFO),
                AppTheme.createStatCard("Students In Class", String.valueOf(currentStudents), AppTheme.SUCCESS)
        );
    }

    private static JPanel buildClassSection(AppShell shell, AttendanceSession session) {
        if (session.status() == SessionStatus.OPEN && !session.temporary()) {
            // Scheduled class is open — show a clear confirmation with class details
            return shell.createSection("Step 1: Class Status", "Your scheduled class opened automatically.", AppFlowPanels.createSimpleList("Class is open", List.of(
                    "Section: " + session.sectionName(),
                    "Subject: " + session.subjectName(),
                    "Room: " + session.roomName(),
                    "Checked at: " + AppClock.nowLabel(),
                    "Go to Step 2 and scan student QR codes.",
                    "If a QR code does not work, use the student buttons in Step 3.",
                    "This page checks your saved class time every 30 seconds."
            )));
        }

        if (session.status() == SessionStatus.OPEN && session.temporary()) {
            JButton endButton = new JButton("End Temporary Class");
            AppTheme.styleDangerButton(endButton);
            endButton.addActionListener(event -> {
                shell.showResult(shell.getStore().endTemporaryClass(shell.getCurrentUser().getUserId()));
                shell.refreshView();
            });

            JPanel body = new JPanel(new BorderLayout(0, 12));
            body.setOpaque(false);
            body.add(AppFlowPanels.createSimpleList("Temporary class is open", List.of(
                    "Current section: " + session.sectionName(),
                    "Current subject: " + session.subjectName(),
                    session.reason().isBlank() ? "No reason was saved." : "Reason: " + session.reason()
            )), BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
            actions.setOpaque(false);
            actions.add(endButton);
            body.add(actions, BorderLayout.SOUTH);
            return shell.createSection("Step 1: Temporary Class", "Use the button below when you are done.", body);
        }

        List<Schedule> teacherSchedules = shell.getStore().getSchedulesForTeacher(shell.getCurrentUser().getUserId());
        Schedule nextSchedule = shell.getStore().getNextScheduleForTeacher(shell.getCurrentUser().getUserId());
        List<Section> sectionChoices = uniqueSections(teacherSchedules, shell.getStore().getSections());
        List<Subject> subjectChoices = uniqueSubjects(teacherSchedules, shell.getStore().getSubjects());
        String[] sectionChoiceNames = new String[sectionChoices.size()];
        for (int i = 0; i < sectionChoices.size(); i++) {
            sectionChoiceNames[i] = sectionChoices.get(i).name();
        }
        String[] subjectChoiceNames = new String[subjectChoices.size()];
        for (int i = 0; i < subjectChoices.size(); i++) {
            subjectChoiceNames[i] = subjectChoices.get(i).name();
        }
        JComboBox<String> sectionCombo = new JComboBox<>(sectionChoiceNames);
        JComboBox<String> subjectCombo = new JComboBox<>(subjectChoiceNames);
        JTextArea reasonArea = shell.newTextArea();
        reasonArea.setRows(3);
        reasonArea.setText("My class needs to start now.");
        AppTheme.styleCombo(sectionCombo);
        AppTheme.styleCombo(subjectCombo);

        JButton startButton = new JButton("Start Temporary Class");
        AppTheme.stylePrimaryButton(startButton);
        startButton.addActionListener(event -> {
            Section section = itemAt(sectionChoices, sectionCombo.getSelectedIndex());
            Subject subject = itemAt(subjectChoices, subjectCombo.getSelectedIndex());
            if (section == null || subject == null) {
                shell.showMessage("Choose the section and subject first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }
            shell.showResult(shell.getStore().startTemporaryClass(
                    shell.getCurrentUser().getUserId(),
                    section.id(),
                    subject.id(),
                    reasonArea.getText()
            ));
            shell.refreshView();
        });

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        form.setOpaque(false);
        form.add(shell.labeledField("Section", sectionCombo));
        form.add(shell.labeledField("Subject", subjectCombo));
        form.add(shell.labeledField("Action", startButton));

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(AppFlowPanels.createSimpleList("No class is open right now", buildNoClassLines(nextSchedule)), BorderLayout.NORTH);
        body.add(form, BorderLayout.CENTER);
        body.add(shell.labeledField("Why are you opening a temporary class?", new JScrollPane(reasonArea)), BorderLayout.SOUTH);
        return shell.createSection("Step 1: Open Class", "This step is only for special cases.", body);
    }

    private static List<String> buildNoClassLines(Schedule nextSchedule) {
        List<String> lines = new ArrayList<>();
        lines.add("Checked at: " + AppClock.nowLabel());
        if (nextSchedule == null) {
            lines.add("There are no more saved classes for today.");
        } else {
            lines.add("Next class today: " + nextSchedule.subjectName() + " | "
                    + nextSchedule.sectionName() + " | " + nextSchedule.getTimeLabel());
        }
        lines.add("If your regular class time starts, this page opens it for you automatically.");
        lines.add("Use Temporary Class only when there is still no class open.");
        lines.add("Pick the section and subject from the saved lists. No typing needed.");
        return lines;
    }

    private static JPanel buildScanSection(AppShell shell, AttendanceSession session) {
        boolean classOpen = session.status() == SessionStatus.OPEN;
        JTextField qrField = shell.newTextField();

        JButton scanButton = new JButton("Scan Student QR");
        JButton cameraButton = new JButton("Use Camera");
        AppTheme.stylePrimaryButton(scanButton);
        AppTheme.styleSecondaryButton(cameraButton);
        scanButton.setEnabled(classOpen);
        cameraButton.setEnabled(classOpen);

        scanButton.addActionListener(event -> {
            shell.showResult(shell.getStore().markAttendanceFromQr(
                    shell.getCurrentUser().getUserId(),
                    qrField.getText()
            ));
            shell.refreshView();
        });
        cameraButton.addActionListener(event -> shell.openQrScannerFor(qrField));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        actions.setOpaque(false);
        actions.add(scanButton);
        actions.add(cameraButton);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(shell.labeledField("Student QR", qrField), BorderLayout.NORTH);
        body.add(actions, BorderLayout.SOUTH);
        return shell.createSection("Step 2: Scan Student QR",
                classOpen ? "Scan one student at a time." : "Open your class first before scanning.",
                body);
    }

    private static JPanel buildManualSection(AppShell shell, AttendanceSession session) {
        JTextField noteField = shell.newTextField();
        noteField.setText("QR could not be used.");

        JButton markAllAbsentButton = new JButton("Mark All Absent");
        AppTheme.styleDangerButton(markAllAbsentButton);
        markAllAbsentButton.setEnabled(session.status() == SessionStatus.OPEN);
        markAllAbsentButton.addActionListener(event -> {
            shell.showResult(shell.getStore().markAllAbsent(shell.getCurrentUser().getUserId()));
            shell.refreshView();
        });

        JPanel actions = new JPanel(new BorderLayout(0, 8));
        actions.setOpaque(false);
        actions.add(shell.labeledField("Note", noteField), BorderLayout.CENTER);
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(markAllAbsentButton);
        actions.add(buttonRow, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(AppFlowPanels.createSimpleList("Need help?", List.of(
                "Use this only when scanning does not work.",
                "Click the student button instead of typing student ID.",
                "Only students from the current class are shown here."
        )), BorderLayout.NORTH);
        body.add(buildManualStudentList(shell, session, noteField), BorderLayout.CENTER);
        body.add(actions, BorderLayout.SOUTH);
        return shell.createSection("Step 3: Mark Without QR", "This is the backup way to save attendance.", body);
    }

    private static JScrollPane buildManualStudentList(AppShell shell, AttendanceSession session, JTextField noteField) {
        List<Student> students = shell.getStore().getCurrentClassStudents(shell.getCurrentUser().getUserId());
        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));

        if (students.isEmpty()) {
            list.add(AppFlowPanels.createSimpleList("Students", List.of(
                    session.status() == SessionStatus.OPEN
                            ? "No students were found for this class section yet."
                            : "Open a class first so the student buttons can appear here."
            )));
        } else {
            for (Student student : students) {
                list.add(createStudentCard(shell, student, noteField, session.status() == SessionStatus.OPEN));
                list.add(Box.createVerticalStrut(10));
            }
        }

        JScrollPane scrollPane = AppTheme.wrapScrollable(list);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        return scrollPane;
    }

    private static JPanel createStudentCard(AppShell shell, Student student, JTextField noteField, boolean enabled) {
        JPanel card = new AppTheme.RoundedPanel(AppTheme.RADIUS_MD, AppTheme.SURFACE_ALT);
        card.setLayout(new BorderLayout(12, 0));
        card.setBorder(BorderFactory.createCompoundBorder(
                new AppTheme.RoundedBorder(AppTheme.RADIUS_MD, AppTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)
        ));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(student.fullName());
        nameLabel.setFont(AppTheme.headlineFont(15));
        nameLabel.setForeground(AppTheme.TEXT_PRIMARY);
        text.add(nameLabel);
        text.add(Box.createVerticalStrut(4));

        JLabel infoLabel = new JLabel(student.studentCode() + " | " + student.sectionName());
        infoLabel.setFont(AppTheme.bodyFont(12));
        infoLabel.setForeground(AppTheme.TEXT_MUTED);
        text.add(infoLabel);

        JButton button = new JButton("Mark Without QR");
        AppTheme.styleSecondaryButton(button);
        button.setEnabled(enabled);
        button.addActionListener(event -> {
            shell.showResult(shell.getStore().markManualAttendance(
                    shell.getCurrentUser().getUserId(),
                    student.id(),
                    noteField.getText()
            ));
            shell.refreshView();
        });

        card.add(text, BorderLayout.CENTER);
        card.add(button, BorderLayout.EAST);
        return card;
    }

    private static JPanel buildRecentSection(AppShell shell) {
        DefaultTableModel model = shell.createTableModel("Student", "Section", "Subject", "Time", "Method");
        for (AttendanceRecord record : shell.getStore().getRecentAttendanceRecords(shell.getCurrentUser().getUserId(), 10)) {
            model.addRow(new Object[]{
                record.studentName(),
                record.sectionName(),
                record.subjectName(),
                record.recordedAt().format(ppb.qrattend.model.CoreModels.DATE_TIME_FORMAT),
                record.method().getLabel()
            });
        }

        JTable table = new JTable(model);
        return shell.createSection("Recent Attendance", "These are the last saved attendance records.", shell.wrapTable(table));
    }

    private static List<Section> uniqueSections(List<Schedule> schedules, List<Section> fallback) {
        Map<Integer, Section> map = new LinkedHashMap<>();
        for (Schedule schedule : schedules) {
            map.put(schedule.sectionId(), new Section(schedule.sectionId(), schedule.sectionName()));
        }
        if (map.isEmpty()) {
            for (Section section : fallback) {
                map.put(section.id(), section);
            }
        }
        return new ArrayList<>(map.values());
    }

    private static List<Subject> uniqueSubjects(List<Schedule> schedules, List<Subject> fallback) {
        Map<Integer, Subject> map = new LinkedHashMap<>();
        for (Schedule schedule : schedules) {
            map.put(schedule.subjectId(), new Subject(schedule.subjectId(), schedule.subjectName()));
        }
        if (map.isEmpty()) {
            for (Subject subject : fallback) {
                map.put(subject.id(), subject);
            }
        }
        return new ArrayList<>(map.values());
    }

    private static <T> T itemAt(List<T> items, int index) {
        if (index < 0 || index >= items.size()) {
            return null;
        }
        return items.get(index);
    }
}
