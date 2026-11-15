package cz.adamhorak.karticka;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Hlavní okno: rozhraní je stránka v assets/www, s telefonem mluví přes most „Karticka". */
public class Hlavni extends Activity {
    static final String HOST = "appassets.androidplatform.net";
    private static final int SKEN = 1, FOTKA = 2, ZALOHA = 3, OBNOVA = 4, FOTKY = 5, OVERENI = 6, POLOHA = 10, POLOHA_VZDY = 11;

    private WebView web;
    private LocationManager lm;
    private WifiManager wm;
    private BroadcastReceiver wifiHotovo;
    private CancellationSignal zruseni, zruseniHruba;
    private LocationListener posluchac;
    private LocationListener zive; // tempo Každou sekundu: poloha podle času, dokud je appka na obrazovce
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean zadanoOPolohu;
    private String startKarta;
    // rychlé vstupy: zkratka po podržení ikony (pridat, mapa, hledat) nebo obrázky nasdílené z jiné appky (sdileno)
    private String startAkce;
    private List<Uri> sdilene;
    private BroadcastReceiver odemceno;
    // heslo k účtu: kdy končí platnost ověření pro kopírování, heslo čekající na zámek obrazovky, štítek naší položky ve schránce
    private volatile long overenoDo;
    private String cekajiciHeslo;
    private static final String STITEK_HESLA = "karticka-heslo-";
    private volatile String stitekSchranky;
    private CancellationSignal zruseniOvereni;
    private final Runnable skryjCasovac = () -> skryjHeslo(true);

    private final Runnable casovac = () -> mamPolohu(null);
    // haptický podnět: zatřesení (tam, zpátky, tam, pozná ho Treseni) otevře podnět a telefon krátce zavrní
    private SensorManager senzory;
    private final Treseni rozpoznani = new Treseni();
    private boolean popredi; // senzor jen při otevřeném okně (mezi onResume a onPause)
    private final SensorEventListener tres = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent e) {
            if (!rozpoznani.vzorek(e.timestamp / 1_000_000L, e.values[0], e.values[1], e.values[2])) return;
            runOnUiThread(() -> {
                if (web == null) return;
                // vrní, jen když se podnět opravdu otevřel (přes kód u pokladny, kartu ani dialog ne)
                web.evaluateJavascript("!!(window.naZatreseni&&naZatreseni())", r -> {
                    if ("true".equals(r) && web != null) web.performHapticFeedback(Build.VERSION.SDK_INT >= 30
                            ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.LONG_PRESS);
                });
            });
        }
        @Override public void onAccuracyChanged(Sensor s, int a) { }
    };
    /** Běžící hlavní okno pro Vzhled widgetu (nastavZvenku); slabý odkaz, ať zavřené okno nedrží v paměti. */
    private static WeakReference<Hlavni> bezici = new WeakReference<>(null);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        startKarta = getIntent().getStringExtra("karta");
        startAkce = akceZ(getIntent());
        nastavZamek();
        // po odemknutí telefonu (appka mohla být vidět přes zámek) se stránce vrátí všechno, co zamčená schovává
        odemceno = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                js(Intent.ACTION_SCREEN_OFF.equals(i.getAction()) ? "window.naZamek&&naZamek(true)" : "window.naZamek&&naZamek()");
            }
        };
        IntentFilter zamek = new IntentFilter(Intent.ACTION_USER_PRESENT);
        zamek.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(odemceno, zamek, RECEIVER_NOT_EXPORTED);
        else registerReceiver(odemceno, zamek);

        web = new WebView(this);
        web.setBackgroundColor(0xFFF4F5F7);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setTextZoom(100);
        // Stránka je jen z assets (Klient), na síť nesmí: internet má appka jen kvůli kontrole verze (Verze.java),
        // sběru se souhlasem (Sber.java) a dlaždicím otevřené mapy okolí (Dlazdice.java, Klient je podá jako /dlazdice/).
        s.setBlockNetworkLoads(true);
        s.setSafeBrowsingEnabled(false);
        web.setWebViewClient(new Klient());
        web.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS); // Samsung Pass a Google heslo nenabídnou uložit
        web.addJavascriptInterface(new Most(), "Karticka");
        setContentView(web);
        web.loadUrl("https://" + HOST + "/index.html");
        // bezici až po načtení stránky (Klient.onPageFinished): dřív by volání do JS potichu propadla
    }

    @Override
    protected void onDestroy() {
        if (bezici.get() == this) bezici.clear();
        if (odemceno != null) {
            try { unregisterReceiver(odemceno); } catch (RuntimeException e) { /* nebyl zaregistrovaný */ }
        }
        super.onDestroy();
    }

    /**
     * Volba appky změněná v nativním okně (Klepnutí na widget ve Vzhledu widgetu). Data karet drží běžící appka
     * v paměti a při každé změně je uloží celá, proto jí volbu předá, ať si ji dalším uložením nepřepíše.
     * Vrátí false, když appka neběží: pak ji Vzhled zapíše rovnou do dat.
     */
    static boolean nastavZvenku(String klic, String hodnota) {
        Hlavni a = bezici.get();
        if (a == null || a.isFinishing() || a.isDestroyed() || a.web == null) return false;
        a.js("window.nastavZvenku&&nastavZvenku(" + JSONObject.quote(klic) + "," + JSONObject.quote(hodnota) + ")");
        return true;
    }

    /** „Jsem v prodejně" z widgetu: stejně jako nastavZvenku předá místo běžící appce. False, když neběží. */
    static boolean potvrdZvenku(String id, double lat, double lon) {
        Hlavni a = bezici.get();
        if (a == null || a.isFinishing() || a.isDestroyed() || a.web == null) return false;
        a.js("window.potvrdZvenku&&potvrdZvenku(" + JSONObject.quote(id) + "," + lat + "," + lon + ")");
        return true;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        String id = i.getStringExtra("karta");
        if (id != null) js("window.naOtevreni&&naOtevreni(" + JSONObject.quote(id) + ")");
        String a = akceZ(i);
        if (a != null) js("window.naAkci&&naAkci(" + JSONObject.quote(a) + ")");
    }

    /** Rychlý vstup z intentu: zkratka nese „akce", nasdílené obrázky se odloží do sdilene a vrátí se „sdileno". */
    private String akceZ(Intent i) {
        if (i == null) return null;
        String a = i.getAction();
        if (Intent.ACTION_SEND.equals(a) || Intent.ACTION_SEND_MULTIPLE.equals(a)) {
            List<Uri> uri = new ArrayList<>();
            try {
                if (Intent.ACTION_SEND.equals(a)) {
                    Uri u = i.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (u != null) uri.add(u);
                } else {
                    ArrayList<Uri> vse = i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                    if (vse != null) uri.addAll(vse);
                }
            } catch (RuntimeException e) {
                uri.clear(); // cizí appka poslala něco, co nejde rozbalit
            }
            List<Uri> jen = new ArrayList<>();
            for (Uri u : uri) if (u != null && "content".equals(u.getScheme()) && jen.size() < 40) jen.add(u); // jen obsah, který appka sama nabídla
            if (jen.isEmpty()) return null;
            sdilene = jen;
            return "sdileno";
        }
        String akce = i.getStringExtra("akce");
        return "pridat".equals(akce) || "mapa".equals(akce) || "hledat".equals(akce) ? akce : null;
    }

    /**
     * Karty i přes zamčenou obrazovku (Adam 9.11.2026): appka se smí ukázat nad zámkem, stránka pak ukazuje jen
     * karty a kódy (window.naZamek, třída zamceno). Jde vypnout v Nastavení.
     */
    private void nastavZamek() {
        boolean ano = getSharedPreferences(Data.PREFS, 0).getBoolean("naZamku", true);
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(ano);
        else if (ano) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
    }

    private boolean zamceny() {
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return km != null && km.isKeyguardLocked();
    }

    /** Tmavý režim stránky: stejná barva pod stránkou a ve stavovém i navigačním řádku, ikony řádků světlé. */
    private void nastavTmu(boolean ano) {
        int b = ano ? 0xFF14161A : 0xFFF4F5F7;
        if (web != null) web.setBackgroundColor(b);
        getWindow().setStatusBarColor(b);
        getWindow().setNavigationBarColor(b);
        View d = getWindow().getDecorView();
        d.setBackgroundColor(b);
        int svetle = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        int f = d.getSystemUiVisibility();
        d.setSystemUiVisibility(ano ? f & ~svetle : f | svetle);
        if (android.os.Build.VERSION.SDK_INT >= 30) { // novější systém se řídí tímhle, starý příznak sám nestačí
            android.view.WindowInsetsController k = getWindow().getInsetsController();
            int s = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            if (k != null) k.setSystemBarsAppearance(ano ? 0 : s, s);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Hlidka.naplanuj(this); // oprávnění nebo widget se mohly změnit v nastavení / na ploše
        Sledovani.spust(this); // widget naživo: službu smí spustit jen otevřená appka
        Sber.dorucFrontu(this); // podněty napsané bez internetu
        popredi = true;
        poslouchejTreseni(true);
        hlidejZive();
        Instalace.posluchac = instalace;
        Intent potvrzeni = Instalace.vezmiCekajici(); // stažená aktualizace čekala, až bude appka vidět
        if (potvrzeni != null) potvrdInstalaci(potvrzeni);
        // bez polohy „Povolit vždy" kreslí widget jinak, překreslí se mimo hlavní vlákno, ať appka naskočí hned
        Context app = getApplicationContext();
        new Thread(() -> {
            try {
                Widget.obnov(app);
            } catch (RuntimeException e) {
                // widget zůstane, jak byl
            }
        }).start();
        poslouchejWifi(true);
        hledejWifi();
        js("window.naNavrat&&naNavrat()");
        Verze.zkontroluj(this, false, ok -> { if (!isDestroyed()) js("window.naNovouVerzi&&naNovouVerzi()"); });
    }

    /** Průběh aktualizace do stránky; potvrzení instalace otevře jen viditelná appka (jinak počká na onResume). */
    private final Instalace.Posluchac instalace = new Instalace.Posluchac() {
        @Override public void stav(int procenta, String chyba) {
            js("window.naInstalaci&&naInstalaci(" + procenta + "," + q(chyba) + ")");
        }

        @Override public boolean potvrdit(Intent potvrzeni) {
            if (!popredi) return false;
            runOnUiThread(() -> potvrdInstalaci(potvrzeni));
            return true;
        }
    };

    @Override
    protected void onPause() {
        zrusPolohu();
        popredi = false;
        if (Instalace.posluchac == instalace) Instalace.posluchac = null;
        hlidejZive();
        poslouchejTreseni(false);
        poslouchejWifi(false);
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean maFokus) {
        super.onWindowFocusChanged(maFokus);
        if (maFokus) docistiSchranku(); // schránku smí číst jen appka s fokusem
    }

    @Override
    protected void onStop() {
        skryjHeslo(true); // heslo nezůstane odkryté po odchodu z appky
        Zaloha.pozdeji(this, false); // druhá kopie karet do Stažené/Karticka, když se něco změnilo
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.zpetNativni ? zpetNativni() : false", v -> {
            if (!"true".equals(v)) finish();
        });
    }

    private void js(String kod) {
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(kod, null); });
    }

    private static String q(String s) { return s == null ? "null" : JSONObject.quote(s); }

    private boolean treseni() { return getSharedPreferences(Data.PREFS, 0).getBoolean("zatreseni", true); }

    private void poslouchejTreseni(boolean ano) {
        if (senzory == null) senzory = getSystemService(SensorManager.class);
        if (senzory == null) return;
        senzory.unregisterListener(tres);
        Sensor a = senzory.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        // GAME = asi 50 vzorků za vteřinu, UI (15 za vteřinu) vrcholy zatřesení přeskakovalo
        if (ano && a != null && treseni()) senzory.registerListener(tres, a, SensorManager.SENSOR_DELAY_GAME);
    }

    // ---------- poloha ----------

    private boolean maPolohu() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean polohaZapnuta() {
        if (Build.VERSION.SDK_INT >= 28) return lm.isLocationEnabled();
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private static String json(Location l) {
        long vek = (SystemClock.elapsedRealtimeNanos() - l.getElapsedRealtimeNanos()) / 1_000_000L;
        return "{\"lat\":" + l.getLatitude() + ",\"lon\":" + l.getLongitude()
                + ",\"presnost\":" + (l.hasAccuracy() ? l.getAccuracy() : 999) + ",\"vek\":" + vek + "}";
    }

    private Location posledni() {
        Location nej = null;
        if (!maPolohu()) return null;
        for (String p : lm.getProviders(true)) {
            try {
                Location l = lm.getLastKnownLocation(p);
                if (l != null && (nej == null || l.getElapsedRealtimeNanos() > nej.getElapsedRealtimeNanos())) nej = l;
            } catch (SecurityException | IllegalArgumentException e) {
                // poskytovatel zmizel
            }
        }
        return nej;
    }

    private void zjistiPolohu() {
        if (!maPolohu()) {
            if (!zadanoOPolohu) {
                zadanoOPolohu = true;
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, POLOHA);
            } else {
                js("window.naPolohu&&naPolohu(null,'zakazano')");
            }
            return;
        }
        if (!polohaZapnuta()) { js("window.naPolohu&&naPolohu(null,'vypnuto')"); return; }
        hledejWifi();
        if (zruseni != null || posluchac != null) return;
        try {
            // fused jen když je zapnutý: bez Google služeb existuje, ale hned vrací prázdnou polohu
            String p;
            if (Build.VERSION.SDK_INT >= 31 && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) p = LocationManager.FUSED_PROVIDER;
            else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) p = LocationManager.NETWORK_PROVIDER;
            else p = LocationManager.GPS_PROVIDER;
            h.postDelayed(casovac, 15000);
            if (Build.VERSION.SDK_INT >= 31) {
                zruseni = new CancellationSignal();
                LocationRequest rq = new LocationRequest.Builder(0)
                        .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY).setDurationMillis(15000).build();
                lm.getCurrentLocation(p, rq, zruseni, getMainExecutor(), this::mamPolohu);
                // hrubá poloha ze sítě bývá za chvilku, přesná až za pár sekund: appka podle hrubé hned ukáže kartu
                // a přesná ji pak jen potvrdí nebo upřesní
                if (!LocationManager.NETWORK_PROVIDER.equals(p) && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    zruseniHruba = new CancellationSignal();
                    lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, new LocationRequest.Builder(0)
                                    .setQuality(LocationRequest.QUALITY_BALANCED_POWER_ACCURACY).setDurationMillis(8000).build(),
                            zruseniHruba, getMainExecutor(), this::hrubaPoloha);
                }
            } else if (Build.VERSION.SDK_INT >= 30) {
                zruseni = new CancellationSignal();
                lm.getCurrentLocation(p, zruseni, getMainExecutor(), this::mamPolohu);
            } else {
                posluchac = new LocationListener() {
                    @Override public void onLocationChanged(Location l) { mamPolohu(l); }
                    @Override public void onStatusChanged(String a, int b, Bundle c) {}
                    @Override public void onProviderEnabled(String a) {}
                    @Override public void onProviderDisabled(String a) {}
                };
                lm.requestSingleUpdate(p, posluchac, Looper.getMainLooper());
            }
        } catch (SecurityException | IllegalArgumentException e) {
            mamPolohu(null);
        }
    }

    private void mamPolohu(Location l) {
        h.removeCallbacks(casovac);
        zrusPolohu();
        js("window.naPolohu&&naPolohu(" + (l == null ? "null,'nic'" : json(l)) + ")");
        if (l != null) {
            // widget se podle polohy srovná i bez „Povolit vždy": daleko od obchodů pustí kartu obchodu jinde
            Context app = getApplicationContext();
            new Thread(() -> {
                try {
                    Hlidka.prepocti(app, l);
                } catch (RuntimeException e) {
                    // widget zůstane u předchozí karty
                }
            }).start();
        }
    }

    private void hrubaPoloha(Location l) {
        zruseniHruba = null;
        // jen dokud se čeká na přesnou, potom by hrubá výsledek zhoršila
        if (l != null && zruseni != null) js("window.naPolohu&&naPolohu(" + json(l) + ",'hruba')");
    }

    /**
     * Tempo Každou sekundu: dokud je appka na obrazovce, bere polohu podle času každou sekundu (i když se člověk
     * nehýbe) a posílá ji stránce, ať karta nahoře přepne hned ve dveřích obchodu. Jinak appka bere polohu jen
     * po otevření a pár dalších pokusů. Volá se při každé změně: na obrazovce, pryč, nové tempo, povolená poloha.
     */
    private void hlidejZive() {
        boolean ano = popredi && Sledovani.kazdouSekundu(this) && maPolohu() && polohaZapnuta();
        if (ano == (zive != null)) return;
        if (!ano) {
            try { lm.removeUpdates(zive); } catch (RuntimeException e) { /* už neposlouchá */ }
            zive = null;
            return;
        }
        zive = new LocationListener() {
            @Override public void onLocationChanged(Location l) {
                if (l != null && zive == this) js("window.naPolohu&&naPolohu(" + json(l) + ")");
            }
            @Override public void onStatusChanged(String a, int b, Bundle c) {}
            @Override public void onProviderEnabled(String a) {}
            @Override public void onProviderDisabled(String a) {}
        };
        try {
            if (Build.VERSION.SDK_INT >= 31 && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.FUSED_PROVIDER, new LocationRequest.Builder(Sledovani.SEKUNDA)
                        .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY).setMinUpdateIntervalMillis(Sledovani.SEKUNDA)
                        .build(), getMainExecutor(), zive);
            } else {
                String p = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER
                        : LocationManager.NETWORK_PROVIDER;
                lm.requestLocationUpdates(p, Sledovani.SEKUNDA, 0f, zive, Looper.getMainLooper());
            }
        } catch (SecurityException | IllegalArgumentException e) {
            zive = null;
        }
    }

    private void zrusPolohu() {
        h.removeCallbacks(casovac);
        if (zruseniHruba != null) { zruseniHruba.cancel(); zruseniHruba = null; }
        if (zruseni != null) { zruseni.cancel(); zruseni = null; }
        if (posluchac != null) { lm.removeUpdates(posluchac); posluchac = null; }
    }

    // ---------- Wi-Fi kolem: které patro / který obchod v obchoďáku ----------
    // GPS uvnitř nerozliší patra. Okolní Wi-Fi sítě jsou ale na každém patře jiné a jinak silné.
    // Appka je jen vidí (jako seznam sítí v nastavení), nepřipojuje se k nim a nic neposílá.

    private boolean smiWifi() {
        return wm != null && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void hledejWifi() {
        if (!smiWifi()) return;
        try {
            wm.startScan(); // systém dovolí 4 hledání za 2 minuty, jinak nechá poslední výsledky
        } catch (SecurityException e) {
            // zůstane jen poloha
        }
    }

    private void poslouchejWifi(boolean ano) {
        if (ano && wifiHotovo == null && wm != null) {
            wifiHotovo = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) { js("window.naWifi&&naWifi()"); }
            };
            IntentFilter f = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(wifiHotovo, f, RECEIVER_NOT_EXPORTED);
            else registerReceiver(wifiHotovo, f);
        } else if (!ano && wifiHotovo != null) {
            unregisterReceiver(wifiHotovo);
            wifiHotovo = null;
        }
    }

    /** Sítě viděné za poslední 2 minuty, nejsilnější první: [{"b": BSSID, "s": dBm}]. */
    private String wifiKolem() {
        JSONArray a = new JSONArray();
        if (!smiWifi()) return "[]";
        try {
            List<ScanResult> r = new ArrayList<>(wm.getScanResults());
            Collections.sort(r, (x, y) -> y.level - x.level);
            long ted = SystemClock.elapsedRealtime() * 1000; // ScanResult.timestamp je v µs od startu
            for (ScanResult s : r) {
                if (s.BSSID == null || ted - s.timestamp > 120_000_000L) continue;
                JSONObject o = new JSONObject();
                o.put("b", s.BSSID);
                o.put("s", s.level);
                a.put(o);
                if (a.length() >= 30) break;
            }
        } catch (SecurityException | JSONException e) {
            return "[]";
        }
        return a.toString();
    }

    @Override
    public void onRequestPermissionsResult(int kod, String[] p, int[] v) {
        if (kod == POLOHA_VZDY) {
            Hlidka.naplanuj(this);
            // systém žádost hned zamítl bez okna (už jednou odmítnuto): rovnou nastavení appky
            if (!Hlidka.smi(this) && SystemClock.elapsedRealtime() - zadanoVzdy < 600) nastaveniAppky();
            return;
        }
        if (kod != POLOHA) return;
        Hlidka.naplanuj(this);
        boolean vzdy = chceVzdy;
        chceVzdy = false;
        if (vzdy && maPolohu()) { povolitVzdy(); return; } // po polohě při používání rovnou krok „Povolit vždy"
        if (maPolohu()) { zjistiPolohu(); hlidejZive(); }
        else {
            js("window.naPolohu&&naPolohu(null,'zakazano')");
            // poloha trvale zakázaná, systém žádost zamítl bez okna: „Povolit vždy" jde jen v nastavení appky
            if (vzdy && SystemClock.elapsedRealtime() - zadanoVzdy < 600) nastaveniAppky();
        }
    }

    private long zadanoVzdy;
    private boolean chceVzdy;

    /** Poloha „Povolit vždy", aby widget sám přepínal kartu podle obchodu. Povoluje ji Adam sám v okně systému. */
    private void povolitVzdy() {
        if (Hlidka.smi(this)) { nastaveniAppky(); return; }
        if (!maPolohu()) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) || !zadanoOPolohu) {
                zadanoOPolohu = true;
                chceVzdy = true;
                zadanoVzdy = SystemClock.elapsedRealtime();
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}, POLOHA);
            } else {
                nastaveniAppky();
            }
            return;
        }
        if (Build.VERSION.SDK_INT >= 29) {
            zadanoVzdy = SystemClock.elapsedRealtime();
            requestPermissions(new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, POLOHA_VZDY);
        } else {
            nastaveniAppky();
        }
    }

    // ---------- výsledky jiných oken ----------

    @Override
    protected void onActivityResult(int kod, int vysledek, Intent d) {
        super.onActivityResult(kod, vysledek, d);
        if (kod == SKEN) {
            if (vysledek == RESULT_OK && d != null) {
                js("window.naKod&&naKod(" + q(d.getStringExtra("format")) + "," + q(d.getStringExtra("text")) + ")");
            } else {
                String ch = d == null ? null : d.getStringExtra("chyba");
                js("window.naKod&&naKod(null,null," + q(ch == null ? "zruseno" : ch) + ")");
            }
        } else if (kod == FOTKA) {
            Uri u = vysledek == RESULT_OK && d != null ? d.getData() : null;
            if (u == null) { js("window.naKod&&naKod(null,null,'zruseno')"); return; }
            new Thread(() -> {
                Result r = null;
                try {
                    Bitmap bmp = nactiFotku(u);
                    if (bmp != null) r = Kod.zFotky(bmp);
                } catch (Exception | OutOfMemoryError e) {
                    r = null;
                }
                if (r == null) js("window.naKod&&naKod(null,null,'nenalezeno')");
                else js("window.naKod&&naKod(" + q(r.getBarcodeFormat().name()) + "," + q(r.getText()) + ")");
            }).start();
        } else if (kod == FOTKY) {
            List<Uri> uri = new ArrayList<>();
            if (vysledek == RESULT_OK && d != null) {
                ClipData c = d.getClipData();
                if (c != null) for (int i = 0; i < c.getItemCount(); i++) { Uri u = c.getItemAt(i).getUri(); if (u != null) uri.add(u); }
                else if (d.getData() != null) uri.add(d.getData());
            }
            if (uri.isEmpty()) { js("window.naFotky&&naFotky(null)"); return; }
            zpracujFotky(uri);
        } else if (kod == ZALOHA) {
            Uri u = vysledek == RESULT_OK && d != null ? d.getData() : null;
            if (u == null) return;
            new Thread(() -> {
                int[] bezHesel = {0};
                boolean ok = false;
                String t = zalohaTextu(this, bezHesel); // napřed text, ať se kvůli chybě nevyprázdní soubor, který člověk přepisuje
                if (t != null) {
                    try (OutputStream o = getContentResolver().openOutputStream(u, "wt")) {
                        if (o != null) { o.write(t.getBytes(StandardCharsets.UTF_8)); ok = true; }
                    } catch (IOException | RuntimeException e) {
                        ok = false;
                    }
                }
                js("window.naZalohu&&naZalohu(" + ok + "," + bezHesel[0] + ")");
            }).start();
        } else if (kod == OVERENI) {
            String ks = cekajiciHeslo;
            cekajiciHeslo = null;
            if (ks == null) return;
            if (vysledek == RESULT_OK) odkryjHeslo(ks, false);
            else js("window.naHeslo&&naHeslo(" + q(ks) + ",'')");
        } else if (kod == OBNOVA) {
            Uri u = vysledek == RESULT_OK && d != null ? d.getData() : null;
            if (u == null) return;
            String t = null;
            try (InputStream in = getContentResolver().openInputStream(u)) {
                if (in != null) t = Data.textSouboru(in, 4_000_000);
            } catch (IOException | SecurityException e) {
                t = null;
            }
            js("window.naObnovu&&naObnovu(" + q(t) + ")");
        }
    }

    /**
     * Text zálohy: karty bez hesel k účtům. S heslem zálohy jdou hesla zvlášť v bloku "hesla" (zamčená tím heslem),
     * bez něj do zálohy nejdou vůbec. V bezHesel[0] je počet karet, jejichž heslo v záloze není. Null = data nejdou přečíst.
     */
    static String zalohaTextu(Context c, int[] bezHesel) {
        String t = Data.nacti(c);
        if (t.isEmpty()) return null; // není co zapsat: starý soubor zálohy zůstane, appka ohlásí chybu
        char[] hz = Trezor.hesloZalohy(c);
        try {
            JSONObject d = new JSONObject(t);
            JSONArray karty = d.optJSONArray("karty");
            Map<String, String> hesla = new HashMap<>();
            if (karty != null) {
                for (int i = 0; i < karty.length(); i++) {
                    JSONObject k = karty.optJSONObject(i);
                    if (k == null) continue;
                    String id = k.optString("id");
                    // heslo k účtu pod id karty, PIN karty pod „id#pin" (starší verze appky takový klíč přeskočí)
                    for (String pole : new String[]{"heslo", "pin"}) {
                        if (!k.has(pole)) continue;
                        String ks = k.optString(pole);
                        k.remove(pole);
                        if (ks.isEmpty()) continue;
                        String otevrene = hz == null ? null : Trezor.odemkni(ks);
                        if (otevrene == null || id.isEmpty()) bezHesel[0]++;
                        else hesla.put("heslo".equals(pole) ? id : id + "#pin", otevrene);
                    }
                }
            }
            if (!hesla.isEmpty()) {
                JSONObject blok = Trezor.zalohaZamkni(hesla, hz);
                if (blok != null) d.put("hesla", blok);
                else bezHesel[0] += hesla.size();
            }
            return d.toString();
        } catch (JSONException e) {
            return null;
        } finally {
            if (hz != null) Arrays.fill(hz, '\0');
        }
    }

    // ---------- heslo k účtu u karty ----------

    /** Odemkne heslo až po ověření zámkem obrazovky nebo otiskem. Bez zámku obrazovky ho ukáže hned (stránka to připomene). */
    private void overUzivatele(String ks) {
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km == null || !km.isDeviceSecure()) { odkryjHeslo(ks, true); return; }
        if (Build.VERSION.SDK_INT >= 29) {
            BiometricPrompt.Builder b = new BiometricPrompt.Builder(this)
                    .setTitle(getString(R.string.ukaz_heslo))
                    .setSubtitle(getString(R.string.ukaz_heslo_popis));
            if (Build.VERSION.SDK_INT >= 30) {
                b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            } else {
                b.setDeviceCredentialAllowed(true);
            }
            zruseniOvereni = new CancellationSignal();
            b.build().authenticate(zruseniOvereni, getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) { odkryjHeslo(ks, false); }

                @Override
                public void onAuthenticationError(int kod, CharSequence zprava) {
                    js("window.naHeslo&&naHeslo(" + q(ks) + ",'')");
                }
            });
        } else {
            // BiometricPrompt umí zámek obrazovky až od Androidu 10, starší telefony ho vyžádají přes systémové okno
            Intent i = km.createConfirmDeviceCredentialIntent(getString(R.string.ukaz_heslo), getString(R.string.ukaz_heslo_popis));
            if (i == null) { odkryjHeslo(ks, true); return; }
            cekajiciHeslo = ks;
            spust(i, OVERENI);
        }
    }

    private void odkryjHeslo(String ks, boolean bezZamku) {
        String text = Trezor.odemkni(ks);
        if (text == null) { js("window.naHeslo&&naHeslo(" + q(ks) + ",null)"); return; }
        overenoDo = SystemClock.elapsedRealtime() + 30_000;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); // žádný snímek obrazovky ani náhled mezi appkami
        h.removeCallbacks(skryjCasovac);
        h.postDelayed(skryjCasovac, 30_000);
        js("window.naHeslo&&naHeslo(" + q(ks) + "," + JSONObject.quote(text) + "," + bezZamku + ")");
    }

    private void skryjHeslo(boolean oznamit) {
        h.removeCallbacks(skryjCasovac);
        overenoDo = 0;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        if (oznamit) js("window.naSkryti&&naSkryti()");
    }

    /** Text do schránky s příznakem citlivého obsahu (Android 13+ pak neukáže náhled). Vrátí false, když to nejde. */
    private boolean dejDoSchranky(String text, String predpona) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null) return false;
            String stitek = predpona + SystemClock.elapsedRealtime();
            ClipData cd = ClipData.newPlainText(stitek, text);
            PersistableBundle pb = new PersistableBundle();
            pb.putBoolean(Build.VERSION.SDK_INT >= 33 ? ClipDescription.EXTRA_IS_SENSITIVE
                    : "android.content.extra.IS_SENSITIVE", true);
            cd.getDescription().setExtras(pb);
            cm.setPrimaryClip(cd);
            stitekSchranky = stitek;
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Proces mohl zaniknout dřív, než minuta uběhla, a časovač s ním: po návratu do appky se heslo ze schránky dočistí. */
    private void docistiSchranku() {
        if (stitekSchranky != null) return;
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            ClipDescription popis = cm == null ? null : cm.getPrimaryClipDescription();
            if (popis == null || popis.getLabel() == null) return;
            String stitek = popis.getLabel().toString();
            if (!stitek.startsWith(STITEK_HESLA)) return;
            long zbyva = Long.parseLong(stitek.substring(STITEK_HESLA.length())) + 60_000 - SystemClock.elapsedRealtime();
            stitekSchranky = stitek;
            h.postDelayed(() -> vycistiSchranku(stitek), Math.max(0, Math.min(60_000, zbyva)));
        } catch (RuntimeException e) {
            // schránka je nedostupná, nechá se být
        }
    }

    /** Za minutu schránku vyčistí, ale jen když je v ní pořád to, co jsme tam dali. */
    private void vycistiSchranku(String stitek) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null || !stitek.equals(stitekSchranky)) return;
            ClipDescription popis = cm.getPrimaryClipDescription();
            if (popis == null || !stitek.equals(String.valueOf(popis.getLabel()))) return;
            if (Build.VERSION.SDK_INT >= 28) cm.clearPrimaryClip();
            else cm.setPrimaryClip(ClipData.newPlainText("", ""));
            stitekSchranky = null;
        } catch (RuntimeException e) {
            // schránka je nedostupná, nechá se být
        }
    }

    /** Malý náhled fotky do přehledu nových karet, ať člověk pozná, čí karta to je. */
    private static String nahled(Bitmap b) {
        float k = Math.min(1f, 480f / Math.max(b.getWidth(), b.getHeight()));
        Bitmap n = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * k)), Math.max(1, Math.round(b.getHeight() * k)), true);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        n.compress(Bitmap.CompressFormat.JPEG, 70, o);
        if (n != b) n.recycle();
        return "data:image/jpeg;base64," + Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
    }

    private void zpracujFotky(List<Uri> uri) {
        new Thread(() -> {
            JSONArray vse = new JSONArray();
            for (int i = 0; i < uri.size(); i++) {
                js("window.naFotkyPrubeh&&naFotkyPrubeh(" + (i + 1) + "," + uri.size() + ")");
                JSONArray jedna = new JSONArray();
                try {
                    Bitmap bmp = nactiFotku(uri.get(i));
                    if (bmp != null) {
                        Result r = Kod.zFotky(bmp);
                        // Fotka bez čitelného kódu má jen náhled, číslo se z ní opíše v přehledu nových karet.
                        if (r != null) jedna.put(r.getBarcodeFormat().name()).put(r.getText()).put(nahled(bmp));
                        else jedna.put("").put("").put(nahled(bmp));
                        bmp.recycle();
                    }
                } catch (Exception | OutOfMemoryError e) {
                    jedna = new JSONArray();
                }
                vse.put(jedna);
            }
            js("window.naFotky&&naFotky(" + q(vse.toString()) + ")");
        }).start();
    }

    private Bitmap nactiFotku(Uri u) throws IOException {
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), u);
            return ImageDecoder.decodeBitmap(src, (dec, info, s) -> {
                dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                int w = info.getSize().getWidth(), hh = info.getSize().getHeight(), m = Math.max(w, hh);
                if (m > 3000) { float k = 3000f / m; dec.setTargetSize(Math.round(w * k), Math.round(hh * k)); }
            });
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, o); }
        int vz = 1;
        while (Math.max(o.outWidth, o.outHeight) / vz > 3000) vz *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = vz;
        try (InputStream in = getContentResolver().openInputStream(u)) { return BitmapFactory.decodeStream(in, null, o2); }
    }

    /**
     * Appka obchodu (Body a kupóny, karta v appce, widget): s odkazem z vernost.json rovnou obrazovka s kódem
     * (McDonald's: Odměny), jinak úvodní obrazovka appky. null, když appka v telefonu není.
     */
    static Intent appkaObchodu(Context c, String balik) {
        if (balik == null || balik.isEmpty()) return null;
        PackageManager pm = c.getPackageManager();
        String odkaz = "";
        try (InputStream in = c.getAssets().open("www/vernost.json")) {
            JSONObject v = new JSONObject(Data.precti(in, 1_000_000));
            for (Iterator<String> it = v.keys(); it.hasNext() && odkaz.isEmpty(); ) {
                JSONObject p = v.optJSONObject(it.next());
                if (p != null && balik.equals(p.optString("app"))) odkaz = p.optString("odkaz");
            }
        } catch (IOException | JSONException e) {
            // bez seznamu programů se otevře úvodní obrazovka
        }
        if (!odkaz.isEmpty()) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(odkaz)).setPackage(balik);
            if (i.resolveActivity(pm) != null) return i;
        }
        return pm.getLaunchIntentForPackage(balik);
    }

    private void potvrdInstalaci(Intent potvrzeni) {
        try {
            startActivity(potvrzeni);
        } catch (RuntimeException e) {
            Instalace.konec("Instalace se nespustila. Stáhni novou verzi z webu.");
        }
    }

    private void spust(Intent i, int kod) {
        try { startActivityForResult(i, kod); } catch (ActivityNotFoundException e) { /* nic takového v telefonu není */ }
    }

    // ---------- most pro stránku ----------

    private final class Most {
        @JavascriptInterface public String karty() { return Data.nactiPresne(Hlavni.this); }
        @JavascriptInterface public String kartyPredchozi() { return Data.nactiPredchozi(Hlavni.this); }

        @JavascriptInterface public boolean uloz(String json) {
            boolean ok = Data.uloz(Hlavni.this, json);
            Widget.obnovPozdeji(Hlavni.this); // JS na zápis čeká, kreslení widgetu ho nebrzdí
            return ok;
        }

        @JavascriptInterface public String soubor(String jmeno) {
            if (!"retezce.json".equals(jmeno) && !"prodejny.json".equals(jmeno) && !"znacky.json".equals(jmeno)
                    && !"vernost.json".equals(jmeno) && !"akce.json".equals(jmeno)
                    && !"pokladny.json".equals(jmeno)) return "";
            try (InputStream in = getAssets().open("www/" + jmeno)) {
                return Data.precti(in, 8_000_000);
            } catch (IOException e) {
                return "";
            }
        }

        @JavascriptInterface public String obrazek(String format, String kod, int sirka, int vyska) {
            try {
                return Kod.dataUrl(Kod.obrazek(format, kod, Math.max(1, Math.min(sirka, 3000)), Math.max(1, Math.min(vyska, 2000))));
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface public boolean je2D(String format) {
            try { return Kod.je2D(BarcodeFormat.valueOf(format)); } catch (IllegalArgumentException e) { return false; }
        }

        @JavascriptInterface public void jas(boolean max) {
            runOnUiThread(() -> {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = max ? 1f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
                getWindow().setAttributes(lp);
                if (max) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface public String posledniPoloha() {
            Location l = Hlavni.this.posledni();
            return l == null ? "" : json(l);
        }

        @JavascriptInterface public void poloha() { runOnUiThread(Hlavni.this::zjistiPolohu); }

        @JavascriptInterface public String stavPolohy() {
            if (!maPolohu()) return "zakazano";
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return "priblizna";
            return polohaZapnuta() ? "ok" : "vypnuto";
        }

        @JavascriptInterface public void povolitPolohu() {
            runOnUiThread(() -> {
                if (!polohaZapnuta() && maPolohu()) {
                    spust(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), 0);
                } else if (!maPolohu() && shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, POLOHA);
                } else {
                    nastaveniAppky();
                }
            });
        }

        @JavascriptInterface public void nastaveni() { runOnUiThread(Hlavni.this::nastaveniAppky); }

        @JavascriptInterface public void skenovat() {
            runOnUiThread(() -> spust(new Intent(Hlavni.this, Snimac.class), SKEN));
        }

        @JavascriptInterface public void zFotky() {
            runOnUiThread(() -> {
                Intent i;
                if (Build.VERSION.SDK_INT >= 33) {
                    i = new Intent(MediaStore.ACTION_PICK_IMAGES);
                } else {
                    i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("image/*");
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                }
                spust(i, FOTKA);
            });
        }

        /** Víc fotek naráz (snímky karet z jiné appky); kódy ze všech vrátí najednou do naFotky. */
        @JavascriptInterface public void fotky() {
            runOnUiThread(() -> {
                Intent i;
                if (Build.VERSION.SDK_INT >= 33) {
                    i = new Intent(MediaStore.ACTION_PICK_IMAGES);
                    i.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, Math.min(50, MediaStore.getPickImagesMaxLimit()));
                } else {
                    i = new Intent(Intent.ACTION_GET_CONTENT);
                    i.setType("image/*");
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                spust(i, FOTKY);
            });
        }

        /** Kdy sluníčko na widgetu dalo které kartě jas („čas:id,…"); appka si to převezme do historie ukázání. */
        @JavascriptInterface public String jasy() {
            synchronized (Jas.class) {
                SharedPreferences p = getSharedPreferences(Data.PREFS, 0);
                String s = p.getString("jasy", "");
                if (!s.isEmpty()) p.edit().remove("jasy").apply();
                return s;
            }
        }

        @JavascriptInterface public void posledni(String id) {
            // kde a kdy appka kartu ukázala: widget ji pak na tom místě nepřebíjí polohou z pozadí
            Vyber.zvol(Hlavni.this, id, Hlavni.this.posledni());
            Widget.obnovPozdeji(Hlavni.this); // JS na návrat čeká, kreslení widgetu ho nebrzdí
        }

        @JavascriptInterface public String stavWidgetu() { return Hlidka.smi(Hlavni.this) ? "vzdy" : "appka"; }

        /** Widget naživo (Sledovani): přepíná kartu podle místa i po odchodu z appky. */
        @JavascriptInterface public boolean stavNazivo() { return Sledovani.zapnuto(Hlavni.this); }

        @JavascriptInterface public void nazivo(boolean ano) { Sledovani.nastav(Hlavni.this, ano); }

        /** Jak často appka a widget hledají nejbližší obchod: sekunda, casto, normalne, uspora. */
        @JavascriptInterface public String tempo() { return Sledovani.tempo(Hlavni.this); }

        @JavascriptInterface public void nastavTempo(String t) {
            if (!"sekunda".equals(t) && !"casto".equals(t) && !"normalne".equals(t) && !"uspora".equals(t)) return;
            Sledovani.nastavTempo(Hlavni.this, t);
            runOnUiThread(Hlavni.this::hlidejZive);
        }

        @JavascriptInterface public boolean stavZatreseni() { return treseni(); }

        @JavascriptInterface public void zatreseni(boolean ano) {
            getSharedPreferences(Data.PREFS, 0).edit().putBoolean("zatreseni", ano).apply();
            runOnUiThread(() -> poslouchejTreseni(popredi));
        }

        /** Pomáhat s mapou prodejen (Sber): souhlas, je čas poslat, místa, náhodné číslo instalace. */
        @JavascriptInterface public boolean stavSberu() { return Sber.zapnuto(Hlavni.this); }

        @JavascriptInterface public void sber(boolean ano) { Sber.nastav(Hlavni.this, ano); }

        @JavascriptInterface public boolean sberCas() { return Sber.cas(Hlavni.this); }

        @JavascriptInterface public void sberMista(String json) { Sber.mista(Hlavni.this, json == null ? "" : json); }

        @JavascriptInterface public String idZarizeni() { return Sber.id(Hlavni.this); }

        /** Podnět ({druh, text, kontakt}); výsledek přijde do window.naPodnet("odeslano" | "fronta" | "chyba"). */
        @JavascriptInterface public boolean podnet(String json) {
            return json != null && json.length() < 20_000 && Sber.podnet(Hlavni.this, json, v -> js("window.naPodnet&&naPodnet(" + q(v) + ")"));
        }

        @JavascriptInterface public void povolitVzdy() { runOnUiThread(Hlavni.this::povolitVzdy); }

        @JavascriptInterface public String stavBaterie() { return bateriiVolna() ? "volna" : "setri"; }

        @JavascriptInterface public void baterie() { runOnUiThread(Hlavni.this::baterie); }

        /** Rychlý vstup, kterým se appka otevřela (pridat, mapa, hledat, sdileno), jinak "". */
        @JavascriptInterface public String startAkce() {
            String a = startAkce;
            startAkce = null;
            return a == null ? "" : a;
        }

        /** Obrázky nasdílené z jiné appky: najde v nich kódy a pošle je stránce jako u výběru z galerie (naFotky). */
        @JavascriptInterface public void sdileneFotky() {
            List<Uri> u = sdilene;
            sdilene = null;
            if (u == null) js("window.naFotky&&naFotky(null)");
            else zpracujFotky(u);
        }

        @JavascriptInterface public boolean zamceno() { return zamceny(); }

        /** Požádá o odemknutí telefonu (otisk, PIN telefonu); po odemknutí přijde stránce naZamek. */
        @JavascriptInterface public void odemknout() {
            runOnUiThread(() -> {
                KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
                if (km != null) km.requestDismissKeyguard(Hlavni.this, null);
            });
        }

        @JavascriptInterface public boolean stavNaZamku() { return getSharedPreferences(Data.PREFS, 0).getBoolean("naZamku", true); }

        @JavascriptInterface public void naZamku(boolean ano) {
            getSharedPreferences(Data.PREFS, 0).edit().putBoolean("naZamku", ano).apply();
            runOnUiThread(Hlavni.this::nastavZamek);
        }

        @JavascriptInterface public String autoZaloha() { return Zaloha.stav(Hlavni.this); }

        @JavascriptInterface public void nastavAutoZalohu(boolean ano) { Zaloha.nastav(Hlavni.this, ano); }

        @JavascriptInterface public void tma(boolean ano) { runOnUiThread(() -> nastavTmu(ano)); }

        /** Je appka obchodu v telefonu? (Balíček musí být v manifestu v queries.) */
        @JavascriptInterface public boolean maAppku(String balik) {
            return balik != null && !balik.isEmpty() && getPackageManager().getLaunchIntentForPackage(balik) != null;
        }

        /** Stránka appky obchodu v Google Play (otevře Obchod Play, Kartička sama na síť nejde). */
        @JavascriptInterface public boolean obchodPlay(String balik) {
            if (balik == null || !balik.matches("[A-Za-z]\\w*(\\.[A-Za-z]\\w*)+")) return false;
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + balik))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            runOnUiThread(() -> {
                try { startActivity(i); } catch (ActivityNotFoundException e) { }
            });
            return true;
        }

        @JavascriptInterface public String start() {
            String id = startKarta;
            startKarta = null;
            return id == null ? "" : id;
        }

        /** Widget na plochu v základní velikosti 4 × 2; na ploše se pak dá zvětšit i zmenšit. */
        @JavascriptInterface public boolean pridatWidget() {
            AppWidgetManager am = AppWidgetManager.getInstance(Hlavni.this);
            if (!am.isRequestPinAppWidgetSupported()) return false;
            return am.requestPinAppWidget(new ComponentName(Hlavni.this, Widget.class), null, null);
        }

        @JavascriptInterface public void vzhledWidgetu() {
            runOnUiThread(() -> startActivity(new Intent(Hlavni.this, Vzhled.class)));
        }

        @JavascriptInterface public void zaloha() {
            runOnUiThread(() -> {
                String dnes = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, "karticka-zaloha-" + dnes + ".json");
                spust(i, ZALOHA);
            });
        }

        /** Zamkne heslo klíčem telefonu: "ks1:..." (do dat karty), nebo "" když to nejde. */
        @JavascriptInterface public String zamkni(String heslo) {
            if (heslo == null || heslo.length() > 500) return "";
            String ks = Trezor.zamkni(heslo);
            return ks == null ? "" : ks;
        }

        /** Po ověření zámkem obrazovky pošle stránce heslo (naHeslo), při zrušení naHeslo(ks, ''). */
        @JavascriptInterface public void ukazHeslo(String ks) {
            runOnUiThread(() -> {
                if (ks == null || !ks.startsWith(Trezor.PREFIX)) { js("window.naHeslo&&naHeslo(" + q(ks) + ",null)"); return; }
                overUzivatele(ks);
            });
        }

        @JavascriptInterface public void skryjHeslo() { runOnUiThread(() -> Hlavni.this.skryjHeslo(false)); }

        /** Heslo do schránky jen krátce po ověření; po minutě se schránka vyčistí. */
        @JavascriptInterface public boolean kopirujHeslo(String ks) {
            if (SystemClock.elapsedRealtime() > overenoDo) return false;
            String text = Trezor.odemkni(ks);
            if (text == null || !dejDoSchranky(text, STITEK_HESLA)) return false;
            String stitek = stitekSchranky;
            h.postDelayed(() -> vycistiSchranku(stitek), 60_000);
            return true;
        }

        /** Přihlašovací jméno do schránky (nemaže se). */
        @JavascriptInterface public boolean kopiruj(String text) {
            return text != null && text.length() <= 500 && dejDoSchranky(text, "karticka");
        }

        @JavascriptInterface public boolean maHesloZalohy() { return Trezor.maHesloZalohy(Hlavni.this); }

        /** Prázdný text heslo zálohy zruší. */
        @JavascriptInterface public boolean nastavHesloZalohy(String heslo) {
            return Trezor.nastavHesloZalohy(Hlavni.this, heslo);
        }

        /**
         * Hesla ze zálohy znovu zamčená klíčem tohoto telefonu jako JSON {id: "ks1:..."}, nebo "" při špatném
         * hesle. Prázdné heslo zálohy zkusí to uložené v telefonu.
         */
        @JavascriptInterface public String odemkniZalohu(String blokJson, String hesloZalohy) {
            char[] hz = hesloZalohy == null || hesloZalohy.isEmpty() ? Trezor.hesloZalohy(Hlavni.this) : hesloZalohy.toCharArray();
            if (hz == null) return "";
            try {
                Map<String, String> hesla = Trezor.zalohaOdemkni(new JSONObject(blokJson), hz);
                if (hesla == null) return "";
                JSONObject v = new JSONObject();
                for (Map.Entry<String, String> e : hesla.entrySet()) {
                    String ks = Trezor.zamkni(e.getValue());
                    if (ks != null) v.put(e.getKey(), ks);
                }
                return v.toString();
            } catch (JSONException | RuntimeException e) {
                return "";
            } finally {
                Arrays.fill(hz, '\0');
            }
        }

        @JavascriptInterface public void obnova() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                spust(i, OBNOVA);
            });
        }

        @JavascriptInterface public void sdilet(String text) {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                try { startActivity(Intent.createChooser(i, null)); } catch (ActivityNotFoundException e) { }
            });
        }

        /**
         * Body a kupóny: appka obchodu, když je v telefonu, jinak jeho web v prohlížeči. Kartička body sama
         * nezjišťuje (z internetu bere jen číslo nové verze). Balíčky appek musí být v manifestu v queries, jinak je Android neukáže.
         * Vrací "app", "web", nebo "", když obchod nemá ani appku v telefonu, ani web.
         */
        @JavascriptInterface public String otevriProgram(String balik, String web) {
            Intent i = appkaObchodu(Hlavni.this, balik);
            String kam = "app";
            if (i == null) {
                if (web == null || !web.startsWith("https://")) return "";
                i = new Intent(Intent.ACTION_VIEW, Uri.parse(web));
                kam = "web";
            }
            final Intent spustit = i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            runOnUiThread(() -> {
                try { startActivity(spustit); } catch (ActivityNotFoundException e) { }
            });
            return kam;
        }

        @JavascriptInterface public String wifi() { return wifiKolem(); }

        @JavascriptInterface public String verze() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (PackageManager.NameNotFoundException e) {
                return "";
            }
        }

        @JavascriptInterface public String novaVerze() { return Verze.nova(Hlavni.this); }

        @JavascriptInterface public void verzeOhlasena(String kod) {
            try {
                Verze.ohlasena(Hlavni.this, Long.parseLong(kod));
            } catch (NumberFormatException e) {
                // číslo verze je vždy z novaVerze(), jinak nic
            }
        }

        /** Verze v telefonu, čas poslední kontroly a co je nového (Verze.stav). */
        @JavascriptInterface public String stavVerze() { return Verze.stav(Hlavni.this); }

        /** Kontrola hned (Nastavení, Aktualizace); výsledek přijde do window.naKontrolu(true/false). */
        @JavascriptInterface public void zkontrolovatVerzi() {
            Verze.zkontroluj(Hlavni.this, true, ok -> { if (!isDestroyed()) js("window.naKontrolu&&naKontrolu(" + ok + ")"); });
        }

        /** Procenta běžícího stažení aktualizace (100 = čeká na potvrzení), -1 = neběží. */
        @JavascriptInterface public int stavInstalace() { return Instalace.procenta(); }

        @JavascriptInterface public boolean smiInstalovat() {
            return Build.VERSION.SDK_INT < 26 || getPackageManager().canRequestPackageInstalls();
        }

        /**
         * Stáhne a nainstaluje novou verzi přímo v appce: "" = začalo (průběh jde do window.naInstalaci),
         * "povolit" = otevřelo se povolení instalace z Kartičky, "web" = jen přes stránku ke stažení.
         */
        @JavascriptInterface public String aktualizovat() {
            if (!Verze.jdeStahnout(Hlavni.this)) {
                stahnoutNovou();
                return "web";
            }
            if (!smiInstalovat()) {
                runOnUiThread(() -> spust(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())), 0));
                return "povolit";
            }
            Instalace.spust(Hlavni.this);
            return "";
        }

        /** Stránka ke stažení v prohlížeči (náhrada, když stažení v appce nejde; karty zůstanou). */
        @JavascriptInterface public void stahnoutNovou() {
            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Verze.STRANKA)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException e) { }
            });
        }
    }

    private void nastaveniAppky() {
        spust(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())), 0);
    }

    private boolean bateriiVolna() {
        return getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName());
    }

    /** Výjimka z úspory baterie, aby telefon hlídání widgetu neuspal. Povoluje ji Adam sám v okně systému. */
    private void baterie() {
        if (!bateriiVolna()) {
            try {
                startActivityForResult(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())), 0);
                return;
            } catch (ActivityNotFoundException e) {
                // okno v telefonu není: zbývá nastavení appky
            }
        }
        nastaveniAppky();
    }

    // ---------- stránka z assets ----------

    private final class Klient extends WebViewClient {
        @Override
        public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
            Uri u = r.getUrl();
            if (!HOST.equals(u.getHost())) return nic();
            String cesta = u.getPath();
            if (cesta == null || cesta.equals("/")) cesta = "/index.html";
            if (cesta.contains("..")) return nic();
            if (cesta.startsWith("/dlazdice/")) {
                byte[] b = Dlazdice.dej(getApplicationContext(), cesta);
                if (b == null) return nic();
                return new WebResourceResponse("image/png", null, new ByteArrayInputStream(b));
            }
            try {
                InputStream in = getAssets().open("www" + cesta);
                WebResourceResponse res = new WebResourceResponse(mime(cesta), "utf-8", in);
                HashMap<String, String> hl = new HashMap<>();
                hl.put("Cache-Control", "no-store");
                res.setResponseHeaders(hl);
                return res;
            } catch (IOException e) {
                return nic();
            }
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            Uri u = r.getUrl();
            if (HOST.equals(u.getHost())) return false;
            try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (ActivityNotFoundException e) { }
            return true;
        }

        @Override
        public void onPageFinished(WebView v, String url) {
            // teprve teď jsou potvrdZvenku a nastavZvenku v JS, do té doby widget a Vzhled zapisují rovnou do dat
            bezici = new WeakReference<>(Hlavni.this);
            web.evaluateJavascript("window.naNavrat&&naNavrat()", null);
        }
    }

    private static WebResourceResponse nic() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Nenalezeno", new HashMap<>(), null);
    }

    private static String mime(String c) {
        if (c.endsWith(".html")) return "text/html";
        if (c.endsWith(".js")) return "text/javascript";
        if (c.endsWith(".css")) return "text/css";
        if (c.endsWith(".svg")) return "image/svg+xml";
        if (c.endsWith(".png")) return "image/png";
        if (c.endsWith(".webp")) return "image/webp";
        if (c.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }
}
