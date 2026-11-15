package cz.adamhorak.karticka;

/**
 * Pozná zatřesení telefonem (haptický podnět) ze vzorků akcelerometru. Bez Androidu, ať jde vyzkoušet na PC.
 * <p>
 * Od zrychlení se odečte tíže (pomalý průměr, chvění se v něm vyruší) a počítají se nárazy přes PRAH. Zatřesení
 * jsou NARAZU nárazů po sobě, každý zhruba opačným směrem než ten před ním (tam a zpátky), všechny do OKNO.
 * Chůze nedá ani třetinu prahu, běh nebo položení telefonu na stůl sice přes práh jdou, ale pořád jedním směrem.
 * Starší verze chtěla 4 nárazy přes 2,7 g včetně tíže (bokem se tak třese až od 2,5 g) se vzorky jen asi 15×
 * za vteřinu a v S25 nefungovala; Hlavni teď posílá vzorky asi 50× za vteřinu. Zkouška na vymyšlených průbězích
 * (třesení 1,3 až 2,5 g ano, chůze, běh, položení na stůl, mávnutí, otočení ne) je v KONTEXT.md.
 */
final class Treseni {
    static final float PRAH = 10f;           // m/s² bez tíže, asi 1 g
    private static final float ZNOVU = 6f;   // pod tím náraz skončil a další se může počítat
    private static final long ODSTUP = 70;   // ms mezi nárazy
    private static final long OKNO = 1500;   // ms od prvního do posledního nárazu
    private static final int NARAZU = 3;     // tam, zpátky, tam
    private static final long PAUZA = 3000;  // další podnět nejdřív za 3 s
    private static final float TIZE_S = 0.5f; // s, jak pomalu se průměr tíže přizpůsobuje

    private float gx, gy, gz;
    private long minule = Long.MIN_VALUE;    // čas minulého vzorku
    private boolean venku;                    // probíhá náraz (nad ZNOVU od chvíle, kdy přešel PRAH)
    private float px, py, pz;                 // směr minulého nárazu
    private long narazCas = Long.MIN_VALUE / 2, start = Long.MIN_VALUE / 2, hotovo = Long.MIN_VALUE / 2;
    private int pocet;

    /** Jeden vzorek (čas v ms, zrychlení v m/s² včetně tíže). Vrací true, když je to zatřesení. */
    boolean vzorek(long t, float x, float y, float z) {
        if (minule == Long.MIN_VALUE || t - minule > 1000) {
            // první vzorek nebo dlouhá díra (appka byla pryč): tíže je, co telefon zrovna cítí
            gx = x; gy = y; gz = z;
            minule = t;
            venku = false;
            pocet = 0;
            return false;
        }
        float dt = Math.max(0.001f, (t - minule) / 1000f);
        minule = t;
        float a = dt / (TIZE_S + dt);
        gx += a * (x - gx); gy += a * (y - gy); gz += a * (z - gz);
        float lx = x - gx, ly = y - gy, lz = z - gz;
        float sila = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (venku) {
            if (sila < ZNOVU) venku = false;
            return false;
        }
        if (sila < PRAH || t - narazCas < ODSTUP) return false;
        venku = true;
        boolean opacne = lx * px + ly * py + lz * pz < 0;
        if (pocet == 0 || !opacne || t - start > OKNO) {
            pocet = 1;
            start = t;
        } else {
            pocet++;
        }
        narazCas = t;
        px = lx; py = ly; pz = lz;
        if (pocet < NARAZU || t - hotovo < PAUZA) return false;
        hotovo = t;
        pocet = 0;
        return true;
    }
}
