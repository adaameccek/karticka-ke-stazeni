package cz.adamhorak.karticka;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.util.SizeF;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import com.google.zxing.BarcodeFormat;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Widget na ploše: kód karty obchodu, kde člověk je (s polohou „Povolit vždy"), jinak karty, kterou
 * appka ukázala před chvílí. Bez obou ukáže první kartu z pořadí Vyber.poradi(): obchod tady, nejbližší obchod
 * (se vzdáleností vedle názvu), nebo kartu podle pořadí z Nastavení appky; prázdný je jen bez karet. Klepnutí na
 * řádek s názvem rozbalí seznam karet v tom pořadí (Nabidka), klepnutí jinam otevře appku (hlavní stránku, nebo
 * tu kartu, podle volby v Nastavení appky a ve Vzhledu widgetu), sluníčko v rohu dá na chvíli jas naplno (Jas).
 * Kartu vybranou ručně (v seznamu nebo v appce) widget drží Vyber.DRZI, pak zase rozhoduje poloha.
 * Rohy, pozadí a Jsem v prodejně si člověk nastaví ve Vzhledu widgetu.
 * Kód se kreslí zvlášť pro každý widget a každou jeho velikost, aby vyplnil celou plochu na celé pixely.
 * Widget je průhledný (nebo černý), svítí jen bílá plocha kódu; od zakulacených rohů launcheru je všechno odsazené.
 * Název, kód a číslo drží u sebe uprostřed a ve větším widgetu mají větší písmo.
 * Když svítí sluníčko, odpočítává vedle něj sekundy. Velký widget u obchodu, kde místo ještě není potvrzené,
 * nabídne dole Jsem v prodejně (místo se uloží ke kartě jako v appce), jde vypnout ve Vzhledu widgetu.
 */
public class Widget extends AppWidgetProvider {
    /** Klepnutí na „Jsem v prodejně" ve widgetu (vysílání jen sem, PendingIntent je neměnitelný). */
    static final String TADY = "cz.adamhorak.karticka.TADY";

    @Override
    public void onReceive(Context c, Intent i) {
        if (!TADY.equals(i.getAction())) {
            super.onReceive(c, i);
            return;
        }
        String id = i.getStringExtra("karta");
        if (id == null) return;
        PendingResult hotovo = goAsync();
        Context a = c.getApplicationContext();
        VLAKNO.execute(() -> {
            try {
                String n = Vyber.potvrd(a, id);
                // potvrzení ukáže tlačítko samo: bublinu (Toast) Android potlačí, když má appka vypnutá oznámení
                a.getSharedPreferences(Data.PREFS, 0).edit().putLong("tadyOk", System.currentTimeMillis())
                        .putBoolean("tadyOkDobre", n != null).apply();
                obnov(a);
                new Handler(Looper.getMainLooper()).postDelayed(() -> VLAKNO.execute(() -> obnov(a)), HLASKA + 100);
            } finally {
                hotovo.finish();
            }
        });
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager am, int[] ids) {
        obnov(c);
        Hlidka.naplanuj(c);
    }

    /** Změna velikosti chodí při tažení za okraj po krocích: kreslí se mimo hlavní vlákno (co() čte data z disku). */
    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager am, int id, Bundle o) {
        PendingResult hotovo = goAsync();
        Context a = c.getApplicationContext();
        VLAKNO.execute(() -> {
            try {
                synchronized (KRESLENI) {
                    posli(a, am, id, co(a));
                }
            } finally {
                hotovo.finish();
            }
        });
    }

    @Override
    public void onEnabled(Context c) { Hlidka.naplanuj(c); }

    /** Z plochy zmizel poslední widget jedné velikosti: hlídání polohy skončí, až když nezbyde žádný jiný. */
    @Override
    public void onDisabled(Context c) { if (vsechny(c).length == 0) Hlidka.zrus(c); }

    /**
     * Druhy widgetu se stejným kreslením, liší se jen výchozí velikostí na ploše. Nabízí se jen základní (Adam chtěl
     * jednu velikost, na ploše jde zvětšit i zmenšit); malý a velký jsou v nabídce widgetů skryté, ale už položené
     * na ploše fungují dál.
     */
    static final Class<?>[] DRUHY = { WidgetMaly.class, Widget.class, WidgetVelky.class };

    /** Všechny widgety Kartičky na ploše, ve všech velikostech. */
    static int[] vsechny(Context c) {
        AppWidgetManager am = AppWidgetManager.getInstance(c);
        int[] vse = new int[0];
        for (Class<?> d : DRUHY) {
            int[] ids = am.getAppWidgetIds(new ComponentName(c, d));
            if (ids == null || ids.length == 0) continue;
            int n = vse.length;
            vse = Arrays.copyOf(vse, n + ids.length);
            System.arraycopy(ids, 0, vse, n, ids.length);
        }
        return vse;
    }

    static void obnov(Context c) { obnov(c, true); }

    /** Překreslí všechny widgety; bez vzdy jen tehdy, když se změnila karta nebo vzdálenost vedle názvu (poloha z pozadí). */
    static void obnov(Context c, boolean vzdy) {
        AppWidgetManager am = AppWidgetManager.getInstance(c);
        int[] ids = vsechny(c);
        if (ids.length == 0) return;
        synchronized (KRESLENI) {
            Co co = co(c);
            SharedPreferences sp = c.getSharedPreferences(Data.PREFS, 0);
            if (!vzdy && co.klic().equals(sp.getString("wKlic", null))) return;
            sp.edit().putString("wKlic", co.klic()).apply();
            for (int id : ids) posli(c, am, id, co);
        }
    }

    /**
     * Předá widgetu vzhled. Kdyby obrázky kódu přesáhly paměť, kterou Android widgetu dá (počítá ji z rozlišení
     * displeje při startu telefonu, po přepnutí rozlišení bývá menší), nakreslí je znovu menší.
     */
    private static void posli(Context c, AppWidgetManager am, int id, Co co) {
        try {
            am.updateAppWidget(id, pohled(c, am, id, co, 1));
        } catch (IllegalArgumentException e) {
            am.updateAppWidget(id, pohled(c, am, id, co, 4));
        }
    }

    /**
     * Kreslí se z více vláken naráz (appka po uložení, klepnutí na widget, poloha z pozadí): zjištění, co ukázat,
     * a předání widgetu musí proběhnout v celku, jinak by pozdě dokreslený starší stav přepsal novější
     * (po „Jsem v prodejně" zůstávalo tlačítko na widgetu).
     */
    private static final Object KRESLENI = new Object();

    private static final ExecutorService VLAKNO = Executors.newSingleThreadExecutor();

    /** Překreslí widget mimo hlavní vlákno, postupně v pořadí volání (sluníčko přepíná barvy tam a zpátky). */
    static void obnovPozdeji(Context c) {
        Context a = c.getApplicationContext();
        VLAKNO.execute(() -> obnov(a));
    }

    /** Rohy z Vzhledu widgetu: rámeček a bílá plocha kódu, 0 hranaté, 1 jemné, 2 malé (výchozí), 3 kulaté. */
    static final int[] RAMECEK = { R.drawable.widget_pozadi_hranate, R.drawable.widget_pozadi_jemne,
            R.drawable.widget_pozadi, R.drawable.widget_pozadi_kulate };
    private static final int[] PANEL_ROH = { 0, R.dimen.w_panel_roh_jemny, R.dimen.w_panel_roh, R.dimen.w_panel_roh_kulaty };

    static int rohy(Context c) {
        int r = c.getSharedPreferences(Data.PREFS, 0).getInt("wRohy", 2);
        return r < 0 || r >= RAMECEK.length ? 2 : r;
    }

    /** Pozadí z Vzhledu widgetu: průhledné (výchozí, jen kód na tapetě), nebo černé. */
    static boolean pruhledne(Context c) { return c.getSharedPreferences(Data.PREFS, 0).getInt("wPozadi", 1) == 1; }

    /** Jestli velký widget u obchodu nabídne Jsem v prodejně: volba ve Vzhledu widgetu, výchozí ano. */
    static boolean tlacitkoTady(Context c) { return c.getSharedPreferences(Data.PREFS, 0).getBoolean("wTady", true); }

    /**
     * Co widget ukáže: kartu (null jen bez karet), vzdálenost k jejímu obchodu vedle názvu (jinak null) a jestli
     * velký widget nabídne Jsem v prodejně.
     */
    static final class Co {
        final JSONObject k;
        final String kde;
        final boolean tady;
        /** Text na tlačítku chvíli po klepnutí na Jsem v prodejně (Zapamatováno), jinak null. */
        final String hlaska;

        Co(JSONObject k, String kde, boolean tady, String hlaska) {
            this.k = k;
            this.kde = kde;
            this.tady = tady;
            this.hlaska = hlaska;
        }

        String klic() {
            return (k == null ? "" : k.optString("id")) + "|" + (kde == null ? "" : kde) + "|" + tady + "|" + hlaska;
        }
    }

    /** Jak dlouho tlačítko po klepnutí ukazuje, jak to dopadlo. */
    private static final long HLASKA = 4_000L;

    /**
     * Karta pro widget. S polohou „Povolit vždy" ji průběžně vybírá Hlidka; kartu vybranou ručně drží Vyber.DRZI
     * (Adam chtěl, ať se po krátké době sám vrátí výběr podle polohy), pak rozhodne poslední poloha (Vyber.uvolni).
     * Bez polohy na pozadí widget neví, kde člověk je, a karta z obchodu by tu visela i doma: kartu z appky proto
     * ukáže taky jen Vyber.DRZI. Jinak (a daleko od obchodů s kartou) první kartu z Vyber.poradi(); dokud platí
     * poslední poloha, řadí podle ní, pak podle Nastavení. Překreslení naplánuje na nejbližší z těch konců
     * (Hlidka drží jen jedno, platí poslední).
     */
    static Co co(Context c) {
        long ted = System.currentTimeMillis(), za = Long.MAX_VALUE;
        SharedPreferences sp = c.getSharedPreferences(Data.PREFS, 0);
        JSONObject k = Data.posledni(c);
        String kde = null;
        if (k != null) {
            long appCas = sp.getLong("appCas", 0);
            if (Hlidka.smi(c) || Sledovani.bezi()) {
                if (appCas > 0 && ted - appCas >= Vyber.DRZI) {
                    Vyber.uvolni(c);
                    k = Data.posledni(c);
                } else if (appCas > 0) {
                    za = appCas + Vyber.DRZI - ted;
                } else if (!Vyber.aktualni(c)) {
                    // kartu vybrala poloha, ale hlídání už dlouho žádnou nedodalo (systém ho uspal): kdo ví, kde
                    // člověk je. Widget kartu pustí a řadí jako bez polohy, dokud nepřijde nová.
                    k = null;
                } else {
                    za = Math.max(1000, Vyber.aktualniDo(c) - ted);
                }
            } else {
                long zbyva = sp.getLong("kartaCas", 0) + Vyber.DRZI - ted;
                if (zbyva > 0) za = Math.min(zbyva, Vyber.DRZI);
                else k = null;
            }
        }
        if (k == null) {
            List<Vyber.Polozka> s = Vyber.poradi(c);
            long cerstva = Vyber.cerstvaDo(c) - ted;
            if (cerstva > 0) za = Math.min(za, Math.min(cerstva, Vyber.STARA)); // pak bez vzdálenosti, podle Nastavení
            if (!s.isEmpty()) {
                k = s.get(0).k;
                kde = s.get(0).kde();
            }
        }
        boolean tady = k != null && tlacitkoTady(c) && Vyber.lzePotvrdit(c, k);
        if (tady) za = Math.min(za, Vyber.tadyDo(c) - ted); // se starší polohou tlačítko zmizí
        String hlaska = null;
        long ok = sp.getLong("tadyOk", 0) + HLASKA - ted;
        if (ok > 0 && ok <= HLASKA) {
            hlaska = c.getString(sp.getBoolean("tadyOkDobre", false) ? R.string.tady_ulozeno : R.string.tady_nevim);
            za = Math.min(za, ok);
        }
        if (za < Long.MAX_VALUE) Hlidka.prekresli(c, Math.max(0, za));
        return new Co(k, kde, tady, hlaska);
    }

    /**
     * Android 12+ dá seznam velikostí, ve kterých widget může být (na výšku, na šířku), a sám vybere (Samsung dá
     * jednu). Paměť na obrázky kódu se dělí mezi ně, při opakování v posli ještě deleni.
     */
    private static RemoteViews pohled(Context c, AppWidgetManager am, int id, Co co, int deleni) {
        Bundle o = am.getAppWidgetOptions(id);
        if (Build.VERSION.SDK_INT >= 31 && o != null) {
            ArrayList<SizeF> velikosti = o.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES);
            if (velikosti != null && !velikosti.isEmpty()) {
                List<SizeF> jine = new ArrayList<>();
                Set<String> byly = new HashSet<>();
                for (SizeF s : velikosti) {
                    if (jine.size() == 4) break;
                    if (s != null && byly.add(Math.round(s.getWidth()) + "x" + Math.round(s.getHeight()))) jine.add(s);
                }
                if (!jine.isEmpty()) {
                    long pamet = pamet(c, jine.size(), deleni);
                    Map<SizeF, RemoteViews> m = new HashMap<>();
                    for (SizeF s : jine)
                        m.put(s, jeden(c, co.k, co.kde, co.tady, co.hlaska, s.getWidth(), s.getHeight(), false, pamet));
                    return new RemoteViews(m);
                }
            }
        }
        int w = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int h = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        if (w <= 0 || h <= 0) { w = 250; h = 110; }
        return jeden(c, co.k, co.kde, co.tady, co.hlaska, w, h, false, pamet(c, 1, deleni));
    }

    /**
     * Kolik bajtů smí mít obrázek kódu v jedné z velikosti velikostí widgetu. Android dovolí v jednom předání
     * obrázky za 6 × šířka × výška displeje; bere se z toho půlka (rezerva) a dělí se mezi velikosti a deleni.
     */
    private static long pamet(Context c, int velikosti, int deleni) {
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        return 6L * dm.widthPixels * dm.heightPixels / 2 / Math.max(1, velikosti) / Math.max(1, deleni);
    }

    /** Náhled ve Vzhledu widgetu: stejný vzhled bez klepnutí (cizí karta by přepsala odkaz skutečného widgetu). */
    static RemoteViews nahled(Context c, JSONObject k, float wDp, float hDp) {
        return jeden(c, k, null, false, null, wDp, hDp, true, pamet(c, 1, 1));
    }

    /**
     * Poloměr, kterým launcher zakulatí okraj widgetu. Samsung One UI zakulatí každý widget, i když si rohy kreslí sám
     * (v S25 kolem 20 dp, systémová hodnota 24 dp s rezervou stačí). Starší Android ji nemá, tam 24 dp.
     */
    private static float polomer(Resources r, float d) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                float p = r.getDimension(android.R.dimen.system_app_widget_background_radius);
                if (p > 0) return p;
            } catch (Resources.NotFoundException e) {
                // launcher bez hodnoty
            }
        }
        return 24 * d;
    }

    /** Jak daleko od bočního kraje musí být bod, který je y od horního nebo dolního kraje, aby ho roh o poloměru p neusekl. */
    private static float zaRohem(float p, float y) {
        if (y >= p) return 0;
        return p - (float) Math.sqrt(p * p - (p - y) * (p - y));
    }

    /**
     * Název karty na widgetu: za ním šedá šipka k seznamu karet (Nabidka) a u karty nejbližšího obchodu, kde člověk
     * není, vzdálenost k němu (jen v širším widgetu, v úzkém má přednost název). Se sluníčkem jen název.
     */
    private static CharSequence titul(String nazev, String kde, boolean jas, float wDp) {
        if (jas) return nazev;
        SpannableStringBuilder s = new SpannableStringBuilder(nazev);
        int od = s.length();
        if (kde != null && wDp >= 120) {
            s.append("  ").append(kde.replace(' ', '\u00A0'));
            s.setSpan(new RelativeSizeSpan(0.8f), od, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        s.append(" \u25BE");
        s.setSpan(new ForegroundColorSpan(0xFFB4B9C2), od, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    /**
     * Číslo pod kódem jako na kartičce v appce (cisloKarty v app.js): kód, a když je to dlouhý text 2D kódu (Mensa:
     * adresa ověření), Další číslo na kartě (členské číslo). Prázdné = bez řádku s číslem.
     */
    private static String cisloPod(JSONObject k) {
        if (k == null) return "";
        String kod = k.optString("kod");
        if (kod.length() <= 24) return kod;
        String dalsi = k.optString("cislo").trim();
        return dalsi.length() <= 24 ? dalsi : "";
    }

    /**
     * Velikost písma, aby se t vešel do misto px: nejvýš max, nejmíň min. Ruční náhrada autoSize, který ve widgetu
     * nejde zvětšit (s ním launcher velikost z kódu nebere); u názvu měří i menší písmo vzdálenosti za názvem.
     */
    private static float vejde(CharSequence t, float max, float min, int misto, Typeface typ) {
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(max);
        tp.setTypeface(typ);
        float w = Layout.getDesiredWidth(t, tp);
        if (w <= 0 || misto <= 0) return max;
        return Math.max(min, Math.min(max, max * misto * 0.95f / w));
    }

    /**
     * Jeden vzhled pro widget wDp × hDp. Plocha kódu se počítá z dimens.xml stejně jako rozložení widget.xml.
     * Widget je průhledný (nebo černý) a svítí jen bílá plocha kódu (Adam chtěl, ať se rozsvítí jen kód a malý okraj
     * kolem něj), název a sluníčko jsou i v 1×1; jen ve widgetu nižším než 50 dp je kód sám (klepnutí otevře kartu
     * v appce a ta dá jas naplno sama). Launcher zakulatí okraj widgetu (One UI vždy, nedá se vypnout): název,
     * sluníčko, kód i číslo jsou proto od kraje tak daleko, aby je zakulacení neuseklo. Čárový kód sahá skoro do
     * kraje, protože nad ním je řádek s názvem a pod ním řádek s číslem (nebo stejně vysoký prázdný pruh).
     * Když svítí sluníčko (Jas), odpočítává vedle něj sekundy (po dvojkliku ∞, svítí do dalšího klepnutí); kód
     * zůstane, jak je, jen se rozsvítí displej (Adam nechtěl, ať se mu widget přebarvuje).
     * Ve větším widgetu je název, číslo i Jsem v prodejně větší (Adam chtěl, ať písmo hraje s velikostí widgetu),
     * v malém zůstane jako dřív. Místo, které kódu zbyde nad a pod ním (čárový kód je nejvýš 3/4 šířky vysoký,
     * 2D kód je čtverec), nezůstane od Androidu 12 mezi kódem a textem: název, kód a číslo drží u sebe uprostřed
     * widgetu a prázdno je jen nad a pod nimi (Adam chtěl co nejmíň prázdných mezer). Velký widget u obchodu, kde
     * místo ještě není potvrzené (tady), má dole Jsem v prodejně, nízký (4×1, 2×1) menší
     * v řádku s názvem. Obrázek kódu má nejvýš pamet bajtů.
     * Launcher může znovu použít minulé zobrazení, proto se viditelnost, velikosti, okraje i barvy prvků nastavují
     * pokaždé.
     */
    private static RemoteViews jeden(Context c, JSONObject k, String kde, boolean tady, String hlaska, float wDp,
            float hDp, boolean nahled, long pamet) {
        Resources r = c.getResources();
        float d = r.getDisplayMetrics().density;
        int rohy = rohy(c), px = TypedValue.COMPLEX_UNIT_PX;
        SharedPreferences pr = c.getSharedPreferences(Data.PREFS, 0);
        long ted = System.currentTimeMillis(), jasDo = nahled ? 0 : pr.getLong("jasDo", 0);
        boolean jas = jasDo > ted, trvale = jas && pr.getBoolean("jasTrvale", false);
        boolean pruhledne = pruhledne(c), novy = Build.VERSION.SDK_INT >= 31;
        float roh = PANEL_ROH[rohy] == 0 ? 0 : r.getDimension(PANEL_ROH[rohy]);
        float p = polomer(r, d);
        int sirka = Math.round(wDp * d), vyska = Math.round(hDp * d);
        // větší widget, větší písmo: od 140 dp (malý jako dřív) plynule do 400 dp nejvýš o 80 %; nízký široký widget
        // se měří výškou (× 1,5), ať písmo nesebere místo kódu. Výšku řádků jde měnit až od Androidu 12, starší
        // má řádky i písmo pevné
        float vetsi = novy ? 1 + 0.8f * Math.max(0, Math.min(1, (Math.min(wDp, 1.5f * hDp) - 140) / 260f)) : 1;
        int radekV = Math.round(r.getDimension(R.dimen.w_radek) * vetsi);
        int cisloV = Math.round(r.getDimension(R.dimen.w_cislo) * vetsi);
        int radekNahore = r.getDimensionPixelSize(R.dimen.w_radek_nahore);
        float pismo = r.getDimension(R.dimen.w_nazev_text) * vetsi;
        boolean radek = hDp >= 50;
        // nahoře řádek s názvem, dole řádek s číslem nebo stejně vysoký prázdný pruh; bez názvu stejný pruh i nahoře
        int nahore = radek ? radekV : cisloV, dole = cisloV;
        int bok = (int) Math.ceil(zaRohem(p, Math.min(nahore, dole)));

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        v.setImageViewResource(android.R.id.background, RAMECEK[rohy]);
        v.setInt(android.R.id.background, "setColorFilter", Color.BLACK);
        v.setViewVisibility(android.R.id.background, pruhledne ? View.INVISIBLE : View.VISIBLE);
        v.setInt(R.id.jas, "setColorFilter", Color.WHITE);
        v.setTextColor(R.id.nazev, Color.WHITE);
        v.setTextColor(R.id.cislo, Color.WHITE);
        v.setTextColor(R.id.zbyva, Color.WHITE);
        v.setTextColor(R.id.prazdne, 0xFFB4B9C2);
        // Jsem v prodejně roste s widgetem o polovinu méně než název (je to tlačítko, ne nápis)
        float tv = 1 + (vetsi - 1) / 2;
        int tadyV = Math.round(r.getDimension(R.dimen.w_tady_radek) * tv);
        if (novy) {
            v.setViewLayoutHeight(R.id.nazev, radekV, px);
            v.setViewLayoutHeight(R.id.zbyva, radekV, px);
            v.setViewLayoutHeight(R.id.cislo, cisloV, px);
            v.setViewLayoutHeight(R.id.tady_radek, tadyV, px);
            v.setViewLayoutHeight(R.id.tady, Math.round(r.getDimension(R.dimen.w_tady) * tv), px);
        }
        v.setTextViewTextSize(R.id.zbyva, px, pismo);
        v.setTextViewTextSize(R.id.tady, px, 12 * d * tv);
        int tBok = Math.round(14 * d * tv);
        v.setViewPadding(R.id.tady, tBok, 0, tBok, 0);
        // karta v appce obchodu (McDonald's): kód pro pokladnu ukáže jen jeho appka, klepnutí na widget ji otevře rovnou na kódu
        boolean vAppce = k != null && k.optString("kod").isEmpty() && !k.optString("appka").isEmpty();
        if (!nahled) {
            Intent i = new Intent(c, Hlavni.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (k != null) i.putExtra("karta", k.optString("id"));
            PendingIntent appka = PendingIntent.getActivity(c, 1, i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            v.setOnClickPendingIntent(R.id.koren, appka);
            // appka obchodu v telefonu není: klepnutí otevře kartu v Kartičce, tam je odkaz na web programu
            Intent obchod = vAppce ? Hlavni.appkaObchodu(c, k.optString("appka")) : null;
            if (obchod != null) v.setOnClickPendingIntent(R.id.koren, PendingIntent.getActivity(c, 5,
                    obchod.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            if (k == null) {
                v.setOnClickPendingIntent(R.id.nazev, appka); // bez karet není z čeho vybírat
                v.setContentDescription(R.id.nazev, null);
            } else {
                // seznam karet pod řádkem s názvem; měnitelný jen proto, aby launcher doplnil, kde řádek je
                v.setContentDescription(R.id.nazev, k.optString("nazev") + ", " + c.getString(R.string.nabidka_popis));
                Intent n = new Intent(c, Nabidka.class).setData(Uri.parse("karticka://nabidka"))
                        .putExtra("karta", k.optString("id"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                v.setOnClickPendingIntent(R.id.nazev, PendingIntent.getActivity(c, 4, n, PendingIntent.FLAG_UPDATE_CURRENT
                        | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0)));
            }
        }

        // Jsem v prodejně ve velkém widgetu dole pod kódem, kterému pod tlačítkem musí zbýt aspoň 100 dp na výšku;
        // v nízkém (4×1, 2×1) menší vpravo v řádku s názvem a kód zůstane celý (Adam 27.10.2026, viz vRadku). Po klepnutí
        // tlačítko chvíli zůstane a ukáže, jak to dopadlo (hlaska), ať kód neposkočí dvakrát po sobě. Náhled ve Vzhledu
        // widgetu ho ukáže (bez klepnutí), když je ve Vzhledu zapnuté, ať je vidět, jak vypadá
        boolean muze = (tady || hlaska != null || nahled && tlacitkoTady(c)) && radek && !vAppce && k != null
                && !k.optString("kod").isEmpty();
        boolean tlacitko = muze && wDp >= 150 && vyska - nahore - dole - tadyV >= Math.round(100 * d);
        int spodek = tlacitko ? tadyV : 0;

        Bitmap b = null;
        boolean cislo = false;
        // okraje plochy kódu: po stranách od rohů, dole prázdný pruh (s číslem nula, číslo má svůj řádek)
        int pBok = bok, pDole = dole;
        // pixelů obrázku kódu: černobílý RGB_565 má 2 bajty na pixel, průhledný ARGB 4
        int strop = (int) Math.min(6_000_000L, pamet / (pruhledne ? 4 : 2));
        int chyba = vAppce ? R.string.widget_appka : R.string.widget_chyba;
        if (k != null && !vAppce) {
            String kod = k.optString("kod");
            try {
                String f = k.optString("format", "CODE_128");
                boolean dva = Kod.je2D(BarcodeFormat.valueOf(f));
                // o 4 px méně, kdyby launcher dal widgetu o kousek jinou velikost, než hlásí: kód se pak neořízne
                int w = sirka - 2 * bok - 4, h = vyska - nahore - dole - spodek - 4;
                // číslo pod kódem: čárový kód má řádek pod sebou vždy (kvůli rohům), 2D kód jen tam, kde by byl
                // stejně omezený šířkou, nebo zbyde aspoň 100 dp; dlouhý text by byl nečitelný
                cislo = radek && !cisloPod(k).isEmpty() && (!dva || h >= Math.min(w, Math.round(100 * d)));
                if (dva && !cislo) {
                    // 2D kód je uprostřed, takže smí níž do řádku pro číslo, dokud roh neusekne jeho plochu:
                    // vyzkouší spodní okraje a vezme ten, kde je kód největší
                    int horniBok = (int) Math.ceil(zaRohem(p, nahore)), nejvic = 0;
                    for (int dol = 0; dol <= dole; dol += Math.max(1, Math.round(d))) {
                        int vys = vyska - nahore - dol - spodek;
                        int[] o = Kod.rozmer(f, kod, sirka - 2 * horniBok - 4, vys - 4, strop);
                        if (o == null) continue;
                        float x0 = (sirka - o[0]) / 2f, y0 = (vys - o[1]) / 2f;
                        if (x0 < zaRohem(p, dol + spodek + y0) || x0 < zaRohem(p, nahore + y0)) continue;
                        if (o[0] * o[1] > nejvic) {
                            nejvic = o[0] * o[1];
                            pBok = horniBok;
                            pDole = dol;
                        }
                    }
                }
                if (cislo) pDole = 0;
                b = Kod.doPlochy(f, kod, sirka - 2 * pBok - 4, vyska - nahore - pDole - (cislo ? dole : 0) - spodek - 4,
                        Math.round(2 * d), roh, pruhledne, strop);
                chyba = R.string.widget_maly;
            } catch (Exception e) {
                b = null;
            }
        }
        v.setViewPadding(R.id.plocha, pBok, radek ? 0 : nahore, pBok, pDole);
        // co kódu zbyde nad a pod ním, se stáhne: plocha kódu je jen o 4 px vyšší než kód (stejná rezerva jako výš)
        // a zbytek jde půl nad název a půl pod číslo. Kód i texty se tím jen přiblíží ke středu, dál od rohů.
        // Sluníčko a odpočet se posunou s názvem (okraj jde nastavit až od Androidu 12, starší nechá místo, jak je)
        int volno = b == null || !novy ? 0
                : Math.max(0, vyska - nahore - pDole - (cislo ? dole : 0) - spodek - 4 - b.getHeight());
        int padNahore = volno / 2, padDole = volno - padNahore;
        v.setViewPadding(R.id.obsah, 0, padNahore, 0, padDole);
        if (novy) {
            v.setViewLayoutMargin(R.id.jas, RemoteViews.MARGIN_TOP, padNahore, px);
            v.setViewLayoutMargin(R.id.zbyva, RemoteViews.MARGIN_TOP, padNahore, px);
        }

        // sluníčko (ikona 12 dp, ve větším widgetu větší, uprostřed řádku s názvem) tak daleko od pravého kraje,
        // aby ho roh neusekl; plocha na klepnutí zůstává v pravém horním rohu řádku (podle ní Jas pozná, kde widget je)
        int dotyk = r.getDimensionPixelSize(R.dimen.w_slunce_dotyk), ikona = Math.round(12 * d * vetsi);
        int sNahore = radekNahore + (radekV - radekNahore - ikona) / 2;
        float pul = ikona / 2f, stred = Math.min(p, padNahore + sNahore + pul);
        int sVpravo = (int) Math.ceil(Math.max(4 * d, p - pul
                - (float) Math.sqrt(Math.max(0, (p - pul) * (p - pul) - (p - stred) * (p - stred)))));
        v.setViewPadding(R.id.jas, Math.max(0, dotyk - ikona - sVpravo), sNahore, sVpravo,
                Math.max(0, dotyk - ikona - sNahore));
        // název od levého kraje podle toho, kam roh sahá ve výšce horního okraje písmen
        int nVlevo = (int) Math.ceil(Math.max(4 * d, zaRohem(p, padNahore + radekNahore + 3 * d)));
        int uSlunce = sVpravo + ikona + Math.round(3 * d);
        // odpočet jasu vlevo od sluníčka; název se zkrátí o šířku nejdelšího čísla, ať s odpočtem neposkakuje
        int odpocet = 0;
        if (jas && radek) {
            Paint pa = new Paint();
            pa.setTextSize(pismo);
            pa.setTypeface(Typeface.DEFAULT_BOLD);
            odpocet = (int) Math.ceil(pa.measureText("88 s")) + Math.round(4 * d);
        }
        // Jsem v prodejně v řádku s názvem (nízký widget): vlevo od odpočtu a sluníčka, název se zkrátí o jeho šířku.
        // Názvu musí zbýt aspoň 60 dp, jinak krátký nápis Jsem tu (2×1); hláška po klepnutí smí název na 4 s stáhnout
        // až na 30 dp. Šířku tlačítka jde nastavit až od Androidu 12, starší ho ukáže, jen když se vejde celé
        String vRadku = null;
        int rSirka = 0, rBok = Math.round(7 * d * vetsi), rKonec = uSlunce + odpocet + Math.round(3 * d);
        float rPismo = r.getDimension(R.dimen.w_nazev_text) * vetsi * 0.9f;
        if (muze && !tlacitko && b != null) {
            Paint pa = new Paint();
            pa.setTextSize(rPismo);
            pa.setTypeface(Typeface.DEFAULT_BOLD);
            int misto = sirka - nVlevo - rKonec - Math.round(5 * d);
            String[] napisy = hlaska != null ? new String[] {hlaska}
                    : new String[] {c.getString(R.string.tady), c.getString(R.string.tady_kratce)};
            for (String n : napisy) {
                int w = (int) Math.ceil(pa.measureText(n)) + 2 * rBok, nejvic = misto - Math.round((hlaska != null ? 30 : 60) * d);
                if (w <= nejvic || hlaska != null && novy && nejvic >= Math.round(40 * d)) {
                    vRadku = n;
                    rSirka = Math.min(w, nejvic);
                    break;
                }
            }
        }
        int vpravo = vRadku == null ? uSlunce + odpocet : rKonec + rSirka + Math.round(5 * d);
        v.setViewPadding(R.id.nazev, nVlevo, radekNahore, vpravo, 0);
        // dlouhý název se zmenší, ať se vejde (nejvýš na w_nazev_text_min, pak teprve tři tečky)
        CharSequence t = k == null ? c.getString(R.string.nazev) : titul(k.optString("nazev"), kde, jas, wDp);
        v.setTextViewText(R.id.nazev, t);
        v.setTextViewTextSize(R.id.nazev, px, vejde(t, pismo, r.getDimension(R.dimen.w_nazev_text_min),
                sirka - nVlevo - vpravo, Typeface.DEFAULT_BOLD));
        v.setViewVisibility(R.id.tady_nahore, vRadku != null ? View.VISIBLE : View.GONE);
        if (vRadku != null) {
            // tlačítko vysoké jako písmo názvu (řádek bez horního odsazení), plocha na klepnutí o 8 dp níž do kódu
            int rV = radekV - radekNahore;
            v.setViewPadding(R.id.tady_nahore, Math.round(4 * d), padNahore + radekNahore, rKonec, 0);
            if (novy) {
                v.setViewLayoutHeight(R.id.tady_nahore, padNahore + radekNahore + rV + Math.round(8 * d), px);
                v.setViewLayoutHeight(R.id.tady_n, rV, px);
                v.setViewLayoutWidth(R.id.tady_n, rSirka, px);
            }
            v.setTextViewTextSize(R.id.tady_n, px, rPismo);
            v.setViewPadding(R.id.tady_n, rBok, 0, rBok, 0);
            v.setTextViewText(R.id.tady_n, vRadku);
            if (!nahled) v.setOnClickPendingIntent(R.id.tady_nahore, hlaska != null ? null : PendingIntent.getBroadcast(c,
                    6, new Intent(c, Widget.class).setAction(TADY).putExtra("karta", k.optString("id")),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        v.setViewPadding(R.id.zbyva, 0, radekNahore, uSlunce, 0);
        v.setTextViewText(R.id.zbyva, trvale ? "∞" : jas ? (jasDo - ted + 999) / 1000 + " s" : "");
        // číslo pod kódem roste s widgetem jako název, dlouhé se zmenší, ať se vejde; od kraje tak daleko, kam sahá
        // roh ve výšce spodku číslic (kousek nad spodkem řádku)
        int cBok = (int) Math.ceil(Math.max(4 * d, zaRohem(p, padDole + spodek + 2 * d)));
        v.setViewPadding(R.id.cislo, cBok, 0, cBok, 0);
        v.setTextViewTextSize(R.id.cislo, px, vejde(cisloPod(k),
                r.getDimension(R.dimen.w_cislo_text) * vetsi, r.getDimension(R.dimen.w_cislo_text_min), sirka - 2 * cBok,
                Typeface.MONOSPACE));

        v.setViewVisibility(R.id.nazev, radek ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.jas, radek ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.zbyva, jas && radek && b != null ? View.VISIBLE : View.GONE);
        v.setViewVisibility(R.id.tady_radek, tlacitko && b != null ? View.VISIBLE : View.GONE);
        if (tlacitko && b != null) {
            v.setTextViewText(R.id.tady, hlaska != null ? hlaska : c.getString(R.string.tady));
            // s hláškou klepnutí nic nedělá (místo je uložené), null launcheru zruší i minulý odkaz
            if (!nahled) v.setOnClickPendingIntent(R.id.tady, hlaska != null ? null : PendingIntent.getBroadcast(c, 6,
                    new Intent(c, Widget.class).setAction(TADY).putExtra("karta", k.optString("id")),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        if (b == null) {
            v.setTextViewText(R.id.prazdne, c.getString(k != null ? chyba : R.string.widget_prazdny));
            v.setViewVisibility(R.id.prazdne, View.VISIBLE);
            v.setViewVisibility(R.id.kod, View.GONE);
            v.setViewVisibility(R.id.cislo, View.GONE);
            // bez kódu na widgetu není čemu dát jas: kód i jas obstará appka obchodu
            if (vAppce) v.setViewVisibility(R.id.jas, View.GONE);
            // bez karty sluníčko otevře appku: ukáže kartu obchodu, kde člověk je, a jas dá naplno sama
            else if (!nahled) v.setOnClickPendingIntent(R.id.jas, PendingIntent.getActivity(c, 3,
                    new Intent(c, Hlavni.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE));
            return v;
        }

        v.setImageViewBitmap(R.id.kod, b);
        v.setViewVisibility(R.id.kod, View.VISIBLE);
        v.setViewVisibility(R.id.prazdne, View.GONE);
        if (cislo) v.setTextViewText(R.id.cislo, cisloPod(k));
        v.setViewVisibility(R.id.cislo, cislo ? View.VISIBLE : View.GONE);
        if (nahled) return v;
        // velikost widgetu a posun sluníčka od horního kraje v adrese: Jas z nich pozná, kam se klepe, a každá
        // velikost má svůj PendingIntent
        Intent j = new Intent(c, Jas.class)
                .setData(Uri.parse("karticka://jas/" + sirka + "x" + vyska + "x" + padNahore))
                .putExtra("karta", k.optString("id"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        // měnitelný jen proto, aby launcher mohl doplnit, kde sluníčko na obrazovce je (neměnitelnému to Android zahodí);
        // cíl, adresa i karta jsou dané, doplnit jde jen to, co v intentu chybí
        v.setOnClickPendingIntent(R.id.jas, PendingIntent.getActivity(c, 2, j, PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0)));
        return v;
    }
}
