package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.AppDomain.StudentProfile;
import ppb.qrattend.model.AppDomain.TeacherProfile;
import ppb.qrattend.model.ModelUser;

final class AdminStudentsScreen {

    private AdminStudentsScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        page.add(buildAddStudentSection(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildStudentListSection(shell));
        return page;
    }

    private static JPanel buildAddStudentSection(AppShell shell) {
        ModelUser user = shell.getCurrentUser();
        List<TeacherProfile> teachers = shell.getStore().getTeachers();

        JComboBox<String> teacherCombo = new JComboBox<>(teachers.stream()
                .map(teacher -> teacher.getFullName())
                .toArray(String[]::new));
        AppTheme.styleCombo(teacherCombo);

        JTextField sectionField = shell.newTextField();
        sectionField.setText("BSIT-2A");
        JTextField idField = shell.newTextField();
        JTextField nameField = shell.newTextField();
        JTextField emailField = shell.newTextField();

        JButton addButton = new JButton("Add Student");
        AppTheme.stylePrimaryButton(addButton);
        addButton.addActionListener(event -> {
            int selectedIndex = teacherCombo.getSelectedIndex();
            if (selectedIndex < 0 || selectedIndex >= teachers.size()) {
                shell.showMessage("Choose a teacher first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }

            TeacherProfile teacher = teachers.get(selectedIndex);
            shell.showResult(shell.getStore().addStudent(
                    user.getUserId(),
                    teacher.getId(),
                    sectionField.getText(),
                    idField.getText(),
                    nameField.getText(),
                    emailField.getText()
            ));
            shell.refreshView();
        });

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(AppFlowPanels.createSimpleList("Start here", java.util.List.of(
                "Pick the teacher first.",
                "Type the section, student ID, full name, and email.",
                "The student QR code will be sent by email."
        )), BorderLayout.NORTH);

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        form.setOpaque(false);
        form.add(shell.labeledField("Teacher", teacherCombo));
        form.add(shell.labeledField("Section", sectionField));
        form.add(shell.labeledField("Student ID", idField));
        form.add(shell.labeledField("Full name", nameField));
        form.add(shell.labeledField("Email", emailField));
        form.add(shell.labeledField("Action", addButton));
        body.add(form, BorderLayout.SOUTH);

        return shell.createSection("Add student", "Add one student at a time.", body);
    }

    private static JPanel buildStudentListSection(AppShell shell) {
        DefaultTableModel model = shell.createTableModel("Student ID", "Full Name", "Section", "Teacher ID", "Email", "QR Email");
        for (StudentProfile student : shell.getStore().getAllStudents()) {
            model.addRow(new Object[]{
                student.getStudentId(),
                student.getFullName(),
                student.getSectionName(),
                student.getTeacherId(),
                student.getEmail(),
                student.getQrStatus().getLabel()
            });
        }

        JTable table = new JTable(model);
        JButton resendButton = new JButton("Send Again");
        AppTheme.styleSecondaryButton(resendButton);
        resendButton.addActionListener(event -> handleResend(shell, table, model));

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(shell.wrapTable(table), BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(resendButton);
        body.add(actions, BorderLayout.SOUTH);

        return shell.createSection("Student list", "Choose a student if you need to send the QR code again.", body);
    }

    private static void handleResend(AppShell shell, JTable table, DefaultTableModel model) {
        int row = table.getSelectedRow();
        if (row < 0) {
            shell.showMessage("Choose a student first.", AppTheme.WARNING);
            shell.refreshView();
            return;
        }

        shell.showResult(shell.getStore().resendStudentQr(
                (Integer) model.getValueAt(row, 3),
                (String) model.getValueAt(row, 0)
        ));
        shell.refreshView();
    }
}
