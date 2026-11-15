package cz.adamhorak.karticka;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Pomoc s mapou prodejen a podněty. Vedle Verze.java jediné místo, které na internet něco posílá, a jen
 * na karticka.adamhorak.cz/api (build.py hlídá, že jinam ne a že tu nejsou karty).
 * Místa se posílají od začátku (vypnout jde v Nastavení, Pomáhat s mapou obchodů; appka o tom jednou dá vědět
 * pruhem a do té doby nic nepošle), nejvýš jednou za 6 hodin: obchod (id
 * řetězce z mapy, u vlastní karty jen název bez delších čísel), místo zaokrouhlené na zhruba 11 m, kolikrát
 * tam karta byla ukázaná, poslední den a vzdálenost k nejbližší známé prodejně. Nikdy čísla karet, poznámky,
 * účty, Wi-Fi ani časy ukázání. Telefon se pozná jen náhodným číslem instalace, ne ničím z telefonu.
 * Podnět odejde, jen když ho člověk napíše a odešle; přidá se k němu verze appky, Androidu a model telefonu.
 * Bez internetu podnět počká ve frontě a místa do příště.
 */
final class Sber {
    private static final String SBER = Verze.STRANKA + "api/sber";
    private static final String PODNET = Verze.STRANKA + "api/podnet";
    private static final String PREFS = "sber";
    private static final long INTERVAL = 6 * 3600_000L;
    private static final long PO_CHYBE = 15 * 60_000L;
    private static final int DAVKA = 100;      // míst v jedné dávce (server vezme 200 a 32 kB, tohle je s rezervou)
    private static final int DAVEK = 5;        // dávek za jedno odeslání
    private static final int PAMET = 2000;     // kolik odeslaných míst si pamatuje, ať je neposílá znovu
    private static final int FRONTA = 20;      // podnětů čekajících na internet
    private static final AtomicBoolean mistaBezi = new AtomicBoolean();
    private static final AtomicBoolean frontaBezi = new AtomicBoolean();

    private Sber() { }

    /** Zapnuto je i bez volby (Adam chtěl sběr pro všechny), vypnutí v Nastavení se drží. */
    static boolean zapnuto(Context c) { return c.getSharedPreferences(PREFS, 0).getBoolean("zapnuto", true); }

    static void nastav(Context c, boolean ano) {
        // po zapnutí odejdou místa hned při dalším návratu do appky
        c.getSharedPreferences(PREFS, 0).edit().putBoolean("zapnuto", ano).remove("ok").remove("pokus").apply();
    }

    /** Náhodné číslo instalace (32 hex znaků): nic z telefonu, po přeinstalování appky je jiné. */
    static synchronized String id(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        String id = p.getString("id", "");
        if (id.matches("[0-9a-f]{32}")) return id;
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        StringBuilder s = new StringBuilder(32);
        for (byte x : b) s.append(String.format(Locale.ROOT, "%02x", x & 0xff));
        id = s.toString();
        p.edit().putString("id", id).apply();
        return id;
    }

    /** Je čas poslat místa? (appka pak teprve sestaví seznam) */
    static boolean cas(Context c) {
        if (!zapnuto(c) || mistaBezi.get()) return false;
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        long ted = System.currentTimeMillis(), ok = p.getLong("ok", 0), pokus = p.getLong("pokus", 0);
        // hodiny posunuté zpátky (ted < ok) by odeslání jinak odložily klidně o roky
        return !(ted >= ok && ted - ok < INTERVAL) && !(ted >= pokus && ted - pokus < PO_CHYBE);
    }

    /** Místa z appky ([{r, n, lat, lon, x, d, den}]) zkontroluje, a co ještě neodešlo, pošle na pozadí. */
    static void mista(Context c, String json) {
        if (!cas(c) || !mistaBezi.compareAndSet(false, true)) return;
        Context app = c.getApplicationContext();
        app.getSharedPreferences(PREFS, 0).edit().putLong("pokus", System.currentTimeMillis()).apply();
        new Thread(() -> {
            try {
                odesliMista(app, json);
            } finally {
                mistaBezi.set(false);
            }
        }).start();
    }

    private static void odesliMista(Context c, String json) {
        SharedPreferences p = c.getSharedPreferences(PREFS, 0);
        JSONObject byla = objekt(p.getString("odeslano", ""));
        List<JSONObject> nova = new ArrayList<>();
        List<String> klice = new ArrayList<>();
        try {
            JSONArray vstup = new JSONArray(json);
            for (int i = 0; i < vstup.length() && nova.size() < DAVKA * DAVEK; i++) {
                JSONObject m = misto(vstup.optJSONObject(i));
                if (m == null) continue;
                String k = m.getString("r") + "|" + m.getString("n") + "|" + m.getDouble("lat") + "|" + m.getDouble("lon");
                if (byla.optInt(k, -1) >= m.getInt("x") || klice.contains(k)) continue; // tohle už odešlo
                nova.add(m);
                klice.add(k);
            }
            String id = id(c), verze = verze(c);
            for (int od = 0; od < nova.size(); od += DAVKA) {
                if (!zapnuto(c)) return; // sdílení se mezitím vyplo
                int az = Math.min(nova.size(), od + DAVKA);
                JSONArray davka = new JSONArray();
                for (int i = od; i < az; i++) davka.put(nova.get(i));
                JSONObject telo = new JSONObject().put("v", 1).put("id", id).put("verze", verze).put("mista", davka);
                int kod = posli(SBER, telo.toString().getBytes(StandardCharsets.UTF_8));
                // 400 a 413 by se opakováním neopravily: dávka se přeskočí, ať nezablokuje ty další
                if (kod != 200 && kod != 400 && kod != 413) return; // zbytek příště
                for (int i = od; i < az; i++) byla.put(klice.get(i), nova.get(i).getInt("x"));
                p.edit().putString("odeslano", orizni(byla).toString()).apply();
            }
            p.edit().putLong("ok", System.currentTimeMillis()).apply();
        } catch (JSONException | RuntimeException e) {
            // neplatný seznam z appky: zkusí se příště
        }
    }

    /** Jedno místo: jen tyhle údaje, v Česku, nic navíc. Neplatné = null. */
    private static JSONObject misto(JSONObject m) throws JSONException {
        if (m == null) return null;
        String r = m.optString("r", "");
        if (!r.matches("[a-z0-9]{0,30}")) return null;
        String n = r.isEmpty() ? nazev(m.optString("n", "")) : "";
        if (r.isEmpty() && n.isEmpty()) return null;
        double lat = m.optDouble("lat", Double.NaN), lon = m.optDouble("lon", Double.NaN);
        if (!(lat >= 48.5 && lat <= 51.1 && lon >= 12 && lon <= 18.9)) return null;
        String den = m.optString("den", "");
        if (!den.matches("\\d{4}-\\d{2}-\\d{2}")) return null;
        int d = m.optInt("d", -1);
        return new JSONObject()
                .put("r", r)
                .put("n", n)
                .put("lat", Math.round(lat * 1e4) / 1e4)
                .put("lon", Math.round(lon * 1e4) / 1e4)
                .put("x", Math.max(0, Math.min(1000, m.optInt("x", 0))))
                .put("d", d < 0 ? -1 : Math.min(d, 100_000))
                .put("den", den);
    }

    /** Název vlastní karty bez čísel (4 a víc číslic za sebou), e-mailů a řídicích znaků, nejvýš 60 znaků. */
    private static String nazev(String s) {
        s = s.replaceAll("\\p{Cntrl}", " ").replaceAll("\\S*@\\S*", " ").replaceAll("\\d[\\d\\s-]{2,}\\d", " ")
                .replaceAll("\\s+", " ").trim();
        return s.length() > 60 ? s.substring(0, 60).trim() : s;
    }

    /** Pamatuje si jen posledních PAMET odeslaných míst (pořadí vložení drží JSONObject v Androidu). */
    private static JSONObject orizni(JSONObject o) throws JSONException {
        int navic = o.length() - PAMET;
        if (navic <= 0) return o;
        JSONObject z = new JSONObject();
        Iterator<String> it = o.keys();
        for (int i = 0; it.hasNext(); i++) {
            String k = it.next();
            if (i >= navic) z.put(k, o.get(k));
        }
        return z;
    }

    /**
     * Podnět napsaný v appce ({druh, text, kontakt}). Hned zkusí odeslat; hotovo dostane "odeslano", "fronta",
     * když není internet nebo server (odejde při dalším otevření appky), nebo "chyba", když ho server odmítl.
     * Neplatný podnět vrátí false.
     */
    static boolean podnet(Context c, String json, Consumer<String> hotovo) {
        JSONObject telo;
        try {
            JSONObject j = new JSONObject(json);
            String druh = j.optString("druh", "jine");
            String text = j.optString("text", "").replace("\u0000", "").trim();
            String kontakt = j.optString("kontakt", "").replaceAll("\\p{Cntrl}", " ").trim();
            if (text.isEmpty()) return false;
            telo = new JSONObject()
                    .put("v", 1)
                    .put("id", id(c))
                    .put("verze", verze(c))
                    .put("android", Build.VERSION.RELEASE)
                    .put("model", Build.MODEL)
                    .put("druh", druh.matches("karta|chyba|jine") ? druh : "jine")
                    .put("text", text.length() > 2000 ? text.substring(0, 2000) : text)
                    .put("kontakt", kontakt.length() > 200 ? kontakt.substring(0, 200) : kontakt);
        } catch (JSONException e) {
            return false;
        }
        Context app = c.getApplicationContext();
        synchronized (Sber.class) {
            JSONArray f = pole(app.getSharedPreferences(PREFS, 0).getString("fronta", ""));
            f.put(telo.toString());
            while (f.length() > FRONTA) f.remove(0);
            app.getSharedPreferences(PREFS, 0).edit().putString("fronta", f.toString()).apply();
        }
        String muj = telo.toString();
        dorucFrontu(app, muj, hotovo);
        return true;
    }

    /** Pošle podněty čekající ve frontě (po otevření appky). */
    static void dorucFrontu(Context c) {
        if (pole(c.getSharedPreferences(PREFS, 0).getString("fronta", "")).length() == 0) return;
        dorucFrontu(c.getApplicationContext(), null, null);
    }

    private static void dorucFrontu(Context c, String muj, Consumer<String> hotovo) {
        if (!frontaBezi.compareAndSet(false, true)) {
            if (hotovo != null) hotovo.accept("fronta"); // odchází jiný podnět, tenhle půjde příště
            return;
        }
        new Thread(() -> {
            String mujStav = "fronta";
            try {
                SharedPreferences p = c.getSharedPreferences(PREFS, 0);
                while (true) {
                    String dalsi;
                    synchronized (Sber.class) {
                        JSONArray f = pole(p.getString("fronta", ""));
                        if (f.length() == 0) break;
                        dalsi = f.optString(0, "");
                    }
                    int kod = posli(PODNET, dalsi.getBytes(StandardCharsets.UTF_8));
                    // 200 je doručeno, 400, 413 a 422 server podnět odmítl a opakování nepomůže. Ostatní (bez
                    // internetu, 403 a 404 při změně nasazení, 429 limit, 5xx) zůstane ve frontě na příště.
                    boolean odmitnut = kod == 400 || kod == 413 || kod == 422;
                    boolean pryc = (kod >= 200 && kod < 300) || odmitnut;
                    if (!pryc) break;
                    synchronized (Sber.class) {
                        JSONArray f = pole(p.getString("fronta", ""));
                        if (f.length() > 0 && dalsi.equals(f.optString(0, ""))) f.remove(0);
                        p.edit().putString("fronta", f.toString()).apply();
                    }
                    if (dalsi.equals(muj)) mujStav = odmitnut ? "chyba" : "odeslano";
                }
            } finally {
                frontaBezi.set(false);
                if (hotovo != null) hotovo.accept(mujStav);
            }
        }).start();
    }

    /** POST JSON na jednu ze dvou adres výše; vrátí kód odpovědi, bez internetu -1. Odpověď nečte. */
    private static int posli(String adresa, byte[] telo) {
        HttpURLConnection s = null;
        try {
            s = (HttpURLConnection) new URL(adresa).openConnection();
            s.setConnectTimeout(5000);
            s.setReadTimeout(5000);
            s.setUseCaches(false);
            s.setInstanceFollowRedirects(false);
            s.setRequestMethod("POST");
            s.setDoOutput(true);
            s.setFixedLengthStreamingMode(telo.length);
            s.setRequestProperty("User-Agent", "Karticka");
            s.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = s.getOutputStream()) {
                o.write(telo);
            }
            return s.getResponseCode();
        } catch (IOException | RuntimeException e) {
            return -1;
        } finally {
            if (s != null) s.disconnect();
        }
    }

    private static String verze(Context c) {
        try {
            String v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    private static JSONObject objekt(String s) {
        try {
            return s.isEmpty() ? new JSONObject() : new JSONObject(s);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static JSONArray pole(String s) {
        try {
            return s.isEmpty() ? new JSONArray() : new JSONArray(s);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }
}
