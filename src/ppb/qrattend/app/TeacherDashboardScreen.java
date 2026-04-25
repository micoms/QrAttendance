package ppb.qrattend.app;

import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JPanel;
import ppb.qrattend.model.AppDomain;
import ppb.qrattend.model.AppDomain.ScheduleSlot;
import ppb.qrattend.model.ModelUser;

final class TeacherDashboardScreen {

    private TeacherDashboardScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        page.add(buildTaskTiles(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildStatusRow(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(shell.createTeacherAssistantSection(
                "dashboard",
                "Ask AI",
                "Ask about your class attendance, late students, or what you should do next.",
                "Who needs my attention today?"
        ));
        return page;
    }

    private static JPanel buildTaskTiles(AppShell shell) {
        JPanel firstRow = AppFlowPanels.createTileRow(
                AppFlowPanels.createActionTile("Step 1", "Start attendance",
                        "Open your class and begin scanning student QR codes.",
                        "Open Attendance", () -> shell.openView("attendance")),
                AppFlowPanels.createActionTile("Step 2", "Check class list",
                        "Make sure your student list looks correct before class starts.",
                        "Open Class List", () -> shell.openView("students"))
        );

        JPanel secondRow = AppFlowPanels.createTileRow(
                AppFlowPanels.createActionTile("Step 3", "Ask for a schedule change",
                        "If something is wrong in your schedule, send the change to the admin.",
                        "Open My Schedule", () -> shell.openView("schedule")),
                AppFlowPanels.createActionTile("Step 4", "Read reports",
                        "Check the attendance summary and ask AI to explain it.",
                        "Open Reports", () -> shell.openView("reports"))
        );

        return AppTheme.stack(firstRow, secondRow);
    }

    private static JPanel buildStatusRow(AppShell shell) {
        ModelUser user = shell.getCurrentUser();
        ScheduleSlot activeSlot = shell.getStore().getActiveScheduleForTeacher(user.getUserId());
        ScheduleSlot nextSlot = shell.getStore().getNextScheduleForTeacher(user.getUserId());

        return AppTheme.stack(
                shell.createMetricsRow(
                        AppTheme.createStatCard("Current class", activeSlot == null ? "No class open" : activeSlot.getSubjectName(), AppTheme.BRAND),
                        AppTheme.createStatCard("Next class", nextSlot == null ? "No next class" : nextSlot.getSubjectName(), AppTheme.INFO),
                        AppTheme.createStatCard("Students", String.valueOf(shell.getStore().getStudentCountForTeacher(user.getUserId())), AppTheme.SUCCESS)
                ),
                AppFlowPanels.createSimpleList("Today", buildTodayLines(shell, activeSlot, nextSlot))
        );
    }

    private static List<String> buildTodayLines(AppShell shell, ScheduleSlot activeSlot, ScheduleSlot nextSlot) {
        List<String> lines = new ArrayList<>();
        int pendingCount = 0;
        for (AppDomain.ScheduleChangeRequest request : shell.getStore().getScheduleRequestsForTeacher(shell.getCurrentUser().getUserId())) {
            if (request.getStatus() == AppDomain.ScheduleRequestStatus.PENDING) {
                pendingCount++;
            }
        }
        for (AppDomain.StudentRemovalRequest request : shell.getStore().getStudentRemovalRequestsForTeacher(shell.getCurrentUser().getUserId())) {
            if (request.getStatus() == AppDomain.ScheduleRequestStatus.PENDING) {
                pendingCount++;
            }
        }
        lines.add(activeSlot == null ? "No class is open right now." : "Current class: " + activeSlot.getSubjectName() + " in " + activeSlot.getRoom());
        lines.add(nextSlot == null ? "No next class is listed." : "Next class: " + nextSlot.getSubjectName() + " at " + nextSlot.getTimeLabel());
        lines.add("Pending requests: " + pendingCount);
        return lines;
    }
}
