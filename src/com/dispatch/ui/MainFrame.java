package com.dispatch.ui;

import com.dispatch.sim.*;

import javax.swing.*;
import java.awt.*;

/**
 * Top-level frame: a 3-column layout.
 *   Left   : ControlPanel (buttons, sliders, weights)
 *   Center : MapPanel (city + drivers + routes)
 *   Right  : DashboardPanel (metrics + tables)
 *   Bottom : EventLogPanel
 */
public final class MainFrame extends JFrame {
    private final SimulationEngine sim;
    private final CityMap map;
    private final Config cfg;
    private final EventLogger logger;
    private final MetricsManager metrics;
    private final DispatchEngine dispatch;
    private final DashboardPanel dashboard;
    private final MapPanel mapPanel;
    private final EventLogPanel logPanel;

    public MainFrame() {
        super("SmartDispatch Simulator");
        cfg = new Config();
        map = new CityMap(cfg);
        logger = new EventLogger();
        metrics = new MetricsManager();
        dispatch = new DispatchEngine(cfg, map, metrics, logger);
        sim = new SimulationEngine(cfg, map, dispatch, metrics, logger);
        map.generate();
        sim.populateDrivers();
        sim.generateOrder();

        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1480, 880);
        setMinimumSize(new Dimension(1200, 720));
        setLocationRelativeTo(null);

        // Layout
        ControlPanel control = new ControlPanel(sim, cfg);
        dashboard = new DashboardPanel(sim);
        mapPanel = new MapPanel(map, sim);
        logPanel = new EventLogPanel(logger);

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(new Color(20, 28, 40));
        center.add(mapPanel, BorderLayout.CENTER);
        center.add(logPanel, BorderLayout.SOUTH);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, center, dashboard);
        rightSplit.setResizeWeight(0.62);
        rightSplit.setDividerSize(4);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, control, rightSplit);
        mainSplit.setResizeWeight(0.18);
        mainSplit.setDividerSize(4);

        getContentPane().add(mainSplit);
        logger.log("SmartDispatch Simulator ready. Press Start to begin.");
    }

    public void dispose() {
        sim.stop();
        dashboard.stopTimer();
        mapPanel.stopTimer();
        super.dispose();
    }
}
