package org.example.logic.managers;

import org.example.logic.entities.Player;
import org.example.logic.items.Item;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class InventoryManager {
    public List<Item> items = new ArrayList<>();
    public int equippedComboId = 0; // 0 = žádné kombo, 4 = Aura, 5 = SuperNova, 6 = Vánice

    public void addItem(Item item) { items.add(item); }

    // --- LOGIKA KOVÁŘSTVÍ ---
    public boolean canCraftAnything() {
        if (getShardCount() >= 3) return true;
        if (hasItem("fire_crystal") && hasItem("wind_crystal")) return true;
        if (hasItem("fire_crystal") && hasItem("ice_crystal")) return true;
        if (hasItem("wind_crystal") && hasItem("ice_crystal")) return true;

        for (int i = 0; i < items.size(); i++) {
            Item current = items.get(i);
            if (current.type == Item.Type.CRAFTING_MAT || current.type == Item.Type.COMBO_ABILITY) continue;
            int count = 0;
            for (Item other : items) {
                if (other.id.equals(current.id) && other.level == current.level) count++;
            }
            if (count >= 3) return true;
        }
        return false;
    }

    public void processCraftingResult(boolean success, Player player) {
        if (getShardCount() >= 3) {
            consumeShards(3);
            if (success) player.level++;
            applyBonusesToPlayer(player);
            return;
        }

        if (hasItem("fire_crystal") && hasItem("wind_crystal")) {
            removeItem("fire_crystal"); removeItem("wind_crystal");
            if (success) {
                items.add(Item.createComboFireWind());
                if (equippedComboId == 0) equippedComboId = 4;
            }
            applyBonusesToPlayer(player);
            return;
        }
        if (hasItem("fire_crystal") && hasItem("ice_crystal")) {
            removeItem("fire_crystal"); removeItem("ice_crystal");
            if (success) {
                items.add(Item.createComboIceFire());
                if (equippedComboId == 0) equippedComboId = 5;
            }
            applyBonusesToPlayer(player);
            return;
        }
        if (hasItem("wind_crystal") && hasItem("ice_crystal")) {
            removeItem("wind_crystal"); removeItem("ice_crystal");
            if (success) {
                items.add(Item.createComboWindIce());
                if (equippedComboId == 0) equippedComboId = 6;
            }
            applyBonusesToPlayer(player);
            return;
        }

        for (int i = 0; i < items.size(); i++) {
            Item current = items.get(i);
            if (current.type == Item.Type.CRAFTING_MAT || current.type == Item.Type.COMBO_ABILITY) continue;

            List<Item> matches = new ArrayList<>();
            for (Item other : items) {
                if (other.id.equals(current.id) && other.level == current.level) matches.add(other);
            }

            if (matches.size() >= 3) {
                items.remove(matches.get(0)); items.remove(matches.get(1)); items.remove(matches.get(2));
                if (success) items.add(current.createUpgradedVersion());
                applyBonusesToPlayer(player);
                return;
            }
        }
    }

    private boolean hasItem(String id) {
        for (Item i : items) if (i.id.equals(id)) return true;
        return false;
    }

    private void removeItem(String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) { items.remove(i); return; }
        }
    }

    // TATO METODA CHYBĚLA - Slouží pro přepínání přes klávesu E
    public void cycleEquippedCombo() {
        List<Integer> ownedCombos = new ArrayList<>();
        for (Item i : items) {
            if (i.type == Item.Type.COMBO_ABILITY) ownedCombos.add(i.unlocksWeaponId);
        }
        if (ownedCombos.isEmpty()) return;

        int currentIndex = ownedCombos.indexOf(equippedComboId);
        if (currentIndex == -1 || currentIndex == ownedCombos.size() - 1) {
            equippedComboId = ownedCombos.get(0);
        } else {
            equippedComboId = ownedCombos.get(currentIndex + 1);
        }
    }

    public void applyBonusesToPlayer(Player player) {
        player.bonusDamage = 0; player.bonusSpeed = 0.0;
        int totalMaxHp = 100;
        int highestWeaponUnlock = 1;

        for (Item item : items) {
            player.bonusDamage += item.bonusDamage;
            player.bonusSpeed += item.bonusSpeed;
            totalMaxHp += item.bonusMaxHp;
            if (item.type == Item.Type.WEAPON_UNLOCK && item.unlocksWeaponId > highestWeaponUnlock) {
                highestWeaponUnlock = item.unlocksWeaponId;
            }
        }

        if (highestWeaponUnlock > player.level) player.level = highestWeaponUnlock;
        if (equippedComboId != 0 && player.level < 4) player.level = 4;

        if (totalMaxHp > player.maxHp) {
            int difference = totalMaxHp - player.maxHp;
            player.maxHp = totalMaxHp;
            player.hp += difference;
        }
    }

    public int getShardCount() {
        int count = 0;
        for (Item i : items) { if (i.id.equals("shard")) count++; }
        return count;
    }

    public void consumeShards(int amount) {
        int removed = 0;
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).id.equals("shard")) {
                items.remove(i); removed++; if (removed >= amount) break;
            }
        }
    }

    // --- LOGIKA PRO MYŠ ---
    private int getHoveredItemIndex(int mx, int my, int screenW, int screenH) {
        int slotSize = 60, padding = 15;
        int startX = screenW / 2 - (slotSize * 5 + padding * 4) / 2;
        int startY = 180;

        for (int i = 0; i < 20; i++) {
            int row = i / 5; int col = i % 5;
            int x = startX + col * (slotSize + padding);
            int y = startY + row * (slotSize + padding);
            if (mx >= x && mx <= x + slotSize && my >= y && my <= y + slotSize) return i;
        }
        return -1;
    }

    // Přepne zbraň při kliknutí levým tlačítkem v GamePanelu
    public boolean tryEquipItemAt(int mx, int my, int screenW, int screenH) {
        int index = getHoveredItemIndex(mx, my, screenW, screenH);
        if (index >= 0 && index < items.size()) {
            Item item = items.get(index);
            if (item.type == Item.Type.COMBO_ABILITY) {
                equippedComboId = item.unlocksWeaponId;
                return true;
            }
        }
        return false;
    }

    // --- VYKRESLOVÁNÍ INVENTÁŘE ---
    public void draw(Graphics2D g2, int screenW, int screenH, int mouseX, int mouseY) {
        g2.setColor(new Color(0, 0, 0, 220)); g2.fillRect(0, 0, screenW, screenH);

        g2.setColor(Color.WHITE); g2.setFont(new Font("Arial", Font.BOLD, 40));
        g2.drawString("INVENTÁŘ", screenW / 2 - 100, 80);

        g2.setFont(new Font("Arial", Font.PLAIN, 18));
        g2.drawString("[TAB] Zpět do hry", screenW / 2 - 80, 120);

        if (canCraftAnything()) {
            g2.setColor(Color.ORANGE);
            g2.drawString(">>> [C] KOVAT VYLEPŠENÍ (Riskantní!) <<<", screenW / 2 - 180, 520);
        }

        int slotSize = 60, padding = 15;
        int startX = screenW / 2 - (slotSize * 5 + padding * 4) / 2;
        int startY = 180;

        for (int i = 0; i < 20; i++) {
            int row = i / 5; int col = i % 5;
            int x = startX + col * (slotSize + padding);
            int y = startY + row * (slotSize + padding);

            g2.setColor(new Color(50, 50, 50, 150));
            g2.fillRoundRect(x, y, slotSize, slotSize, 10, 10);

            // Zlaté ohraničení, pokud je kombo vybavené
            if (i < items.size() && items.get(i).type == Item.Type.COMBO_ABILITY && items.get(i).unlocksWeaponId == equippedComboId) {
                g2.setColor(Color.YELLOW);
                g2.setStroke(new BasicStroke(3));
            } else {
                g2.setColor(Color.GRAY);
                g2.setStroke(new BasicStroke(1));
            }
            g2.drawRoundRect(x, y, slotSize, slotSize, 10, 10);
            g2.setStroke(new BasicStroke(1));

            if (i < items.size()) {
                Item item = items.get(i);
                g2.setColor(item.color); g2.fillRoundRect(x + 10, y + 10, 40, 40, 5, 5);
                g2.setColor(Color.WHITE); g2.setFont(new Font("Arial", Font.BOLD, 12));

                if(item.type == Item.Type.CRAFTING_MAT) g2.drawString("Mat", x + 15, y + 35);
                else if (item.type == Item.Type.COMBO_ABILITY) g2.drawString("Komb", x + 15, y + 35);
                else g2.drawString("Lvl " + item.level, x + 15, y + 35);
            }
        }

        // Vykreslení tooltipu (info tabulky), pokud je nad něčím myš
        drawTooltip(g2, mouseX, mouseY, screenW, screenH);
    }

    private void drawTooltip(Graphics2D g2, int mx, int my, int screenW, int screenH) {
        int index = getHoveredItemIndex(mx, my, screenW, screenH);
        if (index >= 0 && index < items.size()) {
            Item item = items.get(index);
            List<String> lines = new ArrayList<>();

            lines.add(item.name + (item.level > 1 ? " (Lvl " + item.level + ")" : ""));
            if (item.bonusDamage > 0) lines.add("Poškození: +" + item.bonusDamage);
            if (item.bonusMaxHp != 0) lines.add("Max HP: " + (item.bonusMaxHp > 0 ? "+" : "") + item.bonusMaxHp);
            if (item.bonusSpeed > 0) lines.add("Rychlost: +" + item.bonusSpeed);

            if (item.type == Item.Type.COMBO_ABILITY) {
                if (item.unlocksWeaponId == 4) lines.add("Efekt: Spaluje nepřátele v tvém okolí.");
                if (item.unlocksWeaponId == 5) lines.add("Efekt: 8-směrný ničivý a mrazivý výbuch.");
                if (item.unlocksWeaponId == 6) lines.add("Efekt: Rychlopalná ledová brokovnice.");
                lines.add("[LEVY KLIK] Nastavit jako aktivní kombo");
            }

            lines.add("--- CRAFTING INFO ---");
            if (item.type == Item.Type.CRAFTING_MAT) {
                if (item.id.equals("shard")) lines.add("Kovadlina [C]: 3x Úlomek = Lvl Up hráče");
                else if (item.id.equals("fire_crystal")) lines.add("Kombinuj s: Led, Vítr na [C]");
            } else if (item.type == Item.Type.STAT_BOOST) {
                lines.add("3x Stejný Level na [C] = Upgrade");
            } else if (item.type == Item.Type.COMBO_ABILITY) {
                lines.add("Finální zbraň, nelze vylepšit.");
            } else if (item.type == Item.Type.WEAPON_UNLOCK) {
                lines.add("Odemkne ti zbraň v liště. Zkombinuj");
                lines.add("s ohněm na [C] pro vytvoření Komba.");
            }

            int boxW = 320;
            int boxH = lines.size() * 20 + 20;
            int boxX = mx + 15; int boxY = my + 15;

            if (boxX + boxW > screenW) boxX = mx - boxW - 15;
            if (boxY + boxH > screenH) boxY = screenH - boxH - 10;

            g2.setColor(new Color(20, 20, 25, 240));
            g2.fillRoundRect(boxX, boxY, boxW, boxH, 10, 10);
            g2.setColor(item.color);
            g2.drawRoundRect(boxX, boxY, boxW, boxH, 10, 10);

            int textY = boxY + 25;
            for (int i = 0; i < lines.size(); i++) {
                if(i == 0) {
                    g2.setColor(item.color); g2.setFont(new Font("Arial", Font.BOLD, 16));
                } else if (lines.get(i).startsWith("---")) {
                    g2.setColor(Color.ORANGE); g2.setFont(new Font("Arial", Font.BOLD, 14)); textY += 5;
                } else if (lines.get(i).startsWith("[LEVY KLIK]")) {
                    g2.setColor(Color.GREEN); g2.setFont(new Font("Arial", Font.ITALIC, 14)); textY += 5;
                } else {
                    g2.setColor(Color.WHITE); g2.setFont(new Font("Arial", Font.PLAIN, 14));
                }
                g2.drawString(lines.get(i), boxX + 15, textY);
                textY += 20;
            }
        }
    }
}