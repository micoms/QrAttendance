package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.AppDomain.TeacherProfile;
import ppb.qrattend.model.ModelUser;

final class TeachersScreen {

    private TeachersScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        page.add(buildAddTeacherSection(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildTeacherListSection(shell));
        return page;
    }

    private static JPanel buildAddTeacherSection(AppShell shell) {
        ModelUser user = shell.getCurrentUser();
        JTextField nameField = shell.newTextField();
        JTextField emailField = shell.newTextField();

        JButton createButton = new JButton("Add Teacher");
        AppTheme.stylePrimaryButton(createButton);
        createButton.addActionListener(event -> {
            shell.showResult(shell.getStore().addTeacher(user.getUserId(), nameField.getText(), emailField.getText()));
            shell.refreshView();
        });

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(AppFlowPanels.createSimpleList("Start here", java.util.List.of(
                "Enter the teacher name.",
                "Enter the teacher school email.",
                "The password will be sent by email."
        )), BorderLayout.NORTH);

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        form.setOpaque(false);
        form.add(shell.labeledField("Teacher name", nameField));
        form.add(shell.labeledField("Teacher email", emailField));
        form.add(shell.labeledField("Action", createButton));
        body.add(form, BorderLayout.SOUTH);

        return shell.createSection("Add teacher", "Create one teacher account at a time.", body);
    }

    private static JPanel buildTeacherListSection(AppShell shell) {
        DefaultTableModel model = shell.createTableModel("ID", "Teacher", "Email", "Status", "Email");
        for (TeacherProfile teacher : shell.getStore().getTeachers()) {
            model.addRow(new Object[]{
                teacher.getId(),
                teacher.getFullName(),
                teacher.getEmail(),
                shell.friendlyStatus(teacher.getStatus()),
                teacher.getEmailStatus().getLabel()
            });
        }

        JTable table = new JTable(model);
        JButton resendButton = new JButton("Send Again");
        JButton resetButton = new JButton("Reset Password");
        AppTheme.styleSecondaryButton(resendButton);
        AppTheme.styleDangerButton(resetButton);

        resendButton.addActionListener(event -> handlePasswordAction(shell, table, model, true));
        resetButton.addActionListener(event -> handlePasswordAction(shell, table, model, false));

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(shell.wrapTable(table), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(resendButton);
        actions.add(resetButton);
        body.add(actions, BorderLayout.SOUTH);

        return shell.createSection("Teacher list", "Choose one teacher if you need to send the password again.", body);
    }

    private static void handlePasswordAction(AppShell shell, JTable table, DefaultTableModel model, boolean resend) {
        int row = table.getSelectedRow();
        if (row < 0) {
            shell.showMessage("Choose a teacher first.", AppTheme.WARNING);
            shell.refreshView();
            return;
        }

        int teacherId = (Integer) model.getValueAt(row, 0);
        AppDataStore.ActionResult result = resend
                ? shell.getStore().resendTeacherPassword(shell.getCurrentUser().getUserId(), teacherId)
                : shell.getStore().resetTeacherPassword(shell.getCurrentUser().getUserId(), teacherId);
        shell.showResult(result);
        shell.refreshView();
    }
}
