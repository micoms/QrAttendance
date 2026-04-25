package ppb.qrattend.app;

import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.CoreModels.EmailLog;
import ppb.qrattend.model.CoreModels.RequestStatus;

final class AdminDashboardScreen {

    private AdminDashboardScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        page.add(buildActionTiles(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildSummary(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildRecentEmailSection(shell));
        return page;
    }

    private static JPanel buildActionTiles(AppShell shell) {
        JPanel firstRow = AppFlowPanels.createTileRow(
                AppFlowPanels.createActionTile("Step 1", "Add Teacher",
                        "Create the teacher account first. The password email is sent for you.",
                        "Open Teachers", () -> shell.openView("teachers")),
                AppFlowPanels.createActionTile("Step 2", "Add Students",
                        "Pick a section, import a class list, or add one student at a time.",
                        "Open Students", () -> shell.openView("students"))
        );

        JPanel secondRow = AppFlowPanels.createTileRow(
                AppFlowPanels.createActionTile("Step 3", "Set Schedule",
                        "Build classes using saved teachers, sections, subjects, and rooms.",
                        "Open Schedule", () -> shell.openView("schedules")),
                AppFlowPanels.createActionTile("Step 4", "Review Requests",
                        "Check pending schedule changes and class list requests first.",
                        "Open Requests", () -> shell.openView("requests"))
        );

        JPanel thirdRow = AppFlowPanels.createTileRow(
                AppFlowPanels.createActionTile("Step 5", "View Reports",
                        "Read the school attendance summary and open the full records below.",
                        "Open Reports", () -> shell.openView("reports")),
                AppFlowPanels.createSimpleList("What to do next", buildHelpLines(shell))
        );

        return AppTheme.stack(firstRow, secondRow, thirdRow);
    }

    private static JPanel buildSummary(AppShell shell) {
        int teacherCount = shell.getStore().getTeachers().size();
        int sectionCount = shell.getStore().getSections().size();
        int pendingCount = countPending(shell);

        return shell.createMetricsRow(
                AppTheme.createStatCard("Teachers", String.valueOf(teacherCount), AppTheme.BRAND),
                AppTheme.createStatCard("Sections", String.valueOf(sectionCount), AppTheme.INFO),
                AppTheme.createStatCard("Need Approval", String.valueOf(pendingCount), AppTheme.WARNING)
        );
    }

    private static JPanel buildRecentEmailSection(AppShell shell) {
        DefaultTableModel model = shell.createTableModel("Type", "Sent To", "Subject", "Status", "Saved");
        for (EmailLog log : shell.getStore().getRecentEmailLogs(8)) {
            model.addRow(new Object[]{
                log.emailType(),
                log.recipientEmail(),
                log.subjectLine(),
                log.status().getLabel(),
                log.createdAt().format(ppb.qrattend.model.CoreModels.DATE_TIME_FORMAT)
            });
        }

        JTable table = new JTable(model);
        return shell.createSection("Recent Emails", "This helps you check whether password and QR emails were saved.", shell.wrapTable(table));
    }

    private static List<String> buildHelpLines(AppShell shell) {
        List<String> lines = new ArrayList<>();
        if (shell.getStore().getSections().isEmpty()) {
            lines.add("Start with Sections so students and schedules have something to use.");
        } else {
            lines.add("Sections are ready. You can add students and schedules now.");
        }
        if (shell.getStore().getSubjects().isEmpty() || shell.getStore().getRooms().isEmpty()) {
            lines.add("Save subjects and rooms before you build the class schedule.");
        } else {
            lines.add("Subjects and rooms are ready for schedule setup.");
        }
        lines.add("Pending requests: " + countPending(shell));
        return lines;
    }

    private static int countPending(AppShell shell) {
        int total = 0;
        for (var request : shell.getStore().getScheduleRequests()) {
            if (request.status() == RequestStatus.PENDING) {
                total++;
            }
        }
        for (var request : shell.getStore().getStudentRemovalRequests()) {
            if (request.status() == RequestStatus.PENDING) {
                total++;
            }
        }
        return total;
    }
}
