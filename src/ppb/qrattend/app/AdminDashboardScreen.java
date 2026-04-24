package ppb.qrattend.app;

import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.AppDomain;
import ppb.qrattend.model.AppDomain.AttendanceRecord;
import ppb.qrattend.model.AppDomain.ScheduleSlot;
import ppb.qrattend.model.AppDomain.TeacherProfile;

final class AdminDashboardScreen {

    private AdminDashboardScreen() {
    }

    static JPanel build(AppShell shell) {
        AppDataStore store = shell.getStore();
        JPanel page = AppTheme.createPage();

        page.add(shell.createMetricsRow(
                AppTheme.createStatCard("Teachers", String.valueOf(store.getTeacherCount()), AppTheme.BRAND),
                AppTheme.createStatCard("Active Classes", String.valueOf(store.getActiveClassCount()), AppTheme.INFO),
                AppTheme.createStatCard("Pending Requests", String.valueOf(store.getPendingRequestCount()), AppTheme.WARNING),
                AppTheme.createStatCard("Email Issues", String.valueOf(store.getFailedEmailCount()), AppTheme.DANGER)
        ));
        page.add(Box.createVerticalStrut(16));

        DefaultTableModel scheduleModel = shell.createTableModel("Teacher", "Subject", "Time", "Room", "Status");
        for (ScheduleSlot slot : store.getTodaySchedules()) {
            TeacherProfile teacher = store.findTeacher(slot.getTeacherId());
            scheduleModel.addRow(new Object[]{
                teacher == null ? "-" : teacher.getFullName(),
                slot.getSubjectName(),
                slot.getTimeLabel(),
                slot.getRoom(),
                shell.friendlyStatus(slot.getStatus())
            });
        }
        JTable scheduleTable = new JTable(scheduleModel);
        page.add(shell.createSection("Today's Classes", "Classes scheduled for today.", shell.wrapTable(scheduleTable)));
        page.add(Box.createVerticalStrut(16));

        DefaultTableModel attendanceModel = shell.createTableModel("Student", "Subject", "Timestamp", "Source", "Status");
        for (AttendanceRecord record : store.getRecentAttendanceRecords(8, null)) {
            attendanceModel.addRow(new Object[]{
                record.getStudentName(),
                record.getSubjectName(),
                record.getTimestamp().format(AppDomain.DATE_TIME_FORMAT),
                record.getSource().getLabel(),
                record.getStatus().getLabel()
            });
        }
        JTable attendanceTable = new JTable(attendanceModel);
        page.add(shell.createSection("Recent Attendance", "Latest attendance updates across the school.", shell.wrapTable(attendanceTable)));
        return page;
    }
}
