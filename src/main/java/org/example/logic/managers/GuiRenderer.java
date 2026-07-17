package org.example.logic.managers;

import org.example.logic.entities.Player;
import java.awt.*;

public class GuiRenderer {

    public void drawHUD(Graphics2D g2, Player player, int screenW, int screenH) {
        // --- PLACEHOLDERY PRO HUD (FPS styl: rohy a okraje) ---

        // 1. HP BAR (Vlevo dole - Úhlový design)
        int hpW = 200;
        int hpH = 25;
        int hpX = 40;
        int hpY = screenH - 60;

        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(hpX, hpY, hpW, hpH);

        g2.setColor(Color.RED);
        int currentHpW = (int)((player.hp / (double)player.maxHp) * hpW);
        g2.fillRect(hpX, hpY, currentHpW, hpH);

        g2.setColor(Color.WHITE);
        g2.drawRect(hpX, hpY, hpW, hpH);
        g2.drawString("VIT_DATA", hpX, hpY - 5);

        // 2. DASH/STAMINA (Vpravo dole)
        int dashW = 150;
        int dashX = screenW - 190;
        g2.setColor(new Color(0, 0, 0, 150));
        g2.fillRect(dashX, hpY, dashW, 10);
        g2.setColor(Color.CYAN);
        // Příklad cooldownu:
        double dashCooldown = (System.currentTimeMillis() - player.lastDashTime) / (double)player.DASH_COOLDOWN;
        g2.fillRect(dashX, hpY, (int)(Math.min(1.0, dashCooldown) * dashW), 10);
        g2.setColor(Color.WHITE);
        g2.drawRect(dashX, hpY, dashW, 10);

        // 3. IMMERSIVE VIGNETTE (Ztmavující okraje)
        // Toto dává hře hloubku
        float[] dist = {0.0f, 1.0f};
        Color[] colors = {new Color(0, 0, 0, 0), new Color(0, 0, 0, 100)};
        RadialGradientPaint rgp = new RadialGradientPaint(
                new Point(screenW/2, screenH/2), screenW, dist, colors);
        g2.setPaint(rgp);
        g2.fillRect(0, 0, screenW, screenH);
    }
}