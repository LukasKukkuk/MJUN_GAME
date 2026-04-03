package org.example;

import org.example.logic.*;
import org.example.logic.enums.Type;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameArena extends JPanel implements Runnable, KeyListener, MouseListener {
    private int WIDTH = 800;
    private int HEIGHT = 600;

    // --- ODKAZ NA OKNO (Pro Fullscreen) ---
    private JFrame window;
    private boolean isFullscreen = false;
    private int volume = 100;

    private Thread gameThread;
    private boolean isRunning = false;
    private final int FPS = 60;

    private enum State { MENU, PLAYING, SETTINGS }
    private State gameState = State.MENU;

    private AudioManager audioManager;
    private Player player;
    private boolean up, down, left, right;
    private boolean isGameOver = false;

    private CopyOnWriteArrayList<Enemy> enemies = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Projectile> projectiles = new CopyOnWriteArrayList<>();
    private CopyOnWriteArrayList<Projectile> enemyProjectiles = new CopyOnWriteArrayList<>();

    private int currentWave = 1;

    // KONSTRUKTOR NYNÍ PŘIJÍMÁ JFRAME
    public GameArena(JFrame window) {
        this.window = window;
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.DARK_GRAY);
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);

        audioManager = new AudioManager();
        audioManager.playMenuMusic();
    }

    // --- METODA PRO FULLSCREEN ---
    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        window.dispose(); // Zavřeme okno na milisekundu pro aplikaci změn
        window.setUndecorated(isFullscreen); // Schová horní lištu Windows

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();

        if (isFullscreen) {
            window.setExtendedState(JFrame.MAXIMIZED_BOTH);
            gd.setFullScreenWindow(window); // Natívní fullscreen

            // Přizpůsobíme vnitřní logiku hry novému rozlišení monitoru
            WIDTH = window.getWidth();
            HEIGHT = window.getHeight();
        } else {
            gd.setFullScreenWindow(null); // Vypnutí native fullscreenu
            window.setExtendedState(JFrame.NORMAL);

            // Návrat do základního okna
            WIDTH = 800;
            HEIGHT = 600;
            setPreferredSize(new Dimension(WIDTH, HEIGHT));
            window.pack();
            window.setLocationRelativeTo(null);
        }
        window.setVisible(true); // Znovu ukážeme okno
    }

    private Image loadImage(String path) {
        if (path == null) return null;
        URL imageUrl = getClass().getResource(path);
        if (imageUrl != null) {
            return new ImageIcon(imageUrl).getImage();
        } else {
            System.out.println("Obrázek nenalezen: " + path);
            return null;
        }
    }

    private void saveLevelProgress() {
        try (FileWriter writer = new FileWriter("level.txt", false)) {
            writer.write("Ulozeny level: " + currentWave);
        } catch (IOException e) {
            System.out.println("Chyba při ukládání levelu: " + e.getMessage());
        }
    }

    private void resetGame(int startWave) {
        currentWave = startWave;
        player = new Player(WIDTH / 2.0, HEIGHT / 2.0);
        player.level = (startWave >= 3) ? 3 : startWave;

        Image[] walkAnim = new Image[]{
                loadImage("/player_walk1.png"),
                loadImage("/player_walk2.png")
        };
        player.setAnimations(walkAnim);

        enemies.clear();
        projectiles.clear();
        enemyProjectiles.clear();
        isGameOver = false;

        saveLevelProgress();
        audioManager.playMusicForWave(Math.min(currentWave, 3));
        startNewWave();
        gameState = State.PLAYING;
    }

    private void startNewWave() {
        int enemyCount = (currentWave <= 3) ? 2 + (currentWave * 2) : 5 + (currentWave * 3);

        for (int i = 0; i < enemyCount; i++) {
            double spawnX, spawnY;
            if (Math.random() < 0.5) {
                spawnX = Math.random() < 0.5 ? -50 : WIDTH + 50;
                spawnY = Math.random() * HEIGHT;
            } else {
                spawnX = Math.random() * WIDTH;
                spawnY = Math.random() < 0.5 ? -50 : HEIGHT + 50;
            }

            Type typeToSpawn = Type.THIEVES;
            if (currentWave == 2) {
                if (Math.random() < 0.3) typeToSpawn = Type.BANDITS;
            } else if (currentWave == 3) {
                double rand = Math.random();
                if (rand < 0.4) typeToSpawn = Type.BANDITS;
                else if (rand < 0.6) typeToSpawn = Type.ARCHERS;
            } else if (currentWave > 3) {
                double rand = Math.random();
                if (rand < 0.3) typeToSpawn = Type.BANDITS;
                else if (rand < 0.6) typeToSpawn = Type.ARCHERS;
            }

            enemies.add(new Enemy(spawnX, spawnY, typeToSpawn, currentWave));
        }
    }

    public void startGame() {
        if (gameThread == null) {
            isRunning = true;
            gameThread = new Thread(this);
            gameThread.start();
        }
    }

    @Override
    public void run() {
        double drawInterval = 1000000000.0 / FPS;
        double delta = 0;
        long lastTime = System.nanoTime();
        long currentTime;

        while (isRunning) {
            currentTime = System.nanoTime();
            delta += (currentTime - lastTime) / drawInterval;
            lastTime = currentTime;

            if (delta >= 1) {
                if (gameState == State.PLAYING && !isGameOver) {
                    update();
                }
                repaint();
                delta--;
            }
        }
    }

    private void update() {
        if (player.hp <= 0) {
            if (!isGameOver) {
                isGameOver = true;
                audioManager.playGameOverMusic();
            }
            return;
        }

        player.update(up, down, left, right, WIDTH, HEIGHT);

        if (enemies.isEmpty()) {
            currentWave++;
            player.level = Math.min(currentWave, 3);
            saveLevelProgress();
            audioManager.playMusicForWave(Math.min(currentWave, 3));

            if (player.activeWeapon == 2 && currentWave < 2) player.activeWeapon = 1;
            if (player.activeWeapon == 3 && currentWave < 3) player.activeWeapon = 1;
            startNewWave();
        }

        for (Projectile p : projectiles) {
            p.update();
            if (p.x < 0 || p.x > WIDTH || p.y < 0 || p.y > HEIGHT) {
                projectiles.remove(p);
                continue;
            }

            for (Enemy enemy : enemies) {
                if (p.getHitbox().intersects(enemy.getHitbox())) {
                    if (p.type == 1) {
                        if (player.level >= 3) {
                            ArrayList<Enemy> nearby = new ArrayList<>();
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

        for (Projectile p : enemyProjectiles) {
            p.update();
            if (p.x < 0 || p.x > WIDTH || p.y < 0 || p.y > HEIGHT) {
                enemyProjectiles.remove(p);
                continue;
            }

            if (p.getHitbox().intersects(player.getHitbox())) {
                player.takeDamage(10);
                enemyProjectiles.remove(p);
            }
            else if (player.isShieldActive) {
                double distToPlayer = Math.hypot(p.x - player.x, p.y - player.y);
                if (distToPlayer < player.size + 40) {
                    enemyProjectiles.remove(p);
                }
            }
        }

        for (Enemy enemy : enemies) {
            if (enemy.hp <= 0) {
                enemies.remove(enemy);
                continue;
            }

            enemy.update(player.x, player.y, enemies, enemyProjectiles);

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
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        if (gameState == State.MENU) {
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 50));
            g2.drawString("MJUN GAME", WIDTH / 2 - 150, HEIGHT / 3);

            g2.setFont(new Font("Arial", Font.PLAIN, 25));
            g2.drawString("[1] Nová Hra (Level 1)", WIDTH / 2 - 120, HEIGHT / 2);
            g2.drawString("[2] Endless Mód (Level 4)", WIDTH / 2 - 120, HEIGHT / 2 + 40);
            g2.drawString("[3] Nastavení", WIDTH / 2 - 120, HEIGHT / 2 + 80);
            return;
        }

        if (gameState == State.SETTINGS) {
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 40));
            g2.drawString("NASTAVENÍ", WIDTH / 2 - 110, HEIGHT / 4);

            g2.setFont(new Font("Arial", Font.PLAIN, 20));

            // UI Nastavení
            g2.setColor(isFullscreen ? Color.GREEN : Color.GRAY);
            g2.drawString("[F] Fullscreen: " + (isFullscreen ? "ZAPNUTO" : "VYPNUTO"), WIDTH / 2 - 150, HEIGHT / 2 - 40);

            g2.setColor(Color.WHITE);
            g2.drawString("[Šipky Nahoru/Dolů] Hlasitost: " + volume + " %", WIDTH / 2 - 150, HEIGHT / 2 + 10);

            // Kreslení Volume Baru
            g2.setColor(Color.DARK_GRAY);
            g2.fillRect(WIDTH / 2 - 150, HEIGHT / 2 + 30, 200, 20);
            g2.setColor(Color.GREEN);
            g2.fillRect(WIDTH / 2 - 150, HEIGHT / 2 + 30, volume * 2, 20);
            g2.setColor(Color.WHITE);
            g2.drawRect(WIDTH / 2 - 150, HEIGHT / 2 + 30, 200, 20);

            g2.setColor(Color.YELLOW);
            g2.drawString("[ESC] Návrat do Menu", WIDTH / 2 - 100, HEIGHT - 100);
            return;
        }

        if (isGameOver) {
            g2.setColor(Color.RED);
            g2.setFont(new Font("Arial", Font.BOLD, 60));
            String msg = "Prohráli jste";
            int msgWidth = g2.getFontMetrics().stringWidth(msg);
            g2.drawString(msg, (WIDTH - msgWidth) / 2, HEIGHT / 2);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.PLAIN, 20));
            String subMsg = "Stiskněte MEZERNÍK pro návrat do Menu";
            int subMsgWidth = g2.getFontMetrics().stringWidth(subMsg);
            g2.drawString(subMsg, (WIDTH - subMsgWidth) / 2, HEIGHT / 2 + 50);
            return;
        }

        for (Projectile p : projectiles) p.draw(g2);
        for (Projectile p : enemyProjectiles) p.draw(g2);
        for (Enemy enemy : enemies) enemy.draw(g2);
        player.draw(g2);

        g2.setFont(new Font("Arial", Font.BOLD, 18));
        g2.setColor(Color.ORANGE);
        if (currentWave > 3) {
            g2.setColor(Color.MAGENTA);
            g2.drawString("  ENDLESS VLNA " + currentWave, WIDTH / 2 - 40, 25);
        } else {
            g2.drawString("VLNA " + currentWave, WIDTH / 2 - 35, 25);
        }

        String weaponName = switch (player.activeWeapon) {
            case 1 -> "Ohnivá střela";
            case 2 -> "Zmrazení";
            case 3 -> "Wind Štít";
            default -> "Neznámá";
        };

        long cd = player.getRemainingCooldown();
        long swapCd = player.getSwapCooldown();
        int yOffset = 25;

        if (swapCd > 0) {
            g2.setColor(Color.YELLOW);
            g2.drawString("Výměna zbraně: " + String.format("%.1f", swapCd / 1000.0) + "s", 10, yOffset);
        } else if (cd > 0) {
            g2.setColor(Color.RED);
            g2.drawString("Zbraň: " + weaponName + " [ ČEKÁ: " + String.format("%.1f", cd / 1000.0) + "s ]", 10, yOffset);
        } else {
            g2.setColor(Color.GREEN);
            g2.drawString("Zbraň: " + weaponName + " [ PŘIPRAVENO ]", 10, yOffset);
        }

        g2.setFont(new Font("Arial", Font.PLAIN, 12));
        g2.setColor(Color.LIGHT_GRAY);
        if (currentWave == 1) g2.drawString("Povoleno: [1] Oheň", 10, HEIGHT - 20);
        else if (currentWave == 2) g2.drawString("Povoleno: [1] Oheň, [2] Mráz", 10, HEIGHT - 20);
        else g2.drawString("Povoleno: [1] Oheň, [2] Mráz, [3] Štít", 10, HEIGHT - 20);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (gameState != State.PLAYING || isGameOver) return;

        if (SwingUtilities.isLeftMouseButton(e) && player.canUseAbility()) {
            if (player.activeWeapon == 1 || player.activeWeapon == 2) {
                double startX = player.x + player.size / 2.0;
                double startY = player.y + player.size / 2.0;
                projectiles.add(new Projectile(startX, startY, e.getX(), e.getY(), player.activeWeapon, false));
                player.useAbility();
            } else if (player.activeWeapon == 3) {
                player.activateShield(3000, enemies);
            }
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int key = e.getKeyCode();

        if (gameState == State.MENU) {
            if (key == KeyEvent.VK_1) resetGame(1);
            if (key == KeyEvent.VK_2) resetGame(4);
            if (key == KeyEvent.VK_3) gameState = State.SETTINGS;
            return;
        }

        if (gameState == State.SETTINGS) {
            if (key == KeyEvent.VK_ESCAPE) gameState = State.MENU;
            if (key == KeyEvent.VK_F) toggleFullscreen();
            if (key == KeyEvent.VK_UP && volume < 100) {
                volume += 10;
                audioManager.setVolume(volume);
            }
            if (key == KeyEvent.VK_DOWN && volume > 0) {
                volume -= 10;
                audioManager.setVolume(volume);
            }
            return;
        }

        if (gameState == State.PLAYING) {
            if (isGameOver && key == KeyEvent.VK_SPACE) {
                gameState = State.MENU;
                audioManager.playMenuMusic();
                return;
            }

            if (key == KeyEvent.VK_W || key == KeyEvent.VK_UP) up = true;
            if (key == KeyEvent.VK_S || key == KeyEvent.VK_DOWN) down = true;
            if (key == KeyEvent.VK_A || key == KeyEvent.VK_LEFT) left = true;
            if (key == KeyEvent.VK_D || key == KeyEvent.VK_RIGHT) right = true;
            if (key == KeyEvent.VK_1) player.swapWeapon(1, currentWave);
            if (key == KeyEvent.VK_2) player.swapWeapon(2, currentWave);
            if (key == KeyEvent.VK_3) player.swapWeapon(3, currentWave);
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

    public static void main(String[] args) {
        JFrame window = new JFrame("MJUN GAME");
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);

        // Předání okna do konstruktoru GameArena
        GameArena game = new GameArena(window);

        window.add(game);
        window.pack();
        window.setLocationRelativeTo(null);
        window.setVisible(true);
        game.startGame();
    }
}