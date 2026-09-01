package com.dispatch.ui;

import com.dispatch.model.Driver;
import com.dispatch.model.Order;
import com.dispatch.model.Vehicle.VehicleType;
import com.dispatch.sim.Config;
import com.dispatch.sim.SimulationEngine;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Control panel: simulation buttons, sliders, and scoring weight configuration.
 */
public final class ControlPanel extends JPanel {
    private static final Color BG      = new Color(22, 30, 42);
    private static final Color BTN_BG  = new Color(35, 50, 70);
    private static final Color BTN_FG  = new Color(200, 220, 240);
    private static final Color ACCENT  = new Color(80, 200, 255);
    private static final Color GREEN   = new Color(80, 220, 130);
    private static final Color RED     = new Color(255, 90, 90);
    private static final Color YELLOW  = new Color(255, 200, 80);
    private static final Color DIM     = new Color(50, 65, 85);

    private final SimulationEngine sim;
    private final Config cfg;

    private final JButton startBtn, pauseBtn, resumeBtn, stopBtn;
    private final JButton genOrderBtn, gen10Btn, genDriverBtn;
    private final JButton randomFailBtn, trafficBtn, offlineBtn, accidentBtn, cancelBtn, resetBtn;
    private final JButton spikeBtn;
    private final JSlider speedSlider, orderRateSlider, driverCountSlider, trafficSlider;
    private final JSlider wDistSlider, wTimeSlider, wCapSlider, wWorkSlider, wPrioSlider, wDeadlineSlider;
    private final JLabel speedVal, rateVal, driverCountVal, trafficVal;
    private final JLabel wDistVal, wTimeVal, wCapVal, wWorkVal, wPrioVal, wDeadlineVal;

    private final AtomicInteger simState = new AtomicInteger(0); // 0=stopped,1=running,2=paused

    public ControlPanel(SimulationEngine sim, Config cfg) {
        this.sim = sim;
        this.cfg = cfg;
        setBackground(BG);
        setLayout(new BorderLayout(5, 5));

        // ── Simulation controls ────────────────────────────────────────────
        startBtn   = makeBtn("▶ Start",   GREEN,  () -> doStart());
        pauseBtn   = makeBtn("⏸ Pause",   YELLOW, () -> doPause());
        resumeBtn  = makeBtn("▶ Resume",  GREEN,  () -> doResume());
        stopBtn    = makeBtn("■ Stop",    RED,    () -> doStop());
        resetBtn   = makeBtn("⟳ Reset",   DIM,    () -> doReset());
        pauseBtn.setEnabled(false);
        resumeBtn.setEnabled(false);

        JPanel simBtns = new JPanel(new GridLayout(1, 5, 4, 4));
        simBtns.setBackground(BG);
        simBtns.add(startBtn);  simBtns.add(pauseBtn);
        simBtns.add(resumeBtn); simBtns.add(stopBtn); simBtns.add(resetBtn);

        // ── Order generation ────────────────────────────────────────────────
        genOrderBtn = makeBtn("+ Order", ACCENT,  () -> { if (simState.get() == 1) sim.generateOrder(); });
        gen10Btn    = makeBtn("+ 10 Orders", ACCENT, () -> { if (simState.get() == 1) sim.generateBurst(); });
        spikeBtn    = makeBtn("⚡ Spike", YELLOW,  () -> { if (simState.get() == 1) sim.simulateSpike(); });
        cancelBtn   = makeBtn("✕ Cancel", RED,     () -> doCancelOrder());
        JPanel orderBtns = new JPanel(new GridLayout(1, 4, 4, 4));
        orderBtns.setBackground(BG);
        orderBtns.add(genOrderBtn); orderBtns.add(gen10Btn);
        orderBtns.add(spikeBtn);    orderBtns.add(cancelBtn);

        // ── Driver actions ─────────────────────────────────────────────────
        genDriverBtn = makeBtn("+ Driver", ACCENT, () -> {
            if (simState.get() == 1) {
                VehicleType vt = Config.VEHICLE_MIX[(int)(Math.random() * Config.VEHICLE_MIX.length)];
                sim.addDriver(vt, 1 + (int)(Math.random() * 5));
            }
        });
        offlineBtn  = makeBtn("Offline", DIM,  () -> doDriverAction("offline"));
        accidentBtn = makeBtn("Accident", RED, () -> doDriverAction("accident"));
        JPanel drvBtns = new JPanel(new GridLayout(1, 3, 4, 4));
        drvBtns.setBackground(BG);
        drvBtns.add(genDriverBtn); drvBtns.add(offlineBtn); drvBtns.add(accidentBtn);

        // ── Event simulation ────────────────────────────────────────────────
        randomFailBtn = makeBtn("Random Fail", RED,    () -> { if (simState.get() == 1) sim.simulateFailure(); });
        trafficBtn    = makeBtn("Traffic Jam", YELLOW, () -> { if (simState.get() == 1) sim.simulateTraffic(); });
        JPanel eventBtns = new JPanel(new GridLayout(1, 2, 4, 4));
        eventBtns.setBackground(BG);
        eventBtns.add(randomFailBtn); eventBtns.add(trafficBtn);

        // ── Sliders ─────────────────────────────────────────────────────────
        speedVal     = new JLabel("1.0x");   speedVal.setForeground(ACCENT);  speedVal.setFont(new Font("Monaco", Font.BOLD, 10));
        rateVal      = new JLabel("10");     rateVal.setForeground(ACCENT);  rateVal.setFont(new Font("Monaco", Font.BOLD, 10));
        driverCountVal = new JLabel("20 max"); driverCountVal.setForeground(ACCENT); driverCountVal.setFont(new Font("Monaco", Font.BOLD, 10));
        trafficVal   = new JLabel("0.0");   trafficVal.setForeground(ACCENT); trafficVal.setFont(new Font("Monaco", Font.BOLD, 10));

        speedSlider     = makeSlider(1, 40, 10, v -> { cfg.simulationSpeed = v / 10.0; speedVal.setText(String.format("%.1fx", v / 10.0)); });
        orderRateSlider = makeSlider(1, 20, 10, v -> { cfg.orderGenRate = v; rateVal.setText(String.valueOf(v)); });
        driverCountSlider= makeSlider(5, 30, 15, v -> { cfg.maxDrivers = v; driverCountVal.setText(v + " max"); });
        trafficSlider   = makeSlider(0, 10, 0, v -> { cfg.trafficIntensity = v / 10.0; trafficVal.setText(String.format("%.1f", v / 10.0)); });

        JPanel sliders = new JPanel(new GridLayout(4, 1, 3, 3));
        sliders.setBackground(BG);
        sliders.add(row("Speed", speedSlider, speedVal));
        sliders.add(row("Order Rate (/s)", orderRateSlider, rateVal));
        sliders.add(row("Max Drivers", driverCountSlider, driverCountVal));
        sliders.add(row("Traffic", trafficSlider, trafficVal));

        // ── Dispatch scoring weights ────────────────────────────────────────
        wDistVal   = valLabel(cfg.wDistance);  wTimeVal   = valLabel(cfg.wTime);
        wCapVal    = valLabel(cfg.wCapacity);  wWorkVal   = valLabel(cfg.wWorkload);
        wPrioVal   = valLabel(cfg.wPriority);  wDeadlineVal = valLabel(cfg.wDeadline);

        wDistSlider   = makeSlider(0, 20, 10, v -> { cfg.wDistance  = v / 10.0; wDistVal.setText(String.format("%.1f", v / 10.0)); });
        wTimeSlider   = makeSlider(0, 20, 10, v -> { cfg.wTime      = v / 10.0; wTimeVal.setText(String.format("%.1f", v / 10.0)); });
        wCapSlider    = makeSlider(0, 20,  5, v -> { cfg.wCapacity  = v / 10.0; wCapVal.setText(String.format("%.1f", v / 10.0)); });
        wWorkSlider   = makeSlider(0, 20, 15, v -> { cfg.wWorkload  = v / 10.0; wWorkVal.setText(String.format("%.1f", v / 10.0)); });
        wPrioSlider   = makeSlider(0, 20, 10, v -> { cfg.wPriority  = v / 10.0; wPrioVal.setText(String.format("%.1f", v / 10.0)); });
        wDeadlineSlider= makeSlider(0, 20, 20, v -> { cfg.wDeadline  = v / 10.0; wDeadlineVal.setText(String.format("%.1f", v / 10.0)); });

        JPanel weights = new JPanel(new GridLayout(6, 1, 3, 3));
        weights.setBackground(BG);
        weights.add(row("wDistance",  wDistSlider,   wDistVal));
        weights.add(row("wTime",     wTimeSlider,   wTimeVal));
        weights.add(row("wCapacity",  wCapSlider,    wCapVal));
        weights.add(row("wWorkload", wWorkSlider,   wWorkVal));
        weights.add(row("wPriority", wPrioSlider,   wPrioVal));
        weights.add(row("wDeadline", wDeadlineSlider, wDeadlineVal));

        // ── Assemble panels ──────────────────────────────────────────────────
        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.setBackground(BG);
        top.add(simBtns, BorderLayout.NORTH);
        top.add(orderBtns, BorderLayout.CENTER);
        top.add(drvBtns, BorderLayout.SOUTH);

        JPanel slidersSection = new JPanel(new BorderLayout(4, 4));
        slidersSection.setBackground(BG);
        slidersSection.add(sliders, BorderLayout.NORTH);
        slidersSection.add(eventBtns, BorderLayout.CENTER);

        JPanel weightsSection = new JPanel(new BorderLayout());
        weightsSection.setBackground(BG);
        weightsSection.add(weights, BorderLayout.NORTH);

        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setBackground(BG);
        left.add(top, BorderLayout.NORTH);
        left.add(slidersSection, BorderLayout.CENTER);
        add(left, BorderLayout.NORTH);

        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(BG);
        right.add(weightsSection, BorderLayout.NORTH);
        add(right, BorderLayout.CENTER);
    }

    private JButton makeBtn(String text, Color color, Runnable action) {
        JButton b = new JButton(text);
        b.setBackground(color.darker());
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setFont(new Font("Monaco", Font.BOLD, 11));
        b.setBorder(BorderFactory.createLineBorder(color.darker().darker()));
        b.addActionListener(e -> SwingUtilities.invokeLater(action));
        return b;
    }

    private JSlider makeSlider(int min, int max, int init, java.util.function.IntConsumer onChange) {
        JSlider s = new JSlider(min, max, init);
        s.setBackground(BG);
        s.setForeground(ACCENT);
        s.setMajorTickSpacing((max - min) / 4);
        s.setPaintTicks(true);
        s.setOpaque(false);
        s.addChangeListener(e -> onChange.accept(s.getValue()));
        return s;
    }

    private JPanel row(String label, JSlider s, JLabel val) {
        JPanel p = new JPanel(new BorderLayout(4, 0));
        p.setBackground(BG);
        JLabel l = new JLabel(label);
        l.setForeground(new Color(160, 180, 200));
        l.setFont(new Font("Monaco", Font.PLAIN, 10));
        val.setForeground(ACCENT);
        val.setFont(new Font("Monaco", Font.BOLD, 10));
        p.add(l, BorderLayout.WEST);
        p.add(s, BorderLayout.CENTER);
        p.add(val, BorderLayout.EAST);
        return p;
    }

    private JLabel valLabel(double v) {
        JLabel l = new JLabel(String.format("%.1f", v));
        l.setForeground(ACCENT);
        l.setFont(new Font("Monaco", Font.BOLD, 10));
        return l;
    }

    // ── button actions ─────────────────────────────────────────────────────

    private void doStart() {
        simState.set(1);
        sim.start();
        startBtn.setEnabled(false);
        pauseBtn.setEnabled(true);
        resumeBtn.setEnabled(false);
        genOrderBtn.setEnabled(true);
        gen10Btn.setEnabled(true);
        spikeBtn.setEnabled(true);
        genDriverBtn.setEnabled(true);
        randomFailBtn.setEnabled(true);
        trafficBtn.setEnabled(true);
    }

    private void doPause() {
        simState.set(2);
        sim.pause();
        pauseBtn.setEnabled(false);
        resumeBtn.setEnabled(true);
    }

    private void doResume() {
        simState.set(1);
        sim.resume();
        pauseBtn.setEnabled(true);
        resumeBtn.setEnabled(false);
    }

    private void doStop() {
        simState.set(0);
        sim.stop();
        startBtn.setEnabled(true);
        pauseBtn.setEnabled(false);
        resumeBtn.setEnabled(false);
        genOrderBtn.setEnabled(false);
        gen10Btn.setEnabled(false);
        spikeBtn.setEnabled(false);
        genDriverBtn.setEnabled(false);
        randomFailBtn.setEnabled(false);
        trafficBtn.setEnabled(false);
    }

    private void doReset() {
        doStop();
        sim.reset();
        simState.set(0);
        startBtn.setEnabled(true);
    }

    private void doCancelOrder() {
        List<Order> orders = sim.getOrders();
        if (orders.isEmpty()) return;
        String[] ids = orders.stream().map(Order::getId).toArray(String[]::new);
        String chosen = (String) JOptionPane.showInputDialog(this,
            "Select order to cancel:", "Cancel Order",
            JOptionPane.QUESTION_MESSAGE, null, ids, ids[0]);
        if (chosen != null) sim.cancelOrder(chosen);
    }

    private void doDriverAction(String action) {
        List<Driver> drivers = sim.getDrivers();
        if (drivers.isEmpty()) return;
        String[] ids = drivers.stream().map(d -> "D" + d.getId()).toArray(String[]::new);
        String chosen = (String) JOptionPane.showInputDialog(this,
            "Select driver:", "Driver Action",
            JOptionPane.QUESTION_MESSAGE, null, ids, ids[0]);
        if (chosen != null) {
            int id = Integer.parseInt(chosen.substring(1));
            switch (action) {
                case "offline"  -> sim.driverOffline(id);
                case "accident" -> sim.driverAccident(id);
            }
        }
    }
}
