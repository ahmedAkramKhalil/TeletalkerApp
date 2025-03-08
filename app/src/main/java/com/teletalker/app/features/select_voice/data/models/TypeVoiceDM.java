package com.teletalker.app.features.select_voice.data.models;

public class TypeVoiceDM {
    private String name;
    private String lang;

    private int image;

    public TypeVoiceDM(String name, String lang, int image) {
        this.name = name;
        this.lang = lang;
        this.image = image;
    }

    public String getName() {
        return name;
    }

    public String getLang() {
        return lang;
    }

    public int getImage() {
        return image;
    }
}
