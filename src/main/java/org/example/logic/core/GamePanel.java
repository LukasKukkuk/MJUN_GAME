package org.example.logic.core;

import org.example.Launcher;
import org.example.logic.entities.Boss;
import org.example.logic.entities.Enemy;
import org.example.logic.entities.Player;
import org.example.logic.entities.objects.Hazard;
import org.example.logic.entities.objects.Projectile;
import org.example.logic.entities.objects.Soul;
import org.example.logic.entities.objects.Wall;
import org.example.logic.managers.*;
import org.example.logic.enums.Type;
import org.example.logic.items.Item;
import org.example.logic.items.LootDrop;
import io.github.cdimascio.dotenv.Dotenv;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

public class GamePanel extends JPanel implements Runnable, KeyListener, MouseListener, MouseMotionListener {
    public int WIDTH = 800;
    public int HEIGHT = 600;

    private GameWindow window;
    private Thread gameThread;
    private boolean isRunning = false;
    private final int FPS = 60;

    private enum State { LOADING, MENU, PLAYING, SETTINGS, INVENTORY, VICTORY, CUTSCENE, MINIGAME }
    private State gameState = State.LOADING;

    private int loadingProgress = 0;
    private boolean loadingStarted = false;
    private long lastRpcUpdate = 0;

    private AudioManager audioManager;
    private WaveManager waveManager = new WaveManager();
    private DiscordManager discordManager;
    private SettingsScreen settingsScreen;

    private CutsceneManager cutsceneManager;
    private InventoryManager inventoryManager = new InventoryManager();

    private Player player;
    private boolean up, down, left, right;
    private boolean isGameOver = false;

    private Boss boss;

    private boolean isMousePressed = false;
    private int mouseTargetX = 0;
    private int mouseTargetY = 0;

    private CopyOnWriteArrayList<Enemy> enemies = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Projectile> projectiles = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Projectile> enemyProjectiles = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Wall> walls = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Soul> souls = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<LootDrop> lootDrops = new CopyOnWriteArrayList<>();

    // --- SEZNAM PASTÍ (HAZARDS) ---
    private CopyOnWriteArrayList<Hazard> hazards = new CopyOnWriteArrayList<>();

    private CopyOnWriteArrayList<DamageText> damageTexts = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Particle> particles = new CopyOnWriteArrayList<>();
    private int shakeX = 0, shakeY = 0, shakeDuration = 0, shakeIntensity = 0;
    private String activeWeaponInfo = "";

    private class DamageText {
        double x, y;
        String text;
        int alpha = 255;
        Color color;
        Font font;

        public DamageText(double x, double y, String text, Color color, boolean crit) {
            this.x = x + (Math.random() * 20 - 10);
            this.y = y + (Math.random() * 20 - 10);
            this.text = text;
            this.color = color;
            this.font = new Font("Arial", Font.BOLD, crit ? 24 : 16);
        }

        public void update() {
            y -= 1;
            alpha -= 5;
            if(alpha < 0) alpha = 0;
        }

        public void draw(Graphics2D g) {
            g.setFont(font);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            g.drawString(text, (int)x, (int)y);
        }
    }

    private class Particle {
        double x, y, dx, dy;
        int life = 255;
        Color color;

        public Particle(double x, double y, Color color) {
            this.x = x;
            this.y = y;
            this.color = color;
            this.dx = (Math.random() - 0.5) * 8;
            this.dy = (Math.random() - 0.5) * 8;
        }

        public void update() {
            x += dx;
            y += dy;
            life -= 10;
            if(life < 0) life = 0;
        }

        public void draw(Graphics2D g) {
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), life));
            g.fillRect((int)x, (int)y, 4, 4);
        }
    }

    private int mgCursorX = 0;
    private int mgCursorDir = 1;
    private double mgSpeed = 6.0;
    private int mgTargetX = 200;
    private int mgTargetW = 100;
    private int mgSuccessHits = 0;
    private String mgMessage = "Kovadlina je připravena...";

    private String discordMsg = "";
    private long msgTimer = 0;
    private boolean invertedControls = false;
    private long trollTimer = 0;

    private long lastAuraDamageTime = 0;
    private long swapTextTimer = 0;
    private int currentWave = 1;
    private boolean showTutorial = true;
    private Image[] playerWalkAnim;

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.DARK_GRAY);
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(this);

        new Thread(() -> {
            try {
                DiscordRPCManager.start();
                Thread.sleep(1000);
                Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
                String token = dotenv.get("DISCORD_TOKEN");

                if (token != null && !token.isEmpty()) {
                    discordManager = new DiscordManager(this, "ws://212.227.7.153:11536");
                    discordManager.connect();

                    String discordId = DiscordRPCManager.getUserId();
                    if (discordId != null && !discordId.isEmpty()) {
                        System.out.println("🌐 Odesílám Discord ID botovi na hostingu: " + discordId);
                    }
                }
            } catch (Exception e) {
                System.out.println("⚠️ Nepodařilo se připojit k botovi: " + e.getMessage());
            }
        }).start();
    }

    public void setWindow(GameWindow window) {
        this.window = window;
    }

    public void updateDimensions(int w, int h) {
        this.WIDTH = w;
        this.HEIGHT = h;
        setPreferredSize(new Dimension(w, h));
        revalidate();
    }

    private Image loadImage(String path) {
        URL imageUrl = getClass().getResource(path);
        return (imageUrl != null) ? new ImageIcon(imageUrl).getImage() : null;
    }

    private void triggerShake(int duration, int intensity) {
        this.shakeDuration = duration;
        this.shakeIntensity = intensity;
    }

    // --- GENEROVÁNÍ PASTÍ ---
    private void generateHazards() {
        hazards.clear();
        if (currentWave >= 2) {
            int numHazards = Math.min(currentWave + 1, 6); // Max 6 pastí
            for(int i = 0; i < numHazards; i++) {
                double hx = Math.random() * (WIDTH - 100) + 50;
                double hy = Math.random() * (HEIGHT - 100) + 50;
                // Past nesmí vzniknout přímo na hráči ve středu
                if (Math.hypot(hx - WIDTH/2.0, hy - HEIGHT/2.0) > 150) {
                    Hazard.Type type;
                    double rand = Math.random();
                    if (rand > 0.6) type = Hazard.Type.SPIKE;
                    else if (rand > 0.3) type = Hazard.Type.LAVA;
                    else type = Hazard.Type.BARREL;

                    hazards.add(new Hazard(hx, hy, type));
                }
            }
        }
    }

    private void resetGame(int startWave) {
        currentWave = startWave;
        player = new Player(WIDTH / 2.0, HEIGHT / 2.0);
        player.level = 1;
        player.setAnimations(playerWalkAnim);

        enemies.clear();
        projectiles.clear();
        enemyProjectiles.clear();
        walls.clear();
        souls.clear();
        lootDrops.clear();
        damageTexts.clear();
        particles.clear();

        generateHazards();

        inventoryManager = new InventoryManager();

        boss = null;
        isGameOver = false;
        showTutorial = true;
        isMousePressed = false;

        updateWeaponInfo();

        ConfigManager.save(currentWave);
        audioManager.playMusicForWave(Math.min(currentWave, 3));

        waveManager.startNextWave(currentWave);
        gameState = State.PLAYING;
    }

    public void startGame() {
        if (gameThread == null) {
            isRunning = true;
            gameThread = new Thread(this);
            gameThread.start();
        }
    }

    private Item generateRandomItem() {
        int rand = (int) (Math.random() * 100);
        if (rand < 20) return Item.createWeaponShard();
        if (rand < 35) return Item.createHealthHeart();
        if (rand < 50) return Item.createDamageSword();
        if (rand < 65) return Item.createSpeedBoots();
        if (rand < 75) return Item.createBerserkerPotion();
        if (rand < 85) return Item.createIceCrystal();
        if (rand < 93) return Item.createWindCrystal();
        return Item.createFireCrystal();
    }

    public void spawnSoulFromDiscord() {
        if (gameState != State.PLAYING || isGameOver || player == null) return;
        double rx = player.x + (Math.random() * 200 - 100);
        double ry = player.y + (Math.random() * 200 - 100);
        souls.add(new Soul(rx, ry));
    }

    public void spawnEnemyFromDiscord() {
        if (gameState != State.PLAYING || isGameOver || player == null) return;
        enemies.add(new Enemy(player.x + (Math.random() * 400 - 200), player.y + (Math.random() * 400 - 200), Type.THIEVES, currentWave));
    }

    public void spawnHorde() {
        new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) { spawnEnemyFromDiscord(); Thread.sleep(200); }
            } catch (InterruptedException e) {}
        }).start();
    }

    public void rageModeFromDiscord() {
        if (gameState != State.PLAYING || isGameOver) return;
        for (Enemy enemy : enemies) { enemy.hp += 50; }
        for (int i = 0; i < 3; i++) spawnEnemyFromDiscord();
    }

    public void freezeEnemiesFromDiscord() {
        if (gameState != State.PLAYING || isGameOver) return;
        for (Enemy enemy : enemies) { enemy.freeze(3000); }
    }

    public void triggerDiscordAction(String text, Runnable action) {
        this.discordMsg = text;
        this.msgTimer = System.currentTimeMillis() + 4000;
        action.run();
    }

    public void activateTrollMode(int durationMs) {
        this.invertedControls = true;
        this.trollTimer = System.currentTimeMillis() + durationMs;
    }

    @Override
    public void run() {
        double drawInterval = 1000000000.0 / FPS;
        double delta = 0;
        long lastTime = System.nanoTime();

        while (isRunning) {
            long currentTime = System.nanoTime();
            delta += (currentTime - lastTime) / drawInterval;
            lastTime = currentTime;

            if (delta >= 1) {
                if (gameState == State.LOADING) {
                    if (!loadingStarted) {
                        loadingStarted = true;
                        new Thread(() -> {
                            loadingProgress = 10;
                            playerWalkAnim = new Image[]{ loadImage("/player_walk1.png"), loadImage("/player_walk2.png") };
                            loadingProgress = 30;
                            Launcher.audioManager = new AudioManager();
                            audioManager = Launcher.audioManager;

                            cutsceneManager = new CutsceneManager(audioManager);

                            audioManager.preloadAudio();
                            audioManager.setVolume(ConfigManager.volume);
                            loadingProgress = 80;

                            if (window != null) { settingsScreen = new SettingsScreen(audioManager, window); }

                            loadingProgress = 100;
                            try { Thread.sleep(300); } catch (Exception e) {}
                            audioManager.playMenuMusic();
                            gameState = State.MENU;
                        }).start();
                    }
                }
                else if (gameState == State.PLAYING && !isGameOver || gameState == State.CUTSCENE || gameState == State.MINIGAME || gameState == State.INVENTORY) {
                    update();
                }
                repaint();
                delta--;
            }
        }
    }

    private void proceedToNextWave() {
        currentWave++;
        ConfigManager.save(currentWave);
        audioManager.playMusicForWave(Math.min(currentWave, 3));

        if (player.activeWeapon == 2 && player.level < 2) player.activeWeapon = 1;
        if (player.activeWeapon == 3 && player.level < 3) player.activeWeapon = 1;
        if (player.activeWeapon == 4 && player.level < 4) player.activeWeapon = 1;

        generateHazards();
        updateWeaponInfo();
        waveManager.startNextWave(currentWave);
        gameState = State.PLAYING;
    }

    private void startMinigame() {
        gameState = State.MINIGAME;
        mgCursorX = 0; mgCursorDir = 1; mgSpeed = 6.0; mgSuccessHits = 0; mgTargetW = 120;
        mgTargetX = (int) (Math.random() * (400 - mgTargetW)) + 100;
        mgMessage = "Zasáhni zelenou zónu (MEZERNÍK)!";
    }

    private void update() {
        if (gameState == State.CUTSCENE || gameState == State.INVENTORY) {
            if (gameState == State.CUTSCENE) cutsceneManager.update();
            return;
        }

        if (gameState == State.MINIGAME) {
            mgCursorX += mgSpeed * mgCursorDir;
            if (mgCursorX <= 0 || mgCursorX >= 600) mgCursorDir *= -1;
            return;
        }

        if (shakeDuration > 0) {
            shakeX = (int)(Math.random() * shakeIntensity - shakeIntensity / 2);
            shakeY = (int)(Math.random() * shakeIntensity - shakeIntensity / 2);
            shakeDuration--;
        } else {
            shakeX = 0; shakeY = 0;
        }

        damageTexts.removeIf(dt -> dt.alpha <= 0);
        for (DamageText dt : damageTexts) dt.update();

        particles.removeIf(p -> p.life <= 0);
        for (Particle p : particles) p.update();

        if (System.currentTimeMillis() > trollTimer) {
            invertedControls = false;
        }

        if (player.hp <= 0) {
            if (!isGameOver) {
                isGameOver = true; isMousePressed = false;
                audioManager.playGameOverMusic(); DiscordRPCManager.updatePresence(currentWave, 0, 0);
            }
            return;
        }

        player.update(up, down, left, right, WIDTH, HEIGHT, invertedControls);
        waveManager.update(enemies, WIDTH, HEIGHT);

        // --- UPDATE PASTÍ A JEJICH KOLIZÍ S HRÁČEM ---
        for (Hazard h : hazards) {
            h.update();
            if (h.getHitbox().intersects(player.getHitbox())) {
                if (h.type == Hazard.Type.LAVA && !player.isDashing) {
                    // Láva dává častý menší damage a zpomaluje
                    if (System.currentTimeMillis() % 20 == 0) {
                        player.takeDamage(1);
                    }
                } else if (h.type == Hazard.Type.SPIKE && h.isActive && !player.isDashing) {
                    player.takeDamage(10);
                    triggerShake(3, 3);
                    damageTexts.add(new DamageText(player.x, player.y, "-10", Color.RED, true));
                    // Odrážení od bodáků, aby tam hráč nezůstal stát
                    player.y += 20;
                }
            }
        }

        hazards.removeIf(h -> h.type == Hazard.Type.BARREL && h.hp <= 0);

        if (isMousePressed && player != null && gameState == State.PLAYING) {
            if (player.canUseAbility()) {
                double startX = player.x + player.size / 2.0;
                double startY = player.y + player.size / 2.0;

                if (player.activeWeapon == 1 || player.activeWeapon == 2) {
                    projectiles.add(new Projectile(startX, startY, mouseTargetX, mouseTargetY, player.activeWeapon, false));
                    player.useAbility(250);
                }
                else if (player.activeWeapon == 3) {
                    player.activateShield(3000, enemies);
                    isMousePressed = false;
                }
                else if (player.activeWeapon == 4) {
                    int comboId = inventoryManager.equippedComboId;

                    if (comboId == 4) {
                        player.activateFireAura(5000);
                        isMousePressed = false;
                    }
                    else if (comboId == 5) {
                        for (int i = 0; i < 8; i++) {
                            double angle = i * (Math.PI / 4);
                            double targetX = startX + Math.cos(angle) * 100;
                            double targetY = startY + Math.sin(angle) * 100;
                            projectiles.add(new Projectile(startX, startY, targetX, targetY, 5, false));
                        }
                        triggerShake(10, 8);
                        player.useAbility(1500);
                        isMousePressed = false;
                    }
                    else if (comboId == 6) {
                        for(int i = -1; i <= 1; i++) {
                            double modX = mouseTargetX + (i * 40);
                            double modY = mouseTargetY + (Math.random() * 40 - 20);
                            projectiles.add(new Projectile(startX, startY, modX, modY, 2, false));
                        }
                        player.useAbility(800);
                        isMousePressed = false;
                    }
                }
            }
        }

        if (waveManager.isWaveFinished(enemies) && boss == null) {
            if (currentWave == 3) {
                gameState = State.CUTSCENE;
                isMousePressed = false;

                List<CutsceneManager.DialogLine> bossIntro = new ArrayList<>();
                Image bossPortrait = loadImage("/boss_portrait.png");
                Image playerPortrait = loadImage("/player_portrait.png");

                bossIntro.add(new CutsceneManager.DialogLine("Temný Stín", "Tak ty sis myslel, že to bude takhle jednoduché?", bossPortrait, "/voice/boss_1.wav"));
                bossIntro.add(new CutsceneManager.DialogLine("Hrdina", "Ukaž se, zbabělče!", playerPortrait, "/voice/player_1.wav"));
                bossIntro.add(new CutsceneManager.DialogLine("Temný Stín", "Jsem tvůj konec. Připrav se na ZKÁZU!", bossPortrait, "/voice/boss_2.wav"));

                cutsceneManager.startCutscene(bossIntro, () -> {
                    boss = new Boss(WIDTH);
                    enemies.clear();
                    gameState = State.PLAYING;
                });
            } else {
                proceedToNextWave();
            }
        }

        if (boss != null) {
            boss.update(player, enemyProjectiles);
            if (boss.hp <= 0) { boss = null; gameState = State.VICTORY; isMousePressed = false; }
        }

        walls.removeIf(Wall::isDead);
        souls.removeIf(Soul::isExpired);
        lootDrops.removeIf(LootDrop::isExpired);

        for (LootDrop drop : lootDrops) {
            if (drop.getHitbox().intersects(player.getHitbox())) {
                inventoryManager.addItem(drop.item);
                inventoryManager.applyBonusesToPlayer(player);
                lootDrops.remove(drop);
            }
        }

        for (Soul soul : souls) {
            if (soul.getHitbox().intersects(player.getHitbox())) {
                player.hp = Math.min(player.maxHp, player.hp + 10);
                souls.remove(soul);
            }
        }

        for (Projectile p : projectiles) {
            p.update();
            if (p.x < 0 || p.x > WIDTH || p.y < 0 || p.y > HEIGHT) {
                projectiles.remove(p);
                continue;
            }

            boolean hitWall = false;
            for (Wall wall : walls) {
                if (p.getHitbox().intersects(wall.getHitbox())) {
                    wall.hp -= 10;
                    projectiles.remove(p);
                    hitWall = true;
                    break;
                }
            }
            if (hitWall) continue;

            // Kolize střel s Barely (Výbuch)
            boolean hitHazard = false;
            for (Hazard h : hazards) {
                if (h.type == Hazard.Type.BARREL && p.getHitbox().intersects(h.getHitbox())) {
                    h.hp -= 15;
                    projectiles.remove(p);
                    hitHazard = true;

                    if (h.hp <= 0) {
                        triggerShake(10, 8); // Výbuch barely třese obrazovkou
                        for(int i = 0; i < 30; i++) particles.add(new Particle(h.x + 20, h.y + 20, Color.ORANGE));

                        // Zraní nepřátele okolo
                        for (Enemy e : enemies) {
                            if (Math.hypot(e.x - h.x, e.y - h.y) <= 120) {
                                e.hp -= 100;
                                damageTexts.add(new DamageText(e.x, e.y, "-100", Color.ORANGE, true));
                            }
                        }
                        // Zraní hráče, pokud stojí moc blízko
                        if (Math.hypot(player.x - h.x, player.y - h.y) <= 120 && !player.isDashing) {
                            player.takeDamage(30);
                            damageTexts.add(new DamageText(player.x, player.y, "-30", Color.RED, true));
                        }
                    }
                    break;
                }
            }
            if (hitHazard) continue;

            if (boss != null && p.getHitbox().intersects(boss.getHitbox())) {
                projectiles.remove(p);
                if (!boss.isBlocking) {
                    int baseDmg = (p.type == 5) ? 80 : (player.level >= 3 ? 50 : (player.level == 2 ? 40 : 25));
                    int dmg = baseDmg + player.bonusDamage;

                    boss.hp -= dmg;
                    player.hp = Math.min(player.maxHp, player.hp + boss.getLifestealAmount());
                    damageTexts.add(new DamageText(boss.x + boss.width/2.0, boss.y, "-" + dmg, Color.RED, true));
                    triggerShake(5, 4);
                } else {
                    damageTexts.add(new DamageText(boss.x + boss.width/2.0, boss.y, "BLOK", Color.GRAY, false));
                }
                continue;
            }

            for (Enemy enemy : enemies) {
                if (p.getHitbox().intersects(enemy.getHitbox())) {
                    if (p.type == 1 || p.type == 5) {
                        int baseDmg = (p.type == 5) ? 80 : (player.level >= 3 ? 50 : (player.level == 2 ? 40 : 25));
                        int dmg = baseDmg + player.bonusDamage;

                        if (player.level >= 3 && p.type == 1) {
                            java.util.ArrayList<Enemy> nearby = new java.util.ArrayList<>();
                            for (Enemy e : enemies) {
                                if (Math.hypot(e.x - enemy.x, e.y - enemy.y) <= 100) nearby.add(e);
                            }
                            if (nearby.size() >= 4) {
                                enemy.hp -= dmg;
                                for (Enemy e : nearby) {
                                    if (e != enemy) {
                                        int splashDmg = 15 + player.bonusDamage / 2;
                                        e.hp -= splashDmg;
                                        damageTexts.add(new DamageText(e.x, e.y, "-" + splashDmg, Color.ORANGE, false));
                                    }
                                }
                            } else {
                                dmg += 20;
                                enemy.hp -= dmg;
                            }
                        } else {
                            enemy.hp -= dmg;
                        }

                        if (p.type == 5) enemy.freeze(2000);

                        damageTexts.add(new DamageText(enemy.x, enemy.y, "-" + dmg, p.type == 5 ? Color.MAGENTA : Color.ORANGE, p.type == 5));

                        double dx = enemy.x - player.x, dy = enemy.y - player.y;
                        double dist = Math.hypot(dx, dy);
                        if (dist > 0) {
                            enemy.x += (dx / dist) * 15.0; enemy.y += (dy / dist) * 15.0;
                        }
                    }
                    else if (p.type == 2) {
                        enemy.freeze(2500);
                        if (player.level >= 3) enemy.startDotDamage(2000);
                        damageTexts.add(new DamageText(enemy.x, enemy.y, "MRAZ", Color.CYAN, false));
                    }
                    projectiles.remove(p);
                    break;
                }
            }
        }

        for (Projectile p : enemyProjectiles) {
            p.update();
            if (p.x < 0 || p.x > WIDTH || p.y < 0 || p.y > HEIGHT) {
                enemyProjectiles.remove(p);
                continue;
            }

            boolean hitWall = false;
            for (Wall wall : walls) {
                if (p.getHitbox().intersects(wall.getHitbox())) {
                    wall.hp -= 5;
                    enemyProjectiles.remove(p);
                    hitWall = true;
                    break;
                }
            }
            if (hitWall) continue;

            if (p.getHitbox().intersects(player.getHitbox()) && !player.isDashing) {
                player.takeDamage(10);
                enemyProjectiles.remove(p);
                damageTexts.add(new DamageText(player.x, player.y, "-10", Color.RED, true));
                triggerShake(3, 3);
            }
            else if (player.isShieldActive || player.isFireAuraActive) {
                double distToPlayer = Math.hypot(p.x - player.x, p.y - player.y);
                if (distToPlayer < player.AURA_RADIUS + 20) {
                    enemyProjectiles.remove(p);
                }
            }
        }

        if (player.isFireAuraActive) {
            if (System.currentTimeMillis() - lastAuraDamageTime > 500) {
                for (Enemy enemy : enemies) {
                    double dist = Math.hypot(enemy.x - player.x, enemy.y - player.y);
                    if (dist <= player.AURA_RADIUS + 15) {
                        int dmg = 15 + player.bonusDamage;
                        enemy.hp -= dmg;
                        damageTexts.add(new DamageText(enemy.x, enemy.y, "-" + dmg, Color.ORANGE, false));
                        if (dist > 0) {
                            enemy.x += ((enemy.x - player.x) / dist) * 5.0;
                            enemy.y += ((enemy.y - player.y) / dist) * 5.0;
                        }
                    }
                }
                if (boss != null && !boss.isBlocking) {
                    double distToBoss = Math.hypot((boss.x + boss.width/2.0) - player.x, (boss.y + boss.height/2.0) - player.y);
                    if (distToBoss <= player.AURA_RADIUS + boss.width/2.0) {
                        int dmg = 15 + player.bonusDamage;
                        boss.hp -= dmg;
                        damageTexts.add(new DamageText(boss.x, boss.y, "-" + dmg, Color.RED, false));
                    }
                }
                lastAuraDamageTime = System.currentTimeMillis();
            }
        }

        for (Enemy enemy : enemies) {
            if (enemy.hp <= 0) {
                for(int i = 0; i < 10; i++) particles.add(new Particle(enemy.x + 15, enemy.y + 15, new Color(150, 0, 0)));
                souls.add(new Soul(enemy.x, enemy.y));

                if (Math.random() < 0.15) {
                    lootDrops.add(new LootDrop(enemy.x, enemy.y, generateRandomItem()));
                }

                // Exploze Kamikaze nepřítele po smrti
                if (enemy.enemyType == Enemy.EnemyType.KAMIKAZE) {
                    triggerShake(8, 6);
                    for(int i = 0; i < 20; i++) particles.add(new Particle(enemy.x, enemy.y, Color.YELLOW));
                    if (Math.hypot(player.x - enemy.x, player.y - enemy.y) <= 80 && !player.isDashing) {
                        player.takeDamage(25);
                        damageTexts.add(new DamageText(player.x, player.y, "-25", Color.RED, true));
                    }
                }

                enemies.remove(enemy);
                continue;
            }

            enemy.update(player.x, player.y, enemies, enemyProjectiles);

            for (Wall wall : walls) {
                if (enemy.getHitbox().intersects(wall.getHitbox())) {
                    double dist = Math.hypot(enemy.x - (wall.x + wall.width / 2.0), enemy.y - (wall.y + wall.height / 2.0));
                    if (dist > 0) {
                        enemy.x += ((enemy.x - (wall.x + wall.width / 2.0)) / dist) * 5.0;
                        enemy.y += ((enemy.y - (wall.y + wall.height / 2.0)) / dist) * 5.0;
                    }
                    wall.hp -= 1;
                }
            }

            // Kontakt s Kamikaze hráčem = Rychlý výbuch
            if (enemy.getHitbox().intersects(player.getHitbox()) && !player.isDashing && !enemy.isFrozen()) {
                if (enemy.enemyType == Enemy.EnemyType.KAMIKAZE) {
                    enemy.hp = 0; // Odpálí se sám
                } else {
                    player.takeDamage(15);
                    triggerShake(4, 5);
                    damageTexts.add(new DamageText(player.x, player.y, "-15", Color.RED, true));
                }
            }

            if (player.isShieldActive) {
                double dx = enemy.x - player.x;
                double dy = enemy.y - player.y;
                double distance = Math.hypot(dx, dy);
                if (distance < player.size + 20) {
                    enemy.x += (dx / distance) * 5.0;
                    enemy.y += (dy / distance) * 5.0;
                }
            }
        }

        if (System.currentTimeMillis() - lastRpcUpdate >= 2000) {
            DiscordRPCManager.updatePresence(currentWave, player.hp, enemies.size());
            lastRpcUpdate = System.currentTimeMillis();
        }
    }

    private void updateWeaponInfo() {
        if(player == null) return;
        swapTextTimer = System.currentTimeMillis() + 2000;
        int lvl = player.level;
        int bDmg = player.bonusDamage;

        if (player.activeWeapon == 1) {
            activeWeaponInfo = "Ohnivá střela: " + ((lvl >= 3 ? 50 : (lvl == 2 ? 40 : 25)) + bDmg) + " DMG" + (lvl >= 3 ? " (Plošný výbuch)" : "");
        } else if (player.activeWeapon == 2) {
            activeWeaponInfo = "Mráz: Zmrazí cíl" + (lvl >= 3 ? " + Jed (" + bDmg + " DMG)" : "");
        } else if (player.activeWeapon == 3) {
            activeWeaponInfo = "Větrný Štít: Odstrkuje nepřátele (3s)";
        } else if (player.activeWeapon == 4) {
            if (inventoryManager.equippedComboId == 4) {
                activeWeaponInfo = "Aura: Pálí okolí (" + (15 + bDmg) + " DMG/s)";
            } else if (inventoryManager.equippedComboId == 5) {
                activeWeaponInfo = "SuperNova: 8-směrný výbuch (" + (80 + bDmg) + " DMG)";
            } else if (inventoryManager.equippedComboId == 6) {
                activeWeaponInfo = "Vánice: Trojitý ledový výstřel";
            } else {
                activeWeaponInfo = "Žádné Kombo Vybaveno!";
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int realW = getWidth();
        int realH = getHeight();
        float scale = Math.min((float) realW / 800f, (float) realH / 600f);

        if (gameState == State.LOADING) {
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, (int)(40 * scale)));
            FontMetrics fm = g2.getFontMetrics();
            String text = "NAČÍTÁNÍ...";
            g2.drawString(text, (realW - fm.stringWidth(text)) / 2, realH / 2 - 40);

            int barWidth = (int)(300 * scale);
            int barHeight = (int)(30 * scale);
            int barX = (realW - barWidth) / 2;
            int barY = realH / 2 + 20;

            g2.setColor(Color.DARK_GRAY);
            g2.fillRect(barX, barY, barWidth, barHeight);
            g2.setColor(Color.GREEN);
            g2.fillRect(barX, barY, (int)((loadingProgress / 100.0) * barWidth), barHeight);
            g2.setColor(Color.WHITE);
            g2.drawRect(barX, barY, barWidth, barHeight);
            return;
        }

        if (gameState == State.MENU) {
            Font titleFont = new Font("Arial", Font.BOLD, (int)(50 * scale));
            Font menuFont = new Font("Arial", Font.PLAIN, (int)(25 * scale));

            g2.setColor(Color.WHITE);
            g2.setFont(titleFont);
            FontMetrics fmTitle = g2.getFontMetrics();
            g2.drawString("MJUN GAME", (realW - fmTitle.stringWidth("MJUN GAME")) / 2, realH / 3);

            g2.setFont(menuFont);
            FontMetrics fmMenu = g2.getFontMetrics();
            int menuY = realH / 2;
            int spacing = (int)(40 * scale);

            String t1 = "[1] Nová Hra (Level 1)";
            String t2 = "[2] Endless Mód (Level 4)";
            String t3 = "[3] Nastavení";
            String t4 = "[4] Pokračovat (Vlna " + ConfigManager.highestWave + ")";

            g2.drawString(t1, (realW - fmMenu.stringWidth(t1)) / 2, menuY);
            g2.drawString(t2, (realW - fmMenu.stringWidth(t2)) / 2, menuY + spacing);
            g2.drawString(t3, (realW - fmMenu.stringWidth(t3)) / 2, menuY + spacing * 2);
            g2.setColor(Color.YELLOW);
            g2.drawString(t4, (realW - fmMenu.stringWidth(t4)) / 2, menuY + spacing * 3 + 10);
            return;
        }

        if (gameState == State.SETTINGS) {
            if (settingsScreen != null) settingsScreen.draw(g2, realW, realH);
            return;
        }

        if (isGameOver) {
            g2.setColor(Color.RED);
            g2.setFont(new Font("Arial", Font.BOLD, (int)(60 * scale)));
            FontMetrics fmOver = g2.getFontMetrics();
            String overText = "Prohráli jste";
            g2.drawString(overText, (realW - fmOver.stringWidth(overText)) / 2, realH / 2);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.PLAIN, (int)(20 * scale)));
            FontMetrics fmSub = g2.getFontMetrics();
            String subText = "Stiskněte MEZERNÍK pro návrat do Menu";
            g2.drawString(subText, (realW - fmSub.stringWidth(subText)) / 2, realH / 2 + (int)(50 * scale));
            return;
        }

        g2.translate(shakeX, shakeY);

        for (Hazard h : hazards) h.draw(g2); // VYKRESLENÍ PASTÍ NA POZADÍ
        for (Wall w : walls) w.draw(g2);
        for (Soul s : souls) s.draw(g2);
        for (LootDrop drop : lootDrops) drop.draw(g2);
        for (Projectile p : projectiles) p.draw(g2);
        for (Projectile p : enemyProjectiles) p.draw(g2);
        for (Enemy enemy : enemies) enemy.draw(g2);
        for (Particle p : particles) p.draw(g2);

        if (boss != null) boss.draw(g2, realH);
        if (player != null) player.draw(g2);

        for (DamageText dt : damageTexts) dt.draw(g2);

        g2.translate(-shakeX, -shakeY);

        if (System.currentTimeMillis() < swapTextTimer && player != null) {
            g2.setFont(new Font("Arial", Font.BOLD, 16));
            g2.setColor(new Color(255, 255, 0, 200));
            g2.drawString(activeWeaponInfo, (int)player.x - 20, (int)player.y - 20);
        }

        if (gameState == State.CUTSCENE) {
            cutsceneManager.draw(g2, realW, realH);
            return;
        }

        if (gameState == State.INVENTORY) {
            inventoryManager.draw(g2, realW, realH, mouseTargetX, mouseTargetY);
            return;
        }

        if (gameState == State.MINIGAME) {
            g2.setColor(new Color(0, 0, 0, 200)); g2.fillRect(0, 0, realW, realH);
            g2.setColor(Color.WHITE); g2.setFont(new Font("Arial", Font.BOLD, 30));
            g2.drawString("KOVÁŘSKÁ KOVADLINA", realW / 2 - 160, 150);

            g2.setFont(new Font("Arial", Font.PLAIN, 20));
            g2.setColor(Color.YELLOW);
            g2.drawString(mgMessage, realW / 2 - g2.getFontMetrics().stringWidth(mgMessage)/2, 200);

            for (int i = 0; i < 3; i++) {
                g2.setColor(i < mgSuccessHits ? Color.GREEN : Color.DARK_GRAY);
                g2.fillOval(realW / 2 - 60 + (i * 40), 240, 20, 20);
                g2.setColor(Color.WHITE); g2.drawOval(realW / 2 - 60 + (i * 40), 240, 20, 20);
            }

            int barX = realW / 2 - 300, barY = 320;
            g2.setColor(Color.GRAY); g2.fillRect(barX, barY, 600, 40);
            g2.setColor(Color.GREEN); g2.fillRect(barX + mgTargetX, barY, mgTargetW, 40);
            g2.setColor(Color.WHITE); g2.fillRect(barX + mgCursorX - 5, barY - 10, 10, 60);
            return;
        }

        if (gameState == State.VICTORY) {
            g2.setColor(new Color(0, 0, 0, 220)); g2.fillRect(0, 0, realW, realH);
            g2.setColor(Color.YELLOW); g2.setFont(new Font("Arial", Font.BOLD, (int)(40 * scale)));
            String t = "BOSS PORAŽEN!";
            g2.drawString(t, (realW - g2.getFontMetrics().stringWidth(t)) / 2, realH/4);

            g2.setColor(Color.WHITE); g2.setFont(new Font("Arial", Font.PLAIN, (int)(20 * scale)));
            g2.drawString("[1] Jít do Endless módu (Ponechat si Vybavení)", realW/2 - (int)(220*scale), realH/2);
            g2.drawString("[2] Jít do Endless módu Čistý (Hardcore)", realW/2 - (int)(220*scale), realH/2 + (int)(50*scale));
            g2.drawString("[3] Zpět do Menu", realW/2 - (int)(220*scale), realH/2 + (int)(100*scale));
            return;
        }

        if (System.currentTimeMillis() < msgTimer) {
            int boxHeight = (int)(60 * scale);
            g2.setColor(new Color(0, 0, 0, 180)); g2.fillRect(0, realH / 2 - boxHeight/2, realW, boxHeight);
            g2.setColor(Color.CYAN); g2.setFont(new Font("Arial", Font.BOLD, (int)(30 * scale)));
            FontMetrics fmMsg = g2.getFontMetrics();
            int textX = (realW - fmMsg.stringWidth(discordMsg)) / 2;
            g2.drawString(discordMsg, textX, realH / 2 + fmMsg.getAscent()/4);
        }

        int uiFontSize = (int)(18 * scale);
        g2.setFont(new Font("Arial", Font.BOLD, uiFontSize));

        String waveText = currentWave == 3 ? "⚠️ BOSS FIGHT! ⚠️" : (currentWave > 3 ? "ENDLESS VLNA " + currentWave : "VLNA " + currentWave);
        FontMetrics fmUI = g2.getFontMetrics();
        g2.setColor(currentWave >= 3 ? Color.MAGENTA : Color.ORANGE);
        g2.drawString(waveText, (realW - fmUI.stringWidth(waveText)) / 2, (int)(30 * scale));

        if (player != null) {
            g2.setFont(new Font("Arial", Font.BOLD, uiFontSize));

            String weaponName = "Neznámá";
            if(player.activeWeapon == 1) weaponName = "Ohnivá střela";
            if(player.activeWeapon == 2) weaponName = "Zmrazení";
            if(player.activeWeapon == 3) weaponName = "Větrný Štít";
            if(player.activeWeapon == 4) {
                if (inventoryManager.equippedComboId == 4) weaponName = "Ohnivá Aura";
                else if (inventoryManager.equippedComboId == 5) weaponName = "SuperNova";
                else if (inventoryManager.equippedComboId == 6) weaponName = "Vánice";
                else weaponName = "Žádné Kombo";
            }

            long cd = player.getRemainingCooldown();
            long swapCd = player.getSwapCooldown();
            int yOffset = (int)(30 * scale);

            if (swapCd > 0) {
                g2.setColor(Color.YELLOW); g2.drawString("Výměna: " + (Math.round(swapCd / 100.0) / 10.0) + "s", 15, yOffset);
            } else if (cd > 0) {
                g2.setColor(Color.RED); g2.drawString("Zbraň čeká: " + (Math.round(cd / 100.0) / 10.0) + "s", 15, yOffset);
            } else {
                g2.setColor(Color.GREEN); g2.drawString("PŘIPRAVENO", 15, yOffset);
            }

            g2.setColor(new Color(0, 0, 0, 180)); g2.fillRoundRect(10, realH - 80, realW - 20, 75, 15, 15);
            g2.setColor(Color.GRAY); g2.drawRoundRect(10, realH - 80, realW - 20, 75, 15, 15);

            g2.setFont(new Font("Arial", Font.ITALIC, 14));
            g2.setColor(Color.LIGHT_GRAY);
            g2.drawString("Aktivní schopnost: " + activeWeaponInfo, 25, realH - 55);

            g2.setFont(new Font("Arial", Font.BOLD, 16));
            FontMetrics fmSkills = g2.getFontMetrics();
            int bottomY = realH - 25; int sectionWidth = realW / 4;

            g2.setColor(player.activeWeapon == 1 ? Color.YELLOW : Color.GRAY);
            g2.drawString("[1] Oheň", (sectionWidth * 0) + (sectionWidth - fmSkills.stringWidth("[1] Oheň"))/2, bottomY);

            if (player.level >= 2) {
                g2.setColor(player.activeWeapon == 2 ? Color.CYAN : Color.GRAY);
                g2.drawString("[2] Mráz", (sectionWidth * 1) + (sectionWidth - fmSkills.stringWidth("[2] Mráz"))/2, bottomY);
            }
            if (player.level >= 3) {
                g2.setColor(player.activeWeapon == 3 ? Color.GREEN : Color.GRAY);
                g2.drawString("[3] Štít", (sectionWidth * 2) + (sectionWidth - fmSkills.stringWidth("[3] Štít"))/2, bottomY);
            }
            if (inventoryManager.equippedComboId != 0) {
                String comboStr = "[4] Kombo";
                g2.setColor(player.activeWeapon == 4 ? new Color(255, 100, 0) : Color.GRAY);
                g2.drawString(comboStr, (sectionWidth * 3) + (sectionWidth - fmSkills.stringWidth(comboStr))/2, bottomY);
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            if (gameState == State.INVENTORY) {
                boolean swapped = inventoryManager.tryEquipItemAt(e.getX(), e.getY(), WIDTH, HEIGHT);
                if(swapped) updateWeaponInfo();
            } else {
                isMousePressed = true; mouseTargetX = e.getX(); mouseTargetY = e.getY();
            }
        }
    }
    @Override public void mouseReleased(MouseEvent e) { if (SwingUtilities.isLeftMouseButton(e)) isMousePressed = false; }
    @Override public void mouseDragged(MouseEvent e) { mouseTargetX = e.getX(); mouseTargetY = e.getY(); }
    @Override public void mouseMoved(MouseEvent e) { mouseTargetX = e.getX(); mouseTargetY = e.getY(); }
    @Override public void mouseClicked(MouseEvent e) {} @Override public void mouseEntered(MouseEvent e) {} @Override public void mouseExited(MouseEvent e) { isMousePressed = false; }

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        if (gameState == State.LOADING) return;

        if (gameState == State.MENU) {
            if (key == KeyEvent.VK_1) resetGame(1);
            if (key == KeyEvent.VK_2) resetGame(4);
            if (key == KeyEvent.VK_3) gameState = State.SETTINGS;
            if (key == KeyEvent.VK_4) resetGame(ConfigManager.highestWave);
            return;
        }

        if (gameState == State.SETTINGS) {
            if (key == KeyEvent.VK_ESCAPE) gameState = State.MENU;
            else if (settingsScreen != null) { settingsScreen.setCurrentWave(currentWave); settingsScreen.update(key); }
            return;
        }

        if (gameState == State.CUTSCENE) {
            if (key == KeyEvent.VK_SPACE) cutsceneManager.next();
            return;
        }

        if (gameState == State.INVENTORY) {
            if (key == KeyEvent.VK_E) { inventoryManager.cycleEquippedCombo(); updateWeaponInfo(); }
            if (key == KeyEvent.VK_C && inventoryManager.canCraftAnything()) startMinigame();
            if (key == KeyEvent.VK_TAB || key == KeyEvent.VK_ESCAPE) gameState = State.PLAYING;
            return;
        }

        if (gameState == State.MINIGAME) {
            if (key == KeyEvent.VK_SPACE) {
                if (mgCursorX >= mgTargetX && mgCursorX <= mgTargetX + mgTargetW) {
                    mgSuccessHits++; mgMessage = "Pěkná rána!"; mgSpeed += 2.0; mgTargetW -= 20; mgTargetX = (int) (Math.random() * (600 - mgTargetW));
                    if (mgSuccessHits >= 3) { triggerShake(15, 10); inventoryManager.processCraftingResult(true, player); gameState = State.INVENTORY; }
                } else {
                    mgMessage = "Minul jsi! Suroviny jsou zničeny."; mgSuccessHits = 0; inventoryManager.processCraftingResult(false, player); gameState = State.INVENTORY;
                }
            }
            return;
        }

        if (gameState == State.VICTORY) {
            if (key == KeyEvent.VK_1) proceedToNextWave();
            if (key == KeyEvent.VK_2) { int nextWave = currentWave + 1; resetGame(nextWave); }
            if (key == KeyEvent.VK_3) gameState = State.MENU;
            return;
        }

        if (gameState == State.PLAYING) {
            if (key == KeyEvent.VK_TAB) { gameState = State.INVENTORY; isMousePressed = false; return; }
            if (isGameOver && key == KeyEvent.VK_SPACE) { gameState = State.MENU; audioManager.playMenuMusic(); DiscordRPCManager.stop(); DiscordRPCManager.start(); return; }

            if (key == KeyEvent.VK_T) showTutorial = false;

            if (key == KeyEvent.VK_W || key == KeyEvent.VK_UP) up = true;
            if (key == KeyEvent.VK_S || key == KeyEvent.VK_DOWN) down = true;
            if (key == KeyEvent.VK_A || key == KeyEvent.VK_LEFT) left = true;
            if (key == KeyEvent.VK_D || key == KeyEvent.VK_RIGHT) right = true;

            // --- SPOUŠTĚNÍ DASH (Úskoku) POMOCÍ SHIFT ---
            if (key == KeyEvent.VK_SHIFT && player != null) {
                player.performDash(up, down, left, right);
            }

            boolean swapped = false;
            if (key == KeyEvent.VK_1 && player != null) { player.swapWeapon(1, player.level); swapped = true; }
            if (key == KeyEvent.VK_2 && player != null && player.level >= 2) { player.swapWeapon(2, player.level); swapped = true; }
            if (key == KeyEvent.VK_3 && player != null && player.level >= 3) { player.swapWeapon(3, player.level); swapped = true; }
            if (key == KeyEvent.VK_4 && player != null && inventoryManager.equippedComboId != 0) { player.swapWeapon(4, player.level); swapped = true; }
            if (swapped) updateWeaponInfo();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_W || key == KeyEvent.VK_UP) up = false;
        if (key == KeyEvent.VK_S || key == KeyEvent.VK_DOWN) down = false;
        if (key == KeyEvent.VK_A || key == KeyEvent.VK_LEFT) left = false;
        if (key == KeyEvent.VK_D || key == KeyEvent.VK_RIGHT) right = false;
    }

    @Override public void keyTyped(KeyEvent e) {}

    public int getCurrentWave() { return currentWave; }
}