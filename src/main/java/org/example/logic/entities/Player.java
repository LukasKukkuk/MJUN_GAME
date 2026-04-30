package org.example.logic.entities;

import java.awt.*;

public class Player {
    public double x, y;
    public int size = 30;
    public double speed = 4.0;
    public int maxHp = 100;
    public int hp = maxHp;

    public int level = 1;
    public int bonusDamage = 0;
    public double bonusSpeed = 0.0;

    public int activeWeapon = 1;

    private long lastAbilityTime = 0;
    private long lastWeaponSwapTime = 0;

    public boolean isShieldActive = false;
    private long shieldEndTime = 0;

    public boolean isFireAuraActive = false;
    private long fireAuraEndTime = 0;
    public final int AURA_RADIUS = 80;

    // --- DASH MECHANIKA ---
    public boolean isDashing = false;
    private long dashEndTime = 0;
    public long lastDashTime = 0;
    public final long DASH_COOLDOWN = 1500; // Úskok každé 1.5 vteřiny

    private Image[] walkAnim;
    private int animFrame = 0;
    private long lastAnimTime = 0;

    public Player(double x, double y) {
        this.x = x; this.y = y;
    }

    public void setAnimations(Image[] anims) { this.walkAnim = anims; }

    public void update(boolean up, boolean down, boolean left, boolean right, int width, int height, boolean inverted) {
        long currentTime = System.currentTimeMillis();

        // Konec štítu a aury
        if (isShieldActive && currentTime > shieldEndTime) isShieldActive = false;
        if (isFireAuraActive && currentTime > fireAuraEndTime) isFireAuraActive = false;

        // Konec dashe
        if (isDashing && currentTime > dashEndTime) isDashing = false;

        // Během dashe je rychlost 3x větší!
        double currentSpeed = (speed + bonusSpeed) * (isDashing ? 3.0 : 1.0);

        boolean moving = false;
        if (up) { y -= inverted ? -currentSpeed : currentSpeed; moving = true; }
        if (down) { y += inverted ? -currentSpeed : currentSpeed; moving = true; }
        if (left) { x -= inverted ? -currentSpeed : currentSpeed; moving = true; }
        if (right) { x += inverted ? -currentSpeed : currentSpeed; moving = true; }

        if (x < 0) x = 0; if (y < 0) y = 0;
        if (x > width - size) x = width - size;
        if (y > height - size) y = height - size;

        if (moving && walkAnim != null && currentTime - lastAnimTime > 200) {
            animFrame = (animFrame + 1) % walkAnim.length;
            lastAnimTime = currentTime;
        } else if (!moving) {
            animFrame = 0;
        }
    }

    public void performDash(boolean up, boolean down, boolean left, boolean right) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastDashTime > DASH_COOLDOWN && (up || down || left || right)) {
            isDashing = true;
            dashEndTime = currentTime + 200; // Dash trvá 0.2 vteřiny (i-frames)
            lastDashTime = currentTime;
        }
    }

    // Ignorujeme damage během Dashe (i-frames)
    public void takeDamage(int amount) {
        if (!isDashing && !isShieldActive) {
            this.hp -= amount;
        }
    }

    public void draw(Graphics2D g2) {
        if (isFireAuraActive) {
            g2.setColor(new Color(255, 100, 0, 100));
            g2.fillOval((int) x + size/2 - AURA_RADIUS, (int) y + size/2 - AURA_RADIUS, AURA_RADIUS*2, AURA_RADIUS*2);
            g2.setColor(Color.RED);
            g2.drawOval((int) x + size/2 - AURA_RADIUS, (int) y + size/2 - AURA_RADIUS, AURA_RADIUS*2, AURA_RADIUS*2);
        }

        if (isShieldActive) {
            g2.setColor(new Color(0, 255, 255, 150));
            g2.fillOval((int) x - 10, (int) y - 10, size + 20, size + 20);
        }

        // Efekt "ducha" při Dashi
        if (isDashing) {
            g2.setColor(new Color(255, 255, 255, 100));
            g2.fillRect((int) x, (int) y, size, size);
        }

        if (walkAnim != null && walkAnim[animFrame] != null) {
            g2.drawImage(walkAnim[animFrame], (int) x, (int) y, size, size, null);
        } else {
            g2.setColor(Color.BLUE);
            g2.fillRect((int) x, (int) y, size, size);
        }

        g2.setColor(Color.BLACK); g2.fillRect((int) x, (int) y - 10, size, 5);
        g2.setColor(Color.GREEN); g2.fillRect((int) x, (int) y - 10, (int) ((hp / (double) maxHp) * size), 5);
    }

    public Rectangle getHitbox() { return new Rectangle((int) x, (int) y, size, size); }
    public boolean canUseAbility() { return System.currentTimeMillis() - lastAbilityTime > 0; }
    public void useAbility(long cooldownMs) { this.lastAbilityTime = System.currentTimeMillis() + cooldownMs; }
    public long getRemainingCooldown() { return Math.max(0, lastAbilityTime - System.currentTimeMillis()); }
    public void swapWeapon(int weaponId, int currentLevel) { if (System.currentTimeMillis() - lastWeaponSwapTime > 500) { this.activeWeapon = weaponId; this.lastWeaponSwapTime = System.currentTimeMillis(); } }
    public long getSwapCooldown() { return Math.max(0, (lastWeaponSwapTime + 500) - System.currentTimeMillis()); }
    public void activateShield(long duration, java.util.concurrent.CopyOnWriteArrayList<Enemy> enemies) { this.isShieldActive = true; this.shieldEndTime = System.currentTimeMillis() + duration; useAbility(8000); }
    public void activateFireAura(long duration) { this.isFireAuraActive = true; this.fireAuraEndTime = System.currentTimeMillis() + duration; useAbility(10000); }
}