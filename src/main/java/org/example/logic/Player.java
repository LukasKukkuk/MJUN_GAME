package org.example.logic;

import java.awt.*;
import java.util.List;

public class Player {
    public double x, y;
    public final int size = 30;
    public final double speed = 4.0;

    public int maxHp = 100;
    public int hp = maxHp;
    private long lastHitTime = 0;

    // --- SYSTÉM ÚROVNÍ ---
    public int level = 1;

    public int activeWeapon = 1;
    public boolean isShieldActive = false;
    private long shieldEndTime = 0;

    private long[] abilityCooldowns = {0, 0, 5000, 10000};
    private long[] lastAbilityUseTime = {0, 0, 0, 0};

    private long lastSwapTime = 0;
    private final long swapCooldown = 2500;

    private Image[] frames;
    private int currentFrameIndex = 0;
    private int frameCounter = 0;
    private int animationSpeed = 10;

    public Player(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void setAnimations(Image[] frames) {
        this.frames = frames;
    }

    public void updateAnimation(boolean isMoving) {
        if (frames == null || frames.length == 0 || frames[0] == null) return;
        if (isMoving) {
            frameCounter++;
            if (frameCounter >= animationSpeed) {
                currentFrameIndex = (currentFrameIndex + 1) % frames.length;
                frameCounter = 0;
            }
        } else {
            currentFrameIndex = 0;
            frameCounter = 0;
        }
    }

    public void update(boolean up, boolean down, boolean left, boolean right, int screenWidth, int screenHeight) {
        if (up && y > 0) y -= speed;
        if (down && y < screenHeight - size) y += speed;
        if (left && x > 0) x -= speed;
        if (right && x < screenWidth - size) x += speed;

        boolean isMoving = (up || down || left || right);
        updateAnimation(isMoving);

        if (isShieldActive && System.currentTimeMillis() > shieldEndTime) {
            isShieldActive = false;
        }
    }

    public void swapWeapon(int weaponIndex, int currentWave) {
        if (weaponIndex == 2 && currentWave < 2) return;
        if (weaponIndex == 3 && currentWave < 3) return;

        if (System.currentTimeMillis() - lastSwapTime >= swapCooldown) {
            activeWeapon = weaponIndex;
            lastSwapTime = System.currentTimeMillis();
        }
    }

    public void takeDamage(int amount) {
        if (System.currentTimeMillis() - lastHitTime > 1000 && !isShieldActive) {
            hp -= amount;
            lastHitTime = System.currentTimeMillis();
            if (hp < 0) hp = 0;
        }
    }

    public boolean canUseAbility() {
        return System.currentTimeMillis() - lastAbilityUseTime[activeWeapon] >= abilityCooldowns[activeWeapon];
    }

    public void useAbility() {
        lastAbilityUseTime[activeWeapon] = System.currentTimeMillis();
    }

    public long getRemainingCooldown() {
        long elapsed = System.currentTimeMillis() - lastAbilityUseTime[activeWeapon];
        long remaining = abilityCooldowns[activeWeapon] - elapsed;
        return remaining > 0 ? remaining : 0;
    }

    public long getSwapCooldown() {
        long elapsed = System.currentTimeMillis() - lastSwapTime;
        long remaining = swapCooldown - elapsed;
        return remaining > 0 ? remaining : 0;
    }

    // PŘIDÁNO: Parametr listu nepřátel pro rázovou vlnu
    public void activateShield(long durationMillis, List<Enemy> enemies) {
        isShieldActive = true;
        shieldEndTime = System.currentTimeMillis() + durationMillis;

        // LEVEL 3 ŠTÍT: Okamžitý knockback všech v okolí při spuštění
        if (level >= 3) {
            for (Enemy e : enemies) {
                double dx = e.x - this.x;
                double dy = e.y - this.y;
                double dist = Math.sqrt(dx * dx + dy * dy);
                if (dist < 150) { // Dosah rázové vlny
                    e.x += (dx / dist) * 150; // Síla odhození
                    e.y += (dy / dist) * 150;
                }
            }
        }
        useAbility();
    }

    public void draw(Graphics2D g2) {
        boolean isBlinking = System.currentTimeMillis() - lastHitTime < 1000 && (System.currentTimeMillis() / 100) % 2 == 0;

        if (isBlinking) {
            g2.setColor(Color.WHITE);
            g2.fillRect((int) x, (int) y, size, size);
        } else if (frames != null && frames.length > 0 && frames[currentFrameIndex] != null) {
            g2.drawImage(frames[currentFrameIndex], (int) x, (int) y, size, size, null);
        } else {
            g2.setColor(Color.BLUE);
            g2.fillRect((int) x, (int) y, size, size);
        }

        if (isShieldActive) {
            g2.setColor(new Color(173, 216, 230, 120));
            int shieldRadius = size + 40;
            g2.fillOval((int) x - 20, (int) y - 20, shieldRadius, shieldRadius);
            g2.setColor(Color.CYAN);
            g2.drawOval((int) x - 20, (int) y - 20, shieldRadius, shieldRadius);
        }

        g2.setColor(Color.BLACK);
        g2.drawRect((int) x, (int) y - 15, size, 6);
        g2.setColor(Color.GREEN);
        if (hp < 40) g2.setColor(Color.RED);
        int hpWidth = (int) ((hp / (double) maxHp) * size);
        if (hpWidth < 0) hpWidth = 0;
        g2.fillRect((int) x + 1, (int) y - 14, hpWidth - 1, 5);

        // Zobrazení úrovně
        g2.setColor(Color.YELLOW);
        g2.setFont(new Font("Arial", Font.BOLD, 10));
        g2.drawString("Lvl " + level, (int)x, (int)y - 20);
    }

    public Rectangle getHitbox() {
        return new Rectangle((int) x, (int) y, size, size);
    }
}