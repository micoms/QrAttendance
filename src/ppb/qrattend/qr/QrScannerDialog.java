package ppb.qrattend.qr;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.google.zxing.NotFoundException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public final class QrScannerDialog extends JDialog {

    private final Consumer<String> onDetected;
    private final JLabel statusLabel = new JLabel("Show the QR code to the camera or choose a photo.", SwingConstants.CENTER);
    private Webcam webcam;
    private Timer scanTimer;

    private QrScannerDialog(Window owner, Consumer<String> onDetected) {
        super(owner, "Scan QR Code", ModalityType.APPLICATION_MODAL);
        this.onDetected = onDetected;
        setLayout(new BorderLayout(12, 12));
        setSize(760, 620);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUi();
    }

    public static void open(Component parent, Consumer<String> onDetected) {
        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        QrScannerDialog dialog = new QrScannerDialog(owner, onDetected);
        dialog.setVisible(true);
    }

    private void buildUi() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(statusLabel, BorderLayout.CENTER);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton imageButton = new JButton("Use Photo");
        JButton closeButton = new JButton("Done");
        imageButton.addActionListener(e -> openImageFile());
        closeButton.addActionListener(e -> dispose());
        actionRow.add(imageButton);
        actionRow.add(closeButton);
        footer.add(actionRow, BorderLayout.EAST);

        add(buildCameraPanel(), BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    private Component buildCameraPanel() {
        try {
            webcam = Webcam.getDefault();
            if (webcam == null) {
                return emptyState("No camera was found. Choose a photo instead.");
            }
            webcam.setViewSize(new Dimension(640, 480));
            WebcamPanel panel = new WebcamPanel(webcam, new Dimension(700, 520), true);
            panel.setFillArea(true);
            panel.setMirrored(true);
            panel.setFPSDisplayed(true);
            panel.setDisplayDebugInfo(false);
            panel.setImageSizeDisplayed(false);
            startScannerLoop();
            return panel;
        } catch (RuntimeException ex) {
            return emptyState("Couldn't open the camera. You can choose a photo instead.");
        }
    }

    private JPanel emptyState(String message) {
        JPanel panel = new JPanel(new BorderLayout());
        JLabel label = new JLabel("<html><div style='text-align:center;padding:40px;'>" + message + "</div></html>", SwingConstants.CENTER);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private void startScannerLoop() {
        scanTimer = new Timer(350, e -> scanCurrentFrame());
        scanTimer.start();
    }

    private void scanCurrentFrame() {
        if (webcam == null || !webcam.isOpen()) {
            return;
        }
        BufferedImage frame = webcam.getImage();
        if (frame == null) {
            return;
        }
        try {
            String qrValue = QrCodeService.decodeQrImage(frame);
            statusLabel.setText("QR code found.");
            finish(qrValue);
        } catch (NotFoundException ex) {
            statusLabel.setText("Looking for a QR code...");
        } catch (RuntimeException ex) {
            statusLabel.setText("Camera problem. Try again or use a photo.");
        }
    }

    private void openImageFile() {
        JFileChooser chooser = new JFileChooser();
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        try {
            String qrValue = QrCodeService.decodeQrFile(path);
            finish(qrValue);
        } catch (NotFoundException ex) {
            JOptionPane.showMessageDialog(this, "No QR code was found in that photo.", "Scan QR Code", JOptionPane.WARNING_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Couldn't open that photo.", "Scan QR Code", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void finish(String qrValue) {
        if (scanTimer != null) {
            scanTimer.stop();
        }
        if (webcam != null) {
            try {
                webcam.close();
            } catch (RuntimeException ex) {
                // Ignore close errors while the dialog is finishing.
            }
        }
        if (onDetected != null) {
            onDetected.accept(qrValue);
        }
        dispose();
    }

    @Override
    public void dispose() {
        if (scanTimer != null) {
            scanTimer.stop();
        }
        if (webcam != null) {
            try {
                webcam.close();
            } catch (RuntimeException ex) {
                // Ignore close errors during disposal.
            }
        }
        super.dispose();
    }
}
