package com.reversi;

import com.reversi.online.OnlineMain;
import com.reversi.stand.ReversiServer;


import javax.swing.*;
import java.awt.*;

public class Game extends JFrame {
    public Game() {
        setTitle("黑白棋");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        initializeUI();
        pack();
        setLocationRelativeTo(null);
    }

    private void initializeUI() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10);

        // 标题
        JLabel titleLabel = new JLabel("黑白棋", SwingConstants.CENTER);
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 24));
        mainPanel.add(titleLabel, gbc);

//         单人游戏按钮
        JButton singlePlayerButton = createStyledButton("单人游戏");
        singlePlayerButton.addActionListener(e -> {
            dispose();
            SwingUtilities.invokeLater(() -> {
                ReversiServer server = new ReversiServer();
                server.start();
            });
        });
        mainPanel.add(singlePlayerButton, gbc);



        // 联机对战按钮
        JButton onlineButton = createStyledButton("联机对战");
        onlineButton.addActionListener(e -> {
            dispose();
            SwingUtilities.invokeLater(() -> {


                String[] options = {"创建房间", "加入房间"};
                int choice = JOptionPane.showOptionDialog(null,
                        "请选择游戏模式",
                        "黑白棋联机版",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        options,
                        options[0]);

                if (choice == 0) {
                    // 创建服务器时，先设置时间
                    String timeStr = showTimeSettingDialog();
                    if (timeStr != null) {
                        int moveTime = Integer.parseInt(timeStr);
                        com.reversi.online.ReversiServer server = new com.reversi.online.ReversiServer(true, null, 8888);
                        server.setMoveTime(moveTime);
                        server.start();
                    }
                } else if (choice == 1) {
                    // 加入游戏
                    String host = JOptionPane.showInputDialog("请输入服务器IP地址:", "localhost");
                    if (host != null && !host.trim().isEmpty()) {
                        new com.reversi.online.ReversiServer(false, host, 8888).start();
                    }
                }



            });
        });
        mainPanel.add(onlineButton, gbc);

        add(mainPanel);
    }

    private static String showTimeSettingDialog() {
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(2, 1, 5, 5));

        JLabel label = new JLabel("请设置每步最大思考时间（秒）：");
        JTextField timeField = new JTextField("30");

        panel.add(label);
        panel.add(timeField);

        int result = JOptionPane.showConfirmDialog(
                null, panel, "时间设置",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            try {
                int time = Integer.parseInt(timeField.getText().trim());
                if (time > 0) {
                    return String.valueOf(time);
                } else {
                    JOptionPane.showMessageDialog(null, "请输入大于0的数字");
                    return showTimeSettingDialog();
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "请输入有效的数字");
                return showTimeSettingDialog();
            }
        }
        return null;
    }


    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("微软雅黑", Font.PLAIN, 16));
        button.setPreferredSize(new Dimension(200, 50));
        button.setBackground(new Color(70, 130, 180));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        return button;
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(() -> {
            new Game().setVisible(true);
        });
    }
}

