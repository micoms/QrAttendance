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

final class TeacherRosterScreen {

    private TeacherRosterScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        page.add(buildClassListSection(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildRemovalRequestSection(shell));
        return page;
    }

    private static JPanel buildClassListSection(AppShell shell) {
        DefaultTableModel studentModel = shell.createTableModel("Student ID", "Full Name", "Section", "Email", "QR Email");
        for (StudentProfile student : shell.getStore().getStudentsForTeacher(shell.getCurrentUser().getUserId())) {
            studentModel.addRow(new Object[]{
                student.getStudentId(),
                student.getFullName(),
                student.getSectionName(),
                student.getEmail(),
                student.getQrStatus().getLabel()
            });
        }

        JTable table = new JTable(studentModel);
        return shell.createSection("My class list", "These are the students currently in your class list.", shell.wrapTable(table));
    }

    private static JPanel buildRemovalRequestSection(AppShell shell) {
        JTextArea reasonArea = shell.newTextArea();
        reasonArea.setText("This student should be removed from my class list.");

        DefaultTableModel studentModel = shell.createTableModel("Student ID", "Student", "Section");
        for (StudentProfile student : shell.getStore().getStudentsForTeacher(shell.getCurrentUser().getUserId())) {
            studentModel.addRow(new Object[]{
                student.getStudentId(),
                student.getFullName(),
                student.getSectionName()
            });
        }
        JTable table = new JTable(studentModel);

        JButton requestButton = new JButton("Ask Admin");
        AppTheme.styleDangerButton(requestButton);
        requestButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                shell.showMessage("Choose a student first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }

            shell.showResult(shell.getStore().requestStudentRemoval(
                    shell.getCurrentUser().getUserId(),
                    (String) studentModel.getValueAt(row, 0),
                    reasonArea.getText()
            ));
            shell.refreshView();
        });

        JPanel requestBody = new JPanel(new BorderLayout(0, 12));
        requestBody.setOpaque(false);
        requestBody.add(AppFlowPanels.createSimpleList("Need a student removed?", java.util.List.of(
                "Choose the student from the list.",
                "Write a short reason.",
                "The admin must approve it first."
        )), BorderLayout.NORTH);
        requestBody.add(shell.wrapTable(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new GridLayout(1, 2, 12, 0));
        bottom.setOpaque(false);
        bottom.add(shell.labeledField("Reason", new javax.swing.JScrollPane(reasonArea)));
        bottom.add(shell.labeledField("Action", requestButton));
        requestBody.add(bottom, BorderLayout.SOUTH);

        JPanel section = AppTheme.createPage();
        section.add(shell.createSection("Ask admin to remove a student", "Use this when a student should no longer be in your class list.", requestBody));
        section.add(Box.createVerticalStrut(16));

        DefaultTableModel requestModel = shell.createTableModel("ID", "Student", "Section", "Reason", "Status", "Reviewed By");
        for (AppDomain.StudentRemovalRequest request : shell.getStore().getStudentRemovalRequestsForTeacher(shell.getCurrentUser().getUserId())) {
            requestModel.addRow(new Object[]{
                request.getId(),
                request.getStudentName(),
                request.getSectionName(),
                request.getReason(),
                request.getStatus().getLabel(),
                request.getReviewedBy() == null || request.getReviewedBy().isBlank() ? "-" : request.getReviewedBy()
            });
        }

        section.add(shell.createSection("Your requests", "This shows if your request is still waiting, approved, or rejected.",
                shell.wrapTable(new JTable(requestModel))));
        return section;
    }
}
