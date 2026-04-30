package org.example.logic.entities;

import org.example.logic.entities.objects.Projectile;
import org.example.logic.enums.Type;
import java.awt.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Enemy {
    public enum EnemyType { MELEE, SHOOTER, KAMIKAZE, TANK }

    public double x, y;
    public int size = 30;
    public double speed = 2.0;
    public int hp = 100;
    public int maxHp = 100;
    public Type type;
    public EnemyType enemyType;

    private boolean isFrozen = false;
    private long freezeEndTime = 0;
    private long dotEndTime = 0;
    private long lastDotTick = 0;
    private long lastShotTime = 0;

    public Enemy(double x, double y, Type type, int wave) {
        this.x = x; this.y = y; this.type = type;

        // Rozhodnutí o typu nepřítele na základě náhody a vlny
        double rand = Math.random();
        if (wave >= 2 && rand < 0.2) {
            this.enemyType = EnemyType.SHOOTER;
            this.speed = 1.5;
            this.hp = 80 + (wave * 15);
            this.color = Color.MAGENTA;
        } else if (wave >= 3 && rand > 0.8) {
            this.enemyType = EnemyType.KAMIKAZE;
            this.speed = 4.5; // Velmi rychlý
            this.hp = 50 + (wave * 10);
            this.color = Color.YELLOW;
            this.size = 20; // Menší
        } else if (wave >= 4 && rand > 0.6 && rand <= 0.8) {
            this.enemyType = EnemyType.TANK;
            this.speed = 1.0; // Pomalý
            this.hp = 300 + (wave * 40); // Hodně HP
            this.size = 50; // Velký
            this.color = new Color(0, 100, 0); // Tmavě zelená
        } else {
            this.enemyType = EnemyType.MELEE;
            this.speed = 2.0 + (wave * 0.1);
            this.hp = 100 + (wave * 20);
            this.color = Color.RED;
        }

        this.maxHp = this.hp;
    }

    private Color color;

    public void update(double px, double py, CopyOnWriteArrayList<Enemy> others, CopyOnWriteArrayList<Projectile> enemyProjectiles) {
        long currentTime = System.currentTimeMillis();

        if (isFrozen && currentTime > freezeEndTime) isFrozen = false;
        if (currentTime < dotEndTime && currentTime - lastDotTick > 500) { hp -= 5; lastDotTick = currentTime; }
        if (isFrozen) return;

        double dx = px - x, dy = py - y;
        double dist = Math.hypot(dx, dy);

        if (dist > 0) {
            // Logika střelce (udržuje odstup a střílí)
            if (enemyType == EnemyType.SHOOTER) {
                if (dist > 200) {
                    x += (dx / dist) * speed; y += (dy / dist) * speed;
                } else if (dist < 150) { // Utíká, když jsi moc blízko
                    x -= (dx / dist) * speed; y -= (dy / dist) * speed;
                }
                // Střelba
                if (currentTime - lastShotTime > 2000) {
                    enemyProjectiles.add(new Projectile(x + size/2.0, y + size/2.0, px, py, 1, true));
                    lastShotTime = currentTime;
                }
            } else {
                // Ostatní jdou přímo za hráčem
                x += (dx / dist) * speed; y += (dy / dist) * speed;
            }
        }

        // Vyhýbání se ostatním nepřátelům
        for (Enemy other : others) {
            if (other != this) {
                double odx = x - other.x, ody = y - other.y;
                double odist = Math.hypot(odx, ody);
                if (odist < size) { x += (odx / odist) * 1.5; y += (ody / odist) * 1.5; }
            }
        }
    }

    public void freeze(long duration) { this.isFrozen = true; this.freezeEndTime = System.currentTimeMillis() + duration; }
    public void startDotDamage(long duration) { this.dotEndTime = System.currentTimeMillis() + duration; }
    public boolean isFrozen() { return isFrozen; }

    public void draw(Graphics2D g2) {
        if (isFrozen) g2.setColor(Color.CYAN);
        else if (enemyType == EnemyType.KAMIKAZE && (System.currentTimeMillis() / 150) % 2 == 0) g2.setColor(Color.WHITE); // Kamikaze bliká
        else g2.setColor(color);

        g2.fillRect((int) x, (int) y, size, size);

        g2.setColor(Color.BLACK); g2.fillRect((int) x, (int) y - 10, size, 5);
        g2.setColor(Color.RED); g2.fillRect((int) x, (int) y - 10, (int) ((hp / (double) maxHp) * size), 5);
    }
    public Rectangle getHitbox() { return new Rectangle((int) x, (int) y, size, size); }
}