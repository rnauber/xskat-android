package de.xskat.enums;

public enum StatResolution {
    MINIMAL(0), TEN_STEPS(1), MAXIMAL(2);
    private final int _value;

    StatResolution(int value) {
        this._value = value;
    }

    public static StatResolution of(int value) {
        for (StatResolution sr : values()) {
            if (value == sr._value) {
                return sr;
            }
        }
        throw new IllegalArgumentException("No StatResolution found for value " + value);
    }

    public int value() {
        return _value;
    }

    public StatResolution getMore() {
        if (this == MINIMAL) {
            return TEN_STEPS;
        }
        if (this == TEN_STEPS) {
            return MAXIMAL;
        }
        return this;
    }

    public StatResolution getLess() {
        if (this == MAXIMAL) {
            return TEN_STEPS;
        }
        if (this == TEN_STEPS) {
            return MINIMAL;
        }
        return this;
    }
}
