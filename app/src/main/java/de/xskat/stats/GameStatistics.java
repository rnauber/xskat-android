package de.xskat.stats;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import java.text.MessageFormat;
import java.util.Arrays;

import de.xskat.Translations;

public class GameStatistics {

    private static final int MAX_WIDTH = 153;

    static int[] computeHeaderSum(int[][][][] gameStatistics, int currentColor, int currentPlayer, int currentGameType) {
        int[] sums = {0, 0, 0};
        int[] arrayToIterateOverForGameType;
        if (currentGameType == -1) {
            arrayToIterateOverForGameType = new int[]{0, 1, 2, 3};
        } else {
            arrayToIterateOverForGameType = new int[]{currentGameType};
        }
        int[] arrayToIterateOverForPlayer;
        if (currentPlayer == -1) {
            arrayToIterateOverForPlayer = new int[]{0, 1, 2};
        } else {
            arrayToIterateOverForPlayer = new int[]{currentPlayer};
        }
        int[] arrayToIterateOverForColor;
        if (currentColor == -1) {
            arrayToIterateOverForColor = new int[]{0, 1, 2, 3, 4, 5, 6};
        } else {
            arrayToIterateOverForColor = new int[]{currentColor};
        }
        for (int color : arrayToIterateOverForColor) {
            for (int player : arrayToIterateOverForPlayer) {
                for (int game : arrayToIterateOverForGameType) {
                    for (int wonLost : new int[]{0, 1}) {
                        // Farbe/Colour | Normal/Hand/Ouvert/OuvertHand | Player/Androido/Androida | Won/Lost
                        sums[wonLost + 1] += gameStatistics[color][game][player][wonLost];
                    }
                }
            }
        }
        sums[0] = sums[1] + sums[2];
        return sums;
    }

    public static void createTable(Activity parentActivity, TableLayout tableBody, int[][][][] gameStatistic, int currentPlayer, int currentLang) {
        tableBody.removeAllViews();

        TableRow tableRowHeaderTop = new TableRow(parentActivity);

        TextView textViewGame = new TextView(parentActivity);
        textViewGame.setText(Translations.getTranslation(Translations.XT_Spiel, currentLang));
        textViewGame.setGravity(Gravity.CENTER);
        tableRowHeaderTop.addView(textViewGame);

        for (String header : Arrays.asList(Translations.getTranslation(Translations.XT_Total, currentLang),
                Translations.getTranslation(Translations.XT_Normal, currentLang),
                Translations.getTranslation(Translations.XT_Hand, currentLang),
                Translations.getTranslation(Translations.XT_Ouvert, currentLang),
                Translations.getTranslation(Translations.XT_Ouvert_Hand, currentLang))) {

            TextView textView = new TextView(parentActivity);
            String headerText = header;// + " " + Translations.getTranslation(Translations.XT_WonLost, currLang);
            textView.setText(headerText);
            textView.setWidth(MAX_WIDTH);
            textView.setGravity(Gravity.CENTER);
            tableRowHeaderTop.addView(textView);
        }
        tableBody.addView(tableRowHeaderTop);

        TableRow tableRowHeaderBottom = new TableRow(parentActivity);

        textViewGame = new TextView(parentActivity);
        textViewGame.setText("");
        textViewGame.setGravity(Gravity.CENTER);
        tableRowHeaderBottom.addView(textViewGame);

        String template = "{0} ({1}/{2})";
        for (int[] value : Arrays.asList(
                computeHeaderSum(gameStatistic, -1, currentPlayer, -1),
                computeHeaderSum(gameStatistic, -1, currentPlayer, 0),
                computeHeaderSum(gameStatistic, -1, currentPlayer, 1),
                computeHeaderSum(gameStatistic, -1, currentPlayer, 2),
                computeHeaderSum(gameStatistic, -1, currentPlayer, 3)
        )) {
            TextView textView = new TextView(parentActivity);
            textView.setText(MessageFormat.format(template, value[0], value[1], value[2]));
            textView.setGravity(Gravity.CENTER);
            tableRowHeaderBottom.addView(textView);
        }
        tableBody.addView(tableRowHeaderBottom);

        for (int currentColor : new int[]{0, 1, 2, 3, 4, 5, 6}) {
            TableRow tableRow = new TableRow(parentActivity);
            TextView first = new TextView(parentActivity);
            first.setText(Translations.getTranslation(currentColor, currentLang));
            first.setGravity(Gravity.RIGHT);
            tableRow.addView(first);

            for (int currentGameType : new int[]{-1, 0, 1, 2, 3}) {
                TextView element = new TextView(parentActivity);
                if (currentColor == 6 && currentGameType != 0) {
                    element.setText("");
                }else {
                    int[] result = computeHeaderSum(gameStatistic, currentColor, currentPlayer, currentGameType);
                    element.setText(MessageFormat.format(template, result[0], result[1], result[2]));
                    element.setGravity(Gravity.CENTER);
                    if (result[0] > 0) {
                        element.setTypeface(null, Typeface.BOLD);
                        element.setBackgroundColor(getBackgroundColor(result));
                    }
                }
                tableRow.addView(element);
            }

            tableBody.addView(tableRow);
        }
    }

    private static int getBackgroundColor(int[] result) {
        if (result[1] == result[2]) {
            return 0xDDDDDDDD;
        }
        if (result[1] > result[2]) {
            return 0x00AFFFAF | computeAlpha(result[1], result[2]);
        }
        return 0x00FFAFAF | computeAlpha(result[2], result[1]);
    }

    static int computeAlpha(int greater, int smaller) {
        int sum = smaller + greater;
        int opacity = 255 - (int) (255.0 * smaller / sum / 4) * 4;
        //int opacity = (int) (25.5  / greater * smaller) * 10;
        return opacity << 24;
    }
}
