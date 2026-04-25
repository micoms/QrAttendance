package ppb.qrattend.app;

import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JPanel;
import ppb.qrattend.model.CoreModels.AttendanceSession;
import ppb.qrattend.model.CoreModels.RequestStatus;
import ppb.qrattend.model.CoreModels.Schedule;

final class TeacherDashboardScreen {

    private TeacherDashboardScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        page.add(buildActionTiles(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildSummary(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(shell.createTeacherAiPanel(
                "dashboard",
                "Ask about today's class, late students, or what you should do next.",
                "Who needs my attention today?"
        ));
        return page;
    }

    private static JPanel buildActionTiles(AppShell shell) {
        JPanel firstRow = AppFlowPanels.createTileRow(
                AppFlowPanels.createActionTile("Step 1", "Start Attendance",
                        "Open your class, scan QR codes, and mark students without typing student IDs.",
                        "Open Attendance", () -> shell.openView("attendance")),
                AppFlowPanels.createActionTile("Step 2", "My Class List",
                        "Check the students from the sections in your schedule and ask the admin to remove one if needed.",
                        "Open Class List", () -> shell.openView("students"))
        );

        JPanel secondRow = AppFlowPanels.createTileRow(
                AppFlowPanels.createActionTile("Step 3", "Ask for Schedule Change",
                        "Pick a class, choose the new details from the saved lists, and send it to the admin.",
                        "Open My Schedule", () -> shell.openView("schedule")),
                AppFlowPanels.createActionTile("Step 4", "Reports",
                        "Read your class summary and ask AI to explain what you should watch.",
                        "Open Reports", () -> shell.openView("reports"))
        );

        return AppTheme.stack(firstRow, secondRow);
    }

    private static JPanel buildSummary(AppShell shell) {
        int teacherId = shell.getCurrentUser().getUserId();
        Schedule current = shell.getStore().getCurrentScheduleForTeacher(teacherId);
        Schedule next = shell.getStore().getNextScheduleForTeacher(teacherId);
        AttendanceSession session = shell.getStore().getCurrentSessionForTeacher(teacherId);
        int pendingCount = countPending(shell, teacherId);

        return AppTheme.stack(
                shell.createMetricsRow(
                        AppTheme.createStatCard("Class Status", session.status().getLabel(), AppTheme.BRAND),
                        AppTheme.createStatCard("Current Class", current == null ? "No class open" : current.subjectName(), AppTheme.INFO),
                        AppTheme.createStatCard("Next Class", next == null ? "No next class" : next.subjectName(), AppTheme.SUCCESS)
                ),
                AppFlowPanels.createSimpleList("Today", buildTodayLines(shell, teacherId, current, next, pendingCount))
        );
    }

    private static List<String> buildTodayLines(AppShell shell, int teacherId, Schedule current, Schedule next, int pendingCount) {
        List<String> lines = new ArrayList<>();
        lines.add(current == null
                ? "No class is open right now."
                : "Current class: " + current.subjectName() + " with " + current.sectionName() + ".");
        lines.add(next == null
                ? "No next class is listed for today."
                : "Next class: " + next.subjectName() + " at " + next.getTimeLabel() + ".");
        lines.add("Students in your class list: " + shell.getStore().getStudentsForTeacher(teacherId).size());
        lines.add("Requests waiting: " + pendingCount);
        return lines;
    }

    private static int countPending(AppShell shell, int teacherId) {
        int total = 0;
        for (var request : shell.getStore().getScheduleRequestsForTeacher(teacherId)) {
            if (request.status() == RequestStatus.PENDING) {
                total++;
            }
        }
        for (var request : shell.getStore().getStudentRemovalRequestsForTeacher(teacherId)) {
            if (request.status() == RequestStatus.PENDING) {
                total++;
            }
        }
        return total;
    }
}
