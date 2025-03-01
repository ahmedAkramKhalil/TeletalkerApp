package com.teletalker.app.data;

import java.util.ArrayList;

public class Instrument{
    public String id;
    public Title title;
    public Discreption discreption;
    public String image;
    public String unSelectedImage;
    public int strings;
    public ArrayList<Dozan> dozan;
    private boolean cardClicked = false;


    public class Discreption{
        public String ar;
        public String en;
    }

    public class Settings{
        public int hz;
        public String noteType;
    }


    public boolean isCardClicked() {
        return cardClicked;
    }

    public void setCardClicked(boolean cardClicked) {
        this.cardClicked = cardClicked;
    }
}
