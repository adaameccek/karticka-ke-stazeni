package cz.adamhorak.karticka;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Karty v souboru karty.json v soukromé složce appky (nikam jinam se nekopírují). */
final class Data {
    private Data() {}

    static final String PREFS = "karticka";

    private static File soubor(Context c) { return new File(c.getFilesDir(), "karty.json"); }

    private static File predchozi(Context c) { return new File(c.getFilesDir(), "karty.json.bak"); }

    /** Pro widget a okna: co nejde přečíst, je jako prázdné. */
    static String nacti(Context c) {
        String t = nactiPresne(c);
        return t == null ? "" : t;
    }

    /** Pro appku: "" = karty ještě nejsou, null = soubor je, ale nejde přečíst (a nesmí se přepsat prázdným). */
    static String nactiPresne(Context c) {
        File f = soubor(c);
        // karty.json chybí jen po pádu mezi dvěma přejmenováními v uloz: poslední uložené je v předchozí verzi
        if (!f.exists()) f = predchozi(c);
        return f.exists() ? cti(f) : "";
    }

    /** Předchozí uložená verze karet, "" když není nebo nejde přečíst. */
    static String nactiPredchozi(Context c) {
        File f = predchozi(c);
        String t = f.exists() ? cti(f) : "";
        return t == null ? "" : t;
    }

    private static String cti(File f) {
        try (InputStream in = new FileInputStream(f)) {
            return precti(in, 4_000_000);
        } catch (IOException e) {
            return null;
        }
    }

    static synchronized boolean uloz(Context c, String json) {
        File f = soubor(c), b = predchozi(c), tmp = new File(c.getFilesDir(), "karty.json.tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(json.getBytes(StandardCharsets.UTF_8));
            o.getFD().sync();
        } catch (IOException e) {
            return false;
        }
        boolean odlozeno = f.exists() && f.renameTo(b);
        if (tmp.renameTo(f)) return true;
        if (odlozeno) b.renameTo(f);
        return false;
    }

    static String precti(InputStream in, int limit) throws IOException {
        return new String(bajty(in, limit), StandardCharsets.UTF_8);
    }

    private static byte[] bajty(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) {
            o.write(b, 0, n);
            if (o.size() > limit) throw new IOException("soubor je moc velký");
        }
        return o.toByteArray();
    }

    /**
     * Soubor s kartami k načtení: záloha Kartičky, CSV (Catima, Klarna, Excel) nebo seznam. Z exportu Catimy (ZIP)
     * vezme první CSV, JSON nebo TXT uvnitř. Co není UTF-8, je nejspíš CSV z českého Excelu (Windows-1250).
     */
    static String textSouboru(InputStream in, int limit) throws IOException {
        byte[] b = bajty(in, limit);
        if (b.length > 4 && b[0] == 'P' && b[1] == 'K' && b[2] == 3 && b[3] == 4) {
            byte[] uvnitr = null;
            try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(b))) {
                ZipEntry e;
                while ((e = z.getNextEntry()) != null) {
                    String n = e.getName().toLowerCase(Locale.ROOT);
                    if (!e.isDirectory() && (n.endsWith(".csv") || n.endsWith(".json") || n.endsWith(".txt"))) {
                        uvnitr = bajty(z, limit);
                        break;
                    }
                }
            }
            if (uvnitr == null) return "";
            b = uvnitr;
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(b)).toString();
        } catch (CharacterCodingException e) {
            return new String(b, Charset.forName("windows-1250"));
        }
    }

    /** Karta, kterou appka naposledy ukázala (pro widget), nebo null. */
    static JSONObject posledni(Context c) {
        return karta(c, c.getSharedPreferences(PREFS, 0).getString("posledni", null));
    }

    /** Karta podle id, nebo null. */
    static JSONObject karta(Context c, String id) {
        if (id == null) return null;
        try {
            JSONArray karty = new JSONObject(nacti(c)).getJSONArray("karty");
            for (int i = 0; i < karty.length(); i++) {
                JSONObject k = karty.getJSONObject(i);
                if (id.equals(k.optString("id"))) return k;
            }
        } catch (Exception e) {
            // prázdný nebo poškozený soubor
        }
        return null;
    }
}
