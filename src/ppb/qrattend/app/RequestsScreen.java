package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.AppDomain;
import ppb.qrattend.model.AppDomain.ScheduleChangeRequest;
import ppb.qrattend.model.ModelUser;

final class RequestsScreen {

    private RequestsScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        page.add(buildScheduleRequestSection(shell, true));
        page.add(Box.createVerticalStrut(16));
        page.add(buildStudentRequestSection(shell, true));
        page.add(Box.createVerticalStrut(16));
        page.add(buildScheduleRequestSection(shell, false));
        page.add(Box.createVerticalStrut(16));
        page.add(buildStudentRequestSection(shell, false));
        return page;
    }

    private static JPanel buildScheduleRequestSection(AppShell shell, boolean pendingOnly) {
        List<ScheduleChangeRequest> filtered = new ArrayList<>();
        for (ScheduleChangeRequest request : shell.getStore().getScheduleRequests()) {
            boolean isPending = request.getStatus() == AppDomain.ScheduleRequestStatus.PENDING;
            if (pendingOnly == isPending) {
                filtered.add(request);
            }
        }

        DefaultTableModel model = shell.createTableModel("ID", "Teacher", "Old Schedule", "Requested Schedule", "Status", "Reason");
        for (ScheduleChangeRequest request : filtered) {
            model.addRow(new Object[]{
                request.getId(),
                request.getRequester(),
                request.getOldValue(),
                request.getRequestedValue(),
                request.getStatus().getLabel(),
                request.getReason()
            });
        }

        JTable table = new JTable(model);
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(shell.wrapTable(table), BorderLayout.CENTER);

        if (pendingOnly) {
            JButton approveButton = new JButton("Approve");
            JButton rejectButton = new JButton("Reject");
            AppTheme.stylePrimaryButton(approveButton);
            AppTheme.styleDangerButton(rejectButton);
            approveButton.addActionListener(event -> reviewSchedule(shell, table, model, true));
            rejectButton.addActionListener(event -> reviewSchedule(shell, table, model, false));

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            actions.setOpaque(false);
            actions.add(approveButton);
            actions.add(rejectButton);
            body.add(actions, BorderLayout.SOUTH);
        }

        return shell.createSection(
                pendingOnly ? "Schedule requests waiting now" : "Reviewed schedule requests",
                pendingOnly ? "Finish these first." : "These were already reviewed.",
                body
        );
    }

    private static JPanel buildStudentRequestSection(AppShell shell, boolean pendingOnly) {
        List<AppDomain.StudentRemovalRequest> filtered = new ArrayList<>();
        for (AppDomain.StudentRemovalRequest request : shell.getStore().getStudentRemovalRequests()) {
            boolean isPending = request.getStatus() == AppDomain.ScheduleRequestStatus.PENDING;
            if (pendingOnly == isPending) {
                filtered.add(request);
            }
        }

        DefaultTableModel model = shell.createTableModel("ID", "Teacher", "Student", "Section", "Reason", "Status");
        for (AppDomain.StudentRemovalRequest request : filtered) {
            model.addRow(new Object[]{
                request.getId(),
                request.getTeacherName(),
                request.getStudentName(),
                request.getSectionName(),
                request.getReason(),
                request.getStatus().getLabel()
            });
        }

        JTable table = new JTable(model);
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(shell.wrapTable(table), BorderLayout.CENTER);

        if (pendingOnly) {
            JButton approveButton = new JButton("Approve");
            JButton rejectButton = new JButton("Reject");
            AppTheme.stylePrimaryButton(approveButton);
            AppTheme.styleDangerButton(rejectButton);
            approveButton.addActionListener(event -> reviewStudentRemoval(shell, table, model, true));
            rejectButton.addActionListener(event -> reviewStudentRemoval(shell, table, model, false));

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            actions.setOpaque(false);
            actions.add(approveButton);
            actions.add(rejectButton);
            body.add(actions, BorderLayout.SOUTH);
        }

        return shell.createSection(
                pendingOnly ? "Class list requests waiting now" : "Reviewed class list requests",
                pendingOnly ? "These still need your answer." : "These were already reviewed.",
                body
        );
    }

    private static void reviewSchedule(AppShell shell, JTable table, DefaultTableModel model, boolean approve) {
        int row = table.getSelectedRow();
        if (row < 0) {
            shell.showMessage("Choose a request first.", AppTheme.WARNING);
            shell.refreshView();
            return;
        }

        shell.showResult(shell.getStore().reviewScheduleRequest(
                shell.getCurrentUser().getUserId(),
                (Integer) model.getValueAt(row, 0),
                approve,
                shell.getCurrentUser().getFullName()
        ));
        shell.refreshView();
    }

    private static void reviewStudentRemoval(AppShell shell, JTable table, DefaultTableModel model, boolean approve) {
        int row = table.getSelectedRow();
        if (row < 0) {
            shell.showMessage("Choose a student request first.", AppTheme.WARNING);
            shell.refreshView();
            return;
        }

        shell.showResult(shell.getStore().reviewStudentRemovalRequest(
                shell.getCurrentUser().getUserId(),
                (Integer) model.getValueAt(row, 0),
                approve,
                shell.getCurrentUser().getFullName()
        ));
        shell.refreshView();
    }
}
