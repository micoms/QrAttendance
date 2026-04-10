package ppb.qrattend.component.login;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;
import javax.swing.JLabel;
import net.miginfocom.swing.MigLayout;
import ppb.qrattend.swing.ButtonOutLine;

public class PanelCover extends javax.swing.JPanel {

    private final DecimalFormat df = new DecimalFormat("##0.###");
    private ActionListener event;
    private MigLayout layout;
    private JLabel title;
    private JLabel description;
    private JLabel description1;
    private ButtonOutLine button;
    private boolean isLogin;

    public PanelCover() {
        initComponents();
        setOpaque(false);
        layout = new MigLayout("wrap, fill", "[center]", "push[]25[]10[]25[]push");
        setLayout(layout);
        init();
    }

    private void init() {
        title = new JLabel("Welcome Back!");
        title.setFont(new Font("verdana", 1, 30));
        title.setForeground(new Color(245, 245, 245));
        add(title);
        description = new JLabel("Login to access the");
        description.setFont(new Font("verdana", 1, 15));
        description.setForeground(new Color(245, 245, 245));
        add(description);
        description1 = new JLabel("Attendance");
        description1.setFont(new Font("verdana", 1, 15));
        description1.setForeground(new Color(245, 245, 245));
        add(description1);
        button = new ButtonOutLine();
        button.setBackground(new Color(255, 255, 255));
        button.setForeground(new Color(255, 255, 255));
        button.setText("Login as Admin");
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
                event.actionPerformed(ae);
            }
        });
        add(button, "w 60%, h 40");
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    @Override
    public void paintComponent(Graphics grphcs) {
        Graphics2D g2 = (Graphics2D) grphcs;
        GradientPaint gra = new GradientPaint(0, 0, new Color(60, 153, 106), 0, getHeight(), new Color(42, 87, 64));
        g2.setPaint(gra);
        g2.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponents(grphcs);
    }

    public void addEvent(ActionListener event) {
        this.event = event;
    }

    public void teacherLeft(double v) {
        v = Double.valueOf(df.format(v));
        teacher(false);
        layout.setComponentConstraints(title, "pad 0 " + v + "% 0 " + v + "%");
        layout.setComponentConstraints(description, "pad 0 " + v + "% 0 " + v + "%");
        layout.setComponentConstraints(description1, "pad 0 " + v + "% 0 " + v + "%");
    }

    public void teacherRight(double v) {
        v = Double.valueOf(df.format(v));
        teacher(false);
        layout.setComponentConstraints(title, "pad 0 " + v + "% 0 " + v + "%");
        layout.setComponentConstraints(description, "pad 0 " + v + "% 0 " + v + "%");
        layout.setComponentConstraints(description1, "pad 0 " + v + "% 0 " + v + "%");
    }

    public void adminLeft(double v) {
        v = Double.valueOf(df.format(v));
        teacher(true);
        layout.setComponentConstraints(title, "pad 0 -" + v + "% 0 -" + v + "%");
        layout.setComponentConstraints(description, "pad 0 -" + v + "% 0 -" + v + "%");
        layout.setComponentConstraints(description1, "pad 0 -" + v + "% 0 -" + v + "%");
    }

    public void adminRight(double v) {
        v = Double.valueOf(df.format(v));
        teacher(true);
        layout.setComponentConstraints(title, "pad 0 -" + v + "% 0 -" + v + "%");
        layout.setComponentConstraints(description, "pad 0 -" + v + "% 0 -" + v + "%");
        layout.setComponentConstraints(description1, "pad 0 -" + v + "% 0 -" + v + "%");
    }

    private void teacher(boolean login) {
        if (this.isLogin != login) {
            if (login) {
                title.setText("Hello, Admin!");
                description.setText("Login to manage the");
                description1.setText("System");
                button.setText("Login as Teacher");
            } else {
                title.setText("Welcome Back!");
                description.setText("Login to access the");
                description1.setText("attendance");
                button.setText("Login as Admin");
            }
            this.isLogin = login;
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
