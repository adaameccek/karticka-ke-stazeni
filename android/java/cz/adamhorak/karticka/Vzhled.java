package cz.adamhorak.karticka;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RadioGroup;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Vzhled widgetu: co otevře klepnutí (seznam karet, nebo ukázanou kartu), rohy (kulaté až hranaté),
 * pozadí (průhledné jen s kódem na tapetě, nebo černé) a jestli velký widget u obchodu nabídne Jsem v prodejně,
 * platí pro všechny widgety. Otevře se z widgetu (podržet,
 * Nastavení widgetu) nebo z Nastavení v appce. Nahoře živý náhled s kartou, kterou widget naposledy ukázal, každá
 * změna vzhledu se hned propíše i do widgetu na ploše. Kód naruby (bílý na černé) Adam zrušil, čtečky ho nebraly.
 */
public class Vzhled extends Activity {
    /** Pořadí jako Widget.RAMECEK: hranaté, jemné, malé, kulaté. */
    private static final int[] ROHY = { R.id.roh_hranate, R.id.roh_jemne, R.id.roh_male, R.id.roh_kulate };
    private FrameLayout nahled;
    private JSONObject karta;
    /** Velikost widgetu pro náhled v dp: toho, ze kterého se okno otevřelo, jinak prvního na ploše, jinak výchozí 4×2. */
    private float sirka = 250, vyska = 110;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        // nastavení je nepovinné: widget zůstane na ploše, i když člověk okno jen zavře
        int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
        setContentView(R.layout.vzhled);
        float d = getResources().getDisplayMetrics().density;
        getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - Math.round(32 * d),
                Math.round(420 * d)), ViewGroup.LayoutParams.WRAP_CONTENT);

        AppWidgetManager am = AppWidgetManager.getInstance(this);
        int[] na = Widget.vsechny(this);
        int pro = id != AppWidgetManager.INVALID_APPWIDGET_ID || na.length == 0 ? id : na[0];
        if (pro != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Bundle o = am.getAppWidgetOptions(pro);
            int w = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
            int h = o == null ? 0 : o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
            if (w > 0 && h > 0) { sirka = w; vyska = h; }
        }
        nahled = findViewById(R.id.nahled);
        karta = karta();

        SharedPreferences p = getSharedPreferences(Data.PREFS, 0);
        RadioGroup klepnuti = findViewById(R.id.klepnuti), rohy = findViewById(R.id.rohy), pozadi = findViewById(R.id.pozadi),
                tady = findViewById(R.id.tady_volba);
        klepnuti.check("karta".equals(klepnuti()) ? R.id.klepnuti_karta : R.id.klepnuti_hlavni);
        klepnuti.setOnCheckedChangeListener((g, r) -> klepnuti(r == R.id.klepnuti_karta ? "karta" : "hlavni"));
        rohy.check(ROHY[Widget.rohy(this)]);
        pozadi.check(Widget.pruhledne(this) ? R.id.pozadi_pruhledne : R.id.pozadi_cerne);
        tady.check(Widget.tlacitkoTady(this) ? R.id.tady_ano : R.id.tady_ne);
        tady.setOnCheckedChangeListener((g, r) -> {
            p.edit().putBoolean("wTady", r == R.id.tady_ano).apply();
            zmena();
        });
        rohy.setOnCheckedChangeListener((g, r) -> {
            for (int i = 0; i < ROHY.length; i++) if (ROHY[i] == r) p.edit().putInt("wRohy", i).apply();
            zmena();
        });
        pozadi.setOnCheckedChangeListener((g, r) -> {
            p.edit().putInt("wPozadi", r == R.id.pozadi_pruhledne ? 1 : 0).remove("wNaruby").apply();
            zmena();
        });
        findViewById(R.id.hotovo).setOnClickListener(v -> finish());
        nahled.post(this::ukaz);
    }

    /** Klepnutí na widget otevře: hlavni (seznam karet) nebo karta. Volbu má appka v datech karet (nastaveni.widget). */
    private String klepnuti() {
        try {
            JSONObject n = new JSONObject(Data.nacti(this)).optJSONObject("nastaveni");
            return n == null ? "hlavni" : n.optString("widget", "hlavni");
        } catch (Exception e) {
            return "hlavni";
        }
    }

    /** Běží-li appka, uloží volbu sama (data drží v paměti); jinak se zapíše rovnou do dat, appka ji načte při spuštění. */
    private void klepnuti(String h) {
        if (Hlavni.nastavZvenku("widget", h)) return;
        try {
            String t = Data.nacti(this);
            if (t.isEmpty()) return; // appka ještě nemá data, klepnutí pak otevře výchozí seznam karet
            JSONObject d = new JSONObject(t);
            JSONObject n = d.optJSONObject("nastaveni");
            if (n == null) d.put("nastaveni", n = new JSONObject());
            n.put("widget", h);
            Data.uloz(this, d.toString());
        } catch (JSONException e) {
            // poškozená data nepřepisovat
        }
    }

    private void zmena() {
        Widget.obnovPozdeji(this);
        ukaz();
    }

    /** Náhled ve skutečné velikosti widgetu, a když se nevejde, zmenšený jako obrázek
     *  (kreslený rovnou menší by schoval číslo pod kódem, které se do malého widgetu nevejde).
     *  Průhledný widget je na barevné „tapetě", na bílém okně by zmizel. */
    private void ukaz() {
        float d = getResources().getDisplayMetrics().density;
        int k = nahled.getWidth();
        if (k <= 0) return;
        nahled.setBackgroundResource(Widget.pruhledne(this) ? R.drawable.nahled_tapeta : 0);
        float m = Math.min(1f, Math.min(k / (sirka * d), 200f / vyska));
        int w = Math.round(sirka * d), h = Math.round(vyska * d);
        View v = Widget.nahled(this, karta, sirka, vyska).apply(this, nahled);
        v.setPivotX(w / 2f);
        v.setPivotY(0);
        v.setScaleX(m);
        v.setScaleY(m);
        nahled.removeAllViews();
        nahled.getLayoutParams().height = Math.round(h * m);
        nahled.addView(v, new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
    }

    /** Karta pro náhled: ta z widgetu, jinak první v appce, jinak ukázková. */
    private JSONObject karta() {
        JSONObject k = Data.posledni(this);
        if (k != null) return k;
        try {
            JSONArray a = new JSONObject(Data.nacti(this)).getJSONArray("karty");
            if (a.length() > 0) return a.getJSONObject(0);
        } catch (Exception e) {
            // bez karet
        }
        try {
            return new JSONObject().put("nazev", getString(R.string.vzhled_ukazka)).put("kod", "1234 5678 9012")
                    .put("format", "CODE_128").put("barva", "#E75294").put("text", "#FFFFFF");
        } catch (JSONException e) {
            return null;
        }
    }
}
