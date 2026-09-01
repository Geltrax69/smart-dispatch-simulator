package com.dispatch.ui;

import com.dispatch.model.*;
import com.dispatch.model.Location.LocationType;
import com.dispatch.sim.CityMap;
import com.dispatch.sim.SimulationEngine;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.util.List;
import java.util.*;

/**
 * Custom-painted map panel showing:
 *  - roads (grey lines)
 *  - locations (colored circles + labels)
 *  - drivers (colored triangles with driver ID)
 *  - active routes (dashed lines from driver to pickup/dropoff)
 */
public final class MapPanel extends JPanel {
    private static final Color BG = new Color(18, 24, 34);
    private static final Color ROAD = new Color(60, 70, 80);
    private static final Color RESTAURANT = new Color(255, 160, 50);
    private static final Color WAREHOUSE = new Color(100, 200, 255);
    private static final Color CUSTOMER = new Color(80, 220, 130);
    private static final Color DEPOT = new Color(220, 220, 100);
    private static final Color DRIVER_IDLE = new Color(80, 200, 255);
    private static final Color DRIVER_BUSY = new Color(255, 100, 80);
    private static final Color DRIVER_OFFLINE = new Color(100, 100, 100);
    private static final Color PICKUP_MARKER = new Color(255, 220, 80);
    private static final Color DROPOFF_MARKER = new Color(80, 255, 180);

    private final CityMap map;
    private final SimulationEngine sim;
    private final Timer repaintTimer;

    public MapPanel(CityMap map, SimulationEngine sim) {
        this.map = map;
        this.sim = sim;
        setBackground(BG);
        setBorder(BorderFactory.createLineBorder(new Color(40, 55, 70), 2));
        // Refresh at 20fps
        repaintTimer = new Timer(50, e -> repaint());
        repaintTimer.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int W = getWidth(), H = getHeight();
        if (W == 0 || H == 0) return;

        // Scale factor: map coords 0..900x0..600 → panel size
        double sx = (double) W / 900.0;
        double sy = (double) H / 600.0;

        List<Road> roads = map.getRoads();
        List<Location> locs = map.getLocations();
        List<Driver> drivers = sim.getDrivers();
        List<Order> orders = sim.getOrders();

        // 1. Roads
        g2.setStroke(new BasicStroke(1.5f));
        for (Road r : roads) {
            double tf = r.getTrafficFactor();
            int alpha = tf > 2.0 ? 200 : 140;
            g2.setColor(new Color(80, 90, 100, alpha));
            if (tf > 2.5) g2.setColor(new Color(255, 80, 80, 140));
            else if (tf > 1.5) g2.setColor(new Color(255, 180, 60, 160));
            g2.drawLine(scaleX(r.getA().getX(), sx), scaleY(r.getA().getY(), sy),
                        scaleX(r.getB().getX(), sx), scaleY(r.getB().getY(), sy));
        }

        // 2. Locations
        Map<Location, Color> locColor = new HashMap<>();
        for (Location loc : locs) {
            locColor.put(loc, switch (loc.getType()) {
                case RESTAURANT -> RESTAURANT;
                case WAREHOUSE  -> WAREHOUSE;
                case CUSTOMER  -> CUSTOMER;
                case DEPOT      -> DEPOT;
            });
        }
        for (Location loc : locs) {
            Color c = locColor.get(loc);
            int r = loc.getType() == LocationType.DEPOT ? 10 : 7;
            int sx_ = scaleX(loc.getX(), sx);
            int sy_ = scaleY(loc.getY(), sy);
            g2.setColor(c);
            g2.fill(new Ellipse2D.Double(sx_ - r, sy_ - r, r * 2, r * 2));
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Monaco", Font.PLAIN, 8));
            g2.drawString(loc.getName(), sx_ + r + 2, sy_ + 4);
        }

        // 3. Active routes (pickup and dropoff lines for assigned orders)
        g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER, 10.0f, new float[]{4, 4}, 0));
        for (Order o : orders) {
            if (o.getStatus() != Order.Status.ASSIGNED &&
                o.getStatus() != Order.Status.PICKED_UP) continue;
            String did = o.getAssignedDriverId();
            if (did == null) continue;
            int did_ = Integer.parseInt(did.substring(1));
            Driver d = drivers.stream().filter(dr -> dr.getId() == did_).findFirst().orElse(null);
            if (d == null) continue;
            // route: driver → pickup → dropoff
            g2.setColor(PICKUP_MARKER);
            g2.drawLine(scaleX(d.getPosition().getX(), sx), scaleY(d.getPosition().getY(), sy),
                        scaleX(o.getPickup().getX(), sx), scaleY(o.getPickup().getY(), sy));
            g2.setColor(DROPOFF_MARKER);
            g2.drawLine(scaleX(o.getPickup().getX(), sx), scaleY(o.getPickup().getY(), sy),
                        scaleX(o.getDropoff().getX(), sx), scaleY(o.getDropoff().getY(), sy));
        }

        // 4. Drivers
        for (Driver drv : drivers) {
            Location pos = drv.getPosition();
            int sx_ = scaleX(pos.getX(), sx);
            int sy_ = scaleY(pos.getY(), sy);
            Color dc = switch (drv.getStatus()) {
                case AVAILABLE, ASSIGNED, EN_ROUTE_PICKUP, DELIVERING -> DRIVER_BUSY;
                default -> DRIVER_OFFLINE;
            };
            g2.setColor(dc);
            // Triangle pointing in a fixed direction
            int[] xs = {sx_, sx_ - 7, sx_ + 7};
            int[] ys = {sy_ - 10, sy_ + 6, sy_ + 6};
            g2.fillPolygon(xs, ys, 3);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Monaco", Font.BOLD, 7));
            String label = "D" + drv.getId();
            int lw = g2.getFontMetrics().stringWidth(label);
            g2.drawString(label, sx_ - lw / 2, sy_ + 16);
        }

        // 5. Legend
        drawLegend(g2, W, H);
    }

    private void drawLegend(Graphics2D g2, int W, int H) {
        g2.setFont(new Font("Monaco", Font.PLAIN, 9));
        g2.setColor(new Color(30, 40, 55));
        g2.fillRect(5, H - 105, 180, 100);
        g2.setColor(Color.WHITE);
        int y = H - 92;
        drawDot(g2, 12, y, DEPOT);       g2.drawString("Depot", 24, y + 4); y += 14;
        drawDot(g2, 12, y, RESTAURANT);  g2.drawString("Restaurant", 24, y + 4); y += 14;
        drawDot(g2, 12, y, WAREHOUSE);   g2.drawString("Warehouse", 24, y + 4); y += 14;
        drawDot(g2, 12, y, CUSTOMER);    g2.drawString("Customer", 24, y + 4); y += 14;
        drawDot(g2, 12, y, DRIVER_BUSY); g2.drawString("Driver (busy)", 24, y + 4); y += 14;
        drawDot(g2, 12, y, DRIVER_OFFLINE); g2.drawString("Driver (offline)", 24, y + 4); y += 14;
    }

    private void drawDot(Graphics2D g2, int x, int y, Color c) {
        g2.setColor(c);
        g2.fill(new Ellipse2D.Double(x - 5, y - 5, 10, 10));
    }

    private int scaleX(int x, double sx) { return (int) (x * sx); }
    private int scaleY(int y, double sy) { return (int) (y * sy); }

    public void stopTimer() { repaintTimer.stop(); }
}
