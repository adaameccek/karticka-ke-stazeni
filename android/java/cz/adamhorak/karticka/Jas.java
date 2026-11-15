package cz.adamhorak.karticka;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;

import org.json.JSONObject;

/**
 * Sluníčko na widgetu: jas displeje naplno na 5, 15 nebo vlastní počet sekund (Jas naplno v Nastavení appky), pak zase původní.
 * Průhledné okno přes celou obrazovku nastaví jas jen sobě, takže nepotřebuje žádné oprávnění
 * (žádné WRITE_SETTINGS) a po zavření se sám vrátí původní jas, i automatický.
 * Android 12+ pod průhlednou aktivitou cizí appky dotyky na plochu nepustí, proto je okno chytá samo:
 * další klepnutí na sluníčko vrátí jas dřív, dvojklik nechá svítit, dokud se znovu neklepne (nejvýš 10 minut),
 * klepnutí na widget otevře appku (jako bez sluníčka), jinam vrátí jas hned. Zpět, odchod na jinou appku
 * nebo zhasnutí displeje vrátí jas taky hned.
 * Po dobu jasu widget vedle sluníčka odpočítává sekundy (okno ho každou sekundu překreslí).
 * Karta, u které se ke čtečce přikládá horní část telefonu (Albert), má navíc kód v bílém panelu u horního
 * okraje obrazovky, ať je widget na ploše kdekoli; klepnutí na panel funguje jako na sluníčko. V Nastavení appky
 * jde panel zapnout pro všechny karty a pod ním nechat plochu, ztlumit ji, nebo dát černou: černá na OLED displeji
 * nesvítí, takže naplno svítí jen kód (jas jde nastavit jen celému displeji). S černou je widget schovaný, klepnutí
 * mimo panel proto jen vrátí jas.
 */
public class Jas extends Activity {
    /** Dvě klepnutí rychleji po sobě jsou dvojklik (v appce na kód stejně). */
    private static final long DVOJKLIK = 450;
    /** Po dvojkliku svítí, dokud se znovu neklepne, ale nejvýš tolik: zapomenutý telefon by jinak nezhasl. */
    private static final long TRVALE_MAX = 10 * 60_000L;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable casovac = this::konec, tik = this::tik;
    /** Sluníčko a celý widget na obrazovce (sluníčko je v jeho pravém horním rohu); null, když to launcher neřekne. */
    private Rect slunce, widget;
    /** Panel s kódem u horního okraje obrazovky, nebo null. */
    private View panel;
    /** Pod panelem je černá: widget není vidět, klepnutí na sluníčko ani na widget se proto neberou. */
    private boolean cerna;
    private String karta;
    private long doba, jasDo;
    /** Kdy (uptimeMillis) přišlo klepnutí, které může být první půlkou dvojkliku; 0, když žádné. */
    private long klep;
    /** Widget kvůli jasu odpočítává. */
    private boolean svetle;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL;
        getWindow().setAttributes(lp);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        nacti(getIntent());
        ukazPanel();
        doba = doba(this);
        klep = SystemClock.uptimeMillis();
        start(false);
    }

    /**
     * Jak dlouho svítí: jako po klepnutí na kód v appce (Jas naplno 5, 15 s nebo vlastní 1 až 600 s). Volba Hned
     * platí jen v appce (svítí, dokud je karta otevřená), sluníčko na widgetu pak svítí 30 s.
     */
    static long doba(Context c) {
        JSONObject n = nastaveni(c);
        int s = n == null ? 5 : n.optInt("jas", 5);
        return (s == 0 ? 30 : s >= 1 && s <= 600 ? s : 5) * 1000L;
    }

    /** Nastavení z appky (jas, panel, pozadi), null, když ještě žádná nejsou. */
    private static JSONObject nastaveni(Context c) {
        try {
            return new JSONObject(Data.nacti(c)).optJSONObject("nastaveni");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Jas od teď na dobu z Nastavení, nebo po dvojkliku trvale: widget ukáže celou dobu (trvale ∞)
     * a odpočet se překreslí vždy, když se změní číslo.
     */
    private void start(boolean trvale) {
        h.removeCallbacks(casovac);
        h.removeCallbacks(tik);
        long d = trvale ? TRVALE_MAX : doba;
        jasDo = System.currentTimeMillis() + d;
        h.postDelayed(casovac, d);
        svetle = true;
        // svetleDo je po starší verzi (kód naruby se jen přepínal do klasických barev, naruby už není)
        getSharedPreferences(Data.PREFS, 0).edit().putLong("jasDo", jasDo).putBoolean("jasTrvale", trvale)
                .remove("svetleDo").apply();
        Widget.obnovPozdeji(this);
        if (!trvale) dalsiTik();
    }

    /**
     * Klepnutí na sluníčko nebo na panel s kódem: druhé do DVOJKLIK ms nechá svítit trvale, jedno samotné vrátí jas.
     * Zhasne se až po DVOJKLIK ms, ať se z prvního klepnutí ještě může stát dvojklik.
     */
    private void klepnuti(long t) {
        h.removeCallbacks(casovac);
        if (t - klep < DVOJKLIK) {
            klep = 0;
            start(true);
        } else {
            klep = t;
            h.postDelayed(casovac, DVOJKLIK);
        }
    }

    /** Widget ukazuje zbývající sekundy zaokrouhlené nahoru: další překreslení kousek po tom, co se číslo změní. */
    private void dalsiTik() {
        long zbyva = jasDo - System.currentTimeMillis();
        if (zbyva > 1000) h.postDelayed(tik, (zbyva - 1) % 1000 + 1 + 20);
    }

    private void tik() {
        Widget.obnovPozdeji(this);
        dalsiTik();
    }

    /** Widget zpátky do běžného vzhledu; jen jednou, ať okno skončí jakkoli. Kdyby se okno zavřít nestihlo,
     *  vrátí ho další překreslení, protože čas jasDo už bude pryč. */
    private void zpatky() {
        h.removeCallbacks(tik);
        if (!svetle) return;
        svetle = false;
        getSharedPreferences(Data.PREFS, 0).edit().remove("jasDo").remove("jasTrvale").apply();
        Widget.obnovPozdeji(this);
    }

    /** Klepnutí na sluníčko, které prošlo na plochu, protože okno ještě nebylo vidět: rychlé druhé je dvojklik. */
    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        nacti(i);
        ukazPanel();
        long t = SystemClock.uptimeMillis();
        boolean dvakrat = t - klep < DVOJKLIK;
        klep = dvakrat ? 0 : t;
        start(dvakrat);
    }

    private void nacti(Intent i) {
        slunce = i.getSourceBounds();
        karta = i.getStringExtra("karta");
        zapis(this, karta);
        widget = null;
        Uri u = i.getData();
        if (slunce == null || u == null || u.getLastPathSegment() == null) return;
        try {
            // šířka x výška widgetu x o kolik je sluníčko pod horním krajem (widget stáhne prázdné místo kolem kódu)
            String[] wh = u.getLastPathSegment().split("x");
            int w = Integer.parseInt(wh[0]), v = Integer.parseInt(wh[1]), posun = wh.length > 2 ? Integer.parseInt(wh[2]) : 0;
            widget = new Rect(slunce.right - w, slunce.top - posun, slunce.right, slunce.top - posun + v);
        } catch (RuntimeException e) {
            widget = null;
        }
    }

    /** Kód má být u horního okraje obrazovky: volba u karty, jinak podle obchodu (stejně jako kodNahore v app.js). */
    static boolean nahore(JSONObject k) {
        return k.has("nahore") ? k.optBoolean("nahore") : "albert".equals(k.optString("retezec"));
    }

    /**
     * Kód karty v bílém panelu u horního okraje obrazovky, když se u její pokladny ke čtečce přikládá horní část
     * telefonu (Albert), nebo u všech karet podle Nastavení: černý na bílé přes celou šířku, pod ním číslo.
     * Pod panelem plocha, ztlumená plocha, nebo černá podle Nastavení. Bez panelu je okno prázdné a celé průhledné.
     */
    private void ukazPanel() {
        JSONObject k = Data.karta(this, karta), n = nastaveni(this);
        String kod = k == null ? "" : k.optString("kod");
        boolean vsem = n != null && "vse".equals(n.optString("panel"));
        String pod = n == null ? "" : n.optString("pozadi");
        Bitmap b = null;
        if (!kod.isEmpty() && (vsem || nahore(k))) {
            try {
                String f = k.optString("format", "CODE_128");
                int w = getResources().getDisplayMetrics().widthPixels - 2 * dp(20);
                boolean dva = Kod.je2D(BarcodeFormat.valueOf(f));
                // tichou zónu dělá bílý panel kolem kódu (20 dp po stranách)
                b = Kod.naBilou(f, kod, w, dva ? Math.min(w, dp(220)) : dp(110), dp(20));
            } catch (Exception e) {
                b = null;
            }
        }
        if (b == null) {
            if (panel == null) return;
            panel = null;
            cerna = false;
            setContentView(new View(this));
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            getWindow().getDecorView().setSystemUiVisibility(0);
            return;
        }
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER_HORIZONTAL);
        GradientDrawable pozadi = new GradientDrawable();
        pozadi.setColor(Color.WHITE);
        float r = dp(18);
        pozadi.setCornerRadii(new float[] { 0, 0, 0, 0, r, r, r, r });
        p.setBackground(pozadi);
        p.setElevation(dp(8));
        p.setPadding(0, dp(10), 0, dp(12));
        ImageView obr = new ImageView(this);
        obr.setScaleType(ImageView.ScaleType.CENTER);
        obr.setImageBitmap(b);
        obr.setContentDescription(k.optString("nazev"));
        p.addView(obr, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, b.getHeight()));
        if (kod.length() <= 24) {
            TextView cislo = new TextView(this);
            cislo.setText(kod);
            cislo.setTextSize(15);
            cislo.setTextColor(0xFF1B1E24);
            cislo.setLetterSpacing(0.08f);
            cislo.setGravity(Gravity.CENTER);
            cislo.setPadding(dp(20), dp(6), dp(20), 0);
            p.addView(cislo);
        }
        FrameLayout okno = new FrameLayout(this);
        okno.addView(p, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        // ztlumená plocha je vedle kódu naplno zhruba jako běžný jas, černá na OLED nesvítí vůbec
        int podPanelem = "cerna".equals(pod) ? Color.BLACK : "tlumena".equals(pod) ? 0x99000000 : Color.TRANSPARENT;
        okno.setBackgroundColor(podPanelem);
        getWindow().setNavigationBarColor(podPanelem);
        setContentView(okno);
        panel = p;
        cerna = podPanelem == Color.BLACK;
        // stavový řádek nad panelem bílý s tmavými ikonami, kód začíná hned pod ním
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private boolean naPanelu(int x, int y) {
        if (panel == null || !panel.isShown()) return false;
        int[] o = new int[2];
        panel.getLocationOnScreen(o);
        return x >= o[0] && x < o[0] + panel.getWidth() && y >= o[1] && y < o[1] + panel.getHeight();
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    /**
     * Kdy sluníčko dalo které kartě jas: appka si to při dalším otevření převezme do historie ukázání.
     * Jen čas a id karty, nejvýš posledních 200, a stejná karta do minuty jen jednou.
     */
    static void zapis(Context c, String karta) {
        if (karta == null || !karta.matches("[\\w-]{1,40}")) return;
        synchronized (Jas.class) {
            SharedPreferences p = c.getSharedPreferences(Data.PREFS, 0);
            String s = p.getString("jasy", "");
            long ted = System.currentTimeMillis();
            String[] z = s.isEmpty() ? new String[0] : s.split(",");
            if (z.length > 0) {
                String[] posledni = z[z.length - 1].split(":");
                try {
                    if (posledni.length == 2 && posledni[1].equals(karta) && ted - Long.parseLong(posledni[0]) < 60_000) return;
                } catch (NumberFormatException e) {
                    // poškozený záznam, appka ho vynechá
                }
            }
            StringBuilder b = new StringBuilder();
            for (int i = Math.max(0, z.length - 199); i < z.length; i++) if (!z[i].isEmpty()) b.append(z[i]).append(',');
            p.edit().putString("jasy", b.append(ted).append(':').append(karta).toString()).apply();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getActionMasked() != MotionEvent.ACTION_DOWN) return true;
        int x = Math.round(e.getRawX()), y = Math.round(e.getRawY());
        if ((!cerna && slunce != null && slunce.contains(x, y)) || naPanelu(x, y)) {
            klepnuti(e.getEventTime());
            return true;
        }
        if (!cerna && widget != null && widget.contains(x, y)) {
            Intent k = new Intent(this, Hlavni.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (karta != null && !karta.isEmpty()) k.putExtra("karta", karta);
            startActivity(k);
        }
        konec();
        return true;
    }

    /** Odchod na jinou appku nebo zhasnutí displeje: jas hned zpátky. */
    @Override
    protected void onStop() {
        super.onStop();
        konec();
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacks(casovac);
        zpatky();
        super.onDestroy();
    }

    private void konec() {
        h.removeCallbacks(casovac);
        zpatky();
        if (isFinishing()) return;
        finishAndRemoveTask();
        overridePendingTransition(0, 0);
    }
}
