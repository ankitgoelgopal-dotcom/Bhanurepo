package com.example.app;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Starter App");
            JTextField name = new JTextField(18);
            JButton btn = new JButton("Say hello");
            JLabel out = new JLabel(" ");
            btn.addActionListener(e -> {
                String n = name.getText().trim();
                out.setText("Hello, " + (n.isEmpty() ? "world" : n) + "!");
            });
            JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 20));
            p.add(name);
            p.add(btn);
            p.add(out);
            f.add(p);
            f.setSize(480, 140);
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);
        });
    }
}
