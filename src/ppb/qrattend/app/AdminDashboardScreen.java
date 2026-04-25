package ppb.qrattend.app;

import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.AppDomain.ScheduleSlot;
import ppb.qrattend.model.AppDomain.TeacherProfile;

final class AdminDashboardScreen {

    private AdminDashboardScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        page.add(buildTaskTiles(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildSimpleSummary(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildTodayClasses(shell));
        return page;
    }

    private static JPanel buildTaskTiles(AppShell shell) {
        JPanel firstRow = AppFlowPanels.createTileRow(
                AppFlowPanels.createActionTile("Step 1", "Add a teacher",
                        "Start by adding the teacher's name and email address.",
                        "Open Teachers", () -> shell.openView("teachers")),
                AppFlowPanels.createActionTile("Step 2", "Add students",
                        "Put students in the right section and assign the teacher.",
                        "Open Students", () -> shell.openView("students"))
        );

        JPanel secondRow = AppFlowPanels.createTileRow(
                AppFlowPanels.createActionTile("Step 3", "Set class schedule",
                        "Choose the day, time, subject, and room for each class.",
                        "Open Schedule", () -> shell.openView("schedules")),
                AppFlowPanels.createActionTile("Step 4", "Check requests",
                        "Review schedule changes and class list requests that still need approval.",
                        "Open Requests", () -> shell.openView("requests"))
        );

        JPanel thirdRow = AppFlowPanels.createTileRow(
                AppFlowPanels.createActionTile("Step 5", "View reports",
                        "Check attendance records and refresh the school summary.",
                        "Open Reports", () -> shell.openView("reports")),
                AppFlowPanels.createSimpleList("Quick school check", buildQuickLines(shell))
        );

        return AppTheme.stack(firstRow, secondRow, thirdRow);
    }

    private static JPanel buildSimpleSummary(AppShell shell) {
        return shell.createMetricsRow(
                AppTheme.createStatCard("Teachers", String.valueOf(shell.getStore().getTeacherCount()), AppTheme.BRAND),
                AppTheme.createStatCard("Need approval", String.valueOf(shell.getStore().getPendingRequestCount()), AppTheme.WARNING),
                AppTheme.createStatCard("Email issues", String.valueOf(shell.getStore().getFailedEmailCount()), AppTheme.DANGER)
        );
    }

    private static JPanel buildTodayClasses(AppShell shell) {
        DefaultTableModel model = shell.createTableModel("Teacher", "Subject", "Time", "Room");
        for (ScheduleSlot slot : shell.getStore().getTodaySchedules()) {
            TeacherProfile teacher = shell.getStore().findTeacher(slot.getTeacherId());
            model.addRow(new Object[]{
                teacher == null ? "-" : teacher.getFullName(),
                slot.getSubjectName(),
                slot.getTimeLabel(),
                slot.getRoom()
            });
        }

        JTable table = new JTable(model);
        return shell.createSection("Today", "These are the classes planned for today.", shell.wrapTable(table));
    }

    private static List<String> buildQuickLines(AppShell shell) {
        List<String> lines = new ArrayList<>();
        lines.add("Teachers: " + shell.getStore().getTeacherCount());
        lines.add("Classes open now: " + shell.getStore().getActiveClassCount());
        lines.add("Requests waiting: " + shell.getStore().getPendingRequestCount());
        return lines;
    }
}
