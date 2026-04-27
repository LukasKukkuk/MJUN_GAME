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

    public boolean combineItems() {
        boolean combinedSomething = false;

        // 1. MAGIE SLUČOVÁNÍ 3 STEJNÝCH (Stat boosty)
        for (int i = 0; i < items.size(); i++) {
            Item current = items.get(i);
            if (current.type == Item.Type.CRAFTING_MAT || current.type == Item.Type.COMBO_ABILITY) continue;

            List<Item> matches = new ArrayList<>();
            for (Item other : items) {
                if (other.id.equals(current.id) && other.level == current.level) matches.add(other);
            }

            if (matches.size() >= 3) {
                items.remove(matches.get(0)); items.remove(matches.get(1)); items.remove(matches.get(2));
                items.add(current.createUpgradedVersion());
                combinedSomething = true;
                break;
            }
        }

        // 2. KŘÍŽENÍ ELEMENTŮ (Krystaly)
        if (hasItem("fire_crystal") && hasItem("wind_crystal")) {
            removeItem("fire_crystal"); removeItem("wind_crystal");
            items.add(Item.createComboFireWind());
            if (equippedComboId == 0) equippedComboId = 4;
            combinedSomething = true;
        }
        else if (hasItem("fire_crystal") && hasItem("ice_crystal")) {
            removeItem("fire_crystal"); removeItem("ice_crystal");
            items.add(Item.createComboIceFire());
            if (equippedComboId == 0) equippedComboId = 5;
            combinedSomething = true;
        }
        else if (hasItem("wind_crystal") && hasItem("ice_crystal")) {
            removeItem("wind_crystal"); removeItem("ice_crystal");
            items.add(Item.createComboWindIce());
            if (equippedComboId == 0) equippedComboId = 6;
            combinedSomething = true;
        }

        if (combinedSomething) combineItems(); // Kaskáda
        return combinedSomething;
    }

    // Pomocné metody
    private boolean hasItem(String id) {
        for (Item i : items) if (i.id.equals(id)) return true;
        return false;
    }
    private void removeItem(String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id.equals(id)) { items.remove(i); return; }
        }
    }

    // Přepínání komba
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
        int highestWeaponUnlock = 1; // Základní zbraň

        for (Item item : items) {
            player.bonusDamage += item.bonusDamage;
            player.bonusSpeed += item.bonusSpeed;
            totalMaxHp += item.bonusMaxHp;

            // Odemčení zbraní 2 a 3
            if (item.type == Item.Type.WEAPON_UNLOCK && item.unlocksWeaponId > highestWeaponUnlock) {
                highestWeaponUnlock = item.unlocksWeaponId;
            }
        }

        // Aktualizace levelu hráče na základě odemčených krystalů (aby mohl měnit na zbraň 2 a 3)
        if (highestWeaponUnlock > player.level) player.level = highestWeaponUnlock;
        // Pokud má nějaké kombo, odemkne se mu i 4. slot
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

    // Zpracuje výsledek minihry. Pokud success = true, vytvoří item. Pokud false, zničí suroviny!
    public void processCraftingResult(boolean success, Player player) {
        // 1. Zkratka pro úlomky zbraně (Zvyšují level hráče = odemykají zbraně 1-3)
        if (getShardCount() >= 3) {
            consumeShards(3);
            if (success) player.level++;
            applyBonusesToPlayer(player);
            return;
        }

        // 2. Křížení elementů (Komba pro slot 4)
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

        // 3. Slučování 3 stejných předmětů (Stat boosty)
        for (int i = 0; i < items.size(); i++) {
            Item current = items.get(i);
            if (current.type == Item.Type.CRAFTING_MAT || current.type == Item.Type.COMBO_ABILITY) continue;

            List<Item> matches = new ArrayList<>();
            for (Item other : items) {
                if (other.id.equals(current.id) && other.level == current.level) matches.add(other);
            }

            if (matches.size() >= 3) {
                items.remove(matches.get(0)); items.remove(matches.get(1)); items.remove(matches.get(2));
                if (success) {
                    items.add(current.createUpgradedVersion());
                }
                applyBonusesToPlayer(player);
                return;
            }
        }
    }

    public void draw(Graphics2D g2, int screenW, int screenH) {
        g2.setColor(new Color(0, 0, 0, 220)); g2.fillRect(0, 0, screenW, screenH);

        g2.setColor(Color.WHITE); g2.setFont(new Font("Arial", Font.BOLD, 40));
        g2.drawString("INVENTÁŘ", screenW / 2 - 100, 60);

        g2.setFont(new Font("Arial", Font.PLAIN, 18));
        g2.drawString("[K] Sloučit Itemy & Krystaly  |  [TAB] Zpět do hry", screenW / 2 - 190, 100);

        // UI pro výběr Komba
        if (equippedComboId != 0) {
            g2.setColor(Color.CYAN);
            String comboName = (equippedComboId == 4) ? "Ohnivá Aura" : (equippedComboId == 5) ? "SuperNova (8-směr)" : "Vánice (Rychlopalba)";
            g2.drawString(">>> [E] Vybavené Kombo (Zbraň 4): " + comboName + " <<<", screenW / 2 - 250, 130);
        }

        if (getShardCount() >= 3) {
            g2.setColor(Color.ORANGE);
            g2.drawString(">>> [C] KOVAT NOVOU ZBRAŇ (Stojí 3 Úlomky) <<<", screenW / 2 - 230, 160);
        }

        int slotSize = 60, padding = 15;
        int startX = screenW / 2 - (slotSize * 5 + padding * 4) / 2;
        int startY = 180;
        int row = 0, col = 0;

        for (int i = 0; i < 20; i++) {
            int x = startX + col * (slotSize + padding);
            int y = startY + row * (slotSize + padding);

            g2.setColor(new Color(50, 50, 50, 150));
            g2.fillRoundRect(x, y, slotSize, slotSize, 10, 10);
            g2.setColor(Color.GRAY); g2.drawRoundRect(x, y, slotSize, slotSize, 10, 10);

            if (i < items.size()) {
                Item item = items.get(i);
                g2.setColor(item.color); g2.fillRoundRect(x + 10, y + 10, 40, 40, 5, 5);
                g2.setColor(Color.WHITE); g2.setFont(new Font("Arial", Font.BOLD, 12));

                if(item.type == Item.Type.CRAFTING_MAT) g2.drawString("Mat", x + 15, y + 35);
                else if (item.type == Item.Type.COMBO_ABILITY) g2.drawString("Komb", x + 15, y + 35);
                else g2.drawString("Lvl " + item.level, x + 15, y + 35);
            }
            col++; if (col >= 5) { col = 0; row++; }
        }
    }
}