package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.CoreModels.AttendanceRecord;
import ppb.qrattend.model.CoreModels.ReportSummary;
import ppb.qrattend.model.CoreModels.Section;
import ppb.qrattend.model.CoreModels.Subject;
import ppb.qrattend.model.CoreModels.Teacher;

final class ReportsScreen {

    private ReportsScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        page.add(buildFilterSection(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildSummarySection(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildRecordsSection(shell));
        page.add(Box.createVerticalStrut(16));
        if (shell.getCurrentUser().isTeacher()) {
            page.add(shell.createTeacherAiPanel(
                    "reports|" + safeFilter(shell.getReportSectionFilter()) + "|" + safeFilter(shell.getReportSubjectFilter()),
                    "Ask about late students, weak attendance, or what stands out in this report.",
                    "What should I notice in this report?"
            ));
        }
        return page;
    }

    private static JPanel buildFilterSection(AppShell shell) {
        List<Teacher> teachers = shell.getStore().getTeachers();
        List<Section> sections = shell.getStore().getSections();
        List<Subject> subjects = shell.getStore().getSubjects();

        JComboBox<String> teacherCombo = new JComboBox<>(buildTeacherLabels(shell, teachers));
        JComboBox<String> sectionCombo = new JComboBox<>(buildSectionLabels(sections));
        JComboBox<String> subjectCombo = new JComboBox<>(buildSubjectLabels(subjects));
        AppTheme.styleCombo(teacherCombo);
        AppTheme.styleCombo(sectionCombo);
        AppTheme.styleCombo(subjectCombo);

        List<Integer> teacherIds = new ArrayList<>();
        for (Teacher teacher : teachers) {
            teacherIds.add(teacher.id());
        }
        List<Integer> sectionIds = new ArrayList<>();
        for (Section section : sections) {
            sectionIds.add(section.id());
        }
        List<Integer> subjectIds = new ArrayList<>();
        for (Subject subject : subjects) {
            subjectIds.add(subject.id());
        }
        teacherCombo.setSelectedIndex(indexForId(teacherIds, shell.getReportTeacherFilter()) + (shell.getCurrentUser().isAdmin() ? 1 : 0));
        sectionCombo.setSelectedIndex(indexForId(sectionIds, shell.getReportSectionFilter()) + 1);
        subjectCombo.setSelectedIndex(indexForId(subjectIds, shell.getReportSubjectFilter()) + 1);

        JButton showButton = new JButton("Show Report");
        AppTheme.stylePrimaryButton(showButton);
        showButton.addActionListener(event -> {
            if (shell.getCurrentUser().isAdmin()) {
                shell.setReportTeacherFilter(selectedId(teachers, teacherCombo.getSelectedIndex() - 1));
            } else {
                shell.setReportTeacherFilter(shell.getCurrentUser().getUserId());
            }
            shell.setReportSectionFilter(selectedId(sections, sectionCombo.getSelectedIndex() - 1));
            shell.setReportSubjectFilter(selectedId(subjects, subjectCombo.getSelectedIndex() - 1));
            shell.showMessage("Report updated.", AppTheme.SUCCESS);
            shell.refreshView();
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        actions.setOpaque(false);
        if (shell.getCurrentUser().isAdmin()) {
            actions.add(shell.labeledField("Teacher", teacherCombo));
        }
        actions.add(shell.labeledField("Section", sectionCombo));
        actions.add(shell.labeledField("Subject", subjectCombo));
        actions.add(shell.labeledField("Action", showButton));

        return shell.createSection("Report Filter", "Choose what you want to read, then press Show Report.", actions);
    }

    private static JPanel buildSummarySection(AppShell shell) {
        Integer teacherId = shell.getCurrentUser().isAdmin() ? shell.getReportTeacherFilter() : shell.getCurrentUser().getUserId();
        ReportSummary summary = shell.getStore().getReportSummary(teacherId, shell.getReportSectionFilter(), shell.getReportSubjectFilter());
        if (summary == null) {
            summary = new ReportSummary(0, 0, 0, 0);
        }

        JPanel cards = shell.createMetricsRow(
                AppTheme.createStatCard("Students", String.valueOf(summary.totalStudents()), AppTheme.BRAND),
                AppTheme.createStatCard("Present", String.valueOf(summary.totalPresent()), AppTheme.SUCCESS),
                AppTheme.createStatCard("Late", String.valueOf(summary.totalLate()), AppTheme.WARNING)
        );
        return shell.createSection("Summary", "This is the quick view for the current report filter.", cards);
    }

    private static JPanel buildRecordsSection(AppShell shell) {
        Integer teacherId = shell.getCurrentUser().isAdmin() ? shell.getReportTeacherFilter() : shell.getCurrentUser().getUserId();
        List<AttendanceRecord> records = shell.getStore().getReportRecords(teacherId, shell.getReportSectionFilter(), shell.getReportSubjectFilter());

        DefaultTableModel model = shell.createTableModel("Student", "Section", "Subject", "Time", "Method", "Status", "Note");
        for (AttendanceRecord record : records) {
            model.addRow(new Object[]{
                record.studentName(),
                record.sectionName(),
                record.subjectName(),
                record.recordedAt().format(ppb.qrattend.model.CoreModels.DATE_TIME_FORMAT),
                record.method().getLabel(),
                record.status().getLabel(),
                record.note()
            });
        }

        JTable table = new JTable(model);

        JButton exportButton = new JButton("Export CSV");
        AppTheme.styleSecondaryButton(exportButton);
        exportButton.addActionListener(event -> {
            javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV files", "csv"));
            int result = chooser.showSaveDialog(null);
            if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                java.io.File file = chooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".csv")) {
                    file = new java.io.File(file.getAbsolutePath() + ".csv");
                }
                shell.showResult(shell.getStore().exportCsv(records, file));
            }
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        actions.setOpaque(false);
        actions.add(exportButton);

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(AppFlowPanels.createSimpleList("How to read this", List.of(
                "Summary comes first, full records come below.",
                "Use the filter above to focus on one teacher, section, or subject.",
                "Ask AI if you want help understanding the report."
        )), BorderLayout.NORTH);
        body.add(shell.wrapTable(table), BorderLayout.CENTER);
        body.add(actions, BorderLayout.SOUTH);
        return shell.createSection("Attendance Records", "These rows match the current report filter.", body);
    }

    private static String[] buildTeacherLabels(AppShell shell, List<Teacher> teachers) {
        if (!shell.getCurrentUser().isAdmin()) {
            return new String[]{"My classes"};
        }
        List<String> labels = new ArrayList<>();
        labels.add("All teachers");
        for (Teacher teacher : teachers) {
            labels.add(teacher.fullName());
        }
        return labels.toArray(String[]::new);
    }

    private static String[] buildSectionLabels(List<Section> sections) {
        List<String> labels = new ArrayList<>();
        labels.add("All sections");
        for (Section section : sections) {
            labels.add(section.name());
        }
        return labels.toArray(String[]::new);
    }

    private static String[] buildSubjectLabels(List<Subject> subjects) {
        List<String> labels = new ArrayList<>();
        labels.add("All subjects");
        for (Subject subject : subjects) {
            labels.add(subject.name());
        }
        return labels.toArray(String[]::new);
    }

    private static int indexForId(List<Integer> ids, Integer targetId) {
        if (targetId == null) {
            return -1;
        }
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i).equals(targetId)) {   // value comparison — always correct
                return i;
            }
        }
        return -1;
    }

    private static <T> Integer selectedId(List<T> items, int index) {
        if (index < 0 || index >= items.size()) {
            return null;
        }
        Object item = items.get(index);
        if (item instanceof Teacher teacher) {
            return teacher.id();
        }
        if (item instanceof Section section) {
            return section.id();
        }
        if (item instanceof Subject subject) {
            return subject.id();
        }
        return null;
    }

    private static String safeFilter(Integer value) {
        return value == null ? "all" : value.toString();
    }
}
