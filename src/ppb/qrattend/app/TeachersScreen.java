package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.CoreModels.Teacher;

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
        JTextField nameField = shell.newTextField();
        JTextField emailField = shell.newTextField();

        JButton addButton = new JButton("Add Teacher");
        AppTheme.stylePrimaryButton(addButton);
        addButton.addActionListener(event -> {
            shell.showResult(shell.getStore().addTeacher(
                    shell.getCurrentUser().getUserId(),
                    nameField.getText(),
                    emailField.getText()
            ));
            shell.refreshView();
        });

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        form.setOpaque(false);
        form.add(shell.labeledField("Teacher name", nameField));
        form.add(shell.labeledField("Teacher email", emailField));
        form.add(shell.labeledField("Action", addButton));

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(AppFlowPanels.createSimpleList("What to do here", List.of(
                "Add the teacher name and school email.",
                "The teacher password is sent by email for you.",
                "Use Send Again if the teacher needs a fresh password email."
        )), BorderLayout.NORTH);
        body.add(form, BorderLayout.SOUTH);

        return shell.createSection("Add Teacher", "Create one teacher account at a time.", body);
    }

    private static JPanel buildTeacherListSection(AppShell shell) {
        List<Teacher> teachers = shell.getStore().getTeachers();
        DefaultTableModel model = shell.createTableModel("Teacher", "Email", "Account", "Email");
        for (Teacher teacher : teachers) {
            model.addRow(new Object[]{
                teacher.fullName(),
                teacher.email(),
                teacher.active() ? "Active" : "Hidden",
                teacher.emailStatus().getLabel()
            });
        }

        JTable table = new JTable(model);

        // Inline edit form fields (initially empty/hidden via label visibility)
        JTextField editNameField = shell.newTextField();
        JTextField editEmailField = shell.newTextField();
        JLabel editFormLabel = new JLabel("Edit teacher:");
        editFormLabel.setFont(AppTheme.bodyFont(12));
        editFormLabel.setForeground(AppTheme.TEXT_MUTED);

        JButton saveButton = new JButton("Save Changes");
        AppTheme.stylePrimaryButton(saveButton);

        JPanel editForm = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        editForm.setOpaque(false);
        editForm.add(editFormLabel);
        editForm.add(shell.labeledField("Name", editNameField));
        editForm.add(shell.labeledField("Email", editEmailField));
        editForm.add(shell.labeledField("Action", saveButton));
        editForm.setVisible(false);

        // Action buttons
        JButton resendButton = new JButton("Send Again");
        JButton resetButton = new JButton("Reset Password");
        JButton editButton = new JButton("Edit");
        JButton deactivateButton = new JButton("Deactivate");
        AppTheme.styleSecondaryButton(resendButton);
        AppTheme.styleDangerButton(resetButton);
        AppTheme.styleSecondaryButton(editButton);
        AppTheme.styleDangerButton(deactivateButton);

        resendButton.addActionListener(event -> handlePasswordAction(shell, table, teachers, true));
        resetButton.addActionListener(event -> handlePasswordAction(shell, table, teachers, false));

        editButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0 || row >= teachers.size()) {
                shell.showMessage("Choose a teacher first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }
            Teacher teacher = teachers.get(row);
            editNameField.setText(teacher.fullName());
            editEmailField.setText(teacher.email());
            editForm.setVisible(true);
            editForm.revalidate();
            editForm.repaint();
        });

        saveButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0 || row >= teachers.size()) {
                shell.showMessage("Choose a teacher first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }
            Teacher teacher = teachers.get(row);
            shell.showResult(shell.getStore().updateTeacher(
                    teacher.id(),
                    editNameField.getText(),
                    editEmailField.getText()
            ));
            shell.refreshView();
        });

        deactivateButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0 || row >= teachers.size()) {
                shell.showMessage("Choose a teacher first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }
            Teacher teacher = teachers.get(row);
            shell.showResult(shell.getStore().deactivateTeacher(teacher.id()));
            shell.refreshView();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(resendButton);
        actions.add(resetButton);
        actions.add(editButton);
        actions.add(deactivateButton);

        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        south.add(actions, BorderLayout.NORTH);
        south.add(editForm, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(shell.wrapTable(table), BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        return shell.createSection("Teacher List", "Choose one teacher if you need to send a new password.", body);
    }

    private static void handlePasswordAction(AppShell shell, JTable table, List<Teacher> teachers, boolean resend) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= teachers.size()) {
            shell.showMessage("Choose a teacher first.", AppTheme.WARNING);
            shell.refreshView();
            return;
        }

        Teacher teacher = teachers.get(row);
        AppStore.ActionResult result = resend
                ? shell.getStore().resendTeacherPassword(shell.getCurrentUser().getUserId(), teacher.id())
                : shell.getStore().resetTeacherPassword(shell.getCurrentUser().getUserId(), teacher.id());
        shell.showResult(result);
        shell.refreshView();
    }
}
