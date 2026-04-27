package org.example.logic.items;

import java.awt.*;

public class Item {
    public enum Type { STAT_BOOST, WEAPON_UNLOCK, CRAFTING_MAT, COMBO_ABILITY }

    public String id;
    public String name;
    public Type type;
    public int level;

    public int bonusMaxHp = 0;
    public int bonusDamage = 0;
    public double bonusSpeed = 0.0;
    public int unlocksWeaponId = 0;

    public Color color;

    public Item(String id, String name, Type type, int level, Color color) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.level = level;
        this.color = color;
    }

    public Item createUpgradedVersion() {
        Item upgraded = new Item(this.id, this.name, this.type, this.level + 1, this.color);
        upgraded.bonusMaxHp = this.bonusMaxHp * 2;
        upgraded.bonusDamage = this.bonusDamage * 2;
        upgraded.bonusSpeed = this.bonusSpeed * 1.5;
        upgraded.unlocksWeaponId = this.unlocksWeaponId;
        return upgraded;
    }

    // --- STARÉ ITEMY ---
    public static Item createDamageSword() {
        Item item = new Item("sword", "Meč Síly", Type.STAT_BOOST, 1, Color.RED);
        item.bonusDamage = 10;
        return item;
    }

    public static Item createHealthHeart() {
        Item item = new Item("heart", "Srdce Života", Type.STAT_BOOST, 1, Color.PINK);
        item.bonusMaxHp = 20;
        return item;
    }

    public static Item createSpeedBoots() {
        Item item = new Item("boots", "Boty Větru", Type.STAT_BOOST, 1, Color.GREEN);
        item.bonusSpeed = 0.5;
        return item;
    }

    // --- NOVÉ ITEMY A SUROVINY ---
    public static Item createBerserkerPotion() {
        Item item = new Item("potion", "Lektvar Šílenství", Type.STAT_BOOST, 1, Color.MAGENTA);
        item.bonusDamage = 15;
        item.bonusSpeed = 0.8;
        item.bonusMaxHp = -10; // Něco za něco!
        return item;
    }

    public static Item createWeaponShard() {
        return new Item("shard", "Úlomek Zbraně", Type.CRAFTING_MAT, 1, Color.ORANGE);
    }

    // --- ELEMENTÁRNÍ KRYSTALY ---
    public static Item createIceCrystal() {
        Item item = new Item("ice_crystal", "Ledový Krystal", Type.WEAPON_UNLOCK, 1, new Color(0, 255, 255));
        item.unlocksWeaponId = 2; // Odemkne zbraň č. 2 (Led)
        return item;
    }

    public static Item createWindCrystal() {
        Item item = new Item("wind_crystal", "Větrný Krystal", Type.WEAPON_UNLOCK, 1, Color.LIGHT_GRAY);
        item.unlocksWeaponId = 3; // Odemkne zbraň č. 3 (Vítr)
        return item;
    }

    public static Item createFireCrystal() {
        return new Item("fire_crystal", "Ohnivý Krystal", Type.CRAFTING_MAT, 1, new Color(255, 50, 0));
    }

    // --- KOMBINACE PRO ZBRAŇ 4 ---
    public static Item createComboFireWind() {
        Item item = new Item("combo_fire_wind", "Ohnivá Aura (Oheň+Vítr)", Type.COMBO_ABILITY, 1, new Color(255, 100, 0));
        item.unlocksWeaponId = 4; // ID comba 4
        return item;
    }

    public static Item createComboIceFire() {
        Item item = new Item("combo_ice_fire", "SuperNova (Oheň+Led)", Type.COMBO_ABILITY, 1, new Color(150, 0, 255));
        item.unlocksWeaponId = 5; // ID comba 5
        return item;
    }

    public static Item createComboWindIce() {
        Item item = new Item("combo_wind_ice", "Vánice (Vítr+Led)", Type.COMBO_ABILITY, 1, new Color(100, 200, 255));
        item.unlocksWeaponId = 6; // ID comba 6
        return item;
    }
}