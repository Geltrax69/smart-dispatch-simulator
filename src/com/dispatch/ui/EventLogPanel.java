package com.dispatch.ui;

import com.dispatch.sim.EventLogger;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.text.*;
import java.awt.*;

/**
 * Scrolling event log panel. Listens to EventLogger via its Listener interface.
 */
public final class EventLogPanel extends JPanel implements EventLogger.Listener {
    private static final Color BG    = new Color(18, 22, 32);
    private static final Color TEXT  = new Color(200, 215, 230);
    private static final Color INFO  = new Color(140, 200, 255);
    private static final Color WARN  = new Color(255, 200, 80);
    private static final Color ERROR = new Color(255, 90, 90);
    private static final Color OK    = new Color(80, 220, 130);

    private final JTextPane pane;
    private final DefaultStyledDocument doc;
    private static final int MAX_LINES = 300;

    public EventLogPanel(EventLogger logger) {
        setBackground(BG);
        setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(new Color(50, 65, 85)),
            "Event Log", TitledBorder.LEADING, TitledBorder.TOP,
            new Font("Monaco", Font.BOLD, 11), new Color(80, 200, 255)));

        pane = new JTextPane();
        pane.setEditable(false);
        pane.setBackground(BG);
        pane.setFont(new Font("Monaco", Font.PLAIN, 10));
        doc = new DefaultStyledDocument();
        pane.setDocument(doc);

        JScrollPane scroll = new JScrollPane(pane,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        add(scroll, BorderLayout.CENTER);

        // Load existing lines
        for (String line : logger.snapshot()) appendLine(line);
        logger.addListener(this);
    }

    @Override
    public void onNewEvent(String line) {
        SwingUtilities.invokeLater(() -> appendLine(line));
    }

    private void appendLine(String line) {
        try {
            StyleContext sc = StyleContext.getDefaultStyleContext();
            Color color = inferColor(line);
            AttributeSet attr = sc.addAttribute(
                SimpleAttributeSet.EMPTY, StyleConstants.Foreground, color);

            if (doc.getLength() > MAX_LINES * 100) {
                doc.remove(0, doc.getLength());
            }
            doc.insertString(doc.getLength(), line + "\n", attr);
            // Auto-scroll
            pane.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {}
    }

    private Color inferColor(String line) {
        String l = line.toLowerCase();
        if (l.contains("failed") || l.contains("error") || l.contains("accident"))
            return ERROR;
        if (l.contains("warn") || l.contains("⚠") || l.contains("offline"))
            return WARN;
        if (l.contains("completed") || l.contains("delivered") || l.contains("✓"))
            return OK;
        if (l.contains("assigned") || l.contains("picked up") || l.contains("assigned"))
            return INFO;
        return TEXT;
    }
}
