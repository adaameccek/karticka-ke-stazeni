package cz.adamhorak.karticka;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Aktualizace přímo z appky: Verze.java stáhne APK z karticka.adamhorak.cz rovnou do instalace Androidu
 * (PackageInstaller, žádný soubor ve Stažených), a když sedí velikost i SHA-256 z verze.json, Android se zeptá,
 * jestli ji nainstalovat. Novou verzi přijme jen se stejným podpisem, karty zůstanou. Napoprvé chce Android
 * povolit instalaci z Kartičky (Instalovat neznámé aplikace), to řeší Hlavni před spuštěním.
 * Tady je i příjemce výsledku od Androidu: potvrzovací okno otevře Hlavni, když je vidět, jinak po návratu.
 */
public final class Instalace extends BroadcastReceiver {
    /** Co se děje: procenta stažení (100 = čeká na potvrzení), nebo chyba (text pro člověka). */
    interface Posluchac {
        void stav(int procenta, String chyba);
        /** Otevře potvrzení instalace, když je appka vidět; false = počká na návrat. */
        boolean potvrdit(Intent potvrzeni);
    }

    static volatile Posluchac posluchac;
    private static volatile Intent cekajici;
    private static volatile int procenta = -1;
    private static final AtomicBoolean bezi = new AtomicBoolean();

    static boolean bezi() { return bezi.get(); }

    static int procenta() { return bezi.get() ? procenta : -1; }

    /** Potvrzení, které nešlo otevřít, protože appka nebyla vidět (jen jednou). */
    static Intent vezmiCekajici() {
        Intent i = cekajici;
        cekajici = null;
        return i;
    }

    static void spust(Context c) {
        if (!bezi.compareAndSet(false, true)) return;
        Context app = c.getApplicationContext();
        cekajici = null;
        hlas(0, null);
        new Thread(() -> {
            PackageInstaller pi = app.getPackageManager().getPackageInstaller();
            PackageInstaller.Session s = null;
            try {
                long velikost = Verze.velikost(app);
                PackageInstaller.SessionParams par = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                par.setAppPackageName(app.getPackageName());
                if (velikost > 0) par.setSize(velikost);
                int id = pi.createSession(par);
                s = pi.openSession(id);
                boolean ok;
                try (OutputStream out = s.openWrite("karticka.apk", 0, velikost > 0 ? velikost : -1)) {
                    ok = Verze.stahniApk(app, out, p -> hlas(Math.min(p, 99), null));
                    if (ok) s.fsync(out);
                }
                if (!ok) {
                    s.abandon();
                    s = null;
                    konec("Stažení se nepovedlo. Zkus to znovu, nebo stáhni novou verzi z webu.");
                    return;
                }
                PendingIntent vysledek = PendingIntent.getBroadcast(app, id, new Intent(app, Instalace.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
                s.commit(vysledek.getIntentSender());
                s.close();
                s = null;
                hlas(100, null);
            } catch (IOException | RuntimeException e) {
                if (s != null) {
                    try {
                        s.abandon();
                    } catch (RuntimeException e2) {
                        // sezení už je pryč
                    }
                }
                konec("Stažení se nepovedlo. Zkus to znovu, nebo stáhni novou verzi z webu.");
            }
        }).start();
    }

    private static void hlas(int p, String chyba) {
        procenta = p;
        Posluchac l = posluchac;
        if (l != null) l.stav(p, chyba);
    }

    static void konec(String chyba) {
        bezi.set(false);
        hlas(-1, chyba);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        int stav = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (stav == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent potvrzeni = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (potvrzeni == null) {
                konec("Instalace se nespustila. Stáhni novou verzi z webu.");
                return;
            }
            potvrzeni.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Posluchac l = posluchac;
            if (l == null || !l.potvrdit(potvrzeni)) cekajici = potvrzeni;
            return;
        }
        cekajici = null;
        if (stav == PackageInstaller.STATUS_SUCCESS) {
            bezi.set(false); // Android appku po instalaci zavře, nová verze se otevře z plochy
            return;
        }
        konec(stav == PackageInstaller.STATUS_FAILURE_ABORTED ? "Instalace je zrušená."
                : stav == PackageInstaller.STATUS_FAILURE_STORAGE ? "Na instalaci chybí místo v telefonu."
                : "Instalace se nepovedla. Stáhni novou verzi z webu.");
    }
}
