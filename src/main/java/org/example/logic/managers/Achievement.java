package org.example.logic.managers;

public class Achievement {
    public final String id;
    public final String title;
    public final String description;
    public final boolean secret;

    public Achievement(String id, String title, String description, boolean secret) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.secret = secret;
    }
}
