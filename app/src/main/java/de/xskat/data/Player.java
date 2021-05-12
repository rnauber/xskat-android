package de.xskat.data;

import de.xskat.Translations;

public enum Player {
    PLAYER_1(Translations.XT_Spieler), PLAYER_2(Translations.XT_Androido), PLAYER_3(Translations.XT_Androida);

    private final int translation;

    Player(int translation) {
        this.translation = translation;
    }

    public int getTranslation() {
        return translation;
    }

    public static Player nextPlayerForStatistic(Player in) {
        if (in == null) return PLAYER_1;
        if (in == PLAYER_1) return PLAYER_2;
        if (in == PLAYER_2) return PLAYER_3;
        if (in == PLAYER_3) return null;
        throw new IllegalArgumentException("Cannot get next player for " + in);
    }

    public static Player prevPlayerForStatistic(Player in) {
        if (in == null) return PLAYER_3;
        if (in == PLAYER_3) return PLAYER_2;
        if (in == PLAYER_2) return PLAYER_1;
        if (in == PLAYER_1) return null;
        throw new IllegalArgumentException("Cannot get previous player for " + in);
    }
}
