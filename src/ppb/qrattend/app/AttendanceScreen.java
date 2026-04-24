package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.AppDomain;
import ppb.qrattend.model.AppDomain.AttendanceRecord;
import ppb.qrattend.model.AppDomain.AttendanceSession;
import ppb.qrattend.model.AppDomain.StudentProfile;
import ppb.qrattend.model.ModelUser;

final class AttendanceScreen {

    private AttendanceScreen() {
    }

    static JPanel build(AppShell shell) {
        AppDataStore store = shell.getStore();
        ModelUser user = shell.getCurrentUser();
        JPanel page = AppTheme.createPage();
        AttendanceSession session = store.getSessionForTeacher(user.getUserId());

        page.add(shell.createMetricsRow(
                AppTheme.createStatCard("Class Status", session.getState().getLabel(), AppTheme.BRAND),
                AppTheme.createStatCard("Subject", session.getSubjectName(), AppTheme.INFO),
                AppTheme.createStatCard("Students", String.valueOf(store.getStudentCountForTeacher(user.getUserId())), AppTheme.SUCCESS),
                AppTheme.createStatCard("Taken Today", String.valueOf(store.getRecentAttendanceRecords(50, user.getUserId()).size()), AppTheme.WARNING)
        ));
        page.add(Box.createVerticalStrut(16));

        page.add(shell.createTeacherAssistantSection(
                "attendance",
                "Ask AI",
                "Ask why a scan did not work, who may need follow-up, or what attendance pattern needs attention.",
                "Are there any unusual attendance or scan patterns I should watch?"
        ));
        page.add(Box.createVerticalStrut(16));

        JTextField qrField = shell.newTextField();
        JTextField overrideSubjectField = shell.newTextField();
        overrideSubjectField.setText(session.getSubjectName());
        JTextArea overrideReasonArea = shell.newTextArea();
        overrideReasonArea.setText("The camera or scanner is not available right now.");

        JButton scan = new JButton("Scan QR");
        AppTheme.stylePrimaryButton(scan);
        scan.addActionListener(event -> {
            shell.showResult(store.markAttendanceFromQr(user.getUserId(), qrField.getText()));
            shell.refreshView();
        });

        JButton cameraScan = new JButton("Use Camera");
        AppTheme.styleSecondaryButton(cameraScan);
        cameraScan.addActionListener(event -> shell.openQrScannerFor(qrField));

        JButton openOverride = new JButton("Open Temporary Class");
        AppTheme.styleSecondaryButton(openOverride);
        openOverride.addActionListener(event -> {
            shell.showResult(store.openOverrideSession(user.getUserId(), overrideSubjectField.getText(), overrideReasonArea.getText()));
            shell.refreshView();
        });

        JButton closeOverride = new JButton("Close Temporary Class");
        AppTheme.styleDangerButton(closeOverride);
        closeOverride.addActionListener(event -> {
            shell.showResult(store.closeOverrideSession(user.getUserId()));
            shell.refreshView();
        });

        JPanel scanGrid = new JPanel(new GridLayout(2, 2, 12, 12));
        scanGrid.setOpaque(false);
        scanGrid.add(shell.labeledField("QR Code or Student ID", qrField));
        scanGrid.add(shell.labeledField("Subject", overrideSubjectField));
        scanGrid.add(shell.labeledField("Reason", new javax.swing.JScrollPane(overrideReasonArea)));
        JPanel actionBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actionBox.setOpaque(false);
        actionBox.add(scan);
        actionBox.add(cameraScan);
        actionBox.add(openOverride);
        actionBox.add(closeOverride);
        scanGrid.add(shell.labeledField("Actions", actionBox));
        page.add(shell.createSection("Take Attendance", "Use QR during class, or open a temporary class when needed.", scanGrid));
        page.add(Box.createVerticalStrut(16));

        DefaultTableModel studentModel = shell.createTableModel("Student ID", "Student", "Email", "QR Status");
        for (StudentProfile student : store.getStudentsForTeacher(user.getUserId())) {
            studentModel.addRow(new Object[]{
                student.getStudentId(),
                student.getFullName(),
                student.getEmail(),
                student.getQrStatus().getLabel()
            });
        }
        JTable studentTable = new JTable(studentModel);
        JTextField manualNoteField = shell.newTextField();
        manualNoteField.setText("Attendance was marked without QR.");
        JButton markManual = new JButton("Mark Without QR");
        AppTheme.styleSecondaryButton(markManual);
        markManual.addActionListener(event -> {
            int row = studentTable.getSelectedRow();
            if (row < 0) {
                shell.showMessage("Choose a student first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }

            shell.showResult(store.markManualAttendance(user.getUserId(), (String) studentModel.getValueAt(row, 0), manualNoteField.getText()));
            shell.refreshView();
        });

        JPanel manualBody = new JPanel(new BorderLayout(0, 12));
        manualBody.setOpaque(false);
        manualBody.add(shell.wrapTable(studentTable), BorderLayout.CENTER);
        JPanel manualActions = new JPanel(new GridLayout(1, 2, 12, 0));
        manualActions.setOpaque(false);
        manualActions.add(shell.labeledField("Note", manualNoteField));
        manualActions.add(shell.labeledField("Attendance Without QR", markManual));
        manualBody.add(manualActions, BorderLayout.SOUTH);
        page.add(shell.createSection("Attendance Without QR", "Choose a student from the class list when a QR code cannot be used.", manualBody));
        page.add(Box.createVerticalStrut(16));

        DefaultTableModel attendanceModel = shell.createTableModel("Student", "Subject", "Time", "Source", "Status", "Note");
        for (AttendanceRecord record : store.getRecentAttendanceRecords(10, user.getUserId())) {
            attendanceModel.addRow(new Object[]{
                record.getStudentName(),
                record.getSubjectName(),
                record.getTimestamp().format(AppDomain.DATE_TIME_FORMAT),
                record.getSource().getLabel(),
                record.getStatus().getLabel(),
                record.getNote()
            });
        }
        JTable attendanceTable = new JTable(attendanceModel);
        page.add(shell.createSection("Recent Attendance", "Latest attendance saved from QR or without QR.", shell.wrapTable(attendanceTable)));
        return page;
    }
}
