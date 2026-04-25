package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.CoreModels.ScheduleRequest;
import ppb.qrattend.model.CoreModels.StudentRemovalRequest;

final class RequestsScreen {

    private RequestsScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        page.add(buildScheduleRequestSection(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildStudentRequestSection(shell));
        return page;
    }

    private static JPanel buildScheduleRequestSection(AppShell shell) {
        List<ScheduleRequest> requests = shell.getStore().getScheduleRequests();
        DefaultTableModel model = shell.createTableModel("Teacher", "Section", "Subject", "Room", "Day", "Time", "Reason", "Status");
        for (ScheduleRequest request : requests) {
            model.addRow(new Object[]{
                request.teacherName(),
                request.sectionName(),
                request.subjectName(),
                request.roomName(),
                request.day(),
                request.getTimeLabel(),
                request.reason() == null ? "" : request.reason(),
                request.status().getLabel()
            });
        }

        JTable table = new JTable(model);
        JButton approveButton = new JButton("Approve");
        JButton rejectButton = new JButton("Reject");
        AppTheme.stylePrimaryButton(approveButton);
        AppTheme.styleDangerButton(rejectButton);

        approveButton.addActionListener(event -> handleScheduleReview(shell, table, requests, true));
        rejectButton.addActionListener(event -> handleScheduleReview(shell, table, requests, false));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        actions.setOpaque(false);
        actions.add(approveButton);
        actions.add(rejectButton);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(AppFlowPanels.createSimpleList("Schedule Requests", List.of(
                "Pending requests appear first in the list.",
                "Approve only if the new schedule details are correct.",
                "Reject if the change should not be used."
        )), BorderLayout.NORTH);
        body.add(shell.wrapTable(table), BorderLayout.CENTER);
        body.add(actions, BorderLayout.SOUTH);
        return shell.createSection("Schedule Requests", "Choose one request from the list and review it.", body);
    }

    private static JPanel buildStudentRequestSection(AppShell shell) {
        List<StudentRemovalRequest> requests = shell.getStore().getStudentRemovalRequests();
        DefaultTableModel model = shell.createTableModel("Teacher", "Student", "Section", "Reason", "Status");
        for (StudentRemovalRequest request : requests) {
            model.addRow(new Object[]{
                request.teacherName(),
                request.studentName(),
                request.sectionName(),
                request.reason(),
                request.status().getLabel()
            });
        }

        JTable table = new JTable(model);
        JButton approveButton = new JButton("Approve");
        JButton rejectButton = new JButton("Reject");
        AppTheme.stylePrimaryButton(approveButton);
        AppTheme.styleDangerButton(rejectButton);

        approveButton.addActionListener(event -> handleStudentReview(shell, table, requests, true));
        rejectButton.addActionListener(event -> handleStudentReview(shell, table, requests, false));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        actions.setOpaque(false);
        actions.add(approveButton);
        actions.add(rejectButton);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(AppFlowPanels.createSimpleList("Student Removal Requests", List.of(
                "Pending requests appear first in the list.",
                "Approved requests remove the student from the active class list.",
                "Reject if the student should stay in the section."
        )), BorderLayout.NORTH);
        body.add(shell.wrapTable(table), BorderLayout.CENTER);
        body.add(actions, BorderLayout.SOUTH);
        return shell.createSection("Student Removal Requests", "Choose one request from the list and review it.", body);
    }

    private static void handleScheduleReview(AppShell shell, JTable table, List<ScheduleRequest> requests, boolean approve) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= requests.size()) {
            shell.showMessage("Choose a request first.", AppTheme.WARNING);
            shell.refreshView();
            return;
        }
        shell.showResult(shell.getStore().reviewScheduleRequest(
                shell.getCurrentUser().getUserId(),
                requests.get(row).id(),
                approve
        ));
        shell.refreshView();
    }

    private static void handleStudentReview(AppShell shell, JTable table, List<StudentRemovalRequest> requests, boolean approve) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= requests.size()) {
            shell.showMessage("Choose a request first.", AppTheme.WARNING);
            shell.refreshView();
            return;
        }
        shell.showResult(shell.getStore().reviewStudentRemovalRequest(
                shell.getCurrentUser().getUserId(),
                requests.get(row).id(),
                approve
        ));
        shell.refreshView();
    }
}
