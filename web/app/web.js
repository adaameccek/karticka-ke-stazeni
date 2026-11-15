'use strict';
// Webová Kartička (iPhone a jiné prohlížeče, karticka.adamhorak.cz/app): náhrada za Javu z Androidu (Hlavni.java).
// app.js je stejný jako v APK a volá tenhle most jako window.KartickaWeb. Od 5.11.2026 (Adam: tlačítko i pro iPhone).
// Karty jsou jen v úložišti tohohle prohlížeče (localStorage), nikam se neposílají. Kódy kreslí bwip-js (vendor),
// čte zxing-wasm (vendor, načte se až při skenování). Data obchodů jsou v data.js (KartickaData), ať jedou i bez internetu.
// Verzi doplní tools/webappka.py.
(function () {
  const VERZE = '14.11.2026 10:51';
  const LS = {
    get(k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
    set(k, v) { try { localStorage.setItem(k, v); return true; } catch (e) { return false; } },
    del(k) { try { localStorage.removeItem(k); } catch (e) { /* bez úložiště */ } }
  };
  const zavolej = (f, ...a) => setTimeout(() => { if (typeof window[f] === 'function') window[f](...a); }, 0);
  const ios = /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  const naPlose = navigator.standalone === true || (window.matchMedia && matchMedia('(display-mode: standalone)').matches);

  // ---------- kreslení kódů (jako Kod.obrazek v Androidu) ----------
  // Každý modul je celý násobek pixelu, kolem tichá zóna podle normy (QR 4, ostatní 2D 2, čárové 10 modulů).
  const BCID = { EAN_13: 'ean13', EAN_8: 'ean8', UPC_A: 'upca', UPC_E: 'upce', CODE_128: 'code128', CODE_39: 'code39',
    CODE_93: 'code93', CODABAR: 'rationalizedCodabar', ITF: 'interleaved2of5', QR_CODE: 'qrcode',
    DATA_MATRIX: 'datamatrix', PDF_417: 'pdf417', AZTEC: 'azteccode' };
  const DVA = new Set(['QR_CODE', 'DATA_MATRIX', 'PDF_417', 'AZTEC']);
  const zona = f => f === 'QR_CODE' ? 4 : DVA.has(f) ? 2 : 10;
  const kesMatic = new Map();

  // Moduly kódu bez okraje: { w, h, m } (m[y * w + x] = 1 je černá), nebo null, když text v tom formátu nejde.
  function matice(format, kod) {
    const klic = format + '\u0001' + kod;
    if (kesMatic.has(klic)) return kesMatic.get(klic);
    let vysledek = null;
    try {
      const bcid = BCID[format];
      if (!bcid || !kod || !window.bwipjs) throw new Error('nejde');
      let text = kod;
      // ZXing přidá u Codabaru start a stop A, když chybí; bwip-js je chce mít v textu
      if (format === 'CODABAR' && !/^[A-D].*[A-D]$/i.test(text)) text = 'A' + text + 'A';
      const volby = {};
      if (format === 'QR_CODE') volby.eclevel = 'L'; // jako ZXing: menší kód, u pokladny stačí
      if (format === 'CODE_93') volby.includecheck = true; // Code 93 má dva povinné kontrolní znaky, bez nich ho čtečka nevezme
      const r = window.bwipjs.raw(bcid, text, volby)[0];
      if (r.pixs) {
        const px = typeof r.pixs === 'string' ? Array.from(r.pixs, z => z.charCodeAt(0)) : Array.from(r.pixs);
        // PDF417 má řádky po jednom modulu, čtečky chtějí řádek asi třikrát vyšší (jako ZXing v Androidu)
        const nasob = format === 'PDF_417' ? 3 : 1;
        const m = new Uint8Array(r.pixx * r.pixy * nasob);
        for (let y = 0; y < r.pixy * nasob; y++) for (let x = 0; x < r.pixx; x++) m[y * r.pixx + x] = px[Math.floor(y / nasob) * r.pixx + x] ? 1 : 0;
        vysledek = { w: r.pixx, h: r.pixy * nasob, m };
      } else {
        // čárový kód: střídavě šířka čáry a mezery v modulech, začíná čárou
        const sloupce = [];
        r.sbs.forEach((s, i) => { for (let j = 0; j < Math.round(s); j++) sloupce.push(i % 2 === 0 ? 1 : 0); });
        vysledek = { w: sloupce.length, h: 1, m: Uint8Array.from(sloupce) };
      }
      if (!vysledek.w || !vysledek.h) vysledek = null;
    } catch (e) {
      vysledek = null;
    }
    kesMatic.set(klic, vysledek);
    if (kesMatic.size > 60) kesMatic.delete(kesMatic.keys().next().value);
    return vysledek;
  }

  function obrazek(format, kod, sirka, vyska) {
    const mt = matice(format, kod);
    if (!mt) return '';
    sirka = Math.max(1, Math.min(sirka | 0, 3000));
    vyska = Math.max(1, Math.min(vyska | 0, 2000));
    const q = zona(format), dva = DVA.has(format);
    const s = Math.max(1, Math.floor(sirka / (mt.w + 2 * q)));
    const bw = (mt.w + 2 * q) * s;
    const bh = dva ? (mt.h + 2 * q) * s : Math.max(s * 20, vyska);
    if (bw * bh > 6e6) return '';
    const c = document.createElement('canvas');
    c.width = bw; c.height = bh;
    const g = c.getContext('2d');
    g.fillStyle = '#FFFFFF'; g.fillRect(0, 0, bw, bh);
    g.fillStyle = '#000000';
    if (dva) {
      for (let y = 0; y < mt.h; y++) for (let x = 0; x < mt.w; x++) if (mt.m[y * mt.w + x]) g.fillRect((x + q) * s, (y + q) * s, s, s);
    } else {
      for (let x = 0; x < mt.w; x++) if (mt.m[x]) g.fillRect((x + q) * s, 0, s, bh);
    }
    return c.toDataURL('image/png');
  }

  // ---------- čtení kódů (zxing-wasm, jako Kod.cti v Androidu) ----------
  const ZX_NA_FORMAT = { EAN13: 'EAN_13', EAN8: 'EAN_8', UPCA: 'UPC_A', UPCE: 'UPC_E', CODE128: 'CODE_128', CODE39: 'CODE_39',
    CODE39STD: 'CODE_39', CODE39EXT: 'CODE_39', CODE93: 'CODE_93', CODABAR: 'CODABAR', ITF: 'ITF', ITF14: 'ITF',
    QRCODE: 'QR_CODE', QRCODEMODEL2: 'QR_CODE', DATAMATRIX: 'DATA_MATRIX', PDF417: 'PDF_417', AZTEC: 'AZTEC', AZTECCODE: 'AZTEC' };
  const ZX_FORMATY = ['EAN13', 'EAN8', 'UPCA', 'UPCE', 'Code128', 'Code39', 'Code93', 'Codabar', 'ITF', 'QRCode', 'DataMatrix', 'PDF417', 'Aztec'];
  let ctecka = null;
  function nactiCtecku() {
    if (!ctecka) ctecka = new Promise((hotovo, chyba) => {
      const sc = document.createElement('script');
      sc.src = 'vendor/zxing-reader.js';
      sc.onload = () => {
        try {
          window.ZXingWASM.prepareZXingModule({
            overrides: { locateFile: (cesta, predpona) => cesta.endsWith('.wasm') ? 'vendor/zxing_reader.wasm' : predpona + cesta },
            fireImmediately: true
          }).then(() => hotovo(window.ZXingWASM), chyba);
        } catch (e) { chyba(e); }
      };
      sc.onerror = chyba;
      document.head.appendChild(sc);
    }).catch(e => { ctecka = null; throw e; });
    return ctecka;
  }
  async function precti(obraz) {
    const zx = await nactiCtecku();
    const r = await zx.readBarcodes(obraz, { formats: ZX_FORMATY, tryHarder: true, tryRotate: true, tryInvert: true, maxNumberOfSymbols: 1 });
    const x = (r || []).find(v => v && v.isValid !== false && v.text);
    if (!x) return null;
    const f = ZX_NA_FORMAT[String(x.format || '').toUpperCase().replace(/[^A-Z0-9]/g, '')];
    if (!f) return null;
    return jakoAndroid(f, x.text);
  }
  // Číslo a typ kódu tak, jak je vrací ZXing v Androidu, ať je stejná karta v obou appkách stejná.
  // ZXing-C++ dává UPC-A a UPC-E jako 13místné EAN a Codabar se start a stop znakem.
  function jakoAndroid(f, text) {
    if ((f === 'EAN_13' || f === 'UPC_A') && /^0\d{12}$/.test(text)) return { format: 'UPC_A', text: text.slice(1) };
    if (f === 'UPC_E' && /^0[01]\d{11}$/.test(text)) {
      const e = upcE(text.slice(1));
      return e ? { format: 'UPC_E', text: e } : { format: 'UPC_A', text: text.slice(1) };
    }
    if (f === 'CODABAR' && /^[A-D].+[A-D]$/i.test(text)) return { format: f, text: text.slice(1, -1) };
    return { format: f, text };
  }
  // 12místné UPC-A zpátky na 8místné UPC-E (číselná soustava, 6 číslic, kontrolní); null, když to nejde
  function upcE(a) {
    const m = a.slice(1, 6), p = a.slice(6, 11);
    const rozbal = e => {
      const d = e[5];
      if (d <= '2') return e[0] + e[1] + d + '00' + '00' + e[2] + e[3] + e[4];
      if (d === '3') return e[0] + e[1] + e[2] + '00' + '000' + e[3] + e[4];
      if (d === '4') return e.slice(0, 4) + '0' + '0000' + e[4];
      return e.slice(0, 5) + '0000' + d;
    };
    const kandidati = [m[0] + m[1] + p[2] + p[3] + p[4] + m[2], m.slice(0, 3) + p[3] + p[4] + '3', m.slice(0, 4) + p[4] + '4', m + p[4]];
    const e = kandidati.find(k => rozbal(k) === m + p);
    return e ? a[0] + e + a[11] : null;
  }
  // Fotka (i HEIC z iPhonu, ten ji prohlížeč dekóduje sám) do ImageData, nejvýš max px na delší straně.
  async function doPixelu(soubor, max) {
    const url = URL.createObjectURL(soubor);
    try {
      const img = await new Promise((hotovo, chyba) => { const i = new Image(); i.onload = () => hotovo(i); i.onerror = chyba; i.src = url; });
      const k = Math.min(1, max / Math.max(img.naturalWidth, img.naturalHeight));
      const c = document.createElement('canvas');
      c.width = Math.max(1, Math.round(img.naturalWidth * k)); c.height = Math.max(1, Math.round(img.naturalHeight * k));
      const g = c.getContext('2d', { willReadFrequently: true });
      g.drawImage(img, 0, 0, c.width, c.height);
      return { data: g.getImageData(0, 0, c.width, c.height), platno: c };
    } finally { URL.revokeObjectURL(url); }
  }
  function nahled(platno) {
    const c = document.createElement('canvas');
    const k = 138 / Math.max(platno.width, platno.height);
    c.width = Math.max(1, Math.round(platno.width * k)); c.height = Math.max(1, Math.round(platno.height * k));
    c.getContext('2d').drawImage(platno, 0, 0, c.width, c.height);
    return c.toDataURL('image/jpeg', 0.7);
  }
  function vyberSoubor(prijmout, vic, hotovo) {
    const vstup = document.createElement('input');
    vstup.type = 'file';
    vstup.accept = prijmout;
    vstup.multiple = !!vic;
    vstup.style.display = 'none';
    vstup.addEventListener('change', () => { const f = Array.from(vstup.files || []); vstup.remove(); hotovo(f); });
    document.body.appendChild(vstup);
    vstup.click();
  }

  // Skenování kamerou: celá obrazovka s náhledem, čte se několikrát za vteřinu, dokud se kód nenajde nebo člověk nezruší.
  async function skenovat() {
    let proud;
    try {
      proud = await navigator.mediaDevices.getUserMedia({ audio: false,
        video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } } });
    } catch (e) {
      zavolej('naKod', null, null, 'kamera');
      return;
    }
    nactiCtecku().catch(() => {});
    const video = document.createElement('video');
    video.setAttribute('playsinline', ''); video.muted = true; video.autoplay = true;
    video.srcObject = proud;
    const zrusit = document.createElement('button');
    zrusit.type = 'button'; zrusit.className = 'tlacitko'; zrusit.textContent = 'Zrušit';
    const rada = document.createElement('p');
    rada.textContent = 'Namiř kameru na čárový nebo QR kód karty';
    const ram = document.createElement('div'); ram.className = 'web-sken-ram';
    const obal = document.createElement('div'); obal.className = 'web-sken';
    obal.append(video, ram, rada, zrusit);
    document.body.appendChild(obal);
    let konec = false;
    const zavri = (format, text, chyba) => {
      if (konec) return;
      konec = true;
      proud.getTracks().forEach(t => t.stop());
      obal.remove();
      zavolej('naKod', format, text, chyba);
    };
    zrusit.addEventListener('click', () => zavri(null, null, 'zruseno'));
    const c = document.createElement('canvas');
    const g = c.getContext('2d', { willReadFrequently: true });
    const kolo = async () => {
      if (konec) return;
      if (video.readyState >= 2 && video.videoWidth) {
        const k = Math.min(1, 1280 / Math.max(video.videoWidth, video.videoHeight));
        c.width = Math.round(video.videoWidth * k); c.height = Math.round(video.videoHeight * k);
        g.drawImage(video, 0, 0, c.width, c.height);
        try {
          const r = await precti(g.getImageData(0, 0, c.width, c.height));
          if (r) { if (navigator.vibrate) navigator.vibrate(30); zavri(r.format, r.text, ''); return; }
        } catch (e) {
          zavri(null, null, 'nenalezeno');
          return;
        }
      }
      setTimeout(kolo, 150);
    };
    video.addEventListener('loadeddata', () => setTimeout(kolo, 300), { once: true });
  }

  // ---------- poloha ----------
  // Napřed rychlá přibližná (Wi-Fi, síť), pak přesná z GPS. Poslední poloha se pamatuje, ať jde mapa otevřít hned.
  const ladeni = /[?&]poloha=([-\d.]+),([-\d.]+)/.exec(location.search); // jen na zkoušku v počítači
  let hledam = false, povoleni = '';
  if (navigator.permissions && navigator.permissions.query) {
    navigator.permissions.query({ name: 'geolocation' }).then(s => {
      povoleni = s.state;
      s.onchange = () => { povoleni = s.state; };
    }).catch(() => {});
  }
  const zPolohy = p => ({ lat: p.coords.latitude, lon: p.coords.longitude, presnost: p.coords.accuracy || 999,
    vek: Math.max(0, Date.now() - (p.timestamp || Date.now())) });
  function zapamatuj(q) {
    LS.set('karticka-poloha', JSON.stringify({ lat: q.lat, lon: q.lon, presnost: q.presnost, cas: Date.now() - q.vek }));
  }
  function poloha() {
    if (ladeni) {
      zavolej('naPolohu', { lat: +ladeni[1], lon: +ladeni[2], presnost: 20, vek: 0 });
      return;
    }
    if (!navigator.geolocation) { zavolej('naPolohu', null, 'vypnuto'); return; }
    if (hledam) return;
    hledam = true;
    navigator.geolocation.getCurrentPosition(p => {
      if (hledam) zavolej('naPolohu', zPolohy(p), 'hruba');
    }, () => {}, { enableHighAccuracy: false, maximumAge: 60000, timeout: 5000 });
    navigator.geolocation.getCurrentPosition(p => {
      hledam = false;
      povoleni = 'granted';
      const q = zPolohy(p);
      zapamatuj(q);
      zavolej('naPolohu', q);
    }, e => {
      hledam = false;
      if (e && e.code === 1) povoleni = 'denied';
      zavolej('naPolohu', null, e && e.code === 1 ? 'zakazano' : 'nic');
    }, { enableHighAccuracy: true, maximumAge: 0, timeout: 15000 });
  }
  function posledniPoloha() {
    try {
      const p = JSON.parse(LS.get('karticka-poloha') || 'null');
      return p && isFinite(p.lat) ? JSON.stringify({ lat: p.lat, lon: p.lon, presnost: p.presnost, vek: Math.max(0, Date.now() - p.cas) }) : '';
    } catch (e) { return ''; }
  }

  // ---------- rady, kde v iPhonu co povolit ----------
  function rada(nadpis, text) {
    const obal = document.createElement('div'); obal.className = 'dialog-pozadi web-rada';
    const d = document.createElement('div'); d.className = 'dialog'; d.setAttribute('role', 'dialog');
    const h = document.createElement('p'); h.textContent = nadpis;
    const p = document.createElement('p'); p.className = 'dialog-popis'; p.textContent = text;
    const t = document.createElement('div'); t.className = 'dialog-tlacitka';
    const ok = document.createElement('button'); ok.type = 'button'; ok.className = 'tlacitko hlavni'; ok.textContent = 'Rozumím';
    ok.addEventListener('click', () => obal.remove());
    obal.addEventListener('click', e => { if (e.target === obal) obal.remove(); });
    t.append(ok); d.append(h, p, t); obal.append(d);
    document.body.appendChild(obal);
    requestAnimationFrame(() => requestAnimationFrame(() => obal.classList.add('otevrena')));
  }
  const RADA_POLOHA = ios
    ? 'V iPhonu otevři Nastavení, Soukromí a zabezpečení, Polohové služby. Zapni je a u Webů v Safari dej Při používání aplikace. Pak se Kartičky zeptá znovu.'
    : 'Povol polohu pro karticka.adamhorak.cz v nastavení prohlížeče (ikona zámku vedle adresy).';
  const RADA_KAMERA = ios
    ? 'V iPhonu otevři Nastavení, Aplikace, Safari, Kamera a dej Zeptat se nebo Povolit. Pak zkus skenovat znovu.'
    : 'Povol kameru pro karticka.adamhorak.cz v nastavení prohlížeče (ikona zámku vedle adresy).';

  // ---------- záloha ----------
  function zaloha() {
    let t = LS.get('karticka') || '';
    try {
      const d = JSON.parse(t);
      (d.karty || []).forEach(k => { delete k.heslo; });
      t = JSON.stringify(d);
    } catch (e) { zavolej('naZalohu', false, 0); return; }
    const d = new Date();
    const jmeno = 'karticka-zaloha-' + d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0') + '.json';
    const soubor = new File([t], jmeno, { type: 'application/json' });
    if (navigator.canShare && navigator.canShare({ files: [soubor] })) {
      navigator.share({ files: [soubor], title: 'Záloha Kartičky' })
        .then(() => zavolej('naZalohu', true, 0))
        .catch(e => { if (!e || e.name !== 'AbortError') zavolej('naZalohu', false, 0); });
      return;
    }
    const a = document.createElement('a');
    a.href = URL.createObjectURL(soubor); a.download = jmeno;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(a.href), 60000);
    zavolej('naZalohu', true, 0);
  }

  // ---------- most ----------
  let zamekObrazovky = null;
  window.KartickaWeb = {
    karty: () => LS.get('karticka') || '',
    kartyPredchozi: () => LS.get('karticka-predchozi') || '',
    uloz(t) {
      const ted = LS.get('karticka');
      if (ted && ted !== t) LS.set('karticka-predchozi', ted);
      return LS.set('karticka', t);
    },
    soubor: j => (window.KartickaData && window.KartickaData[j]) || '',
    obrazek,
    je2D: f => DVA.has(f),
    // Jas web řídit nesmí. Aspoň nenechá zhasnout obrazovku, dokud je kód naplno.
    jas(max) {
      if (max && navigator.wakeLock && !zamekObrazovky) {
        navigator.wakeLock.request('screen').then(z => { zamekObrazovky = z; z.addEventListener('release', () => { zamekObrazovky = null; }); }).catch(() => {});
      } else if (!max && zamekObrazovky) { zamekObrazovky.release().catch(() => {}); zamekObrazovky = null; }
    },
    jasy: () => '',
    posledniPoloha,
    wifi: () => '',
    poloha,
    stavPolohy: () => povoleni === 'denied' ? 'zakazano' : povoleni === 'granted' ? 'ok' : '',
    stavWidgetu: () => 'appka',
    povolitPolohu() { if (povoleni === 'denied') rada('Poloha', RADA_POLOHA); else poloha(); },
    povolitVzdy() {},
    stavBaterie: () => 'volna',
    baterie() {},
    nastaveni() { rada('Kamera', RADA_KAMERA); },
    skenovat,
    zFotky() {
      vyberSoubor('image/*', false, async f => {
        if (!f.length) { zavolej('naKod', null, null, 'zruseno'); return; }
        try {
          const r = await precti((await doPixelu(f[0], 2400)).data);
          zavolej('naKod', r ? r.format : null, r ? r.text : null, r ? '' : 'nenalezeno');
        } catch (e) { zavolej('naKod', null, null, 'nenalezeno'); }
      });
    },
    fotky() {
      vyberSoubor('image/*', true, async soubory => {
        const vse = [];
        for (let i = 0; i < soubory.length; i++) {
          zavolej('naFotkyPrubeh', i + 1, soubory.length);
          try {
            const { data, platno } = await doPixelu(soubory[i], 2400);
            const r = await precti(data).catch(() => null);
            vse.push([r ? r.format : '', r ? r.text : '', nahled(platno)]);
          } catch (e) { vse.push(['', '', '']); }
        }
        zavolej('naFotky', JSON.stringify(vse));
      });
    },
    posledni() {},
    start: () => '',
    pridatWidget: () => false,
    vzhledWidgetu() {},
    zaloha,
    // Hesla k účtům web neukládá (nemá zámek telefonu), app.js pole na heslo na webu neukazuje.
    zamkni: () => '',
    ukazHeslo(ks) { zavolej('naHeslo', ks, null, false); },
    skryjHeslo() {},
    kopirujHeslo: () => false,
    kopiruj(t) {
      try { navigator.clipboard.writeText(String(t)); return true; } catch (e) { return false; }
    },
    maHesloZalohy: () => false,
    nastavHesloZalohy: () => false,
    odemkniZalohu: () => '',
    obnova() {
      vyberSoubor('.json,.csv,.txt,application/json,text/plain,text/csv', false, f => {
        if (!f.length) return;
        f[0].text().then(t => zavolej('naObnovu', t), () => zavolej('naObnovu', null));
      });
    },
    sdilet(text) {
      if (navigator.share) navigator.share({ text }).catch(() => {});
      else { try { navigator.clipboard.writeText(text); } catch (e) { /* nic */ } }
    },
    otevriProgram(app, web) {
      if (!web) return '';
      window.open(web, '_blank', 'noopener');
      return 'web';
    },
    verze: () => 'web ' + VERZE,
    novaVerze: () => '',
    verzeOhlasena() {},
    stahnoutNovou() {},
    stavVerze: () => '',
    zkontrolovatVerzi() {},
    stavInstalace: () => -1,
    smiInstalovat: () => false,
    aktualizovat: () => '',
    stavNazivo: () => false,
    nazivo() {},
    tempo: () => 'normalne',
    nastavTempo() {},
    stavZatreseni: () => false,
    zatreseni() {},
    stavSberu: () => false,
    sber() {},
    sberCas: () => false,
    sberMista() {},
    idZarizeni: () => '',
    podnet: () => false
  };

  // Návrat do appky (jako onResume v Androidu): nová poloha a případně jiná karta.
  document.addEventListener('visibilitychange', () => { if (!document.hidden && typeof window.naNavrat === 'function') window.naNavrat(); });
  // ať iPhone úložiště appky sám nesmaže
  if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(() => {});
  // nová verze appky se stáhne na pozadí a platí od dalšího otevření
  if ('serviceWorker' in navigator && location.protocol === 'https:') {
    window.addEventListener('load', () => { navigator.serviceWorker.register('sw.js').catch(() => {}); });
  }

  // V Safari (ne z plochy) rada, jak si Kartičku dát na plochu. Safari a appka na ploše mají karty každá zvlášť.
  if (ios && !naPlose && !ladeni) {
    window.addEventListener('load', () => {
      if (Date.now() - (+LS.get('karticka-plocha-zavreno') || 0) < 7 * 864e5) return; // zavřený pruh se ukáže zase za týden
      const pruh = document.createElement('div'); pruh.className = 'web-plocha'; pruh.setAttribute('role', 'note');
      const t = document.createElement('p');
      t.innerHTML = '<b>Dej si Kartičku na plochu.</b> Dole klepni na <span class="web-sdilet" aria-label="Sdílet"></span> Sdílet (v novém iOS je pod ⋯) a pak na Přidat na plochu. Karty pak přidávej až v Kartičce na ploše, Safari je má zvlášť.';
      const x = document.createElement('button'); x.type = 'button'; x.className = 'web-plocha-x'; x.setAttribute('aria-label', 'Zavřít'); x.textContent = '×';
      x.addEventListener('click', () => { pruh.remove(); LS.set('karticka-plocha-zavreno', String(Date.now())); });
      pruh.append(t, x);
      document.body.appendChild(pruh);
    });
  }
})();
