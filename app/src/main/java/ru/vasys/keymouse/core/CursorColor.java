package ru.vasys.keymouse.core;

public enum CursorColor {
    WHITE("WHITE", "White", 0xffffffff),
    BLACK("BLACK", "Black", 0xff111111),
    RED("RED", "Red", 0xfff44336),
    ORANGE("ORANGE", "Orange", 0xffff9800),
    YELLOW("YELLOW", "Yellow", 0xffffeb3b),
    GREEN("GREEN", "Green", 0xff4caf50),
    CYAN("CYAN", "Cyan", 0xff00bcd4),
    BLUE("BLUE", "Blue", 0xff2196f3),
    MAGENTA("MAGENTA", "Magenta", 0xffe040fb);

    public final String id;
    public final String label;
    public final int argb;

    CursorColor(String id, String label, int argb) {
        this.id = id;
        this.label = label;
        this.argb = argb;
    }

    public static CursorColor fromId(String id) {
        for (CursorColor color : values()) if (color.id.equals(id)) return color;
        return WHITE;
    }
}
