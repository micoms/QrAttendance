package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
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
        AppDataStore store = shell.getStore();
        ModelUser user = shell.getCurrentUser();
        JPanel page = AppTheme.createPage();

        DefaultTableModel requestModel = shell.createTableModel("ID", "Teacher", "Old Schedule", "Requested Schedule", "Status", "Reason");
        for (ScheduleChangeRequest request : store.getScheduleRequests()) {
            requestModel.addRow(new Object[]{
                request.getId(),
                request.getRequester(),
                request.getOldValue(),
                request.getRequestedValue(),
                request.getStatus().getLabel(),
                request.getReason()
            });
        }
        JTable requestTable = new JTable(requestModel);
        JButton approve = new JButton("Approve");
        JButton reject = new JButton("Reject");
        AppTheme.stylePrimaryButton(approve);
        AppTheme.styleDangerButton(reject);
        approve.addActionListener(event -> reviewSchedule(shell, store, user, requestTable, requestModel, true));
        reject.addActionListener(event -> reviewSchedule(shell, store, user, requestTable, requestModel, false));

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(shell.wrapTable(requestTable), BorderLayout.CENTER);
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actionRow.setOpaque(false);
        actionRow.add(approve);
        actionRow.add(reject);
        body.add(actionRow, BorderLayout.SOUTH);
        page.add(shell.createSection("Schedule Requests", "Review teacher schedule changes before they affect attendance.", body));
        page.add(Box.createVerticalStrut(16));

        DefaultTableModel removalModel = shell.createTableModel("ID", "Teacher", "Student", "Section", "Reason", "Status");
        for (AppDomain.StudentRemovalRequest request : store.getStudentRemovalRequests()) {
            removalModel.addRow(new Object[]{
                request.getId(),
                request.getTeacherName(),
                request.getStudentName(),
                request.getSectionName(),
                request.getReason(),
                request.getStatus().getLabel()
            });
        }
        JTable removalTable = new JTable(removalModel);
        JButton approveRemoval = new JButton("Approve");
        JButton rejectRemoval = new JButton("Reject");
        AppTheme.stylePrimaryButton(approveRemoval);
        AppTheme.styleDangerButton(rejectRemoval);
        approveRemoval.addActionListener(event -> reviewStudentRemoval(shell, store, user, removalTable, removalModel, true));
        rejectRemoval.addActionListener(event -> reviewStudentRemoval(shell, store, user, removalTable, removalModel, false));

        JPanel removalBody = new JPanel(new BorderLayout(0, 12));
        removalBody.setOpaque(false);
        removalBody.add(shell.wrapTable(removalTable), BorderLayout.CENTER);
        JPanel removalActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        removalActions.setOpaque(false);
        removalActions.add(approveRemoval);
        removalActions.add(rejectRemoval);
        removalBody.add(removalActions, BorderLayout.SOUTH);
        page.add(shell.createSection("Student Removal Requests", "Approve or reject teacher requests to remove students from their class list.", removalBody));
        return page;
    }

    private static void reviewSchedule(AppShell shell, AppDataStore store, ModelUser user,
            JTable requestTable, DefaultTableModel requestModel, boolean approve) {
        int row = requestTable.getSelectedRow();
        if (row < 0) {
            shell.showMessage("Choose a request first.", AppTheme.WARNING);
            shell.refreshView();
            return;
        }

        shell.showResult(store.reviewScheduleRequest(user.getUserId(), (Integer) requestModel.getValueAt(row, 0), approve, user.getFullName()));
        shell.refreshView();
    }

    private static void reviewStudentRemoval(AppShell shell, AppDataStore store, ModelUser user,
            JTable removalTable, DefaultTableModel removalModel, boolean approve) {
        int row = removalTable.getSelectedRow();
        if (row < 0) {
            shell.showMessage("Choose a student request first.", AppTheme.WARNING);
            shell.refreshView();
            return;
        }

        shell.showResult(store.reviewStudentRemovalRequest(user.getUserId(), (Integer) removalModel.getValueAt(row, 0), approve, user.getFullName()));
        shell.refreshView();
    }
}
