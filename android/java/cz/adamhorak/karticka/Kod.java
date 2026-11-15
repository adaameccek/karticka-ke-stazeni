package cz.adamhorak.karticka;

import android.graphics.Bitmap;
import android.util.Base64;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.Result;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Čárové a QR kódy: kreslení pro pokladnu a čtení z kamery nebo fotky (ZXing). */
final class Kod {
    private Kod() {}

    static final List<BarcodeFormat> FORMATY = Arrays.asList(
            BarcodeFormat.EAN_13, BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
            BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93,
            BarcodeFormat.CODABAR, BarcodeFormat.ITF, BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX, BarcodeFormat.PDF_417, BarcodeFormat.AZTEC);

    static boolean je2D(BarcodeFormat f) {
        return f == BarcodeFormat.QR_CODE || f == BarcodeFormat.DATA_MATRIX
                || f == BarcodeFormat.AZTEC || f == BarcodeFormat.PDF_417;
    }

    private static int tichaZona(BarcodeFormat f) {
        if (f == BarcodeFormat.QR_CODE) return 4;
        return je2D(f) ? 2 : 10;
    }

    /**
     * Bitmapa kódu, kde je každý modul celý násobek pixelu (ostré hrany pro čtečku u pokladny).
     * sirka je cílová šířka v pixelech, u čárových kódů je vyska výška čar, u 2D se nepoužije.
     */
    static Bitmap obrazek(String format, String text, int sirka, int vyska) throws Exception {
        BarcodeFormat f = BarcodeFormat.valueOf(format);
        BitMatrix m = matice(f, text);
        int mw = m.getWidth(), mh = m.getHeight(), q = tichaZona(f);
        boolean dva = je2D(f);
        int s = Math.max(1, sirka / (mw + 2 * q));
        int bw = (mw + 2 * q) * s;
        int bh = dva ? (mh + 2 * q) * s : Math.max(s * 20, vyska);
        if ((long) bw * bh > 6_000_000L) throw new IllegalArgumentException("moc velký");

        int[] px = new int[bw * bh];
        Arrays.fill(px, 0xFFFFFFFF);
        if (dva) {
            for (int y = 0; y < mh; y++)
                for (int x = 0; x < mw; x++)
                    if (m.get(x, y)) {
                        int x0 = (x + q) * s, y0 = (y + q) * s;
                        for (int yy = y0; yy < y0 + s; yy++)
                            Arrays.fill(px, yy * bw + x0, yy * bw + x0 + s, 0xFF000000);
                    }
        } else {
            int[] radek = new int[bw];
            Arrays.fill(radek, 0xFFFFFFFF);
            for (int x = 0; x < mw; x++)
                if (m.get(x, 0)) Arrays.fill(radek, (x + q) * s, (x + q + 1) * s, 0xFF000000);
            for (int y = 0; y < bh; y++) System.arraycopy(radek, 0, px, y * bw, bw);
        }
        return Bitmap.createBitmap(px, bw, bh, Bitmap.Config.ARGB_8888);
    }

    /** Moduly kódu bez okraje, jeden modul = jedna buňka. */
    private static BitMatrix matice(BarcodeFormat f, String text) throws Exception {
        Map<EncodeHintType, Object> h = new EnumMap<>(EncodeHintType.class);
        h.put(EncodeHintType.MARGIN, 0);
        boolean ascii = true;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) > 126) { ascii = false; break; }
        if (!ascii && je2D(f)) h.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        return new MultiFormatWriter().encode(text, f, 1, 1, h);
    }

    private static String klic;
    private static BitMatrix posledni;

    /** Matice pro widget: ten ji kreslí ve více velikostech a zkouší, kam se vejde, ať se kód nekóduje pokaždé znovu. */
    private static synchronized BitMatrix maticeWidgetu(BarcodeFormat f, String text) throws Exception {
        String k = f + "\n" + text;
        if (k.equals(klic)) return posledni;
        BitMatrix m = matice(f, text);
        klic = k;
        posledni = m;
        return m;
    }

    /**
     * Tichá zóna kolem kódu ve widgetu a v panelu u sluníčka, v modulech: nejmenší, kterou čtečky u pokladny spolehlivě
     * berou (Adam chtěl kolem kódu co nejmíň bílé). Čárový kód 7 místo 10 podle normy, QR 2 místo 4, PDF417 2 jako
     * norma, Data Matrix 1 jako norma, Aztec podle normy žádnou nepotřebuje, 1 je jen rezerva. V appce zůstává norma.
     */
    private static int zona(BarcodeFormat f) {
        if (f == BarcodeFormat.QR_CODE || f == BarcodeFormat.PDF_417) return 2;
        return je2D(f) ? 1 : 7;
    }

    /**
     * Šířka modulu a velikost obrázku kódu pro plochu w×h pixelů: {šířka modulu, šířka, výška}, nebo null,
     * když by modul měl pod 2 px (to žádná čtečka nepřečte). Ve widgetu (okoli < 0) je tichá zóna v obrázku,
     * kolem je černá nebo tapeta. V bílém panelu u sluníčka (okoli ≥ 0) ji dělá bílé okolí: v obrázku není, jen
     * kód je o tolik menší, aby se zóna vešla do plochy a okolí kolem ní (okoli px na každé straně, u 2D na výšku
     * jen 2 px). Čárový kód je přes celou šířku plochy a na výšku nejvýš px pixelů (ve widgetu taky nejvýš 3/4
     * šířky, vyšší čáry čtečce nepomůžou a widget by měl kolem kódu prázdno), 2D kód co největší, se stejnou
     * šířkou modulu na šířku i výšku, ve widgetu taky nejvýš px pixelů. Strop px hlídá, ať se obrázky všech velikostí
     * widgetu vejdou do paměti, kterou Android widgetu dá (Widget.pamet).
     */
    private static float[] rozmery(BarcodeFormat f, BitMatrix m, int w, int h, int okoli, int px) {
        if (w < 1 || h < 1) return null;
        boolean dva = je2D(f), venku = okoli >= 0;
        int z = zona(f), q = venku ? 0 : z;
        float s = (float) w / (m.getWidth() + 2 * q);
        if (dva) s = Math.min(s, (float) h / (m.getHeight() + 2 * q));
        if (venku) {
            s = Math.min(s, (float) (w + 2 * okoli) / (m.getWidth() + 2 * z));
            if (dva) s = Math.min(s, (float) (h + 4) / (m.getHeight() + 2 * z));
        } else if (dva) {
            s = Math.min(s, (float) Math.sqrt((double) px / ((m.getWidth() + 2 * q) * (m.getHeight() + 2 * q))));
        }
        if (s < 2) return null;
        int bw = dva ? Math.min(w, (int) Math.ceil((m.getWidth() + 2 * q) * s)) : w;
        int bh = dva ? Math.min(h, (int) Math.ceil((m.getHeight() + 2 * q) * s))
                : Math.max(1, Math.min(Math.min(h, px / w), venku ? h : w * 3 / 4));
        return new float[] { s, bw, bh };
    }

    /**
     * Velikost obrázku, který by doPlochy nakreslil do plochy w×h (nejvýš px pixelů): {šířka, výška}, nebo null,
     * když se kód nevejde.
     */
    static int[] rozmer(String format, String text, int w, int h, int px) throws Exception {
        BarcodeFormat f = BarcodeFormat.valueOf(format);
        float[] r = rozmery(f, maticeWidgetu(f, text), w, h, -1, px);
        return r == null ? null : new int[] { (int) r[1], (int) r[2] };
    }

    /**
     * Kód pro bílý panel u horního okraje obrazovky (sluníčko, Jas.java): černé moduly bez rámečku, tichou zónu dělá
     * bílý panel kolem (okoli px od plochy ke kraji panelu po stranách).
     */
    static Bitmap naBilou(String format, String text, int w, int h, int okoli) throws Exception {
        return kresli(format, text, w, h, 0, 0, false, okoli, 1_500_000);
    }

    /**
     * Kód co největší do plochy w×h pixelů (widget). Modul nemusí být celý počet pixelů (Adam chtěl kód roztažený
     * přes celou šířku, s celými pixely zbývalo u malého widgetu po stranách hodně místa): hrana každého modulu
     * padne na nejbližší pixel, takže se šířky modulů liší nejvýš o pixel, a to je na displeji telefonu
     * (pixel kolem 0,05 mm) pro čtečku u pokladny pod její rozlišovací schopností. Hrany zůstanou ostré.
     * Obrázek je nejvýš w×h a ImageView ho jen vycentruje bez škálování, takže se nerozmaže,
     * i když launcher dá ploše o pixel jinou velikost.
     * Obrázek je bílá plocha jen kolem kódu (Adam chtěl, ať svítí jen kód a malý okraj): po stranách tichá zóna
     * (zona), nad a pod čarami čárového kódu okrajY pixelů bílé. Rohy bílé plochy zakulatí poloměrem roh (Vzhled
     * widgetu), nejvýš tak, aby roh nesáhl na kód. Kolem je černý widget, nebo s pruhledne tapeta plochy: rohy
     * se pak vyhladí do průhledna. Vrátí null, když se kód nevejde. Černobílý obrázek je RGB_565, v RemoteViews
     * tak zabere polovinu paměti. Obrázek má nejvýš px pixelů (paměť widgetu, rozmery).
     */
    static Bitmap doPlochy(String format, String text, int w, int h, int okrajY, float roh, boolean pruhledne, int px)
            throws Exception {
        return kresli(format, text, w, h, okrajY, roh, pruhledne, -1, px);
    }

    private static Bitmap kresli(String format, String text, int w, int h, int okrajY, float roh, boolean pruhledne,
            int okoli, int strop) throws Exception {
        BarcodeFormat f = BarcodeFormat.valueOf(format);
        BitMatrix m = maticeWidgetu(f, text);
        float[] r = rozmery(f, m, w, h, okoli, strop);
        if (r == null) return null;
        float s = r[0];
        int bw = (int) r[1], bh = (int) r[2], mw = m.getWidth(), mh = m.getHeight();
        boolean dva = je2D(f);
        if ((long) bw * bh > 6_000_000L) throw new IllegalArgumentException("moc velký");
        // hrana modulu i na celém pixelu: kód přesně uprostřed a hrany na nejbližším pixelu
        int[] hx = new int[mw + 1], hy = new int[mh + 1];
        float x0 = (bw - mw * s) / 2f, y0 = (bh - mh * s) / 2f;
        for (int i = 0; i <= mw; i++) hx[i] = Math.round(x0 + i * s);
        for (int i = 0; i <= mh; i++) hy[i] = Math.round(y0 + i * s);

        int[] px = new int[bw * bh];
        Arrays.fill(px, 0xFFFFFFFF);
        int nad = 0;
        if (dva) {
            for (int y = 0; y < mh; y++)
                for (int x = 0; x < mw; x++)
                    if (m.get(x, y))
                        for (int yy = hy[y]; yy < hy[y + 1]; yy++)
                            Arrays.fill(px, yy * bw + hx[x], yy * bw + hx[x + 1], 0xFF000000);
        } else {
            // čáry nejméně 10 px vysoké, okraj nad a pod nimi jen z toho, co zbyde
            nad = Math.max(0, Math.min(okrajY, (bh - 10) / 2));
            int[] radek = new int[bw];
            Arrays.fill(radek, 0xFFFFFFFF);
            for (int x = 0; x < mw; x++)
                if (m.get(x, 0)) Arrays.fill(radek, hx[x], hx[x + 1], 0xFF000000);
            for (int y = nad; y < bh - nad; y++) System.arraycopy(radek, 0, px, y * bw, bw);
        }
        if (roh > 0) {
            // roh o poloměru p se dotkne bodu (a, b) od kraje, až když p > a + b + √(2ab): první modul zůstane celý
            float a = dva ? Math.min(hx[0], hy[0]) : hx[0], b = dva ? a : nad;
            zakulat(px, bw, bh, Math.min(roh, Math.min(Math.min(bw, bh) / 2f, a + b + (float) Math.sqrt(2 * a * b))),
                    pruhledne);
        }
        Bitmap b = Bitmap.createBitmap(px, bw, bh, pruhledne ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
        b.setDensity(Bitmap.DENSITY_NONE);
        return b;
    }

    /** Zakulatí rohy bílé plochy do černého okolí, nebo do průhledna, s vyhlazeným okrajem. */
    private static void zakulat(int[] px, int bw, int bh, float p, boolean pruhledne) {
        int n = (int) Math.ceil(p);
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                float dx = p - (x + 0.5f), dy = p - (y + 0.5f);
                float uvnitr = Math.max(0, Math.min(1, p - (float) Math.sqrt(dx * dx + dy * dy) + 0.5f));
                if (uvnitr >= 1) continue;
                int g = Math.round(255 * uvnitr), c = pruhledne ? g << 24 | 0xFFFFFF : 0xFF000000 | g << 16 | g << 8 | g;
                px[y * bw + x] = c;
                px[y * bw + bw - 1 - x] = c;
                px[(bh - 1 - y) * bw + x] = c;
                px[(bh - 1 - y) * bw + bw - 1 - x] = c;
            }
    }

    static String dataUrl(Bitmap b) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, o);
        return "data:image/png;base64," + Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
    }

    static MultiFormatReader ctecka(boolean usilovne) {
        Map<DecodeHintType, Object> h = new EnumMap<>(DecodeHintType.class);
        h.put(DecodeHintType.POSSIBLE_FORMATS, FORMATY);
        if (usilovne) h.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        MultiFormatReader r = new MultiFormatReader();
        r.setHints(h);
        return r;
    }

    static Result precti(MultiFormatReader r, LuminanceSource zdroj) {
        try {
            return r.decodeWithState(new BinaryBitmap(new HybridBinarizer(zdroj)));
        } catch (ReaderException e) {
            // zkusí ještě jednodušší prahování, to bere lesklé karty líp
        } finally {
            r.reset();
        }
        try {
            return r.decodeWithState(new BinaryBitmap(new GlobalHistogramBinarizer(zdroj)));
        } catch (ReaderException e) {
            return null;
        } finally {
            r.reset();
        }
    }

    /** Otočí pole jasů (w × h) o 90° po směru hodin, vrátí pole h × w. */
    static byte[] otoc(byte[] d, int w, int h) {
        byte[] o = new byte[d.length];
        for (int y = 0; y < h; y++) {
            int r = y * w, x2 = h - 1 - y;
            for (int x = 0; x < w; x++) o[x * h + x2] = d[r + x];
        }
        return o;
    }

    private static int[] otoc(int[] d, int w, int h) {
        int[] o = new int[d.length];
        for (int y = 0; y < h; y++) {
            int r = y * w, x2 = h - 1 - y;
            for (int x = 0; x < w; x++) o[x * h + x2] = d[r + x];
        }
        return o;
    }

    /** Hledá kód ve fotce: v několika velikostech a natočeních. */
    static Result zFotky(Bitmap b) {
        MultiFormatReader r = ctecka(true);
        int delsi = Math.max(b.getWidth(), b.getHeight());
        int posledni = -1;
        for (int max : new int[]{1600, 1000, 2400, 700}) {
            int cil = Math.min(max, delsi);
            if (cil == posledni) continue;
            posledni = cil;
            Result v = zVelikosti(r, b, cil, 0);
            if (v != null) return v;
        }
        // Snímek karty z webu nebo appky (Atomic Matcha přes yourpass.eu): čáry mají jen úzký bílý okraj a hned za ním
        // barevnou kartu, kterou zxing bere jako tmavou, takže mu před kódem chybí klidová zóna. Barevné a světlé
        // plochy proto zbělí, nejdřív opatrně, pak i tmavě šedé (kód na snímku bývá černý).
        for (int prah : new int[]{128, 64}) {
            posledni = -1;
            for (int max : new int[]{1000, 1600}) {
                int cil = Math.min(max, delsi);
                if (cil == posledni) continue;
                posledni = cil;
                Result v = zVelikosti(r, b, cil, prah);
                if (v != null) return v;
            }
        }
        return null;
    }

    /** Zmenší fotku na delší stranu cil a zkusí ji přečíst rovně i otočenou; prah > 0 napřed zbělí pozadí. */
    private static Result zVelikosti(MultiFormatReader r, Bitmap b, int cil, int prah) {
        int delsi = Math.max(b.getWidth(), b.getHeight());
        Bitmap s = b;
        if (cil < delsi) {
            float k = cil / (float) delsi;
            s = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * k)),
                    Math.max(1, Math.round(b.getHeight() * k)), true);
        }
        int w = s.getWidth(), h = s.getHeight();
        int[] px = new int[w * h];
        s.getPixels(px, 0, w, 0, 0, w, h);
        if (s != b) s.recycle();
        if (prah > 0) zbel(px, prah);
        Result v = precti(r, new RGBLuminanceSource(w, h, px));
        if (v == null) v = precti(r, new RGBLuminanceSource(h, w, otoc(px, w, h)));
        return v;
    }

    /** Barevné pixely a pixely aspoň tak světlé jako prah nahradí bílou, tmavé (čáry kódu) nechá. */
    private static void zbel(int[] px, int prah) {
        for (int i = 0; i < px.length; i++) {
            int c = px[i], r = (c >> 16) & 255, g = (c >> 8) & 255, m = c & 255;
            int mx = Math.max(r, Math.max(g, m)), mn = Math.min(r, Math.min(g, m));
            if (mx - mn > 48 || (r + 2 * g + m) / 4 >= prah) px[i] = 0xFFFFFFFF;
        }
    }
}
