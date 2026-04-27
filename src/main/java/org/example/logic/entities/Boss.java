package org.example.logic.entities;

import org.example.logic.entities.objects.Projectile;

import java.awt.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Boss {
    public double x, y;
    public int width = 120, height = 120;

    // HP nastaveno na 20 000 (S komby a upgrady to půjde rychle dolů, ale štít minionů ho bude chránit)
    public int maxHp = 20000;
    public int hp = maxHp;

    private long lastAttackTime = 0;
    private int attackPhase = 0; // 0 = 100%, 1 = 75%, 2 = 50%, 3 = 25%

    public boolean isBlocking = false;
    private long blockEndTime = 0;

    // --- LASER MECHANIKA ---
    public boolean isLaserActive = false;
    public double laserX = 0;
    private int laserDirection = 1;
    private long laserEndTime = 0;

    // --- AOE (METEOR) MECHANIKA ---
    private class AoeZone {
        double x, y;
        long explodeTime;
        int radius = 60; // Větší a nebezpečnější zóna

        public AoeZone(double x, double y, long explodeTime) {
            this.x = x;
            this.y = y;
            this.explodeTime = explodeTime;
        }
    }
    private CopyOnWriteArrayList<AoeZone> aoeZones = new CopyOnWriteArrayList<>();

    public Boss(int screenWidth) {
        this.x = (screenWidth / 2.0) - (width / 2.0);
        this.y = 50; // Staticky nahoře uprostřed
    }

    public void update(Player player, CopyOnWriteArrayList<Projectile> enemyProjectiles) {
        long currentTime = System.currentTimeMillis();

        // 1. Zjištění fáze podle HP
        double hpPercent = (double) hp / maxHp;

        // ZRYCHLENÉ ÚTOKY PRO VĚTŠÍ VÝZVU
        long attackCooldown = 1500;

        if (hpPercent <= 0.75 && hpPercent > 0.5) {
            attackPhase = 1;
            attackCooldown = 1200; // Rychlejší + Nova Attack
        } else if (hpPercent <= 0.5 && hpPercent > 0.25) {
            attackPhase = 2;
            attackCooldown = 900;  // Velmi rychlé + Štít + Širší spread
        } else if (hpPercent <= 0.25) {
            attackPhase = 3;
            attackCooldown = 600;  // Brutální Bullet Hell rychlost + AOE
        }

        // 2. Konec štítu / Laseru
        if (isBlocking && currentTime > blockEndTime) isBlocking = false;

        if (isLaserActive) {
            laserX += 16 * laserDirection; // Extrémně rychlý laser
            if (currentTime > laserEndTime) isLaserActive = false;

            // Kolize hráče s laserem
            if (player.x + player.size > laserX - 25 && player.x < laserX + 25) {
                player.takeDamage(20); // Zvýšené poškození
            }
        }

        // 3. Kontrola AOE Zón (Exploze)
        for (AoeZone zone : aoeZones) {
            if (currentTime > zone.explodeTime) {
                // Zjištění kolize zóny s hráčem
                double dist = Math.hypot((player.x + player.size/2.0) - zone.x, (player.y + player.size/2.0) - zone.y);
                if (dist < zone.radius + player.size/2.0) {
                    player.takeDamage(40); // 40 DMG = smrtící rána, pokud hráč neuhne!
                }
                aoeZones.remove(zone);
            }
        }

        // 4. Výběr Útoků
        if (currentTime - lastAttackTime > attackCooldown && !isLaserActive) {

            int maxAttackTypes = 2;
            if (attackPhase >= 1) maxAttackTypes = 3;
            if (attackPhase >= 2) maxAttackTypes = 4;
            if (attackPhase >= 3) maxAttackTypes = 5;

            int attackType = (int) (Math.random() * maxAttackTypes);

            if (attackType == 0) {
                // Vystřelí 3, 5 nebo 7 projektilů do vějíře podle fáze
                int bullets = attackPhase >= 3 ? 3 : (attackPhase >= 2 ? 2 : 1);
                for(int i = -bullets; i <= bullets; i++) {
                    enemyProjectiles.add(new Projectile(x + width/2.0, y + height, player.x + (i*60), player.y, 1, true));
                }

            } else if (attackType == 1 && !isLaserActive) {
                // PREDITKIVNÍ LASER: Objeví se na straně, kde je hráč, aby ho donutil změnit směr
                isLaserActive = true;
                laserDirection = (player.x > 400) ? 1 : -1;
                laserX = (laserDirection == 1) ? -50 : 850;
                laserEndTime = currentTime + 3000;

            } else if (attackType == 2) {
                // BULLET HELL NOVA (Hustší kruh projektilů)
                for(int angle = 0; angle < 360; angle += 20) { // Každých 20 stupňů = méně místa na úhyb
                    double rad = Math.toRadians(angle);
                    double targetX = x + width/2.0 + Math.cos(rad) * 100;
                    double targetY = y + height/2.0 + Math.sin(rad) * 100;
                    enemyProjectiles.add(new Projectile(x + width/2.0, y + height/2.0, targetX, targetY, 1, true));
                }

            } else if (attackType == 3 && !isBlocking) {
                // Blokace vlastním štítem
                isBlocking = true;
                blockEndTime = currentTime + 2000;

            } else if (attackType == 4) {
                // AOE ZÓNA (Vytvoří bombu přímo pod hráčem s velmi krátkou dobou na útěk)
                aoeZones.add(new AoeZone(player.x + player.size/2.0, player.y + player.size/2.0, currentTime + 1200));
            }

            lastAttackTime = currentTime;
        }
    }

    public int getLifestealAmount() {
        if (attackPhase == 3) return 5; // V nejtěžší fázi se boss healuje méně, aby se dal dorazit
        return 15;
    }

    public void draw(Graphics2D g2, int screenHeight) {
        // Vykreslení varovných AOE zón na zemi
        for (AoeZone zone : aoeZones) {
            long timeLeft = zone.explodeTime - System.currentTimeMillis();
            if (timeLeft > 0) {
                // Agresivní blikající efekt před výbuchem
                int alpha = (int) (100 + Math.sin(timeLeft / 40.0) * 50);
                g2.setColor(new Color(255, 0, 0, Math.min(255, Math.max(0, alpha))));
                g2.fillOval((int)zone.x - zone.radius, (int)zone.y - zone.radius, zone.radius*2, zone.radius*2);

                g2.setColor(Color.RED);
                g2.setStroke(new BasicStroke(2));
                g2.drawOval((int)zone.x - zone.radius, (int)zone.y - zone.radius, zone.radius*2, zone.radius*2);
                g2.setStroke(new BasicStroke(1));
            }
        }

        // Vlastní štít bosse (odlišný od zlatého štítu z minionů, ten se kreslí v GamePanelu)
        if (isBlocking) {
            g2.setColor(new Color(0, 200, 255, 100));
            g2.fillOval((int)x - 20, (int)y - 20, width + 40, height + 40);
        }

        // Tělo bosse
        g2.setColor(new Color(150, 0, 0));
        g2.fillRect((int) x, (int) y, width, height);

        // Laser
        if (isLaserActive) {
            // Široký, poloprůhledný okraj laseru
            g2.setColor(new Color(255, 0, 0, 150));
            g2.fillRect((int)laserX - 25, (int)y + height, 50, screenHeight);

            // Zářící smrtící střed laseru (Bílý)
            g2.setColor(new Color(255, 255, 255, 255));
            g2.fillRect((int)laserX - 5, (int)y + height, 10, screenHeight);
        }

        // HP Bar Bosse
        g2.setColor(Color.BLACK);
        g2.fillRect((int)x, (int)y - 20, width, 10);
        g2.setColor(Color.RED);
        g2.fillRect((int)x, (int)y - 20, (int)((hp / (double)maxHp) * width), 10);
    }

    public Rectangle getHitbox() {
        return new Rectangle((int) x, (int) y, width, height);
    }
}