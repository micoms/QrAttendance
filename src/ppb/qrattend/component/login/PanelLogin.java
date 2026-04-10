package ppb.qrattend.component.login;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;
import net.miginfocom.swing.MigLayout;
import ppb.qrattend.model.ModelUser;
import ppb.qrattend.swing.Button;
import ppb.qrattend.swing.MyPasswordField;
import ppb.qrattend.swing.MyTextField;

public class PanelLogin extends javax.swing.JLayeredPane {

    private ModelUser user;

    public PanelLogin(ActionListener eventTeacher) {
        initComponents();
        initTeacher(eventTeacher);
        initAdmin();
        admin.setVisible(false);
        teacher.setVisible(true);
    }

    private void initTeacher(ActionListener eventTeacher) {
        teacher.setLayout(new MigLayout("wrap", "push[center]push", "push[]25[]10[]10[]25[]push"));
        JLabel label = new JLabel("Sign In as a Teacher");
        label.setFont(new Font("verdana", 1, 30));
        label.setForeground(new Color(75, 148, 111));
        teacher.add(label);
        MyTextField txtMail = new MyTextField();
        txtMail.setPrefixIcon(new ImageIcon(getClass().getResource("/ppb/qrattend/icon/mail.png")));
        txtMail.setHint("Email");
        teacher.add(txtMail, "w 60%");
        MyPasswordField txtPass = new MyPasswordField();
        txtPass.setPrefixIcon(new ImageIcon(getClass().getResource("/ppb/qrattend/icon/pass.png")));
        txtPass.setHint("Password");
        teacher.add(txtPass, "w 60%");
        JButton cmdForget = new JButton("Forgot Password ?");
        cmdForget.setForeground(new Color(100, 100, 100));
        cmdForget.setFont(new Font("verdana", 1, 12));
        cmdForget.setContentAreaFilled(false);
        cmdForget.setCursor(new Cursor(Cursor.HAND_CURSOR));
        cmdForget.setBorder(new EmptyBorder(5, 0, 5, 0));
        teacher.add(cmdForget);
        Button cmd = new Button();
        cmd.setBackground(new Color(75, 148, 111));
        cmd.setForeground(new Color(250, 250, 250));
        cmd.setText("Sign In");
        teacher.add(cmd, "w 40%, h 40");
        cmd.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String email = txtMail.getText().trim();
                String password = String.valueOf(txtPass.getPassword());
                user = new ModelUser(0, email, password, "teacher");
                if (eventTeacher != null) {
                    eventTeacher.actionPerformed(e);
                }
            }
        });
    }

    private void initAdmin() {
        admin.setLayout(new MigLayout("wrap", "push[center]push", "push[]25[]10[]10[]25[]push"));
        JLabel label = new JLabel("Sign In as a Admin");
        label.setFont(new Font("verdana", 1, 30));
        label.setForeground(new Color(75, 148, 111));
        admin.add(label);
        MyTextField txtMail = new MyTextField();
        txtMail.setPrefixIcon(new ImageIcon(getClass().getResource("/ppb/qrattend/icon/mail.png")));
        txtMail.setHint("Email");
        admin.add(txtMail, "w 60%");
        MyPasswordField txtPass = new MyPasswordField();
        txtPass.setPrefixIcon(new ImageIcon(getClass().getResource("/ppb/qrattend/icon/pass.png")));
        txtPass.setHint("Password");
        admin.add(txtPass, "w 60%");
        JButton cmdForget = new JButton("Forgot Password ?");
        cmdForget.setForeground(new Color(100, 100, 100));
        cmdForget.setFont(new Font("verdana", 1, 12));
        cmdForget.setContentAreaFilled(false);
        cmdForget.setCursor(new Cursor(Cursor.HAND_CURSOR));
        cmdForget.setBorder(new EmptyBorder(5, 0, 5, 0));
        admin.add(cmdForget);
        Button cmd = new Button();
        cmd.setBackground(new Color(75, 148, 111));
        cmd.setForeground(new Color(250, 250, 250));
        cmd.setText("Sign In");
        admin.add(cmd, "w 40%, h 40");
        cmd.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String email = txtMail.getText().trim();
                String password = String.valueOf(txtPass.getPassword());
                user = new ModelUser(0, email, password, "admin");
            }
        });
    }

    public void showAdmin(boolean show) {
        if (show) {
            admin.setVisible(false);
            teacher.setVisible(true);
        } else {
            admin.setVisible(true);
            teacher.setVisible(false);
        }
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        teacher = new javax.swing.JPanel();
        admin = new javax.swing.JPanel();

        setLayout(new java.awt.CardLayout());

        teacher.setBackground(new java.awt.Color(255, 255, 255));

        javax.swing.GroupLayout teacherLayout = new javax.swing.GroupLayout(teacher);
        teacher.setLayout(teacherLayout);
        teacherLayout.setHorizontalGroup(
            teacherLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        teacherLayout.setVerticalGroup(
            teacherLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );

        add(teacher, "card3");

        admin.setBackground(new java.awt.Color(255, 255, 255));

        javax.swing.GroupLayout adminLayout = new javax.swing.GroupLayout(admin);
        admin.setLayout(adminLayout);
        adminLayout.setHorizontalGroup(
            adminLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        adminLayout.setVerticalGroup(
            adminLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );

        add(admin, "card2");
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel admin;
    private javax.swing.JPanel teacher;
    // End of variables declaration//GEN-END:variables

    /**
     * @return the user
     */
    public ModelUser getUser() {
        return user;
    }
}
