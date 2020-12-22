//
// XSkat, Copyright (C) 2014  Gunter Gerhardt
//
// This program is free software; you can redistribute it freely.
// Use it at your own risk; there is NO WARRANTY.
//
// Redistribution of modified versions is permitted
// provided that the following conditions are met:
// 1. All copyright & permission notices are preserved.
// 2.a) Only changes required for packaging or porting are made.
//   or
// 2.b) It is clearly stated who last changed the program.
//      The program is renamed or
//      the version number is of the form x.y.z,
//      where x.y is the version of the original program
//      and z is an arbitrary suffix.
//

package de.xskat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

import java.util.Date;
import java.util.Locale;

import de.xskat.data.GameType;

public class XSkat extends Activity {

    // Don't try this at home, kids!
    // At first glance, this may look like a Java class
    // but it mostly is a copy & paste job of the original C code.

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.xskat);
        activityContext = this;
        layoutParams = new WindowManager.LayoutParams();
        layedOut = false;
        runHandler = new Handler();
        prefs = getPreferences(MODE_PRIVATE);
        editor = prefs.edit();
        setGone(R.id.dialogProto);
        setGone(R.id.dialogListe);
        setGone(R.id.dialogLoeschen);
        setDialogsGone();
        readPrefs();
        renderCards();
        main();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        inOnPause = false;
    }

    @Override
    public void onPause() {
        super.onPause();
        inOnPause = true;
        if (acceptAnim != null) {
            runHandler.removeCallbacks(acceptAnim);
            acceptAnim.run();
        }
        if (cardAnim != null) {
            runHandler.removeCallbacks(cardAnim);
            cardAnim.run();
        }
        if (takeTrick != null) {
            runHandler.removeCallbacks(takeTrick);
            takeTrick.run();
        }
        save_list();
        editor.putString("xskatVersion", xskatVersion);
        editor.putBoolean("restart", !finishing);
        editor.commit();
    }

    private int getButtonSpeedId(int animSpeed) {
        if (animSpeed == 0) {
            return R.id.buttonAnimS;
        }
        return animSpeed == 1 ? R.id.buttonAnim0 : R.id.buttonAnimL;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menuAbout:
                showDialogFromMenu(R.id.dialogCopyright);
                return true;
            case R.id.menuOptions:
                setSelected(R.id.buttonStaerkePP + strateg[0]);
                setSelected(getButtonSpeedId(animSpeed));
                setSelected(R.id.buttonSpracheDE + currLang);
                if (prot1.stiche[0][0] != 0 || prot1.stiche[0][1] != 0) {
                    setText(R.id.buttonOptionsListe, getTranslation(Translations.XT_Protokoll));
                } else {
                    setText(R.id.buttonOptionsListe, getTranslation(Translations.XT_Liste));
                }
                setGone(R.id.mainScreen);
                setGone(R.id.dialogProto);
                setGone(R.id.dialogListe);
                setGone(R.id.dialogLoeschen);
                setDialogsGone();
                setVisible(R.id.dialogOptions);
                setVisible(R.id.dialogScreen);
                return true;
            case R.id.menuLastTrick:
                phase = LETZTERSTICH;
                drawcard(0, prot2.stiche[stich - 2][0], 3, -1);
                drawcard(0, prot2.stiche[stich - 2][1], 2, -1);
                drawcard(0, prot2.stiche[stich - 2][2], 4, -1);
                return true;
            case R.id.menuSort:
                sort2[0] ^= 1;
                initscr(0, 1);
                return true;
            case R.id.menuList:
                di_liste(0, true);
                showDialogFromMenu(R.id.dialogListe);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (phase == LETZTERSTICH) {
            restoreTrick();
        }
        createMenu(menu, R.id.menuAbout, Translations.XT_Ueber_XSkat, true);
        createMenu(menu, R.id.menuOptions, Translations.XT_Optionen, true);
        createMenu(menu, R.id.menuLastTrick, Translations.XT_Letzter_Stich, phase == SPIELEN && stich > 1);
        createMenu(menu, R.id.menuSort, sort2[0] == 0 ? Translations.XT_Sortiere_fuer_Null : Translations.XT_Sortiere_normal, phase < SPIELEN && trumpf != 5);
        createMenu(menu, R.id.menuList, Translations.XT_Liste, true);
        return super.onPrepareOptionsMenu(menu);
    }

    private void createMenu(Menu menu, int itemId, int translationKey, boolean enabled) {
        MenuItem mi = menu.findItem(itemId);
        mi.setTitle(getTranslation(translationKey));
        mi.setEnabled(enabled);
    }

    public void clickedCard(View view) {
        int id = view.getId();

        if (discardInput)
            return;
        if (R.id.card0 <= id && id <= R.id.card9) {
            if (phase == DRUECKEN) {
                if (findViewById(R.id.dialogDesk).getVisibility() == View.VISIBLE)
                    hndl_druecken(id - R.id.card0 + 1);
                return;
            } else if (phase == SPIELEN) {
                if ((ausspl + vmh) % 3 == 0) {
                    hndl_spielen(id - R.id.card0);
                    return;
                }
            }
        }
        clickedSpace(view);
    }

    public void clicked18(View view) {
        int sn = 0;

        if (discardInput)
            return;
        if (phase == REIZEN) {
            if ((saho && sn == sager) || (!saho && sn == hoerer)) {
                maxrw[sn] = 999 - 1;
                do_entsch();
            }
        }
        computer();
    }

    public void clickedPasse(View view) {
        int sn = 0;

        if (discardInput)
            return;
        if (phase == REIZEN) {
            if ((saho && sn == sager) || (!saho && sn == hoerer)) {
                maxrw[sn] = 0;
                do_entsch();
            }
        }
        computer();
    }

    public void clickedToggle(View view) {
        if (discardInput)
            return;
        boolean wasNull = isSelected(R.id.buttonNull);
        if (view == findViewById(R.id.buttonKaro)
                || view == findViewById(R.id.buttonHerz)
                || view == findViewById(R.id.buttonPik)
                || view == findViewById(R.id.buttonKreuz)
                || view == findViewById(R.id.buttonNull)
                || view == findViewById(R.id.buttonGrand)) {
            setDeselected(R.id.buttonKaro);
            setDeselected(R.id.buttonHerz);
            setDeselected(R.id.buttonPik);
            setDeselected(R.id.buttonKreuz);
            setDeselected(R.id.buttonNull);
            setDeselected(R.id.buttonGrand);
            setSelected(view);
            if (wasNull != isSelected(R.id.buttonNull)) {
                sort2[0] = wasNull ? 0 : 1;
            }
            if (isSelected(R.id.buttonKaro)) {
                trumpf = 0;
            } else if (isSelected(R.id.buttonHerz)) {
                trumpf = 1;

            } else if (isSelected(R.id.buttonPik)) {
                trumpf = 2;

            } else if (isSelected(R.id.buttonKreuz)) {
                trumpf = 3;

            } else if (isSelected(R.id.buttonGrand)) {
                trumpf = 4;
            }
            initscr(0, 1);
        } else if (view == findViewById(R.id.buttonStaerkeMM)
                || view == findViewById(R.id.buttonStaerkeM)
                || view == findViewById(R.id.buttonStaerke0)
                || view == findViewById(R.id.buttonStaerkeP)
                || view == findViewById(R.id.buttonStaerkePP)) {
            setDeselected(R.id.buttonStaerkeMM);
            setDeselected(R.id.buttonStaerkeM);
            setDeselected(R.id.buttonStaerke0);
            setDeselected(R.id.buttonStaerkeP);
            setDeselected(R.id.buttonStaerkePP);
            setSelected(view);
            int st = 0;
            if (isSelected(R.id.buttonStaerkeMM)) {
                st = -4;
            } else if (isSelected(R.id.buttonStaerkeM)) {
                st = -3;
            } else if (isSelected(R.id.buttonStaerke0)) {
                st = -2;
            } else if (isSelected(R.id.buttonStaerkeP)) {
                st = -1;
            }
            for (int i = 0; i < 3; i++) {
                strateg[i] = st;
                editor.putInt("strateg" + i, strateg[i]);
            }
        } else if (view == findViewById(R.id.buttonAnimL) ||
                view == findViewById(R.id.buttonAnim0) ||
                view == findViewById(R.id.buttonAnimS)) {
            setDeselected(R.id.buttonAnimL);
            setDeselected(R.id.buttonAnim0);
            setDeselected(R.id.buttonAnimS);
            setSelected(view);
            int v;
            if (isSelected(R.id.buttonAnimL)) {
                v = 2;
            } else if (isSelected(R.id.buttonAnimS)) {
                v = 0;
            } else {
                v = 1;
            }
            if (v != animSpeed) {
                editor.putInt("animSpeed", v);
                animSpeed = v;
            }
        } else if (view == findViewById(R.id.buttonSpracheDE)
                || view == findViewById(R.id.buttonSpracheEN)) {
            setDeselected(R.id.buttonSpracheDE);
            setDeselected(R.id.buttonSpracheEN);
            setSelected(view);
            String defLang = Locale.getDefault().getLanguage();
            currLang = isSelected(R.id.buttonSpracheDE) ? 0 : 1;
            editor.putString("defLang", defLang);
            editor.putInt("currLang", currLang);
            initDialogs();
        } else if (view == findViewById(R.id.buttonBlattTU)
                || view == findViewById(R.id.buttonBlattFR)
                || view == findViewById(R.id.buttonBlattDE)) {
            setDeselected(R.id.buttonBlattTU);
            setDeselected(R.id.buttonBlattFR);
            setDeselected(R.id.buttonBlattDE);
            setDeselected(R.id.buttonBlattBDK);
            setDeselected(R.id.buttonBlattJQK);
            setDeselected(R.id.buttonBlattUOK);
            setDeselected(R.id.buttonBlattSortA);
            setDeselected(R.id.buttonBlattSortS);
            setSelected(view);
            if (isSelected(R.id.buttonBlattFR)) {
                spBlatt = 1;
                spBDK = currLang == 1 ? 1 : 0;
                setSelected(currLang == 1 ? R.id.buttonBlattJQK
                        : R.id.buttonBlattBDK);
                alternate[0] = 1;
                setSelected(R.id.buttonBlattSortA);
            } else if (isSelected(R.id.buttonBlattDE)) {
                spBlatt = 2;
                spBDK = 2;
                setSelected(R.id.buttonBlattUOK);
                alternate[0] = 0;
                setSelected(R.id.buttonBlattSortS);
            } else {
                spBlatt = 0;
                spBDK = currLang == 1 ? 1 : 0;
                setSelected(currLang == 1 ? R.id.buttonBlattJQK
                        : R.id.buttonBlattBDK);
                alternate[0] = 0;
                setSelected(R.id.buttonBlattSortS);
            }
            initscr(0, 1);
            renderCards();
            drawAllCards();
            setText(R.id.buttonKaro, gameName(0));
            setText(R.id.buttonHerz, gameName(1));
            setText(R.id.buttonPik, gameName(2));
            setText(R.id.buttonKreuz, gameName(3));
            if (phase == ANSAGEN)
                initAnsageStr();
        } else if (view == findViewById(R.id.buttonBlattBDK)
                || view == findViewById(R.id.buttonBlattJQK)
                || view == findViewById(R.id.buttonBlattUOK)) {
            setDeselected(R.id.buttonBlattBDK);
            setDeselected(R.id.buttonBlattJQK);
            setDeselected(R.id.buttonBlattUOK);
            setSelected(view);
            if (isSelected(R.id.buttonBlattJQK)) {
                spBDK = 1;
            } else if (isSelected(R.id.buttonBlattUOK)) {
                spBDK = 2;
            } else {
                spBDK = 0;
            }
            renderCards();
            drawAllCards();
            if (phase == DRUECKEN)
                initBubenStr();
        } else if (view == findViewById(R.id.buttonBlattSortA)
                || view == findViewById(R.id.buttonBlattSortS)) {
            setDeselected(R.id.buttonBlattSortA);
            setDeselected(R.id.buttonBlattSortS);
            setSelected(view);
            alternate[0] = isSelected(R.id.buttonBlattSortA) ? 1 : 0;
            initscr(0, 1);
        } else if (view == findViewById(R.id.buttonRamschNein)
                || view == findViewById(R.id.buttonRamschJa)
                || view == findViewById(R.id.buttonRamschImmer)) {
            setDeselected(R.id.buttonRamschNein);
            setDeselected(R.id.buttonRamschJa);
            setDeselected(R.id.buttonRamschImmer);
            setSelected(view);
            if (isSelected(R.id.buttonRamschNein)) {
                playramsch = 0;
            } else if (isSelected(R.id.buttonRamschJa)) {
                playramsch = 1;
            } else {
                playramsch = 2;
            }
        } else if (view == findViewById(R.id.buttonVarKontraNein)
                || view == findViewById(R.id.buttonVarKontraJa)
                || view == findViewById(R.id.buttonVarKontraAb18)) {
            setDeselected(R.id.buttonVarKontraNein);
            setDeselected(R.id.buttonVarKontraJa);
            setDeselected(R.id.buttonVarKontraAb18);
            setSelected(view);
            if (isSelected(R.id.buttonVarKontraNein)) {
                playkontra = 0;
            } else if (isSelected(R.id.buttonVarKontraJa)) {
                playkontra = 1;
            } else {
                playkontra = 2;
            }
        } else if (view == findViewById(R.id.buttonSchieberamschNein)
                || view == findViewById(R.id.buttonSchieberamschJa)) {
            setDeselected(R.id.buttonSchieberamschNein);
            setDeselected(R.id.buttonSchieberamschJa);
            setSelected(view);
            if (isSelected(R.id.buttonSchieberamschNein)) {
                playsramsch = 0;
            } else {
                playsramsch = 1;
            }
        } else if (view == findViewById(R.id.buttonLetztenStich)
                || view == findViewById(R.id.buttonVerlierer)) {
            setDeselected(R.id.buttonLetztenStich);
            setDeselected(R.id.buttonVerlierer);
            setSelected(view);
            if (isSelected(R.id.buttonLetztenStich)) {
                rskatloser = 0;
            } else {
                rskatloser = 1;
            }
        } else if (view == findViewById(R.id.buttonVorschlaegeNein)
                || view == findViewById(R.id.buttonVorschlaegeJa)) {
            setDeselected(R.id.buttonVorschlaegeNein);
            setDeselected(R.id.buttonVorschlaegeJa);
            setSelected(view);
            hints[0] = !isSelected(R.id.buttonVorschlaegeNein);
            restore_hints();
        } else if (view == findViewById(R.id.buttonVonAndroido)
                || view == findViewById(R.id.buttonVonMir)
                || view == findViewById(R.id.buttonVonAndroida)) {
            setDeselected(R.id.buttonVonAndroido);
            setDeselected(R.id.buttonVonMir);
            setDeselected(R.id.buttonVonAndroida);
            setSelected(view);
        } else {
            if (view.getTag() == null) {
                setSelected(view);
            } else {
                setDeselected(view);
            }
        }
    }

    public void clickedSpielen(View view) {
        int i, bb, c;
        boolean ag = false;

        if (discardInput)
            return;
        if (isSelected(R.id.buttonNull) || isSelected(R.id.buttonRevolution))
            trumpf = -1;
        else if (isSelected(R.id.buttonKaro))
            trumpf = 0;
        else if (isSelected(R.id.buttonHerz))
            trumpf = 1;
        else if (isSelected(R.id.buttonPik))
            trumpf = 2;
        else if (isSelected(R.id.buttonKreuz))
            trumpf = 3;
        else
            trumpf = 4;
        if (!handsp
                && trumpf != -1
                && (isSelected(R.id.buttonSchneider)
                        || isSelected(R.id.buttonSchwarz) || isSelected(R.id.buttonOuvert))) {
            setGone(R.id.dialogSpielen);
            setVisible(R.id.dialogFehler);
            ag = true;
        }
        if (!ag
                && trumpf == -1
                && reizValues[reizp] > nullw[isSelected(R.id.buttonRevolution) ? 4
                        : (isSelected(R.id.buttonOuvert) ? 2 : 0)
                                + (handsp ? 1 : 0)]) {
            setGone(R.id.dialogSpielen);
            setVisible(R.id.dialogUeberreizt);
            ag = true;
        }
        spitzeang = false;
        if (!ag && trumpf != -1 && isSelected(R.id.buttonSpitze)) {
            bb = 0;
            for (i = 0; i < (handsp ? 10 : 12); i++) {
                c = i >= 10 ? prot2.skat[1][i - 10] : cards[spieler * 10 + i];
                if (i < 10 && c == (trumpf == 4 ? BUBE : SIEBEN | trumpf << 3)) {
                    spitzeang = true;
                }
                if ((c & 7) == BUBE)
                    bb++;
            }
            if (!spitzeang || (bb == 4 && trumpf == 4)) {
                // TODO create_di(sn,dispitze);
                ag = true;
            }
        }
        if (!ag) {
            if (isSelected(R.id.buttonRevolution))
                revolang = ouveang = true;
            else if (isSelected(R.id.buttonOuvert))
                ouveang = schwang = schnang = true;
            else if (isSelected(R.id.buttonSchwarz))
                schwang = schnang = true;
            else if (isSelected(R.id.buttonSchneider))
                schnang = true;
            if (trumpf == -1)
                schwang = schnang = false;
            di_ansage();
            computer();
        }
    }

    public void clickedBack(View view) {
        if (discardInput)
            return;
        cards[30] = prot2.skat[1][0];
        cards[31] = prot2.skat[1][1];
        umdrueck = 1;
        do_druecken();
        setGone(R.id.dialogSpielen);
        setVisible(R.id.dialogDesk);
    }

    public void clickedHandJa(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogHand);
        setVisible(R.id.dialogDesk);
        handsp = true;
        do_handok();
    }

    public void clickedHandNein(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogHand);
        setVisible(R.id.dialogDesk);
        do_handok();
    }

    public void clickedAufnehmenJa(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogSchieben);
        setVisible(R.id.rowSkat);
        setGone(R.id.rowStich);
        setVisible(R.id.dialogDesk);
        draw_skat(spieler);
        rem_box(1);
        rem_box(2);
        put_fbox(spieler, false);
        drbut = spieler + 1;
    }

    public void clickedAufnehmenNein(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogSchieben);
        setVisible(R.id.dialogDesk);
        di_verdoppelt(false, false);
        computer();
    }

    public void clickedSchiebenOK(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogVerdoppelt);
        setVisible(R.id.dialogDesk);
        di_verdoppelt(true, false);
        computer();
    }

    public void clickedKlopfenJa(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogKlopfen);
        setVisible(R.id.dialogDesk);
        di_verdoppelt(false, true);
        computer();
    }

    public void clickedKlopfenNein(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogKlopfen);
        setVisible(R.id.dialogDesk);
        vmh = left(vmh);
        if (vmh != 0)
            di_schieben();
        else
            start_ramsch();
    }

    public void clickedBubenOK(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogBuben);
        setVisible(R.id.dialogDesk);
    }

    public void clickedKontraJa(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogKontra);
        setVisible(R.id.dialogDesk);
        di_ktrnext(0, true);
        computer();
    }

    public void clickedKontraNein(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogKontra);
        setVisible(R.id.dialogDesk);
        di_ktrnext(0, false);
        computer();
    }

    public void clickedReJa(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogReKontra);
        setVisible(R.id.dialogDesk);
        di_ktrnext(0, true);
        computer();
    }

    public void clickedReNein(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogReKontra);
        setVisible(R.id.dialogDesk);
        di_ktrnext(0, false);
        computer();
    }

    public void clickedKontraReOK(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogKontraRe);
        setVisible(R.id.dialogDesk);
        if (ktrnext >= 0) {
            di_konre(ktrnext);
            ktrnext = -1;
        } else {
            do_angesagt();
        }
        computer();
    }

    public void clickedDruecken(View view) {
        if (discardInput)
            return;
        hndl_druecken(0);
    }

    public void clickedAnsageOK(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogAnsage);
        setVisible(R.id.dialogDesk);
        do_angesagt();
        computer();
    }

    public void clickedFehlerOK(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogFehler);
        setDeselected(R.id.buttonSchneider);
        setDeselected(R.id.buttonSchwarz);
        setDeselected(R.id.buttonOuvert);
        setVisible(R.id.dialogSpielen);
    }

    public void clickedOptionsOK(View view) {
        if (discardInput)
            return;
        setDeselected(R.id.buttonBlattTU);
        setDeselected(R.id.buttonBlattFR);
        setDeselected(R.id.buttonBlattDE);
        setDeselected(R.id.buttonBlattBDK);
        setDeselected(R.id.buttonBlattJQK);
        setDeselected(R.id.buttonBlattUOK);
        switch (spBlatt) {
        default:
            setSelected(R.id.buttonBlattTU);
            break;
        case 1:
            setSelected(R.id.buttonBlattFR);
            break;
        case 2:
            setSelected(R.id.buttonBlattDE);
            break;
        }
        switch (spBDK) {
        default:
            setSelected(R.id.buttonBlattBDK);
            break;
        case 1:
            setSelected(R.id.buttonBlattJQK);
            break;
        case 2:
            setSelected(R.id.buttonBlattUOK);
            break;
        }
        if (alternate[0] == 0) {
            setSelected(R.id.buttonBlattSortS);
        } else {
            setSelected(R.id.buttonBlattSortA);
        }
        setDialogsGone();
        setVisible(R.id.dialogBlatt);
    }

    public void clickedOptionsListe(View view) {
        if (discardInput)
            return;
        if (prot1.stiche[0][0] != 0 || prot1.stiche[0][1] != 0)
            clickedResultProto(null);
        else
            clickedProtoListe(null);
    }

    public void clickedNewGame(View view) {
        if (discardInput)
            return;
        Intent intent = getIntent();
        finishing = true;
        finish();
        startActivity(intent);
    }

    public void clickedBlattOK(View view) {
        if (discardInput)
            return;
        switch (playramsch) {
        default:
            setSelected(R.id.buttonRamschNein);
            break;
        case 1:
            setSelected(R.id.buttonRamschJa);
            break;
        case 2:
            setSelected(R.id.buttonRamschImmer);
            break;
        }
        switch (playkontra) {
        default:
            setSelected(R.id.buttonVarKontraNein);
            break;
        case 1:
            setSelected(R.id.buttonVarKontraJa);
            break;
        case 2:
            setSelected(R.id.buttonVarKontraAb18);
            break;
        }
        setDialogsGone();
        setVisible(R.id.dialogVarianten);
    }

    public void clickedBlattListe(View view) {
        if (discardInput)
            return;
        clickedProtoListe(null);
    }

    public void clickedVariantenOK(View view) {
        if (discardInput)
            return;
        setDialogsGone();
        if (nimmstich[0][0] >= 101) {
            setInvisible(R.id.buttonStichP);
        }
        if (nimmstich[0][0] == 0) {
            setInvisible(R.id.buttonStichM);
        }
        if (playramsch > 0) {
            if (playsramsch != 0) {
                setDeselected(R.id.buttonSchieberamschNein);
                setSelected(R.id.buttonSchieberamschJa);
            } else {
                setSelected(R.id.buttonSchieberamschNein);
                setDeselected(R.id.buttonSchieberamschJa);
            }
            if (rskatloser != 0) {
                setDeselected(R.id.buttonLetztenStich);
                setSelected(R.id.buttonVerlierer);
            } else {
                setSelected(R.id.buttonLetztenStich);
                setDeselected(R.id.buttonVerlierer);
            }
            setVisible(R.id.dialogRamschVarianten);
        } else {
            if (hints[0]) {
                setDeselected(R.id.buttonVorschlaegeNein);
                setSelected(R.id.buttonVorschlaegeJa);
            } else {
                setDeselected(R.id.buttonVorschlaegeJa);
                setSelected(R.id.buttonVorschlaegeNein);
            }
            setVisible(R.id.dialogStich);
        }
    }

    public void clickedVariantenFertig(View view) {
        if (discardInput)
            return;
        setDialogsGone();
        setGone(R.id.dialogScreen);
        setVisible(R.id.mainScreen);
    }

    public void clickedRamschVariantenOK(View view) {
        if (discardInput)
            return;
        setDialogsGone();
        if (nimmstich[0][0] >= 101) {
            setInvisible(R.id.buttonStichP);
        }
        if (nimmstich[0][0] == 0) {
            setInvisible(R.id.buttonStichM);
        }
        if (hints[0]) {
            setDeselected(R.id.buttonVorschlaegeNein);
            setSelected(R.id.buttonVorschlaegeJa);
        } else {
            setDeselected(R.id.buttonVorschlaegeJa);
            setSelected(R.id.buttonVorschlaegeNein);
        }
        setVisible(R.id.dialogStich);
    }

    public void clickedRamschVariantenFertig(View view) {
        if (discardInput)
            return;
        setDialogsGone();
        setGone(R.id.dialogScreen);
        setVisible(R.id.mainScreen);
    }

    public void clickedStichM(View view) {
        if (discardInput)
            return;
        setVisible(R.id.buttonStichP);
        if (nimmstich[0][0] >= 101) {
            nimmstich[0][0] = 100;
        } else if (nimmstich[0][0] > 10) {
            nimmstich[0][0] -= 10;
        } else {
            nimmstich[0][0] = 0;
            setInvisible(R.id.buttonStichM);
        }
        initStichStr();
    }

    public void clickedStichP(View view) {
        if (discardInput)
            return;
        setVisible(R.id.buttonStichM);
        nimmstich[0][0] += 10;
        if (nimmstich[0][0] >= 101) {
            nimmstich[0][0] = 101;
            setInvisible(R.id.buttonStichP);
        }
        initStichStr();
    }

    public void clickedStichOK(View view) {
        if (discardInput)
            return;
        setDialogsGone();
        setGone(R.id.dialogScreen);
        setVisible(R.id.mainScreen);
    }

    public void clickedProtoListe(View view) {
        if (discardInput)
            return;
        di_liste(0, true);
        setGone(R.id.mainScreen);
        setGone(R.id.dialogProto);
        setGone(R.id.dialogLoeschen);
        setDialogsGone();
        setVisible(R.id.dialogListe);
        setVisible(R.id.dialogScreen);
    }

    public void clickedProtoOK(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogProto);
        setGone(R.id.dialogScreen);
        setVisible(R.id.mainScreen);
    }

    public void clickedProtoPfeil(View view) {
        if (discardInput)
            return;
        protsort[0] = !protsort[0];
        di_proto(0, false, false);
    }

    public void clickedListeOK(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogListe);
        setGone(R.id.dialogScreen);
        setVisible(R.id.mainScreen);
    }

    public void clickedListeLoeschen(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogListe);
        setVisible(R.id.dialogLoeschen);
    }

    public void clickedLoeschenJa(View view) {
        if (discardInput)
            return;
        splstp = 0;
        sum = new int[3][3];
        splsum = new int[3][3];
        cgewoverl = new int[3][2];
        sgewoverl = new int[3][2];
        save_list();
        TextView v = (TextView) findViewById(R.id.textSpielstandPlayer);
        v.setText("0");
        v.setTypeface(null, Typeface.NORMAL);
        v = (TextView) findViewById(R.id.textSpielstandComputerLeft);
        v.setText("0");
        v.setTypeface(null, Typeface.NORMAL);
        v = (TextView) findViewById(R.id.textSpielstandComputerRight);
        v.setText("0");
        v.setTypeface(null, Typeface.NORMAL);
        setGone(R.id.dialogListe);
        setGone(R.id.dialogLoeschen);
        setGone(R.id.dialogScreen);
        setVisible(R.id.mainScreen);
    }

    public void clickedLoeschenNein(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogLoeschen);
        setVisible(R.id.dialogListe);
    }

    public void clickedCopyrightOK(View view) {
        if (discardInput)
            return;
        setDialogsGone();
        setGone(R.id.dialogScreen);
        setVisible(R.id.mainScreen);
    }

    public void clickedUeberreiztOK(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogUeberreizt);
        setVisible(R.id.dialogSpielen);
    }

    public void clickedResultProto(View view) {
        if (discardInput)
            return;
        di_proto(0, true, false);
        setGone(R.id.mainScreen);
        setGone(R.id.dialogListe);
        setGone(R.id.dialogLoeschen);
        setDialogsGone();
        setVisible(R.id.dialogProto);
        setVisible(R.id.dialogScreen);
    }

    public void clickedResultNochmal(View view) {
        if (discardInput)
            return;
        setGone(R.id.mainScreen);
        setGone(R.id.dialogProto);
        setGone(R.id.dialogListe);
        setGone(R.id.dialogLoeschen);
        setDialogsGone();
        setDeselected(R.id.buttonVonAndroido);
        setSelected(R.id.buttonVonMir);
        setDeselected(R.id.buttonVonAndroida);
        setVisible(R.id.dialogWiederholen);
        setVisible(R.id.dialogScreen);
    }

    public void clickedResultOK(View view) {
        if (discardInput)
            return;
        setGone(R.id.dialogResult);
        setVisible(R.id.dialogDesk);
        phase = GEBEN;
        computer();
    }

    public void clickedWeiter(View view) {
        if (discardInput)
            return;
        setGone(R.id.boxWeiter);
        clr_desk(false);
        skatopen = false;
        phase = GEBEN;
        computer();
    }

    public void clickedFertig(View view) {
        if (discardInput)
            return;
        hndl_druecken(0);
        computer();
    }

    public void clickedWiederholenZurueck(View view) {
        if (discardInput)
            return;
        setDialogsGone();
        setGone(R.id.dialogScreen);
        setVisible(R.id.mainScreen);
    }

    public void clickedWiederholenStart(View view) {
        if (discardInput)
            return;
        setDialogsGone();
        setGone(R.id.dialogScreen);
        setVisible(R.id.mainScreen);
        setGone(R.id.dialogResult);
        setVisible(R.id.dialogDesk);
        if (isSelected(R.id.buttonVonAndroido)) {
            wieder = 1;
        } else if (isSelected(R.id.buttonVonMir)) {
            wieder = 2;
        } else {
            wieder = 3;
        }
        phase = GEBEN;
        computer();
    }

    public void clickedSpace(View view) {
        if (discardInput)
            return;
        if (takeTrick != null) {
            runHandler.removeCallbacks(takeTrick);
            takeTrick = null;
        }
        if (phase == NIMMSTICH) {
            if (nimmstich[0][1] != 0) {
                hndl_nimmstich(0);
            }
        } else if (phase == LETZTERSTICH) {
            restoreTrick();
        }
    }

    // private

    boolean restart;
    boolean finishing;
    boolean layedOut;
    boolean discardInput;
    int cardHeight;
    int cardWidth;
    int[] lasthint = new int[2];
    boolean inOnPause = false;
    boolean waitForLayout;
    String xskatVersion;
    Context activityContext = null;
    Handler runHandler = null;
    LayoutParams layoutParams = null;
    ImageView overlayView = null;
    Runnable acceptAnim = null;
    Runnable cardAnim = null;
    Runnable takeTrick = null;
    SharedPreferences prefs;
    SharedPreferences.Editor editor;
    LayerDrawable[] drawnCard = null;

    final int[] cardNameUTU = { R.drawable.utka, R.drawable.uthe,
            R.drawable.utpi, R.drawable.utkr };

    final int[] cardNameUFR = { R.drawable.ufka, R.drawable.uthe,
            R.drawable.ufpi, R.drawable.utkr };

    final int[] cardNameUDE = { R.drawable.udsc, R.drawable.udro,
            R.drawable.udgr, R.drawable.udei };

    final int[] cardNameZTUDE = { R.drawable.zoa, R.drawable.zo10,
            R.drawable.zok, R.drawable.zod, R.drawable.zob, R.drawable.zo9,
            R.drawable.zo8, R.drawable.zo7, R.drawable.zra, R.drawable.zr10,
            R.drawable.zrk, R.drawable.zrd, R.drawable.zrb, R.drawable.zr9,
            R.drawable.zr8, R.drawable.zr7, R.drawable.zga, R.drawable.zg10,
            R.drawable.zgk, R.drawable.zgd, R.drawable.zgb, R.drawable.zg9,
            R.drawable.zg8, R.drawable.zg7, R.drawable.zsa, R.drawable.zs10,
            R.drawable.zsk, R.drawable.zsd, R.drawable.zsb, R.drawable.zs9,
            R.drawable.zs8, R.drawable.zs7 };

    final int[] cardNameZTUEN = { R.drawable.zoa, R.drawable.zo10,
            R.drawable.zok, R.drawable.zoq, R.drawable.zoj, R.drawable.zo9,
            R.drawable.zo8, R.drawable.zo7, R.drawable.zra, R.drawable.zr10,
            R.drawable.zrk, R.drawable.zrq, R.drawable.zrj, R.drawable.zr9,
            R.drawable.zr8, R.drawable.zr7, R.drawable.zga, R.drawable.zg10,
            R.drawable.zgk, R.drawable.zgq, R.drawable.zgj, R.drawable.zg9,
            R.drawable.zg8, R.drawable.zg7, R.drawable.zsa, R.drawable.zs10,
            R.drawable.zsk, R.drawable.zsq, R.drawable.zsj, R.drawable.zs9,
            R.drawable.zs8, R.drawable.zs7 };

    final int[] cardNameZFRDE = { R.drawable.zra, R.drawable.zr10,
            R.drawable.zrk, R.drawable.zrd, R.drawable.zrb, R.drawable.zr9,
            R.drawable.zr8, R.drawable.zr7, R.drawable.zra, R.drawable.zr10,
            R.drawable.zrk, R.drawable.zrd, R.drawable.zrb, R.drawable.zr9,
            R.drawable.zr8, R.drawable.zr7, R.drawable.zsa, R.drawable.zs10,
            R.drawable.zsk, R.drawable.zsd, R.drawable.zsb, R.drawable.zs9,
            R.drawable.zs8, R.drawable.zs7, R.drawable.zsa, R.drawable.zs10,
            R.drawable.zsk, R.drawable.zsd, R.drawable.zsb, R.drawable.zs9,
            R.drawable.zs8, R.drawable.zs7 };

    final int[] cardNameZFREN = { R.drawable.zra, R.drawable.zr10,
            R.drawable.zrk, R.drawable.zrq, R.drawable.zrj, R.drawable.zr9,
            R.drawable.zr8, R.drawable.zr7, R.drawable.zra, R.drawable.zr10,
            R.drawable.zrk, R.drawable.zrq, R.drawable.zrj, R.drawable.zr9,
            R.drawable.zr8, R.drawable.zr7, R.drawable.zsa, R.drawable.zs10,
            R.drawable.zsk, R.drawable.zsq, R.drawable.zsj, R.drawable.zs9,
            R.drawable.zs8, R.drawable.zs7, R.drawable.zsa, R.drawable.zs10,
            R.drawable.zsk, R.drawable.zsq, R.drawable.zsj, R.drawable.zs9,
            R.drawable.zs8, R.drawable.zs7 };

    final int[] cardNameZDETU = { R.drawable.zoa, R.drawable.zo10,
            R.drawable.zok, R.drawable.zoo, R.drawable.zou, R.drawable.zo9,
            R.drawable.zo8, R.drawable.zo7, R.drawable.zra, R.drawable.zr10,
            R.drawable.zrk, R.drawable.zro, R.drawable.zru, R.drawable.zr9,
            R.drawable.zr8, R.drawable.zr7, R.drawable.zga, R.drawable.zg10,
            R.drawable.zgk, R.drawable.zgo, R.drawable.zgu, R.drawable.zg9,
            R.drawable.zg8, R.drawable.zg7, R.drawable.zsa, R.drawable.zs10,
            R.drawable.zsk, R.drawable.zso, R.drawable.zsu, R.drawable.zs9,
            R.drawable.zs8, R.drawable.zs7 };

    final int[] cardNameZDEFR = { R.drawable.zra, R.drawable.zr10,
            R.drawable.zrk, R.drawable.zro, R.drawable.zru, R.drawable.zr9,
            R.drawable.zr8, R.drawable.zr7, R.drawable.zra, R.drawable.zr10,
            R.drawable.zrk, R.drawable.zro, R.drawable.zru, R.drawable.zr9,
            R.drawable.zr8, R.drawable.zr7, R.drawable.zsa, R.drawable.zs10,
            R.drawable.zsk, R.drawable.zso, R.drawable.zsu, R.drawable.zs9,
            R.drawable.zs8, R.drawable.zs7, R.drawable.zsa, R.drawable.zs10,
            R.drawable.zsk, R.drawable.zso, R.drawable.zsu, R.drawable.zs9,
            R.drawable.zs8, R.drawable.zs7 };

    final int[] gameSymbTU = { R.drawable.none, R.drawable.pvh,
            R.drawable.pt78, R.drawable.stka, R.drawable.sthe, R.drawable.stpi,
            R.drawable.stkr };

    final int[] gameSymbFR = { R.drawable.none, R.drawable.pvh,
            R.drawable.pf78, R.drawable.sfka, R.drawable.sthe, R.drawable.sfpi,
            R.drawable.stkr };

    final int[] gameSymbDE = { R.drawable.none, R.drawable.pvh,
            R.drawable.pt78, R.drawable.sds, R.drawable.sdr, R.drawable.sdg,
            R.drawable.sde };

    final int[] gameSymbTG = { R.drawable.pttg, R.drawable.pteg,
            R.drawable.ptdg };

    final int[] gameSymbFG = { R.drawable.pftg, R.drawable.pfeg,
            R.drawable.pfdg };

    final int[][] spMat = {
            { R.id.sp0st0, R.id.sp0st1, R.id.sp0st2, R.id.sp0st3, R.id.sp0st4,
                    R.id.sp0st5, R.id.sp0st6, R.id.sp0st7, R.id.sp0st8,
                    R.id.sp0st9 },
            { R.id.sp1st0, R.id.sp1st1, R.id.sp1st2, R.id.sp1st3, R.id.sp1st4,
                    R.id.sp1st5, R.id.sp1st6, R.id.sp1st7, R.id.sp1st8,
                    R.id.sp1st9 },
            { R.id.sp2st0, R.id.sp2st1, R.id.sp2st2, R.id.sp2st3, R.id.sp2st4,
                    R.id.sp2st5, R.id.sp2st6, R.id.sp2st7, R.id.sp2st8,
                    R.id.sp2st9 },
            {R.id.sum0, R.id.sum1, R.id.sum2, R.id.sum3, R.id.sum4, R.id.sum5, R.id.sum6, R.id.sum7, R.id.sum8, R.id.sum9}
    };
    final int[][] liMat = {
            { R.id.li0sp0, R.id.li0sp1, R.id.li0sp2, R.id.li0sp3, R.id.li0sp4,
                    R.id.li0sp5, R.id.li0sp6, R.id.li0sp7, R.id.li0sp8,
                    R.id.li0sp9, R.id.li0foot },
            { R.id.li1sp0, R.id.li1sp1, R.id.li1sp2, R.id.li1sp3, R.id.li1sp4,
                    R.id.li1sp5, R.id.li1sp6, R.id.li1sp7, R.id.li1sp8,
                    R.id.li1sp9, R.id.li1foot },
            { R.id.li2sp0, R.id.li2sp1, R.id.li2sp2, R.id.li2sp3, R.id.li2sp4,
                    R.id.li2sp5, R.id.li2sp6, R.id.li2sp7, R.id.li2sp8,
                    R.id.li2sp9, R.id.li2foot },
            { R.id.li3sp0, R.id.li3sp1, R.id.li3sp2, R.id.li3sp3, R.id.li3sp4,
                    R.id.li3sp5, R.id.li3sp6, R.id.li3sp7, R.id.li3sp8,
                    R.id.li3sp9, R.id.li3foot } };
    final String[] suitColTU = { "#d49c16", "#d41616", "#169c16", "#161616" };
    final String[] suitColFR = { "#d41616", "#d41616", "#161616", "#161616" };
    final String[] cardValFR = { "A", "10", "K", "D", "B", "9", "8", "7" };
    final String[] cardValDE = { "A", "10", "K", "O", "U", "9", "8", "7" };
    final String[] cardValEN = { "A", "10", "K", "Q", "J", "9", "8", "7" };

    void readPrefs() {
        try {
            xskatVersion = getPackageManager().getPackageInfo(getPackageName(),
                    0).versionName;
        } catch (NameNotFoundException e) {
            xskatVersion = "";
        }
        String savdLang = prefs.getString("defLang", "");
        int savcLang = prefs.getInt("currLang", 0);
        String defLang = Locale.getDefault().getLanguage();
        if (savdLang.equals(defLang)) {
            currLang = savcLang;
            spBlatt = prefs.getInt("spBlatt", 0);
            spBDK = prefs.getInt("spBDK", currLang == 0 ? 0 : 1);
            for (int i = 0; i < 3; i++)
                alternate[i] = prefs.getInt("alternate" + i, 0);
        } else {
            currLang = defLang.equals("de") ? 0 : 1;
            spBlatt = 0;
            spBDK = currLang == 0 ? 0 : 1;
            for (int i = 0; i < 3; i++)
                alternate[i] = 0;
            editor.putInt("spBlatt", spBlatt);
            editor.putInt("spBDK", spBDK);
            for (int i = 0; i < 3; i++)
                editor.putInt("alternate" + i, alternate[i]);
        }
        editor.putString("defLang", defLang);
        editor.putInt("currLang", currLang);
        klopfen = prefs.getBoolean("klopfen", false);
        playkontra = prefs.getInt("playkontra", 0);
        playramsch = prefs.getInt("playramsch", 0);
        playsramsch = prefs.getInt("playsramsch", 0);
        rskatloser = prefs.getInt("rskatloser", 0);
        for (int i = 0; i < 3; i++)
            hints[i] = prefs.getBoolean("hints" + i, false);
        for (int i = 0; i < 3; i++)
            strateg[i] = prefs.getInt("strateg" + i, 0);
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 2; j++)
                nimmstich[i][j] = prefs.getInt("nimmstich" + i + "." + j,
                        j == 0 ? 101 : 0);
    }

    void initCallback() {
        final View v = findViewById(R.id.card0);
        ViewTreeObserver vto = v.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                if (!layedOut) {
                    cardWidth = v.getWidth();
                    cardHeight = v.getHeight();
                    initDialogs();
                    if (restart) {
                        readViews();
                    } else {
                        if (firstgame) {
                            showDialog(R.id.dialogCopyright);
                        }
                        put_box(sager);
                        put_box(hoerer);
                    }
                    layedOut = true;
                    if (waitForLayout) {
                        rem_fbox(0);
                        waitForLayout = false;
                        rem_box(1);
                        rem_box(2);
                        do_spielen();
                    }
                }
            }
        });
    }

    void restoreTrick() {
        drawcard(0, cardStichLeftBgr, 2, 1);
        drawcard(0, cardStichMiddleBgr, 3, 1);
        drawcard(0, cardStichRightBgr, 4, 1);
        phase = SPIELEN;
    }

    Drawable cardName(int n) {
        return drawnCard[n];
    }

    int gameSymb(int n) {
        if (n == 8)
            return R.drawable.ram;
        if (n == 7) {
            return spBlatt == 1 ? gameSymbFG[spBDK] : gameSymbTG[spBDK];
        }
        return spBlatt == 0 ? gameSymbTU[n] : spBlatt == 1 ? gameSymbFR[n]
                : gameSymbDE[n];
    }

    String suitCol(int n) {
        if (spBlatt == 1) {
            return suitColFR[n];
        }
        return suitColTU[n];
    }

    String cardVal(int n) {
        if (spBDK == 2) {
            return cardValDE[n];
        }
        return spBDK == 0 ? cardValFR[n] : cardValEN[n];
    }

    String gameName(int n) {
        if (spBlatt == 2 && 0 <= n && n <= 3) {
            return getTranslation(Translations.XT_Schellen + n);
        }
        return getTranslation(n + 1);
    }

    void drawCardOverlay(int x, int y, Drawable cardId, boolean delete) {
        WindowManager wm = (WindowManager) activityContext.getSystemService(WINDOW_SERVICE);
        if (inOnPause) {
            if (overlayView != null) {
                try {
                    wm.removeView(overlayView);
                } catch (IllegalArgumentException ignore) {
                    // ignore
                }
                overlayView = null;
            }
            return;
        }
        layoutParams.x = x;
        layoutParams.y = y;
        layoutParams.width = cardWidth;
        layoutParams.height = cardHeight;
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        layoutParams.format = PixelFormat.TRANSPARENT;
        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        layoutParams.gravity = Gravity.START | Gravity.TOP;
        if (overlayView == null) {
            overlayView = new ImageView(activityContext);
            overlayView.setImageDrawable(cardId);
            overlayView.setScaleType(ScaleType.FIT_XY);
            wm.addView(overlayView, layoutParams);
        } else {
            if (!delete) {
                wm.updateViewLayout(overlayView, layoutParams);
            } else {
                wm.removeView(overlayView);
                overlayView = null;
            }
        }
    }

    void moveCardOverlay(final int card, final int fromId, final int toId,
            final int toX, final int toY, final int tail, final int p) {
        final int steps = 3 + animSpeed;

        discardInput = true;
        if (p <= steps) {
            if (p < steps) {
                int[] xy1 = new int[2], xy2 = new int[2];
                findViewById(R.id.card4).getLocationOnScreen(xy1);
                findViewById(R.id.card5).getLocationOnScreen(xy2);
                if (xy1[0] < xy2[0]) {
                    xy2[0] = xy1[0];
                    xy2[1] -= cardHeight * 7 / 4;
                } else {
                    xy2[0] += cardWidth * 3 / 2;
                    xy2[1] -= cardHeight * 11 / 4;
                }
                findViewById(fromId).getLocationOnScreen(xy1);
                switch (toId) {
                case R.id.cardSkatLeft:
                    break;
                case R.id.cardSkatRight:
                    xy2[0] += cardWidth;
                    break;
                case R.id.cardStichLeft:
                    xy2[0] -= cardWidth / 2;
                    break;
                case R.id.cardStichMiddle:
                    xy2[0] += cardWidth / 2;
                    break;
                case R.id.cardStichRight:
                    xy2[0] += 3 * cardWidth / 2;
                    break;
                default:
                    findViewById(toId).getLocationOnScreen(xy2);
                    break;
                }
                drawCardOverlay((xy1[0] * (steps - 1 - p) + xy2[0] * p)
                        / (steps - 1), (xy1[1] * (steps - 1 - p) + xy2[1] * p)
                        / (steps - 1), cardName(card + 2), p == steps - 1);
            }
            cardAnim = new Runnable() {
                public void run() {
                    moveCardOverlay(card, fromId, toId, toX, toY, tail, p + 1);
                }
            };
            if (inOnPause) {
                cardAnim.run();
            } else {
                runHandler.postDelayed(cardAnim, p < steps - 1 ? 50
                        : tail == 1 ? 200 : 0);
            }
        } else {
            switch (tail) {
            case 0:
                putcard(0, card, toX, toY);
                stdwait();
                do_next();
                if (phase == SPIELEN)
                    do_spielen();
                else
                    computer();
                break;
            case 1:
                boolean nd;
                int[] ufb = new int[1],
                mfb = new int[4],
                sfb = new int[4];
                vmh = 0;
                stich++;
                nd = false;
                if (stich == 11
                        || (trumpf == -1 && (nullv || (!ndichtw && stich < 10 && (nd = null_dicht(
                                spieler, handsp, prot2.skat[1], ufb, mfb, sfb)))))) {
                    if (nd)
                        di_dicht();
                    else
                        finishgame();
                }
                computer();
                break;
            default:
                putcard(0, cards[drkcd + 30], D_skatx + drkcd * D_cardw,
                        D_skaty);
                initscr(0, 1);
                drkcd = 1 - drkcd;
                break;
            }
            cardAnim = null;
            if (inOnPause)
                save_list();
            discardInput = false;
        }
    }

    void setTextSize(int id) {
        TextView v = (TextView) findViewById(id);
        float h = cardHeight / 5.0f;
        float w = cardWidth / 4.5f;
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.min(h, w));
        v.setGravity(Gravity.CENTER);
    }

    void setTitleTextSize(int id) {
        TextView v = (TextView) findViewById(id);
        float h = cardHeight / 5.0f * 1.2f;
        float w = cardWidth / 4.5f * 1.2f;
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, Math.min(h, w));
        v.setTypeface(null, Typeface.BOLD);
        v.setGravity(Gravity.CENTER);
    }

    void initDialogs() {
        setDeselectedAndSize(R.id.buttonKaro);
        setDeselectedAndSize(R.id.buttonHerz);
        setDeselectedAndSize(R.id.buttonPik);
        setDeselectedAndSize(R.id.buttonKreuz);
        setDeselectedAndSize(R.id.buttonNull);
        setDeselectedAndSize(R.id.buttonGrand);
        setDeselectedAndSize(R.id.buttonSchneider);
        setDeselectedAndSize(R.id.buttonSchwarz);
        setDeselectedAndSize(R.id.buttonOuvert);
        setDeselectedAndSize(R.id.buttonSpitze);
        setDeselectedAndSize(R.id.buttonSpielen);
        setDeselectedAndSize(R.id.buttonRevolution);
        setDeselectedAndSize(R.id.buttonBack);
        setTextSize(R.id.textGereizt);
        setText(R.id.buttonKaro, gameName(0));
        setText(R.id.buttonHerz, gameName(1));
        setText(R.id.buttonPik, gameName(2));
        setText(R.id.buttonKreuz, gameName(3));
        setText(R.id.buttonNull, getTranslation(Translations.XT_Null));
        setText(R.id.buttonGrand, getTranslation(Translations.XT_Grand));
        setText(R.id.buttonSchneider, getTranslation(Translations.XT_Schneider));
        setText(R.id.buttonSchwarz, getTranslation(Translations.XT_Schwarz));
        setText(R.id.buttonOuvert, getTranslation(Translations.XT_Ouvert));
        setText(R.id.buttonSpitze, getTranslation(Translations.XT_Spitze));
        setText(R.id.buttonSpielen, getTranslation(Translations.XT_Spielen));
        setText(R.id.buttonRevolution, getTranslation(Translations.XT_Revolution));
        if (phase == ANSAGEN)
            initSpielStr();

        setTitleTextSize(R.id.textHand);
        setDeselectedAndSize(R.id.buttonHandJa);
        setDeselectedAndSize(R.id.buttonHandNein);
        setTextSize(R.id.textGereiztHand);
        setText(R.id.textHand, getTranslation(Translations.XT_HandFrage));
        setText(R.id.buttonHandJa, getTranslation(Translations.XT_Ja));
        setText(R.id.buttonHandNein, getTranslation(Translations.XT_Nein));
        if (phase == HANDSPIEL)
            initHandStr();

        setTitleTextSize(R.id.textSkatAufnehmen);
        setDeselectedAndSize(R.id.buttonAufnehmenJa);
        setDeselectedAndSize(R.id.buttonAufnehmenNein);
        setText(R.id.textSkatAufnehmen, getTranslation(Translations.XT_Skat_aufnehmen));
        setText(R.id.buttonAufnehmenJa, getTranslation(Translations.XT_Ja));
        setText(R.id.buttonAufnehmenNein, getTranslation(Translations.XT_Nein));

        setTitleTextSize(R.id.textVerdoppelt);
        setTextSize(R.id.textWerSchiebt);
        setTextSize(R.id.textSchiebt);
        setText(R.id.textVerdoppelt, getTranslation(Translations.XT_Spielwert_verdoppelt));
        setDeselectedAndSize(R.id.buttonSchiebenOK);
        setText(R.id.buttonSchiebenOK, getTranslation(Translations.XT_Weiter));
        if (phase == DRUECKEN)
            initVerdoppeltStr();

        setTitleTextSize(R.id.textBuben);
        setTextSize(R.id.textDuerfenNicht);
        setDeselectedAndSize(R.id.buttonBubenOK);
        setText(R.id.textDuerfenNicht, getTranslation(Translations.XT_duerfen_nicht_geschoben_werden));
        setText(R.id.buttonBubenOK, getTranslation(Translations.XT_Weiter));
        if (phase == DRUECKEN)
            initBubenStr();

        setTextSize(R.id.textWerSpielt);
        setTitleTextSize(R.id.textSpieltWas);
        setTextSize(R.id.textFuer);
        setDeselectedAndSize(R.id.buttonAnsageOK);
        setText(R.id.buttonAnsageOK, getTranslation(Translations.XT_Weiter));
        if (phase == ANSAGEN)
            initAnsageStr();

        setTextSize(R.id.textKontraWerSpielt);
        setTitleTextSize(R.id.textKontraSpieltWas);
        setTextSize(R.id.textKontraFuer);
        setDeselectedAndSize(R.id.buttonKontraJa);
        setDeselectedAndSize(R.id.buttonKontraNein);
        setText(R.id.buttonKontraJa, getTranslation(Translations.XT_Kontra));
        setText(R.id.buttonKontraNein, getTranslation(Translations.XT_Weiter));
        if (phase == ANSAGEN)
            initKontraStr();

        setTitleTextSize(R.id.textReKontra);
        setTextSize(R.id.textKontraVon);
        setDeselectedAndSize(R.id.buttonReJa);
        setDeselectedAndSize(R.id.buttonReNein);
        setText(R.id.textReKontra, getTranslation(Translations.XT_Kontra));
        setText(R.id.buttonReJa, getTranslation(Translations.XT_Re));
        setText(R.id.buttonReNein, getTranslation(Translations.XT_Weiter));
        if (phase == ANSAGEN)
            initReKontraStr();

        setTextSize(R.id.textKontraReWerSpielt);
        setTitleTextSize(R.id.textKontraReSpieltWas);
        setTextSize(R.id.textKontraReFuer);
        setTextSize(R.id.textMitKontraRe);
        setDeselectedAndSize(R.id.buttonKontraReOK);
        setText(R.id.buttonKontraReOK, getTranslation(Translations.XT_Weiter));
        if (phase == ANSAGEN)
            initKontraReStr();

        setTextSize(R.id.textResultMsg);
        setTextSize(R.id.textResult);
        setTextSize(R.id.textGewVerl);
        setTextSize(R.id.textMitAugen);
        setTextSize(R.id.textGereiztBis);
        setTextSize(R.id.textSpielstandSpieler);
        setTextSize(R.id.textSpielstandComputerL);
        setTextSize(R.id.textSpielstandComputerR);
        setTextSize(R.id.textSpielstandPlayer);
        setTextSize(R.id.textSpielstandComputerLeft);
        setTextSize(R.id.textSpielstandComputerRight);
        setDeselectedAndSize(R.id.buttonResultNochmal);
        setDeselectedAndSize(R.id.buttonResultOK);
        setDeselectedAndSize(R.id.buttonResultProto);
        setText(R.id.textSpielstandSpieler, getTranslation(Translations.XT_Spieler));
        setText(R.id.textSpielstandComputerL, getTranslation(Translations.XT_Androido));
        setText(R.id.textSpielstandComputerR, getTranslation(Translations.XT_Androida));
        setText(R.id.buttonResultNochmal, getTranslation(Translations.XT_Nochmal));
        setText(R.id.buttonResultOK, getTranslation(Translations.XT_Weiter));
        setText(R.id.buttonResultProto, getTranslation(Translations.XT_Protokoll));
        if (phase == RESULT)
            initResultStr();

        setTextSize(R.id.textF1);
        setTextSize(R.id.textF2);
        setTextSize(R.id.textF3);
        setTextSize(R.id.textF4);
        setDeselectedAndSize(R.id.buttonFehlerOK);
        setText(R.id.textF1, getTranslation(Translations.XT_Nur_bei_Handspielen_kann_Schneider));
        setText(R.id.textF2, getTranslation(Translations.XT_oder_schwarz_angesagt_werden));
        setText(R.id.textF3, getTranslation(Translations.XT_Ouvert_schliesst_schwarz_angesagt_ein));
        setText(R.id.textF4, getTranslation(Translations.XT_ausser_bei_Null_natuerlich));
        setText(R.id.buttonFehlerOK, getTranslation(Translations.XT_Weiter));

        setTextSize(R.id.textU1);
        setTextSize(R.id.textU2);
        setTextSize(R.id.textU3);
        setTextSize(R.id.textU4);
        setTextSize(R.id.textU5);
        setDeselectedAndSize(R.id.buttonUeberreiztOK);
        setText(R.id.textU1, getTranslation(Translations.XT_Du_hast_hoeher_gereizt_als_der));
        setText(R.id.textU2, getTranslation(Translations.XT_Wert_des_angesagten_Spiels));
        setText(R.id.textU3, getTranslation(Translations.XT_Null_23_Hand_35));
        setText(R.id.textU4, getTranslation(Translations.XT_ouvert_46_ouvert_Hand_59));
        setText(R.id.textU5, getTranslation(Translations.XT_Revolution_92));
        setText(R.id.buttonUeberreiztOK, getTranslation(Translations.XT_Weiter));

        setTextSize(R.id.sp0head);
        setTextSize(R.id.sp0st0);
        setTextSize(R.id.sp0st1);
        setTextSize(R.id.sp0st2);
        setTextSize(R.id.sp0st3);
        setTextSize(R.id.sp0st4);
        setTextSize(R.id.sp0st5);
        setTextSize(R.id.sp0st6);
        setTextSize(R.id.sp0st7);
        setTextSize(R.id.sp0st8);
        setTextSize(R.id.sp0st9);
        setTextSize(R.id.sp1head);
        setTextSize(R.id.sp1st0);
        setTextSize(R.id.sp1st1);
        setTextSize(R.id.sp1st2);
        setTextSize(R.id.sp1st3);
        setTextSize(R.id.sp1st4);
        setTextSize(R.id.sp1st5);
        setTextSize(R.id.sp1st6);
        setTextSize(R.id.sp1st7);
        setTextSize(R.id.sp1st8);
        setTextSize(R.id.sp1st9);
        setTextSize(R.id.sp2head);
        setTextSize(R.id.sp2st0);
        setTextSize(R.id.sp2st1);
        setTextSize(R.id.sp2st2);
        setTextSize(R.id.sp2st3);
        setTextSize(R.id.sp2st4);
        setTextSize(R.id.sp2st5);
        setTextSize(R.id.sp2st6);
        setTextSize(R.id.sp2st7);
        setTextSize(R.id.sp2st8);
        setTextSize(R.id.sp2st9);
        setTextSize(R.id.sum0);
        setTextSize(R.id.sum1);
        setTextSize(R.id.sum2);
        setTextSize(R.id.sum3);
        setTextSize(R.id.sum4);
        setTextSize(R.id.sum5);
        setTextSize(R.id.sum6);
        setTextSize(R.id.sum7);
        setTextSize(R.id.sum8);
        setTextSize(R.id.sum9);
        setTextSize(R.id.textImSkatIst1);
        setTextSize(R.id.textImSkatIst2);
        setTextSize(R.id.textImSkatIst3);
        setTextSize(R.id.textImSkatIst4);
        setDeselectedAndSize(R.id.buttonProtoListe);
        setDeselectedAndSize(R.id.buttonProtoOK);
        setDeselectedAndSize(R.id.buttonProtoPfeil);
        setText(R.id.sp0head, getTranslation(Translations.XT_Spieler));
        setText(R.id.sp1head, getTranslation(Translations.XT_Androido));
        setText(R.id.sp2head, getTranslation(Translations.XT_Androida));
        setText(R.id.buttonProtoListe, getTranslation(Translations.XT_Liste));
        setText(R.id.buttonProtoOK, getTranslation(Translations.XT_Weiter));

        setTextSize(R.id.li0head);
        setTextSize(R.id.li0sp0);
        setTextSize(R.id.li0sp1);
        setTextSize(R.id.li0sp2);
        setTextSize(R.id.li0sp3);
        setTextSize(R.id.li0sp4);
        setTextSize(R.id.li0sp5);
        setTextSize(R.id.li0sp6);
        setTextSize(R.id.li0sp7);
        setTextSize(R.id.li0sp8);
        setTextSize(R.id.li0sp9);
        setTextSize(R.id.li0foot);
        setTextSize(R.id.li1head);
        setTextSize(R.id.li1sp0);
        setTextSize(R.id.li1sp1);
        setTextSize(R.id.li1sp2);
        setTextSize(R.id.li1sp3);
        setTextSize(R.id.li1sp4);
        setTextSize(R.id.li1sp5);
        setTextSize(R.id.li1sp6);
        setTextSize(R.id.li1sp7);
        setTextSize(R.id.li1sp8);
        setTextSize(R.id.li1sp9);
        setTextSize(R.id.li1foot);
        setTextSize(R.id.li2head);
        setTextSize(R.id.li2sp0);
        setTextSize(R.id.li2sp1);
        setTextSize(R.id.li2sp2);
        setTextSize(R.id.li2sp3);
        setTextSize(R.id.li2sp4);
        setTextSize(R.id.li2sp5);
        setTextSize(R.id.li2sp6);
        setTextSize(R.id.li2sp7);
        setTextSize(R.id.li2sp8);
        setTextSize(R.id.li2sp9);
        setTextSize(R.id.li2foot);
        setTextSize(R.id.li3head);
        setTextSize(R.id.li3sp0);
        setTextSize(R.id.li3sp1);
        setTextSize(R.id.li3sp2);
        setTextSize(R.id.li3sp3);
        setTextSize(R.id.li3sp4);
        setTextSize(R.id.li3sp5);
        setTextSize(R.id.li3sp6);
        setTextSize(R.id.li3sp7);
        setTextSize(R.id.li3sp8);
        setTextSize(R.id.li3sp9);
        setTextSize(R.id.li3foot);
        setDeselectedAndSize(R.id.buttonListeOK);
        setDeselectedAndSize(R.id.buttonListeLoeschen);
        setText(R.id.li0head, getTranslation(Translations.XT_Spieler));
        setText(R.id.li1head, getTranslation(Translations.XT_Androido));
        setText(R.id.li2head, getTranslation(Translations.XT_Androida));
        setText(R.id.li3head, getTranslation(Translations.XT_Spiel));
        setText(R.id.li3foot, getTranslation(Translations.XT_GV));
        setText(R.id.buttonListeOK, getTranslation(Translations.XT_Weiter));
        setText(R.id.buttonListeLoeschen, getTranslation(Translations.XT_Loeschen));

        setTitleTextSize(R.id.dialogLoeschenL1);
        setTextSize(R.id.dialogLoeschenL2);
        setTextSize(R.id.dialogLoeschenL3);
        setDeselectedAndSize(R.id.buttonLoeschenJa);
        setDeselectedAndSize(R.id.buttonLoeschenNein);
        setText(R.id.dialogLoeschenL1, getTranslation(Translations.XT_Loesche));
        setText(R.id.dialogLoeschenL2, getTranslation(Translations.XT_Spielstand));
        setText(R.id.dialogLoeschenL3, getTranslation(Translations.XT_und_Liste));
        setText(R.id.buttonLoeschenJa, getTranslation(Translations.XT_Ja));
        setText(R.id.buttonLoeschenNein, getTranslation(Translations.XT_Nein));

        setTitleTextSize(R.id.dialogCP1);
        setTextSize(R.id.dialogCP2);
        setTextSize(R.id.dialogCP3);
        setTextSize(R.id.dialogCP4);
        setTextSize(R.id.dialogCP5);
        setDeselectedAndSize(R.id.buttonCopyrightOK);
        int flags = -1;
        try {
            flags = getPackageManager().getPackageInfo(getPackageName(), 0).applicationInfo.flags;
        } catch (NameNotFoundException ignore) {
        }
        setText(R.id.dialogCP1, "XSkat "
                + xskatVersion
                + (de.xskat.BuildConfig.DEBUG ? " beta" : "")
                + ((flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0 ? " debug"
                        : ""));
        String http = getTranslation(Translations.XT_xskat_de) + "\n" + getTranslation(Translations.XT_github_xskat);
        setText(R.id.dialogCP3, http);
        setText(R.id.dialogCP4, getTranslation(Translations.XT_Dieses_Programm_ist_freie_Software));
        setText(R.id.dialogCP5, getTranslation(Translations.XT_es_kann_frei_verbreitet_werden));
        setText(R.id.buttonCopyrightOK, getTranslation(Translations.XT_Weiter));

        setDeselectedAndSize(R.id.buttonNewGame);
        setTextSize(R.id.textSpielstaerke);
        setDeselectedAndSize(R.id.buttonStaerkeMM);
        setDeselectedAndSize(R.id.buttonStaerkeM);
        setDeselectedAndSize(R.id.buttonStaerke0);
        setDeselectedAndSize(R.id.buttonStaerkeP);
        setDeselectedAndSize(R.id.buttonStaerkePP);
        setTextSize(R.id.textAnimationSpeed);
        setDeselectedAndSize(R.id.buttonAnimL);
        setDeselectedAndSize(R.id.buttonAnim0);
        setDeselectedAndSize(R.id.buttonAnimS);
        setTextSize(R.id.textSprache);
        setDeselectedAndSize(R.id.buttonSpracheDE);
        setDeselectedAndSize(R.id.buttonSpracheEN);
        setDeselectedAndSize(R.id.buttonOptionsOK);
        setDeselectedAndSize(R.id.buttonOptionsListe);
        setText(R.id.buttonNewGame, getTranslation(Translations.XT_NeuesSpiel));
        setText(R.id.textSpielstaerke, getTranslation(Translations.XT_Spielstaerke));
        setText(R.id.textAnimationSpeed, getTranslation(Translations.XT_AnimationSpeed));
        setText(R.id.textSprache, getTranslation(Translations.XT_Sprache));
        setText(R.id.buttonOptionsOK, getTranslation(Translations.XT_Weiter));
        if (prot1.stiche[0][0] != 0 || prot1.stiche[0][1] != 0) {
            setText(R.id.buttonOptionsListe, getTranslation(Translations.XT_Protokoll));
        } else {
            setText(R.id.buttonOptionsListe, getTranslation(Translations.XT_Liste));
        }

        setTextSize(R.id.textBlatt);
        setDeselectedAndSize(R.id.buttonBlattTU);
        setDeselectedAndSize(R.id.buttonBlattFR);
        setDeselectedAndSize(R.id.buttonBlattDE);
        setDeselectedAndSize(R.id.buttonBlattBDK);
        setDeselectedAndSize(R.id.buttonBlattJQK);
        setDeselectedAndSize(R.id.buttonBlattUOK);
        setTextSize(R.id.textSortAltSeq);
        setDeselectedAndSize(R.id.buttonBlattSortA);
        setDeselectedAndSize(R.id.buttonBlattSortS);
        setDeselectedAndSize(R.id.buttonBlattOK);
        setDeselectedAndSize(R.id.buttonBlattListe);
        setText(R.id.textBlatt, getTranslation(Translations.XT_Blatt));
        setText(R.id.buttonBlattTU, getTranslation(Translations.XT_Turnier));
        setText(R.id.buttonBlattFR, getTranslation(Translations.XT_Franzoesisch));
        setText(R.id.buttonBlattDE, getTranslation(Translations.XT_Deutsch));
        setText(R.id.textSortAltSeq, getTranslation(Translations.XT_Sortierung));
        setText(R.id.buttonBlattSortA, getTranslation(Translations.XT_Alternierend));
        setText(R.id.buttonBlattSortS, getTranslation(Translations.XT_Sequentiell));
        setText(R.id.buttonBlattOK, getTranslation(Translations.XT_Weiter));
        setText(R.id.buttonBlattListe, getTranslation(Translations.XT_Liste));

        setTextSize(R.id.textVarianten);
        setTextSize(R.id.textRamsch);
        setDeselectedAndSize(R.id.buttonRamschNein);
        setDeselectedAndSize(R.id.buttonRamschJa);
        setDeselectedAndSize(R.id.buttonRamschImmer);
        setTextSize(R.id.textKontra);
        setDeselectedAndSize(R.id.buttonVarKontraNein);
        setDeselectedAndSize(R.id.buttonVarKontraJa);
        setDeselectedAndSize(R.id.buttonVarKontraAb18);
        setTextSize(R.id.textBock);
        setDeselectedAndSize(R.id.buttonBockNein);
        setDeselectedAndSize(R.id.buttonBockJa);
        setDeselectedAndSize(R.id.buttonBockRamsch);
        setTextSize(R.id.textSpitze);
        setDeselectedAndSize(R.id.buttonSpitzeNein);
        setDeselectedAndSize(R.id.buttonSpitzeJa);
        setDeselectedAndSize(R.id.buttonSpitzeZaehlt2);
        setDeselectedAndSize(R.id.buttonVariantenOK);
        setDeselectedAndSize(R.id.buttonVariantenFertig);
        setText(R.id.textVarianten, getTranslation(Translations.XT_Varianten));
        setText(R.id.textRamsch, getTranslation(Translations.XT_Ramsch));
        setText(R.id.buttonRamschNein, getTranslation(Translations.XT_Nein));
        setText(R.id.buttonRamschJa, getTranslation(Translations.XT_Ja));
        setText(R.id.buttonRamschImmer, getTranslation(Translations.XT_Immer));
        setText(R.id.textKontra, getTranslation(Translations.XT_Kontra));
        setText(R.id.buttonVarKontraNein, getTranslation(Translations.XT_Nein));
        setText(R.id.buttonVarKontraJa, getTranslation(Translations.XT_Ja));
        setText(R.id.buttonVarKontraAb18, getTranslation(Translations.XT_Ab_18));
        setText(R.id.textBock, getTranslation(Translations.XT_Bock));
        setText(R.id.buttonBockNein, getTranslation(Translations.XT_Nein));
        setText(R.id.buttonBockJa, getTranslation(Translations.XT_Ja));
        setText(R.id.buttonBockRamsch, getTranslation(Translations.XT_Plus_Ramsch));
        setText(R.id.textSpitze, getTranslation(Translations.XT_Spitze));
        setText(R.id.buttonSpitzeNein, getTranslation(Translations.XT_Nein));
        setText(R.id.buttonSpitzeJa, getTranslation(Translations.XT_Ja));
        setText(R.id.buttonSpitzeZaehlt2, getTranslation(Translations.XT_Zaehlt_2));
        setText(R.id.buttonVariantenOK, getTranslation(Translations.XT_Weiter));
        setText(R.id.buttonVariantenFertig, getTranslation(Translations.XT_Fertig));

        setTextSize(R.id.textSchieberamsch);
        setDeselectedAndSize(R.id.buttonSchieberamschNein);
        setDeselectedAndSize(R.id.buttonSchieberamschJa);
        setTextSize(R.id.textSkatGehtAn);
        setDeselectedAndSize(R.id.buttonLetztenStich);
        setDeselectedAndSize(R.id.buttonVerlierer);
        setDeselectedAndSize(R.id.buttonRamschVariantenOK);
        setDeselectedAndSize(R.id.buttonRamschVariantenFertig);
        setText(R.id.textSchieberamsch, getTranslation(Translations.XT_Schieberamsch));
        setText(R.id.buttonSchieberamschNein, getTranslation(Translations.XT_Nein));
        setText(R.id.buttonSchieberamschJa, getTranslation(Translations.XT_Ja));
        setText(R.id.textSkatGehtAn, getTranslation(Translations.XT_Skat_geht_an));
        setText(R.id.buttonLetztenStich, getTranslation(Translations.XT_letzten_Stich));
        setText(R.id.buttonVerlierer, getTranslation(Translations.XT_Verlierer));
        setText(R.id.buttonRamschVariantenOK, getTranslation(Translations.XT_Weiter));
        setText(R.id.buttonRamschVariantenFertig, getTranslation(Translations.XT_Fertig));

        setTextSize(R.id.textVorschlaege);
        setDeselectedAndSize(R.id.buttonVorschlaegeNein);
        setDeselectedAndSize(R.id.buttonVorschlaegeJa);
        setTextSize(R.id.textNimmStich);
        setTextSize(R.id.textSec);
        setDeselectedAndSize(R.id.buttonStichM);
        setDeselectedAndSize(R.id.buttonStichP);
        setDeselectedAndSize(R.id.buttonStichOK);
        setText(R.id.textVorschlaege, getTranslation(Translations.XT_Vorschlaege));
        setText(R.id.buttonVorschlaegeNein, getTranslation(Translations.XT_Nein));
        setText(R.id.buttonVorschlaegeJa, getTranslation(Translations.XT_Ja));
        setText(R.id.textNimmStich, getTranslation(Translations.XT_Nimm_Stich_nach));
        setText(R.id.buttonStichOK, getTranslation(Translations.XT_Weiter));
        initStichStr();

        setTitleTextSize(R.id.textWiederholen);
        setTextSize(R.id.textMitDenKartenVon);
        setDeselectedAndSize(R.id.buttonVonAndroido);
        setDeselectedAndSize(R.id.buttonVonMir);
        setDeselectedAndSize(R.id.buttonVonAndroida);
        setDeselectedAndSize(R.id.buttonWiederholenZurueck);
        setDeselectedAndSize(R.id.buttonWiederholenStart);
        setText(R.id.textWiederholen, getTranslation(Translations.XT_Spiel_wiederholen));
        setText(R.id.textMitDenKartenVon, getTranslation(Translations.XT_mit_den_Karten_von));
        setText(R.id.buttonVonAndroido, getTranslation(Translations.XT_Androido));
        setText(R.id.buttonVonMir, getTranslation(Translations.XT_mir));
        setText(R.id.buttonVonAndroida, getTranslation(Translations.XT_Androida));
        setText(R.id.buttonWiederholenZurueck, getTranslation(Translations.XT_Zurueck));
        setText(R.id.buttonWiederholenStart, getTranslation(Translations.XT_Start));

        setDeselectedAndSize(R.id.buttonComputerLeft);
        setDeselectedAndSize(R.id.buttonComputerRight);
        setDeselectedAndSize(R.id.button18);
        setDeselectedAndSize(R.id.buttonPasse);
        setDeselectedAndSize(R.id.buttonDruecken);
        setDeselectedAndSize(R.id.buttonFertig);
        setDeselectedAndSize(R.id.buttonWeiter);
        setTextSize(R.id.textHS1);
        setTextSize(R.id.textHS2);
        setText(R.id.buttonPasse, getTranslation(Translations.XT_Passe));
        setText(R.id.buttonDruecken, getTranslation(Translations.XT_Druecken));
        setText(R.id.buttonFertig, getTranslation(Translations.XT_Fertig));
        setText(R.id.buttonWeiter, getTranslation(Translations.XT_Weiter));
        setText(R.id.textHS1, getTranslation(Translations.XT_Display_irgendwo_antippen));
        setText(R.id.textHS2, getTranslation(Translations.XT_um_Stich_zu_entfernen));
        if (phase == REIZEN)
            initReizStr();
    }

    void setSelected(View view) {
        view.setTag(1);
        ((TextView) view).setTextColor(Color.WHITE);
        view.setBackgroundResource(R.drawable.buttonsel);
        view.invalidate();
    }

    void setSelected(int id) {
        setSelected(findViewById(id));
    }

    void setDeselected(View view) {
        view.setTag(null);
        ((TextView) view).setTextColor(Color.BLACK);
        view.setBackgroundResource(R.drawable.button);
        view.setPadding(0, 0, 0, 0);
        view.invalidate();
    }

    void setDeselected(int id) {
        setDeselected(findViewById(id));
    }

    void setDeselectedAndSize(int id) {
        if (!layedOut) {
            setDeselected(findViewById(id));
        }
        setTextSize(id);
    }

    void setText(int id, String str) {
        ((TextView) findViewById(id)).setText(str);
    }

    boolean isSelected(int id) {
        return findViewById(id).getTag() != null;
    }

    void setVisible(View view) {
        view.setVisibility(View.VISIBLE);
    }

    void setVisible(int id) {
        setVisible(findViewById(id));
    }

    void setInvisible(View view) {
        view.setVisibility(View.INVISIBLE);
    }

    void setInvisible(int id) {
        setInvisible(findViewById(id));
    }

    void setGone(int id) {
        findViewById(id).setVisibility(View.GONE);
    }

    void setDialogsGone() {
        setGone(R.id.dialogCopyright);
        setGone(R.id.dialogOptions);
        setGone(R.id.dialogBlatt);
        setGone(R.id.dialogVarianten);
        setGone(R.id.dialogRamschVarianten);
        setGone(R.id.dialogStich);
        setGone(R.id.dialogWiederholen);
    }

    void initHandStr() {
        setText(R.id.textGereiztHand, getTranslation(Translations.XT_Gereizt_bis) + reizValues[reizp]);
    }

    void initVerdoppeltStr() {
        setText(R.id.textWerSchiebt, spielerm == 1 ? getTranslation(Translations.XT_Androido)
                : getTranslation(Translations.XT_Androida));
        setText(R.id.textSchiebt, klopfm ? getTranslation(Translations.XT_klopft)
                : getTranslation(Translations.XT_nimmt_den_Skat_nicht_auf));
    }

    void initBubenStr() {
        String bb;
        switch (spBDK) {
        default:
            bb = getTranslation(Translations.XT_Buben);
            break;
        case 1:
            bb = getTranslation(Translations.XT_Jacks);
            break;
        case 2:
            bb = getTranslation(Translations.XT_Unter);
            break;
        }
        setText(R.id.textBuben, bb);
    }

    void initReizStr() {
        if (!saho && hoerer == 0)
            do_msaho(0, getTranslation(Translations.XT_Ja));
        if (saho && sager == 0)
            do_msaho(0, "" + reizValues[reizp]);
    }

    void initAnsageStr() {
        setText(R.id.textWerSpielt, spieler == 1 ? getTranslation(Translations.XT_Androido)
                : getTranslation(Translations.XT_Androida));
        setText(R.id.textSpieltWas, getTranslation(Translations.XT_spielt) + " " + gameName(trumpf)
                + (handsp ? " " + getTranslation(Translations.XT_Hand) : ""));
        setText(R.id.textFuer, getTranslation(Translations.XT_fuer) + " " + reizValues[reizp]);
    }

    void initKontraStr() {
        setText(R.id.textKontraWerSpielt, spieler == 1 ? getTranslation(Translations.XT_Androido)
                : getTranslation(Translations.XT_Androida));
        setText(R.id.textKontraSpieltWas, getTranslation(Translations.XT_spielt) + " "
                + gameName(trumpf) + (handsp ? " " + getTranslation(Translations.XT_Hand) : ""));
        setText(R.id.textKontraFuer, getTranslation(Translations.XT_fuer) + " " + reizValues[reizp]);
    }

    void initReKontraStr() {
        setText(R.id.textKontraVon, getTranslation(Translations.XT_von)
                + (kontram == 1 ? getTranslation(Translations.XT_Androido) : getTranslation(Translations.XT_Androida)));
    }

    void initKontraReStr() {
        setText(R.id.textKontraReWerSpielt, spieler == 1 ? getTranslation(Translations.XT_Androido)
                : getTranslation(Translations.XT_Androida));
        setText(R.id.textKontraReSpieltWas, getTranslation(Translations.XT_spielt) + " "
                + gameName(trumpf) + (handsp ? " " + getTranslation(Translations.XT_Hand) : ""));
        setText(R.id.textKontraReFuer, getTranslation(Translations.XT_fuer) + " " + reizValues[reizp]);
        setText(R.id.textMitKontraRe, getTranslation(Translations.XT_mit_Kontra)
                + (kontrastufe == 2 ? getTranslation(Translations.XT_und) + getTranslation(Translations.XT_Re) : ""));
    }

    void initSpielStr() {
        setText(R.id.textGereizt, getTranslation(Translations.XT_Gereizt_bis) + reizValues[reizp]);
    }

    void initStichStr() {
        String t = getTranslation(Translations.XT_Antippen);
        if (nimmstich[0][0] < 101) {
            t = nimmstich[0][0] / 100.0 + getTranslation(Translations.XT_Sekunden);
        }
        setText(R.id.textSec, t);
    }

    void initResultStr() {
        TextView v = (TextView) findViewById(R.id.textResultMsg);
        String s = (GameType.isRamsch(trumpf) ? (mes1 ? getTranslation(Translations.XT_Eine_Jungfrau)
                : mes2 ? getTranslation(Translations.XT_Durchmarsch) : "") : (mes1 ? getTranslation(Translations.XT_Ueberreizt)
                : mes2 ? getTranslation(Translations.XT_Gegner_nicht_Schneider)
                        : mes3 ? getTranslation(Translations.XT_Gegner_nicht_schwarz)
                                : mes4 ? getTranslation(Translations.XT_Spitze_verloren) : ""));
        v.setText(s);
        v.setTypeface(null, Typeface.BOLD);
        v = (TextView) findViewById(R.id.textResult);
        s = getTranslation(Translations.XT_Spieler);
        if (GameType.isRamsch(trumpf) && spwert == 0) {
            s = getTranslation(Translations.XT_Niemand);
        } else if (spieler > 0) {
            s = spieler == 1 ? getTranslation(Translations.XT_Androido) : getTranslation(Translations.XT_Androida);
        }
        v.setText(s);
        v = (TextView) findViewById(R.id.textGewVerl);
        s = spgew ? getTranslation(Translations.XT_gewinnt) : getTranslation(Translations.XT_verliert);
        v.setText(s);
        v.setTypeface(null, Typeface.BOLD);
        v = (TextView) findViewById(R.id.textMitAugen);
        if (GameType.isNullGame(trumpf)) {
            s = getTranslation(Translations.XT_das_Nullspiel);
        } else if (GameType.isRamsch(trumpf)) {
            s = getTranslation(Translations.XT_den_Ramsch);
        } else {
            if (stich == 1) {
                s = "";
            } else if ((spgew && schwz) || !nullv) {
                s = getTranslation(Translations.XT_schwarz);
            } else {
                s = getTranslation(Translations.XT_mit) + " " + stsum + " " + getTranslation(Translations.XT_Augen);
            }
        }
        s += " " + getTranslation(Translations.XT_Spielwert) + ": "
                + (spgew && (!GameType.isRamsch(trumpf) || stsum == 120) ? spwert : -spwert);
        v.setText(s);
        v = (TextView) findViewById(R.id.textGereiztBis);
        if (GameType.isRamsch(trumpf)) {
            v.setText("");
        } else {
            s = getTranslation(Translations.XT_Gereizt_bis) + reizValues[reizp];
            v.setText(s);
        }
    }

    void saveState() {
        editor.putBoolean("firstgame", firstgame);
        editor.putBoolean("gebenf", gebenf);
        editor.putBoolean("handsp", handsp);
        editor.putBoolean("karobubeanz", karobubeanz);
        editor.putBoolean("klopfen", klopfen);
        editor.putBoolean("klopfm", klopfm);
        editor.putBoolean("l2r", l2r);
        editor.putBoolean("mes1", mes1);
        editor.putBoolean("mes2", mes2);
        editor.putBoolean("mes3", mes3);
        editor.putBoolean("mes4", mes4);
        editor.putBoolean("ndichtw", ndichtw);
        editor.putBoolean("nullv", nullv);
        editor.putBoolean("oldrules", oldrules);
        editor.putBoolean("ouveang", ouveang);
        editor.putBoolean("resumebock", resumebock);
        editor.putBoolean("revolang", revolang);
        editor.putBoolean("revolution", revolution);
        editor.putBoolean("saho", saho);
        editor.putBoolean("schenken", schenken);
        editor.putBoolean("schnang", schnang);
        editor.putBoolean("schwang", schwang);
        editor.putBoolean("schwz", schwz);
        editor.putBoolean("skatopen", skatopen);
        editor.putBoolean("spgew", spgew);
        editor.putBoolean("spitzeang", spitzeang);
        editor.putBoolean("spitzeok", spitzeok);
        editor.putBoolean("spitzeopen", spitzeopen);
        editor.putBoolean("sptzmrk", sptzmrk);
        editor.putBoolean("trickl2r", trickl2r);
        editor.putBoolean("vorhandwn", vorhandwn);
        for (int i = 0; i < 4; i++)
            editor.putBoolean("aussplfb" + i, aussplfb[i]);
        for (int i = 0; i < 3; i++)
            editor.putBoolean("backopen" + i, backopen[i]);
        for (int i = 0; i < 3; i++)
            editor.putBoolean("ggdurchm" + i, ggdurchm[i]);
        for (int i = 0; i < 4; i++)
            editor.putBoolean("hattefb" + i, hattefb[i]);
        for (int i = 0; i < 3; i++)
            editor.putBoolean("hints" + i, hints[i]);
        for (int i = 0; i < 3; i++)
            editor.putBoolean("protsort" + i, protsort[i]);
        for (int i = 0; i < 3; i++)
            editor.putBoolean("rstich" + i, rstich[i]);
        for (int i = 0; i < 3; i++)
            editor.putBoolean("sagte18" + i, sagte18[i]);
        for (int i = 0; i < 4; i++)
            editor.putBoolean("wirftabfb" + i, wirftabfb[i]);
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 8; j++)
                editor.putBoolean("inhand" + i + "." + j, inhand[i][j]);
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 3; j++)
                editor.putBoolean("inhanddl" + i + "." + j, inhanddl[i][j]);
        editor.putInt("astsum", astsum);
        editor.putInt("ausspl", ausspl);
        editor.putInt("bockevents", bockevents);
        editor.putInt("bockinc", bockinc);
        editor.putInt("bockspiele", bockspiele);
        editor.putInt("butternok", butternok);
        editor.putInt("cardLeftBgr", cardLeftBgr);
        editor.putInt("cardRightBgr", cardRightBgr);
        editor.putInt("cardSkatLeftBgr", cardSkatLeftBgr);
        editor.putInt("cardSkatRightBgr", cardSkatRightBgr);
        editor.putInt("cardStichLeftBgr", cardStichLeftBgr);
        editor.putInt("cardStichMiddleBgr", cardStichMiddleBgr);
        editor.putInt("cardStichRightBgr", cardStichRightBgr);
        editor.putInt("drbut", drbut);
        editor.putInt("drkcd", drkcd);
        editor.putInt("geber", geber);
        editor.putInt("gedr", gedr);
        editor.putInt("gstsum", gstsum);
        editor.putInt("hoerer", hoerer);
        editor.putInt("kannspitze", kannspitze);
        editor.putInt("kontram", kontram);
        editor.putInt("kontrastufe", kontrastufe);
        editor.putInt("ktrnext", ktrnext);
        editor.putInt("ktrply", ktrply);
        editor.putInt("ktrsag", ktrsag);
        editor.putInt("lvmh", lvmh);
        editor.putInt("nspwert", nspwert);
        editor.putInt("numsp", numsp);
        editor.putInt("phase", phase);
        editor.putInt("pkoption", pkoption);
        editor.putInt("playbock", playbock);
        editor.putInt("playcd", playcd);
        editor.putInt("playkontra", playkontra);
        editor.putInt("playramsch", playramsch);
        editor.putInt("playsramsch", playsramsch);
        editor.putInt("playVMHLeftBgr", playVMHLeftBgr);
        editor.putInt("playVMHRightBgr", playVMHRightBgr);
        editor.putInt("possc", possc);
        editor.putInt("ramschspiele", ramschspiele);
        editor.putInt("reizp", reizp);
        editor.putInt("rotateby", rotateby);
        editor.putInt("rskatloser", rskatloser);
        editor.putInt("rskatsum", rskatsum);
        editor.putInt("sager", sager);
        editor.putInt("savseed", savseed);
        editor.putInt("schenknext", schenknext);
        editor.putInt("schenkply", schenkply);
        editor.putInt("schenkstufe", schenkstufe);
        editor.putInt("sp", sp);
        editor.putInt("spBlatt", spBlatt);
        editor.putInt("spBDK", spBDK);
        editor.putInt("spieler", spieler);
        editor.putInt("spielerm", spielerm);
        editor.putInt("spitzezaehlt", spitzezaehlt);
        editor.putInt("sptruempfe", sptruempfe);
        editor.putInt("spwert", spwert);
        editor.putInt("sramschstufe", sramschstufe);
        editor.putInt("stich", stich);
        editor.putInt("stichopen", stichopen);
        editor.putInt("stsum", stsum);
        editor.putInt("trumpf", trumpf);
        editor.putInt("umdrueck", umdrueck);
        editor.putInt("vmh", vmh);
        editor.putInt("verd1", verd1);
        editor.putInt("verd2", verd2);
        editor.putInt("wieder", wieder);
        for (int i = 0; i < 3; i++)
            editor.putInt("alist" + i, alist[i]);
        for (int i = 0; i < 3; i++)
            editor.putInt("alternate" + i, alternate[i]);
        for (int i = 0; i < 10; i++)
            editor.putInt("cardBgr" + i, cardBgr[i]);
        for (int i = 0; i < 32; i++)
            editor.putInt("cards" + i, cards[i]);
        for (int i = 0; i < 32; i++)
            editor.putInt("gespcd" + i, gespcd[i]);
        for (int i = 0; i < 4; i++)
            editor.putInt("gespfb" + i, gespfb[i]);
        for (int i = 0; i < 5; i++)
            editor.putInt("high" + i, high[i]);
        for (int i = 0; i < 2; i++)
            editor.putInt("hintcard" + i, hintcard[i]);
        for (int i = 0; i < 3; i++)
            editor.putInt("lastmsaho" + i, lastmsaho[i]);
        for (int i = 0; i < 3; i++)
            editor.putInt("maxrw" + i, maxrw[i]);
        for (int i = 0; i < 3; i++)
            editor.putInt("naussplfb" + i, naussplfb[i]);
        for (int i = 0; i < 4; i++)
            editor.putInt("nochinfb" + i, nochinfb[i]);
        for (int i = 0; i < 10; i++)
            editor.putInt("possi" + i, possi[i]);
        for (int i = 0; i < 3; i++)
            editor.putInt("rstsum" + i, rstsum[i]);
        for (int i = 0; i < 32; i++)
            editor.putInt("savecards" + i, savecards[i]);
        for (int i = 0; i < 2; i++)
            editor.putInt("seed" + i, seed[i]);
        for (int i = 0; i < 5; i++)
            editor.putInt("shigh" + i, shigh[i]);
        for (int i = 0; i < 3; i++)
            editor.putInt("sort1" + i, sort1[i]);
        for (int i = 0; i < 3; i++)
            editor.putInt("sort2" + i, sort2[i]);
        for (int i = 0; i < 12; i++)
            editor.putInt("spcards" + i, spcards[i]);
        for (int i = 0; i < 3; i++)
            editor.putInt("splfirst" + i, splfirst[i]);
        for (int i = 0; i < 3; i++)
            editor.putInt("stcd" + i, stcd[i]);
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 2; j++)
                editor.putInt("cgewoverl" + i + "." + j, cgewoverl[i][j]);
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 5; j++)
                editor.putInt("hatnfb" + i + "." + j, hatnfb[i][j]);
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 2; j++)
                editor.putInt("nimmstich" + i + "." + j, nimmstich[i][j]);
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                editor.putInt("prevsum" + i + "." + j, prevsum[i][j]);
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                editor.putInt("sum" + i + "." + j, sum[i][j]);
        prot1.save("prot1.");
        prot2.save("prot2.");
    }

    void readState() {
        gebenf = prefs.getBoolean("gebenf", false);
        handsp = prefs.getBoolean("handsp", false);
        karobubeanz = prefs.getBoolean("karobubeanz", false);
        klopfm = prefs.getBoolean("klopfm", false);
        l2r = prefs.getBoolean("l2r", false);
        mes1 = prefs.getBoolean("mes1", false);
        mes2 = prefs.getBoolean("mes2", false);
        mes3 = prefs.getBoolean("mes3", false);
        mes4 = prefs.getBoolean("mes4", false);
        ndichtw = prefs.getBoolean("ndichtw", false);
        nullv = prefs.getBoolean("nullv", false);
        oldrules = prefs.getBoolean("oldrules", false);
        ouveang = prefs.getBoolean("ouveang", false);
        resumebock = prefs.getBoolean("resumebock", false);
        revolang = prefs.getBoolean("revolang", false);
        revolution = prefs.getBoolean("revolution", false);
        saho = prefs.getBoolean("saho", false);
        schenken = prefs.getBoolean("schenken", false);
        schnang = prefs.getBoolean("schnang", false);
        schwang = prefs.getBoolean("schwang", false);
        schwz = prefs.getBoolean("schwz", false);
        skatopen = prefs.getBoolean("skatopen", false);
        spgew = prefs.getBoolean("spgew", false);
        spitzeang = prefs.getBoolean("spitzeang", false);
        spitzeok = prefs.getBoolean("spitzeok", false);
        spitzeopen = prefs.getBoolean("spitzeopen", false);
        sptzmrk = prefs.getBoolean("sptzmrk", false);
        trickl2r = prefs.getBoolean("trickl2r", false);
        vorhandwn = prefs.getBoolean("vorhandwn", false);
        for (int i = 0; i < 4; i++)
            aussplfb[i] = prefs.getBoolean("aussplfb" + i, false);
        for (int i = 0; i < 3; i++)
            backopen[i] = prefs.getBoolean("backopen" + i, false);
        for (int i = 0; i < 3; i++)
            ggdurchm[i] = prefs.getBoolean("ggdurchm" + i, false);
        for (int i = 0; i < 4; i++)
            hattefb[i] = prefs.getBoolean("hattefb" + i, false);
        for (int i = 0; i < 3; i++)
            protsort[i] = prefs.getBoolean("protsort" + i, false);
        for (int i = 0; i < 3; i++)
            rstich[i] = prefs.getBoolean("rstich" + i, false);
        for (int i = 0; i < 3; i++)
            sagte18[i] = prefs.getBoolean("sagte18" + i, false);
        for (int i = 0; i < 4; i++)
            wirftabfb[i] = prefs.getBoolean("wirftabfb" + i, false);
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 8; j++)
                inhand[i][j] = prefs.getBoolean("inhand" + i + "." + j, false);
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 3; j++)
                inhanddl[i][j] = prefs.getBoolean("inhanddl" + i + "." + j,
                        false);
        astsum = prefs.getInt("astsum", 0);
        ausspl = prefs.getInt("ausspl", 0);
        bockevents = prefs.getInt("bockevents", 0);
        bockinc = prefs.getInt("bockinc", 0);
        bockspiele = prefs.getInt("bockspiele", 0);
        butternok = prefs.getInt("butternok", 0);
        cardLeftBgr = prefs.getInt("cardLeftBgr", 0);
        cardRightBgr = prefs.getInt("cardRightBgr", 0);
        cardSkatLeftBgr = prefs.getInt("cardSkatLeftBgr", 0);
        cardSkatRightBgr = prefs.getInt("cardSkatRightBgr", 0);
        cardStichLeftBgr = prefs.getInt("cardStichLeftBgr", 0);
        cardStichMiddleBgr = prefs.getInt("cardStichMiddleBgr", 0);
        cardStichRightBgr = prefs.getInt("cardStichRightBgr", 0);
        drbut = prefs.getInt("drbut", 0);
        drkcd = prefs.getInt("drkcd", 0);
        geber = prefs.getInt("geber", 0);
        gedr = prefs.getInt("gedr", 0);
        gstsum = prefs.getInt("gstsum", 0);
        hoerer = prefs.getInt("hoerer", 0);
        kannspitze = prefs.getInt("kannspitze", 0);
        kontram = prefs.getInt("kontram", 0);
        kontrastufe = prefs.getInt("kontrastufe", 0);
        ktrnext = prefs.getInt("ktrnext", 0);
        ktrply = prefs.getInt("ktrply", 0);
        ktrsag = prefs.getInt("ktrsag", 0);
        lvmh = prefs.getInt("lvmh", 0);
        nspwert = prefs.getInt("nspwert", 0);
        numsp = prefs.getInt("numsp", 0);
        phase = prefs.getInt("phase", 0);
        pkoption = prefs.getInt("pkoption", 0);
        playbock = prefs.getInt("playbock", 0);
        playcd = prefs.getInt("playcd", 0);
        playVMHLeftBgr = prefs.getInt("playVMHLeftBgr", 0);
        playVMHRightBgr = prefs.getInt("playVMHRightBgr", 0);
        possc = prefs.getInt("possc", 0);
        ramschspiele = prefs.getInt("ramschspiele", 0);
        reizp = prefs.getInt("reizp", 0);
        rotateby = prefs.getInt("rotateby", 0);
        rskatsum = prefs.getInt("rskatsum", 0);
        sager = prefs.getInt("sager", 0);
        savseed = prefs.getInt("savseed", 0);
        schenknext = prefs.getInt("schenknext", 0);
        schenkply = prefs.getInt("schenkply", 0);
        schenkstufe = prefs.getInt("schenkstufe", 0);
        sp = prefs.getInt("sp", 0);
        spieler = prefs.getInt("spieler", 0);
        spielerm = prefs.getInt("spielerm", 0);
        spitzezaehlt = prefs.getInt("spitzezaehlt", 0);
        sptruempfe = prefs.getInt("sptruempfe", 0);
        spwert = prefs.getInt("spwert", 0);
        sramschstufe = prefs.getInt("sramschstufe", 0);
        stich = prefs.getInt("stich", 0);
        stichopen = prefs.getInt("stichopen", 0);
        stsum = prefs.getInt("stsum", 0);
        trumpf = prefs.getInt("trumpf", 0);
        umdrueck = prefs.getInt("umdrueck", 0);
        vmh = prefs.getInt("vmh", 0);
        verd1 = prefs.getInt("verd1", 0);
        verd2 = prefs.getInt("verd2", 0);
        wieder = prefs.getInt("wieder", 0);
        for (int i = 0; i < 3; i++)
            alist[i] = prefs.getInt("alist" + i, 0);
        for (int i = 0; i < 10; i++)
            cardBgr[i] = prefs.getInt("cardBgr" + i, 0);
        for (int i = 0; i < 32; i++)
            cards[i] = prefs.getInt("cards" + i, 0);
        for (int i = 0; i < 32; i++)
            gespcd[i] = prefs.getInt("gespcd" + i, 0);
        for (int i = 0; i < 4; i++)
            gespfb[i] = prefs.getInt("gespfb" + i, 0);
        for (int i = 0; i < 5; i++)
            high[i] = prefs.getInt("high" + i, 0);
        for (int i = 0; i < 2; i++)
            hintcard[i] = prefs.getInt("hintcard" + i, 0);
        for (int i = 0; i < 3; i++)
            lastmsaho[i] = prefs.getInt("lastmsaho" + i, 0);
        for (int i = 0; i < 3; i++)
            maxrw[i] = prefs.getInt("maxrw" + i, 0);
        for (int i = 0; i < 3; i++)
            naussplfb[i] = prefs.getInt("naussplfb" + i, 0);
        for (int i = 0; i < 4; i++)
            nochinfb[i] = prefs.getInt("nochinfb" + i, 0);
        for (int i = 0; i < 10; i++)
            possi[i] = prefs.getInt("possi" + i, 0);
        for (int i = 0; i < 3; i++)
            rstsum[i] = prefs.getInt("rstsum" + i, 0);
        for (int i = 0; i < 32; i++)
            savecards[i] = prefs.getInt("savecards" + i, 0);
        for (int i = 0; i < 2; i++)
            seed[i] = prefs.getInt("seed" + i, 0);
        for (int i = 0; i < 5; i++)
            shigh[i] = prefs.getInt("shigh" + i, 0);
        for (int i = 0; i < 3; i++)
            sort1[i] = prefs.getInt("sort1" + i, 0);
        for (int i = 0; i < 3; i++)
            sort2[i] = prefs.getInt("sort2" + i, 0);
        for (int i = 0; i < 12; i++)
            spcards[i] = prefs.getInt("spcards" + i, 0);
        for (int i = 0; i < 3; i++)
            splfirst[i] = prefs.getInt("splfirst" + i, 0);
        for (int i = 0; i < 3; i++)
            stcd[i] = prefs.getInt("stcd" + i, 0);
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 2; j++)
                cgewoverl[i][j] = prefs.getInt("cgewoverl" + i + "." + j, 0);
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 5; j++)
                hatnfb[i][j] = prefs.getInt("hatnfb" + i + "." + j, 0);
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                prevsum[i][j] = prefs.getInt("prevsum" + i + "." + j, 0);
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                sum[i][j] = prefs.getInt("sum" + i + "." + j, 0);
        prot1.read("prot1.");
        prot2.read("prot2.");
        if (phase == LETZTERSTICH)
            phase = SPIELEN;
    }

    void saveViews() {
        editor.putInt("v.dialogDesk", findViewById(R.id.dialogDesk)
                .getVisibility());
        editor.putInt("v.buttonComputerLeft",
                findViewById(R.id.buttonComputerLeft).getVisibility());
        editor.putInt("v.buttonComputerRight",
                findViewById(R.id.buttonComputerRight).getVisibility());
        editor.putInt("v.dialogAnsage", findViewById(R.id.dialogAnsage)
                .getVisibility());
        editor.putInt("v.dialogBuben", findViewById(R.id.dialogBuben)
                .getVisibility());
        editor.putInt("v.dialogFehler", findViewById(R.id.dialogFehler)
                .getVisibility());
        editor.putInt("v.dialogHand", findViewById(R.id.dialogHand)
                .getVisibility());
        editor.putInt("v.dialogKlopfen", findViewById(R.id.dialogKlopfen)
                .getVisibility());
        editor.putInt("v.dialogKontra", findViewById(R.id.dialogKontra)
                .getVisibility());
        editor.putInt("v.dialogKontraRe", findViewById(R.id.dialogKontraRe)
                .getVisibility());
        editor.putInt("v.dialogReKontra", findViewById(R.id.dialogReKontra)
                .getVisibility());
        editor.putInt("v.dialogResult", findViewById(R.id.dialogResult)
                .getVisibility());
        editor.putInt("v.dialogSchieben", findViewById(R.id.dialogSchieben)
                .getVisibility());
        editor.putInt("v.dialogSpielen", findViewById(R.id.dialogSpielen)
                .getVisibility());
        editor.putInt("v.dialogUeberreizt", findViewById(R.id.dialogUeberreizt)
                .getVisibility());
        editor.putInt("v.dialogVerdoppelt", findViewById(R.id.dialogVerdoppelt)
                .getVisibility());
        editor.putInt("v.box18", findViewById(R.id.box18).getVisibility());
        editor.putInt("v.boxPasse", findViewById(R.id.boxPasse).getVisibility());
        editor.putInt("v.boxDruecken", findViewById(R.id.boxDruecken)
                .getVisibility());
        editor.putInt("v.boxFertig", findViewById(R.id.boxFertig)
                .getVisibility());
        editor.putInt("v.boxWeiter", findViewById(R.id.boxWeiter)
                .getVisibility());
        editor.putInt("v.boxHinweisStich", findViewById(R.id.boxHinweisStich)
                .getVisibility());
        editor.putInt("v.box18passeL", findViewById(R.id.box18passeL)
                .getVisibility());
        editor.putInt("v.box18passeR", findViewById(R.id.box18passeR)
                .getVisibility());
        editor.putInt("v.rowSkat", findViewById(R.id.rowSkat).getVisibility());
        editor.putInt("v.rowStich", findViewById(R.id.rowStich).getVisibility());
        editor.putInt("v.row18Passe", findViewById(R.id.row18Passe)
                .getVisibility());
        editor.putInt("v.cardStichLeft", findViewById(R.id.cardStichLeft)
                .getVisibility());
        editor.putInt("v.cardStichMiddle", findViewById(R.id.cardStichMiddle)
                .getVisibility());
        editor.putInt("v.cardStichRight", findViewById(R.id.cardStichRight)
                .getVisibility());
        editor.putString("t.buttonComputerLeft",
                ((TextView) findViewById(R.id.buttonComputerLeft)).getText()
                        .toString());
        editor.putString("t.buttonComputerRight",
                ((TextView) findViewById(R.id.buttonComputerRight)).getText()
                        .toString());
    }

    void renderCards() {
        drawnCard = new LayerDrawable[34];
        Drawable[] layers = new Drawable[1];
        layers[0] = getResources().getDrawable(R.drawable.none);
        drawnCard[0] = new LayerDrawable(layers);
        layers = new Drawable[1];
        layers[0] = getResources().getDrawable(R.drawable.bgr);
        drawnCard[1] = new LayerDrawable(layers);
        for (int i = 0; i < 32; i++) {
            layers = new Drawable[3];
            int ob, un;
            switch (spBlatt) {
            case 1:
                un = cardNameUFR[i / 8];
                break;
            case 2:
                un = cardNameUDE[i / 8];
                break;
            default:
                un = cardNameUTU[i / 8];
                break;
            }
            switch (spBDK) {
            case 1:
                ob = spBlatt == 1 ? cardNameZFREN[i] : cardNameZTUEN[i];
                break;
            case 2:
                ob = spBlatt == 1 ? cardNameZDEFR[i] : cardNameZDETU[i];
                break;
            default:
                ob = spBlatt == 1 ? cardNameZFRDE[i] : cardNameZTUDE[i];
                break;
            }
            layers[0] = getResources().getDrawable(ob);
            layers[1] = getResources().getDrawable(un);
            layers[2] = getResources().getDrawable(R.drawable.hint);
            layers[2].mutate().setAlpha(0);
            drawnCard[i + 2] = new LayerDrawable(layers);
        }
    }

    void setBackgroundDrawable(View v, Drawable d) {
        v.setBackgroundDrawable(d);
    }

    void drawAllCards() {
        for (int i = 0; i < 10; i++)
            setBackgroundDrawable(findViewById(R.id.card0 + i),
                    cardName(cardBgr[i] + 2));
        setBackgroundDrawable(findViewById(R.id.cardLeft),
                cardName(cardLeftBgr + 2));
        setBackgroundDrawable(findViewById(R.id.cardRight),
                cardName(cardRightBgr + 2));
        findViewById(R.id.playVMHLeft).setBackgroundResource(
                gameSymb(playVMHLeftBgr));
        findViewById(R.id.playVMHRight).setBackgroundResource(
                gameSymb(playVMHRightBgr));
        setBackgroundDrawable(findViewById(R.id.cardSkatLeft),
                cardName(cardSkatLeftBgr + 2));
        setBackgroundDrawable(findViewById(R.id.cardSkatRight),
                cardName(cardSkatRightBgr + 2));
        setBackgroundDrawable(findViewById(R.id.cardStichLeft),
                cardName(cardStichLeftBgr + 2));
        setBackgroundDrawable(findViewById(R.id.cardStichMiddle),
                cardName(cardStichMiddleBgr + 2));
        setBackgroundDrawable(findViewById(R.id.cardStichRight),
                cardName(cardStichRightBgr + 2));
        restore_hints();
    }

    void readViews() {
        findViewById(R.id.dialogDesk).setVisibility(
                prefs.getInt("v.dialogDesk", View.VISIBLE));
        findViewById(R.id.buttonComputerLeft).setVisibility(
                prefs.getInt("v.buttonComputerLeft", View.VISIBLE));
        findViewById(R.id.buttonComputerRight).setVisibility(
                prefs.getInt("v.buttonComputerRight", View.VISIBLE));
        findViewById(R.id.dialogAnsage).setVisibility(
                prefs.getInt("v.dialogAnsage", View.VISIBLE));
        findViewById(R.id.dialogBuben).setVisibility(
                prefs.getInt("v.dialogBuben", View.VISIBLE));
        findViewById(R.id.dialogFehler).setVisibility(
                prefs.getInt("v.dialogFehler", View.VISIBLE));
        findViewById(R.id.dialogHand).setVisibility(
                prefs.getInt("v.dialogHand", View.VISIBLE));
        findViewById(R.id.dialogKlopfen).setVisibility(
                prefs.getInt("v.dialogKlopfen", View.VISIBLE));
        findViewById(R.id.dialogKontra).setVisibility(
                prefs.getInt("v.dialogKontra", View.VISIBLE));
        findViewById(R.id.dialogKontraRe).setVisibility(
                prefs.getInt("v.dialogKontraRe", View.VISIBLE));
        findViewById(R.id.dialogReKontra).setVisibility(
                prefs.getInt("v.dialogReKontra", View.VISIBLE));
        findViewById(R.id.dialogResult).setVisibility(
                prefs.getInt("v.dialogResult", View.VISIBLE));
        findViewById(R.id.dialogSchieben).setVisibility(
                prefs.getInt("v.dialogSchieben", View.VISIBLE));
        findViewById(R.id.dialogSpielen).setVisibility(
                prefs.getInt("v.dialogSpielen", View.VISIBLE));
        findViewById(R.id.dialogUeberreizt).setVisibility(
                prefs.getInt("v.dialogUeberreizt", View.VISIBLE));
        findViewById(R.id.dialogVerdoppelt).setVisibility(
                prefs.getInt("v.dialogVerdoppelt", View.VISIBLE));
        findViewById(R.id.box18).setVisibility(
                prefs.getInt("v.box18", View.VISIBLE));
        findViewById(R.id.boxPasse).setVisibility(
                prefs.getInt("v.boxPasse", View.VISIBLE));
        findViewById(R.id.boxDruecken).setVisibility(
                prefs.getInt("v.boxDruecken", View.VISIBLE));
        findViewById(R.id.boxFertig).setVisibility(
                prefs.getInt("v.boxFertig", View.VISIBLE));
        findViewById(R.id.boxWeiter).setVisibility(
                prefs.getInt("v.boxWeiter", View.VISIBLE));
        findViewById(R.id.boxHinweisStich).setVisibility(
                prefs.getInt("v.boxHinweisStich", View.VISIBLE));
        findViewById(R.id.box18passeL).setVisibility(
                prefs.getInt("v.box18passeL", View.VISIBLE));
        findViewById(R.id.box18passeR).setVisibility(
                prefs.getInt("v.box18passeR", View.VISIBLE));
        findViewById(R.id.rowSkat).setVisibility(
                prefs.getInt("v.rowSkat", View.VISIBLE));
        findViewById(R.id.rowStich).setVisibility(
                prefs.getInt("v.rowStich", View.VISIBLE));
        findViewById(R.id.row18Passe).setVisibility(
                prefs.getInt("v.row18Passe", View.VISIBLE));
        findViewById(R.id.cardStichLeft).setVisibility(
                prefs.getInt("v.cardStichLeft", View.VISIBLE));
        findViewById(R.id.cardStichMiddle).setVisibility(
                prefs.getInt("v.cardStichMiddle", View.VISIBLE));
        findViewById(R.id.cardStichRight).setVisibility(
                prefs.getInt("v.cardStichRight", View.VISIBLE));
        ((TextView) findViewById(R.id.buttonComputerLeft)).setText(prefs
                .getString("t.buttonComputerLeft", ""));
        ((TextView) findViewById(R.id.buttonComputerRight)).setText(prefs
                .getString("t.buttonComputerRight", ""));
        setGone(R.id.dialogProto);
        setGone(R.id.dialogListe);
        setGone(R.id.dialogLoeschen);
        setDialogsGone();
        setGone(R.id.dialogScreen);
        setVisible(R.id.mainScreen);
        drawAllCards();
        if (findViewById(R.id.dialogResult).getVisibility() == View.VISIBLE)
            di_result(bockinc);
        if (findViewById(R.id.dialogSpielen).getVisibility() == View.VISIBLE)
            initSpiel();
    }

    // --------------------------------------------------------------------------------------
    // File skat.h
    // --------------------------------------------------------------------------------------

    boolean firstgame;
    boolean gebenf;
    boolean handsp;
    boolean karobubeanz;
    boolean klopfen;
    boolean klopfm;
    boolean l2r;
    boolean mes1;
    boolean mes2;
    boolean mes3;
    boolean mes4;
    boolean ndichtw;
    boolean nullv;
    boolean oldrules;
    boolean ouveang;
    boolean resumebock;
    boolean revolang;
    boolean revolution;
    boolean saho;
    boolean schenken;
    boolean schnang;
    boolean schwang;
    boolean schwz;
    boolean skatopen;
    boolean spgew;
    boolean spitzeang;
    boolean spitzeok;
    boolean spitzeopen;
    boolean sptzmrk;
    boolean trickl2r = false;
    boolean vorhandwn;
    boolean[] aussplfb = new boolean[4];
    boolean[] backopen = new boolean[3];
    boolean[] ggdurchm = new boolean[3];
    boolean[] hattefb = new boolean[4];
    boolean[] hints = new boolean[3];
    boolean[] protsort = new boolean[3];
    boolean[] rstich = new boolean[3];
    boolean[] sagte18 = new boolean[3];
    boolean[] wirftabfb = new boolean[4];
    boolean[][] inhand = new boolean[4][8];
    boolean[][] inhanddl = new boolean[4][3];
    int astsum;
    int ausspl;
    int bockevents;
    int bockinc;
    int bockspiele;
    int butternok;
    int cardLeftBgr;
    int cardRightBgr;
    int cardSkatLeftBgr;
    int cardSkatRightBgr;
    int cardStichLeftBgr;
    int cardStichMiddleBgr;
    int cardStichRightBgr;
    int drbut;
    int drkcd;
    int geber;
    int gedr;
    int gstsum;
    int hoerer;
    int kannspitze;
    int kontram;
    int kontrastufe;
    int ktrnext;
    int ktrply;
    int ktrsag;
    int lvmh;
    int nspwert;
    int numsp;
    int phase;
    int pkoption;
    int playbock;
    int playcd;
    int playkontra;
    int playramsch;
    int playsramsch;
    int playVMHLeftBgr;
    int playVMHRightBgr;
    int possc;
    int ramschspiele;
    int reizp;
    int rotateby;
    int rskatloser;
    int rskatsum;
    int sager;
    int savseed;
    int schenknext;
    int schenkply;
    int schenkstufe;
    int sp;
    int spBlatt;
    int spBDK;
    int spieler;
    int spielerm;
    int spitzezaehlt;
    int sptruempfe;
    int spwert;
    int sramschstufe;
    int stich;
    int stichopen;
    int stsum;
    int trumpf;
    int umdrueck;
    int verd1;
    int verd2;
    int vmh;
    int wieder;
    int[] alist = new int[3];
    int[] alternate = new int[3];
    int[] cardBgr = new int[10];
    int[] cards = new int[32];
    int[] gespcd = new int[32];
    int[] gespfb = new int[4];
    int[] high = new int[5];
    int[] hintcard = new int[2];
    int[] lastmsaho = new int[3];
    int[] maxrw = new int[3];
    int[] naussplfb = new int[3];
    int[] nochinfb = new int[4];
    int[] possi = new int[10];
    int[] rstsum = new int[3];
    int[] savecards = new int[32];
    int[] seed = new int[2];
    int[] shigh = new int[5];
    int[] sort1 = new int[3];
    int[] sort2 = new int[3];
    int[] spcards = new int[12];
    int[] splfirst = new int[3];
    int[] stcd = new int[3];
    int[][] cgewoverl = new int[3][2];
    int[][] hatnfb = new int[3][5];
    int[][] nimmstich = new int[3][2];
    int[][] prevsum = new int[3][3];
    int[][] sum = new int[3][3];

    class structprot {
        boolean gewonn, handsp, spitze, revolution;
        int stichgem, spieler;
        // TODO substitute with de.xskat.data.GameType
        int trumpf; // -1=Null, 0=Diamonds, 1=Hearts, 4=Grand, 5=Ramsch
        int gereizt, augen, spwert, ehsso, sramsch,
                rotateby, schenken, savseed;
        int[] anspiel = new int[10], gemacht = new int[10],
                verdopp = new int[3];
        int[][] stiche = new int[10][3], skat = new int[4][2];

        void assign(structprot p) {
            gewonn = p.gewonn;
            handsp = p.handsp;
            spitze = p.spitze;
            revolution = p.revolution;
            stichgem = p.stichgem;
            spieler = p.spieler;
            trumpf = p.trumpf;
            gereizt = p.gereizt;
            augen = p.augen;
            spwert = p.spwert;
            ehsso = p.ehsso;
            sramsch = p.sramsch;
            rotateby = p.rotateby;
            schenken = p.schenken;
            savseed = p.savseed;
            anspiel = new int[10];
            System.arraycopy(p.anspiel, 0, anspiel, 0, 10);
            gemacht = new int[10];
            System.arraycopy(p.gemacht, 0, gemacht, 0, 10);
            verdopp = new int[3];
            System.arraycopy(p.verdopp, 0, verdopp, 0, 3);
            stiche = new int[10][3];
            for (int i = 0; i < 10; i++)
                System.arraycopy(p.stiche[i], 0, stiche[i], 0, 3);
            skat = new int[4][2];
            for (int i = 0; i < 4; i++)
                System.arraycopy(p.skat[i], 0, skat[i], 0, 2);
        }

        void save(String p) {
            editor.putBoolean(p + "gewonn", gewonn);
            editor.putBoolean(p + "handsp", handsp);
            editor.putBoolean(p + "spitze", spitze);
            editor.putBoolean(p + "revolution", revolution);
            editor.putInt(p + "stichgem", stichgem);
            editor.putInt(p + "spieler", spieler);
            editor.putInt(p + "trumpf", trumpf);
            editor.putInt(p + "gereizt", gereizt);
            editor.putInt(p + "augen", augen);
            editor.putInt(p + "spwert", spwert);
            editor.putInt(p + "ehsso", ehsso);
            editor.putInt(p + "sramsch", sramsch);
            editor.putInt(p + "rotateby", rotateby);
            editor.putInt(p + "schenken", schenken);
            editor.putInt(p + "savseed", savseed);
            for (int i = 0; i < 10; i++)
                editor.putInt(p + "anspiel" + i, anspiel[i]);
            for (int i = 0; i < 10; i++)
                editor.putInt(p + "gemacht" + i, gemacht[i]);
            for (int i = 0; i < 3; i++)
                editor.putInt(p + "verdopp" + i, verdopp[i]);
            for (int i = 0; i < 10; i++)
                for (int j = 0; j < 3; j++)
                    editor.putInt(p + "stiche" + i + "." + j, stiche[i][j]);
            for (int i = 0; i < 4; i++)
                for (int j = 0; j < 2; j++)
                    editor.putInt(p + "skat" + i + "." + j, skat[i][j]);
        }

        void read(String p) {
            gewonn = prefs.getBoolean(p + "gewonn", false);
            handsp = prefs.getBoolean(p + "handsp", false);
            spitze = prefs.getBoolean(p + "spitze", false);
            revolution = prefs.getBoolean(p + "revolution", false);
            stichgem = prefs.getInt(p + "stichgem", 0);
            spieler = prefs.getInt(p + "spieler", 0);
            trumpf = prefs.getInt(p + "trumpf", 0);
            gereizt = prefs.getInt(p + "gereizt", 0);
            augen = prefs.getInt(p + "augen", 0);
            spwert = prefs.getInt(p + "spwert", 0);
            ehsso = prefs.getInt(p + "ehsso", 0);
            sramsch = prefs.getInt(p + "sramsch", 0);
            rotateby = prefs.getInt(p + "rotateby", 0);
            schenken = prefs.getInt(p + "schenken", 0);
            savseed = prefs.getInt(p + "savseed", 0);
            for (int i = 0; i < 10; i++)
                anspiel[i] = prefs.getInt(p + "anspiel" + i, 0);
            for (int i = 0; i < 10; i++)
                gemacht[i] = prefs.getInt(p + "gemacht" + i, 0);
            for (int i = 0; i < 3; i++)
                verdopp[i] = prefs.getInt(p + "verdopp" + i, 0);
            for (int i = 0; i < 10; i++)
                for (int j = 0; j < 3; j++)
                    stiche[i][j] = prefs.getInt(p + "stiche" + i + "." + j, 0);
            for (int i = 0; i < 4; i++)
                for (int j = 0; j < 2; j++)
                    skat[i][j] = prefs.getInt(p + "skat" + i + "." + j, 0);

        }
    }

    structprot prot1 = new structprot(), prot2 = new structprot();

    int splstp;
    int currLang;
    int[] strateg = new int[3];
    int animSpeed = 0;
    int[][] sgewoverl = new int[3][2];
    int[][] splsum = new int[3][3];

    static class structsplist {
        int s, e;
        boolean r, d, g;
    }

    final int LIST_LEN = 9;
    structsplist[] splist = new structsplist[LIST_LEN];

    final int LUSCHE = -3;
    final int DAMELUSCHE = -2;
    final int IGNO = -1;
    final int AS = 0;
    final int ZEHN = 1;
    final int KOENIG = 2;
    final int DAME = 3;
    final int BUBE = 4;
    final int NEUN = 5;
    final int ACHT = 6;
    final int SIEBEN = 7;

    final int GEBEN = 0;
    final int REIZEN = 1;
    final int HANDSPIEL = 2;
    final int DRUECKEN = 3;
    final int ANSAGEN = 4;
    final int REVOLUTION = 5;
    final int SPIELEN = 6;
    final int SCHENKEN = 7;
    final int NIMMSTICH = 8;
    final int SPIELDICHT = 9;
    final int WEITER = 10;
    final int RESULT = 11;
    final int LETZTERSTICH = 12;

    final int BOCK_BEI_60 = 1;
    final int BOCK_BEI_GRANDHAND = 2;
    final int BOCK_BEI_KONTRA = 4;
    final int BOCK_BEI_RE = 8;
    final int BOCK_BEI_NNN = 16;
    final int BOCK_BEI_N00 = 32;
    final int BOCK_BEI_72 = 64;
    final int BOCK_BEI_96 = 128;
    final int BOCK_BEI_LAST = 128;

    final int D_skatx = 0;
    final int D_skaty = 1;
    final int D_cardw = 1;
    final int D_cardh = 1;
    final int D_cardx = 1;
    final int D_playx = 0;
    final int D_playy = 2;
    final int D_stichx = 2;
    final int D_stichy = 1;
    final int D_com1x = 0;
    final int D_com1y = 0;
    final int D_com2x = 1;
    final int D_com2y = 0;

    // Array containing the possible bid values for Null games: standard=23, hand=35, ouvert=46,
    // hand&ouvert=59, Revolution=92
    final int[] nullw = { 23, 35, 46, 59, 92 };
    // Array containg the base values for the five default games: Diamonds, Hearts, Spades, Clubs, Grand
    final int[] reizBaseValues = { 9, 10, 11, 12, 24 };
    // Array containing all possible bid values in ascending order
    final int[] reizValues = { 18, 20, 22, 23, 24, 27, 30, 33, 35, 36, 40, 44, 45,
            46, 48, 50, 54, 55, 59, 60, 63, 66, 70, 72, 77, 80, 81, 84, 88, 90,
            96, 99, 100, 108, 110, 117, 120, 121, 126, 130, 132, 135, 140, 143,
            144, 150, 153, 154, 156, 160, 162, 165, 168, 170, 171, 176, 180,
            187, 189, 190, 192, 198, 200, 204, 207, 209, 210, 216, 220, 228,
            240, 264, 999 };
    // Array containing the values of the eight cards: Ace, Ten, King, Queen, Jack, Nine, Eight, Seven.
    final int[] cardValues = { 11, 10, 4, 3, 2, 0, 0, 0 };
    final int[] sortw = { 0, 1, 2, 3 };
    final int[] rswert = { 0, 0, 4, 5, 0, 3, 2, 1 };
    final int[] ggdmw = { 7, 6, 5, 0, 4, 1, 2, 3 };

    // --------------------------------------------------------------------------------------
    // File skat.c
    // --------------------------------------------------------------------------------------

    int left(int s) {
        return (s + 1) % 3;
    }

    int right(int s) {
        return (s + 2) % 3;
    }

    boolean iscomp(int s) {
        return s >= numsp;
    }

    void swap(int[] a, int i, int j) {
        int h = a[i];
        a[i] = a[j];
        a[j] = h;
    }

    void swap(int[][] a, int i, int j, int n) {
        int h = a[i][n];
        a[i][n] = a[j][n];
        a[j][n] = h;
    }

    void setrnd(int s, int v) {
        seed[s] = (v << 1) != 0 ? v : -1;
    }

    int rndval(int s, int m) {
        int h = seed[s];
        int i;

        for (i = 0; i < 7; i++)
            h = (h << 16) | ((((h << 1) ^ (h << 4)) >> 16) & 0xffff);
        seed[s] = h;
        return h & m;
    }

    int rnd(int m) {
        return rndval(1, m);
    }

    boolean get_game() {
        return false;
    }

    boolean gutesblatt() {
        int i, c, tr, bb, bs, as, ze;
        int[] t = new int[4];

        t[0] = t[1] = t[2] = t[3] = 0;
        bb = bs = as = ze = 0;
        for (i = 0; i < 12; i++) {
            c = cards[i < 10 ? i : 20 + i];
            if ((c & 7) == BUBE) {
                bb++;
                if (i > 9)
                    bs++;
            } else
                t[c >> 3]++;
        }
        tr = 0;
        for (i = 1; i < 4; i++) {
            if (t[i] >= t[tr])
                tr = i;
        }
        for (i = 0; i < 12; i++) {
            c = cards[i < 10 ? i : 20 + i];
            if ((c & 7) != BUBE && c >> 3 != tr) {
                switch (c & 7) {
                case AS:
                    as++;
                    break;
                case ZEHN:
                    ze++;
                    break;
                }
            }
        }
        tr += bb;
        return (tr > 5 || (tr == 5 && as + ze > 1) || (bb > 2 && as > 1))
                && bs != 0;
    }

    boolean schlechtesblatt() {
        int i, c, bb, as;

        bb = as = 0;
        for (i = 0; i < 10; i++) {
            c = cards[20 + i];
            if ((c & 7) == BUBE) {
                bb++;
            } else if ((c & 7) == AS) {
                as++;
            }
        }
        return bb < 2 && as < 2;
    }

    void mischen() {
        int i, j;

        if (wieder != 0) {
            for (i = 0; i < 32; i++)
                cards[i] = savecards[i];
            if (wieder == 1) {
                if (vorhandwn)
                    rotateby = (rotateby + 3) % 3 - 1;
                for (i = 0; i < 10; i++)
                    swap(cards, i, 10 + i);
                for (i = 0; i < 10; i++)
                    swap(cards, 10 + i, 20 + i);
            } else if (wieder == 3) {
                if (vorhandwn)
                    rotateby = (rotateby + 2) % 3 - 1;
                for (i = 0; i < 10; i++)
                    swap(cards, i, 20 + i);
                for (i = 0; i < 10; i++)
                    swap(cards, 20 + i, 10 + i);
            }
            wieder = 0;
        } else if (!get_game()) {
            do {
                for (i = 0; i < 32; i++)
                    cards[i] = i;
                for (i = 0; i < 32; i++)
                    swap(cards, i, rndval(0, 31));
                for (i = 0; i < 10; i++)
                    swap(cards, geber * 10 + i, i);
                for (i = 0; i < 10; i++)
                    swap(cards, hoerer * 10 + i, geber == 1 ? i : 10 + i);
                if (rotateby < 0) {
                    for (i = 0; i < 10; i++)
                        swap(cards, i, 10 + i);
                    for (i = 0; i < 10; i++)
                        swap(cards, 10 + i, 20 + i);
                } else if (rotateby > 0) {
                    for (i = 0; i < 10; i++)
                        swap(cards, i, 20 + i);
                    for (i = 0; i < 10; i++)
                        swap(cards, 20 + i, 10 + i);
                }

            } while ((pkoption == 1 || pkoption == 4)
                    && ((numsp == 1 && !gutesblatt()) || (numsp == 2 && !schlechtesblatt())));
            if (pkoption > 1)
                pkoption = 0;
        }
        for (i = 0; i < 32; i++)
            savecards[i] = cards[i];
        setrnd(1, seed[0]);
        for (i = 0; i < 32; i++)
            gespcd[i] = 0;
        for (i = 0; i < 4; i++)
            gespfb[i] = 0;
        butternok = 0;
        for (i = 0; i < 3; i++) {
            for (j = 0; j < 5; j++)
                hatnfb[i][j] = 0;
        }
        gstsum = 0;
        astsum = 0;
    }

    boolean lower(int c1, int c2, int n) {
        int f1, f2, w1, w2;

        if (c1 < 0)
            return true;
        if (c2 < 0)
            return false;
        f1 = c1 >> 3;
        f2 = c2 >> 3;
        w1 = c1 & 7;
        w2 = c2 & 7;
        if (n != 0) {
            if (sortw[f1] < sortw[f2])
                return true;
            if (sortw[f1] > sortw[f2])
                return false;
            if (w1 == ZEHN)
                return w2 <= BUBE;
            if (w2 == ZEHN)
                return w1 > BUBE;
            return w1 > w2;
        }
        if (w2 == BUBE) {
            if (w1 != BUBE)
                return true;
            return f1 < f2;
        } else {
            if (w1 == BUBE)
                return false;
            if (f2 == trumpf && f1 != trumpf)
                return true;
            if (f1 == trumpf && f2 != trumpf)
                return false;
            if (sortw[f1] < sortw[f2])
                return true;
            if (sortw[f1] > sortw[f2])
                return false;
            return w1 > w2;
        }
    }

    void sort(int sn) {
        int i, j, f = sn * 10;
        boolean[] hatfb = new boolean[4];
        int fbsum, firstf, sptz;

        sortw[0] = 0;
        sortw[1] = 1;
        sortw[2] = 2;
        sortw[3] = 3;
        if (alternate[sn] != 0) {
            hatfb[0] = hatfb[1] = hatfb[2] = hatfb[3] = false;
            for (i = f; i < f + 10; i++) {
                if (cards[i] >= 0 && ((cards[i] & 7) != BUBE || sort2[sn] != 0)) {
                    hatfb[cards[i] >> 3] = true;
                }
            }
            if (sort2[sn] == 0 && trumpf >= 0 && trumpf < 4 && hatfb[trumpf]) {
                hatfb[trumpf] = false;
                firstf = trumpf;
            } else
                firstf = -1;
            fbsum = (hatfb[0] ? 1 : 0) + (hatfb[1] ? 1 : 0)
                    + (hatfb[2] ? 1 : 0) + (hatfb[3] ? 1 : 0);
            if ((hatfb[0] || hatfb[1]) && (hatfb[2] || hatfb[3])) {
                switch (fbsum) {
                case 4:
                    sortw[1] = 2;
                    sortw[2] = 1;
                    break;
                case 3:
                    if (hatfb[0] && hatfb[1]) {
                        sortw[0] = 0;
                        sortw[1] = 2;
                        sortw[2] = sortw[3] = 1;
                    } else {
                        sortw[2] = 0;
                        sortw[3] = 2;
                        sortw[0] = sortw[1] = 1;
                    }
                    break;
                case 2:
                    if (firstf > 1) {
                        sortw[0] = sortw[1] = 1;
                        sortw[2] = sortw[3] = 0;
                    }
                    break;
                }
            }
        }
        if (sn == spieler && spitzeang && sort2[sn] == 0) {
            sptz = trumpf == 4 ? BUBE : SIEBEN | trumpf << 3;
        } else
            sptz = -2;
        for (i = f; i < f + 9; i++) {
            for (j = i + 1; j < f + 10; j++) {
                if (((((cards[j] == sptz || lower(cards[i], cards[j], sort2[sn])) && cards[i] != sptz) ? 1
                        : 0) ^ sort1[sn]) != 0) {
                    int c = cards[i];
                    cards[i] = cards[j];
                    cards[j] = c;
                }
            }
        }
        sortw[0] = 0;
        sortw[1] = 1;
        sortw[2] = 2;
        sortw[3] = 3;
    }

    void calc_rw(int s) {
        int i, c, f, tr, bb, as, ze, dk, stg;
        boolean[] b = new boolean[4];
        int[] t = new int[4];

        maxrw[s] = 0;
        b[0] = b[1] = b[2] = b[3] = false;
        t[0] = t[1] = t[2] = t[3] = 0;
        bb = as = ze = dk = 0;
        for (i = 0; i < 10; i++) {
            c = cards[10 * s + i];
            if ((c & 7) == BUBE) {
                b[c >> 3] = true;
                bb++;
            } else
                t[c >> 3]++;
        }
        tr = 0;
        for (i = 1; i < 4; i++) {
            if (t[i] >= t[tr])
                tr = i;
        }
        for (i = 0; i < 10; i++) {
            c = cards[10 * s + i];
            if ((c & 7) != BUBE && c >> 3 != tr) {
                switch (c & 7) {
                case AS:
                    as++;
                    break;
                case ZEHN:
                    ze++;
                    break;
                default:
                    dk += cardValues[c & 7];
                }
            }
        }
        if ((bb + t[tr] == 4 && ((as == 2 && ze >= 2) || (as >= 3)))
                || (bb + t[tr] == 5 && ((dk + 10 * ze >= 39)
                        || (as >= 1 && ze >= 1 && dk + 10 * ze >= 11 && b[3])
                        || (as >= 2 && (dk + 10 * ze) != 0) || (as >= 3)))
                || (bb + t[tr] == 6 && ((dk + 10 * ze >= 14) || (ze + as) != 0))
                || bb + t[tr] >= 7) {
            f = 2;
            if (b[3]) {
                while (f < 5 && b[4 - f])
                    f++;
            }
            maxrw[s] = f * reizBaseValues[tr];
        }
        if (maxrw[s] == 0)
            testnull(s);
        if (maxrw[s] == 0
                && (((b[3] || b[2] || bb == 2) && ((b[3] && b[2] && as >= 2)
                        || (bb + t[tr] == 4 && as >= 1 && dk + 10 * ze + 11
                                * as >= 29)
                        || (bb + t[tr] == 5 && dk + 10 * ze + 11 * as >= 19)
                        || (bb + t[tr] == 5 && ze + as > 1)
                        || (bb + t[tr] == 6 && bb > 2) || (bb + t[tr] == 6 && dk
                        + 10 * ze >= 8)))
                        || (bb + t[tr] == 4 && bb != 0 && as > 1)
                        || (bb + t[tr] == 5 && as > 1) || (bb + t[tr] == 5 && dk
                        + 10 * ze + 11 * as >= 32)))
            maxrw[s] = 18;
        if (maxrw[s] == 0
                && (((b[3] || b[2] || bb == 2) && (bb + t[tr] == 6))
                        || (bb + t[tr] == 4 && bb > 1 && as != 0)
                        || (bb + t[tr] == 4 && bb != 0 && as != 0 && ze != 0 && dk != 0)
                        || (bb + t[tr] == 5 && bb != 0 && as != 0 && ze != 0)
                        || (bb + t[tr] == 5 && bb != 0 && ze != 0 && dk > 4)
                        || (bb + t[tr] == 5 && bb != 0 && ze > 1)
                        || (bb + t[tr] == 5 && bb > 1) || (bb + t[tr] == 6 && dk
                        + 10 * ze + 11 * as >= 8)))
            maxrw[s] = 17;
        stg = strateg[numsp == 0 ? s : numsp == 1 ? s - 1 : 0];
        if (stg < 0 && rnd(3) < -stg) {
            if (maxrw[s] > 17)
                maxrw[s] = 17;
            else if (maxrw[s] == 17 || rnd(7) < -stg)
                maxrw[s] = 2 * reizBaseValues[tr];
            else
                maxrw[s] = 17;
        }
    }

    void do_geben() {
        int sn, i;

        sort2[0] = sort2[1] = sort2[2] = 0;
        prot2.verdopp[0] = prot2.verdopp[1] = prot2.verdopp[2] = 0;
        schnang = schwang = ouveang = spitzeang = revolang = false;
        ndichtw = true;
        hintcard[0] = -1;
        hintcard[1] = -1;
        lasthint[0] = -1;
        lasthint[1] = -1;
        for (sn = 0; sn < numsp; sn++)
            calc_desk(sn);
        if (wieder == 0) {
            if (ramschspiele != 0) {
                if (trumpf == 4)
                    geber = right(geber);
                else
                    ramschspiele--;
            } else if (bockspiele != 0) {
                bockspiele--;
                if (bockspiele % 3 == 0 && playbock == 2) {
                    ramschspiele = 3;
                }
            }
            bockspiele += 3 * bockinc;
            geber = left(geber);
        } else if (!vorhandwn) {
            geber = left(geber + wieder);
        }
        bockinc = 0;
        trumpf = -1;
        hoerer = ausspl = left(geber);
        sager = right(geber);
        mischen();
        setcurs(0);
        View vp = findViewById(R.id.playVMHLeft);
        playVMHLeftBgr = geber == 0 ? 1 : 0;
        vp.setBackgroundResource(gameSymb(playVMHLeftBgr));
        vp = findViewById(R.id.playVMHRight);
        playVMHRightBgr = geber == 1 ? 1 : 0;
        vp.setBackgroundResource(gameSymb(playVMHRightBgr));
        givecard(hoerer, 0);
        givecard(sager, 0);
        givecard(geber, 0);
        givecard(-1, 0);
        givecard(hoerer, 1);
        givecard(sager, 1);
        givecard(geber, 1);
        givecard(hoerer, 2);
        givecard(sager, 2);
        givecard(geber, 2);
        for (sn = 0; sn < numsp; sn++)
            initscr(sn, 1);
        for (i = 0; i < 3; i++) {
            lastmsaho[i] = 0;
            sagte18[i] = false;
        }
        kontrastufe = 0;
        schenkstufe = 0;
        saho = true;
        reizp = 0;
        clear_info();
        if (!gebenf
                && (sum[0][0] != 0 || sum[0][1] != 0 || sum[0][2] != 0
                        || sum[1][0] != 0 || sum[1][1] != 0 || sum[1][2] != 0
                        || sum[2][0] != 0 || sum[2][1] != 0 || sum[2][2] != 0)) {
            di_delliste();
        }
        gebenf = true;
        if (ramschspiele != 0) {
            phase = ANSAGEN;
            di_grandhand(hoerer);
        } else if (playramsch > 1) {
            init_ramsch();
        } else {
            putmark(hoerer);
            if (layedOut) {
                put_box(sager);
                put_box(hoerer);
            }
            for (sn = numsp; sn < 3; sn++)
                calc_rw(sn);
            phase = REIZEN;
            setGone(R.id.rowStich);
            setVisible(R.id.rowSkat);
        }
    }

    void do_sagen(int s, int w) {
        String str;

        str = "" + w;
        b_text(s, str);
        inv_box(s, false, true);
        stdwait();
        inv_box(s, false, false);
        sagte18[s] = true;
    }

    void do_passen(int s) {
        b_text(s, getTranslation(Translations.XT_Passe));
        inv_box(s, true, true);
        stdwait();
        inv_box(s, true, false);
        rem_box(s);
    }

    void do_akzept(final int s) {
        b_text(s, getTranslation(Translations.XT_Ja));
        inv_box(s, false, true);
        stdwait();
        inv_box(s, false, false);
        if (acceptAnim != null)
            runHandler.removeCallbacks(acceptAnim);
        acceptAnim = new Runnable() {
            public void run() {
                b_text(s, "");
                acceptAnim = null;
            }
        };
        runHandler.postDelayed(acceptAnim, 1000);
        sagte18[s] = true;
    }

    void do_msagen(int sn, int w) {
        String str;

        if (lastmsaho[sn] == w)
            return;
        lastmsaho[sn] = w;
        str = "" + w;
        do_msaho(sn, str);
    }

    void do_mhoeren(int sn) {
        if (lastmsaho[sn] == 1)
            return;
        lastmsaho[sn] = 1;
        do_msaho(sn, getTranslation(Translations.XT_Ja));
    }

    void do_entsch() {
        int rw;

        rw = reizValues[reizp];
        if (saho) {
            if (maxrw[sager] >= rw || (maxrw[sager] == 17 && rw == 18)) {
                do_sagen(sager, rw);
                saho = false;
                if (sager == hoerer) {
                    spieler = sager;
                    do_handspiel();
                }
            } else {
                do_passen(sager);
                if (sager == geber || sager == hoerer) {
                    if (sager == hoerer) {
                        reizp--;
                        do_handspiel();
                    } else {
                        if (reizp != 0) {
                            spieler = hoerer;
                            reizp--;
                            do_handspiel();
                        } else {
                            rem_box(sager);
                            sager = hoerer;
                        }
                    }
                } else {
                    rem_box(sager);
                    sager = geber;
                    put_box(sager);
                }
            }
        } else {
            if (maxrw[hoerer] >= rw) {
                do_akzept(hoerer);
                reizp++;
                saho = true;
            } else {
                do_passen(hoerer);
                if (sager == geber) {
                    spieler = sager;
                    do_handspiel();
                } else {
                    rem_box(hoerer);
                    rem_box(sager);
                    hoerer = sager;
                    sager = geber;
                    reizp++;
                    saho = true;
                    put_box(hoerer);
                    put_box(sager);
                }
            }
        }
    }

    void do_reizen() {
        while (phase == REIZEN
                && ((iscomp(sager) && saho) || (iscomp(hoerer) && !saho))) {
            do_entsch();
        }
        if (phase == REIZEN) {
            if (saho)
                do_msagen(sager, reizValues[reizp]);
            else
                do_mhoeren(hoerer);
        }
    }

    void drueck(int f, int n, int[] p) {
        int i, j;

        for (i = (trumpf != 5 ? 1 : 0); i < 8 && n != 0 && gedr < 2; i++) {
            if (inhand[f][i]) {
                inhand[f][i] = false;
                p[f] -= cardValues[i];
                if (gedr == 0 && cards[31] == (f << 3) + i) {
                    swap(cards, 30, 31);
                } else {
                    for (j = 0; j < 10; j++) {
                        if (cards[spieler * 10 + j] == (f << 3) + i) {
                            swap(cards, 30 + gedr, 10 * spieler + j);
                            break;
                        }
                    }
                }
                gedr++;
                n--;
            }
        }
    }

    void truempfe() {
        int i, c;

        for (c = 0; c < 2; c++) {
            if ((cards[30 + c] & 7) == BUBE || cards[30 + c] >> 3 == trumpf) {
                for (i = 0; i < 10; i++) {
                    if ((cards[10 * spieler + i] & 7) != BUBE
                            && cards[10 * spieler + i] >> 3 != trumpf) {
                        swap(cards, 30 + c, 10 * spieler + i);
                        break;
                    }
                }
            }
        }
    }

    boolean tr_voll(int sn, boolean f) {
        int i, c, t, a, z;
        int[] n = new int[4];
        boolean[] ze = new boolean[4];

        if (trumpf == -1 || trumpf == 4)
            return f;
        t = a = z = 0;
        n[0] = n[1] = n[2] = n[3] = 0;
        ze[0] = ze[1] = ze[2] = ze[3] = false;
        for (i = 0; i < 10; i++) {
            c = cards[10 * sn + i];
            if ((c & 7) == BUBE || c >> 3 == trumpf)
                t++;
            else if ((c & 7) == AS)
                a++;
            else if ((c & 7) == ZEHN) {
                z++;
                ze[c >> 3] = true;
            } else
                n[c >> 3]++;
        }
        if (f) {
            return t > 7 || (t > 6 && a + z != 0);
        }
        return (t > 5 || (t > 4 && a + z != 0) || (t > 3 && a > 2))
                && !(t == 4 && ((ze[0] && n[0] == 0) || (ze[1] && n[1] == 0)
                        || (ze[2] && n[2] == 0) || (ze[3] && n[3] == 0)));
    }

    boolean sage_kontra(int sn) {
        return tr_voll(sn, false);
    }

    boolean sage_re(int sn) {
        return tr_voll(sn, true);
    }

    int testgrand(int bb, boolean[] b, boolean vh) {
        int i, j, fl, ih, g3, g4, as, ze, ko, bz;
        int[] a = new int[4];

        bz = 2;
        for (j = 0; j < 4; j++) {
            a[j] = 0;
            for (i = 0; i < 8; i++) {
                if (i >= BUBE || i == ZEHN)
                    continue;
                a[j] += (inhand[j][i] ? 1 : 0);
            }
            if (inhand[j][ZEHN] && a[j] == 0)
                bz = 1;
        }
        if (bb == 2 && spieler != ausspl)
            bz = 1;
        as = (inhand[0][AS] ? 1 : 0) + (inhand[1][AS] ? 1 : 0)
                + (inhand[2][AS] ? 1 : 0) + (inhand[3][AS] ? 1 : 0);
        ze = (inhand[0][ZEHN] ? 1 : 0) + (inhand[1][ZEHN] ? 1 : 0)
                + (inhand[2][ZEHN] ? 1 : 0) + (inhand[3][ZEHN] ? 1 : 0);
        ko = (inhand[0][KOENIG] ? 1 : 0) + (inhand[1][KOENIG] ? 1 : 0)
                + (inhand[2][KOENIG] ? 1 : 0) + (inhand[3][KOENIG] ? 1 : 0);
        if (bb == 2 && as > 2 && ze != 0)
            return bz;
        if (bb != 0 && as > 2 && ze == 4)
            return bz;
        if (as == 4 && ze > 3 - bb)
            return 2;
        if (as == 4 && ze > 2 - bb)
            return 1;
        if (bb <= 2 && (!b[3] || bb != 2 || spieler != ausspl))
            return 0;
        fl = g3 = g4 = 0;
        for (i = 0; i < 4; i++) {
            ih = 0;
            for (j = 0; j < 8; j++) {
                if (j != BUBE && inhand[i][j])
                    ih++;
            }
            for (j = 0; j < 8; j++) {
                if (j != BUBE) {
                    if (inhand[i][j])
                        fl++;
                    else if (7 - ih > j)
                        break;
                }
            }
            if ((ih > 4) || (ih > 3 && (inhand[i][AS] || inhand[i][ZEHN])))
                g4 = 1;
            if (ih > 4 && (inhand[i][AS] || inhand[i][ZEHN]))
                g3 = 1;
            if (ih > 3 && inhand[i][AS] && inhand[i][ZEHN])
                g3 = 1;
        }
        if (fl + bb > 5)
            return bz;
        if (bb == 4 && g4 != 0)
            return bz;
        if ((bb == 3 && (b[3] || vh) && g3 != 0))
            return bz;
        return fl + bb > 4 && b[3] && !(bb + as == 5 && ze == 0 && ko == 0) ? 1
                : 0;
    }

    void calc_inhand(int sn) {
        int i, c;

        for (i = 0; i < 4; i++) {
            for (c = 0; c < 8; c++)
                inhand[i][c] = false;
        }
        for (i = 0; i < 10; i++) {
            c = cards[10 * sn + i];
            if (c >= 0) {
                inhand[c >> 3][c & 7] = true;
            }
        }
    }

    boolean testhand() {
        int i, c, f, bb, as;
        boolean[] b = new boolean[4];
        int[] t = new int[4], p = new int[4], o = new int[4];

        for (i = 0; i < 4; i++) {
            t[i] = p[i] = 0;
            b[i] = false;
            o[i] = i;
        }
        bb = 0;
        for (i = 0; i < 4; i++) {
            for (c = 0; c < 8; c++)
                inhand[i][c] = false;
        }
        for (i = 0; i < 10; i++) {
            c = spcards[i];
            if ((c & 7) == BUBE) {
                b[c >> 3] = true;
                bb++;
            } else {
                p[c >> 3] += cardValues[c & 7];
                t[c >> 3]++;
                inhand[c >> 3][c & 7] = true;
            }
        }
        for (i = 1; i < 4; i++) {
            if (inhand[i][ZEHN] && !inhand[i][AS] && !inhand[i][KOENIG]) {
                o[i] = 0;
                o[0] = i;
                break;
            }
        }
        f = 3;
        while (f < 5 && b[4 - f] == b[3])
            f++;
        trumpf = 0;
        while (f * reizBaseValues[trumpf] < reizValues[reizp])
            trumpf++;
        for (i = trumpf + 1; i < 4; i++) {
            if (t[i] > t[trumpf] || (t[i] == t[trumpf] && p[i] <= p[trumpf]))
                trumpf = i;
        }
        if (testgrand(bb, b, spieler == ausspl) == 2) {
            trumpf = 4;
            return true;
        }
        as = 0;
        for (i = 0; i < 4; i++) {
            if (inhand[i][AS] && i != trumpf)
                as++;
        }
        return t[trumpf] + bb > 7 && as != 0;
    }

    boolean druecken(boolean x, int[] p, int[] t, int T, int N, int C1, int C2) {
        for (int i = 0; x && i < 4; i++) {
            boolean C = (C1 < 0 ? inhanddl[i][C1 + 3] : inhand[i][C1])
                    && (C2 < 0 ? inhanddl[i][C2 + 3] : inhand[i][C2]);
            if (C1 == IGNO && C2 == IGNO)
                C = (p[i] == 0);
            if (i != trumpf && t[i] == T && C) {
                drueck(i, N, p);
                t[i] -= N;
                x = false;
                break;
            }
        }
        return x;
    }

    void calc_drueck() {
        int i, j, c, f, bb, n, sp, tr;
        boolean x;
        boolean[] b = new boolean[4];
        int[] t = new int[4], p = new int[4], o = new int[4];
        int[] savecards = new int[32];

        if (iscomp(spieler)) {
            if (maxrw[spieler] == nullw[0] || maxrw[spieler] == nullw[1]
                    || maxrw[spieler] == nullw[2] || maxrw[spieler] == nullw[3]
                    || maxrw[spieler] == nullw[4]) {
                trumpf = -1;
                if (maxrw[spieler] != nullw[0] && maxrw[spieler] != nullw[2])
                    handsp = true;
                if (maxrw[spieler] >= nullw[3])
                    ouveang = true;
                if (maxrw[spieler] == nullw[4])
                    revolang = true;
                gedr = 2;
                return;
            }
            if (testhand()) {
                gedr = 2;
                handsp = true;
                return;
            }
        } else {
            for (i = 0; i < 32; i++)
                savecards[i] = cards[i];
        }
        for (i = 0; i < 4; i++) {
            b[i] = false;
            t[i] = p[i] = 0;
        }
        bb = 0;
        for (i = 0; i < 4; i++) {
            for (c = 0; c < 8; c++)
                inhand[i][c] = false;
        }
        for (i = 0; i < 12; i++) {
            c = spcards[i];
            if ((c & 7) == BUBE) {
                b[c >> 3] = true;
                bb++;
            } else {
                p[c >> 3] += cardValues[c & 7];
                t[c >> 3]++;
                inhand[c >> 3][c & 7] = true;
            }
        }
        f = 2;
        while (f < 5 && b[4 - f] == b[3])
            f++;
        trumpf = 0;
        if (iscomp(spieler)) {
            while (f * reizBaseValues[trumpf] < reizValues[reizp])
                trumpf++;
        }
        for (i = trumpf + 1; i < 4; i++) {
            if (t[i] > t[trumpf] || (t[i] == t[trumpf] && p[i] <= p[trumpf]))
                trumpf = i;
        }
        tr = t[trumpf];
        truempfe();
        for (i = 0; i < 4; i++) {
            inhanddl[i][LUSCHE + 3] = inhand[i][SIEBEN] || inhand[i][ACHT]
                    || inhand[i][NEUN];
            inhanddl[i][DAMELUSCHE + 3] = inhand[i][DAME]
                    || inhanddl[i][LUSCHE + 3];
            inhanddl[i][IGNO + 3] = true;
        }
        do {
            x = true;
            if (gedr == 0) {
                x = druecken(x, p, t, 1, 1, ZEHN, IGNO);
                x = druecken(x, p, t, 2, 1, ZEHN, DAMELUSCHE);
                x = druecken(x, p, t, 1, 1, KOENIG, IGNO);
                x = druecken(x, p, t, 2, 2, KOENIG, DAME);
                x = druecken(x, p, t, 1, 1, DAME, IGNO);
                x = druecken(x, p, t, 2, 2, KOENIG, LUSCHE);
                x = druecken(x, p, t, 2, 2, DAME, LUSCHE);
                x = druecken(x, p, t, 2, 2, ZEHN, KOENIG);
                x = druecken(x, p, t, 1, 1, IGNO, IGNO);
                x = druecken(x, p, t, 2, 2, IGNO, IGNO);
                x = druecken(x, p, t, 2, 1, AS, KOENIG);
                x = druecken(x, p, t, 2, 1, AS, DAME);
                x = druecken(x, p, t, 2, 1, AS, LUSCHE);
            } else {
                x = druecken(x, p, t, 1, 1, ZEHN, IGNO);
                x = druecken(x, p, t, 2, 1, ZEHN, DAMELUSCHE);
                x = druecken(x, p, t, 1, 1, KOENIG, IGNO);
                x = druecken(x, p, t, 1, 1, DAME, IGNO);
                x = druecken(x, p, t, 1, 1, IGNO, IGNO);
                x = druecken(x, p, t, 2, 1, KOENIG, LUSCHE);
                x = druecken(x, p, t, 2, 1, DAME, LUSCHE);
                x = druecken(x, p, t, 2, 1, KOENIG, DAME);
                x = druecken(x, p, t, 2, 1, IGNO, IGNO);
                x = druecken(x, p, t, 2, 1, AS, KOENIG);
                x = druecken(x, p, t, 2, 1, AS, DAME);
                x = druecken(x, p, t, 2, 1, AS, LUSCHE);
                x = druecken(x, p, t, 2, 1, ZEHN, KOENIG);
            }
        } while (gedr < 2 && !x);
        for (i = 0; i < 4; i++) {
            o[i] = i;
        }
        for (i = 0; i < 4; i++) {
            for (j = i + 1; j < 4; j++) {
                if (p[o[i]] > p[o[j]]) {
                    swap(o, i, j);
                }
            }
        }
        for (n = 3; n < 8 && gedr < 2; n++) {
            for (j = 0; j < 4 && gedr < 2; j++) {
                i = o[j];
                if (t[i] == n && i != trumpf) {
                    if (inhand[i][AS]) {
                        if (!inhand[i][ZEHN])
                            drueck(i, 2, p);
                    } else
                        drueck(i, 2, p);
                }
            }
        }
        if (testgrand(bb, b, spieler == ausspl) != 0) {
            trumpf = 4;
        }
        if (spitzezaehlt != 0
                && ((trumpf < 4 && inhand[trumpf][SIEBEN] && ((tr + bb >= 7 && (bb > 1 || !b[0])) || (tr
                        + bb == 6 && bb >= 4))) || (trumpf == 4 && b[0] && b[3]
                        && bb == 3 && spieler == ausspl))) {
            sp = trumpf == 4 ? BUBE : SIEBEN | trumpf << 3;
            if (cards[30] != sp && cards[31] != sp) {
                spitzeang = true;
            }
        }
        if (iscomp(spieler)) {
            gespcd[cards[30]] = 1;
            gespcd[cards[31]] = 1;
        } else {
            hintcard[0] = cards[30];
            hintcard[1] = cards[31];
            for (i = 0; i < 32; i++)
                cards[i] = savecards[i];
        }
    }

    void nextgame() {
    }

    void save_skat(int i) {
        if (lower(cards[31], cards[30], 0)) {
            swap(cards, 31, 30);
        }
        prot2.skat[i][0] = cards[30];
        prot2.skat[i][1] = cards[31];
    }

    int check_bockevents() {
        return 0;
    }

    void update_list() {
        int i;

        if (splstp >= LIST_LEN) {
            modsum(splsum, sgewoverl, 0);
            for (i = 1; i < splstp; i++)
                splist[i - 1] = splist[i];
            splstp--;
            splist[splstp] = new structsplist();
        }
        splist[splstp].s = spieler;
        splist[splstp].r = trumpf == 5;
        splist[splstp].d = trumpf == 5 && stsum == 120 && spgew;
        splist[splstp].e = spwert;
        splist[splstp].g = spgew;
        prevsum = sum;
        modsum(sum, cgewoverl, splstp);
        splstp++;
    }

    void do_grandhand(int sn) {
        handsp = true;
        trumpf = 4;
        reizp = 0;
        spieler = sn;
        do_handspiel();
    }

    void set_prot() {
        prot2.stichgem = stich - 1;
        prot2.spieler = spieler;
        prot2.trumpf = trumpf;
        prot2.gereizt = reizp < 0 || (ramschspiele != 0) ? 0 : reizValues[reizp];
        prot2.gewonn = spgew;
        prot2.augen = stsum;
        prot2.spwert = spwert;
        prot2.handsp = handsp;
        prot2.ehsso = revolang ? handsp ? 1 : 0 : ouveang ? trumpf == -1
                && handsp ? 5 : 4 : schwang ? 3 : schnang ? 2 : handsp ? 1 : 0;
        if (trumpf != 5)
            prot2.sramsch = 0;
        prot2.savseed = savseed;
        prot2.rotateby = rotateby;
        prot2.spitze = spitzeang;
        prot2.revolution = revolang;
        prot2.schenken = schenkstufe;
    }

    void do_handspiel() {
        int i, sn;

        prot2.anspiel[0] = ausspl;
        prot2.gemacht[0] = -1;
        save_skat(0);
        if (reizp < 0 && ramschspiele == 0) {
            if (playramsch > 0) {
                init_ramsch();
                return;
            }
            stich = 1;
            fill_st();
            trumpf = 4;
            spwert = 0;
            set_prot();
            save_skat(1);
            prot1.assign(prot2);
            update_list();
            save_list();
            nextgame();
            phase = WEITER;
            for (sn = 0; sn < numsp; sn++) {
                draw_skat(sn);
            }
            if (numsp == 1)
                di_wiederweiter(0);
            else
                di_weiter(1);
            return;
        }
        info_reiz();
        drkcd = 0;
        if (ramschspiele == 0)
            handsp = false;
        stsum = 0;
        vmh = 0;
        gedr = 0;
        for (i = 0; i < 10; i++)
            spcards[i] = cards[spieler * 10 + i];
        spcards[10] = cards[30];
        spcards[11] = cards[31];
        rem_box(sager);
        rem_box(hoerer);
        if (!iscomp(spieler) && ramschspiele == 0) {
            phase = HANDSPIEL;
            di_hand();
        } else
            do_handok();
    }

    void do_druecken() {
        draw_skat(spieler);
        trumpf = 4;
        if (hintcard[0] == -1) {
            gedr = 0;
            calc_drueck();
            trumpf = -1;
        }
        if (hints[spieler]) {
            show_hint(spieler, 0, true);
            show_hint(spieler, 1, true);
        }
        put_fbox(spieler, true);
        drbut = spieler + 1;
        phase = DRUECKEN;
        stsum = 0;
        gespcd[cards[30]] = 0;
        gespcd[cards[31]] = 0;
        gedr = 0;
        handsp = false;
    }

    void do_handok() {
        if (iscomp(spieler) || handsp) {
            home_skat();
            if (iscomp(spieler) && !handsp)
                calc_drueck();
            stsum = cardValues[cards[30] & 7] + cardValues[cards[31] & 7];
            save_skat(1);
        }
        if (!iscomp(spieler) && !handsp)
            do_druecken();
        else
            do_ansagen();
    }

    void do_ansagen() {
        int i, c, bb;

        phase = ANSAGEN;
        bb = kannspitze = 0;
        for (i = 0; i < (handsp ? 10 : 12); i++) {
            c = i >= 10 ? prot2.skat[1][i - 10] : cards[spieler * 10 + i];
            if ((c & 7) == BUBE)
                bb++;
            if (i < 10) {
                if ((c & 7) == SIEBEN) {
                    kannspitze = 1;
                    break;
                }
                if (c == BUBE) {
                    kannspitze = 2;
                }
            }
        }
        if (kannspitze == 2) {
            kannspitze = (bb != 4 ? 1 : 0);
        }
        if (!iscomp(spieler) && ramschspiele == 0) {
            di_spiel();
        } else {
            remmark(1);
            di_ansage();
        }
    }

    void karobube() {
        int s, i, n, k, c;

        karobubeanz = false;
        if (trumpf < 0 || trumpf > 3)
            return;
        for (s = 0; s < 3; s++) {
            if (s == spieler)
                continue;
            n = k = 0;
            for (i = 0; i < 10; i++) {
                c = cards[s * 10 + i];
                if ((c & 7) == BUBE || c >> 3 == trumpf) {
                    n++;
                    if ((c & 7) < KOENIG)
                        n = 9;
                    if (c == BUBE)
                        k = 1;
                    else if ((c & 7) == BUBE)
                        n = 9;
                }
            }
            if (k != 0 && n == 2) {
                karobubeanz = true;
            }
        }
    }

    boolean karobubespielen() {
        int i;

        if (!karobubeanz)
            return false;
        for (i = 0; i < possc; i++) {
            if (cards[possi[i]] == BUBE) {
                playcd = i;
                return true;
            }
        }
        return false;
    }

    void do_angesagt() {
        if (!iscomp(spieler)) {
            remmark(1);
        }
        stich = 1;
        schwz = true;
        nullv = false;
        spitzeok = false;
        info_spiel();
        sort2[0] = sort2[1] = sort2[2] = (trumpf == -1 ? 1 : 0);
        if (revolang)
            revolutiondist();
        if (revolang && numsp != 0 && (numsp > 1 || iscomp(spieler)))
            revolutionscr();
        else
            spielphase();
    }

    void spielphase() {
        int sn, c, i;

        phase = SPIELEN;
        hintcard[0] = -1;
        hintcard[1] = -1;
        sptruempfe = 0;
        for (i = 0; i < 10; i++) {
            c = cards[spieler * 10 + i];
            if ((c & 7) == BUBE || c >> 3 == trumpf)
                sptruempfe++;
        }
        karobube();
        if (ouveang) {
            for (sn = 0; sn < numsp; sn++) {
                di_info(sn, -2);
                calc_desk(sn);
            }
        }
        for (sn = numsp; sn < 3; sn++) {
            sort1[sn] = sort1[0];
            sort2[sn] = (trumpf == -1 ? 1 : 0);
            alternate[sn] = alternate[0];
            sort(sn);
        }
        for (sn = 0; sn < numsp; sn++)
            initscr(sn, 1);
        setGone(R.id.dialogSpielen);
        setVisible(R.id.dialogDesk);
        setGone(R.id.rowSkat);
        setVisible(R.id.rowStich);
        setInvisible(R.id.cardStichLeft);
        setInvisible(R.id.cardStichMiddle);
        setInvisible(R.id.cardStichRight);
    }

    boolean higher(int c1, int c2) {
        int f1, w1, f2, w2;

        if (c2 == -1)
            return true;
        f1 = c1 >> 3;
        w1 = c1 & 7;
        f2 = c2 >> 3;
        w2 = c2 & 7;
        if (trumpf == -1) {
            if (f1 == f2) {
                if (w1 == ZEHN)
                    return w2 > BUBE;
                if (w2 == ZEHN)
                    return w1 <= BUBE;
                return w1 < w2;
            }
            return true;
        }
        if (w1 == BUBE) {
            if (w2 == BUBE)
                return f1 > f2;
            else
                return true;
        }
        if (w2 == BUBE)
            return false;
        if (f1 == f2)
            return w1 < w2;
        return f2 != trumpf;
    }

    void calc_result() {
        int i, c, f;
        int[] b = new int[4], s = new int[8];

        mes1 = mes2 = mes3 = mes4 = false;
        if (trumpf == 5) {
            ramsch_result();
            return;
        }
        if (trumpf == -1) {
            spwert = nullw[revolang ? 4 : (ouveang ? 2 : 0) + (handsp ? 1 : 0)];
            if (nullv) {
                spgew = false;
                if (!handsp || !oldrules)
                    spwert *= 2;
                nspwert = 0;
            } else {
                spgew = true;
                nspwert = spwert;
            }
            for (i = 0; i < kontrastufe; i++)
                spwert *= 2;
            if (bockspiele != 0)
                spwert *= 2;
            return;
        }
        if (stich == 1 && schenkstufe != 0) {
            stsum = 61;
            schwz = false;
            nullv = true;
        }
        b[0] = b[1] = b[2] = b[3] = 0;
        s[0] = s[1] = s[2] = s[3] = s[4] = s[5] = s[6] = s[7] = 0;
        for (i = 0; i < 12; i++) {
            c = spcards[i];
            if ((c & 7) == BUBE)
                b[c >> 3] = 1;
            else if (c >> 3 == trumpf)
                s[c & 7] = 1;
        }
        s[BUBE] = s[NEUN];
        s[NEUN] = s[ACHT];
        s[ACHT] = s[SIEBEN];
        f = 1;
        while (f < 4 && b[3 - f] == b[3])
            f++;
        if (f == 4 && trumpf != 4) {
            while (f < 11 && s[f - 4] == b[3])
                f++;
        }
        f++;
        if (handsp)
            f++;
        if (stsum >= 90 || schnang || stsum <= 30)
            f++;
        if (schnang)
            f++;
        if (schwz || schwang || !nullv)
            f++;
        if (schwang)
            f++;
        if (ouveang)
            f++;
        if (spitzeang)
            f += spitzezaehlt;
        if (trumpf == 4 && ouveang && oldrules)
            spwert = (f - 1) * 36;
        else
            spwert = f * reizBaseValues[trumpf];
        if ((stsum > 60 && spwert >= reizValues[reizp] && (stsum >= 90 || !schnang)
                && (schwz || !schwang) && (spitzeok || !spitzeang))
                || stich == 1) {
            spgew = true;
            nspwert = spwert;
        } else {
            if (spwert < reizValues[reizp])
                mes1 = true;
            else if (schnang && stsum < 90)
                mes2 = true;
            else if (schwang && !schwz)
                mes3 = true;
            else if (spitzeang && !spitzeok)
                mes4 = true;
            spgew = false;
            if (spwert < reizValues[reizp]) {
                spwert = ((reizValues[reizp] - 1) / reizBaseValues[trumpf] + 1)
                        * reizBaseValues[trumpf];
            }
            if (!handsp || !oldrules)
                spwert *= 2;
            nspwert = 0;
        }
        for (i = 0; i < kontrastufe; i++)
            spwert *= 2;
        if (bockspiele != 0 && ramschspiele == 0)
            spwert *= 2;
    }

    void get_next() {
        int s;

        prot2.anspiel[stich - 1] = ausspl;
        prot2.stiche[stich - 1][ausspl] = stcd[0];
        prot2.stiche[stich - 1][left(ausspl)] = stcd[1];
        prot2.stiche[stich - 1][right(ausspl)] = stcd[2];
        if (trumpf == -1)
            null_stich();
        if (higher(stcd[0], stcd[1])) {
            if (higher(stcd[0], stcd[2]))
                s = 0;
            else
                s = 2;
        } else {
            if (higher(stcd[1], stcd[2]))
                s = 1;
            else
                s = 2;
        }
        ausspl = (ausspl + s) % 3;
        prot2.gemacht[stich - 1] = ausspl;
        if (spitzeang && stich == 10 && ausspl == spieler
                && stcd[s] == (trumpf == 4 ? BUBE : SIEBEN | trumpf << 3)) {
            spitzeok = true;
        }
        if (trumpf == 5) {
            ramsch_stich();
            return;
        }
        if (stich == 1 && !handsp) {
            astsum += stsum;
        }
        if (spieler == ausspl) {
            if (butternok == 1)
                butternok = 2;
            stsum += cardValues[stcd[0] & 7] + cardValues[stcd[1] & 7]
                    + cardValues[stcd[2] & 7];
            astsum += cardValues[stcd[0] & 7] + cardValues[stcd[1] & 7]
                    + cardValues[stcd[2] & 7];
            nullv = true;
        } else {
            if (butternok != 2)
                butternok = 0;
            gstsum += cardValues[stcd[0] & 7] + cardValues[stcd[1] & 7]
                    + cardValues[stcd[2] & 7];
            schwz = false;
        }
    }

    void save_list() {
        int i, j;

        for (i = 0; i < 3; i++) {
            for (j = 0; j < 3; j++) {
                editor.putInt("splsum" + i + "." + j, splsum[i][j]);
            }
            for (j = 0; j < 2; j++) {
                editor.putInt("sgewoverl" + i + "." + j, sgewoverl[i][j]);
            }
        }
        editor.putInt("splstp", splstp);
        for (i = 0; i < LIST_LEN; i++) {
            editor.putInt("splist" + i + ".s", splist[i].s);
            editor.putInt("splist" + i + ".e", splist[i].e);
            editor.putBoolean("splist" + i + ".r", splist[i].r);
            editor.putBoolean("splist" + i + ".d", splist[i].d);
            editor.putBoolean("splist" + i + ".g", splist[i].g);
        }
        saveState();
        saveViews();
        editor.commit();
    }

    void setsum(boolean clr) {
        int i, j;

        splstp = 0;
        if (clr) {
            for (i = 0; i < LIST_LEN; i++) {
                splist[i] = new structsplist();
            }
        }
        for (i = 0; i < 3; i++) {
            splfirst[i] = 0;
            for (j = 0; j < 3; j++) {
                if (clr) {
                    sum[i][j] = 0;
                    if (j < 2) {
                        cgewoverl[i][j] = 0;
                    }
                }
                splsum[i][j] = sum[i][j];
                if (j < 2) {
                    sgewoverl[i][j] = cgewoverl[i][j];
                }
            }
        }
    }

    void modsum(int[][] sm, int[][] gv, int p) {
        int[] ret = new int[4];
        modsum(sm, gv, p, ret);
    }

    void modsum(int[][] sm, int[][] gv, int p, int[] ret) {
        int s, e;
        boolean r, d;

        s = splist[p].s;
        r = splist[p].r;
        d = splist[p].d;
        e = splist[p].e;
        if (!splist[p].g)
            e = -e;
        if (e <= 0 || !r || d) {
            sm[s][0] += e;
            sm[s][2] += e;
            if (e != 0)
                gv[s][e < 0 ? 1 : 0]++;
        }
        if (e < 0) {
            sm[s][1] -= e;
            if (!r) {
                sm[s][2] -= 50;
                sm[left(s)][2] += 40;
                sm[right(s)][2] += 40;
            }
        } else {
            if (r && !d) {
                sm[left(s)][0] -= e;
                sm[right(s)][0] -= e;
                sm[left(s)][2] -= e;
                sm[right(s)][2] -= e;
                if (e != 0) {
                    gv[left(s)][1]++;
                    gv[right(s)][1]++;
                }
            }
            sm[left(s)][1] += e;
            sm[right(s)][1] += e;
            if (!r && e != 0) {
                sm[s][2] += 50;
            }
        }
        ret[0] = s;
        ret[1] = e;
        ret[2] = r ? 1 : 0;
        ret[3] = d ? 1 : 0;
    }

    void read_list() {
        int i, j;

        setsum(true);
        for (i = 0; i < 3; i++) {
            for (j = 0; j < 3; j++) {
                splsum[i][j] = prefs.getInt("splsum" + i + "." + j, 0);
            }
            for (j = 0; j < 2; j++) {
                sgewoverl[i][j] = prefs.getInt("sgewoverl" + i + "." + j, 0);
            }
        }
        for (i = 0; i < 3; i++) {
            for (j = 0; j < 3; j++) {
                sum[i][j] = splsum[i][j];
            }
            for (j = 0; j < 2; j++) {
                cgewoverl[i][j] = sgewoverl[i][j];
            }
        }
        splstp = prefs.getInt("splstp", 0);
        for (i = 0; i < splstp; i++) {
            splist[i].s = prefs.getInt("splist" + i + ".s", 0);
            splist[i].e = prefs.getInt("splist" + i + ".e", 0);
            splist[i].r = prefs.getBoolean("splist" + i + ".r", false);
            splist[i].d = prefs.getBoolean("splist" + i + ".d", false);
            splist[i].g = prefs.getBoolean("splist" + i + ".g", false);
            modsum(sum, cgewoverl, i);
        }
    }

    void fill_st() {
        int i, j, s, c, sc;

        for (s = 0; s < 3; s++) {
            if (vmh >= 1 && s == ausspl) {
                sc = stcd[0];
            } else if (vmh == 2 && s == left(ausspl)) {
                sc = stcd[1];
            } else {
                sc = -1;
            }
            i = stich - 1;
            for (j = 0; j < 10; j++) {
                c = cards[10 * s + j];
                if (c < 0 && sc >= 0) {
                    c = sc;
                    sc = -1;
                }
                if (c >= 0)
                    prot2.stiche[i++][s] = c;
            }
        }
    }

    int maxnimm() {
        int i, m;

        m = nimmstich[0][0];
        for (i = 1; i < numsp; i++) {
            if (nimmstich[i][0] > m)
                m = nimmstich[i][0];
        }
        return m;
    }

    void next_stich() {
        if (maxnimm() < 101) {
            waitt(maxnimm() * 100, 2);
        }
        info_stich(0, stcd[0]);
        info_stich(1, stcd[1]);
        info_stich(2, stcd[2]);
        nimm_stich();
    }

    void finishgame() {
        int i, s;

        if (stich < 11) {
            if (trumpf < 0 || trumpf > 4 || (schenkstufe != 0 && stich == 1))
                fill_st();
            else {
                while (stich != 11) {
                    s = (ausspl + vmh) % 3;
                    calc_poss(s);
                    make_best(s);
                    i = possi[playcd];
                    stcd[vmh] = cards[i];
                    cards[i] = -1;
                    if (vmh == 2) {
                        get_next();
                        vmh = 0;
                        stich++;
                    } else
                        vmh++;
                }
            }
        }
        calc_result();
        set_prot();
        prot1.assign(prot2);
        update_list();
        if (playbock != 0)
            bockinc = check_bockevents();
        save_list();
        clr_desk(false);
        phase = RESULT;
        di_result(bockinc);
        nextgame();
    }

    void do_next() {
        if (vmh == 2) {
            get_next();
            nimmstich[0][1] = 1;
            phase = NIMMSTICH;
            if (firstgame) {
                if (nimmstich[0][0] >= 101) {
                    setGone(R.id.box18passeL);
                    setGone(R.id.box18passeR);
                    setVisible(R.id.boxHinweisStich);
                }
                firstgame = false;
            }
            if (nimmstich[0][0] < 101) {
                takeTrick = new Runnable() {
                    public void run() {
                        takeTrick = null;
                        clickedSpace(null);
                    }
                };
                runHandler.postDelayed(takeTrick, nimmstich[0][0] * 10);
            }
            return;
        } else
            vmh++;
    }

    void calc_poss(int s) {
        int i, j, k, f1, w1, f2, w2;

        possc = 0;
        for (i = 0; i < 10; i++) {
            if (cards[s * 10 + i] >= 0) {
                for (j = 0; j < possc && cards[s * 10 + i] > cards[possi[j]]; j++)
                    ;
                for (k = possc; k > j; k--)
                    possi[k] = possi[k - 1];
                possi[j] = s * 10 + i;
                possc++;
            }
        }
        if (vmh != 0) {
            f1 = stcd[0] >> 3;
            w1 = stcd[0] & 7;
            if (trumpf != -1 && w1 == BUBE)
                f1 = trumpf;
            i = j = 0;
            do {
                f2 = cards[possi[i]] >> 3;
                w2 = cards[possi[i]] & 7;
                if (trumpf != -1 && w2 == BUBE)
                    f2 = trumpf;
                if (f1 == f2)
                    possi[j++] = possi[i];
            } while (++i < possc);
            if (j != 0)
                possc = j;
            else
                hatnfb[s][Math.min(f1, 4)] = 1;
        }
    }

    void c_high(int f, int[] h) {
        int i, j;

        h[0] = h[1] = h[2] = h[3] = h[4] = -1;
        for (i = 0; i < 4; i++) {
            for (j = 0; j < 8; j++) {
                if (j == BUBE)
                    j++;
                if (gespcd[i << 3 | j] < f) {
                    h[i] = i << 3 | j;
                    break;
                }
            }
        }
        for (i = 3; i >= 0; i--) {
            if (gespcd[i << 3 | BUBE] < f) {
                h[trumpf] = i << 3 | BUBE;
                break;
            }
        }
    }

    void calc_high(int f, int s) {
        int i;
        int[] gespsav = new int[32];

        c_high(f, high);
        if (s == 0)
            return;
        for (i = 0; i < 32; i++)
            gespsav[i] = gespcd[i];
        for (i = 0; i < 5; i++) {
            if (high[i] >= 0)
                gespcd[high[i]] = 2;
        }
        c_high(f, shigh);
        for (i = 0; i < 32; i++)
            gespcd[i] = gespsav[i];
    }

    boolean zweihoechste(int ci) {
        int i, tr, trdr, cj = ci;

        calc_high(1, 1);
        if (ci != high[trumpf])
            return false;
        for (i = 0; i < possc; i++) {
            cj = cards[possi[i]];
            if (cj == shigh[trumpf])
                break;
        }
        tr = 0;
        for (i = 0; i < possc; i++) {
            if (cards[possi[i]] >> 3 == trumpf || (cards[possi[i]] & 7) == BUBE) {
                tr++;
            }
        }
        if (trumpf < 4)
            trdr = 7 - gespfb[trumpf];
        else
            trdr = 0;
        for (i = 0; i < 4; i++)
            if (gespcd[i << 3 | BUBE] == 0)
                trdr++;
        return ci != cj && cj == shigh[trumpf] && trdr - tr <= 1;
    }

    boolean ignorieren() {
        int mi, fb, i, ih;
        int[] k = new int[8];

        mi = right(ausspl);
        fb = stcd[0] >> 3;
        if ((stcd[0] & 7) == BUBE || fb == trumpf || cardValues[stcd[0] & 7] != 0
                || hatnfb[mi][fb] == 1)
            return false;
        ih = 0;
        for (i = 0; i < 8; i++)
            k[i] = 0;
        for (i = 0; i < possc; i++) {
            if (cards[possi[i]] >> 3 == fb) {
                ih++;
                k[cards[possi[i]] & 7] = 1;
            }
        }
        k[BUBE] = k[NEUN];
        k[NEUN] = k[ACHT];
        k[ACHT] = k[SIEBEN];
        if (ih >= 2) {
            for (i = AS; i <= NEUN && k[i] == 0; i++) {
                if (gespcd[fb << 3 | i] != 2)
                    return false;
            }
            for (i++; i <= ACHT && k[i] == 0; i++) {
                if (gespcd[fb << 3 | i] != 2)
                    break;
            }
            if (k[i] != 0)
                return false;
        }
        if (stich > 7) {
            for (i = 0; i < possc; i++) {
                if (cards[possi[i]] >> 3 == fb && (cards[possi[i]] & 7) != BUBE) {
                    if (!higher(stcd[0], cards[possi[i]]))
                        return false;
                }
            }
        }
        return ih < 3;
    }

    boolean genugdrin() {
        return (stcd[0] >> 3 == cards[possi[0]] >> 3 && (cards[possi[0]] & 7) != BUBE)
                || (trumpf != 4 && cardValues[stcd[0] & 7] + cardValues[stcd[1] & 7] > 0)
                || cardValues[stcd[0] & 7] + cardValues[stcd[1] & 7] > 3 + rnd(1);
    }

    boolean gewinnstich(int f) {
        int i, s, ci, sf, su;
        boolean p, g;

        s = f != 0 ? astsum : gstsum;
        sf = 0;
        if (f != 0) {
            if (schnang || spitzeang || stich < 6 || s > 60)
                return false;
        } else {
            if (s > 59)
                return false;
            if (s < 30) {
                su = cardValues[prot2.skat[0][0] & 7] + cardValues[prot2.skat[0][1] & 7]
                        + cardValues[stcd[0] & 7] + cardValues[stcd[1] & 7];
                for (i = 0; i < 30; i++) {
                    if (cards[i] >= 0)
                        su += cardValues[cards[i] & 7];
                }
                if (su + s < 60)
                    sf = 1;
            }
        }
        p = !higher(stcd[0], stcd[1]);
        g = f == 0 && (spieler == ausspl) ^ !p;
        for (i = 0; i < possc; i++) {
            ci = cards[possi[i]];
            if (!higher(stcd[p ? 1 : 0], ci) || g) {
                if (s + cardValues[ci & 7] + cardValues[stcd[0] & 7] + cardValues[stcd[1] & 7] > 59 + f) {
                    playcd = i;
                    return true;
                }
                if (sf != 0
                        && s + cardValues[ci & 7] + cardValues[stcd[0] & 7]
                                + cardValues[stcd[1] & 7] > 30) {
                    playcd = i;
                    return true;
                }
            }
        }
        return false;
    }

    boolean uebernehmen(int p, int h, int n) {
        int i, j, ci, cj, wi, wj, fb;
        boolean is;

        is = (ausspl + vmh) % 3 == spieler;
        if (is
                && vmh == 1
                && hatnfb[left(spieler)][trumpf] == 0
                && (stcd[0] >> 3 == trumpf || (stcd[0] & 7) == BUBE || hatnfb[left(spieler)][stcd[0] >> 3] != 0))
            h = 0;
        j = 0;
        calc_inhand((ausspl + vmh) % 3);
        for (i = 0; i < possc; i++) {
            ci = cards[possi[i]];
            if (!higher(stcd[p], ci)) {
                if (j != 0) {
                    cj = cards[possi[j - 1]];
                    wi = cardValues[ci & 7];
                    wj = cardValues[cj & 7];
                    if (is) {
                        if (h != 0) {
                            calc_high(1, 1);
                            fb = wj == 2 ? trumpf : cj >> 3;
                            if (cj == high[fb] && shigh[fb] >= 0
                                    && !inhand[shigh[fb] >> 3][shigh[fb] & 7]) {
                                j = i + 1;
                                continue;
                            }
                            fb = wi == 2 ? trumpf : ci >> 3;
                            if (ci == high[fb] && shigh[fb] >= 0
                                    && !inhand[shigh[fb] >> 3][shigh[fb] & 7])
                                continue;
                        }
                    }
                    if (wi == 10)
                        wi = 12 - h * 2;
                    if (wj == 10)
                        wj = 12 - h * 2;
                    if (wi == 2 && wj == 2) {
                        if (trumpf == 4 && is) {
                            wi = ci >> 3;
                            wj = cj >> 3;
                        } else {
                            wi = cj >> 3;
                            wj = ci >> 3;
                        }
                    } else {
                        if (wi == 2)
                            wi = 5 - h * 6;
                        if (wj == 2)
                            wj = 5 - h * 6;
                    }
                    if (is) {
                        if (h == 0 && zweihoechste(ci))
                            j = i + 1;
                        else {
                            if (n != 0) {
                                if ((wi == 4 && ci >> 3 != trumpf) || wi == 10)
                                    wi = -1;
                                if ((wj == 4 && cj >> 3 != trumpf) || wj == 10)
                                    wj = -1;
                            }
                            if ((h != 0 || !zweihoechste(cj))
                                    && ((wi < wj) ^ (h != 0)))
                                j = i + 1;
                        }
                    } else {
                        if ((wi < wj) ^ (h != 0))
                            j = i + 1;
                    }
                } else
                    j = i + 1;
            }
        }
        if (j != 0) {
            cj = cards[possi[j - 1]];
            wj = cardValues[cj & 7];
            if (is
                    && vmh == 1
                    && wj > 4
                    && hatnfb[left(spieler)][trumpf] == 0
                    && (stcd[0] >> 3 == trumpf
                            || (wj == 10 && gespcd[(cj & 0x18) | AS] == 0 && !inhand[cj >> 3][AS])
                            || (stcd[0] & 7) == BUBE || hatnfb[left(spieler)][stcd[0] >> 3] != 0))
                j = 0;
            else if (h == 0 && wj == 10
                    && gespcd[(cj & 0x18) | AS] < (is ? 0 : 1) + 1)
                j = 0;
            else
                playcd = j - 1;
        }
        return j != 0;
    }

    void schmieren() {
        int i, j, wi, wj, ci, cj;
        int[] aw = new int[4];

        j = 0;
        aw[0] = aw[1] = aw[2] = aw[3] = 11;
        calc_high(2, 0);
        if (vmh != 2) {
            for (i = 0; i < 4; i++) {
                if (!(i == trumpf && high[i] != (i << 3 | AS) && possc < 3)
                        && hatnfb[spieler][i] == 0)
                    aw[i] = 2;
            }
        }
        for (i = 1; i < possc; i++) {
            wi = cardValues[(ci = cards[possi[i]]) & 7];
            wj = cardValues[(cj = cards[possi[j]]) & 7];
            if (wi == 2)
                wi = -2;
            else if (ci >> 3 == trumpf && cj >> 3 != trumpf)
                wi = 1;
            else if (wi == 11)
                wi = aw[ci >> 3];
            if (wj == 2)
                wj = -2;
            else if (cj >> 3 == trumpf && ci >> 3 != trumpf)
                wj = 1;
            else if (wj == 11)
                wj = aw[cj >> 3];
            if (wi > wj
                    || (vmh == 2 && wi == wj && wi == 0 && (ci & 7) > (cj & 7)))
                j = i;
        }
        playcd = j;
    }

    boolean einstechen() {
        int ci;

        if (cardValues[stcd[0] & 7] == 0 || !uebernehmen(0, 0, 0))
            return false;
        ci = cards[possi[playcd]];
        if ((ci & 7) <= ZEHN || (ci & 7) == BUBE)
            return false;
        return ci >> 3 == trumpf;
    }

    int niedrighoch(int f) {
        int i, j, ok;
        int[] gespsav = new int[32];

        for (i = 0; i < 32; i++)
            gespsav[i] = gespcd[i];
        ok = j = 0;
        do {
            calc_high(1, 0);
            if (ok != 0)
                ok = 2;
            for (i = 0; i < possc; i++) {
                if (cards[possi[i]] == high[f]) {
                    j++;
                    if (f != trumpf || j < 3) {
                        ok = 1;
                        playcd = i;
                        gespcd[cards[possi[i]]] = 2;
                    }
                }
            }
        } while (ok == 1);
        for (i = 0; i < 32; i++)
            gespcd[i] = gespsav[i];
        return ok;
    }

    boolean ueberdoerfer() {
        int i, j;

        if ((trumpf == 4 && (hatnfb[left(spieler)][trumpf] == 0 || hatnfb[right(spieler)][trumpf] == 0))
                || sptruempfe > 4)
            return false;
        calc_high(1, 0);
        for (i = 0; i < possc; i++) {
            for (j = 0; j < 4; j++) {
                if (j != trumpf && cards[possi[i]] == high[j]) {
                    playcd = i;
                    return true;
                }
            }
        }
        return false;
    }

    boolean bubeausspielen() {
        int i, c;

        c = -1;
        calc_inhand(spieler);
        if (inhand[3][BUBE] && inhand[2][BUBE] && inhand[1][BUBE]) {
            c = rnd(1) > 0 ? 1 : rnd(1) > 0 ? 2 : 3;
        } else if (inhand[3][BUBE] && inhand[2][BUBE]) {
            c = rnd(1) > 0 ? 3 : 2;
        } else if (inhand[3][BUBE] && inhand[1][BUBE]) {
            c = rnd(7) > 0 ? 3 : 1;
        } else if (inhand[2][BUBE] && inhand[1][BUBE]) {
            c = rnd(1) > 0 ? 2 : 1;
        }
        if (c >= 0) {
            c = c << 3 | BUBE;
            for (i = 0; i < possc; i++) {
                if (cards[possi[i]] == c) {
                    playcd = i;
                    return true;
                }
            }
        }
        return false;
    }

    boolean trumpfausspielen() {
        int i, j, k, g1, g2, tr, trdr, wi, wj;

        g1 = left(spieler);
        g2 = right(spieler);
        if (hatnfb[g1][trumpf] == 0 || hatnfb[g2][trumpf] == 0) {
            if (trumpf != 4 && bubeausspielen())
                return true;
            if (niedrighoch(trumpf) != 0)
                return true;
        }
        if (trumpf == 4 && hatnfb[g1][trumpf] != 0 && hatnfb[g2][trumpf] != 0) {
            return false;
        }
        calc_high(1, 0);
        tr = wj = 0;
        j = -1;
        for (i = 0; i < possc; i++) {
            if (cards[possi[i]] >> 3 == trumpf || (cards[possi[i]] & 7) == BUBE) {
                tr++;
            }
        }
        trdr = trumpf < 4 ? 7 - gespfb[trumpf] : 0;
        for (i = 0; i < 4; i++)
            if (gespcd[i << 3 | BUBE] == 0)
                trdr++;
        for (i = 0; i < possc; i++) {
            if (cards[possi[i]] >> 3 == trumpf || (cards[possi[i]] & 7) == BUBE) {
                wi = cardValues[cards[possi[i]] & 7];
                if (wi == 2 && trdr - tr != 1)
                    wi = -1;
                if (j < 0 || wi < wj) {
                    j = i;
                    wj = wi;
                }
            }
        }
        k = possc;
        if (trumpf < 4) {
            trdr = 7 - gespfb[trumpf];
            if (wj != -1
                    && (hatnfb[g1][trumpf] != 0 || hatnfb[g2][trumpf] != 0)) {
                calc_inhand(spieler);
                for (i = SIEBEN; i >= DAME; i--) {
                    if (i == BUBE)
                        continue;
                    if (gespcd[trumpf << 3 | i] == 0 && !inhand[trumpf][i]) {
                        for (; i >= KOENIG; i--) {
                            if (i == BUBE)
                                continue;
                            if (inhand[trumpf][i]) {
                                for (k = 0; k < possc; k++) {
                                    if (cards[possi[k]] == (trumpf << 3 | i)) {
                                        break;
                                    }
                                }
                                break;
                            }
                        }
                        break;
                    }
                }
            }
        } else
            trdr = 0;
        for (i = 0; i < 4; i++)
            if (gespcd[i << 3 | BUBE] == 0)
                trdr++;
        if ((tr > 2 && (trumpf != 4 || trdr - tr != 0))
                || (tr > 1 && trdr - tr != 0 && trdr - tr <= 2)) {
            playcd = k != possc
                    && (trdr - tr == 2 || cardValues[cards[possi[k]] & 7] == 0) ? k
                    : j;
            return true;
        }
        for (i = 0; i < possc; i++) {
            for (j = 0; j < 4; j++) {
                if (j != trumpf && cards[possi[i]] == high[j]) {
                    if ((cards[possi[i]] & 7) == AS)
                        playcd = i;
                    else
                        niedrighoch(j);
                    return true;
                }
            }
        }
        return false;
    }

    boolean hochausspielen() {
        int i, j, k;

        calc_high(2, 0);
        for (k = 0; k < 5; k++) {
            j = k != 0 ? k - 1 : trumpf;
            for (i = 0; i < possc; i++) {
                if (cards[possi[i]] == high[j] && (j != trumpf || stich > 6)
                        && (hatnfb[spieler][j] == 0 || stich > 7)) {
                    playcd = i;
                    return true;
                }
            }
        }
        return false;
    }

    void schenke() {
        int i, j, ci, cj, wi, wj, iw, jw;
        int[] ih = new int[4], ze = new int[4], ko = new int[4], da = new int[4], ne = new int[4];

        if (vmh == 0 && trumpf == 4) {
            for (i = 0; i < 4; i++) {
                ih[i] = ze[i] = ko[i] = da[i] = ne[i] = 0;
            }
            for (i = 0; i < possc; i++) {
                ci = cards[possi[i]];
                if ((ci & 7) != BUBE)
                    ih[ci >> 3]++;
                if ((ci & 7) == ZEHN)
                    ze[ci >> 3] = 1;
                else if ((ci & 7) == KOENIG)
                    ko[ci >> 3] = 1;
                else if ((ci & 7) == DAME)
                    da[ci >> 3] = 1;
                else if ((ci & 7) == NEUN)
                    ne[ci >> 3] = 1;
            }
        }
        j = 0;
        for (i = 1; i < possc; i++) {
            ci = cards[possi[i]];
            cj = cards[possi[j]];
            wi = cardValues[iw = (ci & 7)];
            wj = cardValues[jw = (cj & 7)];
            if (wi == 2)
                wi = 5;
            if (wj == 2)
                wj = 5;
            if (wi == 5 && wj == 5) {
                wi = ci >> 3;
                wj = cj >> 3;
            } else {
                if (wi == 0 && gespcd[(ci & ~7) | AS] == 0 && zehnblank(ci)
                        && stich <= 6)
                    wi += 4;
                if (wj == 0 && gespcd[(cj & ~7) | AS] == 0 && zehnblank(cj)
                        && stich <= 6)
                    wj += 4;
            }
            if ((ci & 7) == BUBE || ci >> 3 == trumpf)
                wi += 5;
            if ((cj & 7) == BUBE || cj >> 3 == trumpf)
                wj += 5;
            if (wi < wj
                    || (wi == wj && (((vmh != 0 || trumpf != 4) && iw >= NEUN
                            && jw >= NEUN && iw > jw) || (vmh == 0
                            && trumpf == 4 && ih[ci >> 3] > ih[cj >> 3]))))
                j = i;
        }
        if (vmh == 0 && trumpf == 4) {
            for (i = 1; i < possc; i++) {
                ci = cards[possi[i]];
                cj = cards[possi[j]];
                wi = cardValues[iw = (ci & 7)];
                wj = cardValues[jw = (cj & 7)];
                if (ci >> 3 == cj >> 3 && ze[ci >> 3] != 0 && ko[ci >> 3] != 0
                        && ih[ci >> 3] > 2) {
                    if (((wi == 4 && da[ci >> 3] == 0 && ne[ci >> 3] == 0)
                            || (wi == 3 && ne[ci >> 3] == 0) || iw == NEUN)
                            && wj == 0)
                        j = i;
                }
            }
        }
        playcd = j;
    }

    boolean zehnblank(int ci) {
        int i, f, n, z, a, cj;

        f = ci >> 3;
        n = z = a = 0;
        for (i = 0; i < possc; i++) {
            cj = cards[possi[i]];
            if ((cj & 7) != BUBE && cj >> 3 == f) {
                n++;
                if ((cj & 7) == ZEHN)
                    z = 1;
                else if ((cj & 7) == AS)
                    a = 1;
            }
        }
        return z != 0 && a == 0 && n == 2 && hatnfb[spieler][f] == 0;
    }

    boolean fabwerfen() {
        int i, fb, ci;
        int[] n = new int[4];

        fb = stcd[0] >> 3;
        if (hatnfb[spieler][fb] == 0
                || (vmh == 2 && cardValues[stcd[0] & 7] + cardValues[stcd[1] & 7] > 4)
                || (vmh == 1 && cardValues[stcd[0] & 7] > 0))
            return false;
        n[0] = n[1] = n[2] = n[3] = 0;
        for (i = 0; i < possc; i++) {
            ci = cards[possi[i]];
            if ((ci & 7) != BUBE && ci >> 3 != trumpf) {
                n[ci >> 3]++;
            }
        }
        calc_high(1, 0);
        for (i = 0; i < possc; i++) {
            ci = cards[possi[i]];
            fb = ci >> 3;
            if ((ci & 7) != BUBE && fb != trumpf && cardValues[ci & 7] <= 4
                    && n[fb] == 1 && ci != high[fb]) {
                playcd = i;
                return true;
            }
        }
        return false;
    }

    void abwerfen() {
        int i, j, ci, cj, wi, wj, mi, wio, wjo, h;
        int[] gsp = new int[4], ze = new int[4], as = new int[4], ih = new int[4];

        for (i = 0; i < 4; i++)
            gsp[i] = ze[i] = as[i] = ih[i] = 0;
        for (i = 0; i < 32; i++) {
            if ((i & 7) != BUBE && gespcd[i] == 2)
                gsp[i >> 3]++;
        }
        for (i = 0; i < possc; i++) {
            ci = cards[possi[i]];
            if ((ci & 7) != BUBE)
                ih[ci >> 3]++;
            if ((ci & 7) == ZEHN)
                ze[ci >> 3] = 1;
            else if ((ci & 7) == AS)
                as[ci >> 3] = 1;
        }
        j = 0;
        for (i = 1; i < possc; i++) {
            ci = cards[possi[i]];
            cj = cards[possi[j]];
            wi = cardValues[ci & 7];
            wj = cardValues[cj & 7];
            wio = wi;
            wjo = wj;
            if (wi == 2)
                wi = 5;
            if (wj == 2)
                wj = 5;
            if (wi == 5 && wj == 5) {
                wi = ci >> 3;
                wj = cj >> 3;
            } else {
                if (stich > 7) {
                    wi *= 2;
                    wj *= 2;
                    if (wi == 10 || ci >> 3 == trumpf)
                        wi += 12;
                    if (wj == 10 || cj >> 3 == trumpf)
                        wj += 12;
                    if (hatnfb[spieler][ci >> 3] != 0)
                        wi -= 7;
                    if (hatnfb[spieler][cj >> 3] != 0)
                        wj -= 7;
                } else {
                    if (wi == 5 || ci >> 3 == trumpf)
                        wi += 5;
                    if (wj == 5 || cj >> 3 == trumpf)
                        wj += 5;
                    if (wi < 4 && zehnblank(ci) && stich <= 7)
                        wi += wi != 0 ? 2 : 6;
                    if (wj < 4 && zehnblank(cj) && stich <= 7)
                        wj += wj != 0 ? 2 : 6;
                    if (vmh == 0) {
                        if (trumpf == 4) {
                            if ((ci & 7) != BUBE
                                    && hatnfb[spieler][ci >> 3] != 0)
                                wi -= 30;
                            if ((cj & 7) != BUBE
                                    && hatnfb[spieler][cj >> 3] != 0)
                                wj -= 30;
                        } else {
                            mi = spieler == left(ausspl) ? 2 : 1;
                            wio = wi;
                            wjo = wj;
                            if (hatnfb[spieler][ci >> 3] == 0)
                                wi += 8;
                            else if (hatnfb[spieler][ci >> 3] != 0
                                    && hatnfb[(ausspl + mi) % 3][ci >> 3] != 1
                                    && ih[ci >> 3] + gsp[ci >> 3] > 4
                                    && as[ci >> 3] == 0
                                    && gespcd[(ci & ~7) | AS] != 2) {
                                wi += 35;
                            } else if (wi > 4)
                                wi += 8;
                            if (hatnfb[spieler][cj >> 3] == 0)
                                wj += 8;
                            else if (hatnfb[spieler][cj >> 3] != 0
                                    && hatnfb[(ausspl + mi) % 3][cj >> 3] != 1
                                    && ih[cj >> 3] + gsp[cj >> 3] > 4
                                    && as[cj >> 3] == 0
                                    && gespcd[(cj & ~7) | AS] != 2) {
                                wj += 35;
                            } else if (wj > 4)
                                wj += 8;
                            if (mi == 2
                                    && hatnfb[(ausspl + mi) % 3][trumpf] != 1) {
                                h = 0;
                                if (hatnfb[(ausspl + mi) % 3][ci >> 3] == 1
                                        && wio <= 4) {
                                    wi -= 30;
                                    h++;
                                }
                                if (hatnfb[(ausspl + mi) % 3][cj >> 3] == 1
                                        && wjo <= 4) {
                                    wj -= 30;
                                    h++;
                                }
                                if (h == 2) {
                                    int hv = wi;
                                    wi = wj;
                                    wj = hv;
                                }
                            }
                        }
                        if (wi == wj && stich <= 3 && ci >> 3 != cj >> 3) {
                            if (ih[ci >> 3] < ih[cj >> 3])
                                wi--;
                            else if (ih[ci >> 3] > ih[cj >> 3])
                                wj--;
                            else if (ih[ci >> 3] == 2) {
                                if (as[ci >> 3] != 0)
                                    wi -= spieler == left(ausspl) ? 1 : -1;
                                if (as[cj >> 3] != 0)
                                    wj -= spieler == left(ausspl) ? 1 : -1;
                            }
                            if (spieler == left(ausspl) || trumpf == 4) {
                                int hv = wi;
                                wi = wj;
                                wj = hv;
                            }
                        }
                    } else {
                        if (possc == 2
                                && ((stcd[0] & 7) == BUBE || stcd[0] >> 3 == trumpf)
                                && (wio == 2 || wjo == 2)
                                && (wio >= 10 || wjo >= 10)) {
                            if (wio >= 10) {
                                wi = 1;
                                wj = 2;
                            } else {
                                wi = 2;
                                wj = 1;
                            }
                            if (((gespcd[BUBE] == 2 && (gespcd[trumpf << 3 | AS] == 2
                                    || wio == 11 || wjo == 11))
                                    || ci == BUBE
                                    || cj == BUBE
                                    || gespcd[2 << 3 | BUBE] != 2 || gespcd[3 << 3
                                    | BUBE] != 2)
                                    && !(gespcd[2 << 3 | BUBE] == 2
                                            && gespcd[3 << 3 | BUBE] != 2 && vmh == 1)) {
                                int hv = wi;
                                wi = wj;
                                wj = hv;
                            }
                        } else {
                            if ((ci & 7) == BUBE)
                                wi += 5;
                            else if (hatnfb[spieler][ci >> 3] == 0 && wi >= 4)
                                wi += 3;
                            if ((cj & 7) == BUBE)
                                wj += 5;
                            else if (hatnfb[spieler][cj >> 3] == 0 && wj >= 4)
                                wj += 3;
                            if (vmh == 1 && spieler != ausspl) {
                                if (wi > 1 && wi < 5 && wj == 0
                                        && cardValues[stcd[0] & 7] == 0
                                        && hatnfb[spieler][stcd[0] >> 3] != 0) {
                                    wi = 1;
                                    wj = 2;
                                } else if (wj > 1 && wj < 5 && wi == 0
                                        && cardValues[stcd[0] & 7] == 0
                                        && hatnfb[spieler][stcd[0] >> 3] != 0) {
                                    wi = 2;
                                    wj = 1;
                                }
                            }
                        }
                    }
                }
            }
            if (wi < wj
                    || (wi == wj && cardValues[ci & 7] == 0 && cardValues[cj & 7] == 0 && (ci & 7) > (cj & 7)))
                j = i;
        }
        playcd = j;
    }

    boolean buttern() {
        int fb, mi, se;

        se = left(ausspl);
        mi = spieler == ausspl ? right(ausspl) : ausspl;
        fb = stcd[0] >> 3;
        if ((stcd[0] & 7) == BUBE)
            fb = trumpf;
        if (stich == 9 && spitzeang)
            return true;
        if (hatnfb[se][fb] == 0)
            return false;
        calc_high(2, 0);
        if (spieler == ausspl) {
            if ((fb == trumpf
                    && (trumpf < 4 ? gespcd[trumpf << 3 | AS] == 2 : false)
                    && gespcd[0 | BUBE] == 2 && gespcd[1 << 3 | BUBE] == 2
                    && gespcd[2 << 3 | BUBE] == 2 && gespcd[3 << 3 | BUBE] == 2 && rnd(1) > 0)
                    || ((stcd[0] & 7) == BUBE && gespcd[2 << 3 | BUBE] == 2 && gespcd[3 << 3
                            | BUBE] != 2)
                    || higher(stcd[0], high[fb])
                    || (hatnfb[mi][fb] == 1 && hatnfb[mi][trumpf] == 1)
                    || (trumpf == 4 && (stcd[0] & 7) != BUBE && (gespcd[0
                            | BUBE] == 2
                            || gespcd[1 << 3 | BUBE] == 2
                            || gespcd[2 << 3 | BUBE] == 2 || gespcd[3 << 3
                            | BUBE] == 2))
                    || (cardValues[stcd[0] & 7] > 4 && rnd(1) > 0))
                return false;
            if (butternok != 0)
                return rnd(1) != 0;
            butternok = rnd(1);
            return true;
        }
        if (higher(stcd[0], high[trumpf]) && higher(stcd[0], high[fb]))
            return true;
        return higher(stcd[0], high[fb]) && hatnfb[spieler][fb] == 0;
    }

    boolean hatas() {
        int f, i, as;

        f = stcd[0] >> 3;
        as = 0;
        for (i = 0; i < possc; i++) {
            if (cards[possi[i]] == (f << 3 | AS))
                as = i + 1;
        }
        if (as == 0 || (stcd[0] & 7) == BUBE || f == trumpf
                || cardValues[stcd[0] & 7] > 4 || hatnfb[spieler][f] != 0)
            return false;
        playcd = as - 1;
        return true;
    }

    boolean schnippeln(int f) {
        int fb, i, j, k, as;
        boolean hi;

        if (gstsum >= 44 && gstsum < 60)
            return false;
        if (stich > 8 && gstsum <= 30)
            return false;
        fb = stcd[0] >> 3;
        if ((stcd[0] & 7) == BUBE || (stcd[f] & 7) == BUBE || fb == trumpf
                || stcd[f] >> 3 == trumpf || (f != 0 && fb != stcd[1] >> 3)
                || gespcd[fb << 3 | ZEHN] == 2 || gespfb[fb] > 3) {
            return false;
        }
        as = 0;
        for (i = 0; i < possc; i++) {
            if (cards[possi[i]] == (fb << 3 | AS))
                as = i + 1;
            if (cards[possi[i]] == (fb << 3 | ZEHN))
                return false;
        }
        if (as == 0)
            return false;
        possi[as - 1] = possi[--possc];
        j = k = 0;
        for (i = 1; i < possc; i++) {
            if (cards[possi[i]] < cards[possi[j]])
                j = i;
            if (cards[possi[i]] > cards[possi[k]])
                k = i;
        }
        hi = f != 0 ? higher(stcd[0], stcd[1]) ^ (spieler == ausspl)
                : cards[possi[j]] < stcd[0];
        playcd = hi ? j : k;
        return true;
    }

    void nichtspitze() {
        int sp, i;

        if (spitzeang) {
            sp = trumpf == 4 ? BUBE : SIEBEN | trumpf << 3;
            for (i = 0; i < possc; i++) {
                if (cards[possi[i]] == sp) {
                    possc--;
                    for (; i < possc; i++) {
                        possi[i] = possi[i + 1];
                    }
                    return;
                }
            }
        }
    }

    boolean spitzefangen() {
        int i, c, t;

        if (!spitzeang || stich != 9)
            return false;
        t = -1;
        for (i = 0; i < possc; i++) {
            if (((c = cards[possi[i]]) & 7) == BUBE || c >> 3 == trumpf) {
                if (t != -1)
                    return false;
                t = i;
            }
        }
        if (t == -1)
            return false;
        playcd = t != 0 ? 0 : 1;
        return true;
    }

    boolean restbeimir() {
        int c, h, i, j, k;
        int[] s = new int[2];

        if (stich == 10 || vmh != 0 || ausspl != spieler || trumpf < 0
                || trumpf > 4)
            return false;
        s[0] = left(spieler);
        s[1] = right(spieler);
        if (hatnfb[s[0]][trumpf] == 0 || hatnfb[s[1]][trumpf] == 0) {
            if (trumpf == 4)
                return false;
            h = -1;
            for (k = 0; k < 10; k++) {
                if ((c = cards[spieler * 10 + k]) >= 0) {
                    if (c >> 3 != trumpf && (c & 7) != BUBE)
                        return false;
                    if (h < 0 || !higher(c, cards[spieler * 10 + h]))
                        h = k;
                }
            }
            for (j = 0; j < 2; j++) {
                for (k = handsp ? -1 : 0; k < 10; k++) {
                    if ((c = k < 0 ? prot2.skat[0][j] : cards[s[j] * 10 + k]) >= 0
                            && higher(c, cards[spieler * 10 + h]))
                        return false;
                }
            }
            return true;
        }
        for (i = 0; i < 4; i++) {
            if (i == trumpf || (hatnfb[s[0]][i] != 0 && hatnfb[s[1]][i] != 0))
                continue;
            h = SIEBEN + 1;
            for (j = 0; j < 2; j++) {
                for (k = handsp ? -1 : 0; k < 10; k++) {
                    if ((c = k < 0 ? prot2.skat[0][j] : cards[s[j] * 10 + k]) >= 0
                            && c >> 3 == i && (c & 7) != BUBE && (c & 7) < h)
                        h = c & 7;
                }
            }
            for (k = 0; k < 10; k++) {
                if ((c = cards[spieler * 10 + k]) >= 0 && c >> 3 == i
                        && (c & 7) != BUBE && (c & 7) > h)
                    return false;
            }
        }
        return true;
    }

    void m_bvsp() {
        if (ueberdoerfer())
            return;
        if (!trumpfausspielen())
            schenke();
    }

    void m_bmsp() {
        if (fabwerfen())
            return;
        if (!uebernehmen(0, 1, 1))
            schenke();
    }

    void m_bhsp() {
        if (gewinnstich(1))
            return;
        if (fabwerfen())
            return;
        if (!uebernehmen(higher(stcd[0], stcd[1]) ? 0 : 1, 1, 0))
            schenke();
    }

    void m_bvns() {
        if (spitzefangen())
            return;
        if (spieler == left(ausspl) && karobubespielen())
            return;
        if (!hochausspielen())
            abwerfen();
    }

    void m_bmns() {
        if (spitzefangen())
            return;
        if (karobubespielen())
            return;
        if (spieler == ausspl) {
            if ((rnd(3) > 0 && schnippeln(0))
                    || (!ignorieren() && uebernehmen(0, 1, 0)))
                return;
        } else {
            if (einstechen() || hatas())
                return;
        }
        if (buttern())
            schmieren();
        else
            abwerfen();
    }

    void m_bhns() {
        if (gewinnstich(0))
            return;
        if (spitzefangen())
            return;
        if (rnd(1) > 0 && karobubespielen())
            return;
        if (rnd(3) > 0 && schnippeln(1))
            return;
        if (higher(stcd[0], stcd[1]) ^ (spieler != ausspl)) {
            if (!genugdrin() || !uebernehmen(spieler != ausspl ? 1 : 0, 1, 0))
                abwerfen();
        } else {
            schmieren();
        }
    }

    void m_bsp() {
        playcd = 0;
        nichtspitze();
        if (vmh == 0)
            m_bvsp();
        else if (vmh == 1)
            m_bmsp();
        else
            m_bhsp();
    }

    void m_bns() {
        playcd = 0;
        if (vmh == 0)
            m_bvns();
        else if (vmh == 1)
            m_bmns();
        else
            m_bhns();
    }

    void make_best(int s) {
        if (possc == 1)
            playcd = 0;
        else if (trumpf > 4) {
            m_bramsch();
        } else if (trumpf >= 0) {
            if (s == spieler)
                m_bsp();
            else
                m_bns();
        } else {
            if (s == spieler)
                m_nsp();
            else
                m_nns(s);
        }
    }

    void adjfb(int s, int v) {
        int i, c, n;
        boolean[] fb = new boolean[5];

        fb[0] = fb[1] = fb[2] = fb[3] = fb[4] = false;
        n = handsp && s != spieler ? 12 : 10;
        for (i = 0; i < n; i++) {
            if ((c = i < 10 ? cards[10 * s + i] : prot2.skat[0][i - 10]) >= 0) {
                if (trumpf != -1 && (c & 7) == BUBE)
                    fb[trumpf] = true;
                else
                    fb[c >> 3] = true;
            }
        }
        for (i = 0; i < 5; i++) {
            if (!fb[i]) {
                if (hatnfb[s][i] != 1)
                    hatnfb[s][i] = v;
            }
        }
    }

    void do_spielen() {
        int s, i;

        if (phase != SPIELEN) {
            sp = 0;
            return;
        }
        if (!layedOut) {
            waitForLayout = true;
            return;
        }
        s = (ausspl + vmh) % 3;
        if (iscomp(s))
            sp = 0;
        else {
            if (sp == s + 1 && lvmh == vmh)
                return;
            sp = s + 1;
        }
        lvmh = vmh;
        if (s == spieler && trumpf != 5) {
            adjfb(left(spieler), 2);
            adjfb(right(spieler), 2);
            for (i = 0; i < 5; i++) {
                if (hatnfb[left(spieler)][i] == 0
                        || hatnfb[right(spieler)][i] == 0) {
                    if (hatnfb[left(spieler)][i] == 2)
                        hatnfb[left(spieler)][i] = 0;
                    if (hatnfb[right(spieler)][i] == 2)
                        hatnfb[right(spieler)][i] = 0;
                }
            }
        }
        if (ouveang) {
            adjfb(spieler, 1);
        }
        calc_poss(s);
        if (trumpf == -1 && stich == 1 && sp != 0)
            testnull(s);
        make_best(s);
        hintcard[0] = cards[possi[playcd]];
        if (sp != 0 && hints[s]) {
            show_hint(s, 0, true);
        }
        if (!ndichtw && restbeimir()) {
            di_dicht();
            return;
        }
        if (sp != 0)
            return;
        drop_card(possi[playcd], s);
    }

    void computer() {
        if (phase == GEBEN)
            do_geben();
        if (phase == REIZEN)
            do_reizen();
        if (trumpf == -1 && stich == 1)
            init_null();
        do_spielen();
    }

    void play() {
        if (!resumebock || playbock == 0) {
            bockspiele = 0;
            bockinc = ramschspiele = 0;
        } else if (playbock != 2) {
            ramschspiele = 0;
        }
        phase = GEBEN;
        computer();
    }

    void main() {
        setrnd(0, savseed = (int) (new Date().getTime()));
        numsp = 1;
        geber = 0;
        read_list();
        restart = prefs.getBoolean("restart", false)
                && xskatVersion.equals(prefs.getString("xskatVersion", "-"));
        initCallback();
        firstgame = prefs.getBoolean("firstgame", true);
        if (restart) {
            readState();
            readViews();
        } else {
            rem_fbox(0);
            rem_box(1);
            rem_box(2);
            play();
        }
    }

    // --------------------------------------------------------------------------------------
    // File null.c
    // --------------------------------------------------------------------------------------

    void init_null() {
        int i;

        for (i = 0; i < 4; i++) {
            wirftabfb[i] = false;
            hattefb[i] = false;
            aussplfb[i] = false;
            nochinfb[i] = 8;
        }
    }

    void testnull(int sn) {
        boolean f;
        int i, c;
        int[] a = new int[4], l = new int[4], n = new int[4], m = new int[4], h = new int[4], s = new int[4];
        int[] sk = new int[2], ufb = new int[1], mfb = new int[4], sfb = new int[4];

        naussplfb[sn] = -1;
        sk[0] = cards[30];
        sk[1] = cards[31];
        if (null_dicht(sn, true, sk, ufb, mfb, sfb)) {
            for (i = 0; i < 4 && mfb[i] != 0; i++)
                ;
            if (sn != ausspl || i < 4) {
                if (sn == ausspl)
                    naussplfb[sn] = i;
                maxrw[sn] = nullw[revolution ? 4 : 3]; // TODO ouvert
                maxrw[sn] = nullw[1];
                return;
            }
        }
        for (i = 0; i < 4; i++)
            a[i] = l[i] = n[i] = m[i] = h[i] = s[i] = 0;
        f = true;
        for (i = 0; i < 10; i++) {
            c = cards[10 * sn + i];
            a[c >> 3]++;
            if ((c & 7) > BUBE)
                l[c >> 3]++;
            else if ((c & 7) < BUBE && (c & 7) != ZEHN)
                h[c >> 3] = 1;
            else
                m[c >> 3] = 1;
            if ((c & 7) == NEUN)
                n[c >> 3] = 1;
            if ((c & 7) == SIEBEN)
                s[c >> 3] = 1;
        }
        for (i = 0; i < 4; i++) {
            if ((a[i] != 0 && l[i] != a[i] && l[i] < 2)
                    || (l[i] == 1 && n[i] != 0)
                    || (l[i] != 3 && m[i] == 0 && h[i] != 0)
                    || (a[i] > 2 && s[i] == 0))
                f = false;
        }
        if (f)
            maxrw[sn] = nullw[1];
    }

    boolean kleiner_w(int w1, int w2) {
        if (w1 == ZEHN)
            return w2 <= BUBE;
        if (w2 == ZEHN)
            return w1 > BUBE;
        return w1 > w2;
    }

    boolean kleiner(int i, int j) {
        return kleiner_w(cards[possi[i]] & 7, cards[possi[j]] & 7);
    }

    boolean hat(int i) {
        return hatnfb[spieler][cards[possi[i]] >> 3] == 0;
    }

    int n_anwert(int c) {
        int fb, i, m;

        fb = c >> 3;
        if (hatnfb[spieler][fb] != 0)
            return 0;
        for (i = AS; i <= SIEBEN; i = i == AS ? KOENIG : i == BUBE ? ZEHN
                : i == ZEHN ? NEUN : i + 1) {
            if (c == (fb << 3 | i))
                return 1;
            if (gespcd[fb << 3 | i] != 2)
                break;
        }
        if ((c & 7) == SIEBEN) {
            m = left(ausspl) != spieler ? left(ausspl) : right(ausspl);
            if (hatnfb[m][fb] != 1 && nochinfb[fb] > 4)
                return 2;
        }
        if (wirftabfb[fb])
            return 5;
        if (aussplfb[fb])
            return 3;
        if (hattefb[fb])
            return 6;
        return 4;
    }

    int n_anspiel() {
        int i, j, ci, cj, wi, wj;

        j = 0;
        for (i = 1; i < possc; i++) {
            ci = cards[possi[i]];
            cj = cards[possi[j]];
            wi = n_anwert(ci);
            wj = n_anwert(cj);
            if (wi > wj || (wi == wj && kleiner(i, j)))
                j = i;
        }
        return j;
    }

    int n_abwert(int c) {
        int fb, i, n;

        fb = c >> 3;
        if ((c & 7) >= ACHT)
            return 0;
        for (i = 0; i < possc; i++) {
            if (cards[possi[i]] == (fb << 3 | SIEBEN))
                return 1;
        }
        if (hatnfb[spieler][fb] != 0)
            return 2;
        if (aussplfb[fb])
            return 3;
        if (wirftabfb[fb])
            return 7;
        if (hattefb[fb])
            return 6;
        n = 0;
        for (i = 0; i < possc; i++) {
            if (cards[possi[i]] >> 3 == fb)
                n++;
        }
        if (n < 3)
            return 5;
        return 4;
    }

    int n_abwerfen() {
        int i, j, ci, cj, wi, wj;

        j = 0;
        for (i = 1; i < possc; i++) {
            ci = cards[possi[i]];
            cj = cards[possi[j]];
            wi = n_abwert(ci);
            wj = n_abwert(cj);
            if (wi > wj || (wi == wj && !kleiner(i, j)))
                j = i;
        }
        return j;
    }

    int minmax(boolean f) {
        int i, j;
        boolean hi, hj;

        j = 0;
        for (i = 1; i < possc; i++) {
            hi = hat(i);
            hj = hat(j);
            if ((hi && !hj) || (kleiner(i, j) ^ f && (hi || !hj)))
                j = i;
        }
        return j;
    }

    int minmaxfb(boolean f, int fb) {
        int i, j = -1;

        for (i = 0; i < possc; i++) {
            if ((j < 0 && cards[possi[i]] >> 3 == fb)
                    || (cards[possi[i]] >> 3 == fb && kleiner(i, j) ^ f))
                j = i;
        }
        return Math.max(j, 0);
    }

    int drunter(boolean f) {
        int i, j;

        j = 0;
        for (i = 1; i < possc; i++) {
            if (higher(cards[possi[j]], cards[possi[i]]))
                j = i;
        }
        for (i = 0; i < possc; i++) {
            if (higher(stcd[f ? 1 : 0], cards[possi[i]])
                    && higher(cards[possi[i]], cards[possi[j]]))
                j = i;
        }
        return j;
    }

    int drunterdrue() {
        int i, w, fb;

        fb = stcd[0] >> 3;
        i = -1;
        for (w = stcd[0] & 7; w >= AS; w = w == NEUN ? ZEHN : w == ZEHN ? BUBE
                : w == KOENIG ? AS : w - 1) {
            if (i < 0) {
                i = 0;
                continue;
            }
            for (i = 0; i < possc; i++) {
                if (cards[possi[i]] == (fb << 3 | w))
                    return i;
            }
            if (gespcd[fb << 3 | w] != 2)
                break;
        }
        return drunter(false);
    }

    void m_nsp() {
        int[] ufb = new int[1], mfb = new int[4], sfb = new int[4];

        if (vmh == 0) {
            if (naussplfb[spieler] == -1) {
                playcd = minmax(false);
            } else {
                playcd = minmaxfb(false, naussplfb[spieler]);
            }
        } else if (hatnfb[spieler][stcd[0] >> 3] != 0) {
            if (null_dicht(spieler, handsp, prot2.skat[1], ufb, mfb, sfb)) {
                playcd = minmax(true);
            } else {
                playcd = minmaxfb(true, ufb[0]);
            }
        } else
            playcd = drunter(vmh == 2 ? !higher(stcd[0], stcd[1]) : false);
    }

    void m_nns(int s) {
        boolean sga;

        if (revolang && spieler != ausspl) {
            playcd = minmax(false);
            return;
        }
        sga = spieler == ausspl;
        if (vmh == 0)
            playcd = n_anspiel();
        else if (hatnfb[s][stcd[0] >> 3] != 0)
            playcd = n_abwerfen();
        else if (vmh == 1) {
            if (sga)
                playcd = drunter(false);
            else
                playcd = drunterdrue();
        } else if (higher(stcd[0], stcd[1]) ^ sga) {
            playcd = minmax(true);
        } else {
            playcd = minmax(false);
            if (!higher(stcd[sga ? 0 : 1], cards[possi[playcd]])) {
                playcd = minmax(true);
            }
        }
    }

    void null_stich() {
        int i, fb1, fb2;

        for (i = 0; i < 3; i++) {
            nochinfb[stcd[i] >> 3]--;
        }
        fb1 = stcd[0] >> 3;
        if (ausspl != spieler) {
            fb2 = stcd[(spieler - ausspl + 3) % 3] >> 3;
            if (fb1 != fb2) {
                wirftabfb[fb2] = true;
            } else {
                hattefb[fb2] = true;
            }
        } else {
            aussplfb[fb1] = true;
            hattefb[fb1] = true;
        }
    }

    void null_sort(int[] arr, int cnt) {
        int i;
        boolean swp;

        do {
            swp = false;
            for (i = 0; i < cnt - 1; i++) {
                if (kleiner_w(arr[i + 1], arr[i])) {
                    swap(arr, i, i + 1);
                    swp = true;
                }
            }
        } while (swp);
    }

    boolean null_dicht(int sn, boolean hnd, int[] cd, int[] ufb, int[] mfb,
            int[] sfb) {
        int i, c, fb, spc, nsc, cnt, el;
        int[] sp = new int[8], ns = new int[8], sfbc = new int[4];

        for (fb = 0; fb < 4; fb++) {
            spc = nsc = 0;
            for (i = 0; i < (hnd ? 32 : 30); i++) {
                if (i >= 30)
                    c = cd[i - 30];
                else
                    c = cards[i];
                if (c != -1 && c >> 3 == fb) {
                    if (sn * 10 <= i && i <= sn * 10 + 9)
                        sp[spc++] = c & 7;
                    else
                        ns[nsc++] = c & 7;
                }
            }
            el = fb;
            sfbc[el] = spc;
            for (i = 0; i < fb; i++) {
                if (sfbc[el] < sfbc[sfb[i]]) {
                    int h = el;
                    el = sfb[i];
                    sfb[i] = h;
                }
            }
            sfb[fb] = el;
            if (spc != 0) {
                if (nsc != 0) {
                    null_sort(sp, spc);
                    null_sort(ns, nsc);
                    cnt = Math.min(nsc, spc);
                    for (i = 0; i < cnt; i++) {
                        if (kleiner_w(ns[i], sp[i])) {
                            ufb[0] = fb;
                            return false;
                        }
                    }
                    if (nsc < 3 && hnd)
                        mfb[fb] = 1;
                    else if (spc > 1 && nsc > 1) {
                        mfb[fb] = 0;
                        for (i = 1; i < cnt; i++) {
                            if (kleiner_w(ns[i - 1], sp[i])) {
                                mfb[fb] = 1;
                                break;
                            }
                        }
                    } else
                        mfb[fb] = 0;
                } else
                    mfb[fb] = 1;
            } else
                mfb[fb] = 2;
        }
        return true;
    }

    void revolutiondist() {
        int i, j, k, p, c, sn, mi, fb, cnt;
        int[][] cd = new int[4][8];
        int[] cdc = new int[4], mfb = new int[4], ct = new int[3], sfb = new int[4], ufb = new int[1], sk = new int[2];

        sn = spieler == ausspl ? left(spieler) : ausspl;
        mi = left(sn) == spieler ? left(spieler) : left(sn);
        if (null_dicht(spieler, false, sk, ufb, mfb, sfb)) {
            for (fb = 0; fb < 4 && mfb[fb] != 1; fb++)
                ;
            if (spieler != ausspl || fb == 4)
                return;
            ct[0] = ct[1] = ct[2] = 0;
            for (fb = 0; fb < 4; fb++) {
                if (mfb[sfb[fb]] == 1) {
                    p = ct[sn] < ct[mi] ? sn : mi;
                    for (j = 0, k = sn; j < 2 && ct[p] != 10; j++, k = mi) {
                        for (i = 0; i < 10 && ct[p] != 10; i++) {
                            c = cards[10 * k + i];
                            if (c >> 3 == sfb[fb]) {
                                swap(cards, 10 * k + i, 10 * p + ct[p]);
                                ct[p]++;
                            }
                        }
                    }
                }
            }
            return;
        }
        cdc[0] = cdc[1] = cdc[2] = cdc[3] = 0;
        cnt = 0;
        for (j = 0, k = sn; j < 2; j++, k = mi) {
            for (i = 0; i < 10; i++) {
                c = cards[10 * k + i];
                if (c >> 3 == ufb[0]) {
                    swap(cards, 10 * k + i, 10 * sn + cnt);
                    cnt++;
                }
            }
        }
        for (j = 0, k = sn; j < 2; j++, k = mi) {
            for (i = 0; i < 10; i++) {
                c = cards[10 * k + i];
                cd[c >> 3][cdc[c >> 3]++] = c & 7;
            }
        }
        for (fb = 0; fb < 4; fb++) {
            null_sort(cd[fb], cdc[fb]);
        }
        fb = 0;
        while (cnt < 10) {
            while (fb == ufb[0] || cdc[fb] == 0)
                fb = (fb + 1) % 4;
            for (j = 0, k = sn; j < 2; j++, k = mi) {
                for (i = 0; i < 10; i++) {
                    c = cards[10 * k + i];
                    if (c == (fb << 3 | cd[fb][cdc[fb] - 1])) {
                        swap(cards, 10 * k + i, 10 * sn + cnt);
                        cnt++;
                        cdc[fb]--;
                        fb = (fb + 1) % 4;
                        i = 10;
                        j = 2;
                    }
                }
            }
        }
    }

    // --------------------------------------------------------------------------------------
    // File ramsch.c
    // --------------------------------------------------------------------------------------

    void start_ramsch() {
        vmh = 0;
        spieler = geber;
        save_skat(1);
        home_skat();
        remmark(0);
        phase = SPIELEN;
        View vp = findViewById(R.id.playVMHLeft);
        playVMHLeftBgr = 8;
        vp.setBackgroundResource(gameSymb(playVMHLeftBgr));
        vp = findViewById(R.id.playVMHRight);
        playVMHRightBgr = 8;
        vp.setBackgroundResource(gameSymb(playVMHRightBgr));
        rem_fbox(0);
        rem_box(1);
        rem_box(2);
        setGone(R.id.rowSkat);
        setVisible(R.id.rowStich);
        setInvisible(R.id.cardStichLeft);
        setInvisible(R.id.cardStichMiddle);
        setInvisible(R.id.cardStichRight);
    }

    void init_ramsch() {
        int sn;

        sramschstufe = 0;
        trumpf = 5;
        spieler = geber;
        reizp = -1;
        stich = 1;
        handsp = false;
        vmh = 0;
        ouveang = false;
        sort2[0] = sort2[1] = sort2[2] = 0;
        prot2.sramsch = playsramsch;
        save_skat(0);
        info_reiz();
        info_spiel();
        View vp = findViewById(R.id.playVMHLeft);
        playVMHLeftBgr = geber == 0 ? 1 : 8;
        vp.setBackgroundResource(gameSymb(playVMHLeftBgr));
        vp = findViewById(R.id.playVMHRight);
        playVMHRightBgr = geber == 1 ? 1 : 8;
        vp.setBackgroundResource(gameSymb(playVMHRightBgr));
        for (sn = 0; sn < numsp; sn++) {
            initscr(sn, 1);
        }
        for (sn = 0; sn < 3; sn++) {
            rstsum[sn] = 0;
            rstich[sn] = ggdurchm[sn] = false;
        }
        if (playsramsch != 0 || (ramschspiele != 0 && klopfen)) {
            phase = DRUECKEN;
            di_schieben();
        } else {
            start_ramsch();
        }
    }

    boolean zweibuben() {
        int c0, c1, gespb;

        if (stich != 9 || possc != 2 || ((c0 = cards[possi[0]]) & 7) != BUBE
                || ((c1 = cards[possi[1]]) & 7) != BUBE)
            return false;
        gespb = (gespcd[0 | BUBE] == 2 ? 1 : 0)
                + (gespcd[1 << 3 | BUBE] == 2 ? 1 : 0)
                + (gespcd[2 << 3 | BUBE] == 2 ? 1 : 0)
                + (gespcd[3 << 3 | BUBE] == 2 ? 1 : 0);
        if (vmh == 0 || (vmh == 1 && (stcd[0] & 7) != BUBE)) {
            if ((c0 >> 3) == 3 || (c1 >> 3) == 3) {
                if ((c0 >> 3) == 0 || (c1 >> 3) == 0) {
                    if (gespb != 0) {
                        playcd = (c1 >> 3) == 0 ? 1 : 0;
                    } else {
                        playcd = (c1 >> 3) == 3 ? 1 : 0;
                    }
                } else {
                    playcd = (c0 >> 3) == 3 ? 1 : 0;
                }
                return true;
            }
            if ((c0 >> 3) == 2 || (c1 >> 3) == 2) {
                if ((c0 >> 3) == 0 || (c1 >> 3) == 0) {
                    if (gespb != 0) {
                        playcd = (c1 >> 3) == 0 ? 1 : 0;
                    } else {
                        playcd = (c1 >> 3) == 2 ? 1 : 0;
                    }
                }
                return true;
            }
            return true;
        }
        if (vmh == 1
                || (vmh == 2 && ((stcd[0] & 7) != BUBE ? 1 : 0)
                        + ((stcd[1] & 7) != BUBE ? 1 : 0) == 1)) {
            if ((c0 >> 3) == 3 || (c1 >> 3) == 3) {
                if ((c0 >> 3) == 0 || (c1 >> 3) == 0) {
                    if (gespb > 1) {
                        playcd = (c1 >> 3) == 0 ? 1 : 0;
                    } else {
                        playcd = (c1 >> 3) == 3 ? 1 : 0;
                    }
                    return true;
                }
                if ((c0 >> 3) == 1 || (c1 >> 3) == 1) {
                    if (gespb > 1) {
                        playcd = (c1 >> 3) == 1 ? 1 : 0;
                    } else {
                        if (stcd[0] == (2 << 3 | BUBE)
                                || (vmh == 2 && stcd[1] == (2 << 3 | BUBE))) {
                            playcd = (c1 >> 3) == 1 ? 1 : 0;
                        } else {
                            playcd = (c1 >> 3) == 3 ? 1 : 0;
                        }
                    }
                    return true;
                }
                return true;
            }
            if ((c0 >> 3) == 2 || (c1 >> 3) == 2) {
                if ((c0 >> 3) == 0 || (c1 >> 3) == 0) {
                    if (gespb > 1) {
                        playcd = (c1 >> 3) == 0 ? 1 : 0;
                    } else {
                        if (stcd[0] == (1 << 3 | BUBE)
                                || (vmh == 2 && stcd[1] == (1 << 3 | BUBE))) {
                            playcd = (c1 >> 3) == 0 ? 1 : 0;
                        } else {
                            playcd = (c1 >> 3) == 2 ? 1 : 0;
                        }
                    }
                }
                return true;
            }
            return true;
        }
        if ((stcd[0] & 7) != BUBE && (stcd[1] & 7) != BUBE) {
            playcd = (c1 >> 3) == 3 || (c1 >> 3) == 2 ? 1 : 0;
        } else {
            playcd = (c1 >> 3) == 1 || (c1 >> 3) == 0 ? 1 : 0;
        }
        return true;
    }

    boolean bubeanspielen() {
        int bb, nbb, j;

        bb = -1;
        nbb = 0;
        for (j = 0; j < possc; j++) {
            if ((cards[possi[j]] & 7) == BUBE) {
                nbb++;
                if (cards[possi[j]] >> 3 != 0) {
                    bb = j;
                }
            }
        }
        if (nbb > 1 || bb < 0)
            return false;
        for (j = 0; j < 4; j++) {
            if (gespcd[j << 3 | BUBE] == 2) {
                return false;
            }
        }
        playcd = bb;
        return true;
    }

    boolean sicher(int fb, int[] pc, int[] le) {
        int i, j, mkz, akz;
        int[] mk = new int[7], ak = new int[7], p = new int[7];

        le[0] = 0;
        if (hatnfb[left(ausspl + vmh)][fb] == 1
                && hatnfb[right(ausspl + vmh)][fb] == 1) {
            return false;
        }
        mkz = akz = 0;
        for (i = 7; i >= 0; i--) {
            if (i == BUBE)
                continue;
            if (gespcd[fb << 3 | i] != 2) {
                for (j = 0; j < possc; j++) {
                    if (cards[possi[j]] == (fb << 3 | i))
                        break;
                }
                if (j < possc) {
                    mk[mkz] = i;
                    p[mkz] = j;
                    mkz++;
                } else
                    ak[akz++] = i;
            }
        }
        for (i = 0; i < mkz && i < akz; i++) {
            if (mk[i] < ak[i])
                break;
        }
        if (i < mkz && i < akz) {
            pc[0] = p[mkz > 1 ? 1 : 0];
            if ((cards[possi[pc[0]]] & 7) <= ZEHN) {
                pc[0] = p[0];
            }
            if (mkz == 1 && (cards[possi[pc[0]]] & 7) > ZEHN) {
                le[0] = 1;
            }
            return true;
        }
        return false;
    }

    void moeglklein() {
        int pc, fb, fp;
        boolean mgb, mgp;

        for (pc = 1; pc < possc; pc++) {
            fb = cards[possi[pc]] >> 3;
            fp = cards[possi[playcd]] >> 3;
            mgb = (vmh != 0 || hatnfb[left(ausspl)][fb] != 1 || hatnfb[right(ausspl)][fb] != 1);
            mgp = (vmh != 0 || hatnfb[left(ausspl)][fp] != 1 || hatnfb[right(ausspl)][fp] != 1);
            if ((cards[possi[playcd]] & 7) == BUBE) {
                if ((cards[possi[pc]] & 7) == BUBE) {
                    if (cards[possi[pc]] >> 3 > cards[possi[playcd]] >> 3) {
                        playcd = pc;
                    }
                } else if (mgb) {
                    playcd = pc;
                }
            } else {
                if ((cards[possi[pc]] & 7) != BUBE) {
                    if ((((cards[possi[pc]] & 7) > (cards[possi[playcd]] & 7))
                            && ((cards[possi[pc]] & 7) != ACHT
                                    || (cards[possi[playcd]] & 7) != DAME
                                    || vmh == 0 || gespcd[(cards[possi[pc]] & ~7)
                                    | NEUN] == 2) && mgb)
                            || !mgp) {
                        playcd = pc;
                    }
                } else if (!mgp) {
                    playcd = pc;
                }
            }
        }
    }

    void nimm_bube() {
        int pc;

        if (stich >= 7
                || cardValues[stcd[0] & 7] + cardValues[stcd[1] & 7] > 4
                || (gespcd[BUBE] != 2 && gespcd[1 << 3 | BUBE] != 2
                        && gespcd[2 << 3 | BUBE] != 2 && gespcd[3 << 3 | BUBE] != 2))
            return;
        for (pc = 0; pc < possc; pc++) {
            if (cards[possi[pc]] == (3 << 3 | BUBE)
                    || cards[possi[pc]] == (2 << 3 | BUBE)) {
                playcd = pc;
                return;
            }
        }
    }

    void moegldrunter(int sc) {
        int fb, pc, w1, w2, wc;
        int[] pcl = new int[1], le = new int[1];
        boolean f, fr;

        fb = cards[possi[0]] >> 3;
        for (pc = 1; pc < possc; pc++) {
            if (cards[possi[pc]] >> 3 != fb)
                break;
        }
        fr = pc != possc;
        f = false;
        wc = 30;
        for (pc = 0; pc < possc; pc++) {
            if (higher(sc, cards[possi[pc]])) {
                if (f) {
                    if (fr) {
                        if ((cards[possi[pc]] & 7) == BUBE) {
                            w1 = 30 + (cards[possi[pc]] >> 3);
                        } else {
                            w1 = 7 - (cards[possi[pc]] & 7);
                            if (sicher(cards[possi[pc]] >> 3, pcl, le)) {
                                w1 += 10;
                            }
                        }
                        if ((cards[possi[playcd]] & 7) == BUBE) {
                            w2 = 30 + (cards[possi[playcd]] >> 3);
                        } else {
                            w2 = 7 - (cards[possi[playcd]] & 7);
                            if (sicher(cards[possi[playcd]] >> 3, pcl, le)) {
                                w2 += 10;
                            }
                        }
                    } else {
                        w1 = 7 - (cards[possi[pc]] & 7);
                        w2 = 7 - (cards[possi[playcd]] & 7);
                    }
                    if (w1 > w2) {
                        playcd = pc;
                        wc = w1;
                    }
                } else {
                    playcd = pc;
                    f = true;
                }
            }
        }
        if (!f) {
            moeglklein();
        } else if (fr && vmh == 2 && wc < 10)
            nimm_bube();
    }

    boolean ggdurchmarsch() {
        int i, j, h, bb, sn;

        if ((rstich[0] ? 1 : 0) + (rstich[1] ? 1 : 0) + (rstich[2] ? 1 : 0) > 1
                || (stcd[0] & 7) == SIEBEN
                || (vmh == 2 && stich != 1 && !higher(stcd[0], stcd[1])))
            return false;
        sn = (ausspl + vmh) % 3;
        for (i = 3; i >= 0; i--) {
            if (gespcd[bb = i << 3 | BUBE] != 2) {
                for (i = 0; i < 10; i++) {
                    if (cards[sn * 10 + i] == bb)
                        return false;
                }
                break;
            }
        }
        if (((stcd[0] & 7) == BUBE && ((hatnfb[left(ausspl)][4] == 1 && hatnfb[right(ausspl)][4] == 1) || (gespcd[0
                | BUBE] == 2
                && gespcd[1 << 3 | BUBE] == 2 && gespcd[2 << 3 | BUBE] == 2 && gespcd[3 << 3
                | BUBE] == 2)))
                || (stcd[0] & 7) < KOENIG) {
            ggdurchm[sn] = true;
        }
        if (!ggdurchm[sn])
            return false;
        j = h = 0;
        if (vmh == 2 && !higher(stcd[0], stcd[1])) {
            h = 1;
        }
        for (i = 0; i < possc; i++) {
            if (!higher(stcd[h], cards[possi[i]])) {
                if (j == 0
                        || cardValues[cards[possi[i]] & 7] < cardValues[cards[possi[j - 1]] & 7]) {
                    j = i + 1;
                }
            }
        }
        if (j == 0) {
            for (i = 0; i < possc; i++) {
                if (j == 0
                        || ggdmw[cards[possi[i]] & 7] < ggdmw[cards[possi[j - 1]] & 7]) {
                    j = i + 1;
                }
            }
        }
        playcd = j - 1;
        return true;
    }

    void m_bvr() {
        int fb, lef;
        boolean f;
        int[] pc = new int[1], le = new int[1];

        if (zweibuben())
            return;
        if (bubeanspielen())
            return;
        f = false;
        lef = 0;
        for (fb = 0; fb < 4; fb++) {
            if (sicher(fb, pc, le)) {
                if (f) {
                    if (le[0] > lef
                            || (rswert[cards[possi[pc[0]]] & 7] > rswert[cards[possi[playcd]] & 7] && le[0] >= lef)) {
                        playcd = pc[0];
                        lef = le[0];
                    }
                } else {
                    playcd = pc[0];
                    lef = le[0];
                    f = true;
                }
            }
        }
        if (!f || (cards[possi[playcd]] & 7) <= ZEHN) {
            playcd = 0;
            moeglklein();
        }
    }

    void m_bmr() {
        if (ggdurchmarsch())
            return;
        if (zweibuben())
            return;
        moegldrunter(stcd[0]);
    }

    void m_bhr() {
        if (ggdurchmarsch())
            return;
        if (zweibuben())
            return;
        moegldrunter(higher(stcd[0], stcd[1]) ? stcd[0] : stcd[1]);
    }

    void m_bramsch() {
        playcd = 0;
        if (vmh == 0)
            m_bvr();
        else if (vmh == 1)
            m_bmr();
        else
            m_bhr();
    }

    int unsich_fb(int sn, int[] s) {
        int fb, n;
        int[] pc = new int[1], le = new int[1];

        for (possc = 0; possc < 10; possc++) {
            possi[possc] = sn * 10 + possc;
        }
        n = 0;
        for (fb = 0; fb < 4; fb++) {
            s[fb] = 1;
            if (sicher(fb, pc, le)) {
                s[fb] = 0;
                n++;
            }
        }
        return n;
    }

    boolean comp_sramsch(int sn) {
        int fb, n, i, j, c, ea, bb;
        int[] p = new int[4], t = new int[4], s = new int[4], o = new int[4], b = new int[4], dum = new int[4];

        n = unsich_fb(sn, s);
        bb = b[0] = b[1] = b[2] = b[3] = 0;
        for (i = 0; i < 10; i++) {
            c = cards[sn * 10 + i];
            if ((c & 7) == BUBE) {
                bb++;
                b[c >> 3] = 1;
            }
        }
        if (ramschspiele != 0 && klopfen && playsramsch == 0) {
            if ((n <= 3 && (n == 0 || bb <= 1))
                    || (n <= 2 && (n == 0 || bb <= 2))) {
                return di_verdoppelt(false, true);
            }
        }
        if (playsramsch != 0) {
            if (sn == left(ausspl)) {
                if ((n <= 3 && bb == 0) || (n == 1 && bb <= 1 && b[3] == 0)
                        || n == 0) {
                    return di_verdoppelt(false, false);
                }
            } else if ((n == 3 && bb == 0) || (n == 2 && bb <= 1 && b[3] == 0)
                    || (n == 1 && bb <= 2) || n == 0) {
                return di_verdoppelt(false, false);
            }
        }
        if (playsramsch == 0)
            return false;
        for (fb = 0; fb < 4; fb++) {
            for (c = 0; c < 8; c++)
                inhand[fb][c] = false;
            p[fb] = t[fb] = 0;
            o[fb] = fb;
        }
        if (((vmh != 0 && prot2.verdopp[right(ausspl + vmh)] != 1) || (vmh == 2
                && prot2.verdopp[right(ausspl + vmh)] == 1 && prot2.verdopp[left(ausspl
                + vmh)] != 1))
                && (((cards[30] & 7) > ZEHN && (cards[31] & 7) > ZEHN)
                        || (cards[30] & 7) == SIEBEN || (cards[31] & 7) == SIEBEN)
                && ((cards[30] & 7) >= NEUN || (cards[31] & 7) >= NEUN)) {
            ggdurchm[sn] = true;
        }
        for (i = 0; i < 2; i++) {
            for (j = 0; j < 10; j++) {
                if (cardValues[cards[10 * sn + j] & 7] > cardValues[cards[30 + i] & 7]) {
                    swap(cards, 30 + i, 10 * sn + j);
                }
            }
            if ((cards[30 + i] & 7) == BUBE) {
                for (j = 0; j < 10; j++) {
                    if ((cards[10 * sn + j] & 7) != BUBE) {
                        swap(cards, 30 + i, 10 * sn + j);
                        break;
                    }
                }
            }
        }
        for (i = 0; i < 10; i++)
            spcards[i] = cards[sn * 10 + i];
        spcards[10] = cards[30];
        spcards[11] = cards[31];
        bb = b[0] = b[1] = b[2] = b[3] = 0;
        for (i = 0; i < 12; i++) {
            c = spcards[i];
            if ((c & 7) != BUBE) {
                p[c >> 3] += cardValues[c & 7];
                t[c >> 3]++;
                inhand[c >> 3][c & 7] = true;
            } else {
                bb++;
                b[c >> 3] = 1;
            }
        }
        for (fb = 0; fb < 4; fb++) {
            for (i = fb + 1; i < 4; i++) {
                if (p[o[fb]] < p[o[i]]) {
                    j = o[i];
                    o[i] = o[fb];
                    o[fb] = j;
                }
            }
        }
        gedr = 0;
        ea = 0;
        for (i = 0; i < 4; i++) {
            if (t[i] == 1 && inhand[i][AS])
                ea++;
        }
        if (ea < 2) {
            for (i = 0; i < 4; i++) {
                if (t[i] == 2 && inhand[i][AS] && inhand[i][ZEHN]) {
                    drueck(i, 2, dum);
                    break;
                }
            }
        }
        for (n = 1; n < 8 && gedr < 2; n++) {
            for (j = 0; j < 4 && gedr < 2; j++) {
                i = o[j];
                if (t[i] == n && s[i] == 0) {
                    if (n == 1) {
                        if (!inhand[i][ACHT]) {
                            drueck(i, 1, dum);
                        }
                    } else if (n == 2) {
                        if (inhand[i][SIEBEN] || inhand[i][ACHT])
                            drueck(i, 1, dum);
                        else
                            drueck(i, 2, dum);
                    } else if (n == 3) {
                        switch ((inhand[i][SIEBEN] ? 1 : 0)
                                + (inhand[i][ACHT] ? 1 : 0)
                                + (inhand[i][NEUN] ? 1 : 0)) {
                        case 3:
                            break;
                        case 2:
                            drueck(i, 1, dum);
                            break;
                        default:
                            drueck(i, 2, dum);
                            break;
                        }
                    } else {
                        drueck(i, 2, dum);
                    }
                }
            }
        }
        if (ramschspiele != 0 && klopfen && !ggdurchm[sn]) {
            n = unsich_fb(sn, s);
            if ((n <= 3 && bb == 0)
                    || (n <= 2 && (bb <= 1 || (bb == 2 && b[3] == 0)))
                    || (n <= 1 && (bb <= 2 || (bb == 3 && b[0] != 0)))
                    || n == 0) {
                return di_verdoppelt(false, true);
            }
        }
        return false;
    }

    void ramsch_stich() {
        rstsum[ausspl] += cardValues[stcd[0] & 7] + cardValues[stcd[1] & 7]
                + cardValues[stcd[2] & 7];
        rstich[ausspl] = true;
        if (stich == 10) {
            rskatsum = cardValues[prot2.skat[1][0] & 7]
                    + cardValues[prot2.skat[1][1] & 7];
            if (rskatloser == 0) {
                rstsum[ausspl] += rskatsum;
            }
        }
        if ((stcd[0] & 7) == BUBE && (stcd[1] & 7) != BUBE
                && (stcd[2] & 7) != BUBE) {
            ggdurchm[0] = ggdurchm[1] = ggdurchm[2] = true;
        }
    }

    void ramsch_result() {
        int maxn, i;

        stsum = rstsum[0];
        spieler = 0;
        maxn = 1;
        if (rstsum[1] > stsum) {
            stsum = rstsum[1];
            spieler = 1;
        } else if (rstsum[1] == stsum) {
            spieler = 2;
            maxn++;
        }
        if (rstsum[2] > stsum) {
            stsum = rstsum[2];
            spieler = 2;
            maxn = 1;
        } else if (rstsum[2] == stsum) {
            spieler = 1 - spieler;
            maxn++;
        }
        spgew = false;
        if (maxn == 3) {
            spieler = spwert = stsum = 0;
        } else {
            spwert = stsum;
            if (maxn == 2) {
                stsum = 120 - 2 * stsum;
                spgew = true;
                if (rskatloser != 0) {
                    spwert += rskatsum;
                }
            } else if (rskatloser != 0) {
                stsum += rskatsum;
                spwert += rskatsum;
            }
        }
        nspwert = 0;
        switch ((rstich[0] ? 1 : 0) + (rstich[1] ? 1 : 0) + (rstich[2] ? 1 : 0)) {
        case 1:
            nspwert = spwert = stsum = 120;
            spgew = true;
            mes2 = true;
            break;
        case 2:
            mes1 = true;
            spwert *= 2;
            break;
        }
        for (i = 0; i < sramschstufe; i++)
            spwert *= 2;
        if (bockspiele != 0 && ramschspiele == 0)
            spwert *= 2;
    }

    boolean testgrandhand(int sn) {
        int i, bb, as, zehn;
        boolean[] b = new boolean[4];

        bb = as = zehn = 0;
        for (i = 0; i < 10; i++) {
            switch (cards[10 * sn + i] & 7) {
            case BUBE:
                bb++;
                break;
            case AS:
                as++;
                break;
            case ZEHN:
                zehn++;
                break;
            }
        }
        calc_inhand(sn);
        for (i = 0; i < 4; i++)
            b[i] = inhand[i][BUBE];
        return ((bb >= 3 && as >= 2 && as + zehn >= 3) || (bb == 4 && as >= 2) || testgrand(
                bb, b, sn == hoerer) != 0);
    }

    // --------------------------------------------------------------------------------------
    // File xdial.c
    // --------------------------------------------------------------------------------------

    void info_reiz() {
    }

    void info_spiel() {
    }

    void info_stich(int p, int c) {
    }

    void clear_info() {
    }

    void di_info(int sn, int th) {
    }

    void di_hand() {
        initHandStr();
        setGone(R.id.dialogDesk);
        setVisible(R.id.dialogHand);
    }

    void next_grandhand(int sn) {
        sn = left(sn);
        if (sn == hoerer) {
            ktrply = -1;
            init_ramsch();
        } else {
            di_grandhand(sn);
        }
    }

    void di_grandhand(int sn) {
        if (iscomp(sn)) {
            if (testgrandhand(sn))
                do_grandhand(sn);
            else
                next_grandhand(sn);
        } else {
            ktrply = sn;
            // TODO create_di(sn,digrandhand);
        }
    }

    void di_ansage() {
        int sn, i;
        boolean ktr;

        ktrply = -1;
        for (sn = 0; sn < numsp; sn++) { // TODO Kontra
            if (trumpf == -1) {
                if (!revolang) {
                    if (ouveang) {
                    }
                    if (handsp) {
                    }
                }
            } else {
                if (ouveang) {
                } else if (schwang) {
                } else if (schnang) {
                } else if (handsp) {
                }
            }
        }
        ktr = false;
        ktrnext = -1;
        if (playkontra != 0) {
            for (i = 0; i < 3; i++) {
                if (i != spieler && (playkontra == 1 || sagte18[i])) {
                    if (!ktr) {
                        ktr = true;
                        ktrsag = i;
                        ktrnext = i;
                    } else if ((sagte18[i] && (i == sager || i == hoerer))
                            || !sagte18[ktrsag]) {
                        ktrsag = i;
                    } else {
                        ktrnext = i;
                    }
                }
            }
        }
        View vp = findViewById(R.id.playVMHLeft);
        playVMHLeftBgr = spieler == 1 ? trumpf + 3 : 0;
        vp.setBackgroundResource(gameSymb(playVMHLeftBgr));
        vp = findViewById(R.id.playVMHRight);
        playVMHRightBgr = spieler == 2 ? trumpf + 3 : 0;
        vp.setBackgroundResource(gameSymb(playVMHRightBgr));
        if (numsp == 1 && iscomp(spieler) && !ktr) {
            initAnsageStr();
            setGone(R.id.dialogDesk);
            setVisible(R.id.dialogAnsage);
        } else if (ktr) {
            di_kontra(ktrsag);
        } else
            do_angesagt();
    }

    void di_kontra(int sn) {
        ktrply = sn;
        ktrsag = sn;
        sort2[sn] = sort2[spieler] = (trumpf == -1 ? 1 : 0);
        if (!iscomp(spieler))
            initscr(spieler, 1);
        if (iscomp(sn)) {
            di_ktrnext(sn, sage_kontra(sn));
        } else {
            initKontraStr();
            initscr(sn, 1);
            setGone(R.id.dialogDesk);
            setVisible(R.id.dialogKontra);
        }
    }

    void di_rekontra(int sn) {
        ktrply = -1;
        kontram = sn;
        initReKontraStr();
        if (iscomp(spieler)) {
            di_ktrnext(sn, sage_re(spieler));
        } else {
            setGone(R.id.dialogDesk);
            setGone(R.id.dialogSpielen);
            setVisible(R.id.dialogReKontra);
        }
    }

    void di_konre(int sn) {
        ktrply = sn;
        initKontraReStr();
        setGone(R.id.dialogDesk);
        setVisible(R.id.dialogKontraRe);
    }

    void di_ktrnext(int sn, boolean f) {
        if (kontrastufe == 1) {
            if (f) {
                kontrastufe = 2;
                prot2.verdopp[spieler] = 2;
                ktrnext = left(ktrsag) == spieler ? right(ktrsag)
                        : left(ktrsag);
                if (iscomp(ktrnext))
                    ktrnext = -1;
                if (iscomp(ktrsag)) {
                    ktrsag = ktrnext;
                    ktrnext = -1;
                }
            } else {
                ktrnext = -1;
                ktrsag = left(ktrsag) == spieler ? right(ktrsag) : left(ktrsag);
                if (iscomp(ktrsag))
                    ktrsag = ktrnext;
            }
            if (ktrsag >= 0) {
                di_konre(ktrsag);
            } else {
                do_angesagt();
            }
        } else if (f) {
            kontrastufe = 1;
            prot2.verdopp[sn] = 2;
            di_rekontra(sn);
        } else if (ktrnext >= 0 && ktrnext != sn) {
            di_kontra(ktrnext);
            ktrnext = -1;
        } else {
            if (numsp == 1 && iscomp(spieler) && playkontra == 2 && !sagte18[0]) {
                initAnsageStr();
                setGone(R.id.dialogDesk);
                setVisible(R.id.dialogAnsage);
            } else
                do_angesagt();
        }
    }

    void di_dicht() {
    }

    void di_weiter(int ini) {
    }

    void di_wiederweiter(int sn) {
        setVisible(R.id.boxWeiter);
    }

    void di_klopfen(int sn) {
        setGone(R.id.dialogDesk);
        setVisible(R.id.dialogKlopfen);
    }

    void di_schieben() {
        int sn;

        do {
            if (vmh != 0)
                save_skat(vmh + 1);
            sn = (ausspl + vmh) % 3;
            spieler = sn;
            if (iscomp(sn)) {
                if (comp_sramsch(sn)) {
                    return;
                }
                vmh = left(vmh);
            } else {
                setGone(R.id.dialogDesk);
                if (playsramsch != 0)
                    setVisible(R.id.dialogSchieben);
                else
                    setVisible(R.id.dialogKlopfen);
                return;
            }
        } while (vmh != 0);
        start_ramsch();
    }

    void di_buben() {
        initBubenStr();
        setGone(R.id.dialogDesk);
        setVisible(R.id.dialogBuben);
    }

    void initSpiel() {
        initSpielStr();
        if (spitzezaehlt != 0 && kannspitze != 0) {
            setVisible(R.id.buttonSpitze);
        } else {
            setInvisible(R.id.buttonSpitze);
        }
        if (revolution) {
            setVisible(R.id.buttonRevolution);
        } else {
            setInvisible(R.id.buttonRevolution);
        }
        setDeselected(R.id.buttonKaro);
        setDeselected(R.id.buttonHerz);
        setDeselected(R.id.buttonPik);
        setDeselected(R.id.buttonKreuz);
        setDeselected(R.id.buttonNull);
        setDeselected(R.id.buttonGrand);
        setDeselected(R.id.buttonSchneider);
        setDeselected(R.id.buttonSchwarz);
        setDeselected(R.id.buttonOuvert);
        setSelected(R.id.buttonKaro + trumpf);
        initscr(0, 1);
        setGone(R.id.dialogDesk);
        setVisible(R.id.dialogSpielen);
    }

    boolean di_verdoppelt(boolean f, boolean kl) {
        int sn;

        if (!f) {
            klopfm = kl;
            spielerm = spieler;
            initVerdoppeltStr();
            verd1 = verd2 = -1;
            for (sn = 0; sn < numsp; sn++) {
                if (sn != spieler) {
                    if (verd1 == -1)
                        verd1 = sn;
                    else
                        verd2 = sn;
                }
            }
            sramschstufe++;
            prot2.verdopp[spieler] = kl ? 1 : 0;
        }
        if (verd1 != -1) {
            spieler = verd1;
            setGone(R.id.dialogDesk);
            setVisible(R.id.dialogVerdoppelt);
            verd1 = verd2;
            verd2 = -1;
            return true;
        }
        if (!iscomp(spieler)) {
            vmh = left(vmh);
            if (vmh != 0)
                di_schieben();
            else
                start_ramsch();
        }
        return false;
    }

    void di_spiel() {
        int i, j;
        int[] a = new int[4];

        ktrply = -1;
        a[0] = a[1] = a[2] = a[3] = 0;
        for (i = 0; i < 10; i++) {
            if ((cards[10 * spieler + i] & 7) != BUBE)
                a[cards[10 * spieler + i] >> 3]++;
        }
        j = 3;
        for (i = 2; i >= 0; i--) {
            if (a[i] > a[j])
                j = i;
        }
        trumpf = j;
        initSpiel();
    }

    void list_fun(int sn) {
        int i, j, k, s, e, cp;
        boolean r, d;
        int[] ret = new int[4];
        int[][] curr = new int[3][3], cgv = new int[3][2];
        int[] last = new int[4];

        for (i = 0; i < 3; i++) {
            for (j = 0; j < 3; j++) {
                curr[i][j] = splsum[i][j];
            }
            for (j = 0; j < 2; j++) {
                cgv[i][j] = sgewoverl[i][j];
            }
        }
        cp = alist[sn];
        for (j = 0; j < splfirst[sn]; j++) {
            modsum(curr, cgv, j);
        }
        TextView v;
        String txt;
        for (k = 0; k < 3; k++) {
            v = (TextView) findViewById(liMat[k][0]);
            txt = "" + curr[k][cp];
            v.setText(txt);
            v.setTypeface(null, Typeface.NORMAL);
        }
        for (i = splfirst[sn], j = 1; j < 10 && i < splstp; i++, j++) {
            boolean[] invi = new boolean[4];
            modsum(curr, cgv, i, ret);
            s = ret[0];
            e = ret[1];
            r = ret[2] != 0;
            d = ret[3] != 0;
            for (k = 0; k < 4; k++) {
                v = (TextView) findViewById(liMat[k][j]);
                txt = "" + (k == 3 ? e > 0 && r && !d ? -e : e : curr[k][cp]);
                v.setText(txt);
                setVisible(v);
                v.setTypeface(null, Typeface.NORMAL);
            }
            if ((cp == 1 || (r && !d)) && e > 0) {
                v = (TextView) findViewById(liMat[s][j]);
                setInvisible(v);
                invi[s] = true;
            } else if (cp != 2 || r || e >= 0) {
                if (e == 0)
                    s = 4;
                for (k = 0; k < 3; k++) {
                    if (k != s) {
                        v = (TextView) findViewById(liMat[k][j]);
                        setInvisible(v);
                        invi[k] = true;
                    }
                }
            }
            for (k = 0; k < 4; k++) {
                if (!invi[k])
                    last[k] = j;
            }
        }
        for (; j < 10; j++) {
            for (k = 0; k < 4; k++) {
                v = (TextView) findViewById(liMat[k][j]);
                setInvisible(v);
            }
        }
        for (k = 0; k < 3; k++) {
            v = (TextView) findViewById(liMat[k][10]);
            txt = cgv[k][0] + "/" + cgv[k][1];
            v.setText(txt);
            v = (TextView) findViewById(liMat[k][last[k]]);
            v.setTypeface(null, Typeface.BOLD);
        }
    }

    void di_delliste() {
    }

    void di_liste(int sn, boolean ini) {
        if (ini)
            splfirst[sn] = 0;
        list_fun(sn);
    }

    void prot_fun(int sn) {
        int tr, e, i, j, s;
        int[][] stiche = new int[10][3];

        tr = trumpf;
        trumpf = prot1.trumpf;
        for (s = 0; s < 3; s++) {
            for (i = 0; i < 10; i++)
                stiche[i][s] = prot1.stiche[i][s];
            for (i = (protsort[sn] ? 0 : prot1.stichgem); i < 9; i++) {
                for (j = i + 1; j < 10; j++) {
                    if (lower(stiche[i][s], stiche[j][s], trumpf == -1 ? 1 : 0)) {
                        swap(stiche, i, j, s);
                    }
                }
            }
        }
        trumpf = tr;
        boolean b0 = prot1.spieler == 0;
        boolean b1 = prot1.spieler == 1;
        boolean b2 = prot1.spieler == 2;
        if (prot1.gewonn
                && (alist[0] == 1 || (prot1.trumpf == 5 && prot1.augen != 120))) {
            b0 = !b0;
            b1 = !b1;
            b2 = !b2;
        }
        if (prot1.trumpf >= 4 && prot1.spwert == 0) {
            b0 = b1 = b2 = false;
        }
        // the header of the alone player get bold
        TextView v = (TextView) findViewById(R.id.sp0head);
        v.setTypeface(null, b0 ? Typeface.BOLD : Typeface.NORMAL);
        v = (TextView) findViewById(R.id.sp1head);
        v.setTypeface(null, b1 ? Typeface.BOLD : Typeface.NORMAL);
        v = (TextView) findViewById(R.id.sp2head);
        v.setTypeface(null, b2 ? Typeface.BOLD : Typeface.NORMAL);
        v = (TextView) findViewById(R.id.sumhead);
        v.setTypeface(null, Typeface.NORMAL);

        int[] points = new int[]{0, 0, 0};
        int skat = cardValues[prot1.skat[1][0] & 7] + cardValues[prot1.skat[1][1] & 7];
        if (prot1.trumpf != 5) {
            // if it is not Ramsch add the points for the skat to the single player
            points[prot1.spieler] = skat;
        }
        for (i = 0; i < 10; i++) {
            for (s = 0; s < 3; s++) {
                if (protsort[sn]) {
                    e = prot1.trumpf != -1
                            && (stiche[i][s] >> 3 == prot1.trumpf || (stiche[i][s] & 7) == BUBE) ? 1
                            : 0;
                } else {
                    if (i != 0 && prot1.stichgem <= i) {
                        e = prot1.gewonn && prot1.stichgem != 0 ? 0 : 4;
                    } else {
                        e = prot1.anspiel[i] == s ? 2 : 0;
                        if (prot1.gemacht[i] == s)
                            e |= 1;
                    }
                }
                TextView tv = (TextView) findViewById(spMat[s][i]);
                tv.setTypeface(null, (e & 1) != 0 ? Typeface.BOLD
                        : Typeface.NORMAL);
                if ((e & 4) != 0) {
                    setInvisible(tv);
                } else {
                    setVisible(tv);
                }
                String txt;
                tv.setTextColor(Color.BLACK);
                if (prot1.stichgem != 0 || protsort[sn]) {
                    if (prot1.spitze
                            && stiche[i][s] == (prot1.trumpf == 4 ? BUBE
                                    : SIEBEN | prot1.trumpf << 3)) {
                        txt = getTranslation(Translations.XT_Spitze);
                    } else {
                        txt = gameName(stiche[i][s] >> 3);
                        txt += (e & 2) != 0 ? "_" : " ";
                        txt += cardVal(stiche[i][s] & 7);
                        tv.setTextColor(Color
                                .parseColor(suitCol(stiche[i][s] >> 3)));
                    }
                } else {
                    txt = e == 2 ? "_" : "";
                    txt += prot1.schenken != 0 ? prot1.spieler == s ? getTranslation(Translations.XT_Annehmen)
                            : getTranslation(Translations.XT_Schenken)
                            : getTranslation(Translations.XT_Passe);
                    txt += e == 2 ? "_" : "";
                }
                tv.setText(txt);
            }
            TextView tv = (TextView) findViewById(spMat[3][i]);
            if (tv != null) {
                int trick = cardValues[stiche[i][0] & 7] + cardValues[stiche[i][1] & 7] + cardValues[stiche[i][2] & 7];
                points[prot1.gemacht[i]] += trick;
                if (protsort[sn] || prot1.trumpf < 0) {
                    tv.setText("");
                } else if (prot1.trumpf > 4) {
                    if (i == 9) {
                        // decide who will get the skat
                        if (rskatloser != 0) { // the loser of the round
                            points[Util.getIndexOfLargestValue(points)] += skat;
                        } else { // the one how received the last trick
                            points[prot1.gemacht[9]] += skat;
                        }
                    }
                    String txt = points[0] + " / " + points[1] + " / " + points[2];
                    tv.setTypeface(null, Typeface.NORMAL);
                    tv.setText(txt);
                } else {
                    int gegen;
                    if (prot1.spieler == 0) {
                        gegen = points[1] + points[2];
                    } else if (prot1.spieler == 1) {
                        gegen = points[0] + points[2];
                    } else if (prot1.spieler == 2) {
                        gegen = points[0] + points[1];
                    } else {
                        throw new IllegalStateException("Cannot compute points as there is an illegal player number.");
                    }
                    tv.setTypeface(null, points[prot1.spieler] > 60 || gegen >= 60 ? Typeface.BOLD : Typeface.NORMAL);
                    String txt = points[prot1.spieler] + " / " + gegen;
                    tv.setText(txt);
                }
            }
        }
        im_skat(protsort[sn] ? 0 : 1);
    }

    void im_skat(int i) {
        String s = getTranslation(Translations.XT_Im_Skat) + " " + (i == 0 ? getTranslation(Translations.XT_war) : getTranslation(Translations.XT_ist))
                + ": ";
        TextView tv = (TextView) findViewById(R.id.textImSkatIst1);
        tv.setText(s);
        s = gameName(prot1.skat[i][0] >> 3);
        s += " ";
        s += cardVal(prot1.skat[i][0] & 7);
        tv = (TextView) findViewById(R.id.textImSkatIst2);
        tv.setText(s);
        tv.setTextColor(Color.parseColor(suitCol(prot1.skat[i][0] >> 3)));
        s = gameName(prot1.skat[i][1] >> 3);
        s += " ";
        s += cardVal(prot1.skat[i][1] & 7);
        tv = (TextView) findViewById(R.id.textImSkatIst4);
        tv.setText(s);
        tv.setTextColor(Color.parseColor(suitCol(prot1.skat[i][1] >> 3)));
    }

    void di_proto(int sn, boolean ini, boolean log) {
        if (ini)
            protsort[sn] = false;
        TextView tv = (TextView) findViewById(R.id.buttonProtoPfeil);
        tv.setText(protsort[sn] ? "<<<" : ">>>");
        prot_fun(sn);
    }

    /**
     * Shows the result of the recently finished game.
     * @param be
     */
    void di_result(int be) {
        initResultStr();
        View vp = findViewById(R.id.playVMHLeft);
        playVMHLeftBgr = 0;
        vp.setBackgroundResource(gameSymb(playVMHLeftBgr));
        vp = findViewById(R.id.playVMHRight);
        playVMHRightBgr = 0;
        vp.setBackgroundResource(gameSymb(playVMHRightBgr));
        boolean b0 = spieler == 0;
        boolean b1 = spieler == 1;
        boolean b2 = spieler == 2;
        if (spgew && (alist[0] == 1 || (GameType.isRamsch(trumpf) && stsum != 120))) {
            b0 = !b0;
            b1 = !b1;
            b2 = !b2;
        }
        if (GameType.isRamsch(trumpf) && spwert == 0) {
            b0 = b1 = b2 = false;
        }
        TextView v = (TextView) findViewById(R.id.textSpielstandPlayer);
        String s = "" + sum[0][alist[0]];
        v.setText(s);
        v.setTypeface(null, b0 ? Typeface.BOLD : Typeface.NORMAL);
        v = (TextView) findViewById(R.id.textSpielstandComputerLeft);
        s = "" + sum[1][alist[0]];
        v.setText(s);
        v.setTypeface(null, b1 ? Typeface.BOLD : Typeface.NORMAL);
        v = (TextView) findViewById(R.id.textSpielstandComputerRight);
        s = "" + sum[2][alist[0]];
        v.setText(s);
        v.setTypeface(null, b2 ? Typeface.BOLD : Typeface.NORMAL);
        setGone(R.id.dialogDesk);
        setVisible(R.id.dialogResult);
    }

    void showDialogFromMenu(int id) {
        setGone(R.id.mainScreen);
        setGone(R.id.dialogProto);
        setGone(R.id.dialogListe);
        setGone(R.id.dialogLoeschen);
        setDialogsGone();
        setVisible(id);
        setVisible(R.id.dialogScreen);
    }

    // --------------------------------------------------------------------------------------
    // File xio.c
    // --------------------------------------------------------------------------------------

    void b_text(int s, String str) {
        int sn;
        Button v;

        for (sn = 0; sn < numsp; sn++) {
            if (sn != s) {
                if (s == left(sn)) {
                    v = (Button) findViewById(R.id.buttonComputerLeft);
                } else {
                    v = (Button) findViewById(R.id.buttonComputerRight);
                }
                v.setText(str);
            }
        }
    }

    void do_msaho(int sn, String str) {
        if (sn >= numsp)
            return;
        Button v = (Button) findViewById(R.id.button18);
        v.setText(str);
        v = (Button) findViewById(R.id.buttonPasse);
        v.setText(getTranslation(Translations.XT_Passe));
    }

    void draw_skat(int sn) {
        putcard(sn, cards[30], D_skatx, D_skaty);
        putcard(sn, cards[31], D_skatx + D_cardw, D_skaty);
        skatopen = true;
    }

    void home_skat() {
        int sn = spieler;

        homecard(sn, 0, 0);
        homecard(sn, 0, 1);
        umdrueck = 0;
        skatopen = false;
        backopen[0] = backopen[1] = backopen[2] = true;
        spitzeopen = true;
    }

    void nimm_stich() {
        int sn = ausspl, i;

        for (i = 0; i < 3; i++) {
            homecard(sn, 1, i);
        }
        stichopen = 0;
        moveCardOverlay(-1, R.id.cardStichMiddle, ausspl == 0 ? R.id.card9
                : ausspl == 1 ? R.id.cardLeft : R.id.cardRight, 0, 0, 1, 1);
    }

    void drop_card(int i, int s) {
        int x1, y1, x2, y2;
        int from, to, c;

        if (stich == 10)
            backopen[s] = false;
        if (s == left(0)) {
            x1 = D_com1x;
            y1 = D_com1y;
            from = R.id.cardLeft;
        } else {
            x1 = D_com2x;
            y1 = D_com2y;
            from = R.id.cardRight;
        }
        if (s == 0 || (ouveang && s == spieler)) {
            x1 = D_playx + (i % 10) * D_cardx;
            from = R.id.card0 + (i % 10);
            if (s == 0)
                y1 = D_playy;
            putdesk(0, x1, y1);
        } else if (stich == 10) {
            putdesk(0, x1, y1);
            if (s == spieler)
                spitzeopen = false;
        } else if (spitzeang
                && cards[i] == (trumpf == 4 ? BUBE : SIEBEN | trumpf << 3)) {
            putback(0, x1, y1);
            spitzeopen = false;
            sptzmrk = true;
            putamark(0, spieler);
        }
        if (vmh == 0)
            l2r = trickl2r;
        to = (l2r ? vmh : s == left(0) ? 0 : s == 0 ? 1 : 2);
        x2 = D_stichx + to * D_cardw;
        y2 = D_stichy;
        to += R.id.cardStichLeft;
        stcd[vmh] = cards[i];
        stichopen = vmh + 1;
        gespcd[cards[i]] = 2;
        if ((cards[i] & 7) != BUBE)
            gespfb[cards[i] >> 3]++;
        c = cards[i];
        cards[i] = -1;
        moveCardOverlay(c, from, to, x2, y2, 0, 1);
    }

    void calc_desk(int sn) {
    }

    void waitt(int t, int f) {
    }

    void stdwait() {
        waitt(700, 2);
    }

    void backgr(int sn, int x, int y) {
        drawcard(sn, -2, x, y);
    }

    void putdesk(int sn, int x, int y) {
        backgr(sn, x, y);
    }

    void drawcard(int sn, int c, int x, int y) {
        View v = null;
        switch (y) {
        case 0:
            switch (x) {
            case 0:
                v = findViewById(R.id.cardLeft);
                cardLeftBgr = c;
                break;
            case 1:
                v = findViewById(R.id.cardRight);
                cardRightBgr = c;
                break;
            }
            break;
        case 1:
        case -1:
            switch (x) {
            case 0:
                v = findViewById(R.id.cardSkatLeft);
                cardSkatLeftBgr = c;
                break;
            case 1:
                v = findViewById(R.id.cardSkatRight);
                cardSkatRightBgr = c;
                break;
            case 2:
                v = findViewById(R.id.cardStichLeft);
                if (y == 1)
                    cardStichLeftBgr = c;
                break;
            case 3:
                v = findViewById(R.id.cardStichMiddle);
                if (y == 1)
                    cardStichMiddleBgr = c;
                break;
            case 4:
                v = findViewById(R.id.cardStichRight);
                if (y == 1)
                    cardStichRightBgr = c;
                break;
            }
            break;
        case 2:
            v = findViewById(R.id.card0 + x);
            cardBgr[x] = c;
            break;
        }
        if (v != null) {
            setBackgroundDrawable(v, cardName(c + 2));
            setVisible(v);
        }
    }

    void putcard(int sn, int i, int x, int y) {
        if (i < 0)
            putdesk(sn, x, y);
        else
            drawcard(sn, i, x, y);
    }

    void putback(int sn, int x, int y) {
        drawcard(sn, -1, x, y);
    }

    void restore_hints() {
        int sn = 0;
        if (phase == SPIELEN && sn == (ausspl + vmh) % 3) {
            show_hint(sn, 0, hints[sn]);
        } else if (phase == DRUECKEN && sn == spieler) {
            show_hint(sn, 0, hints[sn]);
            show_hint(sn, 1, hints[sn]);
        }
    }

    void show_hint(int sn, int c, boolean d) {
        if (sn == 0) {
            if (lasthint[c] != -1) {
                drawnCard[lasthint[c] + 2].getDrawable(2).setAlpha(0);
                for (int i = 0; i < 10; i++) {
                    if (cardBgr[i] == lasthint[c]) {
                        findViewById(R.id.card0 + i).invalidate();
                    }
                }
                if (cardSkatLeftBgr == lasthint[c]) {
                    findViewById(R.id.cardSkatLeft).invalidate();
                }
                if (cardSkatRightBgr == lasthint[c]) {
                    findViewById(R.id.cardSkatRight).invalidate();
                }
            }
            if (d && hintcard[c] != -1) {
                drawnCard[hintcard[c] + 2].getDrawable(2).setAlpha(255);
                lasthint[c] = hintcard[c];
                for (int i = 0; i < 10; i++) {
                    if (cardBgr[i] == lasthint[c]) {
                        findViewById(R.id.card0 + i).invalidate();
                    }
                }
                if (cardSkatLeftBgr == lasthint[c]) {
                    findViewById(R.id.cardSkatLeft).invalidate();
                }
                if (cardSkatRightBgr == lasthint[c]) {
                    findViewById(R.id.cardSkatRight).invalidate();
                }
            } else {
                lasthint[c] = -1;
            }
        }
    }

    void putamark(int sn, int s) {
    }

    void putmark(int s) {
    }

    void remmark(int f) {
    }

    void homecard(int s, int n, int m) {
        int x1, y1;

        x1 = (n != 0 ? D_stichx : D_skatx) + m * D_cardw;
        y1 = (n != 0 ? D_stichy : D_skaty);
        putdesk(0, x1, y1);
    }

    void givecard(int s, int n) {
        int sn;
        int[] sna = new int[3], x1 = new int[3], y1 = new int[3];
        sptzmrk = false;
        for (sn = 0; sn < numsp; sn++) {
            sna[sn] = sn;
            if (s < 0) {
                x1[sn] = D_skatx;
                y1[sn] = D_skaty;
            } else if (s != sn) {
                if (s == left(sn))
                    x1[sn] = D_com1x;
                else
                    x1[sn] = D_com2x;
                y1[sn] = D_com1y;
            } else {
                if (n == 0)
                    x1[sn] = D_playx;
                else if (n == 1)
                    x1[sn] = D_playx + 3 * D_cardx;
                else
                    x1[sn] = D_playx + 7 * D_cardx;
                y1[sn] = D_playy;
            }
        }
        for (sn = 0; sn < numsp; sn++) {
            putback(sn, x1[sn], y1[sn]);
            if (s == hoerer)
                putamark(sn, s);
            if (s == sn) {
                putback(sn, x1[sn] + D_cardx, y1[sn]);
                putback(sn, x1[sn] + 2 * D_cardx, y1[sn]);
                if (n == 1)
                    putback(sn, x1[sn] + 3 * D_cardx, y1[sn]);
            } else if (s < 0) {
                putback(sn, x1[sn] + D_cardw, y1[sn]);
            }
        }
    }

    void initscr(int sn, int sor) {
        int i, x, y;

        if (phase == WEITER || phase == REVOLUTION)
            return;
        if (sor != 0) {
            if (sor != 2)
                sort(sn);
            else {
                if (skatopen)
                    draw_skat(spieler);
                if (phase == SPIELEN || phase == NIMMSTICH) {
                    for (i = 0; i < stichopen; i++) {
                        putcard(sn, stcd[i], D_stichx + i * D_cardw, D_stichy);
                    }
                }
            }
            for (i = 0; i < 10; i++) {
                putcard(sn, cards[sn * 10 + i], D_playx + i * D_cardx, D_playy);
            }
            if (hintcard[0] != -1 && !iscomp(sn) && hints[sn]) {
                if (phase == SPIELEN && sn == (ausspl + vmh) % 3) {
                    show_hint(sn, 0, true);
                } else if (phase == DRUECKEN && sn == spieler) {
                    show_hint(sn, 0, true);
                    show_hint(sn, 1, true);
                }
            }
        }
        if (phase != ANSAGEN) {
            di_info(sn, -1);
        }
        if (phase != ANSAGEN && ouveang) {
            // TODO Computer spielt ouvert
        } else if (spitzeang && sn != spieler && spitzeopen) {
            x = spieler == left(sn) ? D_com1x : D_com2x;
            y = spieler == left(sn) ? D_com1y : D_com2y;
            putcard(sn, trumpf == 4 ? BUBE : SIEBEN | trumpf << 3, x, y);
        }
    }

    void revolutionscr() {
    }

    void clr_desk(boolean nsp) {
        int sn, i;

        for (sn = 0; sn < numsp; sn++) {
            if (!nsp || sn != spieler) {
                backgr(sn, D_com1x, D_com1y);
                backgr(sn, D_com2x, D_com2y);
                for (i = 0; i < 2; i++) {
                    backgr(sn, D_skatx + i * D_cardw, D_skaty);
                }
                for (i = 0; i < 3; i++) {
                    backgr(sn, D_stichx + i * D_cardw, D_stichy);
                }
                for (i = 0; i < 10; i++) {
                    backgr(sn, D_playx + i * D_cardw, D_playy);
                }
                di_info(sn, 3);
            }
        }
        if (!nsp && ouveang) {
            for (sn = 0; sn < numsp; sn++) {
                if (sn != spieler)
                    di_info(sn, -2);
            }
            ouveang = false;
            for (sn = 0; sn < numsp; sn++) {
                calc_desk(sn);
                if (sn != spieler)
                    di_info(sn, 3);
            }
            ouveang = true;
        }
    }

    void put_box(int s) {
        int sn;

        for (sn = 0; sn < numsp; sn++) {
            if (s != sn) {
                if (s == left(sn)) {
                    setVisible(R.id.buttonComputerLeft);
                } else {
                    setVisible(R.id.buttonComputerRight);
                }
            } else {
                setVisible(R.id.box18);
                setVisible(R.id.boxPasse);
            }
        }
    }

    void rem_box(int s) {
        int sn;

        for (sn = 0; sn < numsp; sn++) {
            if (s != sn) {
                int id;
                if (s == left(sn)) {
                    id = R.id.buttonComputerLeft;
                } else {
                    id = R.id.buttonComputerRight;
                }
                setGone(id);
                Button v = (Button) findViewById(id);
                v.setText("");
            } else {
                setGone(R.id.box18);
                setGone(R.id.boxPasse);
            }
        }
    }

    void inv_box(int s, boolean c, boolean rev) {
        int id;

        switch (s) {
        case 1:
            id = R.id.buttonComputerLeft;
            break;
        case 2:
            id = R.id.buttonComputerRight;
            break;
        default:
            id = c ? R.id.button18 : R.id.buttonPasse;
            break;
        }
        if (rev) {
            setSelected(id);
        } else {
            setDeselected(id);
        }
    }

    void put_fbox(int sn, boolean druecken) {
        if (sn >= numsp)
            return;
        setGone(R.id.box18);
        setGone(R.id.boxPasse);
        if (druecken) {
            setVisible(R.id.boxDruecken);
            setGone(R.id.boxFertig);
        } else {
            setVisible(R.id.boxFertig);
            setGone(R.id.boxDruecken);
        }
    }

    void rem_fbox(int sn) {
        if (sn >= numsp)
            return;
        setGone(R.id.box18);
        setGone(R.id.boxPasse);
        setGone(R.id.boxFertig);
        setGone(R.id.boxDruecken);
        setGone(R.id.boxHinweisStich);
    }

    void inv_fbox(int sn, int rev) {
    }

    void hndl_druecken(int c) {
        int sn = 0;

        if (c != 0) {
            c--;
            swap(cards, 10 * sn + c, drkcd + 30);
            putdesk(0, D_playx + c, D_playy);
            moveCardOverlay(cards[drkcd + 30], R.id.card0 + c,
                    drkcd == 0 ? R.id.cardSkatLeft : R.id.cardSkatRight, 0, 0,
                    2, 1);
            return;
        }
        inv_fbox(spieler, 1);
        if (hints[sn] && hintcard[0] != -1) {
            show_hint(sn, 0, false);
            show_hint(sn, 1, false);
        }
        stdwait();
        inv_fbox(spieler, 0);
        if (trumpf == 5
                && (((cards[30] & 7) == BUBE) || ((cards[31] & 7) == BUBE))) {
            di_buben();
            return;
        }
        rem_fbox(spieler);
        drbut = 0;
        if (trumpf == 5) {
            putback(sn, D_skatx, D_skaty);
            putback(sn, D_skatx + D_cardw, D_skaty);
            if (ramschspiele != 0 && klopfen) {
                di_klopfen(spieler);
            } else {
                vmh = left(vmh);
                if (vmh != 0)
                    di_schieben();
                else
                    start_ramsch();
            }
            return;
        }
        home_skat();
        save_skat(1);
        for (c = 0; c < 2; c++) {
            stsum += cardValues[cards[c + 30] & 7];
            gespcd[cards[c + 30]] = 1;
            cards[c + 30] = -1;
        }
        gedr = 2;
        do_ansagen();
        return;
    }

    void hndl_spielen(int c) {
        int i;

        if (cards[c] >= 0) {
            calc_poss(0);
            for (i = 0; i < possc; i++) {
                if (c == possi[i]) {
                    if (hints[0])
                        show_hint(0, 0, false);
                    drop_card(c, 0);
                    break;
                }
            }
        }
    }

    void hndl_nimmstich(int sn) {
        nimmstich[sn][1] = 0;
        phase = SPIELEN;
        for (sn = 0; sn < numsp; sn++) {
            if (nimmstich[sn][1] != 0) {
                phase = NIMMSTICH;
            }
        }
        if (phase == SPIELEN) {
            setInvisible(R.id.box18passeL);
            setInvisible(R.id.box18passeR);
            setGone(R.id.boxHinweisStich);
            next_stich();
        }
    }

    void setcurs(int f) {
    }

    private String getTranslation(int key) {
        return Translations.getTranslation(key, currLang);
    }
}
