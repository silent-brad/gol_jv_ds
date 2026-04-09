package com.example.gameoflife;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class GameService {

    public static final int BOARD_SIZE = 50;
    private static final long TICK_MS = 200;

    private final Game game = new Game(BOARD_SIZE, BOARD_SIZE);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger userCounter = new AtomicInteger(0);
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private Thread gameThread;

    // Cached board HTML, updated once per tick
    private volatile String cachedBoardHtml = "";

    @PostConstruct
    public void start() {
        game.getInitialBoard();
        updateCachedBoard();
        gameThread = Thread.ofVirtual().name("game-loop").start(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(TICK_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                game.nextGeneration();
                updateCachedBoard();
                notifyListeners();
            }
        });
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (gameThread != null) {
            gameThread.interrupt();
        }
    }

    public Game getGame() {
        return game;
    }

    public int nextUserId() {
        return userCounter.incrementAndGet();
    }

    public String getCachedBoardHtml() {
        return cachedBoardHtml;
    }

    private void updateCachedBoard() {
        StringBuilder sb = new StringBuilder(BOARD_SIZE * BOARD_SIZE * 60);
        int[] board = game.getBoard();
        for (int i = 0; i < board.length; i++) {
            String colorClass = Game.colorName(board[i]);
            sb.append("<div class=\"tile ").append(colorClass)
              .append("\" data-id=\"").append(i).append("\"></div>");
        }
        this.cachedBoardHtml = sb.toString();
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Exception ignored) {
            }
        }
    }
}
