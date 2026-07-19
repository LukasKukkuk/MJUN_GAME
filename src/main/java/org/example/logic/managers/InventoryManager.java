package org.example.logic.managers;

import org.example.logic.entities.Player;
import org.example.logic.items.Item;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class InventoryManager {
    public List<Item> items = new ArrayList<>();
    public int equippedComboId = 0; // 0 = žádné kombo, 4 = Aura, 5 = SuperNova, 6 = Vánice, 7 = Meteor

    // Kombinování je teď explicitní: hráč klikne na první předmět (vybere ho),
    // pak na druhý, se kterým ho chce zkombinovat. Žádné skryté auto-slučování.
    public int selectedSlot = -1;

    public void addItem(Item item) { items.add(item); }

    private boolean isCrystal(Item i) {
        return i.id.equals("fire_crystal") || i.id.equals("ice_crystal") || i.id.equals("wind_crystal");
    }

    // Pravidla kombinování - jasně a nezávisle na tom, co má hráč zrovna náhodou 3x:
    //  1. dva úlomky zbraně -> přímý bonus poškození
    //  2. dva RŮZNÉ krystaly -> kombo zbraň (případně ultimátní Meteor, pokud má hráč všechny 3)
    //  3. dva stejné vylepšitelné předměty (ne suroviny, ne hotová komba) -> upgrade na vyšší úroveň
    public boolean canCombine(Item a, Item b) {
        if (a == b) return false;
        if (a.id.equals("shard") && b.id.equals("shard")) return true;
        if (isCrystal(a) && isCrystal(b)) return !a.id.equals(b.id);
        if (isCrystal(a) || isCrystal(b)) return false;
        if (a.type == Item.Type.CRAFTING_MAT || b.type == Item.Type.CRAFTING_MAT) return false;
        if (a.type == Item.Type.COMBO_ABILITY || b.type == Item.Type.COMBO_ABILITY) return false;
        return a.id.equals(b.id) && a.level == b.level;
    }

    // Zpracuje výsledek minihry pro konkrétní vybranou dvojici. Suroviny se spotřebují
    // POUZE při úspěchu - neúspěch stojí jen pokus, ne vzácné předměty.
    public void processCombineResult(boolean success, Player player, Item a, Item b) {
        if (!success) return;
        if (!items.contains(a) || !items.contains(b)) return; // bezpečnostní pojistka

        if (a.id.equals("shard") && b.id.equals("shard")) {
            items.remove(a); items.remove(b);
            player.upgradeDamage();
            applyBonusesToPlayer(player);
            return;
        }

        if (isCrystal(a) && isCrystal(b)) {
            if (hasItem("fire_crystal") && hasItem("wind_crystal") && hasItem("ice_crystal")) {
                // Hráč má i třetí krystal navíc - rovnou ultimátní kombo
                removeItem("fire_crystal"); removeItem("wind_crystal"); removeItem("ice_crystal");
                items.add(Item.createComboMeteor());
                if (equippedComboId == 0) equippedComboId = 7;
            } else {
                boolean hasFire = a.id.equals("fire_crystal") || b.id.equals("fire_crystal");
                boolean hasWind = a.id.equals("wind_crystal") || b.id.equals("wind_crystal");
                boolean hasIce = a.id.equals("ice_crystal") || b.id.equals("ice_crystal");
                items.remove(a); items.remove(b);
                if (hasFire && hasWind) {
                    items.add(Item.createComboFireWind());
                    if (equippedComboId == 0) equippedComboId = 4;
                } else if (hasFire && hasIce) {
                    items.add(Item.createComboIceFire());
                    if (equippedComboId == 0) equippedComboId = 5;
                } else if (hasWind && hasIce) {
                    items.add(Item.createComboWindIce());
                    if (equippedComboId == 0) equippedComboId = 6;
                }
            }
            applyBonusesToPlayer(player);
            return;
        }

        if (a.id.equals(b.id) && a.level == b.level) {
            items.remove(a); items.remove(b);
            items.add(a.createUpgradedVersion());
            applyBonusesToPlayer(player);
        }
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

        for (Item item : items) {
            player.bonusDamage += item.bonusDamage;
            player.bonusSpeed += item.bonusSpeed;
            totalMaxHp += item.bonusMaxHp;

            // Trvalé odemknutí zbraně 2/3 - jakmile má hráč krystal jednou u sebe,
            // nezáleží na tom, jestli ho pak spotřebuje na crafting komba.
            if (item.type == Item.Type.WEAPON_UNLOCK) {
                if (item.unlocksWeaponId == 2) player.weapon2Unlocked = true;
                if (item.unlocksWeaponId == 3) player.weapon3Unlocked = true;
            }
        }

        // Jakmile hráč jednou vytvoří libovolné kombo, zbraň 4 zůstává odemčená natrvalo
        if (equippedComboId != 0) player.weapon4Unlocked = true;

        if (totalMaxHp > player.maxHp) {
            int difference = totalMaxHp - player.maxHp;
            player.maxHp = totalMaxHp;
            player.hp += difference;
        }
    }

    // Vrátí index slotu na dané pozici (i prázdného), nebo -1 mimo mřížku.
    public int getSlotAt(int mouseX, int mouseY, int screenW, int screenH) {
        int slotSize = 60, padding = 15;
        int startX = screenW / 2 - (slotSize * 5 + padding * 4) / 2;
        int startY = 180;

        for (int i = 0; i < 20; i++) {
            int col = i % 5, row = i / 5;
            int x = startX + col * (slotSize + padding);
            int y = startY + row * (slotSize + padding);
            if (new Rectangle(x, y, slotSize, slotSize).contains(mouseX, mouseY)) return i;
        }
        return -1;
    }

    public void draw(Graphics2D g2, int screenW, int screenH, int mouseX, int mouseY) {
        g2.setColor(new Color(0, 0, 0, 220)); g2.fillRect(0, 0, screenW, screenH);

        g2.setColor(Color.WHITE); g2.setFont(new Font("Arial", Font.BOLD, 40));
        g2.drawString("INVENTÁŘ", screenW / 2 - 100, 60);

        g2.setFont(new Font("Arial", Font.PLAIN, 16));
        g2.setColor(Color.LIGHT_GRAY);
        String hint = "Klikni na 2 předměty pro jejich kombinaci  |  [E] Přepnout kombo  |  [TAB] Zpět do hry";
        g2.drawString(hint, (screenW - g2.getFontMetrics().stringWidth(hint)) / 2, 95);

        Item selectedItem = (selectedSlot >= 0 && selectedSlot < items.size()) ? items.get(selectedSlot) : null;
        if (selectedItem != null) {
            g2.setColor(Color.CYAN);
            g2.setFont(new Font("Arial", Font.BOLD, 16));
            String selText = "Vybráno: " + selectedItem.name + " - klikni na zeleně zvýrazněný předmět";
            g2.drawString(selText, (screenW - g2.getFontMetrics().stringWidth(selText)) / 2, 118);
        }

        // UI pro výběr Komba
        if (equippedComboId != 0) {
            g2.setColor(Color.ORANGE);
            g2.setFont(new Font("Arial", Font.PLAIN, 16));
            String comboName = (equippedComboId == 4) ? "Ohnivá Aura"
                    : (equippedComboId == 5) ? "SuperNova (8-směr)"
                    : (equippedComboId == 6) ? "Vánice (Rychlopalba)"
                    : "Meteor (Plošný zásah)";
            String comboText = ">>> [E] Vybavené Kombo (Zbraň 4): " + comboName + " <<<";
            g2.drawString(comboText, (screenW - g2.getFontMetrics().stringWidth(comboText)) / 2, 145);
        }

        int slotSize = 60, padding = 15;
        int startX = screenW / 2 - (slotSize * 5 + padding * 4) / 2;
        int startY = 180;
        int row = 0, col = 0;

        for (int i = 0; i < 20; i++) {
            int x = startX + col * (slotSize + padding);
            int y = startY + row * (slotSize + padding);
            boolean hovered = new Rectangle(x, y, slotSize, slotSize).contains(mouseX, mouseY);
            boolean hasItemHere = i < items.size();
            Item item = hasItemHere ? items.get(i) : null;

            g2.setColor(new Color(50, 50, 50, 150));
            g2.fillRoundRect(x, y, slotSize, slotSize, 10, 10);

            // Barva rámečku podle stavu - jasně ukazuje, co se dá s vybraným předmětem zkombinovat
            Color borderColor;
            int borderThickness = 1;
            if (i == selectedSlot) {
                borderColor = Color.CYAN;
                borderThickness = 3;
            } else if (selectedItem != null && item != null && canCombine(selectedItem, item)) {
                borderColor = Color.GREEN;
                borderThickness = 3;
            } else if (hovered) {
                borderColor = Color.YELLOW;
            } else {
                borderColor = Color.GRAY;
            }

            g2.setColor(borderColor);
            if (borderThickness > 1) g2.setStroke(new BasicStroke(borderThickness));
            g2.drawRoundRect(x, y, slotSize, slotSize, 10, 10);
            if (borderThickness > 1) g2.setStroke(new BasicStroke(1));

            if (item != null) {
                g2.setColor(item.color); g2.fillRoundRect(x + 10, y + 10, 40, 40, 5, 5);
                g2.setColor(Color.WHITE); g2.setFont(new Font("Arial", Font.BOLD, 12));

                if (item.type == Item.Type.CRAFTING_MAT) g2.drawString("Mat", x + 15, y + 35);
                else if (item.type == Item.Type.COMBO_ABILITY) g2.drawString("Komb", x + 15, y + 35);
                else g2.drawString("Lvl " + item.level, x + 15, y + 35);
            }
            col++; if (col >= 5) { col = 0; row++; }
        }
    }
}
