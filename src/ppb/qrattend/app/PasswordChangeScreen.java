package ppb.qrattend.app;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

final class PasswordChangeScreen {

    private PasswordChangeScreen() {
    }

    static JPanel build(AppShell shell) {
        JPanel page = AppTheme.createPage();
        JTextField newPasswordField = shell.newTextField();
        JTextField confirmField = shell.newTextField();

        JLabel errorLabel = new JLabel(" ");
        errorLabel.setForeground(AppTheme.DANGER);
        errorLabel.setFont(AppTheme.bodyFont(12));

        JButton submitButton = new JButton("Set New Password");
        AppTheme.stylePrimaryButton(submitButton);
        submitButton.addActionListener(event -> {
            String newPw = newPasswordField.getText();
            String confirm = confirmField.getText();
            if (newPw.length() < 8) {
                errorLabel.setText("Password must be at least 8 characters.");
                return;
            }
            if (!newPw.equals(confirm)) {
                errorLabel.setText("Passwords do not match.");
                return;
            }
            AppStore.ActionResult result = shell.getStore().changePassword(
                    shell.getCurrentUser().getUserId(), newPw);
            if (result.isSuccess()) {
                shell.onPasswordChanged();
                shell.showMessage(result.getMessage(), AppTheme.SUCCESS);
            } else {
                errorLabel.setText(result.getMessage());
            }
        });

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        form.setOpaque(false);
        form.add(shell.labeledField("New Password", newPasswordField));
        form.add(shell.labeledField("Confirm Password", confirmField));
        form.add(shell.labeledField("Action", submitButton));

        List<String> instructions = new ArrayList<>();
        instructions.add("Your account requires a new password before you can continue.");
        instructions.add("Password must be at least 8 characters.");
        instructions.add("Enter the same password in both fields.");

        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        body.add(AppFlowPanels.createSimpleList("Set your password", instructions), BorderLayout.NORTH);
        body.add(form, BorderLayout.CENTER);
        body.add(errorLabel, BorderLayout.SOUTH);

        page.add(shell.createSection("Change Password", "Set a new password to continue.", body));
        return page;
    }
}
