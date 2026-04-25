package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.CoreModels.Section;
import ppb.qrattend.model.CoreModels.Student;

final class AdminStudentsScreen {

    private AdminStudentsScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        if (shell.getStore().getSections().isEmpty()) {
            page.add(AppFlowPanels.createSimpleList("Start here", List.of(
                    "Create a section first.",
                    "After that, come back here to import or add students.",
                    "Students are saved under sections, not under teachers."
            )));
            return page;
        }

        page.add(buildImportSection(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildManualAddSection(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildStudentListSection(shell));
        return page;
    }

    private static JPanel buildImportSection(AppShell shell) {
        List<Section> sections = shell.getStore().getSections();
        JComboBox<String> sectionCombo = createSectionCombo(sections);
        JTextArea inputArea = shell.newTextArea();
        inputArea.setText("2026-0001, Juan Dela Cruz, juan@example.com");

        JButton importButton = new JButton("Import Students");
        AppTheme.stylePrimaryButton(importButton);
        importButton.addActionListener(event -> {
            Section section = sectionAt(sections, sectionCombo.getSelectedIndex());
            if (section == null) {
                shell.showMessage("Choose a section first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }
            shell.showResult(shell.getStore().importStudents(section.id(), inputArea.getText()));
            shell.refreshView();
        });

        JPanel form = new JPanel(new BorderLayout(0, 12));
        form.setOpaque(false);
        form.add(shell.labeledField("Section", sectionCombo), BorderLayout.NORTH);
        form.add(new JScrollPane(inputArea), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        bottom.setOpaque(false);
        bottom.add(importButton);
        form.add(bottom, BorderLayout.SOUTH);

        return shell.createSection("Add Students", "Paste one student per line: student ID, full name, email.", form);
    }

    private static JPanel buildManualAddSection(AppShell shell) {
        List<Section> sections = shell.getStore().getSections();
        JComboBox<String> sectionCombo = createSectionCombo(sections);
        JTextField codeField = shell.newTextField();
        JTextField nameField = shell.newTextField();
        JTextField emailField = shell.newTextField();
        setWidth(sectionCombo, 120);
        setWidth(codeField, 150);
        setWidth(nameField, 230);
        setWidth(emailField, 230);

        JButton addButton = new JButton("Add Student");
        AppTheme.styleSecondaryButton(addButton);
        addButton.setPreferredSize(new Dimension(140, 42));
        addButton.addActionListener(event -> {
            Section section = sectionAt(sections, sectionCombo.getSelectedIndex());
            if (section == null) {
                shell.showMessage("Choose a section first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }
            shell.showResult(shell.getStore().addStudent(
                    section.id(),
                    codeField.getText(),
                    nameField.getText(),
                    emailField.getText()
            ));
            shell.refreshView();
        });

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        form.setOpaque(false);
        form.add(shell.labeledField("Section", sectionCombo));
        form.add(shell.labeledField("Student ID", codeField));
        form.add(shell.labeledField("Full name", nameField));
        form.add(shell.labeledField("Email", emailField));
        form.add(shell.labeledField("Action", addButton));

        return shell.createSection("Add Student", "Use this when you only need to add one student.", form);
    }

    private static JPanel buildStudentListSection(AppShell shell) {
        List<Section> sections = shell.getStore().getSections();
        List<Student> students = new ArrayList<>(shell.getStore().getAllStudents());
        DefaultTableModel model = shell.createTableModel("Student ID", "Full Name", "Section", "Email", "QR Email");
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

        // Inline edit form fields (initially hidden)
        JTextField editCodeField = shell.newTextField();
        JTextField editNameField = shell.newTextField();
        JTextField editEmailField = shell.newTextField();
        JComboBox<String> editSectionCombo = createSectionCombo(sections);
        JLabel editFormLabel = new JLabel("Edit student:");
        editFormLabel.setFont(AppTheme.bodyFont(12));
        editFormLabel.setForeground(AppTheme.TEXT_MUTED);

        JButton saveButton = new JButton("Save Changes");
        AppTheme.stylePrimaryButton(saveButton);

        JPanel editForm = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        editForm.setOpaque(false);
        editForm.add(editFormLabel);
        editForm.add(shell.labeledField("Student ID", editCodeField));
        editForm.add(shell.labeledField("Full name", editNameField));
        editForm.add(shell.labeledField("Email", editEmailField));
        editForm.add(shell.labeledField("Section", editSectionCombo));
        editForm.add(shell.labeledField("Action", saveButton));
        editForm.setVisible(false);

        // Action buttons
        JButton resendButton = new JButton("Send Again");
        JButton editButton = new JButton("Edit");
        JButton deactivateButton = new JButton("Deactivate");
        AppTheme.styleSecondaryButton(resendButton);
        AppTheme.styleSecondaryButton(editButton);
        AppTheme.styleDangerButton(deactivateButton);

        resendButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0 || row >= students.size()) {
                shell.showMessage("Choose a student first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }
            shell.showResult(shell.getStore().resendStudentQr(students.get(row).id()));
            shell.refreshView();
        });

        editButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0 || row >= students.size()) {
                shell.showMessage("Choose a student first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }
            Student student = students.get(row);
            editCodeField.setText(student.studentCode());
            editNameField.setText(student.fullName());
            editEmailField.setText(student.email());
            // Select the matching section in the combo
            int sectionIndex = 0;
            for (int i = 0; i < sections.size(); i++) {
                if (sections.get(i).id() == student.sectionId()) {
                    sectionIndex = i;
                    break;
                }
            }
            editSectionCombo.setSelectedIndex(sectionIndex);
            editForm.setVisible(true);
            editForm.revalidate();
            editForm.repaint();
        });

        saveButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0 || row >= students.size()) {
                shell.showMessage("Choose a student first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }
            Student student = students.get(row);
            Section section = sectionAt(sections, editSectionCombo.getSelectedIndex());
            if (section == null) {
                shell.showMessage("Choose a section first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }
            shell.showResult(shell.getStore().updateStudent(
                    student.id(),
                    section.id(),
                    editCodeField.getText(),
                    editNameField.getText(),
                    editEmailField.getText()
            ));
            shell.refreshView();
        });

        deactivateButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0 || row >= students.size()) {
                shell.showMessage("Choose a student first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }
            Student student = students.get(row);
            shell.showResult(shell.getStore().deactivateStudent(student.id()));
            shell.refreshView();
        });

        JPanel info = AppFlowPanels.createSimpleList("Quick notes", List.of(
                "Students are grouped by section.",
                "Teachers see students from the sections in their saved schedule.",
                "Use Send Again if a student needs a new QR email."
        ));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        actions.setOpaque(false);
        actions.add(resendButton);
        actions.add(editButton);
        actions.add(deactivateButton);

        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        south.add(actions, BorderLayout.NORTH);
        south.add(editForm, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(info, BorderLayout.NORTH);
        body.add(shell.wrapTable(table), BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        return shell.createSection("Student List", "Choose a student from the list to send QR, edit, or deactivate.", body);
    }

    private static JComboBox<String> createSectionCombo(List<Section> sections) {
        String[] names = new String[sections.size()];
        for (int i = 0; i < sections.size(); i++) {
            names[i] = sections.get(i).name();
        }
        JComboBox<String> combo = new JComboBox<>(names);
        AppTheme.styleCombo(combo);
        return combo;
    }

    private static Section sectionAt(List<Section> sections, int index) {
        if (index < 0 || index >= sections.size()) {
            return null;
        }
        return sections.get(index);
    }

    private static void setWidth(javax.swing.JComponent component, int width) {
        Dimension size = component.getPreferredSize();
        component.setPreferredSize(new Dimension(width, size.height));
    }
}
