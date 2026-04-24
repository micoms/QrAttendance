package ppb.qrattend.app;

import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.AppDomain;
import ppb.qrattend.model.AppDomain.AttendanceRecord;
import ppb.qrattend.model.AppDomain.ScheduleSlot;
import ppb.qrattend.model.ModelUser;

final class TeacherDashboardScreen {

    private TeacherDashboardScreen() {
    }

    static JPanel build(AppShell shell) {
        AppDataStore store = shell.getStore();
        ModelUser user = shell.getCurrentUser();
        JPanel page = AppTheme.createPage();
        ScheduleSlot activeSlot = store.getActiveScheduleForTeacher(user.getUserId());
        ScheduleSlot nextSlot = store.getNextScheduleForTeacher(user.getUserId());

        page.add(shell.createMetricsRow(
                AppTheme.createStatCard("Current Class", activeSlot == null ? "No class open" : activeSlot.getSubjectName(), AppTheme.BRAND),
                AppTheme.createStatCard("Next Class", nextSlot == null ? "No next class" : nextSlot.getSubjectName(), AppTheme.INFO),
                AppTheme.createStatCard("Students", String.valueOf(store.getStudentCountForTeacher(user.getUserId())), AppTheme.SUCCESS),
                AppTheme.createStatCard(
                        "Need Approval",
                        String.valueOf(
                                store.getScheduleRequestsForTeacher(user.getUserId()).stream()
                                        .filter(request -> request.getStatus() == AppDomain.ScheduleRequestStatus.PENDING)
                                        .count()
                                        + store.getPendingStudentRemovalCountForTeacher(user.getUserId())
                        ),
                        AppTheme.WARNING
                )
        ));
        page.add(Box.createVerticalStrut(16));

        page.add(shell.createTeacherAssistantSection(
                "dashboard",
                "Ask AI",
                "Ask about recent attendance, students who may need follow-up, or what today's schedule means for your class list.",
                "Who should I follow up with based on recent attendance?"
        ));
        page.add(Box.createVerticalStrut(16));

        DefaultTableModel scheduleModel = shell.createTableModel("Subject", "Day", "Time", "Room", "Status");
        for (ScheduleSlot slot : store.getSchedulesForTeacher(user.getUserId())) {
            scheduleModel.addRow(new Object[]{
                slot.getSubjectName(),
                slot.getDay(),
                slot.getTimeLabel(),
                slot.getRoom(),
                slot.getStatus()
            });
        }
        JTable scheduleTable = new JTable(scheduleModel);
        page.add(shell.createSection("My Classes", "Your approved class schedule.", shell.wrapTable(scheduleTable)));
        page.add(Box.createVerticalStrut(16));

        DefaultTableModel attendanceModel = shell.createTableModel("Student", "Subject", "Timestamp", "Source", "Status");
        for (AttendanceRecord record : store.getRecentAttendanceRecords(8, user.getUserId())) {
            attendanceModel.addRow(new Object[]{
                record.getStudentName(),
                record.getSubjectName(),
                record.getTimestamp().format(AppDomain.DATE_TIME_FORMAT),
                record.getSource().getLabel(),
                record.getStatus().getLabel()
            });
        }
        JTable attendanceTable = new JTable(attendanceModel);
        page.add(shell.createSection("Recent Attendance", "Latest attendance recorded in your classes.", shell.wrapTable(attendanceTable)));
        return page;
    }
}
