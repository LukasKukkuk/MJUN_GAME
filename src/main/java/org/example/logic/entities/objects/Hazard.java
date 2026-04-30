package org.example.logic.entities.objects;

import java.awt.*;

public class Hazard {
    public enum Type { BARREL, SPIKE, LAVA }

    public double x, y;
    public int size;
    public Type type;
    public int hp;

    // Pro bodáky (vysouvají a zasouvají se)
    public boolean isActive = false;
    private long lastToggleTime = 0;

    public Hazard(double x, double y, Type type) {
        this.x = x;
        this.y = y;
        this.type = type;

        if (type == Type.BARREL) {
            this.size = 40;
            this.hp = 30; // Dá se zničit
        } else if (type == Type.SPIKE) {
            this.size = 50;
            this.hp = 9999; // Nezničitelné
        } else if (type == Type.LAVA) {
            this.size = 100;
            this.hp = 9999;
            this.isActive = true; // Láva pálí pořád
        }
    }

    public void update() {
        if (type == Type.SPIKE) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastToggleTime > 2000) { // Každé 2 vteřiny
                isActive = !isActive;
                lastToggleTime = currentTime;
            }
        }
    }

    public void draw(Graphics2D g2) {
        if (type == Type.BARREL) {
            g2.setColor(new Color(139, 69, 19)); // Hnědá
            g2.fillOval((int)x, (int)y, size, size);
            g2.setColor(Color.RED); // Značka výbušniny
            g2.fillRect((int)x + 15, (int)y + 10, 10, 20);
        }
        else if (type == Type.SPIKE) {
            g2.setColor(Color.DARK_GRAY);
            g2.fillRect((int)x, (int)y, size, size); // Podstavec
            if (isActive) {
                g2.setColor(Color.LIGHT_GRAY);
                // Vykreslení bodáků
                for(int i=0; i<3; i++) {
                    for(int j=0; j<3; j++) {
                        g2.fillOval((int)x + 5 + i*15, (int)y + 5 + j*15, 10, 10);
                    }
                }
            }
        }
        else if (type == Type.LAVA) {
            // Pulzující láva
            int alpha = (int) (150 + Math.sin(System.currentTimeMillis() / 200.0) * 50);
            g2.setColor(new Color(255, 60, 0, alpha));
            g2.fillOval((int)x, (int)y, size, size);
        }
    }

    public Rectangle getHitbox() { return new Rectangle((int)x, (int)y, size, size); }
}