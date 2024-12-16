package com.reversi.stand;

import javax.sound.sampled.*;
import java.io.IOException;
import java.net.URL;

public class SoundManager {
    private Clip moveSound;
    private Clip captureSound;
    
    public SoundManager() {
        try {
            moveSound = loadSound("move.wav");
            captureSound = loadSound("capture.wav");
        } catch (Exception e) {
            System.err.println("加载音效失败: " + e.getMessage());
        }
    }
    
    private Clip loadSound(String filename) throws LineUnavailableException, IOException, UnsupportedAudioFileException {
        URL url = getClass().getResource("/sounds/" + filename);
        if (url == null) {
            throw new IOException("找不到音效文件: " + filename);
        }
        
        AudioInputStream audioIn = AudioSystem.getAudioInputStream(url);
        Clip clip = AudioSystem.getClip();
        clip.open(audioIn);
        return clip;
    }
    
    public void playMoveSound() {
        playSound(moveSound);
    }
    
    public void playCaptureSound() {
        playSound(captureSound);
    }
    
    private void playSound(Clip clip) {
        if (clip != null) {
            clip.setFramePosition(0);
            clip.start();
        }
    }
    
    public void close() {
        if (moveSound != null) moveSound.close();
        if (captureSound != null) captureSound.close();
    }
} 