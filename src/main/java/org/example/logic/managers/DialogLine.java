package org.example.logic.managers;

public class DialogLine {
    public enum Type { TEXT, SHAKE, CMD, BGM, SFX }

    public Type type;
    public String name;
    public String text;
    public String portraitPath;
    public String value;
    public String audioPath;

    // Konstruktor pro zpětnou kompatibilitu se starým CutsceneManagerem
    public DialogLine(String name, String text, Object imageObj, String audioPath) {
        this.type = Type.TEXT;
        this.name = name;
        this.text = text;
        this.audioPath = audioPath;
        this.portraitPath = ""; // V souborové verzi sem dáváme cestu
    }

    // Konstruktor pro souborový TEXT
    public DialogLine(Type type, String name, String text, String portraitPath, String audioPath) {
        this.type = type;
        this.name = name;
        this.text = text;
        this.portraitPath = portraitPath;
        this.audioPath = audioPath;
    }

    // Konstruktor pro CMD, BGM, SFX, SHAKE
    public DialogLine(Type type, String value) {
        this.type = type;
        this.value = value;
    }
}