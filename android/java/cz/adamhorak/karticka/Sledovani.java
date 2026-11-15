package cz.adamhorak.karticka;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Widget naživo: po odchodu z appky drží tichou službu v popředí a widget přepíná kartu podle polohy,
 * u obchodů co půl minuty nebo 25 m, daleko od nich co minutu a půl (úsporně přes Wi-Fi a síť, ne GPS).
 * To je normální tempo; v Nastavení jde i často (poloviční odstupy), úsporně (dvojnásobné) nebo každou sekundu
 * (podle času, i bez pohybu, s GPS; jen při rozsvíceném displeji, se zhasnutým jako často). Po rozsvícení displeje se zeptá hned, ať widget ukazuje, kde člověk je. Služba spuštěná z otevřené appky
 * smí brát polohu „při používání", takže stačí běžné povolení polohy. Skončí, když se funkce vypne v Nastavení
 * nebo v tiché zprávě, když widget zmizí z plochy nebo appka přijde o polohu. Po restartu telefonu se sama
 * spustí jen s polohou „Povolit vždy", jinak až po dalším otevření appky. Poloha nikam neodchází.
 */
public class Sledovani extends Service implements LocationListener {
    private static final String KANAL = "nazivo";
    private static final int OZNAMENI = 1;
    private static final String VYPNOUT = "cz.adamhorak.karticka.VYPNOUT";
    private static final String TEMPO = "cz.adamhorak.karticka.TEMPO";
    private static final long U_OBCHODU = 30_000L;
    private static final long DALEKO = 90_000L;
    private static final double BLIZKO_M = 2000;
    private static final float KROK = 25f;
    private static final long CERSTVA = 2 * 60_000L; // starší poloha nechá widget u obchodu, odkud člověk odjel
    static final long SEKUNDA = 1000L; // tempo Každou sekundu (i pro otevřenou appku v Hlavni)
    private static final long SEKUNDA_DALEKO = 15_000L; // daleko od obchodů se ani tehdy nemá co přepnout
    private static volatile boolean bezi;

    private HandlerThread vlakno;
    private Handler h;
    private Executor ex;
    private LocationManager lm;
    private String prov;
    private long interval;
    private float krokNyni = -1;
    private boolean gpsNyni;
    private boolean uObchodu = true;
    private boolean sviti = true; // displej rozsvícený: jen tehdy platí tempo Každou sekundu
    private CancellationSignal hned;
    private BroadcastReceiver displej;
    private volatile boolean konci; // po onDestroy už nic nového nezačínat
    // Wi-Fi kolem (patra v obchoďáku jako v appce): u obchodu si služba řekne o nové hledání sítí, nejvýš co půl
    // minuty (systém jich dovolí 4 za 2 minuty), a když výsledky dorazí, přepočítá kartu s poslední polohou
    private static final double WIFI_DO_M = 300;
    private static final long WIFI_ODSTUP = 30_000L;
    private long wifiCas;
    private Location poslPoloha;
    private BroadcastReceiver wifiHotovo;

    static boolean zapnuto(Context c) {
        return c.getSharedPreferences(Data.PREFS, 0).getBoolean("nazivo", true);
    }

    static void nastav(Context c, boolean ano) {
        c.getSharedPreferences(Data.PREFS, 0).edit().putBoolean("nazivo", ano).apply();
        if (ano) spust(c);
        else zastav(c);
    }

    /** Jak často widget hledá nejbližší obchod (Nastavení): sekunda, casto, normalne, nebo uspora (šetří baterii). */
    static String tempo(Context c) {
        String t = c.getSharedPreferences(Data.PREFS, 0).getString("tempo", "normalne");
        return "sekunda".equals(t) || "casto".equals(t) || "uspora".equals(t) ? t : "normalne";
    }

    static boolean kazdouSekundu(Context c) { return "sekunda".equals(tempo(c)); }

    /**
     * Odstup mezi dotazy na polohu podle tempa: často poloviční, úsporně dvojnásobný. Platí i pro Hlidku, které
     * systém po sekundách budit nedá, takže u ní je Každou sekundu jako často.
     */
    static long podleTempa(Context c, long odstup) {
        String t = tempo(c);
        return "casto".equals(t) || "sekunda".equals(t) ? odstup / 2 : "uspora".equals(t) ? odstup * 2 : odstup;
    }

    static void nastavTempo(Context c, String t) {
        c.getSharedPreferences(Data.PREFS, 0).edit().putString("tempo", t).apply();
        if (!bezi) return;
        try {
            c.startService(new Intent(c, Sledovani.class).setAction(TEMPO)); // běžící služba přejde na nový odstup
        } catch (RuntimeException e) {
            // nové tempo platí od příštího spuštění služby
        }
    }

    private static boolean maPolohu(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean smiBezet(Context c) {
        return zapnuto(c) && maPolohu(c) && Widget.vsechny(c).length > 0;
    }

    /** Spustí službu, když je zapnutá, widget je na ploše a appka má polohu. Volá se z otevřené appky. */
    static void spust(Context c) {
        if (!smiBezet(c)) { zastav(c); return; }
        if (bezi) return;
        try {
            c.startForegroundService(new Intent(c, Sledovani.class));
        } catch (RuntimeException e) {
            // systém službu teď nepustí (appka je na pozadí): spustí se při dalším otevření appky
        }
    }

    static void zastav(Context c) {
        if (!bezi) return;
        try {
            c.stopService(new Intent(c, Sledovani.class));
        } catch (RuntimeException e) {
            // služba neběží
        }
    }

    static boolean bezi() { return bezi; }

    @Override
    public void onCreate() {
        super.onCreate();
        bezi = true;
        vlakno = new HandlerThread("nazivo");
        vlakno.start();
        h = new Handler(vlakno.getLooper());
        ex = h::post;
        lm = getSystemService(LocationManager.class);
    }

    @Override
    public int onStartCommand(Intent i, int flags, int startId) {
        if (i != null && VYPNOUT.equals(i.getAction())) {
            // klepnutí na Vypnout v tiché zprávě (obyčejné spuštění služby, startForeground tu není potřeba)
            getSharedPreferences(Data.PREFS, 0).edit().putBoolean("nazivo", false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (i != null && TEMPO.equals(i.getAction())) {
            // změna tempa v Nastavení: služba už běží v popředí, jen se znovu přihlásí o polohu s novým odstupem
            h.post(() -> { interval = 0; poslouchej(uObchodu ? U_OBCHODU : DALEKO); });
            return START_NOT_STICKY;
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(OZNAMENI, oznameni(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(OZNAMENI, oznameni());
        } catch (RuntimeException e) {
            // systém službu v popředí nepustil: widget jede dál jako bez ní
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!smiBezet(this)) { stopSelf(); return START_NOT_STICKY; }
        h.post(() -> {
            if (prov == null) zacni();
            zeptejSe();
        });
        return START_STICKY; // když ji systém zabije kvůli paměti, pustí ji znovu (i == null projde stejnou cestou)
    }

    private Notification oznameni() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel k = new NotificationChannel(KANAL, "Widget naživo", NotificationManager.IMPORTANCE_MIN);
        k.setDescription("Tichá zpráva, dokud widget přepíná kartu podle místa");
        k.setShowBadge(false);
        nm.createNotificationChannel(k);
        PendingIntent otevrit = PendingIntent.getActivity(this, 0, new Intent(this, Hlavni.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent vypnout = PendingIntent.getService(this, 1, new Intent(this, Sledovani.class).setAction(VYPNOUT),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, KANAL)
                .setSmallIcon(R.drawable.oznameni)
                .setContentTitle("Widget přepíná kartu podle místa")
                .setContentText("Vypnout jde tady nebo v Nastavení Kartičky")
                .setContentIntent(otevrit)
                .addAction(new Notification.Action.Builder((Icon) null, "Vypnout", vypnout).build())
                .setOngoing(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    /** Poskytovatel polohy bez GPS: fused (Android 12+), jinak síť; bez nich služba skončí. */
    private void zacni() {
        if (konci) return;
        List<String> vse = lm.getAllProviders();
        if (Build.VERSION.SDK_INT >= 31 && vse.contains(LocationManager.FUSED_PROVIDER)) prov = LocationManager.FUSED_PROVIDER;
        else if (vse.contains(LocationManager.NETWORK_PROVIDER)) prov = LocationManager.NETWORK_PROVIDER;
        else { stopSelf(); return; }
        PowerManager pm = getSystemService(PowerManager.class);
        sviti = pm == null || pm.isInteractive();
        displej = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                boolean zap = Intent.ACTION_SCREEN_ON.equals(i.getAction());
                h.post(() -> {
                    sviti = zap;
                    poslouchej(uObchodu ? U_OBCHODU : DALEKO); // Každou sekundu jen při rozsvíceném displeji
                    if (zap) zeptejSe();
                });
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(displej, f, null, h, RECEIVER_NOT_EXPORTED);
        else registerReceiver(displej, f, null, h);
        wifiHotovo = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                Location l = poslPoloha;
                if (konci || l == null || Hlidka.vek(l) > Vyber.AKTUALNI) return;
                try {
                    Hlidka.prepocti(Sledovani.this, l);
                } catch (RuntimeException e) {
                    // widget zůstane u předchozí karty
                }
            }
        };
        IntentFilter w = new IntentFilter(android.net.wifi.WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(wifiHotovo, w, null, h, RECEIVER_NOT_EXPORTED);
        else registerReceiver(wifiHotovo, w, null, h);
        poslouchej(U_OBCHODU);
    }

    /** U obchodu požádá o nové hledání sítí Wi-Fi (jen je vidí, nepřipojuje se); výsledky zpracuje wifiHotovo. */
    private void hledejWifi(double kObchodu) {
        long ted = android.os.SystemClock.elapsedRealtime();
        if (kObchodu > WIFI_DO_M || !sviti || ted - wifiCas < WIFI_ODSTUP) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        wifiCas = ted;
        try {
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null) wm.startScan();
        } catch (RuntimeException e) {
            // zůstane jen poloha
        }
    }

    /**
     * zaklad: odstup při normálním tempu; často se ptá dvakrát častěji a hlídá menší krok, úsporně naopak.
     * Každou sekundu se při rozsvíceném displeji ptá podle času (bez kroku) a s GPS, se zhasnutým jako často.
     */
    private void poslouchej(long zaklad) {
        boolean sekunda = sviti && kazdouSekundu(this);
        long co = sekunda ? (zaklad == DALEKO ? SEKUNDA_DALEKO : SEKUNDA) : podleTempa(this, zaklad);
        float krok = sekunda ? 0f : podleTempa(this, 100) / 100f * KROK;
        boolean gps = sekunda && co == SEKUNDA;
        if (konci || prov == null || (co == interval && krok == krokNyni && gps == gpsNyni)) return;
        try {
            lm.removeUpdates(this);
            if (Build.VERSION.SDK_INT >= 31) {
                lm.requestLocationUpdates(prov, new LocationRequest.Builder(co)
                        .setQuality(gps ? LocationRequest.QUALITY_HIGH_ACCURACY : LocationRequest.QUALITY_BALANCED_POWER_ACCURACY)
                        .setMinUpdateDistanceMeters(krok)
                        .setMinUpdateIntervalMillis(Math.min(10_000L, co))
                        .build(), ex, this);
            } else {
                lm.requestLocationUpdates(prov, co, krok, this, vlakno.getLooper());
            }
            interval = co;
            krokNyni = krok;
            gpsNyni = gps;
        } catch (SecurityException | IllegalArgumentException e) {
            stopSelf(); // appka přišla o polohu
        }
    }

    /** Poloha hned (po startu a po rozsvícení displeje): poslední známá jen čerstvá, jinak se počká na novou. */
    private void zeptejSe() {
        if (konci) return;
        Location posl = Hlidka.posledni(this);
        if (posl != null && Hlidka.vek(posl) < CERSTVA) onLocationChanged(posl);
        if (prov == null || (posl != null && Hlidka.vek(posl) < 20_000L)) return;
        if (hned != null) hned.cancel();
        hned = new CancellationSignal();
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                lm.getCurrentLocation(prov, new LocationRequest.Builder(0)
                        .setQuality(LocationRequest.QUALITY_BALANCED_POWER_ACCURACY).setDurationMillis(20_000).build(),
                        hned, ex, l -> { if (l != null) onLocationChanged(l); });
            } else if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(prov, hned, ex, l -> { if (l != null) onLocationChanged(l); });
            }
            // starší Android: stačí průběžný příjem
        } catch (SecurityException | IllegalArgumentException e) {
            // bez polohy: průběžný příjem skončí sám
        }
    }

    @Override
    public void onLocationChanged(Location l) {
        if (l == null || konci) return;
        if (Widget.vsechny(this).length == 0 || !zapnuto(this)) { stopSelf(); return; }
        try {
            poslPoloha = l;
            Hlidka.prepocti(this, l);
            // daleko od všech obchodů s kartou stačí ptát se míň často
            double kObchodu = Vyber.kObchodu(this, l.getLatitude(), l.getLongitude());
            uObchodu = kObchodu <= BLIZKO_M;
            hledejWifi(kObchodu);
            poslouchej(uObchodu ? U_OBCHODU : DALEKO);
        } catch (RuntimeException e) {
            // widget zůstane u předchozí karty
        }
    }

    // Android do 10 volá i tyhle (rozhraní tam nemá výchozí metody)
    @Override public void onStatusChanged(String p, int s, Bundle b) { }
    @Override public void onProviderEnabled(String p) { }
    @Override public void onProviderDisabled(String p) { }

    @Override
    public void onDestroy() {
        bezi = false;
        konci = true;
        h.removeCallbacksAndMessages(null);
        if (hned != null) hned.cancel();
        if (displej != null) {
            try { unregisterReceiver(displej); } catch (RuntimeException e) { /* nebyl zaregistrovaný */ }
        }
        if (wifiHotovo != null) {
            try { unregisterReceiver(wifiHotovo); } catch (RuntimeException e) { /* nebyl zaregistrovaný */ }
        }
        try { lm.removeUpdates(this); } catch (RuntimeException e) { /* nic neposlouchá */ }
        vlakno.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}
