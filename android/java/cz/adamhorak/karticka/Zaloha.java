package cz.adamhorak.karticka;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Samočinná druhá záloha (Adam 9.11.2026): karty.json a jeho .bak leží v soukromém úložišti appky a s odinstalací
 * nebo ztrátou telefonu zmizí. Tahle kopie se sama píše do Stažené/Karticka (soubor na měsíc, přepisuje se),
 * přežije odinstalaci a složku umí odnést dál FolderSync nebo Smart Switch. Appka sama nic nikam neposílá.
 *
 * Obsah je stejný jako u ruční zálohy (Hlavni.zalohaTextu): hesla k účtům a PINy jen zamčené heslem zálohy,
 * bez něj v souboru nejsou. Čísla karet v něm jsou čitelná jako v ruční záloze.
 *
 * Píše se při odchodu z appky, když se od minula změnil počet karet nebo uplynulo 12 hodin a data se změnila.
 * Android 10+ (MediaStore bez oprávnění), na starším se nezálohuje.
 */
final class Zaloha {
    private Zaloha() {}

    private static final String SLOZKA = Environment.DIRECTORY_DOWNLOADS + "/Karticka";
    private static final long ODSTUP = 12 * 3600_000L;
    private static final Object ZAMEK = new Object();

    static boolean jde() { return Build.VERSION.SDK_INT >= 29; }

    static boolean zapnuto(Context c) {
        return jde() && c.getSharedPreferences(Data.PREFS, 0).getBoolean("autoZaloha", true);
    }

    static void nastav(Context c, boolean ano) {
        c.getSharedPreferences(Data.PREFS, 0).edit().putBoolean("autoZaloha", ano).apply();
        if (ano) pozdeji(c, true);
    }

    /** Stav pro Nastavení: {"jde":true,"zap":true,"cas":1731150000000,"soubor":"karticka-auto-2026-11.json"}. */
    static String stav(Context c) {
        SharedPreferences sp = c.getSharedPreferences(Data.PREFS, 0);
        try {
            return new JSONObject().put("jde", jde()).put("zap", zapnuto(c)).put("cas", sp.getLong("autoZalohaCas", 0))
                    .put("soubor", sp.getString("autoZalohaSoubor", "")).toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    /** Při odchodu z appky: mimo hlavní vlákno, tiše. hned = bez ohledu na odstup (po zapnutí v Nastavení). */
    static void pozdeji(Context c, boolean hned) {
        if (!zapnuto(c)) return;
        Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                synchronized (ZAMEK) { zapis(app, hned); }
            } catch (RuntimeException e) {
                // záloha je pojistka navíc, appku nesmí shodit
            }
        }).start();
    }

    private static void zapis(Context c, boolean hned) {
        if (Build.VERSION.SDK_INT < 29) return;
        SharedPreferences sp = c.getSharedPreferences(Data.PREFS, 0);
        String data = Data.nacti(c);
        if (data.isEmpty()) return;
        int pocet;
        try {
            org.json.JSONArray k = new JSONObject(data).optJSONArray("karty");
            pocet = k == null ? 0 : k.length();
        } catch (JSONException e) {
            return; // poškozená data starou dobrou zálohu nepřepíšou
        }
        if (pocet == 0) return; // prázdná appka (nová instalace) nesmí přepsat zálohu s kartami
        long ted = System.currentTimeMillis();
        int otisk = data.hashCode();
        boolean zmena = otisk != sp.getInt("autoZalohaOtisk", 0);
        if (!hned && !(zmena && (pocet != sp.getInt("autoZalohaPocet", -1) || ted - sp.getLong("autoZalohaCas", 0) >= ODSTUP))) return;
        String text = Hlavni.zalohaTextu(c, new int[1]);
        if (text == null) return;
        String jmeno = "karticka-auto-" + new SimpleDateFormat("yyyy-MM", Locale.ROOT).format(new Date(ted)) + ".json";
        ContentResolver cr = c.getContentResolver();
        Uri u = null;
        // soubor z téhle instalace se přepíše; po přeinstalaci už appce nepatří, založí se nový (starý zůstane)
        if (jmeno.equals(sp.getString("autoZalohaSoubor", ""))) {
            String s = sp.getString("autoZalohaUri", "");
            if (!s.isEmpty()) u = Uri.parse(s);
        }
        boolean ok = u != null && pis(cr, u, text);
        if (!ok) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, jmeno);
            v.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, SLOZKA);
            try {
                u = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            } catch (RuntimeException e) {
                u = null;
            }
            ok = u != null && pis(cr, u, text);
        }
        if (!ok) return;
        sp.edit().putLong("autoZalohaCas", ted).putInt("autoZalohaPocet", pocet).putInt("autoZalohaOtisk", otisk)
                .putString("autoZalohaSoubor", jmeno).putString("autoZalohaUri", u.toString()).apply();
    }

    private static boolean pis(ContentResolver cr, Uri u, String text) {
        try (OutputStream o = cr.openOutputStream(u, "wt")) {
            if (o == null) return false;
            o.write(text.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
