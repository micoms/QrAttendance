package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.DayOfWeek;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.AppDomain.ScheduleChangeRequest;
import ppb.qrattend.model.AppDomain.ScheduleSlot;

final class TeacherScheduleScreen {

    private TeacherScheduleScreen() {
    }

    static JPanel build(AppShell shell) {
        ScheduleTableData tableData = createScheduleTable(shell);

        JPanel page = AppTheme.createPage();
        page.add(shell.createSection("My schedule", "These are your current classes.", shell.wrapTable(tableData.table)));
        page.add(Box.createVerticalStrut(16));
        page.add(buildChangeRequestSection(shell, tableData));
        page.add(Box.createVerticalStrut(16));
        page.add(buildRequestHistorySection(shell));
        return page;
    }

    private static ScheduleTableData createScheduleTable(AppShell shell) {
        DefaultTableModel model = shell.createTableModel("ID", "Subject", "Day", "Time", "Room", "Status");
        for (ScheduleSlot slot : shell.getStore().getSchedulesForTeacher(shell.getCurrentUser().getUserId())) {
            model.addRow(new Object[]{
                slot.getId(),
                slot.getSubjectName(),
                slot.getDay(),
                slot.getTimeLabel(),
                slot.getRoom(),
                shell.friendlyStatus(slot.getStatus())
            });
        }
        return new ScheduleTableData(model, new JTable(model));
    }

    private static JPanel buildChangeRequestSection(AppShell shell, ScheduleTableData tableData) {
        JTextField subjectField = shell.newTextField();
        JTextField roomField = shell.newTextField();
        JTextArea reasonArea = shell.newTextArea();
        JComboBox<DayOfWeek> dayCombo = shell.newDayCombo();
        JComboBox<String> startCombo = shell.newTimeCombo();
        JComboBox<String> endCombo = shell.newTimeCombo();

        JButton loadButton = new JButton("Use Selected Class");
        AppTheme.styleSecondaryButton(loadButton);
        loadButton.addActionListener(event -> loadSelectedSchedule(shell, tableData, subjectField, roomField, dayCombo, startCombo, endCombo));

        JButton sendButton = new JButton("Ask for Change");
        AppTheme.stylePrimaryButton(sendButton);
        sendButton.addActionListener(event -> submitScheduleChange(
                shell, tableData, subjectField, roomField, reasonArea, dayCombo, startCombo, endCombo
        ));

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(AppFlowPanels.createSimpleList("How to ask for a change", java.util.List.of(
                "Choose the class from the schedule above.",
                "Press Use Selected Class.",
                "Change only the details that are wrong, then send it."
        )), BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(3, 2, 12, 12));
        form.setOpaque(false);
        form.add(shell.labeledField("Subject", subjectField));
        form.add(shell.labeledField("Room", roomField));
        form.add(shell.labeledField("Day", dayCombo));
        form.add(shell.labeledField("Start time", startCombo));
        form.add(shell.labeledField("End time", endCombo));
        form.add(shell.labeledField("Reason", new javax.swing.JScrollPane(reasonArea)));
        body.add(form, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actions.setOpaque(false);
        actions.add(loadButton);
        actions.add(sendButton);
        body.add(actions, BorderLayout.SOUTH);

        return shell.createSection("Ask for a schedule change", "Send the change to the admin for approval.", body);
    }

    private static JPanel buildRequestHistorySection(AppShell shell) {
        DefaultTableModel requestModel = shell.createTableModel("ID", "Old", "Requested", "Status", "Reason", "Reviewed By");
        for (ScheduleChangeRequest request : shell.getStore().getScheduleRequestsForTeacher(shell.getCurrentUser().getUserId())) {
            requestModel.addRow(new Object[]{
                request.getId(),
                request.getOldValue(),
                request.getRequestedValue(),
                request.getStatus().getLabel(),
                request.getReason(),
                request.getReviewedBy() == null ? "-" : request.getReviewedBy()
            });
        }

        return shell.createSection("Your requests", "Check if your request is still waiting, approved, or rejected.",
                shell.wrapTable(new JTable(requestModel)));
    }

    private static void loadSelectedSchedule(AppShell shell, ScheduleTableData tableData,
            JTextField subjectField, JTextField roomField, JComboBox<DayOfWeek> dayCombo,
            JComboBox<String> startCombo, JComboBox<String> endCombo) {
        int row = tableData.table.getSelectedRow();
        if (row < 0) {
            shell.showMessage("Choose a class first.", AppTheme.WARNING);
            shell.refreshView();
            return;
        }

        subjectField.setText(String.valueOf(tableData.model.getValueAt(row, 1)));
        dayCombo.setSelectedItem(tableData.model.getValueAt(row, 2));
        String[] timeParts = String.valueOf(tableData.model.getValueAt(row, 3)).split(" - ");
        if (timeParts.length == 2) {
            startCombo.setSelectedItem(timeParts[0].trim());
            endCombo.setSelectedItem(timeParts[1].trim());
        }
        roomField.setText(String.valueOf(tableData.model.getValueAt(row, 4)));
    }

    private static void submitScheduleChange(AppShell shell, ScheduleTableData tableData,
            JTextField subjectField, JTextField roomField, JTextArea reasonArea, JComboBox<DayOfWeek> dayCombo,
            JComboBox<String> startCombo, JComboBox<String> endCombo) {
        int row = tableData.table.getSelectedRow();
        if (row < 0) {
            shell.showMessage("Choose a class first.", AppTheme.WARNING);
            shell.refreshView();
            return;
        }

        shell.showResult(shell.getStore().submitScheduleChangeRequest(
                shell.getCurrentUser().getUserId(),
                (Integer) tableData.model.getValueAt(row, 0),
                subjectField.getText(),
                (DayOfWeek) dayCombo.getSelectedItem(),
                shell.parseTimeValue((String) startCombo.getSelectedItem()),
                shell.parseTimeValue((String) endCombo.getSelectedItem()),
                roomField.getText(),
                reasonArea.getText(),
                shell.getCurrentUser().getFullName()
        ));
        shell.refreshView();
    }

    private static final class ScheduleTableData {

        private final DefaultTableModel model;
        private final JTable table;

        private ScheduleTableData(DefaultTableModel model, JTable table) {
            this.model = model;
            this.table = table;
        }
    }
}
