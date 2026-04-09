package com.example.gameoflife;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class Game {

    public static final int DEAD = 0;

    private static final int[][] NEIGHBOR_OFFSETS = {
        {-1, -1}, {-1, 0}, {-1, 1},
        { 0, -1},          { 0, 1},
        { 1, -1}, { 1, 0}, { 1, 1}
    };

    private final int rows;
    private final int cols;
    private int[] board;

    public Game(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.board = new int[rows * cols];
    }

    public int getRows() {
        return rows;
    }

    public int getCols() {
        return cols;
    }

    public int[] getBoard() {
        return board;
    }

    public int getCell(int index) {
        return board[index];
    }

    public void setCell(int index, int color) {
        if (index >= 0 && index < board.length) {
            board[index] = color;
        }
    }

    public void fillCross(int index, int color) {
        int row = index / cols;
        int col = index % cols;

        setCell(index, color);
        if (row > 0) setCell(index - cols, color);
        if (row < rows - 1) setCell(index + cols, color);
        if (col > 0) setCell(index - 1, color);
        if (col < cols - 1) setCell(index + 1, color);
    }

    public int[] getInitialBoard() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < board.length; i++) {
            if (random.nextInt(10) < 3) {
                board[i] = random.nextInt(1, 7);
            } else {
                board[i] = DEAD;
            }
        }
        return board;
    }

    public void nextGeneration() {
        int[] next = new int[board.length];

        for (int i = 0; i < board.length; i++) {
            int row = i / cols;
            int col = i % cols;
            int cell = board[i];

            int aliveCount = 0;
            int[] livingColors = new int[8];

            for (int[] offset : NEIGHBOR_OFFSETS) {
                int nr = row + offset[0];
                int nc = col + offset[1];
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                    int neighbor = board[nr * cols + nc];
                    if (neighbor != DEAD) {
                        livingColors[aliveCount] = neighbor;
                        aliveCount++;
                    }
                }
            }

            if (cell != DEAD) {
                // alive cell: survives with 2 or 3 neighbors
                if (aliveCount == 2 || aliveCount == 3) {
                    next[i] = cell;
                } else {
                    next[i] = DEAD;
                }
            } else {
                // dead cell: becomes alive with exactly 3 neighbors
                if (aliveCount == 3) {
                    next[i] = livingColors[ThreadLocalRandom.current().nextInt(aliveCount)];
                } else {
                    next[i] = DEAD;
                }
            }
        }

        this.board = next;
    }

    public static final String[] COLORS = {
        "dead", "red", "blue", "green", "orange", "fuchsia", "purple"
    };

    public static int colorForUser(int userId) {
        // Map user id to a color (1-6, skipping 0 which is dead)
        return (Math.abs(userId) % 6) + 1;
    }

    public static String colorName(int color) {
        if (color >= 0 && color < COLORS.length) {
            return COLORS[color];
        }
        return COLORS[0];
    }
}
