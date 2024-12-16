package com.reversi.stand;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;

public class ReversiServer extends JFrame {
    private GameState gameState;
    private static final int BOARD_SIZE = 8;
    private static final int CELL_SIZE = 60;
    private static final int MARGIN = 30; // 为标签预留边距
    private JPanel boardPanel;
    private JLabel statusLabel;
    private JPanel controlPanel;
    private static final Color DARK_CELL = new Color(0xc1924e);
    private static final Color LIGHT_CELL = new Color(0xcaa570);
    private static final Color HINT_COLOR = new Color(0x80ffcf31, true); // 添加透明度的绿色提示点
    private JLabel timerLabel;
    
    public ReversiServer() {
        gameState = new GameState();
        initializeUI();
        
        // 添加窗口关闭事件处理
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                gameState.close();
                System.exit(0);
            }
        });
    }
    
    private void initializeUI() {
        setTitle("黑白棋");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));  // 添加组件间距
        
        // 创建状态标签
        statusLabel = new JLabel("黑方回合", SwingConstants.CENTER);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));  // 添加内边距
        
        // 创建计时器标签
        timerLabel = new JLabel("黑方: 30s  白方: 30s");
        timerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        timerLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        
        // 创建顶部面板来容纳状态标签和计时器标签
        JPanel topPanel = new JPanel(new GridLayout(2, 1));
        topPanel.add(statusLabel);
        topPanel.add(timerLabel);
        add(topPanel, BorderLayout.NORTH);
        
        // 创建棋盘面板
        boardPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawBoard(g);
                drawCoordinates(g);
                drawPieces(g);
            }
        };
        
        // 计算棋盘面板的首选大小
        int boardWidth = BOARD_SIZE * CELL_SIZE + MARGIN * 2;
        int boardHeight = BOARD_SIZE * CELL_SIZE + MARGIN * 2;
        boardPanel.setPreferredSize(new Dimension(boardWidth, boardHeight));
        boardPanel.setBackground(Color.WHITE);
        
        // 添加鼠标点击事件
        boardPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = (e.getY() - MARGIN) / CELL_SIZE;
                int col = (e.getX() - MARGIN) / CELL_SIZE;
                if (row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE) {
                    handleMove(row, col);
                }
            }
        });
        
        // 创建一个包装面板来居中显示棋盘
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        centerPanel.add(boardPanel);
        add(centerPanel, BorderLayout.CENTER);
        
        // 添加控制面板
        controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout());
        controlPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));  // 添加内边距

        JButton newGameButton = new JButton("新游戏");
        JButton saveButton = new JButton("保存");
        JButton loadButton = new JButton("读取");
        JButton undoButton = new JButton("悔棋");

        newGameButton.addActionListener(e -> {
            gameState.reset();
            boardPanel.repaint();
            updateStatus();
        });

        saveButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    gameState.saveToFile(fileChooser.getSelectedFile().getPath());
                    JOptionPane.showMessageDialog(this, "游戏已保存");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "保存失败: " + ex.getMessage(), 
                        "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        loadButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    gameState.loadFromFile(fileChooser.getSelectedFile().getPath());
                    boardPanel.repaint();
                    updateStatus();
                    JOptionPane.showMessageDialog(this, "游戏已加载");
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "加载失败: " + ex.getMessage(), 
                        "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        undoButton.addActionListener(e -> {
            if (gameState.undo()) {
                boardPanel.repaint();
                updateStatus();
            } else {
                JOptionPane.showMessageDialog(this, 
                    "无法悔棋！", 
                    "提示", 
                    JOptionPane.INFORMATION_MESSAGE);
            }
        });

        controlPanel.add(newGameButton);
        controlPanel.add(saveButton);
        controlPanel.add(loadButton);
        controlPanel.add(undoButton);
        add(controlPanel, BorderLayout.SOUTH);
        
        // 设置计时器回调
        gameState.setTimerCallback(new GameState.TimerCallback() {
            @Override
            public void onTimeUpdate(int blackTime, int whiteTime) {
                SwingUtilities.invokeLater(() -> {
                    timerLabel.setText(String.format("黑方: %ds  白方: %ds", blackTime, whiteTime));
                });
            }

            @Override
            public void onTimeout(boolean isBlackTimeout) {
                SwingUtilities.invokeLater(() -> {
                    String message = isBlackTimeout ? "黑方超时，白方胜利！" : "白方超时，黑方胜利���";
                    JOptionPane.showMessageDialog(ReversiServer.this, message);
                    gameState.reset();
                    boardPanel.repaint();
                    updateStatus();
                });
            }
        });
        
        // 调整窗口大小以适应所有组件
        int windowWidth = boardWidth + 40;  // 额外空间用于边框和边距
        int windowHeight = boardHeight + 150;  // 增加高度，为状态栏、计时器和控制面板预留更多空间
        setSize(windowWidth, windowHeight);
        setLocationRelativeTo(null);  // 窗口居中显示
        
        // 防止窗口被调整得太小
        setMinimumSize(new Dimension(windowWidth, windowHeight));
    }
    
    private void drawBoard(Graphics g) {
        // 绘制棋盘格子
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                int x = j * CELL_SIZE + MARGIN;
                int y = i * CELL_SIZE + MARGIN;
                g.setColor((i + j) % 2 == 0 ? LIGHT_CELL : DARK_CELL);
                g.fillRect(x, y, CELL_SIZE, CELL_SIZE);
            }
        }
        
        // 绘制棋盘边框
        g.setColor(Color.BLACK);
        g.drawRect(MARGIN, MARGIN, BOARD_SIZE * CELL_SIZE, BOARD_SIZE * CELL_SIZE);
        
        // 绘制可落子位置的提示
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (gameState.isValidMove(i, j)) {
                    int x = j * CELL_SIZE + MARGIN + CELL_SIZE / 2;
                    int y = i * CELL_SIZE + MARGIN + CELL_SIZE / 2;
                    int diameter = (int)(CELL_SIZE * 0.4);
                    
                    // 使用2D图形来实现更好的透明效果
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setColor(HINT_COLOR);
                    g2d.fillOval(x - diameter/2, y - diameter/2, diameter, diameter);
                }
            }
        }
    }
    
    private void drawCoordinates(Graphics g) {
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 16));
        FontMetrics metrics = g.getFontMetrics();
        
        // 绘制字母坐标（A-H）
        for (int i = 0; i < BOARD_SIZE; i++) {
            String letter = String.valueOf((char)('A' + i));
            int x = i * CELL_SIZE + MARGIN + CELL_SIZE/2 - metrics.stringWidth(letter)/2;
            g.drawString(letter, x, MARGIN - 10);
        }
        
        // 绘制数字坐标（1-8）
        for (int i = 0; i < BOARD_SIZE; i++) {
            String number = String.valueOf(i + 1);
            int y = i * CELL_SIZE + MARGIN + CELL_SIZE/2 + metrics.getAscent()/2;
            g.drawString(number, MARGIN - metrics.stringWidth(number) - 10, y);
        }
    }
    
    private void drawPieces(Graphics g) {
        int[][] board = gameState.getBoard();
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] != 0) {
                    int x = j * CELL_SIZE + MARGIN + CELL_SIZE / 2;
                    int y = i * CELL_SIZE + MARGIN + CELL_SIZE / 2;
                    int diameter = (int)(CELL_SIZE * 0.8);
                    
                    // 添加阴影效果
                    g.setColor(new Color(0, 0, 0, 50));
                    g.fillOval(x - diameter/2 + 2, y - diameter/2 + 2, diameter, diameter);
                    
                    g.setColor(board[i][j] == 1 ? Color.BLACK : Color.WHITE);
                    g.fillOval(x - diameter/2, y - diameter/2, diameter, diameter);
                    g.setColor(Color.BLACK);
                    g.drawOval(x - diameter/2, y - diameter/2, diameter, diameter);
                }
            }
        }
    }
    
    private void handleMove(int row, int col) {
        if (gameState.makeMove(row, col)) {
            boardPanel.repaint();
            updateStatus();
            
            // 检查游戏是否结束
            if (!gameState.hasValidMoves()) {
                // 切换玩家
                gameState.toggleTurn();
                
                // 检查另一个玩家是否合法移动
                if (!gameState.hasValidMoves()) {
                    showGameResult();
                } else {
                    updateStatus();
                    boardPanel.repaint();
                }
            }
        }
    }
    
    private void showGameResult() {
        int[] score = calculateScore();
        String message;
        if (score[0] > score[1]) {
            message = "游戏结束！黑方胜利！\n黑方: " + score[0] + " 白方: " + score[1];
        } else if (score[1] > score[0]) {
            message = "游戏结束！白方胜！\n黑方: " + score[0] + " 白方: " + score[1];
        } else {
            message = "游戏结束！平局！\n黑方: " + score[0] + " 白方: " + score[1];
        }
        JOptionPane.showMessageDialog(this, message);
    }
    
    private int[] calculateScore() {
        int[] score = new int[2];  // [黑子数量, 白子数量]
        int[][] board = gameState.getBoard();
        
        for (int i = 0; i < BOARD_SIZE; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] == 1) {  // 黑子
                    score[0]++;
                } else if (board[i][j] == 2) {  // 白子
                    score[1]++;
                }
            }
        }
        return score;
    }
    
    private void updateStatus() {
        int[] score = calculateScore();
        statusLabel.setText(String.format(
            "%s    黑方: %d  白方: %d", 
            gameState.isBlackTurn() ? "黑方回合" : "白方回合",
            score[0], 
            score[1]
        ));
    }
    
    public void start() {
        SwingUtilities.invokeLater(() -> {
            setVisible(true);
        });
    }
} 