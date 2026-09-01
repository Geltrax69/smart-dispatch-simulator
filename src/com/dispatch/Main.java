package com.dispatch;

import com.dispatch.ui.MainFrame;

import javax.swing.*;

/**
 * Entry point. Uses SwingUtilities.invokeLater to be EDT-safe.
 */
public final class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            MainFrame f = new MainFrame();
            f.setVisible(true);
        });
    }
}
