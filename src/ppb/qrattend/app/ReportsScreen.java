package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.AppDomain;
import ppb.qrattend.model.AppDomain.AttendanceRecord;
import ppb.qrattend.model.ModelUser;

final class ReportsScreen {

    private ReportsScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        page.add(buildFilterSection(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildSummarySection(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildReportTableSection(shell));
        page.add(Box.createVerticalStrut(16));

        if (shell.getCurrentUser().isTeacher()) {
            page.add(shell.createTeacherAssistantSection(
                    "reports|" + shell.getReportSubjectFilter(),
                    "Ask AI",
                    "Ask AI to explain the report, point out patterns, or tell you what needs attention.",
                    "What should I notice in this report?"
            ));
        }

        return page;
    }

    private static JPanel buildFilterSection(AppShell shell) {
        ModelUser user = shell.getCurrentUser();
        List<String> subjects = new ArrayList<>();
        subjects.add("All Subjects");
        subjects.addAll(shell.getStore().getSubjectOptions(user.isAdmin() ? null : user.getUserId()));

        JComboBox<String> subjectCombo = new JComboBox<>(subjects.toArray(String[]::new));
        AppTheme.styleCombo(subjectCombo);
        subjectCombo.setSelectedItem(shell.getReportSubjectFilter());

        JButton refreshButton = new JButton("Show Report");
        AppTheme.stylePrimaryButton(refreshButton);
        refreshButton.addActionListener(event -> {
            shell.setReportSubjectFilter((String) subjectCombo.getSelectedItem());
            shell.setReportPreview(shell.getStore().exportAttendanceSummary(
                    user.isAdmin() ? null : user.getUserId(),
                    shell.getReportSubjectFilter()
            ));
            shell.showMessage("Report updated.", AppTheme.SUCCESS);
            shell.refreshView();
        });

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(AppFlowPanels.createSimpleList("How to use this page", java.util.List.of(
                "Pick one subject if you want a smaller report.",
                "Press Show Report after changing the filter.",
                "Use Ask AI if you want the report explained."
        )), BorderLayout.NORTH);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        actions.setOpaque(false);
        actions.add(shell.labeledField("Subject", subjectCombo));
        actions.add(shell.labeledField("Action", refreshButton));
        body.add(actions, BorderLayout.SOUTH);

        return shell.createSection("Report filter", "Choose what report you want to read.", body);
    }

    private static JPanel buildSummarySection(AppShell shell) {
        JTextArea previewArea = shell.newTextArea();
        previewArea.setEditable(false);
        previewArea.setText(shell.getReportPreview().isBlank()
                ? "Press Show Report to load the summary."
                : shell.getReportPreview());
        return shell.createSection("Summary", "This is the simple summary for the current filter.", new javax.swing.JScrollPane(previewArea));
    }

    private static JPanel buildReportTableSection(AppShell shell) {
        ModelUser user = shell.getCurrentUser();
        DefaultTableModel model = shell.createTableModel("Student", "Subject", "Timestamp", "Source", "Status", "Note");
        List<AttendanceRecord> records = user.isAdmin()
                ? shell.getStore().getAttendanceRecords()
                : shell.getStore().getAttendanceRecordsForTeacher(user.getUserId());

        for (AttendanceRecord record : records) {
            if (!"All Subjects".equalsIgnoreCase(shell.getReportSubjectFilter())
                    && !record.getSubjectName().equalsIgnoreCase(shell.getReportSubjectFilter())) {
                continue;
            }

            model.addRow(new Object[]{
                record.getStudentName(),
                record.getSubjectName(),
                record.getTimestamp().format(AppDomain.DATE_TIME_FORMAT),
                record.getSource().getLabel(),
                record.getStatus().getLabel(),
                record.getNote()
            });
        }

        JTable table = new JTable(model);
        return shell.createSection("Attendance records", "These records match the report filter.", shell.wrapTable(table));
    }
}
