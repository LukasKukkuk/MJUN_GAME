package org.example.logic.entities.objects;

import java.awt.*;

public class Hazard {
    public enum Type { SPIKE, LAVA, BARREL }

    public double x, y;
    public final int size = 40;
    public Type type;
    public int hp;
    public boolean isActive = true;

    private long lastToggleTime = System.currentTimeMillis();
    private final long toggleInterval = 1500;

    public Hazard(double x, double y, Type type) {
        this.x = x;
        this.y = y;
        this.type = type;
        if (type == Type.BARREL) {
            this.hp = 30;
        }
    }

    public void update() {
        if (type == Type.SPIKE) {
            if (System.currentTimeMillis() - lastToggleTime >= toggleInterval) {
                isActive = !isActive;
                lastToggleTime = System.currentTimeMillis();
            }
        }
    }

    public Rectangle getHitbox() {
        return new Rectangle((int) x, (int) y, size, size);
    }

    public void draw(Graphics2D g2) {
        switch (type) {
            case LAVA -> {
                g2.setColor(new Color(200, 60, 0, 180));
                g2.fillRect((int) x, (int) y, size, size);
                g2.setColor(Color.ORANGE);
                g2.drawRect((int) x, (int) y, size, size);
            }
            case SPIKE -> {
                g2.setColor(isActive ? Color.LIGHT_GRAY : new Color(120, 120, 120, 120));
                int[] xs = {(int) x, (int) x + size / 2, (int) x + size};
                int[] ys = {(int) y + size, (int) y, (int) y + size};
                g2.fillPolygon(xs, ys, 3);
                g2.setColor(Color.DARK_GRAY);
                g2.drawPolygon(xs, ys, 3);
            }
            case BARREL -> {
                g2.setColor(new Color(139, 69, 19));
                g2.fillRoundRect((int) x, (int) y, size, size, 8, 8);
                g2.setColor(Color.BLACK);
                g2.drawRoundRect((int) x, (int) y, size, size, 8, 8);
                g2.setColor(Color.RED);
                int hpW = (int) ((hp / 30.0) * size);
                if (hpW < 0) hpW = 0;
                g2.fillRect((int) x, (int) y - 6, hpW, 4);
            }
        }
    }
}
