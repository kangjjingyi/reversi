package com.reversi.stand;

import javax.swing.Timer;
import java.io.*;
import java.util.*;

public class GameState {
    private int[][] board;
    private boolean isBlackTurn;
    private static final int EMPTY = 0;
    private static final int BLACK = 1;
    private static final int WHITE = 2;
    
    // 八个方向的偏移量：上、下、左、右、左上、右上、左下、右下
    private static final int[][] DIRECTIONS = {
        {-1, 0}, {1, 0}, {0, -1}, {0, 1},
        {-1, -1}, {-1, 1}, {1, -1}, {1, 1}
    };
    
    // 添加历史记录相关的成员变量
    private Stack<GameStateMemento> history;
    private SoundManager soundManager;
    private static final int MAX_MOVE_TIME = 30; // 每步最大思考时间（秒）
    private int blackTimeLeft; // 黑方剩余时间（秒）
    private int whiteTimeLeft; // 白方剩余时间（秒）
    private Timer moveTimer; // 当前回合计时器
    private long moveStartTime; // 当前回合开始时间
    private TimerCallback timerCallback;

    public interface TimerCallback {
        void onTimeUpdate(int blackTime, int whiteTime);
        void onTimeout(boolean isBlackTimeout);
    }

    public GameState() {
        board = new int[8][8];
        history = new Stack<>();
        soundManager = new SoundManager();
        blackTimeLeft = MAX_MOVE_TIME;
        whiteTimeLeft = MAX_MOVE_TIME;
        initializeBoard();
        startMoveTimer();
    }

    public void setTimerCallback(TimerCallback callback) {
        this.timerCallback = callback;
    }

    private void startMoveTimer() {
        if (moveTimer != null) {
            moveTimer.stop();
        }
        moveStartTime = System.currentTimeMillis();
        moveTimer = new Timer(1000, e -> updateTime());
        moveTimer.start();
    }

    private void updateTime() {
        long currentTime = System.currentTimeMillis();
        int elapsedSeconds = (int)((currentTime - moveStartTime) / 1000);
        
        if (isBlackTurn) {
            blackTimeLeft = Math.max(0, MAX_MOVE_TIME - elapsedSeconds);
            if (blackTimeLeft == 0) {
                moveTimer.stop();
                if (timerCallback != null) {
                    timerCallback.onTimeout(true);
                }
            }
        } else {
            whiteTimeLeft = Math.max(0, MAX_MOVE_TIME - elapsedSeconds);
            if (whiteTimeLeft == 0) {
                moveTimer.stop();
                if (timerCallback != null) {
                    timerCallback.onTimeout(false);
                }
            }
        }
        
        if (timerCallback != null) {
            timerCallback.onTimeUpdate(blackTimeLeft, whiteTimeLeft);
        }
    }

    // 内部类用于保存游戏状态
    private static class GameStateMemento {
        private final int[][] board;
        private final boolean isBlackTurn;
        private final int blackCount;
        private final int whiteCount;
        
        public GameStateMemento(int[][] board, boolean isBlackTurn, int blackCount, int whiteCount) {
            this.board = new int[8][8];
            for (int i = 0; i < 8; i++) {
                this.board[i] = Arrays.copyOf(board[i], 8);
            }
            this.isBlackTurn = isBlackTurn;
            this.blackCount = blackCount;
            this.whiteCount = whiteCount;
        }
    }
    
    private void initializeBoard() {
        // 初始化空棋盘
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                board[i][j] = EMPTY;
            }
        }
        
        // 设置初始四个棋子
        board[3][3] = WHITE;
        board[3][4] = BLACK;
        board[4][3] = BLACK;
        board[4][4] = WHITE;
        
        // 黑子先行
        isBlackTurn = true;
    }
    
    public int[][] getBoard() {
        return board;
    }
    
    public boolean isBlackTurn() {
        return isBlackTurn;
    }
    
    // 在落子前保存���前状态
    private void saveCurrentState() {
        int[] score = calculateScore();
        history.push(new GameStateMemento(board, isBlackTurn, score[0], score[1]));
    }
    
    // 计算当前分数
    private int[] calculateScore() {
        int[] score = new int[2];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (board[i][j] == BLACK) {
                    score[0]++;
                } else if (board[i][j] == WHITE) {
                    score[1]++;
                }
            }
        }
        return score;
    }
    
    // 悔棋功能
    public boolean undo() {
        if (history.isEmpty()) {
            return false;
        }
        
        GameStateMemento memento = history.pop();
        board = new int[8][8];
        for (int i = 0; i < 8; i++) {
            board[i] = Arrays.copyOf(memento.board[i], 8);
        }
        isBlackTurn = memento.isBlackTurn;
        return true;
    }
    
    // 修改makeMove方法以支持历史记录
    public boolean makeMove(int row, int col) {
        if (!isValidMove(row, col)) {
            return false;
        }

        // 保存当前状态
        saveCurrentState();

        int currentColor = isBlackTurn ? BLACK : WHITE;
        board[row][col] = currentColor;
        
        // 播放落子音效
        soundManager.playMoveSound();
        
        boolean hasFlipped = false;
        // 在所有方向上翻转棋子
        for (int[] direction : DIRECTIONS) {
            if (flipPieces(row, col, direction[0], direction[1])) {
                hasFlipped = true;
            }
        }
        
        // 如果有翻转棋子，播放翻转音效
        if (hasFlipped) {
            soundManager.playCaptureSound();
        }
        
        isBlackTurn = !isBlackTurn;
        startMoveTimer(); // 重置计时器
        return true;
    }
    
    public boolean isValidMove(int row, int col) {
        // 如果位置已经有棋子，则不合法
        if (board[row][col] != EMPTY) {
            return false;
        }
        
        int currentColor = isBlackTurn ? BLACK : WHITE;
        int oppositeColor = isBlackTurn ? WHITE : BLACK;
        
        // 检查八个方向
        for (int[] direction : DIRECTIONS) {
            int r = row + direction[0];
            int c = col + direction[1];
            
            // 首先必须找到一个相邻的对方棋子
            if (isInBoard(r, c) && board[r][c] == oppositeColor) {
                r += direction[0];
                c += direction[1];
                
                // 继续在该方向上搜索，直到找���自己的棋子
                while (isInBoard(r, c)) {
                    if (board[r][c] == EMPTY) {
                        break;
                    }
                    if (board[r][c] == currentColor) {
                        return true;
                    }
                    r += direction[0];
                    c += direction[1];
                }
            }
        }
        return false;
    }
    
    private boolean flipPieces(int row, int col, int dr, int dc) {
        int currentColor = isBlackTurn ? BLACK : WHITE;
        int oppositeColor = isBlackTurn ? WHITE : BLACK;
        List<int[]> toFlip = new ArrayList<>();
        
        int r = row + dr;
        int c = col + dc;
        boolean flipped = false;
        
        // 收集需要翻转的棋子位置
        while (isInBoard(r, c) && board[r][c] == oppositeColor) {
            toFlip.add(new int[]{r, c});
            r += dr;
            c += dc;
            
            if (!isInBoard(r, c) || board[r][c] == EMPTY) {
                toFlip.clear();
                break;
            }
            
            if (board[r][c] == currentColor) {
                // 翻转收集到的所有棋子
                for (int[] pos : toFlip) {
                    board[pos[0]][pos[1]] = currentColor;
                    flipped = true;
                }
                break;
            }
        }
        return flipped;
    }
    
    private boolean isInBoard(int row, int col) {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }
    
    public boolean hasValidMoves() {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (isValidMove(i, j)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public void toggleTurn() {
        isBlackTurn = !isBlackTurn;
    }
    
    public void setTurn(boolean isBlack) {
        isBlackTurn = isBlack;
    }
    
    // 重置游戏时清空历史记录
    public void reset() {
        history.clear();
        blackTimeLeft = MAX_MOVE_TIME;
        whiteTimeLeft = MAX_MOVE_TIME;
        initializeBoard();
        startMoveTimer();
    }
    
    public void saveToFile(String filename) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            // 保存当前回合
            writer.println(isBlackTurn ? "BLACK" : "WHITE");
            
            // 保存当前棋盘状态
            saveBoard(writer, board);
            
            // 保存历史记录数量
            writer.println(history.size());
            
            // 保存所有历史状态
            for (GameStateMemento memento : history) {
                writer.println(memento.isBlackTurn ? "BLACK" : "WHITE");
                writer.println(memento.blackCount + "," + memento.whiteCount);
                saveBoard(writer, memento.board);
            }
        }
    }
    
    private void saveBoard(PrintWriter writer, int[][] boardToSave) {
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                writer.print(boardToSave[i][j]);
                if (j < 7) writer.print(",");
            }
            writer.println();
        }
    }
    
    public void loadFromFile(String filename) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            // 清空当前历史记录
            history.clear();
            
            // 读取当前回合
            String turn = reader.readLine();
            isBlackTurn = turn.equals("BLACK");
            
            // 读取当前棋盘状态
            board = readBoard(reader);
            
            // 读取历史记录数量
            int historySize = Integer.parseInt(reader.readLine());
            
            // 读取所有历史状态
            for (int i = 0; i < historySize; i++) {
                boolean historyTurn = reader.readLine().equals("BLACK");
                String[] scores = reader.readLine().split(",");
                int blackCount = Integer.parseInt(scores[0]);
                int whiteCount = Integer.parseInt(scores[1]);
                int[][] historyBoard = readBoard(reader);
                
                history.push(new GameStateMemento(historyBoard, historyTurn, blackCount, whiteCount));
            }
        }
    }
    
    private int[][] readBoard(BufferedReader reader) throws IOException {
        int[][] boardToLoad = new int[8][8];
        for (int i = 0; i < 8; i++) {
            String[] values = reader.readLine().split(",");
            for (int j = 0; j < 8; j++) {
                boardToLoad[i][j] = Integer.parseInt(values[j]);
            }
        }
        return boardToLoad;
    }
    
    // 添加 close 方法来释放资源
    public void close() {
        if (soundManager != null) {
            soundManager.close();
        }
        if (moveTimer != null) {
            moveTimer.stop();
        }
    }
} 