package ppb.qrattend.app;

import java.awt.GridLayout;
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
        AppDataStore store = shell.getStore();
        ModelUser user = shell.getCurrentUser();
        JPanel page = AppTheme.createPage();

        List<String> subjects = new ArrayList<>();
        subjects.add("All Subjects");
        subjects.addAll(store.getSubjectOptions(user.isAdmin() ? null : user.getUserId()));

        JComboBox<String> subjectCombo = new JComboBox<>(subjects.toArray(String[]::new));
        AppTheme.styleCombo(subjectCombo);
        subjectCombo.setSelectedItem(shell.getReportSubjectFilter());

        JButton refreshReport = new JButton("Refresh Report");
        AppTheme.stylePrimaryButton(refreshReport);
        refreshReport.addActionListener(event -> {
            shell.setReportSubjectFilter((String) subjectCombo.getSelectedItem());
            shell.setReportPreview(store.exportAttendanceSummary(user.isAdmin() ? null : user.getUserId(), shell.getReportSubjectFilter()));
            shell.showMessage("Report updated for " + shell.getReportSubjectFilter() + ".", AppTheme.SUCCESS);
            shell.refreshView();
        });

        JPanel filterRow = new JPanel(new GridLayout(1, 2, 12, 0));
        filterRow.setOpaque(false);
        filterRow.add(shell.labeledField("Subject", subjectCombo));
        filterRow.add(shell.labeledField("Report", refreshReport));
        page.add(shell.createSection("Report Filters", "Choose a subject and refresh the report.", filterRow));
        page.add(Box.createVerticalStrut(16));

        DefaultTableModel reportModel = shell.createTableModel("Student", "Subject", "Timestamp", "Source", "Status", "Note");
        List<AttendanceRecord> records = user.isAdmin()
                ? store.getAttendanceRecords()
                : store.getAttendanceRecordsForTeacher(user.getUserId());
        for (AttendanceRecord record : records) {
            if (!"All Subjects".equalsIgnoreCase(shell.getReportSubjectFilter())
                    && !record.getSubjectName().equalsIgnoreCase(shell.getReportSubjectFilter())) {
                continue;
            }
            reportModel.addRow(new Object[]{
                record.getStudentName(),
                record.getSubjectName(),
                record.getTimestamp().format(AppDomain.DATE_TIME_FORMAT),
                record.getSource().getLabel(),
                record.getStatus().getLabel(),
                record.getNote()
            });
        }
        JTable reportTable = new JTable(reportModel);
        page.add(shell.createSection("Attendance Records", "Attendance records for the current filter.", shell.wrapTable(reportTable)));
        page.add(Box.createVerticalStrut(16));

        JTextArea previewArea = shell.newTextArea();
        previewArea.setEditable(false);
        previewArea.setText(shell.getReportPreview().isBlank()
                ? "Press Refresh Report to load the current class summary."
                : shell.getReportPreview());
        page.add(shell.createSection("Report Summary", "A simple text summary of the current filter.", new javax.swing.JScrollPane(previewArea)));
        page.add(Box.createVerticalStrut(16));

        if (user.isTeacher()) {
            page.add(shell.createTeacherAssistantSection(
                    "reports|" + shell.getReportSubjectFilter(),
                    "Ask AI",
                    "Ask AI to explain the report, summarize attendance, or suggest next steps.",
                    "Summarize this report and tell me what needs attention."
            ));
        }
        return page;
    }
}
