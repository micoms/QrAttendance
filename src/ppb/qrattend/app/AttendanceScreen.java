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
        JPanel page = AppTheme.createPage();
        AttendanceSession session = shell.getStore().getSessionForTeacher(shell.getCurrentUser().getUserId());

        page.add(buildStatusRow(shell, session));
        page.add(Box.createVerticalStrut(16));
        page.add(buildStartClassSection(shell, session));
        page.add(Box.createVerticalStrut(16));
        page.add(buildScanSection(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildHelpSection(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildRecentAttendanceSection(shell));
        return page;
    }

    private static JPanel buildStatusRow(AppShell shell, AttendanceSession session) {
        return shell.createMetricsRow(
                AppTheme.createStatCard("Class status", session.getState().getLabel(), AppTheme.BRAND),
                AppTheme.createStatCard("Subject", session.getSubjectName(), AppTheme.INFO),
                AppTheme.createStatCard("Taken today",
                        String.valueOf(shell.getStore().getRecentAttendanceRecords(50, shell.getCurrentUser().getUserId()).size()),
                        AppTheme.SUCCESS)
        );
    }

    private static JPanel buildStartClassSection(AppShell shell, AttendanceSession session) {
        JTextField subjectField = shell.newTextField();
        subjectField.setText(session.getSubjectName());
        JTextArea reasonArea = shell.newTextArea();
        reasonArea.setText("My class needs to start now.");

        JButton startButton = new JButton("Start Class");
        AppTheme.stylePrimaryButton(startButton);
        startButton.addActionListener(event -> {
            shell.showResult(shell.getStore().openOverrideSession(
                    shell.getCurrentUser().getUserId(),
                    subjectField.getText(),
                    reasonArea.getText()
            ));
            shell.refreshView();
        });

        JButton closeButton = new JButton("Close Temporary Class");
        AppTheme.styleDangerButton(closeButton);
        closeButton.addActionListener(event -> {
            shell.showResult(shell.getStore().closeOverrideSession(shell.getCurrentUser().getUserId()));
            shell.refreshView();
        });

        JPanel info = AppFlowPanels.createSimpleList("Step 1: Start class", java.util.List.of(
                "Current status: " + session.getState().getLabel(),
                "Current subject: " + session.getSubjectName(),
                session.isOverrideSession()
                        ? "You are using a temporary class right now."
                        : "If your regular class is already open, move to Step 2."
        ));

        JPanel form = new JPanel(new GridLayout(1, 3, 12, 0));
        form.setOpaque(false);
        form.add(shell.labeledField("Subject", subjectField));
        form.add(shell.labeledField("Why do you need this?", new javax.swing.JScrollPane(reasonArea)));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(startButton);
        actions.add(closeButton);
        form.add(shell.labeledField("Action", actions));

        return shell.createSection("Step 1: Start class",
                "If your regular class is not open, start a temporary class first.",
                AppTheme.stack(info, form));
    }

    private static JPanel buildScanSection(AppShell shell) {
        JTextField qrField = shell.newTextField();

        JButton cameraButton = new JButton("Use Camera");
        AppTheme.styleSecondaryButton(cameraButton);
        cameraButton.addActionListener(event -> shell.openQrScannerFor(qrField));

        JButton scanButton = new JButton("Scan Student QR");
        AppTheme.stylePrimaryButton(scanButton);
        scanButton.addActionListener(event -> {
            shell.showResult(shell.getStore().markAttendanceFromQr(
                    shell.getCurrentUser().getUserId(),
                    qrField.getText()
            ));
            shell.refreshView();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(scanButton);
        actions.add(cameraButton);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(shell.labeledField("Student QR or student ID", qrField), BorderLayout.NORTH);
        body.add(actions, BorderLayout.SOUTH);

        return shell.createSection("Step 2: Scan student QR",
                "Scan one student at a time. If the camera does not work, use the help section below.",
                body);
    }

    private static JPanel buildHelpSection(AppShell shell) {
        DefaultTableModel studentModel = shell.createTableModel("Student ID", "Student", "Email");
        for (StudentProfile student : shell.getStore().getStudentsForTeacher(shell.getCurrentUser().getUserId())) {
            studentModel.addRow(new Object[]{
                student.getStudentId(),
                student.getFullName(),
                student.getEmail()
            });
        }
        JTable studentTable = new JTable(studentModel);

        JTextField noteField = shell.newTextField();
        noteField.setText("QR could not be used.");

        JButton markManualButton = new JButton("Mark Without QR");
        AppTheme.styleSecondaryButton(markManualButton);
        markManualButton.addActionListener(event -> {
            int row = studentTable.getSelectedRow();
            if (row < 0) {
                shell.showMessage("Choose a student first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }

            shell.showResult(shell.getStore().markManualAttendance(
                    shell.getCurrentUser().getUserId(),
                    (String) studentModel.getValueAt(row, 0),
                    noteField.getText()
            ));
            shell.refreshView();
        });

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(AppFlowPanels.createSimpleList("Step 3: Need help?", java.util.List.of(
                "Use this only when QR scanning is not possible.",
                "Choose the student first, then save the attendance.",
                "Add a short note so you remember why QR was not used."
        )), BorderLayout.NORTH);
        body.add(shell.wrapTable(studentTable), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new GridLayout(1, 2, 12, 0));
        bottom.setOpaque(false);
        bottom.add(shell.labeledField("Why was QR not used?", noteField));
        bottom.add(shell.labeledField("Action", markManualButton));
        body.add(bottom, BorderLayout.SOUTH);

        return shell.createSection("Need help?", "Use this only if scanning does not work.", body);
    }

    private static JPanel buildRecentAttendanceSection(AppShell shell) {
        DefaultTableModel attendanceModel = shell.createTableModel("Student", "Subject", "Time", "Source", "Status");
        for (AttendanceRecord record : shell.getStore().getRecentAttendanceRecords(10, shell.getCurrentUser().getUserId())) {
            attendanceModel.addRow(new Object[]{
                record.getStudentName(),
                record.getSubjectName(),
                record.getTimestamp().format(AppDomain.DATE_TIME_FORMAT),
                record.getSource().getLabel(),
                record.getStatus().getLabel()
            });
        }

        JTable attendanceTable = new JTable(attendanceModel);
        return shell.createSection("Recent attendance", "These are the latest records saved for your class.", shell.wrapTable(attendanceTable));
    }
}
