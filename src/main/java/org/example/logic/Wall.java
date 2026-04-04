package org.example.logic;
import java.awt.*;

public class Wall {
    public double x, y;
    public int width = 80, height = 20; // Tvar zdi
    public int hp = 150; // Zničí se, když do ní budou bušit dlouho
    public long expireTime;

    public Wall(double x, double y) {
        this.x = x - width / 2.0; // Vykreslí se na středu myši
        this.y = y - height / 2.0;
        this.expireTime = System.currentTimeMillis() + 8000; // Zmizí sama za 8 sekund
    }

    public Rectangle getHitbox() { return new Rectangle((int)x, (int)y, width, height); }

    public void draw(Graphics2D g2) {
        g2.setColor(new Color(139, 69, 19)); // Hnědá
        g2.fillRect((int)x, (int)y, width, height);
        g2.setColor(Color.BLACK);
        g2.drawRect((int)x, (int)y, width, height);
    }

    public boolean isDead() { return hp <= 0 || System.currentTimeMillis() > expireTime; }
}