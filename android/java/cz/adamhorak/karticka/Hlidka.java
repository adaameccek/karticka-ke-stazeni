package cz.adamhorak.karticka;

import android.Manifest;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Poloha pro widget, když appka neběží. Bere polohy, které telefon zjistil pro jiné appky (pasivně, nic to nestojí),
 * a sám se zeptá úsporně přes Wi-Fi a síť: u obchodu nebo v pohybu co 2 minuty, jinak co 5 až 10 minut.
 * Když telefon spí, systém úlohu odloží a pustí ji po rozsvícení. Běží jen s polohou „Povolit vždy"
 * a jen když je widget na ploše. Poloha nikam neodchází, slouží jen k výběru karty.
 * Místo periodického jobu je to řetěz jednorázových: každý naplánuje další s odstupem podle polohy.
 */
public class Hlidka extends JobService {
    private static final int STARY = 1;          // periodický job z minulé verze, jen se ruší
    private static final int RETEZ_A = 2, RETEZ_B = 3; // střídají se: schedule() se stejným id zastaví běžící job
    private static final int PREKRESLENI = 4; // widget pustí ručně vybranou kartu, až přestane platit (i Jsem v prodejně)
    private static final long KRATKA = 2 * 60_000L;
    private static final long STREDNI = 5 * 60_000L;
    private static final long DLOUHA = 10 * 60_000L;
    private static final long BEZ_POLOHY = 10 * 60_000L;
    private static final long POJISTKA = 5 * 60_000L; // kdyby systém běh přerušil, řetěz nezanikne
    private static final long CERSTVA = 60_000L;
    private static final long STARA_POLOHA = 20 * 60_000L;
    private static final long POHYB_OKNO = 30 * 60_000L;

    private final Handler h = new Handler(Looper.getMainLooper());
    private CancellationSignal zruseni;
    private LocationListener posluchac;
    private JobParameters bezi;

    static boolean smi(Context c) {
        boolean popredi = c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!popredi) return false;
        return Build.VERSION.SDK_INT < 29
                || c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean maWidget(Context c) {
        return Widget.vsechny(c).length > 0;
    }

    /** Zapne nebo vypne hlídání podle oprávnění a podle toho, jestli je widget na ploše. */
    static void naplanuj(Context c) {
        try {
            if (!smi(c) || !maWidget(c)) { zrus(c); return; }
            pasivne(c, true);
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (js.getPendingJob(STARY) != null) js.cancel(STARY);
            if (!retezec(js)) planuj(c, RETEZ_A, 60_000L); // běžící řetěz se nechá být
        } catch (RuntimeException e) {
            // plánovač nebo poloha nejsou k dispozici: widget zůstane u karty z appky
        }
    }

    static void zrus(Context c) {
        try {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            js.cancel(STARY);
            js.cancel(RETEZ_A);
            js.cancel(RETEZ_B);
        } catch (RuntimeException e) {
            // nic naplánovaného
        }
        pasivne(c, false);
    }

    /** Je naplánovaný některý job řetězu? */
    private static boolean retezec(JobScheduler js) {
        return js.getPendingJob(RETEZ_A) != null || js.getPendingJob(RETEZ_B) != null;
    }

    /** Jednorázový job za zadanou dobu. */
    private static void planuj(Context c, int id, long za) {
        try {
            c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(id, new ComponentName(c, Hlidka.class))
                    .setMinimumLatency(za)
                    .setPersisted(true)
                    .build());
        } catch (RuntimeException e) {
            // plánovač není k dispozici: řetěz se obnoví při dalším otevření appky
        }
    }

    /**
     * Překreslení widgetu za zadanou dobu, bez polohy. Neruší ho zrus(): to se volá právě bez „Povolit vždy",
     * kdy překreslení potřeba je. Bez widgetu na ploše nic neudělá.
     */
    static void prekresli(Context c, long za) {
        planuj(c, PREKRESLENI, za + 5_000L);
    }

    private static int dalsi(JobParameters p) {
        return p.getJobId() == RETEZ_A ? RETEZ_B : RETEZ_A;
    }

    private static PendingIntent pasivniIntent(Context c) {
        Intent i = new Intent(c, PolohaWidgetu.class).setAction(PolohaWidgetu.POLOHA);
        // MUTABLE: systém do intentu doplňuje polohu
        int f = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getBroadcast(c, 0, i, f);
    }

    private static void pasivne(Context c, boolean zapnout) {
        LocationManager lm = c.getSystemService(LocationManager.class);
        // pasivní příjem chce přesnou polohu; s přibližnou zbývá jen dotaz z hlídání
        if (zapnout && c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            zapnout = false;
        }
        try {
            if (zapnout) lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 60_000L, 25f, pasivniIntent(c));
            else lm.removeUpdates(pasivniIntent(c));
        } catch (SecurityException | IllegalArgumentException e) {
            // bez oprávnění nebo bez pasivního poskytovatele
        }
    }

    static long vek(Location l) {
        return (SystemClock.elapsedRealtimeNanos() - l.getElapsedRealtimeNanos()) / 1_000_000L;
    }

    static Location posledni(Context c) {
        LocationManager lm = c.getSystemService(LocationManager.class);
        Location nej = null;
        for (String p : lm.getProviders(true)) {
            Location l;
            try {
                l = lm.getLastKnownLocation(p);
            } catch (SecurityException | IllegalArgumentException e) {
                continue; // gps a pasivní chtějí přesnou polohu, ostatní poskytovatelé můžou odpovědět
            }
            if (l != null && (nej == null || l.getElapsedRealtimeNanos() > nej.getElapsedRealtimeNanos())) nej = l;
        }
        return nej;
    }

    /** Přepočítá kartu pro widget a překreslí ho, když se změnila karta nebo vzdálenost k nejbližšímu obchodu. */
    static void prepocti(Context c, Location l) {
        Widget.obnov(c, Vyber.naPolohu(c, l));
    }

    /**
     * Za jak dlouho se zeptat příště: u obchodu nebo v pohybu často, jinak méně. Zapamatuje si i tuhle polohu,
     * podle ní se příště pozná pohyb.
     */
    static long interval(Context c, Location l) {
        if (l == null || vek(l) > STARA_POLOHA) return BEZ_POLOHY;
        SharedPreferences sp = c.getSharedPreferences(Data.PREFS, 0);
        long ted = System.currentTimeMillis();
        double lat = l.getLatitude(), lon = l.getLongitude();
        float presnost = l.hasAccuracy() ? l.getAccuracy() : 999;
        long hlCas = sp.getLong("hlCas", 0);
        boolean jede = sp.contains("hlLat") && sp.contains("hlLon") && hlCas > 0 && ted - hlCas < POHYB_OKNO
                && Vyber.vzdalenost(sp.getFloat("hlLat", 0), sp.getFloat("hlLon", 0), lat, lon) >= Math.max(150, presnost);
        sp.edit().putFloat("hlLat", (float) lat).putFloat("hlLon", (float) lon).putLong("hlCas", ted).apply();
        double d = Vyber.kObchodu(c, lat, lon);
        if (d <= 300 || jede) return Sledovani.podleTempa(c, KRATKA);
        if (d <= 1500) return Sledovani.podleTempa(c, STREDNI);
        return Sledovani.podleTempa(c, DLOUHA);
    }

    @Override
    public boolean onStartJob(JobParameters p) {
        if (p.getJobId() == PREKRESLENI) {
            new Thread(() -> {
                try {
                    Widget.obnov(this);
                } catch (RuntimeException e) {
                    // widget zůstane, srovná se při dalším otevření appky
                } finally {
                    jobFinished(p, false);
                }
            }).start();
            return true;
        }
        if (p.getJobId() == STARY) {
            // job z minulé verze: přejde na řetěz a sám se zruší
            JobScheduler js = getSystemService(JobScheduler.class);
            if (!retezec(js)) planuj(this, RETEZ_A, 60_000L);
            js.cancel(STARY);
            return false;
        }
        if (!smi(this) || !maWidget(this)) { zrus(this); return false; }
        // služba je sdílená: když už jiný job běží, pole bezi, zruseni a posluchac se nepřepisují
        if (bezi != null) { planuj(this, dalsi(p), POJISTKA); return false; }
        planuj(this, dalsi(p), POJISTKA); // pojistka: kdyby systém běh přerušil, řetěz nezanikne
        pasivne(this, true); // po restartu telefonu nebo aktualizaci appky se pasivní příjem obnoví
        Sledovani.spust(this); // Widget naživo, který systém uspal, se zkusí vrátit (smí jen s výjimkou z úspory baterie)
        Location posl = posledni(this);
        if (posl != null && vek(posl) < CERSTVA) { hotovo(p, posl); return true; }
        LocationManager lm = getSystemService(LocationManager.class);
        String prov = null;
        if (Build.VERSION.SDK_INT >= 31 && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) prov = LocationManager.FUSED_PROVIDER;
        else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) prov = LocationManager.NETWORK_PROVIDER;
        if (prov == null) { hotovo(p, posl); return true; }
        bezi = p;
        h.postDelayed(() -> konec(null), 30_000);
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                zruseni = new CancellationSignal();
                LocationRequest rq = new LocationRequest.Builder(0)
                        .setQuality(LocationRequest.QUALITY_BALANCED_POWER_ACCURACY).setDurationMillis(30_000).build();
                lm.getCurrentLocation(prov, rq, zruseni, getMainExecutor(), this::konec);
            } else if (Build.VERSION.SDK_INT >= 30) {
                zruseni = new CancellationSignal();
                lm.getCurrentLocation(prov, zruseni, getMainExecutor(), this::konec);
            } else {
                posluchac = new LocationListener() {
                    @Override public void onLocationChanged(Location l) { konec(l); }
                    @Override public void onStatusChanged(String a, int b, Bundle c) {}
                    @Override public void onProviderEnabled(String a) {}
                    @Override public void onProviderDisabled(String a) {}
                };
                lm.requestSingleUpdate(prov, posluchac, Looper.getMainLooper());
            }
        } catch (SecurityException | IllegalArgumentException e) {
            konec(null);
        }
        return true;
    }

    private void konec(Location l) {
        JobParameters p = bezi;
        if (p == null) return;
        bezi = null;
        uklid();
        hotovo(p, l);
    }

    /** Výpočet karty a kreslení widgetu mimo hlavní vlákno, naplánování dalšího jobu, pak konec jobu. */
    private void hotovo(JobParameters p, Location l) {
        new Thread(() -> {
            try {
                Location x = l != null ? l : posledni(this);
                try {
                    prepocti(this, x);
                } catch (RuntimeException e) {
                    // widget zůstane u předchozí karty
                }
                planuj(this, dalsi(p), interval(this, x)); // přepíše pojistku
            } catch (RuntimeException e) {
                // zůstane pojistka
            } finally {
                jobFinished(p, false);
            }
        }).start();
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
    public boolean onStopJob(JobParameters p) {
        if (p.getJobId() == PREKRESLENI) return false; // nesahá na zjišťování polohy, které třeba zrovna běží
        bezi = null;
        uklid();
        return false;
    }
}
