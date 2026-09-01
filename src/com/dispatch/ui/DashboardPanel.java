package com.dispatch.ui;

import com.dispatch.sim.MetricsManager;
import com.dispatch.sim.MetricsManager.Snapshot;
import com.dispatch.sim.SimulationEngine;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.*;
import java.awt.*;
import java.util.List;

/**
 * Dashboard showing live metrics in styled stat tiles and a driver/order table.
 */
public final class DashboardPanel extends JPanel {
    private static final Color PANEL_BG   = new Color(22, 30, 42);
    private static final Color TILE_BG    = new Color(30, 40, 56);
    private static final Color TILE_BORDER= new Color(50, 65, 85);
    private static final Color ACCENT     = new Color(80, 200, 255);
    private static final Color GREEN      = new Color(80, 220, 130);
    private static final Color RED        = new Color(255, 90, 90);
    private static final Color YELLOW     = new Color(255, 200, 80);
    private static final Color DIM       = new Color(100, 120, 150);

    private final JLabel totalOrders    = makeTile("Total Orders",    "0");
    private final JLabel pendingOrders = makeTile("Pending",         "0");
    private final JLabel activeOrders  = makeTile("Active",          "0");
    private final JLabel completedOrders= makeTile("Completed",      "0");
    private final JLabel failedOrders   = makeTile("Failed",         "0");
    private final JLabel cancelledOrders= makeTile("Cancelled",      "0");
    private final JLabel availDrivers   = makeTile("Avail Drivers",   "0");
    private final JLabel busyDrivers    = makeTile("Busy Drivers",   "0");
    private final JLabel avgAssignMs    = makeTile("Avg Assign (ms)","0");
    private final JLabel avgDeliverySec = makeTile("Avg Delivery (s)","0");
    private final JLabel ordersPerSec   = makeTile("Orders/sec",     "0");
    private final JLabel utilization    = makeTile("Driver Util %",  "0");

    private final JTable driverTable;
    private final JTable orderTable;
    private final DefaultTableModel driverModel;
    private final DefaultTableModel orderModel;

    private final SimulationEngine sim;
    private final Timer updateTimer;

    public DashboardPanel(SimulationEngine sim) {
        this.sim = sim;
        setBackground(PANEL_BG);
        setLayout(new BorderLayout(4, 4));

        // ── Top stat tiles ────────────────────────────────────────────────
        JPanel tiles = new JPanel(new GridLayout(3, 4, 6, 6));
        tiles.setBackground(PANEL_BG);
        tiles.add(totalOrders);      tiles.add(pendingOrders);
        tiles.add(activeOrders);     tiles.add(completedOrders);
        tiles.add(failedOrders);     tiles.add(cancelledOrders);
        tiles.add(availDrivers);     tiles.add(busyDrivers);
        tiles.add(avgAssignMs);     tiles.add(avgDeliverySec);
        tiles.add(ordersPerSec);    tiles.add(utilization);

        tiles.setBorder(new TitledBorder(
            BorderFactory.createLineBorder(TILE_BORDER),
            "Live Metrics", TitledBorder.LEADING, TitledBorder.TOP,
            new Font("Monaco", Font.BOLD, 11), ACCENT));
        add(tiles, BorderLayout.NORTH);

        // ── Tables ─────────────────────────────────────────────────────────
        driverModel = new DefaultTableModel(
            new String[]{"ID", "Name", "Status", "Vehicle", "Load", "Priority"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        driverTable = makeTable(driverModel);

        orderModel = new DefaultTableModel(
            new String[]{"Order", "Pickup", "Dropoff", "Status", "Pkg", "Prio", "Assign"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        orderTable = makeTable(orderModel);

        JPanel tables = new JPanel(new GridLayout(2, 1, 4, 4));
        tables.setBackground(PANEL_BG);
        JScrollPane dp = new JScrollPane(driverTable);
        dp.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(TILE_BORDER),
            "Drivers", TitledBorder.LEADING, TitledBorder.TOP,
            new Font("Monaco", Font.BOLD, 11), ACCENT));
        JScrollPane op = new JScrollPane(orderTable);
        op.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(TILE_BORDER),
            "Orders", TitledBorder.LEADING, TitledBorder.TOP,
            new Font("Monaco", Font.BOLD, 11), ACCENT));
        tables.add(dp);
        tables.add(op);
        add(tables, BorderLayout.CENTER);

        updateTimer = new Timer(300, e -> refresh());
        updateTimer.start();
    }

    private JLabel makeTile(String title, String value) {
        JLabel l = new JLabel("<html><center>" + title + "<br><font size=+2 color='" + toHex(ACCENT) + "'>" + value + "</font></center></html>", SwingConstants.CENTER);
        l.setBackground(TILE_BG);
        l.setOpaque(true);
        l.setBorder(BorderFactory.createLineBorder(TILE_BORDER));
        l.setFont(new Font("Monaco", Font.PLAIN, 11));
        return l;
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private JTable makeTable(DefaultTableModel m) {
        JTable t = new JTable(m);
        t.setBackground(new Color(25, 35, 50));
        t.setForeground(Color.WHITE);
        t.setFont(new Font("Monaco", Font.PLAIN, 10));
        t.setRowHeight(18);
        t.setGridColor(TILE_BORDER);
        t.getTableHeader().setBackground(new Color(35, 50, 70));
        t.getTableHeader().setForeground(ACCENT);
        t.getTableHeader().setFont(new Font("Monaco", Font.BOLD, 10));
        return t;
    }

    public void refresh() {
        if (!sim.isRunning() && sim.getDrivers().isEmpty()) return;
        MetricsManager.Snapshot s = sim.getMetrics().snapshot(sim.getDrivers());
        setTile(totalOrders,    String.valueOf(s.total()));
        setTile(pendingOrders, String.valueOf(s.pending()), s.pending() > 5 ? YELLOW : DIM);
        setTile(activeOrders,   String.valueOf(s.active()), s.active() > 0 ? ACCENT : DIM);
        setTile(completedOrders, String.valueOf(s.completed()), GREEN);
        setTile(failedOrders,   String.valueOf(s.failed()), s.failed() > 0 ? RED : DIM);
        setTile(cancelledOrders, String.valueOf(s.cancelled()), DIM);
        setTile(availDrivers,   String.valueOf(s.available()), GREEN);
        setTile(busyDrivers,    String.valueOf(s.busy()), s.busy() > 0 ? ACCENT : DIM);
        setTile(avgAssignMs,    String.format("%.0f", s.avgAssignMs()));
        setTile(avgDeliverySec, String.format("%.1f", s.avgDeliveryMs() / 1000.0));
        setTile(ordersPerSec,  String.format("%.2f", s.ordersPerSec()), ACCENT);
        setTile(utilization,   String.format("%.0f%%", s.driverUtilization() * 100),
            s.driverUtilization() > 0.8 ? RED : s.driverUtilization() > 0.5 ? YELLOW : GREEN);

        // driver table
        SwingUtilities.invokeLater(() -> {
            driverModel.setRowCount(0);
            for (var d : sim.getDrivers()) {
                driverModel.addRow(new Object[]{
                    "D" + d.getId(), d.getName(), d.getStatus().toString(),
                    d.getVehicle().getType(), d.getWorkload() + "/" + d.getVehicle().getCapacity(),
                    d.getPriority()
                });
            }
            orderModel.setRowCount(0);
            for (var o : sim.getOrders()) {
                orderModel.addRow(new Object[]{
                    o.getId(),
                    o.getPickup().getName(),
                    o.getDropoff().getName(),
                    o.getStatus().toString(),
                    o.getPackageSize(),
                    o.getPriority(),
                    o.getAssignedDriverId() == null ? "—" : o.getAssignedDriverId()
                });
            }
        });
    }

    private void setTile(JLabel tile, String value) { setTile(tile, value, ACCENT); }

    private void setTile(JLabel tile, String value, Color color) {
        String title = tile.getText().replaceAll("<font size=.*?</font>", "")
            .replaceAll("</?html>", "").replaceAll("<br>", " ").trim();
        // extract title from original HTML
        String html = "<html><center>" + getTileTitle(tile) + "<br><font size=+2 color='" + toHex(color) + "'>" + value + "</font></center></html>";
        tile.setText(html);
    }

    private String getTileTitle(JLabel tile) {
        String t = tile.getText();
        int b = t.indexOf("<br>");
        return b > 0 ? t.substring("<html><center>".length(), b) : t;
    }

    public void stopTimer() { updateTimer.stop(); }
}
