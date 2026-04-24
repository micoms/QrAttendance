package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.AppDomain;
import ppb.qrattend.model.AppDomain.StudentProfile;
import ppb.qrattend.model.ModelUser;

final class TeacherRosterScreen {

    private TeacherRosterScreen() {
    }

    static JPanel build(AppShell shell) {
        AppDataStore store = shell.getStore();
        ModelUser user = shell.getCurrentUser();
        JPanel page = AppTheme.createPage();

        JTextArea reasonArea = shell.newTextArea();
        reasonArea.setText("Student transferred or should be removed from my section list.");

        DefaultTableModel studentModel = shell.createTableModel("Student ID", "Full Name", "Section", "Email", "QR Email");
        for (StudentProfile student : store.getStudentsForTeacher(user.getUserId())) {
            studentModel.addRow(new Object[]{
                student.getStudentId(),
                student.getFullName(),
                student.getSectionName(),
                student.getEmail(),
                student.getQrStatus().getLabel()
            });
        }
        JTable studentTable = new JTable(studentModel);

        JButton requestRemoval = new JButton("Request Removal");
        AppTheme.styleDangerButton(requestRemoval);
        requestRemoval.addActionListener(event -> {
            int row = studentTable.getSelectedRow();
            if (row < 0) {
                shell.showMessage("Choose a student first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }

            shell.showResult(store.requestStudentRemoval(user.getUserId(), (String) studentModel.getValueAt(row, 0), reasonArea.getText()));
            shell.refreshView();
        });

        JPanel rosterBody = new JPanel(new BorderLayout(0, 12));
        rosterBody.setOpaque(false);
        rosterBody.add(shell.wrapTable(studentTable), BorderLayout.CENTER);
        JPanel removalRow = new JPanel(new GridLayout(1, 2, 12, 0));
        removalRow.setOpaque(false);
        removalRow.add(shell.labeledField("Reason", new javax.swing.JScrollPane(reasonArea)));
        removalRow.add(shell.labeledField("Need Approval", requestRemoval));
        rosterBody.add(removalRow, BorderLayout.SOUTH);
        page.add(shell.createSection("My Class List", "Check your students and ask the admin to remove a student if needed.", rosterBody));
        page.add(Box.createVerticalStrut(16));

        DefaultTableModel requestModel = shell.createTableModel("ID", "Student", "Section", "Reason", "Status", "Reviewed By");
        for (AppDomain.StudentRemovalRequest request : store.getStudentRemovalRequestsForTeacher(user.getUserId())) {
            requestModel.addRow(new Object[]{
                request.getId(),
                request.getStudentName(),
                request.getSectionName(),
                request.getReason(),
                request.getStatus().getLabel(),
                request.getReviewedBy() == null || request.getReviewedBy().isBlank() ? "-" : request.getReviewedBy()
            });
        }
        JTable requestTable = new JTable(requestModel);
        page.add(shell.createSection("Student Removal Requests", "Check whether your requests are still waiting, approved, or rejected.", shell.wrapTable(requestTable)));
        return page;
    }
}
