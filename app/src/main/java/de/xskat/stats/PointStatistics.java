package de.xskat.stats;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.xskat.Translations;
import de.xskat.data.Pair;
import de.xskat.enums.StatResolution;

public class PointStatistics {

    private static final int MAX_WIDTH = 170;

    public static void createTableHeader(Activity parentActivity, TableLayout tableHeader, int[][] pointsStatistic, int currentLanguage) {
        tableHeader.removeAllViews();
        TableRow tableRowHeader = new TableRow(parentActivity);
        WindowManager.LayoutParams tableRowParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        tableRowHeader.setLayoutParams(tableRowParams);

        for (String header : Arrays.asList(Translations.getTranslation(Translations.XT_Augen, currentLanguage),
                "",
                Translations.getTranslation(Translations.XT_Spieler, currentLanguage),
                Translations.getTranslation(Translations.XT_Androido, currentLanguage),
                Translations.getTranslation(Translations.XT_Androida, currentLanguage))) {
            TextView headerTextView = new TextView(parentActivity);
            headerTextView.setText(header);
            headerTextView.setGravity(Gravity.CENTER);
            if (header.length() != 0) {
                headerTextView.setWidth(MAX_WIDTH);
            } else {
                // second column is smaller
                headerTextView.setWidth(MAX_WIDTH / 2);
            }
            tableRowHeader.addView(headerTextView);
        }
        tableHeader.addView(tableRowHeader);

        TableRow tableRowSumHeader = new TableRow(parentActivity);
        WindowManager.LayoutParams tableRowSumParams = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        tableRowSumHeader.setLayoutParams(tableRowSumParams);

        TextView empty = new TextView(parentActivity);
        empty.setText(Translations.getTranslation(Translations.XT_Sum, currentLanguage));
        empty.setGravity(Gravity.CENTER);
        empty.setWidth(MAX_WIDTH);
        tableRowSumHeader.addView(empty);
        int[] sum = new int[]{sum(pointsStatistic[0]), sum(pointsStatistic[1]), sum(pointsStatistic[2])};
        TextView allSum = new TextView(parentActivity);
        allSum.setText(String.valueOf(sum(sum)));
        allSum.setGravity(Gravity.CENTER);
        tableRowSumHeader.addView(allSum);
        for (int value : sum) {
            TextView sumView = new TextView(parentActivity);
            sumView.setText(String.valueOf(value));
            sumView.setGravity(Gravity.CENTER);
            sumView.setWidth(MAX_WIDTH);
            if (value > 0) {
                sumView.setTypeface(null, Typeface.BOLD);
            }
            tableRowSumHeader.addView(sumView);
        }
        tableHeader.addView(tableRowSumHeader);
    }

    public static void createTable(Activity parentActivity, TableLayout tableBody, List<Pair<String, int[]>> compute) {
        tableBody.removeAllViews();
        if (!compute.isEmpty()) {
            for (int i = 0; i < compute.size(); ++i) {
                Pair<String, int[]> line = compute.get(i);
                TableRow tableRow = new TableRow(parentActivity);
                TextView textView = new TextView(parentActivity);
                textView.setText(line.getLeft());
                textView.setGravity(Gravity.CENTER);
                textView.setWidth(MAX_WIDTH);
                tableRow.addView(textView);

                int[] numbers = line.getRight();
                for (int j = 0; j < numbers.length; j++) {
                    int number = numbers[j];
                    TextView textView2 = new TextView(parentActivity);
                    textView2.setText(String.valueOf(number));
                    textView2.setGravity(Gravity.CENTER);
                    if (j > 0) {
                        textView2.setWidth(MAX_WIDTH);
                    } else {
                        // second column is smaller
                        textView2.setWidth(MAX_WIDTH / 2);
                    }
                    if (number != 0) {
                        textView2.setTypeface(null, Typeface.BOLD);
                    }
                    tableRow.addView(textView2);
                }
                tableBody.addView(tableRow);
            }
        }
    }

    public static List<Pair<String, int[]>> compute(StatResolution statResolution, int[][] input) {
        if (statResolution == StatResolution.MAXIMAL) {
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
        } else if (statResolution == StatResolution.TEN_STEPS) {
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
        } else if (statResolution == StatResolution.MINIMAL) {
            List<Pair<String, int[]>> modi = new ArrayList<Pair<String, int[]>>();
            modi.add(Pair.of("0-30", new int[]{0, 30}));
            modi.add(Pair.of("31-60", new int[]{31, 60}));
            modi.add(Pair.of("61-89", new int[]{61, 89}));
            modi.add(Pair.of("90-120", new int[]{90, 120}));
            return process(modi, input);
        }
        return new ArrayList<Pair<String, int[]>>();
    }

    static List<Pair<String, int[]>> process(List<Pair<String, int[]>> modi, int[][] input) {
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

    static void add(int[] a, int[] b) {
        for (int i = 0; i < a.length; ++i) {
            a[i] += b[i];
        }
    }

    static int sum(int[][] input, int index) {
        int result = 0;
        for (int[] i : input) {
            result += i[index];
        }
        return result;
    }

    static int sum(int[] array) {
        int result = 0;
        for (int k : array) {
            result += k;
        }
        return result;
    }
}
