package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.AppDomain.EmailDispatch;
import ppb.qrattend.model.AppDomain.TeacherProfile;
import ppb.qrattend.model.ModelUser;

final class TeachersScreen {

    private TeachersScreen() {
    }

    static JPanel build(AppShell shell) {
        AppDataStore store = shell.getStore();
        ModelUser user = shell.getCurrentUser();
        JPanel page = AppTheme.createPage();

        JTextField nameField = shell.newTextField();
        JTextField emailField = shell.newTextField();
        JButton createTeacher = new JButton("Add Teacher");
        AppTheme.stylePrimaryButton(createTeacher);
        createTeacher.addActionListener(event -> {
            shell.showResult(store.addTeacher(user.getUserId(), nameField.getText(), emailField.getText()));
            shell.refreshView();
        });

        JPanel formBody = new JPanel(new GridLayout(1, 3, 12, 0));
        formBody.setOpaque(false);
        formBody.add(shell.labeledField("Teacher Name", nameField));
        formBody.add(shell.labeledField("Teacher Email", emailField));
        formBody.add(shell.labeledField("Action", createTeacher));
        page.add(shell.createSection("Add Teacher", "Enter the teacher's name and email. A temporary password will be sent by email.", formBody));
        page.add(Box.createVerticalStrut(16));

        DefaultTableModel teacherModel = shell.createTableModel("ID", "Teacher", "Email", "Status", "Email");
        for (TeacherProfile teacher : store.getTeachers()) {
            teacherModel.addRow(new Object[]{
                teacher.getId(),
                teacher.getFullName(),
                teacher.getEmail(),
                shell.friendlyStatus(teacher.getStatus()),
                teacher.getEmailStatus().getLabel()
            });
        }
        JTable teacherTable = new JTable(teacherModel);
        JButton resend = new JButton("Send Password Again");
        JButton reset = new JButton("Reset Password");
        AppTheme.styleSecondaryButton(resend);
        AppTheme.styleDangerButton(reset);
        resend.addActionListener(event -> handlePasswordAction(shell, store, user, teacherTable, teacherModel, true));
        reset.addActionListener(event -> handlePasswordAction(shell, store, user, teacherTable, teacherModel, false));

        JPanel teacherBody = new JPanel(new BorderLayout(0, 12));
        teacherBody.setOpaque(false);
        teacherBody.add(shell.wrapTable(teacherTable), BorderLayout.CENTER);
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actionRow.setOpaque(false);
        actionRow.add(resend);
        actionRow.add(reset);
        teacherBody.add(actionRow, BorderLayout.SOUTH);
        page.add(shell.createSection("Teachers", "See teacher accounts and password email status.", teacherBody));
        page.add(Box.createVerticalStrut(16));

        DefaultTableModel emailModel = shell.createTableModel("Recipient", "Subject", "Status", "Preview");
        for (EmailDispatch dispatch : store.getRecentEmailDispatches(8)) {
            emailModel.addRow(new Object[]{
                dispatch.getRecipient(),
                dispatch.getSubject(),
                dispatch.getStatus().getLabel(),
                dispatch.getPreview()
            });
        }
        JTable emailTable = new JTable(emailModel);
        page.add(shell.createSection("Recent Emails", "Latest password and QR emails.", shell.wrapTable(emailTable)));
        return page;
    }

    private static void handlePasswordAction(AppShell shell, AppDataStore store, ModelUser user,
            JTable teacherTable, DefaultTableModel teacherModel, boolean resend) {
        int row = teacherTable.getSelectedRow();
        if (row < 0) {
            shell.showMessage("Choose a teacher first.", AppTheme.WARNING);
            shell.refreshView();
            return;
        }

        int teacherId = (Integer) teacherModel.getValueAt(row, 0);
        AppDataStore.ActionResult result = resend
                ? store.resendTeacherPassword(user.getUserId(), teacherId)
                : store.resetTeacherPassword(user.getUserId(), teacherId);
        shell.showResult(result);
        shell.refreshView();
    }
}
