package org.example.logic.managers;

import org.example.logic.entities.Player;
import java.awt.*;

public class GuiRenderer {

    private static final Font LABEL_FONT = new Font("Arial", Font.BOLD, 12);

    // Vlastní vignette-gradient si necháváme napříč snímky - RadialGradientPaint
    // se přepočítává jen při změně rozměrů okna, ne při každém volání drawHUD().
    private RadialGradientPaint cachedVignette;
    private int cachedW = -1, cachedH = -1;

    public void drawHUD(Graphics2D g2, Player player, int screenW, int screenH) {
        if (player == null) return;

        // 1. HP BAR (vlevo nahoře, mimo cestu spodnímu panelu zbraní a vlnovému textu nahoře uprostřed)
        int hpW = 200;
        int hpH = 22;
        int hpX = 20;
        int hpY = 20;

        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(hpX, hpY, hpW, hpH);

        g2.setColor(Color.RED);
        int currentHpW = (int) ((player.hp / (double) player.maxHp) * hpW);
        if (currentHpW < 0) currentHpW = 0;
        g2.fillRect(hpX, hpY, currentHpW, hpH);

        g2.setColor(Color.WHITE);
        g2.drawRect(hpX, hpY, hpW, hpH);
        g2.setFont(LABEL_FONT);
        g2.drawString("HP " + player.hp + "/" + player.maxHp, hpX, hpY - 5);

        // 2. DASH/STAMINA (vpravo nahoře, zrcadlově k HP baru)
        int dashW = 150;
        int dashH = 10;
        int dashX = screenW - 20 - dashW;
        int dashY = hpY + hpH + 6;

        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(dashX, dashY, dashW, dashH);
        g2.setColor(player.isDashing ? Color.GRAY : Color.CYAN);
        double dashReady = (System.currentTimeMillis() - player.lastDashTime) / (double) Player.DASH_COOLDOWN;
        g2.fillRect(dashX, dashY, (int) (Math.min(1.0, Math.max(0.0, dashReady)) * dashW), dashH);
        g2.setColor(Color.WHITE);
        g2.drawRect(dashX, dashY, dashW, dashH);
        g2.setFont(LABEL_FONT);
        String dashLabel = dashReady >= 1.0 ? "DASH" : "DASH...";
        g2.drawString(dashLabel, dashX + dashW - g2.getFontMetrics().stringWidth(dashLabel), dashY - 4);

        // 3. IMMERSIVE VIGNETTE (Ztmavující okraje)
        if (cachedVignette == null || cachedW != screenW || cachedH != screenH) {
            float[] dist = {0.0f, 1.0f};
            Color[] colors = {new Color(0, 0, 0, 0), new Color(0, 0, 0, 100)};
            cachedVignette = new RadialGradientPaint(
                    new Point(screenW / 2, screenH / 2), screenW, dist, colors);
            cachedW = screenW;
            cachedH = screenH;
        }
        g2.setPaint(cachedVignette);
        g2.fillRect(0, 0, screenW, screenH);
        g2.setPaint(null);
    }
}