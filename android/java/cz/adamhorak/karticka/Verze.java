package cz.adamhorak.karticka;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Je na karticka.adamhorak.cz novější verze, než je v telefonu? Nejvýš jednou za 6 hodin (po otevření appky),
 * nebo hned po klepnutí na Zkontrolovat, stáhne verze.json (pár bajtů s číslem verze a otiskem APK) a zmeny.json
 * (co je v posledních verzích nového). Po klepnutí na Aktualizovat stáhne odtamtud i APK (Instalace.java ho předá
 * Androidu). Nic neposílá: žádné karty, polohu ani údaje o telefonu (ani model v User-Agent). Bez internetu se nic
 * nestane, appka jede dál a zkusí to při dalším otevření. build.py hlídá, aby síť nepoužíval jiný soubor.
 * Pruh o nové verzi ukazuje app.js, dokud ho člověk nezavře, to si pamatuje tady (ohlaseno).
 */
final class Verze {
    static final String STRANKA = "https://karticka.adamhorak.cz/";
    private static final String ADRESA = STRANKA + "verze.json";
    private static final String ZMENY = STRANKA + "zmeny.json";
    private static final String APK = STRANKA + "karticka.apk";
    private static final String PREFS = "verze";
    private static final long INTERVAL = 6 * 3600_000L;
    private static final long PO_CHYBE = 15 * 60_000L;
    private static final long NEJVIC_APK = 30_000_000L;
    private static final AtomicBoolean bezi = new AtomicBoolean();

    interface Vysledek { void hotovo(boolean ok); }
    interface Prubeh { void procenta(int p); }

    private Verze() { }

    /**
     * Zjistí na pozadí verzi na webu: sama jen když je čas, s hned=true pokaždé (klepnutí na Zkontrolovat).
     * Vysledek se zavolá z vlákna na pozadí; bez hned jen když se to povedlo.
     */
    static void zkontroluj(Context c, boolean hned, Vysledek vysledek) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        long ted = System.currentTimeMillis();
        long ok = p.getLong("ok", 0), pokus = p.getLong("pokus", 0);
        // hodiny posunuté zpátky (ted < ok) by kontrolu jinak odložily klidně o roky
        if (!hned && ted >= ok && ted - ok < INTERVAL) return;
        if (!hned && ted >= pokus && ted - pokus < PO_CHYBE) return;
        if (!bezi.compareAndSet(false, true)) {
            if (hned && vysledek != null) vysledek.hotovo(false);
            return;
        }
        p.edit().putLong("pokus", ted).apply();
        Context app = c.getApplicationContext();
        new Thread(() -> {
            boolean povedlo = false;
            try {
                JSONObject j = json(ADRESA, 4096);
                if (j == null || j.optLong("kod") <= 0) return;
                SharedPreferences.Editor e = app.getSharedPreferences(PREFS, 0).edit()
                        .putLong("kod", j.optLong("kod"))
                        .putString("nazev", nazev(j.optString("verze")))
                        .putString("sha256", j.optString("sha256").matches("[0-9a-f]{64}") ? j.optString("sha256") : "")
                        .putLong("velikost", Math.max(0, Math.min(NEJVIC_APK, j.optLong("velikost"))))
                        .putLong("ok", System.currentTimeMillis());
                // seznam změn hned po verze.json se na emulátoru (u Speedometru 4.11.2026) jednou ze čtyř pokusů
                // nestáhl: zkusí se ještě jednou
                String zmeny = zmeny();
                if (zmeny == null) zmeny = zmeny();
                if (zmeny != null) e.putString("zmeny", zmeny);
                e.apply();
                povedlo = true;
            } finally {
                bezi.set(false);
                if (vysledek != null && (povedlo || hned)) vysledek.hotovo(povedlo);
            }
        }).start();
    }

    private static HttpURLConnection otevri(String adresa, String prijmout) throws IOException {
        HttpURLConnection s = (HttpURLConnection) new URL(adresa).openConnection();
        s.setConnectTimeout(5000);
        s.setReadTimeout(10000);
        s.setUseCaches(false);
        s.setInstanceFollowRedirects(false);
        s.setRequestProperty("User-Agent", "Karticka");
        s.setRequestProperty("Accept", prijmout);
        return s;
    }

    /** JSON z webu, nejvýš nejvic bajtů; jinak null. */
    private static JSONObject json(String adresa, int nejvic) {
        String text = text(adresa, nejvic);
        try {
            return text == null ? null : new JSONObject(text);
        } catch (JSONException e) {
            return null;
        }
    }

    private static String text(String adresa, int nejvic) {
        HttpURLConnection s = null;
        try {
            s = otevri(adresa, "application/json");
            if (s.getResponseCode() != 200) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = s.getInputStream()) {
                byte[] b = new byte[1024];
                for (int n; (n = in.read(b)) > 0; ) {
                    out.write(b, 0, n);
                    if (out.size() > nejvic) return null;
                }
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            if (s != null) s.disconnect();
        }
    }

    /** Seznam změn pročištěný na [{kod, datum, zmeny: [text]}], ať do stránky nejde nic jiného; jinak null. */
    private static String zmeny() {
        String text = text(ZMENY, 32768);
        if (text == null) return null;
        try {
            JSONArray vstup = new JSONArray(text), ven = new JSONArray();
            for (int i = 0; i < vstup.length() && i < 12; i++) {
                JSONObject z = vstup.optJSONObject(i);
                JSONArray body = z == null ? null : z.optJSONArray("zmeny");
                if (body == null || z.optLong("kod") <= 0) continue;
                JSONArray texty = new JSONArray();
                for (int k = 0; k < body.length() && k < 12; k++) {
                    String b = radek(body.optString(k), 200);
                    if (!b.isEmpty()) texty.put(b);
                }
                ven.put(new JSONObject().put("kod", z.optLong("kod")).put("datum", radek(z.optString("datum"), 20))
                        .put("zmeny", texty));
            }
            return ven.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Stáhne APK nové verze do out (instalace Androidu) a ověří ho podle verze.json: true, jen když sedí velikost
     * i SHA-256. Jinak se do instalace nic nepošle (Instalace.java pak sezení zahodí).
     */
    static boolean stahniApk(Context c, OutputStream out, Prubeh prubeh) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        String otisk = p.getString("sha256", "");
        long velikost = p.getLong("velikost", 0);
        if (otisk.isEmpty() || velikost <= 0) return false;
        HttpURLConnection s = null;
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            s = otevri(APK, "application/vnd.android.package-archive");
            if (s.getResponseCode() != 200) return false;
            long hotovo = 0;
            int minule = -1;
            try (InputStream in = s.getInputStream()) {
                byte[] b = new byte[16384];
                for (int n; (n = in.read(b)) > 0; ) {
                    hotovo += n;
                    if (hotovo > velikost) return false;
                    sha.update(b, 0, n);
                    out.write(b, 0, n);
                    int pr = (int) (hotovo * 100 / velikost);
                    if (pr != minule && prubeh != null) prubeh.procenta(minule = pr);
                }
            }
            return hotovo == velikost && otisk.equals(hex(sha.digest()));
        } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
            return false;
        } finally {
            if (s != null) s.disconnect();
        }
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(String.format("%02x", x & 0xff));
        return s.toString();
    }

    /** Text do appky: jen řádek bez řídicích znaků. */
    private static String radek(String s, int nejvic) {
        s = s == null ? "" : s.replaceAll("\\p{Cntrl}", "").trim();
        return s.length() > nejvic ? s.substring(0, nejvic) : s;
    }

    private static String nazev(String s) { return radek(s, 40); }

    static long vlastni(Context c) {
        try {
            PackageInfo i = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return Build.VERSION.SDK_INT >= 28 ? i.getLongVersionCode() : i.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return Long.MAX_VALUE; // vlastní verzi nezjistí: radši nic neohlásit
        }
    }

    static long velikost(Context c) { return c.getSharedPreferences(PREFS, 0).getLong("velikost", 0); }

    /** Jde novou verzi stáhnout přímo v appce (verze.json má otisk APK)? */
    static boolean jdeStahnout(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        return p.getLong("kod", 0) > vlastni(c) && !p.getString("sha256", "").isEmpty() && p.getLong("velikost", 0) > 0;
    }

    /**
     * Novější verze na webu, než je v telefonu: {"kod":…, "verze":"…", "ohlasena":true/false}, jinak "".
     * Na webu stejná nebo starší verze (třeba build nahraný kabelem dřív, než je na webu) nic neukáže.
     */
    static String nova(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        long kod = p.getLong("kod", 0);
        if (kod <= vlastni(c)) return "";
        try {
            return new JSONObject()
                    .put("kod", kod)
                    .put("verze", p.getString("nazev", ""))
                    .put("ohlasena", p.getLong("ohlaseno", 0) >= kod)
                    .put("velikost", p.getLong("velikost", 0))
                    .toString();
        } catch (JSONException e) {
            return "";
        }
    }

    /** Pro Nastavení: {"kod": verze v telefonu, "zkontrolovano": čas poslední kontroly (ms), "zmeny": [...]}. */
    static String stav(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        try {
            JSONObject j = new JSONObject().put("kod", vlastni(c)).put("zkontrolovano", p.getLong("ok", 0));
            String zmeny = p.getString("zmeny", null);
            if (zmeny != null) j.put("zmeny", new JSONArray(zmeny));
            return j.toString();
        } catch (JSONException e) {
            return "";
        }
    }

    static void ohlasena(Context c, long kod) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        if (kod > p.getLong("ohlaseno", 0)) p.edit().putLong("ohlaseno", kod).apply();
    }
}
