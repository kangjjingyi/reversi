package com.reversi.online;

import javax.swing.Timer;
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
    private int maxMoveTime = 30; // 默认30秒
    private int blackTimeLeft; // 黑方剩余时间（秒）
    private int whiteTimeLeft; // 白方剩余时间（秒）
    private Timer moveTimer; // 当前回合计时器
    private long moveStartTime; // 当前回合开始时间
    private TimerCallback timerCallback;
    private NetworkManager networkManager;
    private boolean isOnlineMode;
    private boolean isServer;
    private boolean undoRequested = false;
    private boolean newGameRequested = false;
    private boolean isOpponentConnected = false;

    public interface TimerCallback {
        void onTimeUpdate(int blackTime, int whiteTime);
        void onTimeout(boolean isBlackTimeout);
    }

    public GameState() {
        board = new int[8][8];
        history = new Stack<>();
        soundManager = new SoundManager();
        blackTimeLeft = maxMoveTime;
        whiteTimeLeft = maxMoveTime;
        initializeBoard();
    }

    public void setTimerCallback(TimerCallback callback) {
        this.timerCallback = callback;
    }

    private void startMoveTimer() {
        if (isOnlineMode && !isOpponentConnected) {
            return;
        }
        
        if (moveTimer != null) {
            moveTimer.stop();
        }
        
        blackTimeLeft = maxMoveTime;
        whiteTimeLeft = maxMoveTime;
        
        moveStartTime = System.currentTimeMillis();
        moveTimer = new Timer(1000, e -> updateTime());
        moveTimer.start();
    }

    private void updateTime() {
        long currentTime = System.currentTimeMillis();
        int elapsedSeconds = (int)((currentTime - moveStartTime) / 1000);
        
        if (isBlackTurn) {
            blackTimeLeft = Math.max(0, maxMoveTime - elapsedSeconds);
            if (blackTimeLeft == 0) {
                moveTimer.stop();
                if (timerCallback != null) {
                    timerCallback.onTimeout(true);
                }
            }
        } else {
            whiteTimeLeft = Math.max(0, maxMoveTime - elapsedSeconds);
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
        
        // 设置初始四个子
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
    
    // 在落子前保存前状态
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
        if (history.isEmpty() || (isOnlineMode && !undoRequested)) {
            return false;
        }
        
        GameStateMemento memento = history.pop();
        board = new int[8][8];
        for (int i = 0; i < 8; i++) {
            board[i] = Arrays.copyOf(memento.board[i], 8);
        }
        isBlackTurn = memento.isBlackTurn;
        undoRequested = false;
        return true;
    }
    
    // 修改makeMove方法以支持历史记录
    public boolean makeMove(int row, int col) {
        // 在联机模式下，需要等待对手连接
        if (isOnlineMode && !isOpponentConnected) {
            return false;
        }
        
        // 检查是否是当前玩家的回合
        if (isOnlineMode) {
            boolean isBlackPlayer = isServer;  // 服务端是黑方
            if ((isBlackPlayer && !isBlackTurn) || (!isBlackPlayer && isBlackTurn)) {
                return false; // 不是当前玩家的回合
            }
        }

        if (!isValidMove(row, col)) {
            return false;
        }

        // 执行移动
        executeMove(row, col);

        // 发送移动信息到对方
        if (isOnlineMode && networkManager != null) {
            networkManager.sendMove(row + "," + col);
        }

        return true;
    }
    
    // 新增 executeMove 方法，用于实际执行移动操作
    private void executeMove(int row, int col) {
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
                
                // 继续在该方向上搜索，直到找到自己的棋子
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
        blackTimeLeft = maxMoveTime;
        whiteTimeLeft = maxMoveTime;
        
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
        
        // 在联机模式下，只有当是��务端（黑方）且对手已连接时才启动计时器
        if (!isOnlineMode || (isServer && isOpponentConnected)) {
            startMoveTimer();
        }
        
        newGameRequested = false;
        undoRequested = false;
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

    public void setNetworkManager(NetworkManager networkManager, boolean isServer) {
        this.networkManager = networkManager;
        this.isOnlineMode = true;
        this.isServer = isServer;
        
        // 重置游戏状态
        reset();
        
        // 服务器默认执黑，客户端执白
        this.isBlackTurn = true;  // 总是从黑方开始
        
        // 停止现有的计时器
        if (moveTimer != null) {
            moveTimer.stop();
        }
    }

    // 修改 handleReceivedMove 方法
    public void handleReceivedMove(String moveData) {
        try {
            String[] parts = moveData.split(",");
            int row = Integer.parseInt(parts[0]);
            int col = Integer.parseInt(parts[1]);
            
            // 直接执行移动，不进行回合检查
            if (isValidMove(row, col)) {
                executeMove(row, col);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 添加新方法
    public void requestUndo() {
        if (networkManager != null) {
            networkManager.sendUndoRequest();
        }
    }

    public void acceptUndo() {
        if (networkManager != null) {
            networkManager.sendUndoAccept();
            undoRequested = true;
        }
    }

    public void rejectUndo() {
        if (networkManager != null) {
            networkManager.sendUndoReject();
        }
    }

    public void setUndoRequested(boolean requested) {
        this.undoRequested = requested;
    }

    // 添加 isOnlineMode 方法
    public boolean isOnlineMode() {
        return isOnlineMode;
    }

    // 添加新游戏相关的方法
    public void requestNewGame() {
        if (networkManager != null) {
            networkManager.sendNewGameRequest();
        }
    }

    public void acceptNewGame() {
        if (networkManager != null) {
            networkManager.sendNewGameAccept();
            reset();  // 重置游戏状态
        }
    }

    public void rejectNewGame() {
        if (networkManager != null) {
            networkManager.sendNewGameReject();
        }
    }

    // 添加设���对手连接状态的方法
    public void setOpponentConnected(boolean connected) {
        this.isOpponentConnected = connected;
        if (connected && isOnlineMode) {
            if (isServer) {
                // 服务端（黑方）连接后启动计时器
                startMoveTimer();
            }
        } else {
            // 断开连接时停止计时器
            if (moveTimer != null) {
                moveTimer.stop();
            }
        }
    }

    // 添加设置最大时间的方法
    public void setMaxMoveTime(int seconds) {
        this.maxMoveTime = seconds;
        // 重置当前剩余时间
        blackTimeLeft = maxMoveTime;
        whiteTimeLeft = maxMoveTime;
        // 如果计时器正在运行，重新启动
        if (moveTimer != null && moveTimer.isRunning()) {
            startMoveTimer();
        }
    }

    // 添加获取最大时间的方法
    public int getMaxMoveTime() {
        return maxMoveTime;
    }
} 