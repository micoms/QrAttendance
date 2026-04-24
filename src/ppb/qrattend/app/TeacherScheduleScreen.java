package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.DayOfWeek;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import ppb.qrattend.model.AppDomain.ScheduleChangeRequest;
import ppb.qrattend.model.AppDomain.ScheduleSlot;
import ppb.qrattend.model.ModelUser;

final class TeacherScheduleScreen {

    private TeacherScheduleScreen() {
    }

    static JPanel build(AppShell shell) {
        AppDataStore store = shell.getStore();
        ModelUser user = shell.getCurrentUser();
        JPanel page = AppTheme.createPage();

        DefaultTableModel scheduleModel = shell.createTableModel("ID", "Subject", "Day", "Time", "Room", "Status");
        for (ScheduleSlot slot : store.getSchedulesForTeacher(user.getUserId())) {
            scheduleModel.addRow(new Object[]{
                slot.getId(),
                slot.getSubjectName(),
                slot.getDay(),
                slot.getTimeLabel(),
                slot.getRoom(),
                shell.friendlyStatus(slot.getStatus())
            });
        }
        JTable scheduleTable = new JTable(scheduleModel);

        JTextField subjectField = shell.newTextField();
        JTextField roomField = shell.newTextField();
        JTextArea reasonArea = shell.newTextArea();
        JComboBox<DayOfWeek> dayCombo = shell.newDayCombo();
        JComboBox<String> startCombo = shell.newTimeCombo();
        JComboBox<String> endCombo = shell.newTimeCombo();

        JButton loadSelected = new JButton("Use Selected Class");
        AppTheme.styleSecondaryButton(loadSelected);
        loadSelected.addActionListener(event -> {
            int row = scheduleTable.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(shell, "Choose a class first.", "My Schedule", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            subjectField.setText(String.valueOf(scheduleModel.getValueAt(row, 1)));
            dayCombo.setSelectedItem(scheduleModel.getValueAt(row, 2));
            String[] timeParts = String.valueOf(scheduleModel.getValueAt(row, 3)).split(" - ");
            if (timeParts.length == 2) {
                startCombo.setSelectedItem(timeParts[0].trim());
                endCombo.setSelectedItem(timeParts[1].trim());
            }
            roomField.setText(String.valueOf(scheduleModel.getValueAt(row, 4)));
        });

        JButton submit = new JButton("Ask for Change");
        AppTheme.stylePrimaryButton(submit);
        submit.addActionListener(event -> {
            int row = scheduleTable.getSelectedRow();
            if (row < 0) {
                shell.showMessage("Choose a class first.", AppTheme.WARNING);
                shell.refreshView();
                return;
            }

            shell.showResult(store.submitScheduleChangeRequest(
                    user.getUserId(),
                    (Integer) scheduleModel.getValueAt(row, 0),
                    subjectField.getText(),
                    (DayOfWeek) dayCombo.getSelectedItem(),
                    shell.parseTimeValue((String) startCombo.getSelectedItem()),
                    shell.parseTimeValue((String) endCombo.getSelectedItem()),
                    roomField.getText(),
                    reasonArea.getText(),
                    user.getFullName()
            ));
            shell.refreshView();
        });

        JPanel scheduleBody = new JPanel(new BorderLayout(0, 12));
        scheduleBody.setOpaque(false);
        scheduleBody.add(shell.wrapTable(scheduleTable), BorderLayout.CENTER);
        JPanel correctionGrid = new JPanel(new GridLayout(3, 2, 12, 12));
        correctionGrid.setOpaque(false);
        correctionGrid.add(shell.labeledField("Subject", subjectField));
        correctionGrid.add(shell.labeledField("Room", roomField));
        correctionGrid.add(shell.labeledField("Day", dayCombo));
        correctionGrid.add(shell.labeledField("Start Time", startCombo));
        correctionGrid.add(shell.labeledField("End Time", endCombo));
        correctionGrid.add(shell.labeledField("Reason", new javax.swing.JScrollPane(reasonArea)));
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        actionRow.setOpaque(false);
        actionRow.add(loadSelected);
        actionRow.add(submit);
        JPanel correctionPanel = new JPanel(new BorderLayout(0, 12));
        correctionPanel.setOpaque(false);
        correctionPanel.add(correctionGrid, BorderLayout.CENTER);
        correctionPanel.add(actionRow, BorderLayout.SOUTH);
        scheduleBody.add(correctionPanel, BorderLayout.SOUTH);
        page.add(shell.createSection("My Schedule", "Choose a class, update the form, and send the change to the admin.", scheduleBody));
        page.add(Box.createVerticalStrut(16));

        DefaultTableModel requestModel = shell.createTableModel("ID", "Old", "Requested", "Status", "Reason", "Reviewed By");
        for (ScheduleChangeRequest request : store.getScheduleRequestsForTeacher(user.getUserId())) {
            requestModel.addRow(new Object[]{
                request.getId(),
                request.getOldValue(),
                request.getRequestedValue(),
                request.getStatus().getLabel(),
                request.getReason(),
                request.getReviewedBy() == null ? "-" : request.getReviewedBy()
            });
        }
        JTable requestTable = new JTable(requestModel);
        page.add(shell.createSection("Schedule Requests", "Check whether your schedule requests are still waiting, approved, or rejected.", shell.wrapTable(requestTable)));
        return page;
    }
}
