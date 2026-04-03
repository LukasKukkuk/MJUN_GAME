package org.example.logic;

import org.example.logic.enums.Type;

import java.awt.*;
import java.util.List;

public class Enemy {
    public double x, y;
    public int size = 30;
    public double speed;
    public int maxHp;
    public int hp;
    public Type type;

    private long freezeEndTime = 0;
    private long dotEndTime = 0;
    private long lastDotTickTime = 0;

    // Proměnné pro střely nepřátel
    private long lastShootTime = 0;
    private long shootCooldown;

    public Enemy(double x, double y, Type type, int currentWave) {
        this.x = x;
        this.y = y;
        this.type = type;

        // Škálování podle vln (v Endless módu mají mnohem více životů)
        int waveBonusHp = (currentWave > 3) ? (currentWave - 3) * 20 : 0;

        if (type == Type.THIEVES) {
            this.maxHp = 100 + waveBonusHp;
            this.speed = 1.5 + (currentWave * 0.05);
        } else if (type == Type.BANDITS) {
            this.maxHp = 120 + waveBonusHp;
            this.speed = 1.2 + (currentWave * 0.05);
            this.shootCooldown = 2500; // Střílí každé 2.5s
        } else if (type == Type.ARCHERS) {
            this.maxHp = 70 + waveBonusHp;
            this.speed = 1.8 + (currentWave * 0.05); // Jsou rychlí
            this.shootCooldown = 1500; // Střílí každých 1.5s
        }

        this.hp = this.maxHp;
    }

    public void freeze(long durationMillis) {
        freezeEndTime = System.currentTimeMillis() + durationMillis;
    }

    public boolean isFrozen() {
        return System.currentTimeMillis() < freezeEndTime;
    }

    public void startDotDamage(long durationMillis) {
        dotEndTime = System.currentTimeMillis() + durationMillis;
        lastDotTickTime = System.currentTimeMillis();
    }

    // Předáváme i list nepřátelských střel, aby je nepřátelé mohli tvořit
    public void update(double targetX, double targetY, List<Enemy> allEnemies, List<Projectile> enemyProjectiles) {
        if (System.currentTimeMillis() < dotEndTime) {
            if (System.currentTimeMillis() - lastDotTickTime >= 250) {
                hp -= 1;
                lastDotTickTime = System.currentTimeMillis();
            }
        }

        if (isFrozen()) return;

        double dx = targetX - x;
        double dy = targetY - y;
        double distance = Math.sqrt(dx * dx + dy * dy);

        double moveX = 0;
        double moveY = 0;

        // POHYB PODLE TYPU
        if (type == Type.THIEVES || type == Type.BANDITS) {
            if (distance > 0) {
                moveX = (dx / distance);
                moveY = (dy / distance);
            }
        } else if (type == Type.ARCHERS) {
            if (distance < 200) {
                // Uteč od hráče, pokud je moc blízko
                moveX = -(dx / distance);
                moveY = -(dy / distance);
            } else if (distance > 300) {
                // Přibliž se, pokud je moc daleko
                moveX = (dx / distance);
                moveY = (dy / distance);
            } else {
                // Kroužení kolem hráče, pokud je ve správné vzdálenosti
                moveX = -(dy / distance);
                moveY = (dx / distance);
            }
        }

        x += moveX * speed;
        y += moveY * speed;

        // STŘELBA BANDITŮ A ARCHERŮ
        long time = System.currentTimeMillis();
        if (type == Type.BANDITS && distance < 300 && time - lastShootTime > shootCooldown) {
            enemyProjectiles.add(new Projectile(x + size/2, y + size/2, targetX, targetY, 1, true));
            lastShootTime = time;
        } else if (type == Type.ARCHERS && distance < 450 && time - lastShootTime > shootCooldown) {
            enemyProjectiles.add(new Projectile(x + size/2, y + size/2, targetX, targetY, 1, true));
            lastShootTime = time;
        }

        // Zamezení procházení nepřátel sebou navzájem
        for (Enemy other : allEnemies) {
            if (other == this) continue;
            double distX = x - other.x;
            double distY = y - other.y;
            double dist = Math.sqrt(distX * distX + distY * distY);

            if (dist < size) {
                double overlap = size - dist;
                if (dist == 0) {
                    distX = Math.random() - 0.5; distY = Math.random() - 0.5;
                    dist = Math.sqrt(distX * distX + distY * distY);
                }
                x += (distX / dist) * overlap * 0.1;
                y += (distY / dist) * overlap * 0.1;
            }
        }
    }

    public void draw(Graphics2D g2) {
        if (isFrozen()) {
            if (System.currentTimeMillis() < dotEndTime) {
                g2.setColor(new Color(50, 255, 200));
            } else {
                g2.setColor(new Color(100, 200, 255));
            }
        } else {
            // Barva podle typu
            if (type == Type.THIEVES) g2.setColor(Color.RED);
            else if (type == Type.BANDITS) g2.setColor(Color.YELLOW);
            else if (type == Type.ARCHERS) g2.setColor(new Color(128, 0, 128)); // Fialová
        }

        g2.fillRect((int) x, (int) y, size, size);
        g2.setColor(Color.BLACK);
        g2.drawRect((int) x, (int) y - 10, size, 5);
        g2.setColor(Color.GREEN);
        int hpWidth = (int) ((hp / (double) maxHp) * size);
        if (hpWidth < 0) hpWidth = 0;
        g2.fillRect((int) x + 1, (int) y - 9, hpWidth - 1, 4);
    }

    public Rectangle getHitbox() {
        return new Rectangle((int) x, (int) y, size, size);
    }
}