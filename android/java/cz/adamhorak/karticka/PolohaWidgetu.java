package cz.adamhorak.karticka;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;

/** Polohy pro widget (pasivní příjem) a obnovení hlídání a Widgetu naživo po startu telefonu nebo po aktualizaci appky. */
public class PolohaWidgetu extends BroadcastReceiver {
    static final String POLOHA = "cz.adamhorak.karticka.POLOHA";

    @Override
    public void onReceive(Context c, Intent i) {
        if (POLOHA.equals(i.getAction())) {
            Location l = i.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED);
            if (l == null) return;
            Context app = c.getApplicationContext();
            PendingResult r = goAsync();
            new Thread(() -> {
                try {
                    Hlidka.prepocti(app, l);
                } catch (RuntimeException e) {
                    // widget zůstane u předchozí karty, appka kvůli tomu nespadne
                } finally {
                    r.finish();
                }
            }).start();
        } else {
            Hlidka.naplanuj(c);
            Widget.obnov(c); // karta z appky bez polohy na pozadí mezitím mohla přestat platit
            // Widget naživo bez otevřené appky jen s „Povolit vždy", jinak ho spustí až další otevření appky
            if (Hlidka.smi(c)) Sledovani.spust(c);
        }
    }
}
