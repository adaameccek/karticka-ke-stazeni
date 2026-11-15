package cz.adamhorak.karticka;

import java.util.Map;

/**
 * Pravidla výběru karty u obchodu, stejná jako blok „pravidla" v app.js (podobnost, uroven, drzi).
 * Bez Androidu, ať jdou pustit na PC: tools/test_pravidla.py porovná výsledky s app.js na tisících
 * náhodných vstupů a build bez shody neprojde. Kdo mění tady, mění i v app.js a naopak.
 */
final class Pravidla {
    private Pravidla() {}

    /** Od téhle shody otisku Wi-Fi rozhoduje Wi-Fi (patro, obchod nad obchodem), ne poloha. */
    static final double WIFI_SEDI = 0.35;

    private static int sila(int dBm) { return Math.max(0, Math.min(70, dBm + 100)); }

    /**
     * 0 = nic společného, 1 = stejné sítě stejně silné. F je uložený otisk, S sítě teď. Síť, která je teď silná
     * a v otisku chybí, se počítá proti; slabší než nejslabší v otisku se nepočítají (otisk má jen 15 nejsilnějších).
     */
    static double podobnost(Map<String, Integer> F, Map<String, Integer> S) {
        int nejslabsi = Integer.MAX_VALUE;
        long spolu = 0, celkem = 0;
        for (Map.Entry<String, Integer> e : F.entrySet()) {
            int f = sila(e.getValue());
            Integer x = S.get(e.getKey());
            int s = x == null ? 0 : sila(x);
            spolu += Math.min(f, s);
            celkem += Math.max(f, s);
            if (e.getValue() < nejslabsi) nejslabsi = e.getValue();
        }
        for (Map.Entry<String, Integer> e : S.entrySet()) {
            if (!F.containsKey(e.getKey()) && e.getValue() > nejslabsi) celkem += sila(e.getValue());
        }
        return celkem == 0 ? 0 : (double) spolu / celkem;
    }

    /**
     * Úroveň pořadí karty u obchodu, menší vyhrává; karty se stejnou úrovní jsou pro polohu nerozlišitelné.
     * w = shoda otisku Wi-Fi (0 bez otisku), m = metry k vlastnímu místu, c = k pokladně ze sběru, v = k nejbližšímu
     * bodu vůbec, p = přesnost polohy. Wi-Fi pod nulou, vlastní místo od 0, pokladna od 500, mapa od 1000.
     */
    static long uroven(double w, double m, double c, double v, double p) {
        double tesne = Math.max(35, Math.min(p, 75));
        double kosMoje = Math.max(20, Math.min(p, 60));
        double kos = Math.max(40, Math.min(p, 100));
        if (w >= WIFI_SEDI) return -100 + Math.round((1 - w) / 0.08);
        if (m <= tesne) return Math.round(m / kosMoje);
        if (c <= tesne) return 500 + Math.round(c / kosMoje);
        return 1000 + Math.round(v / kos);
    }

    /**
     * Ukázaná karta (úroveň b) se drží proti nejlepší (úroveň nej): když je stejně dobrá, nebo když člověk stojí,
     * kde se ukázala (naMiste), ať se kód nepřehodí pod rukama. Nedrží se, když jiný obchod poznala Wi-Fi nebo
     * když má jiná karta potvrzené místo a držená jen bod z mapy.
     */
    static boolean drzi(long b, long nej, boolean naMiste) {
        if (b == nej) return true;
        if (nej < 0 && b >= 0) return false;
        if (nej < 1000 && b >= 1000) return false;
        return naMiste;
    }
}
