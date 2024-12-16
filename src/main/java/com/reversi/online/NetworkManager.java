package com.reversi.online;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NetworkManager {
    private static final int BUFFER_SIZE = 1024;
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private SocketChannel clientChannel;
    private ByteBuffer buffer;
    private boolean isServer;
    private ConcurrentLinkedQueue<String> messageQueue;
    private volatile boolean running;
    private GameStateUpdateCallback callback;

    private static final String MSG_MOVE = "MOVE:";
    private static final String MSG_UNDO_REQUEST = "UNDO_REQUEST";
    private static final String MSG_UNDO_ACCEPT = "UNDO_ACCEPT";
    private static final String MSG_UNDO_REJECT = "UNDO_REJECT";
    private static final String MSG_NEW_GAME_REQUEST = "NEW_GAME_REQUEST";
    private static final String MSG_NEW_GAME_ACCEPT = "NEW_GAME_ACCEPT";
    private static final String MSG_NEW_GAME_REJECT = "NEW_GAME_REJECT";
    private static final String MSG_TIME_SETTING = "TIME:";

    public interface GameStateUpdateCallback {
        void onGameStateUpdate(String move);
        void onPlayerConnected();
        void onPlayerDisconnected();
        void onUndoRequest();
        void onUndoAccepted();
        void onUndoRejected();
        void onNewGameRequest();
        void onNewGameAccepted();
        void onNewGameRejected();
        void onTimeSettingReceived(int seconds);
    }

    public NetworkManager(boolean isServer, String host, int port, GameStateUpdateCallback callback) throws IOException {
        this.isServer = isServer;
        this.callback = callback;
        this.buffer = ByteBuffer.allocate(BUFFER_SIZE);
        this.messageQueue = new ConcurrentLinkedQueue<>();
        this.selector = Selector.open();

        if (isServer) {
            initializeServer(port);
        } else {
            initializeClient(host, port);
        }
    }

    private void initializeServer(int port) throws IOException {
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    private void initializeClient(String host, int port) throws IOException {
        clientChannel = SocketChannel.open();
        clientChannel.configureBlocking(false);
        clientChannel.connect(new InetSocketAddress(host, port));
        clientChannel.register(selector, SelectionKey.OP_CONNECT);
    }

    public void start() {
        running = true;
        new Thread(this::run).start();
    }

    public void stop() {
        running = false;
        try {
            if (serverChannel != null) serverChannel.close();
            if (clientChannel != null) clientChannel.close();
            selector.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void run() {
        while (running) {
            try {
                if (selector.select() > 0) {
                    Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                    while (keys.hasNext()) {
                        SelectionKey key = keys.next();
                        keys.remove();

                        if (!key.isValid()) continue;

                        if (key.isAcceptable()) {
                            handleAccept();
                        } else if (key.isConnectable()) {
                            handleConnect();
                        } else if (key.isReadable()) {
                            handleRead(key);
                        } else if (key.isWritable()) {
                            handleWrite(key);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleAccept() throws IOException {
        SocketChannel channel = serverChannel.accept();
        if (channel != null) {
            channel.configureBlocking(false);
            channel.register(selector, SelectionKey.OP_READ);
            clientChannel = channel;
            callback.onPlayerConnected();
        }
    }

    private void handleConnect() throws IOException {
        if (clientChannel.finishConnect()) {
            clientChannel.register(selector, SelectionKey.OP_READ);
            callback.onPlayerConnected();
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        buffer.clear();
        int read = channel.read(buffer);
        
        if (read == -1) {
            channel.close();
            callback.onPlayerDisconnected();
            return;
        }

        buffer.flip();
        byte[] data = new byte[buffer.remaining()];
        buffer.get(data);
        String message = new String(data).trim();

        if (message.startsWith(MSG_MOVE)) {
            callback.onGameStateUpdate(message.substring(MSG_MOVE.length()));
        } else if (message.startsWith(MSG_TIME_SETTING)) {
            int seconds = Integer.parseInt(message.substring(MSG_TIME_SETTING.length()));
            callback.onTimeSettingReceived(seconds);
        } else if (message.equals(MSG_UNDO_REQUEST)) {
            callback.onUndoRequest();
        } else if (message.equals(MSG_UNDO_ACCEPT)) {
            callback.onUndoAccepted();
        } else if (message.equals(MSG_UNDO_REJECT)) {
            callback.onUndoRejected();
        } else if (message.equals(MSG_NEW_GAME_REQUEST)) {
            callback.onNewGameRequest();
        } else if (message.equals(MSG_NEW_GAME_ACCEPT)) {
            callback.onNewGameAccepted();
        } else if (message.equals(MSG_NEW_GAME_REJECT)) {
            callback.onNewGameRejected();
        }
    }

    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        String message = messageQueue.poll();
        if (message != null) {
            channel.write(ByteBuffer.wrap(message.getBytes()));
        }
        key.interestOps(SelectionKey.OP_READ);
    }

    public void sendMove(String move) {
        sendMessage(MSG_MOVE + move);
    }

    public void sendUndoRequest() {
        sendMessage(MSG_UNDO_REQUEST);
    }

    public void sendUndoAccept() {
        sendMessage(MSG_UNDO_ACCEPT);
    }

    public void sendUndoReject() {
        sendMessage(MSG_UNDO_REJECT);
    }

    public void sendNewGameRequest() {
        sendMessage(MSG_NEW_GAME_REQUEST);
    }

    public void sendNewGameAccept() {
        sendMessage(MSG_NEW_GAME_ACCEPT);
    }

    public void sendNewGameReject() {
        sendMessage(MSG_NEW_GAME_REJECT);
    }

    public void sendTimeSetting(int seconds) {
        sendMessage(MSG_TIME_SETTING + seconds);
    }

    private void sendMessage(String message) {
        if (clientChannel != null && clientChannel.isConnected()) {
            messageQueue.offer(message);
            SelectionKey key = clientChannel.keyFor(selector);
            if (key != null) {
                key.interestOps(SelectionKey.OP_WRITE);
                selector.wakeup();
            }
        }
    }

    public boolean isConnected() {
        return clientChannel != null && clientChannel.isConnected();
    }
} 