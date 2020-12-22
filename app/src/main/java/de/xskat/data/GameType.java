package de.xskat.data;

public enum GameType {
    NULL(-1), DIAMONDS(0), HEARTS(1), SPADES(2), CLUBS(3), GRAND(4), RAMSCH(5);
    private final int _value;

    GameType(int value) {
        _value = value;
    }

    public static boolean isRamsch(int value) {
        if (value == RAMSCH._value) {
            return true;
        }
        isValid(value);
        return false;
    }

    public static boolean isNullGame(int value) {
        if (value == NULL._value) {
            return true;
        }
        isValid(value);
        return false;
    }

    private static void isValid(int value) {
        if (value < -1 || value > 5) {
            throw new IllegalArgumentException("Value " + value + " is not recognized as trump.");
        }
    }
}
