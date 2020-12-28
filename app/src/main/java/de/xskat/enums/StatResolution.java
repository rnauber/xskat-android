package de.xskat.enums;

import java.util.ArrayList;
import java.util.List;

import de.xskat.data.Pair;

public enum StatResolution {
    MINIMAL(0), TEN_STEPS(1), TWO_STEPS(2), MAXIMAL(3);
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

    public List<Pair<String, int[]>> compute(int[][] input) {
        if (this == MAXIMAL) {
            List<Pair<String, int[]>> result = new ArrayList<Pair<String, int[]>>(121);

            boolean toContinue = true;
            for (int i = 0; i < 121; i++) {
                if (i == 1 || i == 119) {
                    // it's not possible to get 1 or 119 points
                    continue;
                }
                int sum = sum(input, i);
                if (sum == 0 && toContinue) {
                    continue;
                }
                toContinue = false;
                int[] line = new int[]{sum, input[0][i], input[1][i], input[2][i]};
                result.add(Pair.of(String.valueOf(i), line));
            }
            return result;
        } else if (this == TEN_STEPS) {
            List<Pair<String, int[]>> modi = new ArrayList<Pair<String, int[]>>();
            modi.add(Pair.of("0-10", new int[]{0, 10}));
            modi.add(Pair.of("11-20", new int[]{11, 20}));
            modi.add(Pair.of("21-30", new int[]{21, 30}));
            modi.add(Pair.of("31-40", new int[]{31, 40}));
            modi.add(Pair.of("41-50", new int[]{41, 50}));
            modi.add(Pair.of("51-60", new int[]{51, 60}));
            modi.add(Pair.of("61-70", new int[]{61, 70}));
            modi.add(Pair.of("71-80", new int[]{71, 80}));
            modi.add(Pair.of("81-90", new int[]{81, 90}));
            modi.add(Pair.of("91-100", new int[]{91, 100}));
            modi.add(Pair.of("101-110", new int[]{101, 110}));
            modi.add(Pair.of("111-120", new int[]{111, 120}));
            return process(modi,input);
        } else if (this == MINIMAL) {
            List<Pair<String, int[]>> modi = new ArrayList<Pair<String, int[]>>();
            modi.add(Pair.of("0-30", new int[]{0, 30}));
            modi.add(Pair.of("31-60", new int[]{31, 60}));
            modi.add(Pair.of("61-89", new int[]{61, 89}));
            modi.add(Pair.of("90-120", new int[]{90, 120}));

            return process(modi, input);
        }
        return new ArrayList<Pair<String, int[]>>();
    }

    List<Pair<String, int[]>> process(List<Pair<String, int[]>> modi, int[][] input) {
        List<Pair<String, int[]>> result = new ArrayList<Pair<String, int[]>>();
        for (Pair<String, int[]> modus : modi) {
            int[] summedLine = new int[4];
            for (int i = modus.getRight()[0]; i <= modus.getRight()[1]; ++i) {
                int[] line = new int[]{sum(input, i), input[0][i], input[1][i], input[2][i]};
                add(summedLine, line);
            }
            result.add(Pair.of(modus.getLeft(), summedLine));
        }
        return result;
    }

    void add(int[] a, int[] b) {
        for (int i = 0; i < a.length; ++i) {
            a[i] += b[i];
        }
    }

    int sum(int[][] input, int index) {
        int k = 0;
        for (int[] i : input) {
            k += i[index];
        }
        return k;
    }

    public StatResolution getMore() {
        if (this == MINIMAL) {
            return TEN_STEPS;
        }
        if (this == TEN_STEPS) {
            /*return TWO_STEPS;
        }
        if (this == TWO_STEPS) {*/
            return MAXIMAL;
        }
        return this;
    }

    public StatResolution getLess() {
        if (this == MAXIMAL) {
            /*return TWO_STEPS;
        }
        if (this == TWO_STEPS) {*/
            return TEN_STEPS;
        }
        if (this == TEN_STEPS) {
            return MINIMAL;
        }
        return this;
    }
}
