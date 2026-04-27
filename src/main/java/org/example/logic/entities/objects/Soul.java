package org.example.logic.entities.objects;
import java.awt.*;

public class Soul {
    public double x, y;
    public long expireTime;

    public Soul(double x, double y) {
        this.x = x; this.y = y;
        this.expireTime = System.currentTimeMillis() + 5000; // Hráč má 5 sekund na sebrání
    }

    public Rectangle getHitbox() { return new Rectangle((int)x, (int)y, 15, 15); }

    public void draw(Graphics2D g2) {
        long timeLeft = expireTime - System.currentTimeMillis();
        if (timeLeft < 0) return;

        // Postupné mizení (průhlednost)
        int alpha = (int)(255 * (timeLeft / 5000.0));
        g2.setColor(new Color(0, 255, 150, Math.max(0, alpha))); // Svítivě tyrkysová
        g2.fillOval((int)x, (int)y, 15, 15);
        g2.setColor(Color.WHITE);
        g2.drawOval((int)x, (int)y, 15, 15);
    }

    public boolean isExpired() { return System.currentTimeMillis() > expireTime; }
}