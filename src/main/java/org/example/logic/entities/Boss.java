package org.example.logic.entities;

import org.example.logic.entities.objects.Projectile;

import java.awt.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Boss {
    public enum Variant { SHADOW, FINAL }

    public double x, y;
    public int width, height;
    public final Variant variant;
    public final String displayName;

    public int maxHp;
    public int hp;

    private long lastAttackTime = 0;
    private int attackPhase = 0; // 0 = 100%, 1 = 75%, 2 = 50%, 3 = 25%

    public boolean isBlocking = false;
    private long blockEndTime = 0;

    // --- LASER MECHANIKA ---
    public boolean isLaserActive = false;
    public double laserX = 0;
    private int laserDirection = 1;
    private long laserEndTime = 0;

    // --- CHARGE DASH (exkluzivní útok finálního bosse) ---
    public boolean isChargingAttack = false;
    private double chargeTargetX = 0;
    private long chargeEndTime = 0;
    private boolean chargeHasHit = false;

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
        this(screenWidth, Variant.SHADOW);
    }

    public Boss(int screenWidth, Variant variant) {
        this.variant = variant;
        if (variant == Variant.FINAL) {
            this.width = 140;
            this.height = 140;
            // Sníženo z 20 000 - bez kombo zbraně (RNG-závislé) šlo o desítky minut
            // boj proti damage sponge. Finální boss je ale těžší než ten první.
            this.maxHp = 18000;
            this.displayName = "Temný Stín: Probuzení";
        } else {
            this.width = 120;
            this.height = 120;
            this.maxHp = 12000;
            this.displayName = "Temný Stín";
        }
        this.hp = maxHp;
        this.x = (screenWidth / 2.0) - (width / 2.0);
        this.y = 50; // Staticky nahoře uprostřed
    }

    public void update(Player player, CopyOnWriteArrayList<Projectile> enemyProjectiles) {
        long currentTime = System.currentTimeMillis();

        // 1. Zjištění fáze podle HP
        double hpPercent = (double) hp / maxHp;

        // ZRYCHLENÉ ÚTOKY PRO VĚTŠÍ VÝZVU (finální boss je o něco rychlejší)
        long speedBonus = (variant == Variant.FINAL) ? 200 : 0;
        long attackCooldown = 1500 - speedBonus;

        if (hpPercent <= 0.75 && hpPercent > 0.5) {
            attackPhase = 1;
            attackCooldown = 1200 - speedBonus; // Rychlejší + Nova Attack
        } else if (hpPercent <= 0.5 && hpPercent > 0.25) {
            attackPhase = 2;
            attackCooldown = 900 - speedBonus;  // Velmi rychlé + Štít + Širší spread
        } else if (hpPercent <= 0.25) {
            attackPhase = 3;
            attackCooldown = 600 - speedBonus;  // Brutální Bullet Hell rychlost + AOE
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

        // 2b. Charge dash (jen finální boss)
        if (isChargingAttack) {
            double dx = chargeTargetX - x;
            double step = Math.signum(dx) * 18;
            if (Math.abs(dx) <= Math.abs(step)) {
                x = chargeTargetX;
                isChargingAttack = false;
            } else {
                x += step;
            }
            if (!chargeHasHit && getHitbox().intersects(player.getHitbox())) {
                player.takeDamage(35);
                chargeHasHit = true;
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
        if (currentTime - lastAttackTime > attackCooldown && !isLaserActive && !isChargingAttack) {

            int maxAttackTypes = 2;
            if (attackPhase >= 1) maxAttackTypes = 3;
            if (attackPhase >= 2) maxAttackTypes = 4;
            if (attackPhase >= 3) maxAttackTypes = 5;
            if (variant == Variant.FINAL && attackPhase >= 1) maxAttackTypes = 6; // + charge dash

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
                // BULLET HELL NOVA (Hustší kruh projektilů, finální boss má ještě hustší)
                int step = (variant == Variant.FINAL) ? 15 : 20;
                for(int angle = 0; angle < 360; angle += step) {
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

            } else if (attackType == 5 && variant == Variant.FINAL) {
                // CHARGE DASH: boss se rychle přesune přes celou obrazovku ve směru hráče
                isChargingAttack = true;
                chargeHasHit = false;
                chargeTargetX = (player.x > x) ? Math.min(x + 500, 900) : Math.max(x - 500, -140);
                chargeEndTime = currentTime + 900;
            }

            lastAttackTime = currentTime;
        }
    }

    public int getLifestealAmount() {
        // Sníženo - lifesteal měl při agresivním útočení skoro rušit smysl
        // uhýbání útokům, což je u bullet-hell bosse kontraproduktivní.
        if (attackPhase == 3) return 2;
        return 8;
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

        if (isChargingAttack) {
            g2.setColor(new Color(255, 255, 255, 120));
            g2.fillRect((int)x - 10, (int)y - 10, width + 20, height + 20);
        }

        // Tělo bosse - finální boss je odlišen barvou, aby byl na první pohled jiný souboj
        g2.setColor(variant == Variant.FINAL ? new Color(80, 0, 120) : new Color(150, 0, 0));
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

        // Jméno bosse
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("Arial", Font.BOLD, 14));
        g2.drawString(displayName, (int) x, (int) y - 25);

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
