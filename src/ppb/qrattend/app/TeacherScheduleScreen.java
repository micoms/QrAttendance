package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.CoreModels.RequestStatus;
import ppb.qrattend.model.CoreModels.Room;
import ppb.qrattend.model.CoreModels.Schedule;
import ppb.qrattend.model.CoreModels.ScheduleRequest;
import ppb.qrattend.model.CoreModels.Section;
import ppb.qrattend.model.CoreModels.Subject;

final class TeacherScheduleScreen {

    private TeacherScheduleScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        page.add(buildSavedScheduleSection(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildRequestForm(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildRequestHistory(shell));
        return page;
    }

    private static JPanel buildSavedScheduleSection(AppShell shell) {
        DefaultTableModel model = shell.createTableModel("Section", "Subject", "Room", "Day", "Time");
        for (Schedule schedule : shell.getStore().getSchedulesForTeacher(shell.getCurrentUser().getUserId())) {
            model.addRow(new Object[]{
                schedule.sectionName(),
                schedule.subjectName(),
                schedule.roomName(),
                schedule.day(),
                schedule.getTimeLabel()
            });
        }
        JTable table = new JTable(model);
        return shell.createSection("My Schedule", "These classes are already saved for you.", shell.wrapTable(table));
    }

    private static JPanel buildRequestForm(AppShell shell) {
        List<Schedule> schedules = shell.getStore().getSchedulesForTeacher(shell.getCurrentUser().getUserId());
        if (schedules.isEmpty()) {
            return shell.createSection("Ask for Schedule Change", "There are no classes to change yet.", AppFlowPanels.createSimpleList("What to do next", List.of(
                    "Ask the admin to save your class schedule first.",
                    "After that, you can come back here and send a schedule change request."
            )));
        }

        List<Section> sections = shell.getStore().getSections();
        List<Subject> subjects = shell.getStore().getSubjects();
        List<Room> rooms = shell.getStore().getRooms();

        String[] scheduleLabels = new String[schedules.size()];
        for (int i = 0; i < schedules.size(); i++) {
            Schedule s = schedules.get(i);
            scheduleLabels[i] = s.subjectName() + " | " + s.sectionName() + " | " + s.getTimeLabel();
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
        JComboBox<String> scheduleCombo = new JComboBox<>(scheduleLabels);
        JComboBox<String> sectionCombo = new JComboBox<>(sectionNames);
        JComboBox<String> subjectCombo = new JComboBox<>(subjectNames);
        JComboBox<String> roomCombo = new JComboBox<>(roomNames);
        JComboBox<DayOfWeek> dayCombo = shell.newDayCombo();
        JComboBox<String> startCombo = shell.newTimeCombo();
        JComboBox<String> endCombo = shell.newTimeCombo();
        JTextArea reasonArea = shell.newTextArea();
        reasonArea.setRows(3);
        reasonArea.setText("The class details need to be updated.");

        AppTheme.styleCombo(scheduleCombo);
        AppTheme.styleCombo(sectionCombo);
        AppTheme.styleCombo(subjectCombo);
        AppTheme.styleCombo(roomCombo);

        scheduleCombo.addActionListener(event -> applyScheduleDefaults(
                schedules,
                scheduleCombo,
                sections,
                sectionCombo,
                subjects,
                subjectCombo,
                rooms,
                roomCombo,
                dayCombo,
                startCombo,
                endCombo
        ));
        applyScheduleDefaults(schedules, scheduleCombo, sections, sectionCombo, subjects, subjectCombo, rooms, roomCombo, dayCombo, startCombo, endCombo);

        JButton sendButton = new JButton("Ask Admin");
        AppTheme.stylePrimaryButton(sendButton);
        sendButton.addActionListener(event -> {
            Schedule selected = itemAt(schedules, scheduleCombo.getSelectedIndex());
            Section section = itemAt(sections, sectionCombo.getSelectedIndex());
            Subject subject = itemAt(subjects, subjectCombo.getSelectedIndex());
            Room room = itemAt(rooms, roomCombo.getSelectedIndex());
            LocalTime start = shell.parseTime((String) startCombo.getSelectedItem());
            LocalTime end = shell.parseTime((String) endCombo.getSelectedItem());

            if (selected == null || section == null || subject == null || room == null) {
                shell.showMessage("Complete the request first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }

            shell.showResult(shell.getStore().submitScheduleRequest(
                    shell.getCurrentUser().getUserId(),
                    selected.id(),
                    section.id(),
                    subject.id(),
                    room.id(),
                    (DayOfWeek) dayCombo.getSelectedItem(),
                    start,
                    end,
                    reasonArea.getText()
            ));
            shell.refreshView();
        });

        JPanel rowOne = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        rowOne.setOpaque(false);
        rowOne.add(shell.labeledField("Class to change", scheduleCombo));
        rowOne.add(shell.labeledField("Section", sectionCombo));
        rowOne.add(shell.labeledField("Subject", subjectCombo));
        rowOne.add(shell.labeledField("Room", roomCombo));

        JPanel rowTwo = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        rowTwo.setOpaque(false);
        rowTwo.add(shell.labeledField("Day", dayCombo));
        rowTwo.add(shell.labeledField("Start", startCombo));
        rowTwo.add(shell.labeledField("End", endCombo));
        rowTwo.add(shell.labeledField("Action", sendButton));

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(AppFlowPanels.createSimpleList("How to send a change", List.of(
                "Pick the class you want to change.",
                "Choose the new section, subject, room, day, and time from the saved lists.",
                "Tell the admin why this change is needed."
        )), BorderLayout.NORTH);
        body.add(AppTheme.stack(rowOne, rowTwo, shell.labeledField("Reason", new javax.swing.JScrollPane(reasonArea))), BorderLayout.CENTER);
        return shell.createSection("Ask for Schedule Change", "Send the change to the admin for approval.", body);
    }

    private static JPanel buildRequestHistory(AppShell shell) {
        DefaultTableModel model = shell.createTableModel("Section", "Subject", "Room", "Day", "Time", "Status");
        int pending = 0;
        for (ScheduleRequest request : shell.getStore().getScheduleRequestsForTeacher(shell.getCurrentUser().getUserId())) {
            if (request.status() == RequestStatus.PENDING) {
                pending++;
            }
            model.addRow(new Object[]{
                request.sectionName(),
                request.subjectName(),
                request.roomName(),
                request.day(),
                request.getTimeLabel(),
                request.status().getLabel()
            });
        }

        JTable table = new JTable(model);
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(AppFlowPanels.createSimpleList("Schedule Requests", List.of(
                "Pending requests: " + pending,
                "Approved requests update your saved class schedule.",
                "Rejected requests stay here so you can check them later."
        )), BorderLayout.NORTH);
        body.add(shell.wrapTable(table), BorderLayout.CENTER);
        return shell.createSection("Request History", "These are the schedule changes you already asked for.", body);
    }

    private static void applyScheduleDefaults(List<Schedule> schedules, JComboBox<String> scheduleCombo,
            List<Section> sections, JComboBox<String> sectionCombo,
            List<Subject> subjects, JComboBox<String> subjectCombo,
            List<Room> rooms, JComboBox<String> roomCombo,
            JComboBox<DayOfWeek> dayCombo, JComboBox<String> startCombo, JComboBox<String> endCombo) {
        Schedule schedule = itemAt(schedules, scheduleCombo.getSelectedIndex());
        if (schedule == null) {
            return;
        }
        selectSection(sections, sectionCombo, schedule.sectionId());
        selectSubject(subjects, subjectCombo, schedule.subjectId());
        selectRoom(rooms, roomCombo, schedule.roomId());
        dayCombo.setSelectedItem(schedule.day());
        startCombo.setSelectedItem(schedule.startTime().format(ppb.qrattend.model.CoreModels.TIME_FORMAT));
        endCombo.setSelectedItem(schedule.endTime().format(ppb.qrattend.model.CoreModels.TIME_FORMAT));
    }

    private static void selectSection(List<Section> sections, JComboBox<String> combo, int sectionId) {
        for (int i = 0; i < sections.size(); i++) {
            if (sections.get(i).id() == sectionId) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private static void selectSubject(List<Subject> subjects, JComboBox<String> combo, int subjectId) {
        for (int i = 0; i < subjects.size(); i++) {
            if (subjects.get(i).id() == subjectId) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private static void selectRoom(List<Room> rooms, JComboBox<String> combo, int roomId) {
        for (int i = 0; i < rooms.size(); i++) {
            if (rooms.get(i).id() == roomId) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private static <T> T itemAt(List<T> items, int index) {
        if (index < 0 || index >= items.size()) {
            return null;
        }
        return items.get(index);
    }
}
