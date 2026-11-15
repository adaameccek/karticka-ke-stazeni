package cz.adamhorak.karticka;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Dlaždice v rychlém nastavení (stažená horní lišta): jedno klepnutí otevře Kartičku. Hlavni se smí ukázat
 * i nad zamčenou obrazovkou, takže karta obchodu je po ruce bez odemykání.
 */
public class Rychle extends TileService {
    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(Tile.STATE_INACTIVE);
        t.setLabel(getString(R.string.nazev));
        t.updateTile();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onClick() {
        Intent i = new Intent(this, Hlavni.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, i,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            } else {
                startActivityAndCollapse(i);
            }
        } catch (RuntimeException e) {
            // systém otevření nedovolil, dlaždice nic neudělá
        }
    }
}
