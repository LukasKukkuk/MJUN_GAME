package org.example.logic;

import org.example.Launcher;
import org.example.logic.managers.*;
import org.example.logic.enums.Type;
import io.github.cdimascio.dotenv.Dotenv;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.net.URL;
import java.util.concurrent.CopyOnWriteArrayList;

public class GamePanel extends JPanel implements Runnable, KeyListener, MouseListener {
    public int WIDTH = 800;
    public int HEIGHT = 600;

    private GameWindow window;
    private Thread gameThread;
    private boolean isRunning = false;
    private final int FPS = 60;

    private enum State { LOADING, MENU, PLAYING, SETTINGS }
    private State gameState = State.LOADING;

    private int loadingProgress = 0;
    private boolean loadingStarted = false;
    private long lastRpcUpdate = 0;

    private AudioManager audioManager;
    private WaveManager waveManager = new WaveManager();
    private DiscordManager discordManager;
    private SettingsScreen settingsScreen;

    private Player player;
    private boolean up, down, left, right;
    private boolean isGameOver = false;

    private CopyOnWriteArrayList<Enemy> enemies = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Projectile> projectiles = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Projectile> enemyProjectiles = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Wall> walls = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Soul> souls = new CopyOnWriteArrayList<>();

    private String discordMsg = "";
    private long msgTimer = 0;
    private boolean invertedControls = false;
    private long trollTimer = 0;

    // Časovač poškození pro Ohnivou auru
    private long lastAuraDamageTime = 0;

    private int currentWave = 1;
    private boolean showTutorial = true;
    private Image[] playerWalkAnim;

    public GamePanel() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.DARK_GRAY);
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);

        new Thread(() -> {
            try {
                Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
                String token = dotenv.get("DISCORD_TOKEN");
                if (token != null && !token.isEmpty()) {
                    discordManager = new DiscordManager(this, "ws://localhost:8765");
                    discordManager.connect();
                }
            } catch (Exception e) {
                System.out.println("⚠️ Nepodařilo se načíst konfiguraci pro Discord bota.");
            }
            DiscordRPCManager.start();
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

    private void resetGame(int startWave) {
        currentWave = startWave;
        player = new Player(WIDTH / 2.0, HEIGHT / 2.0);
        player.level = Math.min(currentWave, 3);
        player.setAnimations(playerWalkAnim);

        enemies.clear();
        projectiles.clear();
        enemyProjectiles.clear();
        walls.clear();
        souls.clear();
        isGameOver = false;
        showTutorial = true;

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

    // ===== AKCE PRO DISCORD BOTA =====

    public void spawnSoulFromDiscord() {
        if (gameState != State.PLAYING || isGameOver || player == null) return;
        double rx = player.x + (Math.random() * 200 - 100);
        double ry = player.y + (Math.random() * 200 - 100);
        souls.add(new Soul(rx, ry));
    }

    public void spawnEnemyFromDiscord() {
        if (gameState != State.PLAYING || isGameOver || player == null) return;
        double rx = player.x + (Math.random() * 400 - 200);
        double ry = player.y + (Math.random() * 400 - 200);
        enemies.add(new Enemy(rx, ry, Type.THIEVES, currentWave));
    }

    public void spawnHorde() {
        new Thread(() -> {
            try {
                for (int i = 0; i < 10; i++) {
                    spawnEnemyFromDiscord();
                    Thread.sleep(200);
                }
            } catch (InterruptedException e) {}
        }).start();
    }

    public void rageModeFromDiscord() {
        if (gameState != State.PLAYING || isGameOver) return;
        for (Enemy enemy : enemies) {
            enemy.hp += 50;
        }
        for (int i=0; i<3; i++) spawnEnemyFromDiscord();
    }

    public void freezeEnemiesFromDiscord() {
        if (gameState != State.PLAYING || isGameOver) return;
        for (Enemy enemy : enemies) {
            enemy.freeze(3000);
        }
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

    // ===== HERNÍ SMYČKA =====

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
                            audioManager.preloadAudio();
                            audioManager.setVolume(ConfigManager.volume);
                            loadingProgress = 80;
                            if (window != null) settingsScreen = new SettingsScreen(audioManager, window);
                            loadingProgress = 100;
                            try { Thread.sleep(300); } catch (Exception e) {}
                            audioManager.playMenuMusic();
                            gameState = State.MENU;
                        }).start();
                    }
                }
                else if (gameState == State.PLAYING && !isGameOver) {
                    update();
                }
                repaint();
                delta--;
            }
        }
    }

    private void update() {
        if (System.currentTimeMillis() > trollTimer) {
            invertedControls = false;
        }

        if (player.hp <= 0) {
            if (!isGameOver) {
                isGameOver = true;
                audioManager.playGameOverMusic();
                DiscordRPCManager.updatePresence(currentWave, 0, 0);
            }
            return;
        }

        player.update(up, down, left, right, WIDTH, HEIGHT, invertedControls);
        waveManager.update(enemies, WIDTH, HEIGHT);

        if (waveManager.isWaveFinished(enemies)) {
            currentWave++;
            player.level = Math.min(currentWave, 3);
            ConfigManager.save(currentWave);
            audioManager.playMusicForWave(Math.min(currentWave, 3));
            if (player.activeWeapon == 2 && currentWave < 2) player.activeWeapon = 1;
            if (player.activeWeapon == 3 && currentWave < 3) player.activeWeapon = 1;
            if (player.activeWeapon == 4 && currentWave < 4) player.activeWeapon = 1;
            waveManager.startNextWave(currentWave);
        }

        walls.removeIf(Wall::isDead);
        souls.removeIf(Soul::isExpired);

        for (Soul soul : souls) {
            if (soul.getHitbox().intersects(player.getHitbox())) {
                player.hp = Math.min(100, player.hp + 10);
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

            for (Enemy enemy : enemies) {
                if (p.getHitbox().intersects(enemy.getHitbox())) {
                    if (p.type == 1) {
                        if (player.level >= 3) {
                            java.util.ArrayList<Enemy> nearby = new java.util.ArrayList<>();
                            for (Enemy e : enemies) {
                                if (Math.hypot(e.x - enemy.x, e.y - enemy.y) <= 100) nearby.add(e);
                            }
                            if (nearby.size() >= 4) {
                                enemy.hp -= 30;
                                for (Enemy e : nearby) if (e != enemy) e.hp -= 15;
                            } else {
                                enemy.hp -= 50;
                            }
                        } else {
                            enemy.hp -= (player.level == 2) ? 40 : 25;
                        }
                        double dx = enemy.x - player.x, dy = enemy.y - player.y;
                        double dist = Math.hypot(dx, dy);
                        if(dist > 0) {
                            enemy.x += (dx / dist) * 15.0; enemy.y += (dy / dist) * 15.0;
                        }
                    }
                    else if (p.type == 2) {
                        enemy.freeze(2500);
                        if (player.level >= 3) enemy.startDotDamage(2000);
                    }
                    projectiles.remove(p);
                    break;
                }
            }
        }

        // --- AKTUALIZACE NEPŘÁTELSKÝCH STŘEL (Blokace Aurou / Štítem) ---
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

            if (p.getHitbox().intersects(player.getHitbox())) {
                player.takeDamage(10);
                enemyProjectiles.remove(p);
            }
            // ZDE JE KOUZLO: Štít i Aura blokují střely!
            else if (player.isShieldActive || player.isFireAuraActive) {
                double distToPlayer = Math.hypot(p.x - player.x, p.y - player.y);
                if (distToPlayer < player.AURA_RADIUS + 20) {
                    enemyProjectiles.remove(p);
                }
            }
        }

        // --- TICK OHNIVÉ AURY (Udělování damage) ---
        if (player.isFireAuraActive) {
            if (System.currentTimeMillis() - lastAuraDamageTime > 500) { // Každou půl sekundu
                for (Enemy enemy : enemies) {
                    double dist = Math.hypot(enemy.x - player.x, enemy.y - player.y);
                    if (dist <= player.AURA_RADIUS + 15) {
                        enemy.hp -= 15; // Damage per tick
                        // Drobný knockback
                        double dx = enemy.x - player.x;
                        double dy = enemy.y - player.y;
                        if(dist > 0) {
                            enemy.x += (dx / dist) * 5.0;
                            enemy.y += (dy / dist) * 5.0;
                        }
                    }
                }
                lastAuraDamageTime = System.currentTimeMillis();
            }
        }

        for (Enemy enemy : enemies) {
            if (enemy.hp <= 0) {
                souls.add(new Soul(enemy.x, enemy.y));
                enemies.remove(enemy);
                continue;
            }

            enemy.update(player.x, player.y, enemies, enemyProjectiles);

            for (Wall wall : walls) {
                if (enemy.getHitbox().intersects(wall.getHitbox())) {
                    double wallCenterX = wall.x + (wall.width / 2.0);
                    double wallCenterY = wall.y + (wall.height / 2.0);
                    double dx = enemy.x - wallCenterX;
                    double dy = enemy.y - wallCenterY;
                    double dist = Math.hypot(dx, dy);

                    if (dist > 0) {
                        enemy.x += (dx / dist) * 5.0;
                        enemy.y += (dy / dist) * 5.0;
                    }
                    wall.hp -= 1;
                }
            }

            if (enemy.getHitbox().intersects(player.getHitbox()) && !enemy.isFrozen()) {
                player.takeDamage(15);
            }

            if (player.isShieldActive) {
                double dx = enemy.x - player.x, dy = enemy.y - player.y;
                double distance = Math.hypot(dx, dy);
                if (distance < player.size + 20) {
                    enemy.x += (dx / distance) * 5.0;
                    enemy.y += (dy / distance) * 5.0;
                }
            }
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRpcUpdate >= 2000) {
            DiscordRPCManager.updatePresence(currentWave, player.hp, enemies.size());
            lastRpcUpdate = currentTime;
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

        for (Wall w : walls) w.draw(g2);
        for (Soul s : souls) s.draw(g2);
        for (Projectile p : projectiles) p.draw(g2);
        for (Projectile p : enemyProjectiles) p.draw(g2);
        for (Enemy enemy : enemies) enemy.draw(g2);
        if (player != null) player.draw(g2);

        if (System.currentTimeMillis() < msgTimer) {
            int boxHeight = (int)(60 * scale);
            g2.setColor(new Color(0, 0, 0, 180));
            g2.fillRect(0, realH / 2 - boxHeight/2, realW, boxHeight);

            g2.setColor(Color.CYAN);
            g2.setFont(new Font("Arial", Font.BOLD, (int)(30 * scale)));
            FontMetrics fmMsg = g2.getFontMetrics();
            int textX = (realW - fmMsg.stringWidth(discordMsg)) / 2;
            g2.drawString(discordMsg, textX, realH / 2 + fmMsg.getAscent()/4);
        }

        int uiFontSize = (int)(18 * scale);
        g2.setFont(new Font("Arial", Font.BOLD, uiFontSize));

        String waveText = currentWave > 3 ? "ENDLESS VLNA " + currentWave : "VLNA " + currentWave;
        FontMetrics fmUI = g2.getFontMetrics();
        g2.setColor(currentWave > 3 ? Color.MAGENTA : Color.ORANGE);
        g2.drawString(waveText, (realW - fmUI.stringWidth(waveText)) / 2, (int)(30 * scale));

        if (player != null) {
            g2.setFont(new Font("Arial", Font.BOLD, uiFontSize));
            String weaponName = switch (player.activeWeapon) {
                case 1 -> "Ohnivá střela";
                case 2 -> "Zmrazení";
                case 3 -> "Větrný Štít";
                case 4 -> "Ohnivá Aura";
                default -> "Neznámá";
            };

            long cd = player.getRemainingCooldown();
            long swapCd = player.getSwapCooldown();
            int yOffset = (int)(30 * scale);

            if (swapCd > 0) {
                g2.setColor(Color.YELLOW);
                g2.drawString("Výměna zbraně: " + (Math.round(swapCd / 100.0) / 10.0) + "s", 15, yOffset);
            } else if (cd > 0) {
                g2.setColor(Color.RED);
                g2.drawString("Zbraň: " + weaponName + " [ ČEKÁ: " + (Math.round(cd / 100.0) / 10.0) + "s ]", 15, yOffset);
            } else {
                g2.setColor(Color.GREEN);
                g2.drawString("Zbraň: " + weaponName + " [ PŘIPRAVENO ]", 15, yOffset);
            }

            int barHeight = (int)(50 * scale);
            int barY = realH - barHeight;

            g2.setColor(new Color(0, 0, 0, 150));
            g2.fillRect(0, barY, realW, barHeight);

            g2.setFont(new Font("Arial", Font.BOLD, (int)(16 * scale)));
            FontMetrics fmSkills = g2.getFontMetrics();
            int bottomY = barY + (barHeight / 2) + (fmSkills.getAscent() / 3);
            int sectionWidth = realW / 4;

            g2.setColor(player.activeWeapon == 1 ? Color.YELLOW : Color.GRAY);
            String skill1 = "[1] Oheň";
            g2.drawString(skill1, (sectionWidth * 0) + (sectionWidth - fmSkills.stringWidth(skill1))/2, bottomY);

            if (currentWave >= 2) {
                g2.setColor(player.activeWeapon == 2 ? Color.CYAN : Color.GRAY);
                String skill2 = "[2] Mráz";
                g2.drawString(skill2, (sectionWidth * 1) + (sectionWidth - fmSkills.stringWidth(skill2))/2, bottomY);
            }

            if (currentWave >= 3) {
                g2.setColor(player.activeWeapon == 3 ? Color.GREEN : Color.GRAY);
                String skill3 = "[3] Štít";
                g2.drawString(skill3, (sectionWidth * 2) + (sectionWidth - fmSkills.stringWidth(skill3))/2, bottomY);
            }

            if (currentWave >= 4) {
                g2.setColor(player.activeWeapon == 4 ? new Color(255, 100, 0) : Color.GRAY);
                String skill4 = "[4] Aura";
                g2.drawString(skill4, (sectionWidth * 3) + (sectionWidth - fmSkills.stringWidth(skill4))/2, bottomY);
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (gameState != State.PLAYING || isGameOver || player == null) return;

        if (SwingUtilities.isLeftMouseButton(e) && player.canUseAbility()) {
            if (player.activeWeapon == 1 || player.activeWeapon == 2) {
                double startX = player.x + player.size / 2.0;
                double startY = player.y + player.size / 2.0;
                projectiles.add(new Projectile(startX, startY, e.getX(), e.getY(), player.activeWeapon, false));
                player.useAbility();
            } else if (player.activeWeapon == 3) {
                player.activateShield(3000, enemies);
            } else if (player.activeWeapon == 4) {

                // AKTIVACE OHNIVÉ AURY
                player.activateFireAura(5000);

            }
        }
    }

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
            if (key == KeyEvent.VK_ESCAPE) {
                gameState = State.MENU;
            } else if (settingsScreen != null) {
                settingsScreen.setCurrentWave(currentWave);
                settingsScreen.update(key);
            }
            return;
        }

        if (gameState == State.PLAYING) {
            if (isGameOver && key == KeyEvent.VK_SPACE) {
                gameState = State.MENU;
                audioManager.playMenuMusic();
                DiscordRPCManager.stop();
                DiscordRPCManager.start();
                return;
            }

            if (key == KeyEvent.VK_T) showTutorial = false;

            if (key == KeyEvent.VK_W || key == KeyEvent.VK_UP) up = true;
            if (key == KeyEvent.VK_S || key == KeyEvent.VK_DOWN) down = true;
            if (key == KeyEvent.VK_A || key == KeyEvent.VK_LEFT) left = true;
            if (key == KeyEvent.VK_D || key == KeyEvent.VK_RIGHT) right = true;
            if (key == KeyEvent.VK_1 && player != null) player.swapWeapon(1, currentWave);
            if (key == KeyEvent.VK_2 && player != null) player.swapWeapon(2, currentWave);
            if (key == KeyEvent.VK_3 && player != null) player.swapWeapon(3, currentWave);
            if (key == KeyEvent.VK_4 && player != null) player.swapWeapon(4, currentWave);
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
    @Override public void mouseClicked(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}

    public int getCurrentWave() {
        return currentWave;
    }
}