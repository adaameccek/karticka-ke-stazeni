package cz.adamhorak.karticka;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dlaždice mapy okolí (OpenStreetMap). Stránka appky má síť zakázanou, takže mapa chce /dlazdice/z/x/y.png
 * od Klienta v Hlavni.java a ten ji vezme odsud: z mezipaměti v telefonu, jinak ji stáhne z tile.openstreetmap.org.
 * Ven jde jen číslo dlaždice (tedy zhruba která čtvrť je na mapě) a jen když je mapa otevřená. Žádné karty,
 * poloha telefonu ani údaje o něm. Mezipaměť drží dlaždice 30 dní (OSM chce aspoň 7) a nejvýš asi 40 MB.
 * Volá se z vlákna WebView na pozadí, takže smí čekat na síť. build.py hlídá, že nic jiného ven nejde.
 */
final class Dlazdice {
    static final String SERVER = "https://tile.openstreetmap.org/";
    private static final Pattern CESTA = Pattern.compile("^/dlazdice/(\\d{1,2})/(\\d{1,7})/(\\d{1,7})\\.png$");
    private static final long STARI = 30L * 24 * 3600_000L;
    private static final long MISTO = 40L * 1024 * 1024;
    private static final int NEJVIC = 512 * 1024;
    private static volatile boolean uklizeno;

    private Dlazdice() { }

    /** PNG dlaždice, nebo null (mapa pak kreslí kruhy a obchody na prázdném podkladu). */
    static byte[] dej(Context c, String cesta) {
        Matcher m = CESTA.matcher(cesta);
        if (!m.matches()) return null;
        int z = Integer.parseInt(m.group(1)), x = Integer.parseInt(m.group(2)), y = Integer.parseInt(m.group(3));
        if (z > 19 || x >= (1 << z) || y >= (1 << z)) return null;
        File slozka = new File(c.getCacheDir(), "dlazdice");
        File f = new File(slozka, z + "_" + x + "_" + y + ".png");
        if (f.isFile() && Math.abs(System.currentTimeMillis() - f.lastModified()) < STARI) {
            byte[] b = cti(f);
            if (b != null) return b;
        }
        byte[] b = stahni(z, x, y);
        if (b == null) return f.isFile() ? cti(f) : null; // bez internetu poslouží i starší
        if (!uklizeno) { uklizeno = true; uklid(slozka); }
        slozka.mkdirs();
        // přes dočasný soubor: dvě stejné dlaždice naráz si soubor nepřepíšou napůl
        File tmp = new File(slozka, f.getName() + "." + Thread.currentThread().getId() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(b);
        } catch (IOException e) {
            tmp.delete();
            return b;
        }
        if (!tmp.renameTo(f)) tmp.delete();
        return b;
    }

    private static byte[] stahni(int z, int x, int y) {
        HttpURLConnection s = null;
        try {
            s = (HttpURLConnection) new URL(SERVER + z + "/" + x + "/" + y + ".png").openConnection();
            s.setConnectTimeout(6000);
            s.setReadTimeout(8000);
            s.setUseCaches(false);
            s.setInstanceFollowRedirects(false);
            s.setRequestProperty("User-Agent", "Karticka (+https://karticka.adamhorak.cz)");
            if (s.getResponseCode() != 200) return null;
            String typ = s.getContentType();
            if (typ == null || !typ.startsWith("image/png")) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = s.getInputStream()) {
                byte[] b = new byte[8192];
                for (int n; (n = in.read(b)) > 0; ) {
                    out.write(b, 0, n);
                    if (out.size() > NEJVIC) return null; // dlaždice má desítky kB
                }
            }
            return out.size() > 0 ? out.toByteArray() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            if (s != null) s.disconnect();
        }
    }

    private static byte[] cti(File f) {
        if (f.length() <= 0 || f.length() > NEJVIC) return null;
        byte[] b = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) {
            int i = 0;
            for (int n; i < b.length && (n = in.read(b, i, b.length - i)) > 0; ) i += n;
            return i == b.length ? b : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Jednou za spuštění: pryč staré dlaždice a zbytky, a když je jich přes 40 MB, nejdřív ty nejstarší. */
    private static void uklid(File slozka) {
        File[] s = slozka.listFiles();
        if (s == null) return;
        long ted = System.currentTimeMillis(), celkem = 0;
        for (File f : s) {
            if (f.getName().endsWith(".tmp") || Math.abs(ted - f.lastModified()) > 2 * STARI) f.delete();
            else celkem += f.length();
        }
        if (celkem <= MISTO) return;
        s = slozka.listFiles();
        if (s == null) return;
        Arrays.sort(s, Comparator.comparingLong(File::lastModified));
        for (File f : s) {
            if (celkem <= MISTO * 3 / 4) break;
            celkem -= f.length();
            f.delete();
        }
    }
}
