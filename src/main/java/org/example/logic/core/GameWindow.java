package org.example.logic.core;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class GameWindow {
    private JFrame frame;
    private boolean isFullscreen = false;
    private GamePanel gamePanel;

    public GameWindow(GamePanel panel) {
        this.gamePanel = panel;
        frame = new JFrame("MJUN GAME");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(true);
        frame.add(gamePanel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // Hlídá ruční natažení okna myší
        frame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (!isFullscreen) gamePanel.updateDimensions(frame.getWidth(), frame.getHeight());
            }
        });
        // Tento kód zajistí, že při kliknutí na křížek se okamžitě ukončí úplně vše (včetně hudby)
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                System.exit(0); // Zabije proces a vyčistí paměť
            }
        });
    }

    public void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        frame.dispose();
        frame.setUndecorated(isFullscreen);

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        if (isFullscreen) {
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            gd.setFullScreenWindow(frame);
            gamePanel.updateDimensions(frame.getWidth(), frame.getHeight());
        } else {
            gd.setFullScreenWindow(null);
            frame.setExtendedState(JFrame.NORMAL);
            gamePanel.updateDimensions(800, 600);
            frame.pack();
            frame.setLocationRelativeTo(null);
        }
        frame.setVisible(true);
    }

    // PŘIDÁNO: Metoda pro okamžitou změnu rozlišení okna
    public void changeResolution(int width, int height) {
        if (!isFullscreen()) {
            gamePanel.updateDimensions(width, height);
            frame.pack();
            frame.setLocationRelativeTo(null);
        }
    }

    public boolean isFullscreen() { return isFullscreen; }
}