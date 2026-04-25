package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.CoreModels.Room;
import ppb.qrattend.model.CoreModels.Schedule;
import ppb.qrattend.model.CoreModels.Section;
import ppb.qrattend.model.CoreModels.Subject;
import ppb.qrattend.model.CoreModels.Teacher;

final class AdminSchedulesScreen {

    private AdminSchedulesScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        page.add(buildScheduleForm(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildSavedSchedules(shell));
        return page;
    }

    private static JPanel buildScheduleForm(AppShell shell) {
        List<Teacher> teachers = shell.getStore().getTeachers();
        List<Section> sections = shell.getStore().getSections();
        List<Subject> subjects = shell.getStore().getSubjects();
        List<Room> rooms = shell.getStore().getRooms();

        if (teachers.isEmpty() || sections.isEmpty() || subjects.isEmpty() || rooms.isEmpty()) {
            return shell.createSection("Set Schedule", "Finish the saved lists first.", AppFlowPanels.createSimpleList("What is missing?", buildMissingLines(teachers, sections, subjects, rooms)));
        }

        String[] teacherNames = new String[teachers.size()];
        for (int i = 0; i < teachers.size(); i++) {
            teacherNames[i] = teachers.get(i).fullName();
        }
        String[] sectionNames = new String[sections.size()];
        for (int i = 0; i < sections.size(); i++) {
            sectionNames[i] = sections.get(i).name();
        }
        String[] subjectNames = new String[subjects.size()];
        for (int i = 0; i < subjects.size(); i++) {
            subjectNames[i] = subjects.get(i).name();
        }
        String[] roomNames = new String[rooms.size()];
        for (int i = 0; i < rooms.size(); i++) {
            roomNames[i] = rooms.get(i).name();
        }
        JComboBox<String> teacherCombo = new JComboBox<>(teacherNames);
        JComboBox<String> sectionCombo = new JComboBox<>(sectionNames);
        JComboBox<String> subjectCombo = new JComboBox<>(subjectNames);
        JComboBox<String> roomCombo = new JComboBox<>(roomNames);
        JComboBox<DayOfWeek> dayCombo = shell.newDayCombo();
        JComboBox<String> startCombo = shell.newTimeCombo();
        JComboBox<String> endCombo = shell.newTimeCombo();

        AppTheme.styleCombo(teacherCombo);
        AppTheme.styleCombo(sectionCombo);
        AppTheme.styleCombo(subjectCombo);
        AppTheme.styleCombo(roomCombo);

        JButton saveButton = new JButton("Save Class");
        AppTheme.stylePrimaryButton(saveButton);
        saveButton.addActionListener(event -> {
            Teacher teacher = teachers.get(teacherCombo.getSelectedIndex());
            Section section = sections.get(sectionCombo.getSelectedIndex());
            Subject subject = subjects.get(subjectCombo.getSelectedIndex());
            Room room = rooms.get(roomCombo.getSelectedIndex());
            LocalTime start = shell.parseTime((String) startCombo.getSelectedItem());
            LocalTime end = shell.parseTime((String) endCombo.getSelectedItem());

            shell.showResult(shell.getStore().addSchedule(
                    teacher.id(),
                    section.id(),
                    subject.id(),
                    room.id(),
                    (DayOfWeek) dayCombo.getSelectedItem(),
                    start,
                    end
            ));
            shell.refreshView();
        });

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        form.setOpaque(false);
        form.add(shell.labeledField("Teacher", teacherCombo));
        form.add(shell.labeledField("Section", sectionCombo));
        form.add(shell.labeledField("Subject", subjectCombo));
        form.add(shell.labeledField("Room", roomCombo));
        form.add(shell.labeledField("Day", dayCombo));
        form.add(shell.labeledField("Start", startCombo));
        form.add(shell.labeledField("End", endCombo));
        form.add(shell.labeledField("Action", saveButton));

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        List<String> hints = new ArrayList<>();
        hints.add("Pick everything from the saved lists.");
        hints.add("You do not need to type subjects or rooms here.");
        hints.add("If the time overlaps with another class for the same teacher, the system will stop it.");
        body.add(AppFlowPanels.createSimpleList("How this works", hints), BorderLayout.NORTH);
        body.add(form, BorderLayout.SOUTH);
        return shell.createSection("Set Schedule", "Build one class at a time using the saved school lists.", body);
    }

    private static JPanel buildSavedSchedules(AppShell shell) {
        List<Schedule> schedules = shell.getStore().getSchedules();
        List<Teacher> teachers = shell.getStore().getTeachers();
        List<Section> sections = shell.getStore().getSections();
        List<Subject> subjects = shell.getStore().getSubjects();
        List<Room> rooms = shell.getStore().getRooms();

        if (schedules.isEmpty()) {
            List<String> emptyHints = new ArrayList<>();
            emptyHints.add("Save your first class above.");
            emptyHints.add("Deleted classes are hidden from this list.");
            emptyHints.add("Choose a saved class later if you need to edit it.");
            return shell.createSection("Saved Classes", "Your active classes will show here.", AppFlowPanels.createSimpleList("Nothing saved yet", emptyHints));
        }

        DefaultTableModel model = shell.createTableModel("Teacher", "Section", "Subject", "Room", "Day", "Time");
        for (Schedule schedule : schedules) {
            model.addRow(new Object[]{
                schedule.teacherName(),
                schedule.sectionName(),
                schedule.subjectName(),
                schedule.roomName(),
                schedule.day(),
                schedule.getTimeLabel()
            });
        }

        JTable table = new JTable(model);

        // Inline edit form (initially hidden)
        JLabel editFormLabel = new JLabel("Edit selected class:");
        editFormLabel.setFont(AppTheme.bodyFont(12));
        editFormLabel.setForeground(AppTheme.TEXT_MUTED);

        String[] teacherNames = new String[teachers.size()];
        for (int i = 0; i < teachers.size(); i++) {
            teacherNames[i] = teachers.get(i).fullName();
        }
        String[] sectionNames = new String[sections.size()];
        for (int i = 0; i < sections.size(); i++) {
            sectionNames[i] = sections.get(i).name();
        }
        String[] subjectNames = new String[subjects.size()];
        for (int i = 0; i < subjects.size(); i++) {
            subjectNames[i] = subjects.get(i).name();
        }
        String[] roomNames = new String[rooms.size()];
        for (int i = 0; i < rooms.size(); i++) {
            roomNames[i] = rooms.get(i).name();
        }

        JComboBox<String> editTeacherCombo = new JComboBox<>(teacherNames);
        JComboBox<String> editSectionCombo = new JComboBox<>(sectionNames);
        JComboBox<String> editSubjectCombo = new JComboBox<>(subjectNames);
        JComboBox<String> editRoomCombo = new JComboBox<>(roomNames);
        JComboBox<DayOfWeek> editDayCombo = shell.newDayCombo();
        JComboBox<String> editStartCombo = shell.newTimeCombo();
        JComboBox<String> editEndCombo = shell.newTimeCombo();

        AppTheme.styleCombo(editTeacherCombo);
        AppTheme.styleCombo(editSectionCombo);
        AppTheme.styleCombo(editSubjectCombo);
        AppTheme.styleCombo(editRoomCombo);

        JButton saveChangesButton = new JButton("Save Changes");
        AppTheme.stylePrimaryButton(saveChangesButton);

        JPanel editForm = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        editForm.setOpaque(false);
        editForm.add(editFormLabel);
        editForm.add(shell.labeledField("Teacher", editTeacherCombo));
        editForm.add(shell.labeledField("Section", editSectionCombo));
        editForm.add(shell.labeledField("Subject", editSubjectCombo));
        editForm.add(shell.labeledField("Room", editRoomCombo));
        editForm.add(shell.labeledField("Day", editDayCombo));
        editForm.add(shell.labeledField("Start", editStartCombo));
        editForm.add(shell.labeledField("End", editEndCombo));
        editForm.add(shell.labeledField("Action", saveChangesButton));
        editForm.setVisible(false);

        // Action buttons
        JButton editButton = new JButton("Edit");
        JButton deleteButton = new JButton("Delete");
        AppTheme.styleSecondaryButton(editButton);
        AppTheme.styleDangerButton(deleteButton);

        editButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0 || row >= schedules.size()) {
                shell.showMessage("Choose a class first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }
            Schedule schedule = schedules.get(row);

            // Pre-select teacher
            for (int i = 0; i < teachers.size(); i++) {
                if (teachers.get(i).id() == schedule.teacherId()) {
                    editTeacherCombo.setSelectedIndex(i);
                    break;
                }
            }
            // Pre-select section
            for (int i = 0; i < sections.size(); i++) {
                if (sections.get(i).id() == schedule.sectionId()) {
                    editSectionCombo.setSelectedIndex(i);
                    break;
                }
            }
            // Pre-select subject
            for (int i = 0; i < subjects.size(); i++) {
                if (subjects.get(i).id() == schedule.subjectId()) {
                    editSubjectCombo.setSelectedIndex(i);
                    break;
                }
            }
            // Pre-select room
            for (int i = 0; i < rooms.size(); i++) {
                if (rooms.get(i).id() == schedule.roomId()) {
                    editRoomCombo.setSelectedIndex(i);
                    break;
                }
            }
            // Pre-select day
            editDayCombo.setSelectedItem(schedule.day());

            // Pre-select start time
            String startLabel = schedule.startTime().format(ppb.qrattend.model.CoreModels.TIME_FORMAT);
            for (int i = 0; i < editStartCombo.getItemCount(); i++) {
                if (startLabel.equals(editStartCombo.getItemAt(i))) {
                    editStartCombo.setSelectedIndex(i);
                    break;
                }
            }
            // Pre-select end time
            String endLabel = schedule.endTime().format(ppb.qrattend.model.CoreModels.TIME_FORMAT);
            for (int i = 0; i < editEndCombo.getItemCount(); i++) {
                if (endLabel.equals(editEndCombo.getItemAt(i))) {
                    editEndCombo.setSelectedIndex(i);
                    break;
                }
            }

            editForm.setVisible(true);
            editForm.revalidate();
            editForm.repaint();
        });

        saveChangesButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0 || row >= schedules.size()) {
                shell.showMessage("Choose a class first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }
            Schedule schedule = schedules.get(row);
            Teacher teacher = teachers.get(editTeacherCombo.getSelectedIndex());
            Section section = sections.get(editSectionCombo.getSelectedIndex());
            Subject subject = subjects.get(editSubjectCombo.getSelectedIndex());
            Room room = rooms.get(editRoomCombo.getSelectedIndex());
            LocalTime start = shell.parseTime((String) editStartCombo.getSelectedItem());
            LocalTime end = shell.parseTime((String) editEndCombo.getSelectedItem());

            shell.showResult(shell.getStore().updateSchedule(
                    schedule.id(),
                    teacher.id(),
                    section.id(),
                    subject.id(),
                    room.id(),
                    (DayOfWeek) editDayCombo.getSelectedItem(),
                    start,
                    end
            ));
            shell.refreshView();
        });

        deleteButton.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0 || row >= schedules.size()) {
                shell.showMessage("Choose a class first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }
            Schedule schedule = schedules.get(row);
            shell.showResult(shell.getStore().deactivateSchedule(schedule.id()));
            shell.refreshView();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(editButton);
        actions.add(deleteButton);

        JPanel south = new JPanel(new BorderLayout(0, 8));
        south.setOpaque(false);
        south.add(actions, BorderLayout.NORTH);
        south.add(editForm, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(shell.wrapTable(table), BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);

        return shell.createSection("Saved Classes", "Choose one class below if you want to edit or remove it.", body);
    }

    private static List<String> buildMissingLines(List<Teacher> teachers, List<Section> sections, List<Subject> subjects, List<Room> rooms) {
        List<String> lines = new java.util.ArrayList<>();
        if (teachers.isEmpty()) {
            lines.add("Add at least one teacher.");
        }
        if (sections.isEmpty()) {
            lines.add("Add at least one section.");
        }
        if (subjects.isEmpty()) {
            lines.add("Save at least one subject.");
        }
        if (rooms.isEmpty()) {
            lines.add("Save at least one room.");
        }
        return lines;
    }
}
