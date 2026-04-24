package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.DayOfWeek;
import java.util.ArrayList;
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
        AppDataStore store = shell.getStore();
        ModelUser user = shell.getCurrentUser();
        JPanel page = AppTheme.createPage();

        List<TeacherProfile> teachers = store.getTeachers();
        List<String> teacherLabels = new ArrayList<>();
        for (TeacherProfile teacher : teachers) {
            teacherLabels.add(teacher.getFullName() + " (" + teacher.getEmail() + ")");
        }

        JComboBox<String> teacherCombo = new JComboBox<>(teacherLabels.toArray(String[]::new));
        AppTheme.styleCombo(teacherCombo);
        JTextField subjectField = shell.newTextField();
        JTextField roomField = shell.newTextField();
        JComboBox<DayOfWeek> dayCombo = shell.newDayCombo();
        JComboBox<String> startCombo = shell.newTimeCombo();
        JComboBox<String> endCombo = shell.newTimeCombo();

        JButton save = new JButton("Save Class");
        AppTheme.stylePrimaryButton(save);
        save.addActionListener(event -> {
            if (teachers.isEmpty()) {
                shell.showMessage("Add a teacher first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }

            TeacherProfile selectedTeacher = teachers.get(teacherCombo.getSelectedIndex());
            shell.showResult(store.addScheduleSlot(
                    user.getUserId(),
                    selectedTeacher.getId(),
                    subjectField.getText(),
                    (DayOfWeek) dayCombo.getSelectedItem(),
                    shell.parseTimeValue((String) startCombo.getSelectedItem()),
                    shell.parseTimeValue((String) endCombo.getSelectedItem()),
                    roomField.getText()
            ));
            shell.refreshView();
        });

        JPanel formGrid = new JPanel(new GridLayout(2, 3, 12, 12));
        formGrid.setOpaque(false);
        formGrid.add(shell.labeledField("Teacher", teacherCombo));
        formGrid.add(shell.labeledField("Subject", subjectField));
        formGrid.add(shell.labeledField("Room", roomField));
        formGrid.add(shell.labeledField("Day", dayCombo));
        formGrid.add(shell.labeledField("Start Time", startCombo));
        formGrid.add(shell.labeledField("End Time", endCombo));

        JPanel scheduleBody = new JPanel(new BorderLayout(0, 12));
        scheduleBody.setOpaque(false);
        scheduleBody.add(formGrid, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.setOpaque(false);
        actions.add(save);
        scheduleBody.add(actions, BorderLayout.SOUTH);
        page.add(shell.createSection("Add Class", "Choose the teacher, subject, day, and time for each class.", scheduleBody));
        page.add(Box.createVerticalStrut(16));

        DefaultTableModel scheduleModel = shell.createTableModel("ID", "Teacher", "Subject", "Day", "Time", "Room", "Status");
        for (ScheduleSlot slot : store.getSchedules()) {
            TeacherProfile teacher = store.findTeacher(slot.getTeacherId());
            scheduleModel.addRow(new Object[]{
                slot.getId(),
                teacher == null ? "-" : teacher.getFullName(),
                slot.getSubjectName(),
                slot.getDay(),
                slot.getTimeLabel(),
                slot.getRoom(),
                shell.friendlyStatus(slot.getStatus())
            });
        }
        JTable scheduleTable = new JTable(scheduleModel);
        page.add(shell.createSection("Saved Classes", "All class schedules that are already approved.", shell.wrapTable(scheduleTable)));
        return page;
    }
}
