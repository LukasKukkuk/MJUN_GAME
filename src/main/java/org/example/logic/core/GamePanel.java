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

    private enum State { LOADING, MENU, PLAYING, SETTINGS, INVENTORY, VICTORY, CUTSCENE, MINIGAME, BUILD_SELECT, ACHIEVEMENTS }
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
    private GuiRenderer guiRenderer = new GuiRenderer();

    private Player player;
    private boolean up, down, left, right;
    private boolean isGameOver = false;

    private Boss boss;

    private boolean isMousePressed = false;
    private int mouseTargetX = 0;
    private int mouseTargetY = 0;

    // Předměty čekající na výsledek minihry při kombinování v inventáři
    private Item pendingCombineA, pendingCombineB;

    private CopyOnWriteArrayList<Enemy> enemies = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Projectile> projectiles = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Projectile> enemyProjectiles = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Wall> walls = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Soul> souls = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<LootDrop> lootDrops = new CopyOnWriteArrayList<>();

    // --- KOLEKCE PRO HAZARDS (PASTI) ---
    private CopyOnWriteArrayList<Hazard> hazards = new CopyOnWriteArrayList<>();

    // --- VIZUÁLNÍ EFEKTY (Juice) ---
    private CopyOnWriteArrayList<DamageText> damageTexts = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Particle> particles = new CopyOnWriteArrayList<>();
    private int shakeX = 0, shakeY = 0, shakeDuration = 0, shakeIntensity = 0;
    private String activeWeaponInfo = ""; // Zpráva o swappnutí

    // Vnitřní třída pro plovoucí čísla zranění
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
            if (alpha < 0) alpha = 0;
        }

        public void draw(Graphics2D g) {
            g.setFont(font);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            g.drawString(text, (int)x, (int)y);
        }
    }

    // Vnitřní třída pro částice (krev/výbuchy)
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
            if (life < 0) life = 0;
        }

        public void draw(Graphics2D g) {
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), life));
            g.fillRect((int)x, (int)y, 4, 4);
        }
    }

    // --- PROMĚNNÉ PRO CRAFTING MINIGAME ---
    private int mgCursorX = 0;
    private int mgCursorDir = 1;
    private double mgSpeed = 6.0;
    private int mgTargetX = 200;
    private int mgTargetW = 100;
    private int mgSuccessHits = 0;
    private String mgMessage = "Kovadlina je připravena...";

    private String discordMsg = "";
    private long msgTimer = 0;
    private long lastDiscordActionTime = 0;
    private static final long DISCORD_ACTION_COOLDOWN = 5000; // Anti-griefing: max 1 akce diváků / 5s

    // --- ACHIEVEMENT TOAST ---
    private String achievementToastTitle = "";
    private String achievementToastDesc = "";
    private long achievementToastTimer = 0;
    private boolean invertedControls = false;
    private long trollTimer = 0;

    private long lastAuraDamageTime = 0;
    private long swapTextTimer = 0;
    private int currentWave = 1;
    private boolean showTutorial = true;
    private boolean introPlayed = false;
    private static final int FINAL_BOSS_WAVE = 6;
    private boolean finalBossDefeated = false;

    // --- VOLBA BUILDU NA STARTU ---
    private int pendingStartWave = 1;
    private int selectedBuild = 0; // 0 = Tank, 1 = Rychlý, 2 = Glass Cannon

    // --- LOKÁLNÍ CO-OP (2. HRÁČ) ---
    // Druhý hráč má vlastní HP (může zemřít nezávisle na prvním), ale sdílí
    // InventoryManager/postup s hráčem 1. Šipky = pohyb, ENTER = auto-útok na
    // nejbližšího nepřítele, CTRL = dash. Zbraň i poškození sdílí formuli s hráčem 1,
    // protože Projectile nenese informaci o vlastníkovi - jde o vědomé zjednodušení.
    private boolean coopEnabled = false;
    private Player player2;
    private boolean up2, down2, left2, right2;
    private boolean player2FireHeld = false;
    private long player2LastShotTime = 0;
    private static final long PLAYER2_FIRE_COOLDOWN = 350;
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

    // Krátké zamrznutí hry na klíčové momenty (kritický zásah, smrt bosse, výbuchy) - "hit-stop"
    private int hitStopFrames = 0;

    private void triggerHitStop(int frames) {
        if (frames > hitStopFrames) hitStopFrames = frames;
    }

    private void tryUnlockAchievement(String id) {
        Achievement unlocked = AchievementManager.tryUnlock(id);
        if (unlocked != null) {
            achievementToastTitle = "🏆 " + unlocked.title;
            achievementToastDesc = unlocked.description;
            achievementToastTimer = System.currentTimeMillis() + 5000;
        }
    }

    // --- CO-OP: interakce druhého hráče se světem ---
    // Vědomé zjednodušení: kolize s bossem (laser/AOE) a řešeny nejsou pro
    // hráče 2, aby se nemusela duplikovat celá logika Boss.update(). Pasti,
    // běžní nepřátelé, jejich střely a sběr lootu/duší fungují pro oba.
    private void updatePlayer2Interactions() {
        for (Hazard h : hazards) {
            if (h.getHitbox().intersects(player2.getHitbox())) {
                if (h.type == Hazard.Type.LAVA && !player2.isDashing) {
                    if (System.currentTimeMillis() % 20 == 0) player2.takeDamage(1);
                } else if (h.type == Hazard.Type.SPIKE && h.isActive && !player2.isDashing) {
                    player2.takeDamage(10);
                    triggerShake(3, 3);
                    damageTexts.add(new DamageText(player2.x, player2.y, "-10", Color.RED, true));
                    player2.y += 20;
                }
            }
        }

        List<LootDrop> collected2 = new ArrayList<>();
        for (LootDrop drop : lootDrops) {
            if (drop.getHitbox().intersects(player2.getHitbox())) {
                inventoryManager.addItem(drop.item);
                inventoryManager.applyBonusesToPlayer(player);
                inventoryManager.applyBonusesToPlayer(player2);
                collected2.add(drop);
            }
        }
        if (!collected2.isEmpty()) lootDrops.removeAll(collected2);

        List<Soul> collectedSouls2 = new ArrayList<>();
        for (Soul soul : souls) {
            if (soul.getHitbox().intersects(player2.getHitbox())) {
                player2.hp = Math.min(player2.maxHp, player2.hp + 10);
                collectedSouls2.add(soul);
            }
        }
        if (!collectedSouls2.isEmpty()) souls.removeAll(collectedSouls2);

        List<Projectile> hitPlayer2 = new ArrayList<>();
        for (Projectile p : enemyProjectiles) {
            if (p.getHitbox().intersects(player2.getHitbox()) && !player2.isDashing) {
                player2.takeDamage(10);
                hitPlayer2.add(p);
                damageTexts.add(new DamageText(player2.x, player2.y, "-10", Color.RED, true));
                triggerShake(3, 3);
            }
        }
        if (!hitPlayer2.isEmpty()) enemyProjectiles.removeAll(hitPlayer2);

        for (Enemy enemy : enemies) {
            if (enemy.hp <= 0) continue; // smrt zpracuje hlavní smyčka nepřátel
            if (enemy.getHitbox().intersects(player2.getHitbox()) && !player2.isDashing && !enemy.isFrozen()) {
                if (enemy.enemyType == Enemy.EnemyType.KAMIKAZE) {
                    enemy.hp = 0;
                } else {
                    player2.takeDamage(15);
                    triggerShake(4, 5);
                    damageTexts.add(new DamageText(player2.x, player2.y, "-15", Color.RED, true));
                }
            }
        }
    }

    // Automatická střelba hráče 2 na nejbližšího nepřítele/bosse - nemá myš,
    // takže cílí sám. Sdílí formuli poškození se sdíleným inventářem/staty.
    private void updatePlayer2AutoFire() {
        if (!player2FireHeld) return;
        long now = System.currentTimeMillis();
        if (now - player2LastShotTime < PLAYER2_FIRE_COOLDOWN) return;

        double fromX = player2.x + player2.size / 2.0;
        double fromY = player2.y + player2.size / 2.0;

        double bestDist = Double.MAX_VALUE;
        double targetX = 0, targetY = 0;
        boolean found = false;

        for (Enemy e : enemies) {
            double d = Math.hypot(e.x - fromX, e.y - fromY);
            if (d < bestDist) { bestDist = d; targetX = e.x; targetY = e.y; found = true; }
        }
        if (boss != null) {
            double bx = boss.x + boss.width / 2.0, by = boss.y + boss.height / 2.0;
            double d = Math.hypot(bx - fromX, by - fromY);
            if (d < bestDist) { bestDist = d; targetX = bx; targetY = by; found = true; }
        }

        if (found) {
            projectiles.add(new Projectile(fromX, fromY, targetX, targetY, 1, false));
            player2LastShotTime = now;
        }
    }

    // --- GENEROVÁNÍ NÁHODNÝCH PASTÍ ---
    private void generateHazards() {
        hazards.clear();
        if (currentWave >= 2) {
            int numHazards = Math.min(currentWave + 1, 6);
            for (int i = 0; i < numHazards; i++) {
                double hx = Math.random() * (WIDTH - 100) + 50;
                double hy = Math.random() * (HEIGHT - 100) + 50;

                if (Math.hypot(hx - WIDTH / 2.0, hy - HEIGHT / 2.0) > 150) {
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
        player.setAnimations(playerWalkAnim);

        // Trvalá meta-progrese - permanentní bonusy odemčené za dosažené vlny napříč runy
        player.maxHp += ConfigManager.getMetaBonusMaxHp();
        player.bonusDamage += ConfigManager.getMetaBonusDamage();
        player.bonusSpeed += ConfigManager.getMetaBonusSpeed();

        // Zvolený build ze startovní obrazovky
        if (selectedBuild == 0) { // Tank
            player.maxHp += 50;
            player.bonusSpeed -= 0.5;
        } else if (selectedBuild == 1) { // Rychlý
            player.maxHp -= 20;
            player.bonusSpeed += 1.5;
        } else if (selectedBuild == 2) { // Glass Cannon
            player.maxHp -= 30;
            player.bonusDamage += 20;
        }
        if (player.maxHp < 20) player.maxHp = 20; // Pojistka proti záporným/nesmyslně nízkým HP
        player.hp = player.maxHp;

        if (coopEnabled) {
            player2 = new Player(WIDTH / 2.0 + 50, HEIGHT / 2.0);
            player2.setAnimations(playerWalkAnim);
            player2.weapon2Unlocked = player.weapon2Unlocked;
            player2.weapon3Unlocked = player.weapon3Unlocked;
            player2.weapon4Unlocked = player.weapon4Unlocked;
            player2.maxHp = player.maxHp;
            player2.hp = player2.maxHp;
            player2.bonusDamage = player.bonusDamage;
            player2.bonusSpeed = player.bonusSpeed;
            up2 = down2 = left2 = right2 = false;
            player2FireHeld = false;
        } else {
            player2 = null;
        }

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
        finalBossDefeated = false;

        updateWeaponInfo();

        ConfigManager.save(currentWave);
        if (currentWave >= 10) tryUnlockAchievement("wave_10");
        audioManager.playMusicForWave(Math.min(currentWave, 3));

        waveManager.startNextWave(currentWave);
        gameState = State.PLAYING;
    }

    // Spustí zvolený run (nová hra/endless/pokračování) - u úplně první nové hry
    // ještě před samotným startem přehraje intro cutscénu.
    private void startPendingRun() {
        if (pendingStartWave == 1 && !introPlayed) {
            introPlayed = true;
            gameState = State.CUTSCENE;
            cutsceneManager.startCutsceneFromFile("/cutscenes/texts/intro.txt", () -> resetGame(1));
        } else {
            resetGame(pendingStartWave);
        }
    }

    public void startGame() {
        if (gameThread == null) {
            isRunning = true;
            gameThread = new Thread(this);
            gameThread.start();
        }
    }

    private Item generateRandomItem() {
        // Krystaly (odemykají zbraně 2/3 a jsou craft materiál) mají teď výrazně
        // vyšší váhu, aby postup k nim nebyl přehnaně grindový.
        int rand = (int) (Math.random() * 100);
        if (rand < 15) return Item.createWeaponShard();
        if (rand < 27) return Item.createHealthHeart();
        if (rand < 39) return Item.createDamageSword();
        if (rand < 51) return Item.createSpeedBoots();
        if (rand < 59) return Item.createBerserkerPotion();
        if (rand < 75) return Item.createIceCrystal();
        if (rand < 89) return Item.createWindCrystal();
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
        long now = System.currentTimeMillis();
        if (now - lastDiscordActionTime < DISCORD_ACTION_COOLDOWN) return; // Rate-limit proti spamu/griefingu diváků
        lastDiscordActionTime = now;

        this.discordMsg = text;
        this.msgTimer = now + 4000;
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
        if (currentWave >= 10) tryUnlockAchievement("wave_10");
        audioManager.playMusicForWave(Math.min(currentWave, 3));

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
            if (gameState == State.CUTSCENE) {
                cutsceneManager.update();
                // Dynamická kontrola shaku z běžící cutscény
                if (cutsceneManager.consumeShakeRequest()) {
                    triggerShake(10, cutsceneManager.getShakeIntensity());
                }
            }
            return;
        }

        if (gameState == State.MINIGAME) {
            mgCursorX += mgSpeed * mgCursorDir;
            if (mgCursorX <= 0 || mgCursorX >= 600) mgCursorDir *= -1;
            return;
        }

        if (hitStopFrames > 0) {
            hitStopFrames--;
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
                ConfigManager.recordRunResult(currentWave);
            }
            return;
        }

        player.update(up, down, left, right, WIDTH, HEIGHT, invertedControls);
        if (coopEnabled && player2 != null && player2.hp > 0) {
            player2.update(up2, down2, left2, right2, WIDTH, HEIGHT, invertedControls);
            updatePlayer2Interactions();
            updatePlayer2AutoFire();
        }
        waveManager.update(enemies, WIDTH, HEIGHT);

        // --- UPDATE PASTÍ A JEJICH KOLIZÍ S HRÁČEM ---
        for (Hazard h : hazards) {
            h.update();
            if (h.getHitbox().intersects(player.getHitbox())) {
                if (h.type == Hazard.Type.LAVA && !player.isDashing) {
                    if (System.currentTimeMillis() % 20 == 0) {
                        player.takeDamage(1);
                    }
                } else if (h.type == Hazard.Type.SPIKE && h.isActive && !player.isDashing) {
                    player.takeDamage(10);
                    triggerShake(3, 3);
                    damageTexts.add(new DamageText(player.x, player.y, "-10", Color.RED, true));
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
                    else if (comboId == 7) {
                        // METEOR: okamžitý plošný zásah v místě kurzoru, ultimátní kombo za všechny 3 krystaly
                        double impactX = mouseTargetX, impactY = mouseTargetY;
                        int meteorRadius = 140;
                        int meteorDmg = 120 + player.bonusDamage;

                        triggerShake(20, 14);
                        triggerHitStop(6);
                        for (int i = 0; i < 40; i++) particles.add(new Particle(impactX, impactY, Color.YELLOW));

                        for (Enemy e : enemies) {
                            if (Math.hypot(e.x - impactX, e.y - impactY) <= meteorRadius) {
                                e.hp -= meteorDmg;
                                damageTexts.add(new DamageText(e.x, e.y, "-" + meteorDmg, Color.YELLOW, true));
                            }
                        }
                        if (boss != null) {
                            double bx = boss.x + boss.width / 2.0, by = boss.y + boss.height / 2.0;
                            if (Math.hypot(bx - impactX, by - impactY) <= meteorRadius) {
                                boss.hp -= meteorDmg;
                                damageTexts.add(new DamageText(bx, by, "-" + meteorDmg, Color.YELLOW, true));
                            }
                        }
                        player.useAbility(4000);
                        isMousePressed = false;
                    }
                }
            }
        }

        if (waveManager.isWaveFinished(enemies) && boss == null) {
            if (currentWave == 3) {
                gameState = State.CUTSCENE;
                isMousePressed = false;

                // NAČÍTÁNÍ PŘÍBĚHU PŘÍMO ZE SOUBORU Z RESOURCES
                cutsceneManager.startCutsceneFromFile("/cutscenes/texts/text_example.txt", () -> {
                    boss = new Boss(WIDTH);
                    enemies.clear();
                    gameState = State.PLAYING;
                });
            } else if (currentWave == FINAL_BOSS_WAVE && !finalBossDefeated) {
                gameState = State.CUTSCENE;
                isMousePressed = false;

                cutsceneManager.startCutsceneFromFile("/cutscenes/texts/3/level3_ending.txt", () -> {
                    boss = new Boss(WIDTH, Boss.Variant.FINAL);
                    enemies.clear();
                    gameState = State.PLAYING;
                });
            } else {
                proceedToNextWave();
            }
        }

        if (boss != null) {
            boss.update(player, enemyProjectiles);
            if (boss.hp <= 0) {
                boolean wasFinalBoss = boss.variant == Boss.Variant.FINAL;
                triggerShake(20, 12);
                triggerHitStop(12);
                boss = null;
                isMousePressed = false;

                if (coopEnabled) tryUnlockAchievement("coop_victory");

                if (wasFinalBoss) {
                    finalBossDefeated = true;
                    tryUnlockAchievement("true_ending");
                    ConfigManager.recordRunResult(currentWave);
                    gameState = State.CUTSCENE;
                    cutsceneManager.startCutsceneFromFile("/cutscenes/texts/epilogue.txt", () -> gameState = State.MENU);
                } else {
                    tryUnlockAchievement("first_blood");
                    gameState = State.VICTORY;
                }
            }
        }

        walls.removeIf(Wall::isDead);
        souls.removeIf(Soul::isExpired);
        lootDrops.removeIf(LootDrop::isExpired);

        List<LootDrop> collectedDrops = new ArrayList<>();
        for (LootDrop drop : lootDrops) {
            if (drop.getHitbox().intersects(player.getHitbox())) {
                inventoryManager.addItem(drop.item);
                inventoryManager.applyBonusesToPlayer(player);
                if (coopEnabled && player2 != null) inventoryManager.applyBonusesToPlayer(player2);
                collectedDrops.add(drop);
            }
        }
        if (!collectedDrops.isEmpty()) lootDrops.removeAll(collectedDrops);

        List<Soul> collectedSouls = new ArrayList<>();
        for (Soul soul : souls) {
            if (soul.getHitbox().intersects(player.getHitbox())) {
                player.hp = Math.min(player.maxHp, player.hp + 10);
                collectedSouls.add(soul);
            }
        }
        if (!collectedSouls.isEmpty()) souls.removeAll(collectedSouls);

        List<Projectile> deadProjectiles = new ArrayList<>();
        for (Projectile p : projectiles) {
            p.update();
            if (p.x < 0 || p.x > WIDTH || p.y < 0 || p.y > HEIGHT) {
                deadProjectiles.add(p);
                continue;
            }

            boolean hitWall = false;
            for (Wall wall : walls) {
                if (p.getHitbox().intersects(wall.getHitbox())) {
                    wall.hp -= 10; deadProjectiles.add(p); hitWall = true; break;
                }
            }
            if (hitWall) continue;

            // Kolize střel s Barely (Exploze a plošný damage)
            boolean hitHazard = false;
            for (Hazard h : hazards) {
                if (h.type == Hazard.Type.BARREL && p.getHitbox().intersects(h.getHitbox())) {
                    h.hp -= 15; deadProjectiles.add(p); hitHazard = true;

                    if (h.hp <= 0) {
                        triggerShake(10, 8);
                        triggerHitStop(4);
                        for (int i = 0; i < 30; i++) particles.add(new Particle(h.x + 20, h.y + 20, Color.ORANGE));

                        for (Enemy e : enemies) {
                            if (Math.hypot(e.x - h.x, e.y - h.y) <= 120) {
                                e.hp -= 100;
                                damageTexts.add(new DamageText(e.x, e.y, "-100", Color.ORANGE, true));
                            }
                        }
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
                deadProjectiles.add(p);
                if (!boss.isBlocking) {
                    int baseDmg = (p.type == 5) ? 80 : 25;
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
                        int baseDmg = (p.type == 5) ? 80 : 25;
                        int dmg = baseDmg + player.bonusDamage;

                        if (player.weapon3Unlocked && p.type == 1) {
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
                                dmg += 20; enemy.hp -= dmg;
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
                        if (player.weapon3Unlocked) enemy.startDotDamage(2000);
                        damageTexts.add(new DamageText(enemy.x, enemy.y, "MRAZ", Color.CYAN, false));
                    }
                    deadProjectiles.add(p);
                    break;
                }
            }
        }
        if (!deadProjectiles.isEmpty()) projectiles.removeAll(deadProjectiles);

        List<Projectile> deadEnemyProjectiles = new ArrayList<>();
        for (Projectile p : enemyProjectiles) {
            p.update();
            if (p.x < 0 || p.x > WIDTH || p.y < 0 || p.y > HEIGHT) {
                deadEnemyProjectiles.add(p);
                continue;
            }

            boolean hitWall = false;
            for (Wall wall : walls) {
                if (p.getHitbox().intersects(wall.getHitbox())) {
                    wall.hp -= 5; deadEnemyProjectiles.add(p); hitWall = true; break;
                }
            }
            if (hitWall) continue;

            if (p.getHitbox().intersects(player.getHitbox()) && !player.isDashing) {
                player.takeDamage(10);
                deadEnemyProjectiles.add(p);
                damageTexts.add(new DamageText(player.x, player.y, "-10", Color.RED, true));
                triggerShake(3, 3);
            }
            else if (player.isShieldActive || player.isFireAuraActive) {
                double distToPlayer = Math.hypot(p.x - player.x, p.y - player.y);
                if (distToPlayer < player.AURA_RADIUS + 20) {
                    deadEnemyProjectiles.add(p);
                }
            }
        }
        if (!deadEnemyProjectiles.isEmpty()) enemyProjectiles.removeAll(deadEnemyProjectiles);

        if (player.isFireAuraActive) {
            if (System.currentTimeMillis() - lastAuraDamageTime > 500) {
                for (Enemy enemy : enemies) {
                    double dist = Math.hypot(enemy.x - player.x, enemy.y - player.y);
                    if (dist <= player.AURA_RADIUS + 15) {
                        int dmg = 15 + player.bonusDamage;
                        enemy.hp -= dmg;
                        damageTexts.add(new DamageText(enemy.x, enemy.y, "-" + dmg, Color.ORANGE, false));
                        double dx = enemy.x - player.x; double dy = enemy.y - player.y;
                        if (dist > 0) {
                            enemy.x += (dx / dist) * 5.0; enemy.y += (dy / dist) * 5.0;
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

        List<Enemy> deadEnemies = new ArrayList<>();
        for (Enemy enemy : enemies) {
            if (enemy.hp <= 0) {
                for(int i = 0; i < 10; i++) particles.add(new Particle(enemy.x + 15, enemy.y + 15, new Color(150, 0, 0)));
                souls.add(new Soul(enemy.x, enemy.y));

                if (Math.random() * 100 < 25) {
                    lootDrops.add(new LootDrop(enemy.x, enemy.y, generateRandomItem()));
                }

                // Výbuch Kamikaze po jeho smrti
                if (enemy.enemyType == Enemy.EnemyType.KAMIKAZE) {
                    triggerShake(8, 6);
                    triggerHitStop(3);
                    for(int i = 0; i < 20; i++) particles.add(new Particle(enemy.x, enemy.y, Color.YELLOW));
                    if (Math.hypot(player.x - enemy.x, player.y - enemy.y) <= 80 && !player.isDashing) {
                        player.takeDamage(25);
                        damageTexts.add(new DamageText(player.x, player.y, "-25", Color.RED, true));
                    }
                }

                deadEnemies.add(enemy);
                continue;
            }

            enemy.update(player.x, player.y, enemies, enemyProjectiles);

            for (Wall wall : walls) {
                if (enemy.getHitbox().intersects(wall.getHitbox())) {
                    double wallCenterX = wall.x + (wall.width / 2.0);
                    double wallCenterY = wall.y + (wall.height / 2.0);
                    double dx = enemy.x - wallCenterX; double dy = enemy.y - wallCenterY;
                    double dist = Math.hypot(dx, dy);

                    if (dist > 0) {
                        enemy.x += (dx / dist) * 5.0; enemy.y += (dy / dist) * 5.0;
                    }
                    wall.hp -= 1;
                }
            }

            // Náraz Kamikaze nepřítele do hráče
            if (enemy.getHitbox().intersects(player.getHitbox()) && !player.isDashing && !enemy.isFrozen()) {
                if (enemy.enemyType == Enemy.EnemyType.KAMIKAZE) {
                    enemy.hp = 0;
                } else {
                    player.takeDamage(15);
                    triggerShake(4, 5);
                    damageTexts.add(new DamageText(player.x, player.y, "-15", Color.RED, true));
                }
            }

            if (player.isShieldActive) {
                double dx = enemy.x - player.x; double dy = enemy.y - player.y;
                double distance = Math.hypot(dx, dy);
                if (distance < player.size + 20) {
                    enemy.x += (dx / distance) * 5.0; enemy.y += (dy / distance) * 5.0;
                }
            }
        }
        if (!deadEnemies.isEmpty()) enemies.removeAll(deadEnemies);

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRpcUpdate >= 2000) {
            DiscordRPCManager.updatePresence(currentWave, player.hp, enemies.size());
            lastRpcUpdate = currentTime;
        }
    }

    private void updateWeaponInfo() {
        if(player == null) return;
        swapTextTimer = System.currentTimeMillis() + 2000;
        int bDmg = player.bonusDamage;

        if (player.activeWeapon == 1) {
            activeWeaponInfo = "Ohnivá střela: " + (25 + bDmg) + " DMG" + (player.weapon3Unlocked ? " (Plošný výbuch)" : "");
        } else if (player.activeWeapon == 2) {
            activeWeaponInfo = "Mráz: Zmrazí cíl" + (player.weapon3Unlocked ? " + Jed (" + bDmg + " DMG)" : "");
        } else if (player.activeWeapon == 3) {
            activeWeaponInfo = "Větrný Štít: Odstrkuje nepřátele (3s)";
        } else if (player.activeWeapon == 4) {
            if (inventoryManager.equippedComboId == 4) {
                activeWeaponInfo = "Aura: Pálí okolí (" + (15 + bDmg) + " DMG/s)";
            } else if (inventoryManager.equippedComboId == 5) {
                activeWeaponInfo = "SuperNova: 8-směrný výbuch (" + (80 + bDmg) + " DMG)";
            } else if (inventoryManager.equippedComboId == 6) {
                activeWeaponInfo = "Vánice: Trojitý ledový výstřel";
            } else if (inventoryManager.equippedComboId == 7) {
                activeWeaponInfo = "Meteor: Plošný zásah v místě kurzoru (" + (120 + bDmg) + " DMG, 4s CD)";
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

        int realW = getWidth(); int realH = getHeight();
        float scale = Math.min((float) realW / 800f, (float) realH / 600f);

        if (gameState == State.LOADING) {
            g2.setColor(Color.WHITE); g2.setFont(new Font("Arial", Font.BOLD, (int)(40 * scale)));
            FontMetrics fm = g2.getFontMetrics(); String text = "NAČÍTÁNÍ...";
            g2.drawString(text, (realW - fm.stringWidth(text)) / 2, realH / 2 - 40);

            int barWidth = (int)(300 * scale); int barHeight = (int)(30 * scale);
            int barX = (realW - barWidth) / 2; int barY = realH / 2 + 20;

            g2.setColor(Color.DARK_GRAY); g2.fillRect(barX, barY, barWidth, barHeight);
            g2.setColor(Color.GREEN); g2.fillRect(barX, barY, (int)((loadingProgress / 100.0) * barWidth), barHeight);
            g2.setColor(Color.WHITE); g2.drawRect(barX, barY, barWidth, barHeight);
            return;
        }

        if (gameState == State.MENU) {
            Font titleFont = new Font("Arial", Font.BOLD, (int)(50 * scale));
            Font menuFont = new Font("Arial", Font.PLAIN, (int)(25 * scale));

            g2.setColor(Color.WHITE); g2.setFont(titleFont);
            FontMetrics fmTitle = g2.getFontMetrics();
            g2.drawString("MJUN GAME", (realW - fmTitle.stringWidth("MJUN GAME")) / 2, realH / 3);

            // TOP 5 - lokální leaderboard v pravém horním rohu
            if (!ConfigManager.leaderboard.isEmpty()) {
                Font lbTitleFont = new Font("Arial", Font.BOLD, (int)(15 * scale));
                Font lbFont = new Font("Arial", Font.PLAIN, (int)(13 * scale));
                int lbX = realW - (int)(160 * scale);
                int lbY = (int)(20 * scale);

                g2.setColor(Color.YELLOW); g2.setFont(lbTitleFont);
                g2.drawString("TOP RUNY", lbX, lbY);

                g2.setFont(lbFont); g2.setColor(Color.LIGHT_GRAY);
                int rank = 1;
                for (String entry : ConfigManager.leaderboard) {
                    String[] parts = entry.split("\\|");
                    String line = rank + ". Vlna " + parts[0] + (parts.length > 1 ? "  (" + parts[1] + ")" : "");
                    g2.drawString(line, lbX, lbY + rank * (int)(18 * scale));
                    rank++;
                }
            }

            g2.setFont(menuFont); FontMetrics fmMenu = g2.getFontMetrics();
            int menuY = realH / 2; int spacing = (int)(40 * scale);

            String t1 = "[1] Nová Hra (Level 1)";
            String t2 = "[2] Endless Mód (Level 4)";
            String t3 = "[3] Nastavení";
            String t4 = "[4] Pokračovat (Vlna " + ConfigManager.highestWave + ")";

            g2.drawString(t1, (realW - fmMenu.stringWidth(t1)) / 2, menuY);
            g2.drawString(t2, (realW - fmMenu.stringWidth(t2)) / 2, menuY + spacing);
            g2.drawString(t3, (realW - fmMenu.stringWidth(t3)) / 2, menuY + spacing * 2);
            g2.setColor(Color.YELLOW); g2.drawString(t4, (realW - fmMenu.stringWidth(t4)) / 2, menuY + spacing * 3 + 10);

            Font coopFont = new Font("Arial", Font.BOLD, (int)(17 * scale));
            g2.setFont(coopFont);
            g2.setColor(coopEnabled ? Color.GREEN : Color.GRAY);
            String coopText = "[C] Co-op (2 hráči): " + (coopEnabled ? "ZAPNUTO" : "vypnuto");
            g2.drawString(coopText, (realW - g2.getFontMetrics().stringWidth(coopText)) / 2, menuY + spacing * 4 - 2);

            Font metaFont = new Font("Arial", Font.ITALIC, (int)(15 * scale));
            g2.setFont(metaFont);
            g2.setColor(new Color(150, 220, 255));
            String meta = ConfigManager.describeMetaProgress();
            g2.drawString(meta, (realW - g2.getFontMetrics().stringWidth(meta)) / 2, menuY + spacing * 4 + 30);

            g2.setColor(new Color(255, 215, 0));
            String achText = "[A] Achievementy: " + ConfigManager.unlockedAchievements.size() + "/" + AchievementManager.ALL.size();
            g2.drawString(achText, (realW - g2.getFontMetrics().stringWidth(achText)) / 2, menuY + spacing * 4 + 55);
            return;
        }

        if (gameState == State.ACHIEVEMENTS) {
            Font achTitleFont = new Font("Arial", Font.BOLD, (int)(34 * scale));
            g2.setColor(Color.WHITE); g2.setFont(achTitleFont);
            String achScreenTitle = "ACHIEVEMENTY";
            g2.drawString(achScreenTitle, (realW - g2.getFontMetrics().stringWidth(achScreenTitle)) / 2, (int)(55 * scale));

            Font achHintFont = new Font("Arial", Font.ITALIC, (int)(14 * scale));
            g2.setColor(Color.LIGHT_GRAY); g2.setFont(achHintFont);
            String achHint = "[TAB/ESC] Zpět do menu";
            g2.drawString(achHint, (realW - g2.getFontMetrics().stringWidth(achHint)) / 2, (int)(78 * scale));

            int listY = (int)(120 * scale);
            int rowHeight = (int)(55 * scale);
            Font nameFont = new Font("Arial", Font.BOLD, (int)(18 * scale));
            Font descFont = new Font("Arial", Font.PLAIN, (int)(14 * scale));

            for (Achievement a : AchievementManager.ALL) {
                boolean unlocked = ConfigManager.isAchievementUnlocked(a.id);
                int boxX = (int)(60 * scale), boxW = realW - (int)(120 * scale);

                g2.setColor(unlocked ? new Color(40, 40, 0, 200) : new Color(30, 30, 30, 200));
                g2.fillRoundRect(boxX, listY, boxW, rowHeight - 10, 10, 10);
                g2.setColor(unlocked ? new Color(255, 215, 0) : Color.DARK_GRAY);
                g2.drawRoundRect(boxX, listY, boxW, rowHeight - 10, 10, 10);

                g2.setFont(nameFont);
                g2.setColor(unlocked ? new Color(255, 215, 0) : Color.GRAY);
                String title = unlocked ? ("🏆 " + a.title) : (a.secret ? "??? (tajný achievement)" : a.title);
                g2.drawString(title, boxX + 15, listY + (int)(22 * scale));

                g2.setFont(descFont);
                g2.setColor(unlocked ? Color.WHITE : Color.DARK_GRAY);
                String desc = unlocked ? a.description : (a.secret ? "Objevíš, až ho odemkneš." : a.description);
                g2.drawString(desc, boxX + 15, listY + (int)(40 * scale));

                listY += rowHeight;
            }
            return;
        }

        if (gameState == State.BUILD_SELECT) {
            Font bsTitleFont = new Font("Arial", Font.BOLD, (int)(36 * scale));
            Font bsFont = new Font("Arial", Font.PLAIN, (int)(20 * scale));
            Font bsDescFont = new Font("Arial", Font.ITALIC, (int)(14 * scale));

            g2.setColor(Color.WHITE); g2.setFont(bsTitleFont);
            String bsTitle = "ZVOL SVŮJ BUILD";
            g2.drawString(bsTitle, (realW - g2.getFontMetrics().stringWidth(bsTitle)) / 2, realH / 4);

            String[] names = {"[1] TANK", "[2] RYCHLÝ", "[3] GLASS CANNON"};
            String[] descs = {
                    "+50 HP, ale pomalejší",
                    "+1.5 rychlost, ale -20 HP",
                    "+20 poškození, ale -30 HP"
            };
            Color[] colors = {Color.GREEN, Color.CYAN, Color.RED};

            int bsY = realH / 2 - 40;
            int bsSpacing = (int)(70 * scale);
            for (int i = 0; i < 3; i++) {
                g2.setFont(bsFont);
                g2.setColor(colors[i]);
                g2.drawString(names[i], (realW - g2.getFontMetrics().stringWidth(names[i])) / 2, bsY + i * bsSpacing);

                g2.setFont(bsDescFont);
                g2.setColor(Color.LIGHT_GRAY);
                g2.drawString(descs[i], (realW - g2.getFontMetrics().stringWidth(descs[i])) / 2, bsY + i * bsSpacing + 22);
            }
            return;
        }

        if (gameState == State.SETTINGS) {
            if (settingsScreen != null) settingsScreen.draw(g2, realW, realH);
            return;
        }

        if (isGameOver) {
            g2.setColor(Color.RED); g2.setFont(new Font("Arial", Font.BOLD, (int)(60 * scale)));
            FontMetrics fmOver = g2.getFontMetrics(); String overText = "Prohráli jste";
            g2.drawString(overText, (realW - fmOver.stringWidth(overText)) / 2, realH / 2);

            g2.setColor(Color.WHITE); g2.setFont(new Font("Arial", Font.PLAIN, (int)(20 * scale)));
            FontMetrics fmSub = g2.getFontMetrics(); String subText = "Stiskněte MEZERNÍK pro návrat do Menu";
            g2.drawString(subText, (realW - fmSub.stringWidth(subText)) / 2, realH / 2 + (int)(50 * scale));
            return;
        }

        g2.translate(shakeX, shakeY);

        for (Hazard h : hazards) h.draw(g2);
        for (Wall w : walls) w.draw(g2);
        for (Soul s : souls) s.draw(g2);
        for (LootDrop drop : lootDrops) drop.draw(g2);
        for (Projectile p : projectiles) p.draw(g2);
        for (Projectile p : enemyProjectiles) p.draw(g2);
        for (Enemy enemy : enemies) enemy.draw(g2);
        for (Particle p : particles) p.draw(g2);

        if (boss != null) boss.draw(g2, realH);
        if (player != null) player.draw(g2);
        if (coopEnabled && player2 != null && player2.hp > 0) player2.draw(g2);

        for (DamageText dt : damageTexts) dt.draw(g2);

        g2.translate(-shakeX, -shakeY);

        if (gameState == State.PLAYING && player != null) {
            guiRenderer.drawHUD(g2, player, realW, realH);
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

            g2.setFont(new Font("Arial", Font.PLAIN, 20)); g2.setColor(Color.YELLOW);
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
            String t = "BOSS PORAŽEN!"; g2.drawString(t, (realW - g2.getFontMetrics().stringWidth(t)) / 2, realH/4);

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
            FontMetrics fmMsg = g2.getFontMetrics(); int textX = (realW - fmMsg.stringWidth(discordMsg)) / 2;
            g2.drawString(discordMsg, textX, realH / 2 + fmMsg.getAscent()/4);
        }

        if (System.currentTimeMillis() < achievementToastTimer) {
            int toastW = (int)(360 * scale), toastH = (int)(55 * scale);
            int toastX = (realW - toastW) / 2, toastY = (int)(70 * scale);

            g2.setColor(new Color(0, 0, 0, 200)); g2.fillRoundRect(toastX, toastY, toastW, toastH, 12, 12);
            g2.setColor(new Color(255, 215, 0)); g2.setStroke(new BasicStroke(2));
            g2.drawRoundRect(toastX, toastY, toastW, toastH, 12, 12);
            g2.setStroke(new BasicStroke(1));

            g2.setFont(new Font("Arial", Font.BOLD, (int)(16 * scale)));
            g2.setColor(new Color(255, 215, 0));
            g2.drawString(achievementToastTitle, toastX + 12, toastY + (int)(22 * scale));

            g2.setFont(new Font("Arial", Font.PLAIN, (int)(13 * scale)));
            g2.setColor(Color.WHITE);
            g2.drawString(achievementToastDesc, toastX + 12, toastY + (int)(40 * scale));
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
                else if (inventoryManager.equippedComboId == 7) weaponName = "Meteor";
                else weaponName = "Žádné Kombo";
            }

            long swapCd = player.getSwapCooldown();

            g2.setColor(new Color(0, 0, 0, 180)); g2.fillRoundRect(10, realH - 80, realW - 20, 75, 15, 15);
            g2.setColor(Color.GRAY); g2.drawRoundRect(10, realH - 80, realW - 20, 75, 15, 15);

            g2.setFont(new Font("Arial", Font.ITALIC, 14)); g2.setColor(Color.LIGHT_GRAY);
            g2.drawString("Aktivní schopnost: " + activeWeaponInfo, 25, realH - 55);

            // Připravenost výměny zbraně - bez textu, jen barevný indikátor v rohu panelu
            int readyDotSize = (int)(14 * scale);
            int readyDotX = realW - 30, readyDotY = realH - 68;
            g2.setColor(swapCd > 0 ? new Color(200, 60, 0) : new Color(60, 220, 60));
            g2.fillOval(readyDotX, readyDotY, readyDotSize, readyDotSize);
            g2.setColor(Color.BLACK);
            g2.drawOval(readyDotX, readyDotY, readyDotSize, readyDotSize);

            g2.setFont(new Font("Arial", Font.BOLD, 16));
            FontMetrics fmSkills = g2.getFontMetrics();
            int bottomY = realH - 25; int sectionWidth = realW / 4;

            g2.setColor(player.activeWeapon == 1 ? Color.YELLOW : Color.GRAY);
            g2.drawString("[1] Oheň", (sectionWidth * 0) + (sectionWidth - fmSkills.stringWidth("[1] Oheň"))/2, bottomY);

            if (player.weapon2Unlocked) {
                g2.setColor(player.activeWeapon == 2 ? Color.CYAN : Color.GRAY);
                g2.drawString("[2] Mráz", (sectionWidth * 1) + (sectionWidth - fmSkills.stringWidth("[2] Mráz"))/2, bottomY);
            }

            if (player.weapon3Unlocked) {
                g2.setColor(player.activeWeapon == 3 ? Color.GREEN : Color.GRAY);
                g2.drawString("[3] Štít", (sectionWidth * 2) + (sectionWidth - fmSkills.stringWidth("[3] Štít"))/2, bottomY);
            }

            if (inventoryManager.equippedComboId != 0) {
                String comboStr = "[4] Kombo";
                g2.setColor(player.activeWeapon == 4 ? new Color(255, 100, 0) : Color.GRAY);
                g2.drawString(comboStr, (sectionWidth * 3) + (sectionWidth - fmSkills.stringWidth(comboStr))/2, bottomY);
            }
        }

        if (System.currentTimeMillis() < swapTextTimer && player != null) {
            g2.setFont(new Font("Arial", Font.BOLD, 16)); g2.setColor(new Color(255, 255, 0, 200));
            g2.drawString(activeWeaponInfo, (int)player.x - 20, (int)player.y - 20);
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e)) {
            if (gameState == State.INVENTORY) {
                handleInventoryClick(e.getX(), e.getY());
            } else {
                isMousePressed = true; mouseTargetX = e.getX(); mouseTargetY = e.getY();
            }
        }
    }

    // Klik na první předmět ho vybere, klik na druhý (kompatibilní) spustí kovářskou minihru.
    private void handleInventoryClick(int mouseX, int mouseY) {
        int slot = inventoryManager.getSlotAt(mouseX, mouseY, WIDTH, HEIGHT);

        if (slot == -1 || slot >= inventoryManager.items.size()) {
            inventoryManager.selectedSlot = -1;
            return;
        }

        if (inventoryManager.selectedSlot == -1) {
            inventoryManager.selectedSlot = slot;
            return;
        }

        if (inventoryManager.selectedSlot == slot) {
            inventoryManager.selectedSlot = -1; // klik na stejný předmět zruší výběr
            return;
        }

        Item a = inventoryManager.items.get(inventoryManager.selectedSlot);
        Item b = inventoryManager.items.get(slot);
        inventoryManager.selectedSlot = -1;

        if (inventoryManager.canCombine(a, b)) {
            pendingCombineA = a;
            pendingCombineB = b;
            startMinigame();
        } else {
            discordMsg = "Tyto předměty nelze kombinovat";
            msgTimer = System.currentTimeMillis() + 2000;
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
            if (key == KeyEvent.VK_1) { pendingStartWave = 1; gameState = State.BUILD_SELECT; }
            if (key == KeyEvent.VK_2) { pendingStartWave = 4; gameState = State.BUILD_SELECT; }
            if (key == KeyEvent.VK_3) gameState = State.SETTINGS;
            if (key == KeyEvent.VK_4) { pendingStartWave = ConfigManager.highestWave; gameState = State.BUILD_SELECT; }
            if (key == KeyEvent.VK_C) coopEnabled = !coopEnabled;
            if (key == KeyEvent.VK_A) gameState = State.ACHIEVEMENTS;
            return;
        }

        if (gameState == State.ACHIEVEMENTS) {
            if (key == KeyEvent.VK_TAB || key == KeyEvent.VK_ESCAPE) gameState = State.MENU;
            return;
        }

        if (gameState == State.BUILD_SELECT) {
            if (key == KeyEvent.VK_1) { selectedBuild = 0; startPendingRun(); }
            if (key == KeyEvent.VK_2) { selectedBuild = 1; startPendingRun(); }
            if (key == KeyEvent.VK_3) { selectedBuild = 2; startPendingRun(); }
            if (key == KeyEvent.VK_ESCAPE) gameState = State.MENU;
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
            if (key == KeyEvent.VK_E) {
                inventoryManager.cycleEquippedCombo();
                updateWeaponInfo();
            }

            if (key == KeyEvent.VK_TAB || key == KeyEvent.VK_ESCAPE) {
                inventoryManager.selectedSlot = -1;
                gameState = State.PLAYING;
            }
            return;
        }

        if (gameState == State.MINIGAME) {
            if (key == KeyEvent.VK_SPACE) {
                if (mgCursorX >= mgTargetX && mgCursorX <= mgTargetX + mgTargetW) {
                    mgSuccessHits++; mgMessage = "Pěkná rána!"; mgSpeed += 2.0; mgTargetW -= 20; mgTargetX = (int) (Math.random() * (600 - mgTargetW));
                    if (mgSuccessHits >= 3) {
                        triggerShake(15, 10);
                        inventoryManager.processCombineResult(true, player, pendingCombineA, pendingCombineB);
                        tryUnlockAchievement("crafter");
                        pendingCombineA = null; pendingCombineB = null;
                        gameState = State.INVENTORY;
                    }
                } else {
                    mgMessage = "Minul jsi! Zkus to znovu."; mgSuccessHits = 0;
                    inventoryManager.processCombineResult(false, player, pendingCombineA, pendingCombineB);
                    pendingCombineA = null; pendingCombineB = null;
                    gameState = State.INVENTORY;
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

            if (key == KeyEvent.VK_W) up = true;
            if (key == KeyEvent.VK_S) down = true;
            if (key == KeyEvent.VK_A) left = true;
            if (key == KeyEvent.VK_D) right = true;

            if (coopEnabled && player2 != null) {
                // Hráč 2: šipky pro pohyb, ENTER pro útok, CTRL pro dash
                if (key == KeyEvent.VK_UP) up2 = true;
                if (key == KeyEvent.VK_DOWN) down2 = true;
                if (key == KeyEvent.VK_LEFT) left2 = true;
                if (key == KeyEvent.VK_RIGHT) right2 = true;
                if (key == KeyEvent.VK_ENTER) player2FireHeld = true;
                if (key == KeyEvent.VK_CONTROL) player2.performDash(up2, down2, left2, right2);
            } else {
                // Bez co-opu fungují šipky jako alternativa k WASD (jako dřív)
                if (key == KeyEvent.VK_UP) up = true;
                if (key == KeyEvent.VK_DOWN) down = true;
                if (key == KeyEvent.VK_LEFT) left = true;
                if (key == KeyEvent.VK_RIGHT) right = true;
            }

            // ÚSKOK (DASH) NA KLÁVESU SHIFT
            if (key == KeyEvent.VK_SHIFT && player != null) {
                player.performDash(up, down, left, right);
            }

            boolean swapped = false;
            if (key == KeyEvent.VK_1 && player != null) swapped = player.swapWeapon(1);
            if (key == KeyEvent.VK_2 && player != null) swapped = player.swapWeapon(2);
            if (key == KeyEvent.VK_3 && player != null) swapped = player.swapWeapon(3);
            if (key == KeyEvent.VK_4 && player != null) swapped = player.swapWeapon(4);
            if (swapped) updateWeaponInfo();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int key = e.getKeyCode();
        if (key == KeyEvent.VK_W) up = false;
        if (key == KeyEvent.VK_S) down = false;
        if (key == KeyEvent.VK_A) left = false;
        if (key == KeyEvent.VK_D) right = false;

        if (coopEnabled && player2 != null) {
            if (key == KeyEvent.VK_UP) up2 = false;
            if (key == KeyEvent.VK_DOWN) down2 = false;
            if (key == KeyEvent.VK_LEFT) left2 = false;
            if (key == KeyEvent.VK_RIGHT) right2 = false;
            if (key == KeyEvent.VK_ENTER) player2FireHeld = false;
        } else {
            if (key == KeyEvent.VK_UP) up = false;
            if (key == KeyEvent.VK_DOWN) down = false;
            if (key == KeyEvent.VK_LEFT) left = false;
            if (key == KeyEvent.VK_RIGHT) right = false;
        }
    }

    @Override public void keyTyped(KeyEvent e) {}
    public int getCurrentWave() { return currentWave; }
}