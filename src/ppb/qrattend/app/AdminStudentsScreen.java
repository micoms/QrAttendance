package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
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
        AppDataStore store = shell.getStore();
        ModelUser user = shell.getCurrentUser();
        JPanel page = AppTheme.createPage();

        List<TeacherProfile> teachers = store.getTeachers();
        JComboBox<String> teacherCombo = new JComboBox<>(teachers.stream()
                .map(teacher -> teacher.getId() + " - " + teacher.getFullName())
                .toArray(String[]::new));
        AppTheme.styleCombo(teacherCombo);

        JTextField sectionField = shell.newTextField();
        sectionField.setText("BSIT-2A");
        JTextField idField = shell.newTextField();
        JTextField nameField = shell.newTextField();
        JTextField emailField = shell.newTextField();
        JButton add = new JButton("Add Student");
        AppTheme.stylePrimaryButton(add);
        add.addActionListener(event -> {
            int selectedIndex = teacherCombo.getSelectedIndex();
            if (selectedIndex < 0 || selectedIndex >= teachers.size()) {
                shell.showMessage("Choose a teacher first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }

            TeacherProfile teacher = teachers.get(selectedIndex);
            shell.showResult(store.addStudent(
                    user.getUserId(),
                    teacher.getId(),
                    sectionField.getText(),
                    idField.getText(),
                    nameField.getText(),
                    emailField.getText()
            ));
            shell.refreshView();
        });

        JPanel formGrid = new JPanel(new GridLayout(2, 3, 12, 12));
        formGrid.setOpaque(false);
        formGrid.add(shell.labeledField("Assigned Teacher", teacherCombo));
        formGrid.add(shell.labeledField("Section", sectionField));
        formGrid.add(shell.labeledField("Student ID", idField));
        formGrid.add(shell.labeledField("Full Name", nameField));
        formGrid.add(shell.labeledField("Email", emailField));
        formGrid.add(shell.labeledField("Action", add));
        page.add(shell.createSection("Add Student", "Add a student, choose the section, choose the teacher, and send the QR code.", formGrid));
        page.add(Box.createVerticalStrut(16));

        DefaultTableModel studentModel = shell.createTableModel("Student ID", "Full Name", "Section", "Teacher ID", "Email", "QR Email");
        for (StudentProfile student : store.getAllStudents()) {
            studentModel.addRow(new Object[]{
                student.getStudentId(),
                student.getFullName(),
                student.getSectionName(),
                student.getTeacherId(),
                student.getEmail(),
                student.getQrStatus().getLabel()
            });
        }
        JTable studentTable = new JTable(studentModel);
        JButton resend = new JButton("Send QR Again");
        AppTheme.styleSecondaryButton(resend);
        resend.addActionListener(event -> {
            int row = studentTable.getSelectedRow();
            if (row < 0) {
                shell.showMessage("Choose a student first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }

            shell.showResult(store.resendStudentQr((Integer) studentModel.getValueAt(row, 3), (String) studentModel.getValueAt(row, 0)));
            shell.refreshView();
        });

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(shell.wrapTable(studentTable), BorderLayout.CENTER);
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actionRow.setOpaque(false);
        actionRow.add(resend);
        body.add(actionRow, BorderLayout.SOUTH);
        page.add(shell.createSection("Student List", "See students by section and teacher.", body));
        return page;
    }
}
