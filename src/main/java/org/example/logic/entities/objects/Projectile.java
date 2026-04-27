package org.example.logic.entities.objects;

import java.awt.*;

public class Projectile {
    public double x, y;
    public double vx, vy;
    public int size = 10;
    public int type;
    public boolean isEnemy; // Rozpoznání, čí je to střela

    public Projectile(double startX, double startY, double targetX, double targetY, int type, boolean isEnemy) {
        this.x = startX;
        this.y = startY;
        this.type = type;
        this.isEnemy = isEnemy;

        // Nepřátelské střely letí trochu pomaleji, aby se daly uhnout
        double speed = isEnemy ? 4.5 : 8.0;

        double dx = targetX - startX;
        double dy = targetY - startY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        if (distance > 0) {
            this.vx = (dx / distance) * speed;
            this.vy = (dy / distance) * speed;
        }
    }

    public void update() {
        x += vx;
        y += vy;
    }

    public void draw(Graphics2D g2) {
        if (isEnemy) {
            g2.setColor(Color.MAGENTA); // Nepřátelské střely jsou fialové
            g2.fillOval((int) x, (int) y, size, size);
        } else {
            if (type == 1) g2.setColor(Color.ORANGE);
            else if (type == 2) g2.setColor(Color.CYAN);
            g2.fillOval((int) x, (int) y, size, size);
        }
    }

    public Rectangle getHitbox() {
        return new Rectangle((int) x, (int) y, size, size);
    }
}