package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.DayOfWeek;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.AppDomain.ScheduleSlot;
import ppb.qrattend.model.AppDomain.TeacherProfile;
import ppb.qrattend.model.ModelUser;

final class AdminSchedulesScreen {

    private AdminSchedulesScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        page.add(buildAddScheduleSection(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildSavedScheduleSection(shell));
        return page;
    }

    private static JPanel buildAddScheduleSection(AppShell shell) {
        ModelUser user = shell.getCurrentUser();
        List<TeacherProfile> teachers = shell.getStore().getTeachers();
        JComboBox<String> teacherCombo = new JComboBox<>(teachers.stream()
                .map(TeacherProfile::getFullName)
                .toArray(String[]::new));
        AppTheme.styleCombo(teacherCombo);

        JTextField subjectField = shell.newTextField();
        JTextField roomField = shell.newTextField();
        JComboBox<DayOfWeek> dayCombo = shell.newDayCombo();
        JComboBox<String> startCombo = shell.newTimeCombo();
        JComboBox<String> endCombo = shell.newTimeCombo();

        JButton saveButton = new JButton("Save Class");
        AppTheme.stylePrimaryButton(saveButton);
        saveButton.addActionListener(event -> {
            if (teachers.isEmpty()) {
                shell.showMessage("Add a teacher first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }

            TeacherProfile teacher = teachers.get(teacherCombo.getSelectedIndex());
            shell.showResult(shell.getStore().addScheduleSlot(
                    user.getUserId(),
                    teacher.getId(),
                    subjectField.getText(),
                    (DayOfWeek) dayCombo.getSelectedItem(),
                    shell.parseTimeValue((String) startCombo.getSelectedItem()),
                    shell.parseTimeValue((String) endCombo.getSelectedItem()),
                    roomField.getText()
            ));
            shell.refreshView();
        });

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(AppFlowPanels.createSimpleList("Start here", java.util.List.of(
                "Pick the teacher.",
                "Add the subject, room, day, and time.",
                "Save the class only after checking the time carefully."
        )), BorderLayout.NORTH);

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        form.setOpaque(false);
        form.add(shell.labeledField("Teacher", teacherCombo));
        form.add(shell.labeledField("Subject", subjectField));
        form.add(shell.labeledField("Room", roomField));
        form.add(shell.labeledField("Day", dayCombo));
        form.add(shell.labeledField("Start", startCombo));
        form.add(shell.labeledField("End", endCombo));
        form.add(shell.labeledField("Action", saveButton));
        body.add(form, BorderLayout.SOUTH);

        return shell.createSection("Set class schedule", "Add one class at a time.", body);
    }

    private static JPanel buildSavedScheduleSection(AppShell shell) {
        DefaultTableModel model = shell.createTableModel("ID", "Teacher", "Subject", "Day", "Time", "Room", "Status");
        for (ScheduleSlot slot : shell.getStore().getSchedules()) {
            TeacherProfile teacher = shell.getStore().findTeacher(slot.getTeacherId());
            model.addRow(new Object[]{
                slot.getId(),
                teacher == null ? "-" : teacher.getFullName(),
                slot.getSubjectName(),
                slot.getDay(),
                slot.getTimeLabel(),
                slot.getRoom(),
                shell.friendlyStatus(slot.getStatus())
            });
        }

        JTable table = new JTable(model);
        return shell.createSection("Saved classes", "These are the classes already saved.", shell.wrapTable(table));
    }
}
