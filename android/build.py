# -*- coding: utf-8 -*-
r"""
Sestaví appku Kartička (cz.adamhorak.karticka) bez Android Studia a Gradlu, jen nástroji
z Android SDK. Postup je převzatý z Speedometru (cifernik\android\build.py).

    py build.py

Kroky:
  1. celé rozhraní z ..\aplikace (rekurzivně) -> assets/www v APK
  2. aapt2 compile/link -> javac (+ ZXing) -> d8 (+ ZXing) -> zipalign -> apksigner -> kontrola
  3. výsledek dist\karticka.apk; když existuje ..\tools\stranka.py, zavolá se pro stránku ke stažení

Podpis: podpis\karticka.jks, heslo v C:\Users\info\.secrets\karticka-keystore.txt.
TÍMHLE KLÍČEM MUSÍ BÝT PODEPSANÁ KAŽDÁ DALŠÍ VERZE - jinak ji telefon nenainstaluje
přes starou a appka by se musela odinstalovat (a s ní uložené karty).
Mezivýsledky jdou mimo OneDrive do %LOCALAPPDATA%\karticka-build.
"""
import datetime
import io
import os
import re
import secrets
import shutil
import stat
import subprocess
import sys
import time
import zipfile

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

PROJ = os.path.dirname(os.path.abspath(__file__))     # ...\karticka\android
KOREN = os.path.dirname(PROJ)                         # ...\karticka
APLIKACE = os.path.join(KOREN, "aplikace")            # rozhraní, přibalí se do APK
STRANKA_PY = os.path.join(KOREN, "tools", "stranka.py")
DIST = os.path.join(PROJ, "dist")

SDK = os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk")
BT = os.path.join(SDK, "build-tools", "35.0.0")
ANDROID_JAR = os.path.join(SDK, "platforms", "android-35", "android.jar")
BUILD = os.path.join(os.environ["LOCALAPPDATA"], "karticka-build")
ZXING_JAR = os.path.join(PROJ, "knihovny", "core-3.5.3.jar")

KEYSTORE = os.path.join(PROJ, "podpis", "karticka.jks")
HESLO = os.path.join(os.path.expanduser("~"), ".secrets", "karticka-keystore.txt")
ALIAS = "karticka"

MIN_SDK = 26
TARGET_SDK = 34
NAZEV_APK = "karticka.apk"


def krok(nazev):
    print("\n== " + nazev)


def spust(args, **kw):
    """Spustí nástroj; .bat přes cmd. Při chybě vypíše výstup a skončí."""
    if args[0].lower().endswith(".bat"):
        args = ["cmd", "/c"] + args
    p = subprocess.run(args, capture_output=True, text=True, encoding="utf-8",
                       errors="replace", **kw)
    if p.returncode != 0:
        print(p.stdout)
        print(p.stderr)
        jmeno = os.path.basename(args[2] if args[0] == "cmd" else args[0])
        sys.exit("CHYBA: %s skončil kódem %d" % (jmeno, p.returncode))
    return p.stdout + p.stderr


def java_soubory(koren):
    out = []
    for d, _, soubory in os.walk(koren):
        out += [os.path.join(d, s) for s in soubory if s.endswith(".java")]
    return sorted(out)


def verze():
    ted = datetime.datetime.now()
    kod = (ted.date() - datetime.date(2026, 1, 1)).days * 1440 + ted.hour * 60 + ted.minute
    nazev = "%d.%d.%d %d:%02d" % (ted.day, ted.month, ted.year, ted.hour, ted.minute)
    return kod, nazev


def zajisti_podpis():
    if os.path.exists(KEYSTORE):
        if not os.path.exists(HESLO):
            sys.exit("CHYBA: podpisový klíč existuje, ale chybí heslo %s" % HESLO)
        return
    if os.path.exists(HESLO):
        sys.exit("CHYBA: heslo existuje, ale chybí klíč %s - nic nepřepisuju" % KEYSTORE)
    os.makedirs(os.path.dirname(HESLO), exist_ok=True)
    os.makedirs(os.path.dirname(KEYSTORE), exist_ok=True)
    with open(HESLO, "w", encoding="ascii") as f:
        f.write(secrets.token_urlsafe(24) + "\n")
    spust(["keytool", "-genkeypair", "-keystore", KEYSTORE, "-storetype", "PKCS12",
           "-alias", ALIAS, "-keyalg", "RSA", "-keysize", "3072", "-validity", "36500",
           "-dname", "CN=Adam Horak, O=adamhorak.cz, C=CZ",
           "-storepass:file", HESLO, "-keypass:file", HESLO])
    print("  vytvořen nový podpisový klíč (heslo v %s)" % HESLO)


def soubory_rozhrani():
    """Relativní cesty (s lomítky) všech souborů ve složce aplikace, rekurzivně."""
    out = []
    for d, _, soubory in os.walk(APLIKACE):
        for s in soubory:
            out.append(os.path.relpath(os.path.join(d, s), APLIKACE).replace(os.sep, "/"))
    return sorted(out)


def pribal_rozhrani(assets):
    """Celá složka ..\\aplikace do assets/www. Jediný zdroj je složka aplikace."""
    if not os.path.isdir(APLIKACE):
        sys.exit("CHYBA: chybí složka %s" % APLIKACE)
    www = os.path.join(assets, "www")
    shutil.copytree(APLIKACE, www)
    seznam = soubory_rozhrani()
    print("  %d souborů rozhraní" % len(seznam))
    if "index.html" not in seznam:
        print("  VAROVÁNÍ: v aplikace\\ zatím chybí index.html")
    return seznam


def kontrola_vernosti():
    """Každá appka z aplikace\\vernost.json musí být v manifestu v <queries>, jinak ji Android 11+ appce
    neukáže a tlačítko Body a kupóny by otevřelo jen web."""
    cesta = os.path.join(APLIKACE, "vernost.json")
    if not os.path.exists(cesta):
        return
    import json
    programy = [p for p in json.load(open(cesta, encoding="utf-8")).values() if isinstance(p, dict)]
    baliky = {p["app"] for p in programy if p.get("app")}
    bez_appky = sorted(p["odkaz"] for p in programy if p.get("odkaz") and not p.get("app"))
    if bez_appky:
        sys.exit("CHYBA: odkaz do appky ve vernost.json bez app (balíčku): %s" % ", ".join(bez_appky))
    manifest = open(os.path.join(PROJ, "AndroidManifest.xml"), encoding="utf-8").read()
    chybi = sorted(b for b in baliky if '<package android:name="%s" />' % b not in manifest)
    if chybi:
        sys.exit("CHYBA: appky z vernost.json chybí v manifestu v <queries>: %s" % ", ".join(chybi))
    print("  %d appek obchodů v <queries>" % len(baliky))


def kontrola_akci():
    """Akce a leták: každá hodnota v aplikace\\akce.json je stránka https (appka jinou neotevře) a klíč je
    řetězec z retezce.json, ať tlačítko nevisí na překlepu."""
    cesta = os.path.join(APLIKACE, "akce.json")
    if not os.path.exists(cesta):
        return
    import json
    akce = {k: v for k, v in json.load(open(cesta, encoding="utf-8")).items() if not k.startswith("_")}
    spatne = sorted(k for k, v in akce.items() if not (isinstance(v, str) and v.startswith("https://")))
    if spatne:
        sys.exit("CHYBA: v akce.json není odkaz https: %s" % ", ".join(spatne))
    retezce = {r["id"] for r in json.load(open(os.path.join(APLIKACE, "retezce.json"), encoding="utf-8"))}
    nezname = sorted(set(akce) - retezce)
    if nezname:
        sys.exit("CHYBA: v akce.json je neznámý řetězec: %s" % ", ".join(nezname))
    print("  %d řetězců s odkazem na akce a leták" % len(akce))


def kontrola_site():
    """Na internet smí jen Verze.java (číslo nové verze z karticka.adamhorak.cz), Sber.java (se souhlasem
    místa obchodů a napsané podněty na karticka.adamhorak.cz/api) a Dlazdice.java (dlaždice OSM pro otevřenou
    mapu okolí). Jinde v Javě síť nic nepoužívá, ať karty, poloha ani nic dalšího z telefonu neodchází."""
    zakazane = ("java.net", "javax.net", "HttpURLConnection", "openConnection", "Socket", "okhttp", "DownloadManager",
                "android.net.http", "HttpEngine", "MediaPlayer", "ExoPlayer", "ProcessBuilder", "Runtime.getRuntime",
                "setBlockNetworkLoads(false)")
    for cesta in java_soubory(os.path.join(PROJ, "java")):
        text = open(cesta, encoding="utf-8").read()
        jmeno = os.path.basename(cesta)
        if jmeno == "Verze.java":
            # jen tři pevné soubory z karticka.adamhorak.cz (verze, co je nového, APK), jen čtení, tyhle dvě hlavičky
            adresy = sorted(set(re.findall(r'"(https?://[^"]*)', text)))
            hlavicky = re.findall(r"setRequestProperty\(([^;]*)\);", text)
            soubory = sorted(re.findall(r'(\w+) = STRANKA \+ "([^"]*)";', text))
            otevreni = sorted(set(re.findall(r"(?:otevri|text|json)\(\s*(\w+)\s*,", text)))
            if (adresy != ["https://karticka.adamhorak.cz/"]
                    or soubory != [("ADRESA", "verze.json"), ("APK", "karticka.apk"), ("ZMENY", "zmeny.json")]
                    or re.findall(r"new URL\([^)]*\)", text) != ["new URL(adresa)"]
                    or text.count("openConnection") != 1
                    or otevreni != ["ADRESA", "APK", "ZMENY", "adresa"]
                    or any(z in text for z in ("setDoOutput", "getOutputStream", "Build.MODEL", "Build.DEVICE",
                                               "Build.MANUFACTURER", "ANDROID_ID", "Settings.Secure", "Data.",
                                               "karty.json", "Trezor", "Location", "Wifi"))
                    or hlavicky != ['"User-Agent", "Karticka"', '"Accept", prijmout']):
                sys.exit("CHYBA: Verze.java smí jen stáhnout verze.json, zmeny.json a karticka.apk"
                         " z https://karticka.adamhorak.cz/, nic neposílat (adresy %s, soubory %s, volání %s, hlavičky %s)"
                         % (adresy, soubory, otevreni, hlavicky))
            continue
        if jmeno == "Sber.java":
            # jen dvě pevné adresy pod karticka.adamhorak.cz/api, jedno místo se sítí, žádná data karet
            adresy = re.findall(r'"(https?://[^"]*)', text)
            hlavicky = re.findall(r"setRequestProperty\(([^;]*)\);", text)
            volani = set(re.findall(r"posli\(\s*([^,\s)]+)", text))
            if (adresy
                    or 'SBER = Verze.STRANKA + "api/sber";' not in text
                    or 'PODNET = Verze.STRANKA + "api/podnet";' not in text
                    or re.findall(r"new URL\([^)]*\)", text) != ["new URL(adresa)"]
                    or text.count("openConnection") != 1
                    or volani != {"SBER", "PODNET", "String"}
                    or text.count("Build.MODEL") != 1
                    or any(z in text for z in ("Data.", "karty.json", "Trezor", "getFilesDir", "Location", "Wifi",
                                               "Build.DEVICE", "Build.MANUFACTURER", "Build.SERIAL", "ANDROID_ID",
                                               "Settings.Secure", "getInputStream", "Socket", "okhttp"))
                    or hlavicky != ['"User-Agent", "Karticka"', '"Content-Type", "application/json"']):
                sys.exit("CHYBA: Sber.java smí posílat jen na karticka.adamhorak.cz/api/sber a /api/podnet a nesmí"
                         " sahat na karty ani polohu telefonu (adresy %s, volání %s, hlavičky %s)"
                         % (adresy, sorted(volani), hlavicky))
            continue
        if jmeno == "Dlazdice.java":
            # jen dlaždice OSM podle čísla z/x/y, nic neposílá a na karty, polohu ani telefon nesahá
            adresy = re.findall(r'"(https?://[^"]*)', text)
            hlavicky = re.findall(r"setRequestProperty\(([^;]*)\);", text)
            if (adresy != ["https://tile.openstreetmap.org/"]
                    or re.findall(r"new URL\([^)]*\)", text) != ['new URL(SERVER + z + "/" + x + "/" + y + ".png")']
                    or text.count("openConnection") != 1
                    or "(\\\\d{1,2})/(\\\\d{1,7})/(\\\\d{1,7})\\\\.png$" not in text
                    or any(z in text for z in ("setDoOutput", "getOutputStream", "Data.", "karty.json", "Trezor",
                                               "getFilesDir", "Location", "Wifi", "Build.", "ANDROID_ID",
                                               "Settings.Secure", "Socket", "okhttp", "Cookie"))
                    or hlavicky != ['"User-Agent", "Karticka (+https://karticka.adamhorak.cz)"']):
                sys.exit("CHYBA: Dlazdice.java smí jen stáhnout dlaždici z tile.openstreetmap.org podle z/x/y a nic"
                         " neposílat (adresy %s, hlavičky %s)" % (adresy, hlavicky))
            continue
        nalez = [z for z in zakazane if z in text]
        if nalez:
            sys.exit("CHYBA: %s používá síť (%s), na internet smí jen Verze.java, Sber.java a Dlazdice.java"
                     % (jmeno, ", ".join(nalez)))
    hlavni = open(os.path.join(PROJ, "java", "cz", "adamhorak", "karticka", "Hlavni.java"), encoding="utf-8").read()
    if "setBlockNetworkLoads(true)" not in hlavni:
        sys.exit("CHYBA: WebView v Hlavni.java musí mít síť zakázanou (setBlockNetworkLoads(true))")
    print("  síť jen ve Verze.java (kontrola a stažení verze), Sber.java (místa se souhlasem, podněty)"
          " a Dlazdice.java (podklad otevřené mapy)")


def uklid_build():
    """Smaže mezivýsledky; Windows (indexer, antivir) občas drží složku, tak několik pokusů."""
    for _ in range(10):
        # soubory jen pro čtení rmtree na Windows nesmaže, proto nejdřív zrušit příznak
        for koren, slozky, soubory in os.walk(BUILD):
            for jmeno in slozky + soubory:
                try:
                    os.chmod(os.path.join(koren, jmeno), stat.S_IWRITE | stat.S_IREAD)
                except OSError:
                    pass
        shutil.rmtree(BUILD, ignore_errors=True)
        if not os.path.exists(BUILD):
            return
        time.sleep(1)
    sys.exit("CHYBA: nejde smazat %s (něco ji drží)" % BUILD)


def main():
    kod, nazev = verze()
    print("Kartička, verze %s (kód %d)" % (nazev, kod))
    for p in (BT, ANDROID_JAR, ZXING_JAR):
        if not os.path.exists(p):
            sys.exit("CHYBA: chybí %s" % p)

    uklid_build()
    for d in ("gen", "classes", "dex"):
        os.makedirs(os.path.join(BUILD, d))
    assets = os.path.join(BUILD, "assets")

    krok("rozhraní do assets")
    seznam = pribal_rozhrani(assets)
    kontrola_vernosti()
    kontrola_akci()
    kontrola_site()

    krok("pravidla výběru karty (app.js proti Pravidla.java)")
    sys.path.insert(0, os.path.join(KOREN, "tools"))
    import test_pravidla
    print("  shoda na %d výsledcích" % test_pravidla.main())

    krok("zdroje (aapt2)")
    res_zip = os.path.join(BUILD, "res.zip")
    zaklad = os.path.join(BUILD, "zaklad.apk")
    spust([os.path.join(BT, "aapt2.exe"), "compile", "--dir", os.path.join(PROJ, "res"),
           "-o", res_zip])
    spust([os.path.join(BT, "aapt2.exe"), "link", "-o", zaklad, "-I", ANDROID_JAR,
           "--manifest", os.path.join(PROJ, "AndroidManifest.xml"), "-R", res_zip,
           "-A", assets,
           "--java", os.path.join(BUILD, "gen"), "--auto-add-overlay",
           "--min-sdk-version", str(MIN_SDK), "--target-sdk-version", str(TARGET_SDK),
           "--version-code", str(kod), "--version-name", nazev])

    krok("kompilace (javac)")
    zdrojaky = java_soubory(os.path.join(PROJ, "java")) + java_soubory(os.path.join(BUILD, "gen"))
    # android.jar nemá LambdaMetafactory, kterou javac na lambdy potřebuje; d8 je pak přepíše
    lambda_stuby = os.path.join(BT, "core-lambda-stubs.jar")
    vystup = spust(["javac", "-encoding", "UTF-8", "-source", "8", "-target", "8",
                    "-Xlint:-options", "-Xlint:unchecked",
                    "-bootclasspath", ANDROID_JAR + os.pathsep + lambda_stuby,
                    "-classpath", ZXING_JAR,
                    "-d", os.path.join(BUILD, "classes")] + zdrojaky)
    if vystup.strip():
        print(vystup.strip())
    print("  %d souborů" % len(zdrojaky))

    krok("dex (d8)")
    classes_jar = os.path.join(BUILD, "classes.jar")
    spust(["jar", "cf", classes_jar, "-C", os.path.join(BUILD, "classes"), "."])
    # ZXing je druhý vstup, ať je v classes.dex přímo v APK
    spust([os.path.join(BT, "d8.bat"), "--release", "--min-api", str(MIN_SDK),
           "--lib", ANDROID_JAR, "--output", os.path.join(BUILD, "dex"),
           classes_jar, ZXING_JAR])
    dexy = sorted(f for f in os.listdir(os.path.join(BUILD, "dex")) if f.endswith(".dex"))
    if "classes.dex" not in dexy:
        sys.exit("CHYBA: d8 nevytvořil classes.dex")
    with zipfile.ZipFile(zaklad, "a", compression=zipfile.ZIP_DEFLATED) as z:
        for d in dexy:
            z.write(os.path.join(BUILD, "dex", d), d)
    print("  %s" % ", ".join(dexy))

    krok("zarovnání a podpis")
    zarovnano = os.path.join(BUILD, "zarovnano.apk")
    hotovo = os.path.join(BUILD, NAZEV_APK)
    spust([os.path.join(BT, "zipalign.exe"), "-p", "-f", "4", zaklad, zarovnano])
    zajisti_podpis()
    # jen --ks-pass: apksigner čte soubor s heslem po řádcích, druhé čtení téhož souboru
    # skončí na konci; heslo klíče se v PKCS12 bere stejné jako heslo úložiště
    spust([os.path.join(BT, "apksigner.bat"), "sign", "--ks", KEYSTORE,
           "--ks-key-alias", ALIAS, "--ks-pass", "file:" + HESLO,
           "--out", hotovo, zarovnano])

    krok("kontrola")
    podpis = spust([os.path.join(BT, "apksigner.bat"), "verify", "--verbose",
                    "--print-certs", hotovo])
    for radek in podpis.splitlines():
        if radek.startswith(("Verified using v2", "Verified using v3 ", "Signer #1 certificate DN")):
            print("  " + radek)
    if "Verifies" not in podpis:
        sys.exit("CHYBA: podpis neprošel")
    badging = spust([os.path.join(BT, "aapt2.exe"), "dump", "badging", hotovo])
    for radek in badging.splitlines():
        if radek.startswith(("package:", "minSdkVersion", "targetSdkVersion", "uses-permission",
                             "application-label:", "launchable-activity")):
            print("  " + radek)
    with zipfile.ZipFile(hotovo) as z:
        v_apk = sorted(n[len("assets/www/"):] for n in z.namelist() if n.startswith("assets/www/"))
        if "classes.dex" not in z.namelist():
            sys.exit("CHYBA: v APK chybí classes.dex")
    if v_apk != seznam:
        sys.exit("CHYBA: rozhraní v APK nesedí se složkou aplikace (%s vs %s)" % (v_apk, seznam))
    print("  rozhraní v APK: %d souborů" % len(v_apk))

    krok("výstupy")
    os.makedirs(DIST, exist_ok=True)
    cil = os.path.join(DIST, NAZEV_APK)
    shutil.copy2(hotovo, cil)
    print("  dist\\%s  %d kB" % (NAZEV_APK, round(os.path.getsize(cil) / 1024)))

    if os.path.exists(STRANKA_PY):
        krok("stránka ke stažení (tools\\stranka.py)")
        print(spust([sys.executable, "-I", STRANKA_PY, "--apk", cil,
                     "--verze", nazev, "--kod", str(kod)]).strip()[-600:])
    else:
        print("  tools\\stranka.py zatím neexistuje, stránku přeskakuju")

    # webová Kartička pro iPhone (web/app), stejné rozhraní jako v APK, od 5.11.2026
    webappka = os.path.join(KOREN, "tools", "webappka.py")
    if os.path.exists(webappka):
        krok("webová Kartička pro iPhone (tools\\webappka.py)")
        print(spust([sys.executable, "-I", webappka]).strip()[-600:])


main()
