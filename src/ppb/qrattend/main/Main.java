package ppb.qrattend.main;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import ppb.qrattend.app.AppDataStore;
import ppb.qrattend.app.AppShell;
import ppb.qrattend.component.login.PanelCover;
import ppb.qrattend.component.login.PanelLogin;
import ppb.qrattend.db.DatabaseAuthenticationService;
import ppb.qrattend.model.AppDomain;
import ppb.qrattend.model.ModelUser;

public class Main extends JFrame {

    private final AppDataStore store = new AppDataStore();
    private final DatabaseAuthenticationService authenticationService = DatabaseAuthenticationService.fromDefaultConfig();
    private final CardLayout layout = new CardLayout();
    private final JPanel root = new JPanel(layout);
    private final JPanel workspaceHost = new JPanel(new BorderLayout());

    private final PanelCover cover = new PanelCover();
    private final PanelLogin login = new PanelLogin();
    private AppDomain.UserRole currentRole = AppDomain.UserRole.TEACHER;

    public Main() {
        configureFrame();
        buildLoginScreen();
        workspaceHost.setOpaque(false);
        root.add(createLoginScreen(), "login");
        root.add(workspaceHost, "workspace");
        setContentPane(root);
        layout.show(root, "login");
    }

    private void configureFrame() {
        setTitle("QR Attend");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setMinimumSize(new java.awt.Dimension(1180, 760));
        setLocationRelativeTo(null);
    }

    private void buildLoginScreen() {
        login.setLoginHandler(this::attemptLogin);
        cover.setToggleAction(e -> toggleRole());
        applyRole();
    }

    private JPanel createLoginScreen() {
        JPanel screen = new JPanel(new GridLayout(1, 2));
        screen.add(cover);
        screen.add(login);
        return screen;
    }

    private void toggleRole() {
        currentRole = currentRole == AppDomain.UserRole.ADMIN ? AppDomain.UserRole.TEACHER : AppDomain.UserRole.ADMIN;
        applyRole();
    }

    private void applyRole() {
        cover.setRole(currentRole);
        login.setRole(currentRole);
    }

    private void attemptLogin(String email, String password, AppDomain.UserRole role) {
        if (!authenticationService.isDatabaseLoginEnabled()) {
            login.showStatus("Sign-in is not ready yet. Check the school system setup.", true);
            return;
        }

        DatabaseAuthenticationService.AuthenticationResult result = authenticationService.authenticate(email, password, role);
        if (!result.isSuccess()) {
            login.showStatus(result.getMessage(), true);
            return;
        }
        login.showStatus("Signed in.", false);
        showWorkspace(store.prepareWorkspaceUser(result.getUser()));
    }

    private void showWorkspace(ModelUser authenticated) {
        workspaceHost.removeAll();
        workspaceHost.add(new AppShell(store, authenticated, this::showLogin), BorderLayout.CENTER);
        workspaceHost.revalidate();
        workspaceHost.repaint();
        layout.show(root, "workspace");
    }

    private void showLogin() {
        login.resetFields();
        applyRole();
        layout.show(root, "login");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}
