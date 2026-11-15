package cz.adamhorak.karticka;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Kterou kartu má widget ukázat podle polohy. Stejná pravidla jako okoli() v app.js
 * (stejné vzdálenosti i pořadí), ať widget a appka ukazují v obchodě totéž.
 */
final class Vyber {
    private Vyber() {}

    static final long STARA = 20 * 60_000L; // starší poloha se nebere ani na řazení (jako v appce)
    /**
     * Kartu obchodu smí přepnout a „tady" smí říct jen aktuální poloha (Adam 9.11.2026: nechce, aby widget bral
     * starou). Starší slouží jen na řazení a vzdálenost k nejbližšímu obchodu.
     */
    static final long AKTUALNI = 3 * 60_000L;
    /** Jak dlouho se karta vybraná polohou drží na místě, kde se ukázala (jako heroKde v app.js). */
    private static final long NA_MISTE = 10 * 60_000L;
    /**
     * Po téhle době bez jakékoli nové polohy widget pustí kartu, kterou poloha vybrala. Delší než nejdelší krok
     * hlídání na pozadí (Hlidka: 10 minut plus zdržení systému), ať karta v obchodě neproblikává.
     */
    private static final long BEZ_POLOHY = 15 * 60_000L;
    /**
     * Kartu vybranou ručně (ukázaná v appce, zvolená v seznamu u widgetu) widget drží 10 minut, pak zase vybírá
     * poloha sama (Adam chtěl, ať se režim podle polohy po krátké chvíli vrátí). Na zaplacení i s frontou to stačí.
     */
    static final long DRZI = 10 * 60_000L;
    /** Do téhle vzdálenosti widget mimo obchod ukáže kartu nejbližšího obchodu (dál už to „nejbližší" není). */
    private static final double BLIZKO = 50_000;
    /** Zápis karty pro widget: appka a výpočet z pozadí se nesmí přepsat navzájem. */
    static final Object ZAMEK = new Object();
    private static JSONObject prodejny, pokladny;
    private static Set<String> daleko;

    private static synchronized JSONObject prodejny(Context c) {
        if (prodejny == null) {
            try (InputStream in = c.getAssets().open("www/prodejny.json")) {
                prodejny = new JSONObject(Data.precti(in, 4_000_000));
            } catch (Exception e) {
                return new JSONObject(); // nezapamatovat, příště se načte znovu
            }
        }
        return prodejny;
    }

    /** Místa pokladen ze sběru (pokladny.json, jako v app.js): kartu řetězce tu ukázaly aspoň 3 telefony nebo je schválil Adam. */
    private static synchronized JSONObject pokladny(Context c) {
        if (pokladny == null) {
            try (InputStream in = c.getAssets().open("www/pokladny.json")) {
                pokladny = new JSONObject(Data.precti(in, 4_000_000));
            } catch (Exception e) {
                return new JSONObject();
            }
        }
        return pokladny;
    }

    /** Obchody v cizině bez prodejen na mapě (znacky.json "daleko", třeba Conad): v pořadí jdou dolů jako v appce. */
    private static synchronized Set<String> daleko(Context c) {
        if (daleko == null) {
            Set<String> s = new HashSet<>();
            try (InputStream in = c.getAssets().open("www/znacky.json")) {
                JSONArray a = new JSONObject(Data.precti(in, 1_000_000)).optJSONArray("daleko");
                for (int i = 0; a != null && i < a.length(); i++) s.add(a.optString(i));
            } catch (Exception e) {
                return s;
            }
            daleko = s;
        }
        return daleko;
    }

    /** Jako klicNazvu() v app.js: bez diakritiky, malými písmeny, jen písmena a číslice. */
    private static String klicNazvu(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("[\\u0300-\\u036f]", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static double vzdalenost(double a, double b, double c, double d) {
        double kx = Math.cos((a + c) * Math.PI / 360) * 111320;
        double dx = (d - b) * kx, dy = (c - a) * 110574;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double nejbliz(JSONArray body, double lat, double lon, boolean jenKolem) {
        double nej = Double.POSITIVE_INFINITY;
        if (body == null) return nej;
        for (int i = 0; i < body.length(); i++) {
            JSONArray m = body.optJSONArray(i);
            if (m == null || m.length() < 2) continue;
            double a = m.optDouble(0), b = m.optDouble(1);
            if (jenKolem && Math.abs(a - lat) > 0.3) continue;
            double v = vzdalenost(lat, lon, a, b);
            if (v < nej) nej = v;
        }
        return nej;
    }

    private static JSONArray body(JSONObject k, JSONObject mapa) {
        String retezec = k.optString("retezec", "");
        return retezec.isEmpty() ? null : mapa.optJSONArray(retezec);
    }

    /** Jako vzdalenostKarty() v app.js: nejbližší prodejna řetězce, pokladna nebo vlastní místo; daleko v cizině i ta nejbližší vůbec. */
    private static double vzdalenostKarty(JSONObject k, JSONObject mapa, JSONObject pokl, double lat, double lon) {
        JSONArray body = body(k, mapa);
        double v = Math.min(nejbliz(body, lat, lon, true), nejbliz(k.optJSONArray("mista"), lat, lon, false));
        v = Math.min(v, nejbliz(body(k, pokl), lat, lon, true));
        if (v == Double.POSITIVE_INFINITY && body != null && body.length() > 0) v = nejbliz(body, lat, lon, false);
        return v;
    }

    private static double dosah(double p) { return 120 + Math.min(p, 200); }

    /**
     * Sítě Wi-Fi kolem teď jako wifiTed() v app.js: {BSSID bez dvojteček: dBm}, jen výsledky do 2 minut a aspoň 3 sítě,
     * jinak null. Telefon sítě jen vidí, nepřipojuje se a nic neposílá. O nové hledání žádá Sledovani.
     */
    static Map<String, Integer> wifi(Context c) {
        if (c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null;
        try {
            WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return null;
            long ted = SystemClock.elapsedRealtime() * 1000; // ScanResult.timestamp je v µs od startu
            Map<String, Integer> s = new HashMap<>();
            for (ScanResult r : wm.getScanResults()) {
                if (r.BSSID == null || ted - r.timestamp > 120_000_000L) continue;
                String b = r.BSSID.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-f]", "");
                if (b.length() == 12 && r.level > -100 && r.level < 0) s.put(b, r.level);
            }
            return s.size() >= 3 ? s : null;
        } catch (RuntimeException e) {
            return null; // bez oprávnění nebo bez Wi-Fi: rozhodne poloha
        }
    }

    /** Nejlepší shoda uložených otisků karty se sítěmi teď (shodaWifi() v app.js), 0 bez otisků nebo bez sítí. */
    private static double shodaWifi(JSONObject k, Map<String, Integer> S) {
        JSONArray otisky = S == null ? null : k.optJSONArray("wifi");
        double nej = 0;
        for (int i = 0; otisky != null && i < otisky.length(); i++) {
            JSONObject o = otisky.optJSONObject(i);
            if (o == null) continue;
            Map<String, Integer> F = new HashMap<>();
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String b = it.next();
                F.put(b, o.optInt(b, -100));
            }
            double x = Pravidla.podobnost(F, S);
            if (x > nej) nej = x;
        }
        return nej;
    }

    /** Stojí člověk tam, kde widget kartu vybral (hLat, hLon, hCas v prefs)? Jako heroKde v app.js. */
    private static boolean naMiste(SharedPreferences sp, double lat, double lon, double p) {
        return sp.contains("hLat") && System.currentTimeMillis() - sp.getLong("hCas", 0) < NA_MISTE
                && vzdalenost(lat, lon, sp.getFloat("hLat", 0), sp.getFloat("hLon", 0)) <= Math.max(60, p);
    }

    private static JSONArray karty(Context c) {
        try {
            return new JSONObject(Data.nacti(c)).getJSONArray("karty");
        } catch (Exception e) {
            return null;
        }
    }

    /** Vzdálenost v metrech k nejbližšímu obchodu kterékoli karty (podle toho se hlídání zrychluje). */
    static double kObchodu(Context c, double lat, double lon) {
        JSONArray karty = karty(c);
        if (karty == null) return Double.POSITIVE_INFINITY;
        JSONObject mapa = prodejny(c), pokl = pokladny(c);
        double nej = Double.POSITIVE_INFINITY;
        for (int i = 0; i < karty.length(); i++) {
            JSONObject k = karty.optJSONObject(i);
            if (k == null) continue;
            double v = Math.min(nejbliz(body(k, mapa), lat, lon, true), nejbliz(k.optJSONArray("mista"), lat, lon, false));
            v = Math.min(v, nejbliz(body(k, pokl), lat, lon, true));
            if (v < nej) nej = v;
        }
        return nej;
    }

    /** Nejlepší karta pro tohle místo, nebo null, když tu žádný obchod s kartou není. */
    static String nejlepsi(Context c, double lat, double lon, float presnostM, String drzena) {
        JSONArray karty = karty(c);
        if (karty == null) return null;
        JSONObject mapa = prodejny(c), pokl = pokladny(c);
        double p = presnostM;
        Map<String, Integer> S = wifi(c);
        JSONObject nej = null, drzeny = null;
        long nejU = 0, drzenyU = 0;
        for (int i = 0; i < karty.length(); i++) {
            JSONObject k = karty.optJSONObject(i);
            if (k == null) continue;
            double m = nejbliz(k.optJSONArray("mista"), lat, lon, false);
            double kp = nejbliz(body(k, pokl), lat, lon, true);
            double v = Math.min(Math.min(nejbliz(body(k, mapa), lat, lon, true), m), kp);
            if (!(v <= dosah(p))) continue;
            long u = Pravidla.uroven(shodaWifi(k, S), m, kp, v, p);
            if (nej == null || lepsi(k, u, nej, nejU)) { nej = k; nejU = u; }
            if (k.optString("id").equals(drzena)) { drzeny = k; drzenyU = u; }
        }
        if (nej == null) return null;
        // ukázaná karta se drží jako v appce (drzi() v app.js): stejně dobrá, nebo člověk stojí, kde se ukázala
        if (drzeny != null && Pravidla.drzi(drzenyU, nejU,
                naMiste(c.getSharedPreferences(Data.PREFS, 0), lat, lon, p))) return drzeny.optString("id");
        return nej.optString("id");
    }

    private static boolean lepsi(JSONObject k, long u, JSONObject nej, long nejU) {
        if (u != nejU) return u < nejU;
        int a = k.optInt("pouzito"), b = nej.optInt("pouzito");
        if (a != b) return a > b;
        return k.optLong("naposledy") > nej.optLong("naposledy");
    }

    /**
     * Poloha pro pořadí karet na widgetu a v jeho seznamu (Nabidka): poslední, kterou zjistila appka, seznam nebo hlídání.
     * Jen v telefonu, nikam neodchází. Starší poloha novější nepřepíše.
     */
    private static void zapamatuj(SharedPreferences sp, SharedPreferences.Editor e, Location l, long kdy) {
        if (kdy <= sp.getLong("wCas", 0)) return;
        e.putFloat("wLat", (float) l.getLatitude()).putFloat("wLon", (float) l.getLongitude())
                .putFloat("wPresnost", l.hasAccuracy() ? l.getAccuracy() : 999).putLong("wCas", kdy);
    }

    /**
     * Nová poloha (z pozadí nebo z otevřené appky): když je u obchodu s kartou, widget přepne na jeho kartu;
     * když je i s nepřesností polohy daleko od všech obchodů s kartou, widget kartu pustí a ukáže kartu nejbližšího
     * obchodu (poradi). Ručně vybranou kartu na tom místě do DRZI nepřebíjí. Vrací true, když se karta změnila.
     */
    static boolean naPolohu(Context c, Location l) {
        if (l == null) return false;
        long vek = (SystemClock.elapsedRealtimeNanos() - l.getElapsedRealtimeNanos()) / 1_000_000L;
        if (vek > STARA) return false;
        float presnost = l.hasAccuracy() ? l.getAccuracy() : 999;
        SharedPreferences sp = c.getSharedPreferences(Data.PREFS, 0);
        long kdy = System.currentTimeMillis() - vek;
        SharedPreferences.Editor kde = sp.edit();
        zapamatuj(sp, kde, l, kdy);
        kde.apply();
        if (vek > AKTUALNI) return false; // stará poloha kartu nepřepíná, jen řadí
        String ted = sp.getString("posledni", null);
        long appCas = sp.getLong("appCas", 0);
        if (ted != null && appCas > 0 && kdy - appCas < DRZI) {
            if (kdy <= appCas) return false; // poloha je starší než ručně vybraná karta
            // bez místa výběru se drží celou dobu, s ním jen na tom místě (člověk odešel: rozhodne poloha hned)
            if (!sp.contains("appLat") || vzdalenost(l.getLatitude(), l.getLongitude(),
                    sp.getFloat("appLat", 0), sp.getFloat("appLon", 0)) <= Math.max(80, presnost)) {
                return false;
            }
        }
        String id = nejlepsi(c, l.getLatitude(), l.getLongitude(), presnost, ted);
        if (id == null) {
            // mezi dosahem a dosahem + nepřesností se neví, jestli člověk v obchodě není: karta zůstane
            if (ted == null || !(kObchodu(c, l.getLatitude(), l.getLongitude()) > dosah(presnost) + presnost)) {
                return false;
            }
        } else if (id.equals(ted)) {
            return false;
        }
        synchronized (ZAMEK) {
            // mezitím kartu ukázala appka nebo ji přepočítala jiná poloha: platí ta novější
            if (sp.getLong("appCas", 0) != appCas || !Objects.equals(sp.getString("posledni", null), ted)) return false;
            // nikde je po starší verzi (widget psal „Tady není obchod s tvou kartou"); volba člověka už neplatí, jinak
            // by po návratu na její místo chránila kartu, kterou mezitím vybrala poloha
            SharedPreferences.Editor e = sp.edit().remove("nikde").remove("appCas").remove("appLat").remove("appLon");
            if (id == null) e.remove("posledni").remove("hCas");
            else e.putString("posledni", id).putLong("kartaCas", System.currentTimeMillis())
                    .putFloat("hLat", (float) l.getLatitude()).putFloat("hLon", (float) l.getLongitude())
                    .putLong("hCas", System.currentTimeMillis());
            e.apply();
        }
        return true;
    }

    /**
     * Ručně vybraná karta se už nedrží (uplynulo DRZI): rozhodne poslední poloha jako v naPolohu. U obchodu s kartou
     * ukáže jeho kartu, jinde kartu pustí a widget vezme první z poradi(). Volá ho Widget, když kreslí.
     */
    static void uvolni(Context c) {
        SharedPreferences sp = c.getSharedPreferences(Data.PREFS, 0);
        long ted = System.currentTimeMillis(), appCas = sp.getLong("appCas", 0);
        if (appCas == 0 || ted - appCas < DRZI) return;
        String drzena = sp.getString("posledni", null), id = null;
        if (sp.contains("wLat") && ted - sp.getLong("wCas", 0) <= AKTUALNI) {
            id = nejlepsi(c, sp.getFloat("wLat", 0), sp.getFloat("wLon", 0), sp.getFloat("wPresnost", 999), drzena);
        }
        synchronized (ZAMEK) {
            if (sp.getLong("appCas", 0) != appCas) return; // mezitím vybral člověk znovu
            SharedPreferences.Editor e = sp.edit().remove("appCas").remove("appLat").remove("appLon");
            if (id == null) e.remove("posledni");
            else if (!id.equals(drzena)) e.putString("posledni", id).putLong("kartaCas", ted);
            e.apply();
        }
    }

    /**
     * Kartu vybral člověk (ukázala ji appka, nebo ji zvolil v seznamu u widgetu): widget ji ukáže a na tom místě
     * ji do DRZI nepřebije poloha z pozadí. Poloha l je, kde to bylo (bez polohy null).
     */
    static void zvol(Context c, String id, Location l) {
        synchronized (ZAMEK) { // ať ji hned nepřepíše výpočet z pozadí, který běžel současně
            long ted = System.currentTimeMillis();
            SharedPreferences sp = c.getSharedPreferences(Data.PREFS, 0);
            SharedPreferences.Editor e = sp.edit()
                    .putString("posledni", id).remove("nikde").putLong("appCas", ted).putLong("kartaCas", ted);
            long vek = l == null ? Long.MAX_VALUE : Hlidka.vek(l);
            if (vek < STARA) {
                e.putFloat("appLat", (float) l.getLatitude()).putFloat("appLon", (float) l.getLongitude());
                e.putFloat("hLat", (float) l.getLatitude()).putFloat("hLon", (float) l.getLongitude()).putLong("hCas", ted);
                zapamatuj(sp, e, l, ted - vek);
            } else {
                e.remove("appLat").remove("appLon");
            }
            e.apply();
        }
    }

    /** Jak stará smí být poloha pro „Jsem v prodejně" na widgetu (ukázat tlačítko / uložit klepnutí). */
    private static final long TADY_UKAZAT = 3 * 60_000L, TADY_ULOZIT = 5 * 60_000L;

    /**
     * Poslední poloha z prefs, když je dost přesná (do 100 m jako Jsem v prodejně v appce) a nejvýš stara ms stará:
     * {lat, lon, přesnost}, jinak null.
     */
    private static double[] presnaPoloha(SharedPreferences sp, long stara) {
        if (!sp.contains("wLat") || System.currentTimeMillis() - sp.getLong("wCas", 0) > stara) return null;
        float p = sp.getFloat("wPresnost", 999);
        return p > 100 ? null : new double[] { sp.getFloat("wLat", 0), sp.getFloat("wLon", 0), p };
    }

    /** Do kdy je poslední poloha dost čerstvá na tlačítko Jsem v prodejně (Widget pak překreslí bez něj). */
    static long tadyDo(Context c) {
        return c.getSharedPreferences(Data.PREFS, 0).getLong("wCas", 0) + TADY_UKAZAT;
    }

    /**
     * Velký widget nabídne „Jsem v prodejně" (jako Taky tady v appce): poloha je čerstvá a přesná, obchod karty je
     * kousek, tohle místo v obchodě ještě potvrzené není (žádné vlastní místo ani pokladna ze sběru do 30 m) a obchod nemá potvrzená
     * aspoň 3 místa (u dlouhé řady pokladen je jich víc, pak už tlačítko nepřekáží).
     */
    static boolean lzePotvrdit(Context c, JSONObject k) {
        SharedPreferences sp = c.getSharedPreferences(Data.PREFS, 0);
        double[] t = k == null ? null : presnaPoloha(sp, TADY_UKAZAT);
        if (t == null || k.optString("kod").isEmpty()) return false;
        JSONArray mista = k.optJSONArray("mista");
        double kp = nejbliz(body(k, pokladny(c)), t[0], t[1], true);
        if (kp < 30) return false;
        if (!(Math.min(Math.min(nejbliz(body(k, prodejny(c)), t[0], t[1], true), nejbliz(mista, t[0], t[1], false)), kp) <= dosah(t[2]))) {
            return false;
        }
        int blizko = 0;
        for (int i = 0; mista != null && i < mista.length(); i++) {
            JSONArray m = mista.optJSONArray(i);
            if (m == null || m.length() < 2) continue;
            double v = vzdalenost(t[0], t[1], m.optDouble(0), m.optDouble(1));
            if (v < 30) return false;
            if (v < 150) blizko++;
        }
        return blizko < 3;
    }

    /**
     * Klepnutí na „Jsem v prodejně": místo se uloží ke kartě jako pridejMisto() v app.js (do 25 m od uloženého
     * se nepřidá, nejvýš 100 míst). Běží-li appka, uloží ho sama (data drží v paměti), jinak se zapíše rovnou
     * do dat. Vrátí název karty, nebo null, když poloha už není dost čerstvá a přesná.
     */
    static String potvrd(Context c, String id) {
        double[] t = presnaPoloha(c.getSharedPreferences(Data.PREFS, 0), TADY_ULOZIT);
        JSONObject k = Data.karta(c, id);
        if (t == null || k == null) return null;
        double lat = Math.round(t[0] * 1e5) / 1e5, lon = Math.round(t[1] * 1e5) / 1e5;
        if (Hlavni.potvrdZvenku(id, lat, lon)) return k.optString("nazev");
        synchronized (Data.class) { // jako Data.uloz: mezi čtením a zápisem nic jiného nezapíše
            try {
                JSONObject d = new JSONObject(Data.nacti(c));
                JSONArray karty = d.getJSONArray("karty");
                for (int i = 0; i < karty.length(); i++) {
                    JSONObject x = karty.optJSONObject(i);
                    if (x == null || !id.equals(x.optString("id"))) continue;
                    JSONArray mista = x.optJSONArray("mista");
                    if (mista == null) x.put("mista", mista = new JSONArray());
                    if (nejbliz(mista, lat, lon, false) < 25) return x.optString("nazev");
                    mista.put(new JSONArray().put(lat).put(lon));
                    if (mista.length() > 100) mista.remove(0);
                    Data.uloz(c, d.toString());
                    return x.optString("nazev");
                }
            } catch (Exception e) {
                // poškozená data nepřepisovat
            }
        }
        return null;
    }

    /** Karta v pořadí pro widget a jeho seznam. v: metry k nejbližší prodejně podle čerstvé polohy, jinak nekonečno. */
    static final class Polozka {
        final JSONObject k;
        final double v;
        /** Obchod je tady (v dosahu polohy jako v okoli() v app.js). */
        final boolean tady;

        Polozka(JSONObject k, double v, boolean tady) { this.k = k; this.v = v; this.tady = tady; }

        /** Vzdálenost k ukázání vedle názvu: jen u karty nejbližšího obchodu mimo obchod, ne „1 200 km". */
        String kde() { return !tady && v <= BLIZKO ? km(v) : null; }
    }

    /** Do kdy platí uložená poloha jako čerstvá (pak se pořadí přepočítá podle Nastavení), 0 bez polohy. */
    static long cerstvaDo(Context c) {
        SharedPreferences sp = c.getSharedPreferences(Data.PREFS, 0);
        if (!sp.contains("wLat")) return 0;
        long cas = sp.getLong("wCas", 0), ted = System.currentTimeMillis();
        return ted < cas + AKTUALNI ? cas + AKTUALNI : cas + STARA; // napřed přestane platit „tady", pak i vzdálenost
    }

    /** Dodalo hlídání polohu v posledních minutách (smí držet kartu, kterou poloha vybrala)? */
    static boolean aktualni(Context c) {
        SharedPreferences sp = c.getSharedPreferences(Data.PREFS, 0);
        return sp.contains("wLat") && System.currentTimeMillis() - sp.getLong("wCas", 0) <= BEZ_POLOHY;
    }

    /** Do kdy poslední poloha drží kartu, kterou vybrala (Widget pak překreslí). */
    static long aktualniDo(Context c) {
        return c.getSharedPreferences(Data.PREFS, 0).getLong("wCas", 0) + BEZ_POLOHY;
    }

    /**
     * Všechny karty v pořadí pro widget: s čerstvou polohou nahoře obchody tady (jako okoli() v app.js), jinak karta
     * nejbližšího obchodu do 50 km. Pod nimi karty podle pořadí z Nastavení appky (podle místa, chytře, podle
     * používání, abecedně, vlastní) se stejnými pravidly jako serazene() v app.js; pořadí podle místa a chytré berou
     * i starší polohu, na město stačí.
     * Blízké obchody jsou nahoře v každém pořadí: widget je od toho, aby ukázal kartu obchodu, kde člověk je.
     */
    static List<Polozka> poradi(Context c) {
        List<Polozka> vysledek = new ArrayList<>();
        JSONObject data;
        try {
            data = new JSONObject(Data.nacti(c));
        } catch (Exception e) {
            return vysledek;
        }
        JSONArray pole = data.optJSONArray("karty");
        if (pole == null) return vysledek;
        List<JSONObject> karty = new ArrayList<>();
        for (int i = 0; i < pole.length(); i++) {
            JSONObject k = pole.optJSONObject(i);
            if (k != null && !k.optString("id").isEmpty()) karty.add(k);
        }
        if (karty.isEmpty()) return vysledek;

        SharedPreferences sp = c.getSharedPreferences(Data.PREFS, 0);
        long ted = System.currentTimeMillis();
        boolean mam = sp.contains("wLat") && sp.contains("wLon");
        boolean cerstva = mam && ted - sp.getLong("wCas", 0) <= STARA;
        boolean aktualni = mam && ted - sp.getLong("wCas", 0) <= AKTUALNI; // jen ta smí říct „obchod je tady"
        Map<String, Integer> S = aktualni ? wifi(c) : null;
        double lat = sp.getFloat("wLat", 0), lon = sp.getFloat("wLon", 0), p = sp.getFloat("wPresnost", 999);
        JSONObject mapa = prodejny(c), pokl = pokladny(c);
        Map<JSONObject, Double> v = new HashMap<>();
        if (mam) for (JSONObject k : karty) v.put(k, vzdalenostKarty(k, mapa, pokl, lat, lon));

        List<JSONObject> tady = new ArrayList<>();
        Map<JSONObject, Long> u = new HashMap<>();
        if (cerstva) {
            for (JSONObject k : karty) {
                double x = v.get(k);
                if (!aktualni || !(x <= dosah(p))) continue;
                tady.add(k);
                u.put(k, Pravidla.uroven(shodaWifi(k, S), nejbliz(k.optJSONArray("mista"), lat, lon, false),
                        nejbliz(body(k, pokl), lat, lon, true), x, p));
            }
            Collections.sort(tady, (a, b) -> {
                int r = Long.compare(u.get(a), u.get(b));
                if (r == 0) r = Integer.compare(b.optInt("pouzito"), a.optInt("pouzito"));
                if (r == 0) r = Long.compare(b.optLong("naposledy"), a.optLong("naposledy"));
                return r;
            });
            // první rozhodne úroveň (Wi-Fi, vybraná místa, mapa), další obchody tady od nejbližšího jako tadyNahore() v app.js
            if (tady.size() > 2) Collections.sort(tady.subList(1, tady.size()), (a, b) -> Double.compare(v.get(a), v.get(b)));
            if (tady.isEmpty()) {
                JSONObject nej = null;
                for (JSONObject k : karty) if (v.get(k) <= BLIZKO && (nej == null || v.get(k) < v.get(nej))) nej = k;
                if (nej != null) {
                    vysledek.add(new Polozka(nej, v.get(nej), false));
                    karty.remove(nej);
                }
            }
        }
        for (JSONObject k : tady) vysledek.add(new Polozka(k, v.get(k), true));
        karty.removeAll(tady);

        for (JSONObject k : podleNastaveni(c, data.optJSONObject("nastaveni"), karty, mam ? v : null)) {
            vysledek.add(new Polozka(k, cerstva ? v.get(k) : Double.POSITIVE_INFINITY, false));
        }
        return vysledek;
    }

    /** Pořadí z Nastavení appky jako serazene() v app.js (bez obchodů tady, ty jsou už nahoře). */
    private static List<JSONObject> podleNastaveni(Context c, JSONObject n, List<JSONObject> karty, Map<JSONObject, Double> v) {
        Collator cs = Collator.getInstance(new Locale("cs"));
        Comparator<JSONObject> jmenem = (a, b) -> cs.compare(a.optString("nazev"), b.optString("nazev"));
        Comparator<JSONObject> oblibene = (a, b) -> Boolean.compare(b.optBoolean("oblibena"), a.optBoolean("oblibena"));
        String poradi = n == null ? "" : n.optString("poradi");
        List<JSONObject> s = new ArrayList<>(karty);
        if (poradi.equals("pouziti")) {
            Collections.sort(s, oblibene.thenComparing((a, b) -> Integer.compare(b.optInt("pouzito"), a.optInt("pouzito")))
                    .thenComparing((a, b) -> Long.compare(b.optLong("naposledy"), a.optLong("naposledy"))).thenComparing(jmenem));
            return s;
        }
        if (poradi.equals("abeceda")) {
            Collections.sort(s, oblibene.thenComparing(jmenem));
            return s;
        }
        List<JSONObject> zbytek = s;
        List<JSONObject> vlastni = new ArrayList<>();
        JSONArray ids = n == null ? null : n.optJSONArray("vlastni");
        if (poradi.equals("vlastni") && ids != null) {
            // karty přidané po poskládání jdou na konec (podle místa)
            Map<String, JSONObject> podleId = new HashMap<>();
            for (JSONObject k : s) podleId.put(k.optString("id"), k);
            for (int i = 0; i < ids.length(); i++) {
                JSONObject k = podleId.remove(ids.optString(i));
                if (k != null) vlastni.add(k);
            }
            zbytek = new ArrayList<>(s);
            zbytek.removeAll(vlastni);
        }
        Set<String> ciz = daleko(c);
        // "chytre" bez příznaku misto je staré výchozí pořadí z doby před "misto": i to se řadí podle místa
        if (!(poradi.equals("chytre") && n.optBoolean("misto"))) {
            // podle místa jako podleMista() v app.js: od nejbližší prodejny po 10 m, stejně daleko oblíbené, pak používání
            Map<JSONObject, Long> pasmo = new HashMap<>();
            for (JSONObject k : zbytek) pasmo.put(k, pasmoMista(k, v, ciz));
            Collections.sort(zbytek, Comparator.<JSONObject>comparingLong(pasmo::get).thenComparing(oblibene)
                    .thenComparing((a, b) -> Integer.compare(b.optInt("pouzito"), a.optInt("pouzito")))
                    .thenComparing((a, b) -> Long.compare(b.optLong("naposledy"), a.optLong("naposledy"))).thenComparing(jmenem));
            vlastni.addAll(zbytek);
            return vlastni;
        }
        // chytře: každé další ukázání váží o něco míň, blízká prodejna přidá body, obchody v cizině jsou dole
        Map<JSONObject, double[]> sk = new HashMap<>();
        for (JSONObject k : zbytek) {
            double b = 2 * Math.log(1 + Math.max(0, k.optInt("pouzito"))) / Math.log(2), x = 1e12;
            JSONArray mista = k.optJSONArray("mista");
            if (k.optString("retezec", "").isEmpty() && (mista == null || mista.length() == 0)
                    && ciz.contains(klicNazvu(k.optString("nazev")))) {
                b -= 20;
            } else if (v != null) {
                double d = v.get(k);
                if (d == Double.POSITIVE_INFINITY) b -= 1; // karta bez prodejen (letenky, e-shop): kousek pod obchody v okolí
                else {
                    b += d < 500 ? 4 : d < 2000 ? 3 : d < 1e4 ? 2 : d < 5e4 ? 1 : d < 3e5 ? 0 : -3;
                    x = d;
                }
            }
            sk.put(k, new double[]{b, x});
        }
        Collections.sort(zbytek, oblibene.thenComparing((a, b) -> Double.compare(sk.get(b)[0], sk.get(a)[0]))
                .thenComparing((a, b) -> Double.compare(sk.get(a)[1], sk.get(b)[1])).thenComparing(jmenem));
        vlastni.addAll(zbytek);
        return vlastni;
    }

    /**
     * Místo karty pro pořadí podle místa jako mistoKarty() v app.js: vzdálenost nejbližší prodejny po 10 m,
     * karty bez prodejen nebo bez polohy pod obchody v okolí, obchody v cizině úplně dole.
     * Do 5.11.2026 pásma po 25 % vzdálenosti, kamarádovi to nepřišlo seřazené od nejbližšího.
     */
    private static long pasmoMista(JSONObject k, Map<JSONObject, Double> v, Set<String> ciz) {
        JSONArray mista = k.optJSONArray("mista");
        if (k.optString("retezec", "").isEmpty() && (mista == null || mista.length() == 0)
                && ciz.contains(klicNazvu(k.optString("nazev")))) {
            return 1_000_000_000_000L;
        }
        Double d = v == null ? null : v.get(k);
        if (d == null || !(d <= 3e5)) return 1_000_000_000L;
        return Math.round(d / 10);
    }

    /** Vzdálenost jako km() v app.js: 350 m, 1,2 km, 15 km. */
    static String km(double m) {
        if (m < 1000) return Math.max(10, Math.round(m / 10) * 10) + " m";
        if (m < 10000) return String.format(Locale.ROOT, "%.1f", m / 1000).replace('.', ',') + " km";
        return Math.round(m / 1000) + " km";
    }
}
