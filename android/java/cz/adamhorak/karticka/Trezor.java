package cz.adamhorak.karticka;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Hesla k účtům u karet. V telefonu jsou jen zašifrovaná klíčem v AndroidKeyStore ("ks1:" + base64 z IV a šifry),
 * klíč z telefonu nejde vyndat. Do zálohy jdou jen pod heslem zálohy (PBKDF2 + AES-GCM), jinak vůbec.
 */
final class Trezor {
    private Trezor() {}

    static final String PREFIX = "ks1:";
    private static final String ALIAS = "karticka-hesla";
    private static final String HESLO_ZALOHY = "hesloZalohy";
    private static final int ITERACE = 200_000;
    private static final SecureRandom NAHODA = new SecureRandom();

    private static synchronized SecretKey klic() throws Exception { // dvě vlákna naráz by mohla založit klíč dvakrát
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(ALIAS)) {
            KeyStore.Entry e = ks.getEntry(ALIAS, null);
            if (e instanceof KeyStore.SecretKeyEntry) return ((KeyStore.SecretKeyEntry) e).getSecretKey();
        }
        KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return g.generateKey();
    }

    /** Zašifruje heslo klíčem telefonu, vrátí "ks1:..." nebo null, když to nejde. */
    static String zamkni(String heslo) {
        if (heslo == null || heslo.isEmpty()) return null;
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, klic()); // IV vyrobí keystore (12 B)
            byte[] iv = c.getIV();
            byte[] sifra = c.doFinal(heslo.getBytes(StandardCharsets.UTF_8));
            byte[] vse = new byte[iv.length + sifra.length];
            System.arraycopy(iv, 0, vse, 0, iv.length);
            System.arraycopy(sifra, 0, vse, iv.length, sifra.length);
            return PREFIX + Base64.encodeToString(vse, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    /** Otevře "ks1:...", nebo vrátí null (jiný telefon, poškozený řetězec, smazaný klíč). */
    static String odemkni(String ks) {
        if (ks == null || !ks.startsWith(PREFIX)) return null;
        try {
            byte[] vse = Base64.decode(ks.substring(PREFIX.length()), Base64.NO_WRAP);
            if (vse.length < 12 + 16) return null;
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, klic(), new GCMParameterSpec(128, vse, 0, 12));
            return new String(c.doFinal(vse, 12, vse.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static SecretKey zHesla(char[] heslo, byte[] sul, int iterace) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(heslo, sul, iterace, 256);
        try {
            byte[] b = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return new SecretKeySpec(b, "AES");
        } finally {
            spec.clearPassword();
        }
    }

    /** Hesla (id karty na otevřené heslo) zamčená heslem zálohy, nebo null při chybě. */
    static JSONObject zalohaZamkni(Map<String, String> idNaHeslo, char[] hesloZalohy) {
        try {
            byte[] sul = new byte[16], iv = new byte[12];
            NAHODA.nextBytes(sul);
            NAHODA.nextBytes(iv);
            JSONObject mapa = new JSONObject();
            for (Map.Entry<String, String> e : idNaHeslo.entrySet()) mapa.put(e.getKey(), e.getValue());
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, zHesla(hesloZalohy, sul, ITERACE), new GCMParameterSpec(128, iv));
            byte[] data = c.doFinal(mapa.toString().getBytes(StandardCharsets.UTF_8));
            JSONObject o = new JSONObject();
            o.put("v", 1);
            o.put("kdf", "PBKDF2WithHmacSHA256");
            o.put("iter", ITERACE);
            o.put("sul", Base64.encodeToString(sul, Base64.NO_WRAP));
            o.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP));
            o.put("data", Base64.encodeToString(data, Base64.NO_WRAP));
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    /** Otevře blok hesel ze zálohy; při špatném hesle nebo poškozeném bloku vrátí null. */
    static Map<String, String> zalohaOdemkni(JSONObject blok, char[] hesloZalohy) {
        try {
            if (blok == null || blok.optInt("v") != 1) return null;
            int iter = blok.optInt("iter");
            if (iter < 1000 || iter > 5_000_000) return null;
            byte[] sul = Base64.decode(blok.getString("sul"), Base64.NO_WRAP);
            byte[] iv = Base64.decode(blok.getString("iv"), Base64.NO_WRAP);
            byte[] data = Base64.decode(blok.getString("data"), Base64.NO_WRAP);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, zHesla(hesloZalohy, sul, iter), new GCMParameterSpec(128, iv));
            JSONObject mapa = new JSONObject(new String(c.doFinal(data), StandardCharsets.UTF_8));
            Map<String, String> v = new HashMap<>();
            for (Iterator<String> it = mapa.keys(); it.hasNext(); ) {
                String id = it.next();
                String h = mapa.optString(id);
                if (!h.isEmpty()) v.put(id, h);
            }
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    /** Uložené heslo zálohy (zašifrované klíčem telefonu), nebo null, když žádné není. */
    static char[] hesloZalohy(Context c) {
        String s = c.getSharedPreferences(Data.PREFS, 0).getString(HESLO_ZALOHY, null);
        String h = odemkni(s);
        return h == null ? null : h.toCharArray();
    }

    static boolean maHesloZalohy(Context c) {
        return hesloZalohy(c) != null;
    }

    /** Prázdné heslo zálohu zruší. Vrátí false, když se heslo nepodařilo zašifrovat. */
    static boolean nastavHesloZalohy(Context c, String heslo) {
        SharedPreferences.Editor e = c.getSharedPreferences(Data.PREFS, 0).edit();
        if (heslo == null || heslo.isEmpty()) {
            e.remove(HESLO_ZALOHY).apply();
            return true;
        }
        String ks = zamkni(heslo);
        if (ks == null) return false;
        e.putString(HESLO_ZALOHY, ks).apply();
        return true;
    }
}
