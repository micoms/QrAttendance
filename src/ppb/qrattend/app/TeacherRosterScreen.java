package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.CoreModels.RequestStatus;
import ppb.qrattend.model.CoreModels.Student;
import ppb.qrattend.model.CoreModels.StudentRemovalRequest;

final class TeacherRosterScreen {

    private TeacherRosterScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        page.add(buildStudentListSection(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildRequestHistory(shell));
        return page;
    }

    private static JPanel buildStudentListSection(AppShell shell) {
        List<Student> students = shell.getStore().getStudentsForTeacher(shell.getCurrentUser().getUserId());
        DefaultTableModel model = shell.createTableModel("Student ID", "Full Name", "Section", "Email", "QR");
        for (Student student : students) {
            model.addRow(new Object[]{
                student.studentCode(),
                student.fullName(),
                student.sectionName(),
                student.email(),
                student.qrStatus().getLabel()
            });
        }

        JTable table = new JTable(model);
        JTextField reasonField = shell.newTextField();
        reasonField.setText("The student is no longer in this class.");

        JButton requestButton = new JButton("Ask Admin");
        AppTheme.stylePrimaryButton(requestButton);
        requestButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0 || row >= students.size()) {
                shell.showMessage("Choose a student first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }
            shell.showResult(shell.getStore().submitStudentRemovalRequest(
                    shell.getCurrentUser().getUserId(),
                    students.get(row).id(),
                    reasonField.getText()
            ));
            shell.refreshView();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        actions.setOpaque(false);
        actions.add(shell.labeledField("Why does this student need to be removed?", reasonField));
        actions.add(requestButton);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(AppFlowPanels.createSimpleList("What this page shows", List.of(
                "Students appear here because their sections are in your saved schedule.",
                "You cannot remove a student by yourself.",
                "Use Ask Admin to send a removal request."
        )), BorderLayout.NORTH);
        body.add(shell.wrapTable(table), BorderLayout.CENTER);
        body.add(actions, BorderLayout.SOUTH);
        return shell.createSection("My Class List", "Choose a student if you need the admin to remove them from your class list.", body);
    }

    private static JPanel buildRequestHistory(AppShell shell) {
        DefaultTableModel model = shell.createTableModel("Student", "Section", "Reason", "Status", "Reviewed By");
        for (StudentRemovalRequest request : shell.getStore().getStudentRemovalRequestsForTeacher(shell.getCurrentUser().getUserId())) {
            model.addRow(new Object[]{
                request.studentName(),
                request.sectionName(),
                request.reason(),
                request.status().getLabel(),
                request.reviewedBy().isBlank() ? "-" : request.reviewedBy()
            });
        }

        JTable table = new JTable(model);
        int pending = 0;
        for (StudentRemovalRequest request : shell.getStore().getStudentRemovalRequestsForTeacher(shell.getCurrentUser().getUserId())) {
            if (request.status() == RequestStatus.PENDING) {
                pending++;
            }
        }

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(AppFlowPanels.createSimpleList("Student Removal Requests", List.of(
                "Pending requests: " + pending,
                "Approved requests remove the student from the active class list.",
                "Rejected requests stay in history so you can check what happened."
        )), BorderLayout.NORTH);
        body.add(shell.wrapTable(table), BorderLayout.CENTER);
        return shell.createSection("Request History", "These are the student removal requests you already sent.", body);
    }
}
