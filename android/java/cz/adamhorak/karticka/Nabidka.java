package cz.adamhorak.karticka;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Seznam karet u widgetu: klepnutí na řádek s názvem ho rozbalí hned pod ním (průhledné okno přes plochu jako
 * u sluníčka). Karty jsou v pořadí Vyber.poradi(): nahoře obchody tady, nebo nejbližší obchod, pod nimi podle
 * Nastavení appky, u obchodů do 300 km vzdálenost. Okno si řekne o čerstvou polohu (jen dokud je otevřené, poloha
 * na pozadí k tomu není potřeba), widget podle ní srovná a seznam přeřadí, dokud se ho člověk nedotkl.
 * Vybraná karta se ukáže na widgetu a na tom místě ji poloha z pozadí 10 minut nepřebije (Vyber.DRZI, jako kartu
 * z appky), pak zase rozhoduje poloha.
 * Klepnutí vedle, Zpět nebo odchod jinam seznam zavře.
 */
public class Nabidka extends Activity {
    /** Vzdálenost se u karty ukáže jen do 300 km, dál nic neříká. */
    private static final double DALEKO = 300_000;
    /** Nejvýš osm a půl řádku, ať je vidět, že seznam pokračuje. */
    private static final int MAX_VYSKA = 40 + 8 * 48 + 24 + 6;

    private final Handler h = new Handler(Looper.getMainLooper());
    /** Řádek s názvem na widgetu (na obrazovce); null, když to launcher neřekne, pak je seznam uprostřed. */
    private Rect kotva;
    private LinearLayout panel, seznam;
    private ScrollView posuv;
    /** Čerstvá poloha z tohohle okna, null než přijde. */
    private Location poloha;
    private CancellationSignal zruseni;
    private LocationListener posluchac;
    /** Kolikáté načtení seznamu: starší výsledek, který doběhl pozdě, se zahodí. */
    private int kolo;
    private boolean dotknuto, ukazano;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.dimAmount = 0.35f;
        getWindow().setAttributes(lp);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable pozadi = new GradientDrawable();
        pozadi.setColor(0xFF17191D);
        pozadi.setCornerRadius(dp(18));
        panel.setBackground(pozadi);
        panel.setElevation(dp(8));
        panel.setClipToOutline(true);
        panel.setClickable(true); // klepnutí do seznamu mimo řádek ho nezavře
        panel.setPadding(0, 0, 0, dp(6));
        panel.setVisibility(View.INVISIBLE); // až s kartami, ať neblikne prázdný

        TextView nadpis = new TextView(this);
        nadpis.setText(R.string.nabidka_nadpis);
        nadpis.setTextSize(12);
        nadpis.setTextColor(0xFF8A909B);
        nadpis.setPadding(dp(18), dp(14), dp(18), dp(6));
        panel.addView(nadpis);

        posuv = new ScrollView(this);
        seznam = new LinearLayout(this);
        seznam.setOrientation(LinearLayout.VERTICAL);
        posuv.addView(seznam);
        panel.addView(posuv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Okno okno = new Okno(this);
        okno.addView(panel);
        okno.setOnClickListener(v -> konec());
        setContentView(okno);

        nacti(getIntent());
        nactiSeznam();
        zjistiPolohu();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        nacti(i);
        dotknuto = false;
        nactiSeznam();
        if (poloha == null && zruseni == null && posluchac == null) zjistiPolohu();
        View okno = findViewById(android.R.id.content);
        if (okno != null) okno.requestLayout();
    }

    private void nacti(Intent i) {
        kotva = i.getSourceBounds();
        if (kotva != null && kotva.isEmpty()) kotva = null;
    }

    /**
     * Karty v pořadí pro widget, mimo hlavní vlákno. Poloha, kterou už telefon zná (nebo čerstvá z tohohle okna),
     * nejdřív srovná widget: seznam pak začíná kartou, kterou widget ukazuje.
     */
    private void nactiSeznam() {
        final int moje = ++kolo;
        final Location l = poloha;
        final Context app = getApplicationContext();
        new Thread(() -> {
            List<Vyber.Polozka> s;
            String id;
            try {
                Location x = l != null ? l : Hlidka.posledni(app);
                if (x != null && Hlidka.vek(x) < Vyber.STARA) Hlidka.prepocti(app, x);
                s = Vyber.poradi(app);
                Widget.Co co = Widget.co(app);
                id = co.k == null ? null : co.k.optString("id");
            } catch (RuntimeException e) {
                return; // seznam zůstane, jak byl
            }
            runOnUiThread(() -> {
                if (moje != kolo || isFinishing()) return;
                if (ukazano && dotknuto) return; // člověk už vybírá: seznam se mu pod prstem nepřeskládá
                ukaz(s, id);
            });
        }).start();
    }

    private void ukaz(List<Vyber.Polozka> s, String id) {
        if (s.isEmpty()) { // karty mezitím zmizely: appka nabídne přidat první
            startActivity(new Intent(this, Hlavni.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            konec();
            return;
        }
        seznam.removeAllViews();
        View vybrany = null;
        for (Vyber.Polozka x : s) {
            boolean tahle = x.k.optString("id").equals(id);
            View r = radek(x, tahle);
            seznam.addView(r);
            if (tahle) vybrany = r;
        }
        if (vybrany != null) {
            View v = vybrany;
            // karta z widgetu je vidět i hluboko v seznamu
            posuv.post(() -> {
                if (v.getBottom() > posuv.getHeight()) posuv.scrollTo(0, v.getTop() - dp(48));
            });
        }
        if (!ukazano) {
            ukazano = true;
            panel.setAlpha(0);
            panel.setVisibility(View.VISIBLE);
            panel.animate().alpha(1).setDuration(120).start();
        }
    }

    /** Řádek karty: tečka v barvě karty, název, vzdálenost k obchodu; karta z widgetu je podbarvená. */
    private View radek(Vyber.Polozka x, boolean tahle) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(48));
        r.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable podklad = null;
        if (tahle) {
            podklad = new GradientDrawable();
            podklad.setColor(0xFF2A2E35);
            podklad.setCornerRadius(dp(12));
        }
        GradientDrawable maska = new GradientDrawable();
        maska.setColor(Color.WHITE);
        maska.setCornerRadius(dp(12));
        r.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), podklad, maska));

        View tecka = new View(this);
        GradientDrawable t = new GradientDrawable();
        t.setShape(GradientDrawable.OVAL);
        int barva = barva(x.k.optString("barva"));
        t.setColor(barva);
        if (tmava(barva)) t.setStroke(Math.max(1, dp(1)), 0xFF5A606B); // černá tečka na tmavém podkladu
        tecka.setBackground(t);
        r.addView(tecka, new LinearLayout.LayoutParams(dp(12), dp(12)));

        TextView n = new TextView(this);
        n.setText(x.k.optString("nazev"));
        n.setTextColor(Color.WHITE);
        n.setTextSize(15);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        if (tahle) n.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        np.setMarginStart(dp(12));
        r.addView(n, np);

        if (x.v <= DALEKO) {
            TextView km = new TextView(this);
            km.setText(Vyber.km(x.v));
            km.setTextColor(0xFF9AA0AA);
            km.setTextSize(13);
            km.setSingleLine(true);
            LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            kp.setMarginStart(dp(8));
            r.addView(km, kp);
        }

        String id = x.k.optString("id");
        r.setOnClickListener(v -> zvol(id));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(dp(6), 0, dp(6), 0);
        r.setLayoutParams(rp);
        return r;
    }

    private static int barva(String s) {
        try {
            return Color.parseColor(s);
        } catch (RuntimeException e) {
            return 0xFF5A606B;
        }
    }

    private static boolean tmava(int c) {
        return 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c) < 60;
    }

    private void zvol(String id) {
        Context app = getApplicationContext();
        Vyber.zvol(app, id, poloha != null ? poloha : Hlidka.posledni(app));
        Widget.obnovPozdeji(app);
        konec();
    }

    /** Čerstvá poloha jako v Hlidka, nejvýš 15 s; bez oprávnění k poloze zůstane seznam podle té, kterou telefon zná. */
    private void zjistiPolohu() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationManager lm = getSystemService(LocationManager.class);
        String prov = null;
        try {
            if (Build.VERSION.SDK_INT >= 31 && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) prov = LocationManager.FUSED_PROVIDER;
            else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) prov = LocationManager.NETWORK_PROVIDER;
        } catch (IllegalArgumentException e) {
            prov = null;
        }
        if (prov == null) return;
        h.postDelayed(() -> mamPolohu(null), 15_000);
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                zruseni = new CancellationSignal();
                LocationRequest rq = new LocationRequest.Builder(0)
                        .setQuality(LocationRequest.QUALITY_BALANCED_POWER_ACCURACY).setDurationMillis(15_000).build();
                lm.getCurrentLocation(prov, rq, zruseni, getMainExecutor(), this::mamPolohu);
            } else if (Build.VERSION.SDK_INT >= 30) {
                zruseni = new CancellationSignal();
                lm.getCurrentLocation(prov, zruseni, getMainExecutor(), this::mamPolohu);
            } else {
                posluchac = new LocationListener() {
                    @Override public void onLocationChanged(Location l) { mamPolohu(l); }
                    @Override public void onStatusChanged(String a, int b, Bundle c) {}
                    @Override public void onProviderEnabled(String a) {}
                    @Override public void onProviderDisabled(String a) {}
                };
                lm.requestSingleUpdate(prov, posluchac, Looper.getMainLooper());
            }
        } catch (SecurityException | IllegalArgumentException e) {
            mamPolohu(null);
        }
    }

    private void mamPolohu(Location l) {
        uklid();
        if (l == null || isFinishing()) return;
        poloha = l;
        nactiSeznam();
    }

    private void uklid() {
        h.removeCallbacksAndMessages(null);
        if (zruseni != null) { zruseni.cancel(); zruseni = null; }
        if (posluchac != null) {
            getSystemService(LocationManager.class).removeUpdates(posluchac);
            posluchac = null;
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) dotknuto = true;
        return super.dispatchTouchEvent(e);
    }

    @Override
    public void onBackPressed() { konec(); }

    /** Odchod na jinou appku nebo zhasnutí displeje: seznam se zavře. */
    @Override
    protected void onStop() {
        super.onStop();
        konec();
    }

    @Override
    protected void onDestroy() {
        uklid();
        super.onDestroy();
    }

    private void konec() {
        if (isFinishing()) return;
        finishAndRemoveTask();
        overridePendingTransition(0, 0);
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    /**
     * Celá obrazovka; seznam pod řádkem s názvem (zarovnaný s jeho levým krajem), když se tam nevejde, nad ním,
     * jinak tam, kde je víc místa, a zbytek se posouvá. Bez řádku od launcheru uprostřed.
     */
    private final class Okno extends ViewGroup {
        Okno(Context c) { super(c); }

        @Override
        protected void onMeasure(int ws, int hs) {
            int w = MeasureSpec.getSize(ws), v = MeasureSpec.getSize(hs);
            setMeasuredDimension(w, v);
            int sirka = Math.min(w - 2 * dp(8), Math.max(dp(260), kotva == null ? 0 : kotva.width()));
            int vyska = Math.min(v - 2 * dp(8), dp(MAX_VYSKA));
            panel.measure(MeasureSpec.makeMeasureSpec(Math.max(0, sirka), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.max(0, vyska), MeasureSpec.AT_MOST));
        }

        @Override
        protected void onLayout(boolean zmena, int l, int t, int r, int b) {
            int w = r - l, v = b - t, okraj = dp(8), mezera = dp(4);
            int pw = panel.getMeasuredWidth(), ph = panel.getMeasuredHeight();
            int x = (w - pw) / 2, y = (v - ph) / 2;
            if (kotva != null) {
                int[] o = new int[2];
                getLocationOnScreen(o);
                Rect a = new Rect(kotva);
                a.offset(-o[0], -o[1]);
                int dole = v - okraj - a.bottom - mezera, nahore = a.top - mezera - okraj;
                if (ph > dole && ph > nahore) {
                    int max = Math.max(Math.max(dole, nahore), dp(144));
                    panel.measure(MeasureSpec.makeMeasureSpec(pw, MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST));
                    ph = panel.getMeasuredHeight();
                }
                y = ph <= dole || dole >= nahore ? a.bottom + mezera : a.top - mezera - ph;
                y = Math.max(okraj, Math.min(y, v - okraj - ph));
                x = Math.max(okraj, Math.min(a.left, w - okraj - pw));
            }
            panel.layout(x, y, x + pw, y + ph);
        }
    }
}
