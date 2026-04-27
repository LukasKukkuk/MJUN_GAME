package org.example.logic.items;

import java.awt.*;

public class LootDrop {
    public double x, y;
    public Item item;

    private long spawnTime;
    private long despawnTime; // Doba, po které zmizí (např. 15 vteřin)
    public final int size = 20;

    public LootDrop(double x, double y, Item item) {
        this.x = x;
        this.y = y;
        this.item = item;
        this.spawnTime = System.currentTimeMillis();
        this.despawnTime = spawnTime + 15000; // 15 vteřin leží na zemi
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > despawnTime;
    }

    public void draw(Graphics2D g2) {
        // Blikání, když už brzy zmizí (poslední 3 vteřiny)
        long timeLeft = despawnTime - System.currentTimeMillis();
        if (timeLeft < 3000 && (timeLeft / 200) % 2 == 0) return;

        // Vypočítáme "levitaci" předmětu pomocí sinu
        double floatOffset = Math.sin((System.currentTimeMillis() - spawnTime) / 200.0) * 5.0;

        g2.setColor(item.color);
        g2.fillRect((int) x, (int) (y + floatOffset), size, size);
        g2.setColor(Color.WHITE);
        g2.drawRect((int) x, (int) (y + floatOffset), size, size);

        // Vykreslíme level předmětu do jeho středu
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 12));
        g2.drawString("L" + item.level, (int) x + 2, (int) (y + floatOffset) + 14);
    }

    public Rectangle getHitbox() {
        return new Rectangle((int) x, (int) y, size, size);
    }
}