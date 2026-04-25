package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import ppb.qrattend.model.CoreModels.Room;
import ppb.qrattend.model.CoreModels.Section;
import ppb.qrattend.model.CoreModels.Subject;

final class SectionsScreen {

    private SectionsScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        page.add(buildSectionPanel(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildSubjectPanel(shell));
        page.add(Box.createVerticalStrut(16));
        page.add(buildRoomPanel(shell));
        return page;
    }

    private static JPanel buildSectionPanel(AppShell shell) {
        JTextField nameField = shell.newTextField();
        JButton saveButton = new JButton("Save Section");
        AppTheme.stylePrimaryButton(saveButton);
        saveButton.addActionListener(event -> {
            shell.showResult(shell.getStore().addSection(nameField.getText()));
            shell.refreshView();
        });

        List<Section> savedSections = shell.getStore().getSections();

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        form.setOpaque(false);
        form.add(shell.labeledField("Section name", nameField));
        form.add(shell.labeledField("Action", saveButton));

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(form, BorderLayout.NORTH);
        body.add(buildSectionList(shell, savedSections), BorderLayout.CENTER);
        return shell.createSection("Sections",
                "Save each section once. Students and schedules will reuse this list.",
                body);
    }

    private static JPanel buildSubjectPanel(AppShell shell) {
        JTextField nameField = shell.newTextField();
        JButton saveButton = new JButton("Save Subject");
        AppTheme.stylePrimaryButton(saveButton);
        saveButton.addActionListener(event -> {
            shell.showResult(shell.getStore().addSubject(nameField.getText()));
            shell.refreshView();
        });

        List<Subject> savedSubjects = shell.getStore().getSubjects();

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        form.setOpaque(false);
        form.add(shell.labeledField("Subject name", nameField));
        form.add(shell.labeledField("Action", saveButton));

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(form, BorderLayout.NORTH);
        body.add(buildSubjectList(shell, savedSubjects), BorderLayout.CENTER);
        return shell.createSection("Subjects",
                "Save school subjects once so schedules use dropdowns instead of typing.",
                body);
    }

    private static JPanel buildRoomPanel(AppShell shell) {
        JTextField nameField = shell.newTextField();
        JButton saveButton = new JButton("Save Room");
        AppTheme.stylePrimaryButton(saveButton);
        saveButton.addActionListener(event -> {
            shell.showResult(shell.getStore().addRoom(nameField.getText()));
            shell.refreshView();
        });

        List<Room> savedRooms = shell.getStore().getRooms();

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        form.setOpaque(false);
        form.add(shell.labeledField("Room name", nameField));
        form.add(shell.labeledField("Action", saveButton));

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(form, BorderLayout.NORTH);
        body.add(buildRoomList(shell, savedRooms), BorderLayout.CENTER);
        return shell.createSection("Rooms",
                "Save rooms once so admins can pick them from the list when making schedules.",
                body);
    }

    // --- Section list with Edit / Delete ---

    private static JPanel buildSectionList(AppShell shell, List<Section> sections) {
        JPanel panel = new AppTheme.RoundedPanel(AppTheme.RADIUS_MD, AppTheme.SURFACE_ALT);
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                new AppTheme.RoundedBorder(AppTheme.RADIUS_MD, AppTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)
        ));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Saved items");
        titleLabel.setFont(AppTheme.headlineFont(14));
        titleLabel.setForeground(AppTheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        body.add(titleLabel);
        body.add(Box.createVerticalStrut(8));

        if (sections == null || sections.isEmpty()) {
            JLabel emptyLabel = new JLabel("Nothing here yet.");
            emptyLabel.setFont(AppTheme.bodyFont(12));
            emptyLabel.setForeground(AppTheme.TEXT_MUTED);
            emptyLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            body.add(emptyLabel);
        } else {
            for (Section section : sections) {
                body.add(buildSectionRow(shell, section));
                body.add(Box.createVerticalStrut(4));
            }
        }

        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel buildSectionRow(AppShell shell, Section section) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel(section.name());
        nameLabel.setFont(AppTheme.bodyFont(12));
        nameLabel.setForeground(AppTheme.TEXT_PRIMARY);

        JTextField editField = new JTextField(section.name(), 16);
        AppTheme.styleField(editField);
        editField.setVisible(false);

        JButton editButton = new JButton("Edit");
        AppTheme.styleSecondaryButton(editButton);

        JButton saveButton = new JButton("Save");
        AppTheme.stylePrimaryButton(saveButton);
        saveButton.setVisible(false);

        JButton deleteButton = new JButton("Delete");
        AppTheme.styleDangerButton(deleteButton);

        editButton.addActionListener(event -> {
            nameLabel.setVisible(false);
            editField.setVisible(true);
            editButton.setVisible(false);
            saveButton.setVisible(true);
            row.revalidate();
            row.repaint();
        });

        saveButton.addActionListener(event -> {
            shell.showResult(shell.getStore().renameSection(section.id(), editField.getText()));
            shell.refreshView();
        });

        deleteButton.addActionListener(event -> {
            shell.showResult(shell.getStore().deleteSection(section.id()));
            shell.refreshView();
        });

        row.add(nameLabel);
        row.add(editField);
        row.add(editButton);
        row.add(saveButton);
        row.add(deleteButton);
        return row;
    }

    // --- Subject list with Edit / Delete ---

    private static JPanel buildSubjectList(AppShell shell, List<Subject> subjects) {
        JPanel panel = new AppTheme.RoundedPanel(AppTheme.RADIUS_MD, AppTheme.SURFACE_ALT);
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                new AppTheme.RoundedBorder(AppTheme.RADIUS_MD, AppTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)
        ));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Saved items");
        titleLabel.setFont(AppTheme.headlineFont(14));
        titleLabel.setForeground(AppTheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        body.add(titleLabel);
        body.add(Box.createVerticalStrut(8));

        if (subjects == null || subjects.isEmpty()) {
            JLabel emptyLabel = new JLabel("Nothing here yet.");
            emptyLabel.setFont(AppTheme.bodyFont(12));
            emptyLabel.setForeground(AppTheme.TEXT_MUTED);
            emptyLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            body.add(emptyLabel);
        } else {
            for (Subject subject : subjects) {
                body.add(buildSubjectRow(shell, subject));
                body.add(Box.createVerticalStrut(4));
            }
        }

        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel buildSubjectRow(AppShell shell, Subject subject) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel(subject.name());
        nameLabel.setFont(AppTheme.bodyFont(12));
        nameLabel.setForeground(AppTheme.TEXT_PRIMARY);

        JTextField editField = new JTextField(subject.name(), 16);
        AppTheme.styleField(editField);
        editField.setVisible(false);

        JButton editButton = new JButton("Edit");
        AppTheme.styleSecondaryButton(editButton);

        JButton saveButton = new JButton("Save");
        AppTheme.stylePrimaryButton(saveButton);
        saveButton.setVisible(false);

        JButton deleteButton = new JButton("Delete");
        AppTheme.styleDangerButton(deleteButton);

        editButton.addActionListener(event -> {
            nameLabel.setVisible(false);
            editField.setVisible(true);
            editButton.setVisible(false);
            saveButton.setVisible(true);
            row.revalidate();
            row.repaint();
        });

        saveButton.addActionListener(event -> {
            shell.showResult(shell.getStore().renameSubject(subject.id(), editField.getText()));
            shell.refreshView();
        });

        deleteButton.addActionListener(event -> {
            shell.showResult(shell.getStore().deleteSubject(subject.id()));
            shell.refreshView();
        });

        row.add(nameLabel);
        row.add(editField);
        row.add(editButton);
        row.add(saveButton);
        row.add(deleteButton);
        return row;
    }

    // --- Room list with Edit / Delete ---

    private static JPanel buildRoomList(AppShell shell, List<Room> rooms) {
        JPanel panel = new AppTheme.RoundedPanel(AppTheme.RADIUS_MD, AppTheme.SURFACE_ALT);
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                new AppTheme.RoundedBorder(AppTheme.RADIUS_MD, AppTheme.BORDER, 1),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)
        ));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Saved items");
        titleLabel.setFont(AppTheme.headlineFont(14));
        titleLabel.setForeground(AppTheme.TEXT_PRIMARY);
        titleLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        body.add(titleLabel);
        body.add(Box.createVerticalStrut(8));

        if (rooms == null || rooms.isEmpty()) {
            JLabel emptyLabel = new JLabel("Nothing here yet.");
            emptyLabel.setFont(AppTheme.bodyFont(12));
            emptyLabel.setForeground(AppTheme.TEXT_MUTED);
            emptyLabel.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            body.add(emptyLabel);
        } else {
            for (Room room : rooms) {
                body.add(buildRoomRow(shell, room));
                body.add(Box.createVerticalStrut(4));
            }
        }

        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel buildRoomRow(AppShell shell, Room room) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel(room.name());
        nameLabel.setFont(AppTheme.bodyFont(12));
        nameLabel.setForeground(AppTheme.TEXT_PRIMARY);

        JTextField editField = new JTextField(room.name(), 16);
        AppTheme.styleField(editField);
        editField.setVisible(false);

        JButton editButton = new JButton("Edit");
        AppTheme.styleSecondaryButton(editButton);

        JButton saveButton = new JButton("Save");
        AppTheme.stylePrimaryButton(saveButton);
        saveButton.setVisible(false);

        JButton deleteButton = new JButton("Delete");
        AppTheme.styleDangerButton(deleteButton);

        editButton.addActionListener(event -> {
            nameLabel.setVisible(false);
            editField.setVisible(true);
            editButton.setVisible(false);
            saveButton.setVisible(true);
            row.revalidate();
            row.repaint();
        });

        saveButton.addActionListener(event -> {
            shell.showResult(shell.getStore().renameRoom(room.id(), editField.getText()));
            shell.refreshView();
        });

        deleteButton.addActionListener(event -> {
            shell.showResult(shell.getStore().deleteRoom(room.id()));
            shell.refreshView();
        });

        row.add(nameLabel);
        row.add(editField);
        row.add(editButton);
        row.add(saveButton);
        row.add(deleteButton);
        return row;
    }
}
