'use strict';
(function () {
  // Most do telefonu (Hlavni.java). Webová Kartička pro iPhone (karticka.adamhorak.cz/app, od 5.11.2026) má most
  // ve web.js (KartickaWeb): kódy kreslí bwip-js, čte zxing-wasm, karty jsou jen v úložišti prohlížeče.
  // V obyčejném prohlížeči bez web.js náhradní verze, ať jde rozhraní ladit.
  const V_TELEFONU = !!window.Karticka;
  const WEB = !V_TELEFONU && !!window.KartickaWeb;
  const KRESLI = V_TELEFONU || WEB; // umí nakreslit kód
  const N = window.Karticka || window.KartickaWeb || prohlizec();
  const $ = (s, r) => (r || document).querySelector(s);
  const umi = n => typeof N[n] === 'function'; // novější volání mostu: webová Kartička (web.js) je mít nemusí

  // ---------- drobnosti ----------

  function el(tag, a, ...deti) {
    const e = document.createElement(tag);
    if (a) for (const k in a) {
      const v = a[k];
      if (v == null || v === false) continue;
      if (k === 'class') e.className = v;
      else if (k === 'text') e.textContent = v;
      else if (k === 'style') e.style.cssText = v;
      else if (k.startsWith('on')) e.addEventListener(k.slice(2), v);
      else e.setAttribute(k, v === true ? '' : v);
    }
    for (const d of deti.flat()) if (d != null && d !== false) e.append(d.nodeType ? d : String(d));
    return e;
  }

  const IKONY = {
    plus: '<path d="M12 5v14M5 12h14"/>',
    nastaveni: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>',
    zpet: '<path d="M15 18l-6-6 6-6"/>',
    dal: '<path d="M9 18l6-6-6-6"/>',
    foto: '<path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/>',
    obrazek: '<rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><path d="M21 15l-5-5L5 21"/>',
    tuzka: '<path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4z"/>',
    spendlik: '<path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/>',
    lupa: '<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/>',
    hvezda: '<path d="M12 2.5l2.9 5.9 6.6 1-4.8 4.6 1.1 6.5L12 17.4l-5.8 3.1 1.1-6.5-4.8-4.6 6.6-1z"/>',
    slunce: '<circle cx="12" cy="12" r="4.5"/><path d="M12 1.5v2.5M12 20v2.5M4.6 4.6l1.8 1.8M17.6 17.6l1.8 1.8M1.5 12H4M20 12h2.5M4.6 19.4l1.8-1.8M17.6 6.4l1.8-1.8"/>',
    stitek: '<path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><path d="M7 7h.01"/>',
    darek: '<path d="M20 12v10H4V12M2 7h20v5H2zM12 22V7"/><path d="M12 7H7.5a2.5 2.5 0 0 1 0-5C11 2 12 7 12 7zM12 7h4.5a2.5 2.5 0 0 0 0-5C13 2 12 7 12 7z"/>',
    poradi: '<path d="M7 4v16M3 16l4 4 4-4M17 20V4M13 8l4-4 4 4"/>',
    uchop: '<path d="M4 8h16M4 12h16M4 16h16"/>',
    znovu: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8"/><path d="M3 3v5h5"/>',
    hodiny: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    widget: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
    stetec: '<circle cx="13.5" cy="6.5" r="1.5"/><circle cx="17.5" cy="10.5" r="1.5"/><circle cx="8.5" cy="7.5" r="1.5"/><circle cx="6.5" cy="12.5" r="1.5"/><path d="M12 2a10 10 0 0 0 0 20c1 0 1.7-.8 1.7-1.7 0-.4-.2-.8-.4-1.1-.3-.3-.4-.7-.4-1.1 0-.9.8-1.7 1.7-1.7H17a5 5 0 0 0 5-5C22 6 17.5 2 12 2z"/>',
    baterie: '<rect x="2" y="7" width="17" height="10" rx="2"/><path d="M22 11v2M6 10.5v3M9.5 10.5v3"/>',
    ulozit: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3"/>',
    nacist: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12"/>',
    sdilet: '<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="M8.6 13.5l6.8 4M15.4 6.5l-6.8 4"/>',
    poloha: '<path d="M3 11l19-9-9 19-2-8-8-2z"/>',
    krizek: '<path d="M6 6l12 12M18 6L6 18"/>',
    oko: '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>',
    okoZavrene: '<path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><path d="M1 1l22 22"/>',
    zamek: '<rect x="3" y="11" width="18" height="11" rx="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>',
    zprava: '<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
    mapa: '<path d="M1 6v16l7-4 8 4 7-4V2l-7 4-8-4-7 4z"/><path d="M8 2v16"/><path d="M16 6v16"/>',
    navigace: '<circle cx="12" cy="12" r="2"/><path d="M16.24 7.76a6 6 0 0 1 0 8.49M7.76 16.24a6 6 0 0 1 0-8.49M19.07 4.93a10 10 0 0 1 0 14.14M4.93 19.07a10 10 0 0 1 0-14.14"/>',
    zatrest: '<rect x="7" y="2" width="10" height="20" rx="2"/><path d="M3 8v8"/><path d="M21 8v8"/>'
  };
  function ikona(n) {
    const s = el('span', { class: 'ik', 'aria-hidden': 'true' });
    s.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' + IKONY[n] + '</svg>';
    return s;
  }

  function odkaz(text, akce) { return el('button', { type: 'button', class: 'odkaz', onclick: akce, text }); }
  function spoj(pole, oddel) { const o = []; pole.forEach((x, i) => { if (i) o.push(oddel); o.push(x); }); return o; }
  const TMAVA = '#1B1E24', BILA = '#FFFFFF', SEDA_ZNACKY = '#E6E8EC';
  function tvar(n, a, b, c) { return n === 1 ? a : n >= 2 && n <= 4 ? b : c; }
  function bezDiakritiky(s) { return s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase(); }
  function barvy(x) { return '--b:' + x.barva + ';--t:' + x.text; }
  function sirkaObsahu() { return Math.min(document.documentElement.clientWidth || 360, 640) - 32; }
  function noveId() { return 'k' + Date.now().toString(36) + Math.random().toString(36).slice(2, 6); }

  function hodiny(d) { return d.getHours() + ':' + String(d.getMinutes()).padStart(2, '0'); }
  function den(t) {
    const d = new Date(t), dnes = new Date();
    const pulnoc = new Date(dnes.getFullYear(), dnes.getMonth(), dnes.getDate()).getTime();
    if (t >= pulnoc) return 'dnes';
    if (t >= pulnoc - 864e5) return 'včera';
    return d.getDate() + '. ' + (d.getMonth() + 1) + '.' + (d.getFullYear() !== dnes.getFullYear() ? ' ' + d.getFullYear() : '');
  }
  function kdy(t) { return t ? den(t) + ' ' + hodiny(new Date(t)) : 'nikdy'; }

  const metru = r => r < 1000 ? r + ' m' : r / 1000 + ' km'; // kulaté vzdálenosti (kruhy na mapě, okruh v Nastavení)
  function km(m) {
    if (m < 1000) return Math.max(10, Math.round(m / 10) * 10) + ' m';
    if (m < 10000) return (m / 1000).toFixed(1).replace('.', ',') + ' km';
    return Math.round(m / 1000) + ' km';
  }

  // Dlouhá čísla po čtveřicích, ať se dají u pokladny přečíst a opsat.
  function hezke(kod) { return /^\d{9,}$/.test(kod) ? kod.replace(/(\d{4})(?=\d)/g, '$1 ') : kod; }
  // Na kartičce celé číslo (Adam ho chce vidět, karty jsou jen v jeho telefonu). Dlouhý text QR kódu by nic neřekl,
  // místo něj Další číslo na kartě (Mensa: QR je adresa ověření, na průkazu je členské číslo), jinak nic.
  function dlouhyKod(k) { return CTVERCOVE.has(k.format) && k.kod.length > 24; }
  function cisloKarty(k) { return dlouhyKod(k) ? k.cislo || '' : hezke(k.kod); }

  // ---------- formáty kódů ----------

  const FORMATY = ['EAN_13', 'EAN_8', 'UPC_A', 'UPC_E', 'CODE_128', 'CODE_39', 'CODE_93', 'CODABAR', 'ITF',
    'QR_CODE', 'DATA_MATRIX', 'PDF_417', 'AZTEC'];
  const NAZVY = { EAN_13: 'EAN-13', EAN_8: 'EAN-8', UPC_A: 'UPC-A', UPC_E: 'UPC-E', CODE_128: 'Code 128',
    CODE_39: 'Code 39', CODE_93: 'Code 93', CODABAR: 'Codabar', ITF: 'ITF', QR_CODE: 'QR kód',
    DATA_MATRIX: 'Data Matrix', PDF_417: 'PDF417', AZTEC: 'Aztec' };
  const NABIDKA = ['EAN_13', 'EAN_8', 'UPC_A', 'CODE_128', 'CODE_39', 'QR_CODE'];
  const CTVERCOVE = new Set(['QR_CODE', 'DATA_MATRIX', 'AZTEC']);
  const CISELNE = new Set(['AUTO', 'EAN_13', 'EAN_8', 'UPC_A', 'UPC_E', 'ITF']);
  // Řetězce, kde je kód v detailu u horního okraje (pokladna má čtečku, ke které se přikládá horní část telefonu).
  // Výchozí hodnota se do dat nezapisuje, karta ji přebije jen vlastním nahore (stejné pravidlo má i Jas.java).
  const NAHORE = new Set(['albert']);
  function kodNahore(k) { return typeof k.nahore === 'boolean' ? k.nahore : NAHORE.has(k.retezec); }

  function kontrolniCislice(k) {
    let s = 0;
    const n = k.length;
    for (let i = 0; i < n - 1; i++) { const c = +k[n - 2 - i]; s += i % 2 === 0 ? c * 3 : c; }
    return (10 - s % 10) % 10 === +k[n - 1];
  }
  function autoFormat(kod) {
    if (/^\d+$/.test(kod) && kontrolniCislice(kod)) {
      if (kod.length === 13) return 'EAN_13';
      if (kod.length === 8) return 'EAN_8';
      if (kod.length === 12) return 'UPC_A';
    }
    if (kod.length > 80 || /[^\x20-\x7e]/.test(kod)) return 'QR_CODE';
    return 'CODE_128';
  }

  // Kód kreslí telefon (ZXing), každý modul na celé pixely displeje. PNG se zobrazí 1:1 bez rozmazání.
  const kesKodu = new Map();
  function rozmerPng(url) {
    const b = atob(url.slice(22, 66));
    const u = i => b.charCodeAt(i);
    return [((u(16) << 24) | (u(17) << 16) | (u(18) << 8) | u(19)) >>> 0,
      ((u(20) << 24) | (u(21) << 16) | (u(22) << 8) | u(23)) >>> 0];
  }
  function obrazekKodu(format, kod, cssW, cssH) {
    if (!KRESLI) return el('div', { class: 'kod-nahradni', style: 'height:' + cssH + 'px', text: 'Kód se kreslí v telefonu' });
    const dpr = window.devicePixelRatio || 1;
    const w = Math.max(1, Math.round(cssW * dpr)), h = Math.max(1, Math.round(cssH * dpr));
    const klic = format + '\u0001' + kod + '\u0001' + w + '\u0001' + h;
    let url = kesKodu.get(klic);
    if (url === undefined) {
      url = N.obrazek(format, kod, w, h) || '';
      kesKodu.set(klic, url);
      if (kesKodu.size > 30) kesKodu.delete(kesKodu.keys().next().value);
    }
    if (!url) return el('div', { class: 'kod-chyba', text: 'Tohle číslo nejde nakreslit jako ' + (NAZVY[format] || format) });
    const [pw, ph] = rozmerPng(url);
    return el('img', { class: 'kod-img', src: url, alt: 'Čárový kód karty', style: 'width:' + (pw / dpr) + 'px;aspect-ratio:' + pw + '/' + ph });
  }

  // ---------- data ----------

  let RETEZCE = [];
  try { RETEZCE = JSON.parse(N.soubor('retezce.json') || '[]'); } catch (e) { RETEZCE = []; }
  const RETEZEC = {};
  for (const r of RETEZCE) RETEZEC[r.id] = r;
  // Karta založená jako Jiný obchod (třeba z Klarny) dostane řetězec podle názvu, ať ji appka najde na mapě.
  const klicNazvu = s => String(s).normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]/g, '');
  const PODLE_NAZVU = {};
  for (const r of RETEZCE) PODLE_NAZVU[klicNazvu(r.nazev)] = r.id;

  // Loga obchodů jsou přibalená v appce (loga/<klíč>.webp), appka je nestahuje a načtou se hned.
  // znacky.json: {loga: [klíče s logem], barvy: {klíč: [barva, text]}} pro obchody mimo mapu (Motiko, Flying Blue…),
  // dlazdice: {klíč: [podklad, zvětšení, dx, dy]} pro logo v mapě okolí.
  let ZNACKY = { loga: [], barvy: {} };
  try { ZNACKY = Object.assign(ZNACKY, JSON.parse(N.soubor('znacky.json') || '{}')); } catch (e) { /* bez log */ }
  const LOGA = new Set(ZNACKY.loga || []);
  const DALEKO = new Set(ZNACKY.daleko || []);
  function klicZnacky(x) {
    if (x.retezec && LOGA.has(x.retezec)) return x.retezec;
    const n = klicNazvu(x.nazev || '');
    if (LOGA.has(n)) return n;
    // vlastní karta pojmenovaná podle obchodu i s něčím navíc („Pardubická karta Adam“) dostane jeho logo
    const r = x.retezec ? '' : retezecProNazev(x.nazev);
    return r && LOGA.has(r) ? r : '';
  }
  function logo(x, trida) {
    const id = klicZnacky(x);
    return id ? el('span', { class: 'logo' + (trida ? ' ' + trida : '') },
      el('img', { src: 'loga/' + id + '.webp', alt: '', decoding: 'sync', draggable: 'false' })) : null;
  }
  // Logo v mapě jako ikona appky: celá dlaždice v barvě loga, malé logo zvětšené, průhledné na bílém s okrajem.
  // Podklad, zvětšení a posun středu spočítá tools/loga_dlazdice.py do znacky.json "dlazdice".
  function dlazdiceLoga(x) {
    const id = klicZnacky(x);
    if (!id) return null;
    const [podklad, zoom, dx, dy] = (ZNACKY.dlazdice || {})[id] || [BILA, 0.78, 0, 0];
    return el('span', { class: 'm-logo', style: 'background:' + podklad },
      el('img', { src: 'loga/' + id + '.webp', alt: '', decoding: 'sync', draggable: 'false',
        style: 'transform:scale(' + zoom + ') translate(' + dx + '%,' + dy + '%)' }));
  }

  let _prodejny = null;
  function prodejny() {
    if (!_prodejny) {
      try { _prodejny = JSON.parse(N.soubor('prodejny.json') || '{}'); } catch (e) { _prodejny = {}; }
    }
    return _prodejny;
  }
  function body(k) { return (k.retezec && prodejny()[k.retezec]) || []; }
  // Místa pokladen ze sběru (pokladny.json, stejný tvar): kde kartu řetězce ukázaly aspoň 3 různé telefony nebo co
  // Adam schválil ve správě. Leží i u prodejen z mapy, jen přesněji než střed budovy z OSM.
  let _pokladny = null;
  function pokladny() {
    if (!_pokladny) {
      try { _pokladny = JSON.parse(N.soubor('pokladny.json') || '{}'); } catch (e) { _pokladny = {}; }
    }
    return _pokladny;
  }
  function bodyPokladen(k) { return (k.retezec && pokladny()[k.retezec]) || []; }

  // Body a kupóny: vernost.json {klíč značky: {app: balíček appky, web: stránka programu}}. Kartička body sama
  // nezjišťuje (z internetu bere jen číslo nové verze), jen otevře appku obchodu, a když v telefonu není, jeho web.
  let VERNOST = {};
  try { VERNOST = JSON.parse(N.soubor('vernost.json') || '{}'); } catch (e) { VERNOST = {}; }
  function program(k) {
    const n = klicNazvu(k.nazev || '');
    // vlastní karta pojmenovaná podle obchodu i s něčím navíc („Pardubická karta 2030“) dostane jeho tlačítko
    const p = VERNOST[k.retezec] || VERNOST[PODLE_NAZVU[n]] || VERNOST[n] || (k.retezec ? null : VERNOST[retezecProNazev(k.nazev)]);
    return p && (p.app || p.web) ? p : null;
  }
  // Akce a leták: akce.json {klíč značky: stránka s akční nabídkou, letákem nebo výprodejem}. Kartička jen otevře
  // prohlížeč na klepnutí, nabídky nestahuje, nehlídá a nic neposílá. Načte se až při prvním detailu karty.
  let _akce = null;
  function akce(k) {
    if (!_akce) {
      try { _akce = JSON.parse(N.soubor('akce.json') || '{}'); } catch (e) { _akce = {}; }
    }
    const n = klicNazvu(k.nazev || '');
    const u = _akce[k.retezec] || _akce[PODLE_NAZVU[n]] || _akce[n] || (k.retezec ? '' : _akce[retezecProNazev(k.nazev)]);
    return typeof u === 'string' && u.startsWith('https://') ? u : '';
  }
  // Karta v appce (McDonald's): kód pro pokladnu ukazuje jen appka obchodu, Kartička ji otevře, jinak web programu.
  // Vlastní odkaz na body (osobní stránka karty) má přednost před appkou a webem programu.
  function otevriAppku(k) {
    if (k.odkaz && !k.appka) return N.otevriProgram('', k.odkaz);
    const p = program(k) || {};
    return N.otevriProgram(k.appka || p.app || '', p.web || '');
  }
  const BALIK = /^[A-Za-z]\w*(\.[A-Za-z]\w*)+$/;

  const BARVA = /^#[0-9a-f]{6}$/i;
  const PALETA = [TMAVA, '#E75294', '#449D8B', '#57AB27', '#666CAB', '#F39C59',
    '#D7263D', '#1565C0', '#F2C230', '#7B4FA0', '#8D6E63', '#9AA3AE'];
  function textNa(b) {
    const r = parseInt(b.slice(1, 3), 16), g = parseInt(b.slice(3, 5), 16), m = parseInt(b.slice(5, 7), 16);
    return (0.299 * r + 0.587 * g + 0.114 * m) / 255 > 0.62 ? TMAVA : BILA;
  }

  // Heslo k účtu u karty je v datech jen zamčené klíčem telefonu ("ks1:" + base64), otevřené se nikam neukládá.
  const HESLO_KS = /^ks1:[A-Za-z0-9+/=]{16,4000}$/;

  // Odkaz na body: jen https, zůstává v telefonu a v záloze, nikam se neposílá.
  function cistyOdkaz(s) {
    s = String(s || '').trim();
    if (!s || s.length > 500 || /\s/.test(s)) return '';
    try {
      const u = new URL(s);
      return u.protocol === 'https:' && u.hostname ? u.href : '';
    } catch (e) { return ''; }
  }

  function ocisti(k) {
    if (!k || typeof k !== 'object' || typeof k.kod !== 'string') return null;
    // bez čísla jen karta v appce obchodu (appka = balíček, který otevře)
    const appka = !k.kod && typeof k.appka === 'string' && k.appka.length <= 150 && BALIK.test(k.appka) ? k.appka : '';
    if (!k.kod && !appka) return null;
    const mista = Array.isArray(k.mista) ? k.mista.filter(m => Array.isArray(m) && isFinite(m[0]) && isFinite(m[1])
      && Math.abs(m[0]) <= 90 && Math.abs(m[1]) <= 180).slice(-100).map(m => [+m[0], +m[1]]) : [];
    const wifi = Array.isArray(k.wifi) ? k.wifi.map(cistyOtisk).filter(Boolean).slice(-20) : [];
    const nazev = String(k.nazev || 'Karta').slice(0, 60);
    const karta = {
      id: typeof k.id === 'string' && k.id ? k.id.slice(0, 40) : noveId(),
      // řetězec, který z katalogu vypadl (neměl skutečnou kartu), z karty udělá vlastní obchod
      retezec: (typeof k.retezec === 'string' && (RETEZEC[k.retezec] || !RETEZCE.length) ? k.retezec.slice(0, 30) : '') || PODLE_NAZVU[klicNazvu(nazev)] || '',
      nazev,
      barva: BARVA.test(k.barva) ? k.barva : TMAVA,
      text: BARVA.test(k.text) ? k.text : BILA,
      format: FORMATY.includes(k.format) ? k.format : 'CODE_128',
      kod: k.kod.slice(0, 2000),
      appka,
      cislo: String(k.cislo || '').trim().slice(0, 60), // další číslo na kartě (třeba horní číslo ISIC)
      poznamka: String(k.poznamka || '').slice(0, 500),
      odkaz: cistyOdkaz(k.odkaz),
      ucet: String(k.ucet || '').slice(0, 200),
      heslo: typeof k.heslo === 'string' && HESLO_KS.test(k.heslo) ? k.heslo : '',
      pin: typeof k.pin === 'string' && HESLO_KS.test(k.pin) ? k.pin : '', // PIN karty (třeba dm), zamčený jako heslo
      oblibena: k.oblibena === true,
      pouzito: Math.max(0, Math.floor(+k.pouzito || 0)),
      naposledy: Math.max(0, +k.naposledy || 0),
      mista,
      wifi
    };
    if (typeof k.nahore === 'boolean') karta.nahore = k.nahore; // jen když si ho karta nastavila sama
    return karta;
  }

  // Otisk Wi-Fi: {BSSID bez dvojteček: síla v dBm}, nejvýš 15 nejsilnějších sítí.
  function cistyOtisk(F) {
    if (!F || typeof F !== 'object' || Array.isArray(F)) return null;
    const o = {};
    Object.keys(F).filter(b => /^[0-9a-f]{12}$/.test(b) && isFinite(F[b]) && F[b] > -100 && F[b] < 0)
      .sort((x, y) => F[y] - F[x]).slice(0, 15).forEach(b => { o[b] = Math.round(F[b]); });
    return Object.keys(o).length >= 3 ? o : null;
  }

  // Historie ukázání: [čas, id karty, odkud, šířka?, délka?]. Odkud: a = appka ji ukázala sama nahoře,
  // t = vybraná přes Taky tady, o = otevřená v appce, w = klepnutí na widget, j = sluníčko u widgetu.
  // Kdy pokladna kód opravdu načetla, telefon nepozná; tohle je, kdy byla karta na displeji.
  const ODKUD = { a: 'Appka ji ukázala sama', t: 'Vybraná přes Taky tady', o: 'Otevřená v appce', w: 'Z widgetu', j: 'Jas u widgetu', m: 'Z mapy okolí' };
  const HISTORIE_MAX = 5000;
  function cistaHistorie(h) {
    if (!Array.isArray(h)) return [];
    const o = [];
    for (const x of h) {
      if (!Array.isArray(x) || !(+x[0] > 0) || typeof x[1] !== 'string' || !x[1]) continue;
      const z = [+x[0], x[1].slice(0, 40), ODKUD[x[2]] ? x[2] : 'o'];
      if (typeof x[3] === 'number' && typeof x[4] === 'number' && Math.abs(x[3]) <= 90 && Math.abs(x[4]) <= 180) {
        z.push(x[3], x[4]);
        if (typeof x[5] === 'number' && x[5] >= 0) z.push(Math.min(9999, Math.round(x[5]))); // přesnost polohy v m
      }
      o.push(z);
    }
    return o.sort((a, b) => a[0] - b[0]).slice(-HISTORIE_MAX);
  }

  // Karta obchodu z mapy má barvy toho obchodu (vybrat si je nejde), po nové verzi appky se srovnají.
  // Karta jiného obchodu dostane barvy značky jen jednou (verze 2), pak si je člověk může změnit.
  function barvyZnacek(karty, poprve) {
    let zmena = false;
    for (const k of karty) {
      const r = RETEZEC[k.retezec];
      const z = r ? [r.barva, r.text] : poprve && (ZNACKY.barvy || {})[klicNazvu(k.nazev)];
      if (z && BARVA.test(z[0]) && BARVA.test(z[1]) && (k.barva !== z[0] || k.text !== z[1])) {
        k.barva = z[0];
        k.text = z[1];
        zmena = true;
      }
    }
    return zmena;
  }

  // Nastavení (jen v telefonu a v záloze): pořadí karet, kam vede klepnutí na widget, jak dlouho kód svítí naplno.
  const PORADI = ['misto', 'chytre', 'pouziti', 'abeceda', 'vlastni'];
  // Jas naplno: sekundy po klepnutí na kód, 0 = svítí hned po otevření karty, dokud je otevřená. Mimo JASY je to
  // vlastní počet sekund (1 až JAS_MAX, nejvíc jako trvalé svícení po dvojkliku v Jas.java).
  const JASY = [5, 15, 0], JAS_MAX = 600;
  // Řazení obchodů při přidání karty. Oblíbenost appka nezná (nic o lidech nesbírá), nejbližší náhradou je počet prodejen.
  const RAZENI = [['druh', 'Druh'], ['blizko', 'Nejblíž'], ['prodejny', 'Nejrozšířenější'], ['abc', 'A–Z']];
  const vlastniJas = s => Number.isInteger(s) && s >= 1 && s <= JAS_MAX;
  // Nahoře na hlavní obrazovce (mimo prodejnu): kolik nejbližších prodejen s kartou a do jaké vzdálenosti (0 = jakkoli daleko).
  // Adam 5.11.2026: výchozí jen ta jedna nejbližší.
  const POCTY_BLIZKO = [1, 2, 3, 5], OKRUHY = [500, 1000, 5000, 20000, 0];
  const VZHLEDY = ['slunce', 'svetly', 'tmavy'];
  function cisteNastaveni(n) {
    n = n && typeof n === 'object' && !Array.isArray(n) ? n : {};
    return {
      // výchozí je podle místa; "chytre" bez příznaku misto je staré výchozí pořadí, ne volba, převede se taky
      poradi: PORADI.includes(n.poradi) && !(n.poradi === 'chytre' && n.misto !== true) ? n.poradi : 'misto',
      misto: true,
      vlastni: Array.isArray(n.vlastni) ? n.vlastni.filter(id => typeof id === 'string' && id).map(id => id.slice(0, 40)).slice(0, 2000) : [],
      widget: n.widget === 'karta' ? 'karta' : 'hlavni',
      jas: n.jas === 0 || vlastniJas(n.jas) ? n.jas : 5,
      jasVlastni: vlastniJas(n.jasVlastni) ? n.jasVlastni : vlastniJas(n.jas) && !JASY.includes(n.jas) ? n.jas : 30,
      // sluníčko na widgetu: velký kód nahoře jen u karet, které ho mají (karta), nebo u všech (vse); co je pod ním
      panel: n.panel === 'vse' ? 'vse' : 'karta',
      razeni: RAZENI.some(r => r[0] === n.razeni) ? n.razeni : 'druh', // seznam obchodů při přidání karty
      skrytMoje: n.skrytMoje !== false, // v tom seznamu skrýt obchody, které už kartu mají (výchozí zapnuto)
      pozadi: n.pozadi === 'tlumena' || n.pozadi === 'cerna' ? n.pozadi : 'plocha',
      blizko: POCTY_BLIZKO.includes(n.blizko) ? n.blizko : 1,
      okruh: OKRUHY.includes(n.okruh) ? n.okruh : 0,
      vzhled: VZHLEDY.includes(n.vzhled) ? n.vzhled : 'slunce', // tmavý režim: podle slunce, světlý, tmavý
      sberOznamen: n.sberOznamen === true // pruh Místa obchodů pomáhají mapě se už ukázal (dřív nic neodchází)
    };
  }

  // Karty z textu souboru, null když v něm nejsou.
  function rozeberData(t) {
    try {
      const d = JSON.parse(t);
      if (!d || !Array.isArray(d.karty)) return null;
      const karty = d.karty.map(ocisti).filter(Boolean);
      const zmena = barvyZnacek(karty, !(d.verze >= 2)) || !(d.verze >= 2)
        || !(d.nastaveni && d.nastaveni.misto === true); // převod na pořadí podle místa uložit hned (čte ho i widget)
      return { data: { verze: 2, karty, historie: cistaHistorie(d.historie), nastaveni: cisteNastaveni(d.nastaveni) }, zmena };
    } catch (e) { return null; }
  }
  // "" = karty ještě nejsou. Co nejde přečíst, se nesmí přepsat prázdným: zkusí se předchozí uložená verze,
  // a když ani ta, appka začne prázdná a neukládá, dokud to člověk nepotvrdí (nebo nenačte zálohu).
  function nactiData() {
    const prazdna = () => ({ verze: 2, karty: [], historie: [], nastaveni: cisteNastaveni() });
    const t = N.karty();
    if (t === '') return { data: prazdna(), zmena: false };
    const r = typeof t === 'string' ? rozeberData(t) : null;
    if (r) return r;
    const p = N.kartyPredchozi();
    const q = p ? rozeberData(p) : null;
    if (q) return { data: q.data, zmena: true, obnoveno: true };
    return { data: prazdna(), zmena: false, necitelne: true };
  }
  const nacteno = nactiData();
  let data = nacteno.data;
  let neukladat = !!nacteno.necitelne;
  function ulozData() {
    // zpráva až po startu: první uložení běží dřív, než je připravený pruh s oznámením
    if (neukladat) return void setTimeout(() => oznam('Karty nejdou přečíst, změna se neuložila.'));
    if (N.uloz(JSON.stringify(data)) === false) setTimeout(() => oznam('Změnu se nepodařilo uložit. Zkus to znovu.'));
  }
  if (nacteno.zmena) ulozData();
  function najdi(id) { return data.karty.find(k => k.id === id); }

  // Moje karty v pořadí podle Nastavení:
  // podle místa (výchozí) = nahoře karty obchodů tady kolem (ukázaná první), pod nimi podle toho, jak blízko je
  //   nejbližší prodejna (pásma po 25 %, v pásmu oblíbené a častěji ukazované výš), karty bez prodejen pod nimi,
  //   obchody v cizině (třeba Conad) úplně dole;
  // chytře = obchody tady, pod nimi oblíbené (hvězdička v detailu), pak podle toho, kolikrát se karta ukázala a jak
  //   blízko je její nejbližší prodejna;
  // používání = oblíbené, pak počet ukázání; abecedně = oblíbené, pak název; vlastní = jak si je člověk poskládal.
  // Stejně řadí widget (Vyber.podleNastaveni).
  const jmenem = (a, b) => a.nazev.localeCompare(b.nazev, 'cs');
  function serazene() {
    const n = data.nastaveni;
    if (n.poradi === 'pouziti') return data.karty.slice().sort((a, b) => b.oblibena - a.oblibena
      || b.pouzito - a.pouzito || b.naposledy - a.naposledy || jmenem(a, b));
    if (n.poradi === 'abeceda') return data.karty.slice().sort((a, b) => b.oblibena - a.oblibena || jmenem(a, b));
    if (n.poradi === 'vlastni') {
      // karty přidané po poskládání jdou na konec (podle místa)
      const poz = new Map(n.vlastni.map((id, i) => [id, i]));
      return data.karty.filter(k => poz.has(k.id)).sort((a, b) => poz.get(a.id) - poz.get(b.id))
        .concat(podleMista(data.karty.filter(k => !poz.has(k.id))));
    }
    if (n.poradi === 'chytre') return chytre(data.karty);
    return podleMista(data.karty);
  }
  // Karty obchodů, kde člověk právě je (ukázaná první): v pořadí podle místa i chytře jsou nahoře.
  function tadyNahore(karty, p) {
    const tady = p ? okoli(p).blizko.map(b => b.k).filter(k => karty.includes(k)) : [];
    const i = tady.findIndex(k => k.id === pol.hero);
    if (i > 0) tady.unshift(tady.splice(i, 1)[0]);
    // pod kartou, kterou appka ukazuje, ostatní obchody tady od nejbližšího (kamarád 5.11.2026: nebylo to podle vzdálenosti)
    if (tady.length > 2) {
      const v = new Map(tady.map(k => [k, vzdalenostKarty(k, p)]));
      tady.splice(1, tady.length - 1, ...tady.slice(1).sort((a, b) => v.get(a) - v.get(b)));
    }
    return tady;
  }
  function podleMista(karty) {
    const p = cerstva();
    const tady = tadyNahore(karty, p);
    const kde = p || pol.p; // starší poloha na město stačí
    const m = new Map(karty.map(k => [k, mistoKarty(k, kde)]));
    return tady.concat(karty.filter(k => !tady.includes(k)).sort((a, b) => m.get(a) - m.get(b)
      || b.oblibena - a.oblibena || b.pouzito - a.pouzito || b.naposledy - a.naposledy || jmenem(a, b)));
  }
  // Místo karty v pořadí: vzdálenost nejbližší prodejny po 10 m (stejně daleko rozhodne oblíbenost a používání);
  // bez prodejen nebo bez polohy pod obchody, obchody v cizině úplně dole. Stejně Vyber.pasmoMista ve widgetu.
  // Do 5.11.2026 to byla pásma po 25 % vzdálenosti a v pásmu vyhrávala oblíbená karta: kamarádovi to nepřišlo
  // seřazené od nejbližšího.
  function mistoKarty(k, p) {
    if (daleko(k)) return 1e12;
    if (!p) return 1e9;
    const v = vzdalenostKarty(k, p);
    if (!(v <= 3e5)) return 1e9;
    return Math.round(v / 10);
  }
  function chytre(karty) {
    const p = cerstva();
    const tady = tadyNahore(karty, p);
    const kde = p || pol.p; // starší poloha na město stačí
    const s = new Map(karty.map(k => [k, skoreKarty(k, kde)]));
    return tady.concat(karty.filter(k => !tady.includes(k)).sort((a, b) => b.oblibena - a.oblibena
      || s.get(b).s - s.get(a).s || s.get(a).v - s.get(b).v || jmenem(a, b)));
  }
  // Každé další ukázání váží o něco míň (10× vs. 20× je menší rozdíl než 0× vs. 2×), blízká prodejna přidá body.
  function skoreKarty(k, p) {
    const s = 2 * Math.log2(1 + k.pouzito);
    if (daleko(k)) return { s: s - 20, v: 1e12 };
    if (!p) return { s, v: 1e12 };
    const v = vzdalenostKarty(k, p);
    if (v === Infinity) return { s: s - 1, v: 1e12 }; // karta bez prodejen (letenky, e-shop): kousek pod obchody v okolí
    return { s: s + (v < 500 ? 4 : v < 2000 ? 3 : v < 1e4 ? 2 : v < 5e4 ? 1 : v < 3e5 ? 0 : -3), v };
  }
  // Obchod, který v mapě není a je v cizině (znacky.json "daleko"), dokud k němu člověk neuloží vlastní místo.
  function daleko(k) { return !k.retezec && !k.mista.length && DALEKO.has(klicNazvu(k.nazev)); }

  // Karta se ukázala u pokladny: jednou za návštěvu se připočte použití, widget dostane tuhle kartu.
  // Do historie jde každé ukázání, jen stejná karta znovu do 3 minut (návrat z jiné appky k pokladně) je jeden záznam.
  // bezMista: karta otevřená z mapy okolí jinde, než člověk stojí; poloha k ní do historie (a do sběru) nepatří.
  function ukazana(k, odkud, bezMista) {
    const t = Date.now();
    const pridal = t - k.naposledy > 30 * 60000;
    if (pridal) k.pouzito++;
    k.naposledy = t;
    const zaznam = zapis(t, k.id, odkud || 'o', bezMista ? null : cerstva(2 * 60000));
    ulozData();
    N.posledni(k.id);
    return { pridal, zaznam };
  }
  function zapis(t, id, odkud, p) {
    const h = data.historie;
    for (let i = h.length - 1; i >= 0 && Math.abs(t - h[i][0]) < 3 * 60000; i--) if (h[i][1] === id) return null;
    const z = [t, id, odkud];
    if (p) z.push(Math.round(p.lat * 1e4) / 1e4, Math.round(p.lon * 1e4) / 1e4, Math.min(9999, Math.round(p.presnost)));
    h.push(z);
    if (h.length && h[h.length - 2] && h[h.length - 2][0] > t) h.sort((a, b) => a[0] - b[0]);
    if (h.length > HISTORIE_MAX) h.splice(0, h.length - HISTORIE_MAX);
    return z;
  }
  // Sluníčko u widgetu zapisuje telefon zvlášť (appka u toho neběží), tady se převezme do historie.
  function prevezmiJasy() {
    if (typeof N.jasy !== 'function') return;
    const t = N.jasy() || '';
    let pridano = false;
    for (const x of t.split(',')) {
      const m = /^(\d{10,14}):([\w-]{1,40})$/.exec(x.trim());
      if (m && najdi(m[2]) && zapis(+m[1], m[2], 'j', null)) pridano = true;
    }
    if (pridano) ulozData();
  }

  // ---------- poloha a nejbližší prodejna ----------

  // vybrany: karta zvolená přes Taky tady {id, lat, lon, cas}; platí jen na tom místě a chvíli.
  // auto: karta, kterou appka ukázala sama, ať jde vrátit její započtení, když ji člověk hned přepne.
  // wifi: sítě kolem teď {s: {BSSID: dBm}, cas}.
  const pol = { p: null, stav: '', hledam: KRESLI, pokusy: 0, casovac: 0, vybrany: null, hero: null, heroKde: null, auto: null, klic: null, wifi: null };
  let cekaMisto = null;
  const STARA = 20 * 60000, AKTUALNI = 2 * 60000;

  // stara: poloha, kterou člověk zahodil tlačítkem Zjistit polohu znovu (resetPolohy); platí jen pro mapu okolí
  function cerstva(max) { return pol.p && !pol.p.stara && Date.now() - pol.p.cas < (max || STARA) ? pol.p : null; }
  function maMapu() { return data.karty.some(k => body(k).length || k.mista.length || bodyPokladen(k).length); }

  function vzdalenost(a, b, c, d) {
    const kx = Math.cos((a + c) * Math.PI / 360) * 111320;
    const dx = (d - b) * kx, dy = (c - a) * 110574;
    return Math.sqrt(dx * dx + dy * dy);
  }
  function vzdalenostKarty(k, p) {
    let nej = Infinity;
    for (const m of body(k)) {
      if (Math.abs(m[0] - p.lat) > 0.3) continue;
      const v = vzdalenost(p.lat, p.lon, m[0], m[1]);
      if (v < nej) nej = v;
    }
    for (const m of k.mista) { const v = vzdalenost(p.lat, p.lon, m[0], m[1]); if (v < nej) nej = v; }
    for (const m of bodyPokladen(k)) { const v = vzdalenost(p.lat, p.lon, m[0], m[1]); if (v < nej) nej = v; }
    if (nej === Infinity && body(k).length) {
      for (const m of body(k)) { const v = vzdalenost(p.lat, p.lon, m[0], m[1]); if (v < nej) nej = v; }
    }
    return nej;
  }
  function vzdalenostMist(k, p) {
    let nej = Infinity;
    for (const m of k.mista) { const v = vzdalenost(p.lat, p.lon, m[0], m[1]); if (v < nej) nej = v; }
    return nej;
  }
  function vzdalenostPokladen(k, p) {
    let nej = Infinity;
    for (const m of bodyPokladen(k)) { const v = vzdalenost(p.lat, p.lon, m[0], m[1]); if (v < nej) nej = v; }
    return nej;
  }

  // ---------- Wi-Fi kolem: obchody nad sebou ----------
  // GPS uvnitř nerozliší patra (výška skáče o víc než celé patro). Telefon ale vidí okolní Wi-Fi sítě
  // (bez připojení a bez internetu) a každé patro i každý obchod má jinou sadu sítí a jinak silných.
  // Když člověk sám řekne, kde je (Taky tady, Jsem v prodejně, otevře kartu u prodejny), appka si
  // k té kartě uloží otisk sítí. Příště rozhodne podobnost s otiskem, ale jen mezi kartami, které sedí i s polohou.

  function nactiWifi() {
    let s = null;
    try {
      const a = JSON.parse((typeof N.wifi === 'function' && N.wifi()) || '[]');
      if (Array.isArray(a)) for (const x of a) {
        const b = x && typeof x.b === 'string' ? x.b.toLowerCase().replace(/[^0-9a-f]/g, '') : '';
        if (b.length === 12 && isFinite(x.s) && x.s > -100 && x.s < 0) (s || (s = {}))[b] = Math.round(x.s);
      }
    } catch (e) { s = null; }
    pol.wifi = s && Object.keys(s).length >= 3 ? { s, cas: Date.now() } : null;
  }
  window.naWifi = function () { nactiWifi(); vykresliTady(); };
  function wifiTed() { return pol.wifi && Date.now() - pol.wifi.cas < 2 * 60000 ? pol.wifi.s : null; }

  // <pravidla> Stejná pravidla má widget v Pravidla.java. tools/test_pravidla.py tenhle blok vyřízne, pustí ho
  // i Javu na tisících náhodných vstupů a build bez shody neprojde. Kdo mění tady, mění i tam.
  const WIFI_SEDI = 0.35; // od téhle shody otisku rozhoduje Wi-Fi (patro, obchod nad obchodem), ne poloha
  const sila = r => Math.max(0, Math.min(70, r + 100));
  // 0 = nic společného, 1 = stejné sítě stejně silné. Síť, která je teď silná a v otisku chybí, se počítá proti;
  // slabší sítě, než byla nejslabší v otisku, se nepočítají (otisk má jen 15 nejsilnějších).
  function podobnost(F, S) {
    let nejslabsi = Infinity, spolu = 0, celkem = 0;
    for (const b in F) {
      const f = sila(F[b]), s = b in S ? sila(S[b]) : 0;
      spolu += Math.min(f, s);
      celkem += Math.max(f, s);
      if (F[b] < nejslabsi) nejslabsi = F[b];
    }
    for (const b in S) if (!(b in F) && S[b] > nejslabsi) celkem += sila(S[b]);
    return celkem ? spolu / celkem : 0;
  }
  // Úroveň pořadí karty u obchodu, menší vyhrává: w = shoda Wi-Fi, m = metry k vlastnímu místu, c = k pokladně
  // ze sběru, v = k nejbližšímu bodu vůbec, p = přesnost polohy. Wi-Fi pod nulou, vlastní místo od 0, pokladna od 500, mapa od 1000.
  function urovenKarty(w, m, c, v, p) {
    const tesne = Math.max(35, Math.min(p, 75));
    const kosMoje = Math.max(20, Math.min(p, 60));
    const kos = Math.max(40, Math.min(p, 100));
    if (w >= WIFI_SEDI) return -100 + Math.round((1 - w) / 0.08);
    if (m <= tesne) return Math.round(m / kosMoje);
    if (c <= tesne) return 500 + Math.round(c / kosMoje);
    return 1000 + Math.round(v / kos);
  }
  // Ukázaná karta (úroveň b) se drží proti nejlepší (nej): stejně dobrá, nebo člověk stojí, kde se ukázala (naMiste).
  // Nedrží se, když jiný obchod poznala Wi-Fi nebo když má jiná karta potvrzené místo a držená jen bod z mapy.
  function drziKartu(b, nej, naMiste) {
    if (b === nej) return true;
    if (nej < 0 && b >= 0) return false;
    if (nej < 1000 && b >= 1000) return false;
    return naMiste;
  }
  // </pravidla>
  function shodaWifi(k, S) {
    let nej = 0;
    for (const F of k.wifi) { const x = podobnost(F, S); if (x > nej) nej = x; }
    return nej;
  }
  function otisk(S) {
    const o = {};
    Object.keys(S).sort((x, y) => S[y] - S[x]).slice(0, 15).forEach(b => { o[b] = S[b]; });
    return o;
  }
  // Tady je tahle karta: skoro stejný otisk se přepíše čerstvým, jinak přibude (nejvýš 20 na kartu).
  function naucWifi(k) {
    const S = wifiTed();
    if (!S) return;
    let i = -1, nej = 0;
    k.wifi.forEach((G, j) => { const x = podobnost(G, S); if (x > nej) { nej = x; i = j; } });
    if (nej >= 0.7) k.wifi.splice(i, 1);
    k.wifi.push(otisk(S));
    if (k.wifi.length > 20) k.wifi.shift();
  }
  // Tady tahle karta není (člověk ji přepnul na jinou): otisky tohohle místa zapomene.
  function odnaucWifi(k) {
    const S = wifiTed();
    if (S) k.wifi = k.wifi.filter(G => podobnost(G, S) < 0.6);
  }

  // Pořadí karet kolem polohy. V obchoďáku jsou prodejny pár metrů od sebe (i nad sebou) a poloha uvnitř skáče o desítky metrů:
  // 1. přednost má karta, jejíž otisk Wi-Fi sedí s tím, co telefon vidí teď (víc podobná vyhrává);
  // 2. pak karta, kterou tu člověk už sám vybral (Jsem v prodejně, Taky tady), vyhrává nejbližší takové místo;
  //    hned za ní místa pokladen, kde kartu řetězce ukázaly aspoň 3 různé telefony (pokladny.json);
  // 3. pak prodejny z mapy, ale nahrubo: v rámci nepřesnosti polohy se berou jako stejně daleko
  //    a rozhoduje, kterou kartu člověk používá častěji a naposledy.
  // u = úroveň pořadí; karty se stejnou úrovní jsou pro polohu nerozlišitelné.
  function okoli(p) {
    const dosah = 120 + Math.min(p.presnost, 200);
    const S = wifiTed();
    const blizko = [];
    let nej = null;
    for (const k of data.karty) {
      const v = vzdalenostKarty(k, p);
      if (!isFinite(v)) continue;
      if (v <= dosah) {
        const w = S && k.wifi.length ? shodaWifi(k, S) : 0;
        blizko.push({ k, v, u: urovenKarty(w, vzdalenostMist(k, p), vzdalenostPokladen(k, p), v, p.presnost) });
      }
      if (!nej || v < nej.v) nej = { k, v };
    }
    blizko.sort((a, b) => a.u - b.u || b.k.pouzito - a.k.pouzito || b.k.naposledy - a.k.naposledy);
    return { blizko, nej };
  }

  function platnyVyber(p) {
    const v = pol.vybrany;
    return v && Date.now() - v.cas < 30 * 60000 && vzdalenost(p.lat, p.lon, v.lat, v.lon) <= Math.max(80, p.presnost) ? v.id : null;
  }

  // Ukázaná karta se drží, dokud je stejně dobrá jako nejlepší, nebo dokud člověk stojí na stejném místě
  // (třeba se vrátí z bankovní appky k pokladně), ať se kód nepřehodí pod rukama.
  function drzi(b, nejlepsi, p) {
    if (!b) return false;
    const h = pol.heroKde;
    return drziKartu(b.u, nejlepsi.u, !!h && Date.now() - h.cas < 10 * 60000
      && vzdalenost(p.lat, p.lon, h.lat, h.lon) <= Math.max(60, p.presnost));
  }

  function pridejMisto(k, p) {
    if (k.mista.some(m => vzdalenost(p.lat, p.lon, m[0], m[1]) < 25)) return;
    k.mista.push([Math.round(p.lat * 1e5) / 1e5, Math.round(p.lon * 1e5) / 1e5]);
    if (k.mista.length > 100) k.mista.shift();
  }

  // Taky tady: člověk ví líp než GPS. Volba platí na tomhle místě, a když je poloha přesná, appka si místo zapamatuje.
  // Okolní Wi-Fi si zapamatuje vždy: příště tu vybraná karta vyhraje i nad tou, která tu byla nahoře.
  function vyber(k) {
    const a = pol.auto;
    if (a && a.id === pol.hero && a.id !== k.id && Date.now() - a.cas < 5 * 60000) {
      const h = najdi(a.id);
      if (h && a.pridal) { h.pouzito = Math.max(0, h.pouzito - 1); h.naposledy = a.naposledy; }
      const i = a.zaznam ? data.historie.indexOf(a.zaznam) : -1;
      if (i >= 0) data.historie.splice(i, 1); // ukázala se jiná karta, než u pokladny byla potřeba
    }
    pol.auto = null;
    const predtim = pol.hero && pol.hero !== k.id ? najdi(pol.hero) : null;
    if (predtim) odnaucWifi(predtim);
    naucWifi(k);
    const p = cerstva() || pol.p;
    pol.vybrany = p ? { id: k.id, lat: p.lat, lon: p.lon, cas: Date.now() } : null;
    const q = cerstva(2 * 60000);
    if (q && q.presnost <= 50) pridejMisto(k, q);
    ulozData();
    vykresliTady();
  }

  function zjisti(znovu, vzdy) {
    if (znovu) pol.pokusy = 0;
    if (!vzdy && !maMapu()) return;
    clearTimeout(pol.casovac);
    pol.hledam = true;
    N.poloha();
  }

  function zPolohy(p) {
    return p && isFinite(p.lat) && isFinite(p.lon)
      ? { lat: +p.lat, lon: +p.lon, presnost: isFinite(p.presnost) ? +p.presnost : 999, cas: Date.now() - (+p.vek || 0) }
      : null;
  }

  window.naPolohu = function (p, chyba) {
    // Hrubá poloha z Wi-Fi a sítě přijde za vteřinu, přesná z GPS až za chvíli: hrubá stačí, aby se karta
    // ukázala hned, a přesná ji pak případně opraví. Hledání tím nekončí.
    if (chyba === 'hruba') {
      const q = zPolohy(p);
      if (q && q.presnost <= 150 && (!cerstva(60000) || q.presnost <= pol.p.presnost)) {
        pol.p = q;
        pol.stav = 'ok';
        nactiWifi();
        vykresliTady();
      }
      return;
    }
    pol.hledam = false;
    const q = zPolohy(p);
    if (q) { pol.p = q; pol.stav = 'ok'; nactiWifi(); }
    else if (chyba === 'zakazano' || chyba === 'vypnuto') pol.stav = chyba;
    else if (!pol.p) pol.stav = 'nic';
    if (cekaMisto) { const c = cekaMisto; cekaMisto = null; c(q, chyba); }
    vykresliTady();
    naplanuj();
  };

  // Dokud appka žádný obchod nepoznala, zkusí to ještě párkrát (třeba když člověk teprve dochází ke dveřím).
  function naplanuj() {
    clearTimeout(pol.casovac);
    if (pol.stav === 'zakazano' || pol.stav === 'vypnuto' || document.hidden) return;
    if (pol.hero || pol.pokusy >= 6) return;
    pol.pokusy++;
    pol.casovac = setTimeout(() => { zjisti(false); vykresliTady(); }, 20000);
  }

  // ---------- zamčená obrazovka ----------
  // Appka se smí ukázat nad zámkem telefonu (Hlavni.nastavZamek), ať je karta po ruce bez odemykání. Dokud je
  // telefon zamčený, jsou vidět jen karty a kódy: přidávání, úpravy, Nastavení, poznámky, účty, hesla a PINy
  // schová třída zamceno a funkce, které by je otevřely, napřed požádají o odemknutí.
  let zamceno = false;
  const pruhZamku = el('button', { type: 'button', class: 'pruh-zamku', hidden: true, onclick: () => N.odemknout() },
    ikona('zamek'), el('span', null, 'Telefon je zamčený, jsou vidět jen karty. ', el('b', { text: 'Odemknout' })));
  function zamek(zhasnuto) {
    const z = umi('zamceno') && (zhasnuto === true || !!N.zamceno());
    if (z === zamceno) return;
    zamceno = z;
    document.body.classList.toggle('zamceno', z);
    pruhZamku.hidden = !z;
    if (z) {
      skryjOdkryte(false);
      if (vrstvy.some(v => !v.karta)) zavri(true, true); // otevřené Nastavení, úprava nebo dialog nad zámkem nezůstane
    }
  }
  window.naZamek = zamek;
  // Zhasnutý displej se bere jako zámek hned (telefon se může zamknout až za pár sekund) a skutečný stav se
  // pak hlídá průběžně: systém zamknutí neohlašuje a odemknutí jen někdy.
  setInterval(() => { if (!document.hidden) zamek(); }, 1000);
  const chceOdemknout = () => zamceno && (N.odemknout(), true);

  // ---------- tmavý režim ----------
  // Východ a západ slunce pro místo a den (běžný výpočet ze sluneční rovnice, přesnost na pár minut stačí).
  // Vrací [východ, západ] v ms, nebo 'den' / 'noc' za polárního dne a noci.
  function slunce(lat, lon, cas) {
    const R = Math.PI / 180, J0 = 2451545;
    const n = Math.round(cas / 86400000 + 2440587.5 - J0 - 0.0009 + lon / 360);
    const js = J0 + 0.0009 - lon / 360 + n;
    const M = (357.5291 + 0.98560028 * (js - J0)) % 360;
    const C = 1.9148 * Math.sin(M * R) + 0.02 * Math.sin(2 * M * R) + 0.0003 * Math.sin(3 * M * R);
    const L = (M + 102.9372 + C + 180) % 360;
    const poledne = js + 0.0053 * Math.sin(M * R) - 0.0069 * Math.sin(2 * L * R);
    const sinD = Math.sin(L * R) * Math.sin(23.44 * R), cosD = Math.sqrt(1 - sinD * sinD);
    const cosW = (Math.sin(-0.83 * R) - Math.sin(lat * R) * sinD) / (Math.cos(lat * R) * cosD);
    if (cosW > 1) return 'noc';
    if (cosW < -1) return 'den';
    const w = Math.acos(cosW) / R / 360, ms = j => (j - 2440587.5) * 86400000;
    return [ms(poledne - w), ms(poledne + w)];
  }
  function tmaTed() {
    const r = data.nastaveni.vzhled;
    if (r !== 'slunce') return r === 'tmavy';
    let p = pol.p;
    if (!p) { try { p = zPolohy(JSON.parse(N.posledniPoloha() || 'null')); } catch (e) { p = null; } }
    const s = slunce(p ? p.lat : 50.08, p ? p.lon : 14.43, Date.now()); // bez polohy Praha
    return s === 'noc' || s !== 'den' && (Date.now() < s[0] || Date.now() > s[1]);
  }
  let tmaJe = null;
  function nastavVzhled() {
    const t = tmaTed();
    if (t === tmaJe) return;
    tmaJe = t;
    document.documentElement.classList.toggle('tma', t);
    if (umi('tma')) N.tma(t);
  }
  setInterval(() => { if (!document.hidden) nastavVzhled(); }, 5 * 60000);

  // ---------- rychlé vstupy ----------
  // Zkratky po podržení ikony (hledat, mapa, pridat) a obrázky nasdílené z jiné appky (sdileno).
  window.naAkci = function (a) {
    zamek();
    if (a === 'hledat' || a === 'mapa') {
      if (vrstvy.length) zavri(true, true);
      if (a === 'mapa') otevriMapu();
      else setTimeout(() => { hledaniKaret.focus(); hledaniKaret.scrollIntoView({ block: 'center' }); }, 60);
      return;
    }
    if (chceOdemknout()) return;
    if (a === 'pridat') { if (vrstvy.length) zavri(true, true); pridat(); }
    if (a === 'sdileno' && umi('sdileneFotky')) {
      if (vrstvy.length) zavri(true, true);
      oznam('Hledám kód v obrázku…');
      fotkyZprava = { set textContent(t) { if (t) oznam(t); } }; // přehled nových karet hlásí pruhem dole
      N.sdileneFotky();
    }
  };

  window.naNavrat = function () {
    zamek();
    nastavVzhled();
    let lp = null;
    try { lp = zPolohy(JSON.parse(N.posledniPoloha() || 'null')); } catch (e) { lp = null; }
    if (lp && (!pol.p || lp.cas > pol.p.cas)) { pol.p = lp; pol.stav = 'ok'; }
    // Kartu obchodu ukáže jen aktuální poloha (Adam 9.11.2026): co je po návratu do appky starší než 2 minuty,
    // slouží jen na řazení a mapu okolí, o kartě nahoře rozhodne až nová poloha (nebo sedící Wi-Fi).
    if (pol.p && Date.now() - pol.p.cas > AKTUALNI) pol.p.stara = true;
    nactiWifi();
    prevezmiJasy();
    if (!vrstvy.length) vykresliSeznam();
    zjisti(true);
    vykresliTady();
    const top = vrstvy[vrstvy.length - 1];
    if (top && top.priNavratu) top.priNavratu();
    window.naNovouVerzi();
    clearTimeout(sberOdklad);
    sberOdklad = setTimeout(() => { sberMist(); nabidniSber(); }, 4000);
  };

  // ---------- nová verze ----------
  // Číslo verze na webu zjišťuje Java (Verze.java, nejvýš jednou za 6 hodin nebo hned v okně Aktualizace, nic
  // neposílá). Když je na webu novější, vyjede dole tenký pruh, jen na hlavní obrazovce (ne přes kartu ani
  // u prodejny, kde je nahoře kód), a sám za chvíli zmizí. Vyjíždí po každém otevření appky, dokud ho člověk
  // nezavře nebo neotevře Co je nového; tečka u Nastavení svítí, dokud je nová verze ke stažení.
  // Bez internetu se nic neukáže.
  let pruhVerze = null, pruhOdklad = 0;
  // až po chvilce: zavri() hned za sebou často otevře další vrstvu (widget, uložení úpravy karty)
  window.naNovouVerzi = function () {
    clearTimeout(pruhOdklad);
    pruhOdklad = setTimeout(ukazNovouVerzi, 600);
  };
  function proPruhVerze() { return !vrstvy.length && !document.hidden && !pol.hero; }
  function novejsiVerze() {
    try { return JSON.parse(N.novaVerze() || 'null'); } catch (e) { return null; }
  }
  function ukazNovouVerzi() {
    const v = novejsiVerze();
    $('#btnNastaveni').classList.toggle('novinka', !!(v && v.kod));
    if (pruhVerze || !proPruhVerze()) return;
    if (!v || !v.kod || v.ohlasena) return;
    let casovac = 0;
    const ohlas = () => N.verzeOhlasena(String(v.kod));
    const pruh = el('div', { class: 'nova-verze', role: 'status' },
      el('span', { text: 'Je nová verze Kartičky' }),
      el('button', { type: 'button', class: 'nova-verze-stahnout', text: 'Co je nového', onclick: () => { ohlas(); pryc(); aktualizace(); } }),
      el('button', { type: 'button', class: 'ikona', 'aria-label': 'Zavřít', onclick: () => { ohlas(); pryc(); } }, ikona('krizek')));
    function pryc() {
      clearTimeout(casovac);
      pruh.classList.remove('ukazany');
      setTimeout(() => { pruh.remove(); if (pruhVerze === pruh) pruhVerze = null; }, 300);
    }
    pruh.pryc = pryc;
    pruhVerze = pruh;
    document.body.append(pruh);
    void pruh.offsetWidth; // výchozí poloha pod okrajem se vykreslí, ať pruh vyjede i bez snímku animace
    pruh.classList.add('ukazany');
    casovac = setTimeout(pryc, 12000);
  }
  function schovejPruhVerze() {
    if (pruhVerze && !proPruhVerze()) pruhVerze.pryc();
  }

  // ---------- aktualizace ----------
  // Okno Aktualizace (Nastavení, Appka): verze v telefonu, kontrola na webu, stažení a instalace nové verze
  // přímo v appce (Instalace.java: APK z karticka.adamhorak.cz ověřené otiskem, Android se zeptá, karty zůstanou)
  // a co je v posledních verzích nového (zmeny.json z webu, stahuje se s kontrolou verze).
  let kresliAktualizaci = null;
  window.naKontrolu = function (ok) {
    if (kresliAktualizaci) kresliAktualizaci(ok ? 'zkontrolovano' : 'chyba');
    window.naNovouVerzi();
  };
  window.naInstalaci = function (procenta, chyba) {
    if (kresliAktualizaci) kresliAktualizaci(chyba ? 'chyba-instalace' : '', chyba);
    else if (chyba) oznam(chyba);
  };
  function mb(b) { return (b / 1048576).toFixed(1).replace('.', ',') + ' MB'; }
  function aktualizace() {
    const v = vrstva('Aktualizace', { trida: 'aktualizace' });
    const stavText = el('p', { class: 'akt-stav' });
    const tl = el('button', { type: 'button', class: 'tlacitko hlavni akt-tlacitko' });
    const zprava = el('p', { class: 'zprava', 'aria-live': 'polite' });
    const seznam = el('div', { class: 'akt-zmeny' });
    let hleda = false, cekaNaPovoleni = false;
    const stav = () => { try { return JSON.parse(N.stavVerze() || 'null') || {}; } catch (e) { return {}; } };
    const zkontroluj = () => { hleda = true; zprava.textContent = ''; N.zkontrolovatVerzi(); kresli(); };
    function kresli(udalost, text) {
      const s = stav(), nova = novejsiVerze(), pr = N.stavInstalace();
      if (udalost === 'zkontrolovano' || udalost === 'chyba') hleda = false;
      stavText.replaceChildren(
        el('b', { text: nova ? 'Je nová verze' + (nova.verze ? ' ' + nova.verze : '') : 'Máš nejnovější verzi' }),
        el('span', { text: 'V telefonu ' + (N.verze() || '?') + (s.zkontrolovano ? ' · zkontrolováno ' + kdy(s.zkontrolovano) : '') }));
      tl.disabled = pr >= 0 || hleda;
      tl.textContent = pr >= 100 ? 'Čeká na potvrzení' : pr >= 0 ? 'Stahuji ' + pr + ' %' : hleda ? 'Hledám novou verzi…'
        : nova ? 'Aktualizovat' + (nova.velikost ? ' (' + mb(nova.velikost) + ')' : '') : 'Zkontrolovat aktualizace';
      if (udalost === 'chyba') zprava.textContent = 'Novou verzi teď nejde zjistit. Jsi připojený k internetu?';
      else if (udalost === 'zkontrolovano') zprava.textContent = nova ? '' : 'Máš nejnovější verzi.';
      else if (udalost === 'chyba-instalace') zprava.textContent = text || '';
      else if (pr >= 100) zprava.textContent = 'Android se zeptá, jestli novou verzi nainstalovat. Karty zůstanou, Kartička se pak zavře a otevřeš ji znovu.';
      const zmeny = Array.isArray(s.zmeny) ? s.zmeny : [];
      seznam.replaceChildren(...(zmeny.length ? [el('h3', { class: 'sekce', text: 'Co je nového' })] : []),
        ...zmeny.map(z => el('div', { class: 'akt-verze' + (z.kod > s.kod ? ' nova' : '') },
          el('p', { class: 'akt-datum' }, z.datum || '',
            z.kod > s.kod ? el('span', { class: 'akt-stitek', text: 'Nové' })
              : z.kod === s.kod ? el('span', { class: 'akt-stitek tato', text: 'V telefonu' }) : null),
          el('ul', null, ...(z.zmeny || []).map(b => el('li', { text: b }))))));
    }
    tl.addEventListener('click', () => {
      if (!novejsiVerze()) { zkontroluj(); return; }
      const r = N.aktualizovat();
      zprava.textContent = r === 'povolit' ? 'Povol u Kartičky instalaci aplikací a vrať se zpátky, stažení se spustí samo.'
        : r === 'web' ? 'Tuhle verzi stáhneš na webu, otevřel se prohlížeč.' : '';
      cekaNaPovoleni = r === 'povolit';
      kresli();
    });
    v.priNavratu = () => {
      if (cekaNaPovoleni) {
        cekaNaPovoleni = false;
        if (N.smiInstalovat()) { zprava.textContent = ''; N.aktualizovat(); }
        else zprava.textContent = 'Bez povolení se nová verze nenainstaluje. Stáhnout jde i na webu.';
      }
      kresli();
    };
    v.priZavreni = () => { if (kresliAktualizaci === kresli) kresliAktualizaci = null; };
    kresliAktualizaci = kresli;
    v.telo.append(stavText, tl, zprava, seznam,
      el('p', { class: 'o-appce' }, 'Stáhnout jde i na webu ', odkaz('karticka.adamhorak.cz', () => N.stahnoutNovou())));
    kresli();
    // starší kontrola než 10 minut, nebo v seznamu změn chybí nová verze: zjistí to rovnou, ať je seznam čerstvý
    const nova = novejsiVerze(), s = stav();
    const bezZmen = nova && !(Array.isArray(s.zmeny) && s.zmeny.some(z => z.kod === nova.kod));
    if (N.stavInstalace() < 0 && (bezZmen || Date.now() - (s.zkontrolovano || 0) > 600000)) zkontroluj();
  }

  // Krátká zpráva dole (vypadá jako pruh nové verze, ale je nad vším a nejde na ni klepnout), sama zmizí.
  let oznameni = null;
  function oznam(text) {
    if (oznameni) oznameni.pryc();
    if (pruhVerze) pruhVerze.pryc();
    const o = el('div', { class: 'nova-verze oznam', role: 'status' }, el('span', { text }));
    let casovac = 0;
    o.pryc = () => {
      clearTimeout(casovac);
      o.classList.remove('ukazany');
      setTimeout(() => o.remove(), 300);
      if (oznameni === o) oznameni = null;
    };
    oznameni = o;
    document.body.append(o);
    void o.offsetWidth;
    o.classList.add('ukazany');
    casovac = setTimeout(o.pryc, 4500);
  }

  // ---------- mapa obchodů ----------
  // Zapnuté od začátku (vypnout jde v Nastavení, Pomáhat s mapou obchodů). Appka o tom jednou řekne pruhem dole
  // a dokud pruh nešlo vidět, nic neodchází. Pak pošle Java nejvýš jednou za 6 hodin místa, kde se karta
  // ukázala: značku (u vlastní karty jen název), souřadnice na 10 m, kolikrát, vzdálenost ke známé prodejně
  // a poslední den. Z karet nic dalšího (číslo, kód, poznámka, účet) neodchází. Body jedné karty do 60 m od sebe
  // jsou jedno místo a jeho souřadnice je místo z Jsem v prodejně, nebo první bod, ať se při dalším odeslání
  // nemění. Místo daleko od známých prodejen jde ven, až když se tam karta ukázala aspoň ve dva různé dny
  // (jednou otevřená karta doma tak neodejde).
  const SBER_SHLUK = 60, SBER_PRESNOST = 150, SBER_DALEKO = 500;
  let sberOdklad = 0;
  function denIso(t) {
    const d = new Date(t);
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }
  const vCesku = (lat, lon) => lat > 48.5 && lat < 51.1 && lon > 12 && lon < 18.9;
  // Průkazy (ISIC, Lítačka, knihovna) se ukazují doma, ve škole nebo v práci, ne v obchodě: neodchází vůbec.
  // Vlastní karta nese název, který může být osobní (Posilovna, Zubař): odejde jen místo, které si člověk sám označil.
  const sdilenaPolohou = (k, t) => (k.retezec ? (RETEZEC[k.retezec] || {}).skupina !== 'Karty a průkazy' : !t);
  const zaokrouhli = x => Math.round(x * 1e4) / 1e4; // asi 10 m, prodejnu to pozná, přesný bod ne
  const presnaPoloha = z => z.length >= 6 && z[5] <= SBER_PRESNOST; // bez údaje o přesnosti se nebere
  function mistaProSber() {
    const podleKarty = new Map();
    const pridej = (k, lat, lon, t) => {
      if (!vCesku(lat, lon) || !sdilenaPolohou(k, t)) return;
      let s = podleKarty.get(k.id);
      if (!s) podleKarty.set(k.id, s = []);
      let m = s.find(b => vzdalenost(lat, lon, b.lat, b.lon) < SBER_SHLUK);
      if (!m) s.push(m = { lat, lon, x: 0, t: 0, dny: new Set(), oznacene: false });
      if (t) { m.x++; m.t = Math.max(m.t, t); m.dny.add(denIso(t)); } else m.oznacene = true;
    };
    for (const k of data.karty) for (const m of k.mista) pridej(k, m[0], m[1], 0);
    for (const z of data.historie) {
      if (!presnaPoloha(z)) continue; // bez polohy, nebo poloha moc nepřesná
      const k = najdi(z[1]);
      if (k) pridej(k, z[3], z[4], z[0]);
    }
    const ven = [];
    for (const [id, s] of podleKarty) {
      const k = najdi(id), bd = body(k);
      for (const m of s) {
        let d = Infinity;
        for (const b of bd) {
          if (Math.abs(b[0] - m.lat) > 1) continue;
          const v = vzdalenost(m.lat, m.lon, b[0], b[1]);
          if (v < d) d = v;
        }
        d = !bd.length ? -1 : d === Infinity ? 100000 : Math.min(100000, Math.round(d));
        if (!m.oznacene && (d < 0 || d > SBER_DALEKO) && m.dny.size < 2) continue;
        ven.push({ r: k.retezec || '', n: k.retezec ? '' : k.nazev, lat: zaokrouhli(m.lat), lon: zaokrouhli(m.lon), x: m.x, d,
          den: denIso(m.t || Date.now()) });
      }
    }
    return ven;
  }
  function sberMist() {
    if (!V_TELEFONU || !N.stavSberu() || !data.nastaveni.sberOznamen || !N.sberCas()) return;
    const m = mistaProSber();
    if (m.length) N.sberMista(JSON.stringify(m));
  }
  function zapniSber(ano) {
    N.sber(ano);
    if (ano) setTimeout(sberMist, 500);
  }

  // Jednou řekne pruhem dole, že místa obchodů pomáhají mapě, až je co poslat. Do té doby nic neodchází.
  function nabidniSber() {
    const n = data.nastaveni;
    if (!V_TELEFONU || n.sberOznamen || !N.stavSberu() || pruhVerze || oznameni || !proPruhVerze()) return;
    if (!mistaProSber().length) return;
    let casovac = 0, videno = 0;
    const hotovo = () => {
      clearTimeout(videno);
      if (!n.sberOznamen) { n.sberOznamen = true; ulozData(); } // poprvé odejde až při dalším návratu do appky
    };
    const pruh = el('div', { class: 'nova-verze', role: 'status' },
      el('span', { text: 'Místa obchodů pomáhají mapě' }),
      el('button', { type: 'button', class: 'nova-verze-stahnout', text: 'Co to je', onclick: () => { hotovo(); pryc(); souhlasSberu(); } }),
      el('button', { type: 'button', class: 'ikona', 'aria-label': 'Zavřít', onclick: () => { hotovo(); pryc(); } }, ikona('krizek')));
    function pryc() {
      clearTimeout(casovac);
      clearTimeout(videno);
      pruh.classList.remove('ukazany');
      setTimeout(() => { pruh.remove(); if (pruhVerze === pruh) pruhVerze = null; }, 300);
    }
    pruh.pryc = pryc;
    pruhVerze = pruh; // stejné místo jako pruh nové verze, zmizí za stejných okolností
    document.body.append(pruh);
    void pruh.offsetWidth;
    pruh.classList.add('ukazany');
    // nabídnuto je, až když pruh chvíli šlo vidět; zakrytý dřív vyjede příště znovu
    videno = setTimeout(hotovo, 3000);
    casovac = setTimeout(pryc, 12000);
  }
  const POPIS_SBERU = 'Kartička jednou za čas pošle, u kterých obchodů jsi kartu ukázal: značku obchodu, místo na zhruba 10 metrů, poslední den, kolikrát tam karta byla ukázaná a jak daleko je nejbližší známá prodejna. K tomu kód této instalace a verzi appky. Vlastní karta jen s místem, které jsi jí sám označil, a jejím názvem. Průkazy (ISIC, Lítačka, knihovna), čísla karet, kódy ani jméno neodchází. Z míst od všech doplníme mapu obchodů, ať se karta ukáže sama i jinde.';
  function souhlasSberu() {
    const obal = el('div', { class: 'dialog-pozadi' },
      el('div', { class: 'dialog', role: 'alertdialog', 'aria-label': 'Pomáháš s mapou obchodů' },
        el('p', { text: 'Pomáháš s mapou obchodů' }),
        el('p', { class: 'dialog-popis', text: POPIS_SBERU + ' Vypnout jde kdykoli v Nastavení.' }),
        el('div', { class: 'dialog-tlacitka' },
          el('button', { type: 'button', class: 'tlacitko', onclick: () => {
            zavri();
            zapniSber(false);
            oznam('Vypnuto, nic se neposílá.');
          } }, 'Vypnout'),
          el('button', { type: 'button', class: 'tlacitko hlavni', onclick: () => zavri() }, 'Rozumím'))));
    obal.addEventListener('click', e => { if (e.target === obal) zavri(); });
    pridejVrstvu(obal, { jas: false });
  }

  // ---------- podnět ----------
  // Haptický podnět (zatřesení na hlavní obrazovce, Hlavni.java) nebo Napsat podnět v Nastavení: druh, text a nepovinný kontakt.
  // Odchází jen to, kód instalace, verze appky, Android a typ telefonu; bez internetu počká ve frontě (Sber.java).
  const DRUHY_PODNETU = [
    ['karta', 'Chybí karta', 'Která karta nebo obchod chybí? Třeba Tamda, ISIC nebo kavárna u tebe.'],
    ['chyba', 'Nefunguje', 'Co nefunguje a kde? Třeba widget neukázal kartu u Rossmannu.'],
    ['jine', 'Jiné', 'Nápad, přání, cokoli dalšího.']
  ];
  function napsatPodnet(zatresenim) {
    if (vrstvy.some(v => v.podnet)) return;
    let druh = 'karta';
    const text = el('textarea', { rows: '4', maxlength: '2000', placeholder: DRUHY_PODNETU[0][2] });
    const kontakt = el('input', { type: 'text', maxlength: '200', autocomplete: 'email', autocapitalize: 'off',
      spellcheck: 'false', placeholder: 'Mail nebo telefon, jestli chceš odpověď' });
    const chyba = el('p', { class: 'chyba', 'aria-live': 'assertive' });
    const volby = DRUHY_PODNETU.map(([h, nazev, napoveda]) => el('button', { type: 'button', role: 'radio', text: nazev,
      'aria-checked': String(h === druh), onclick: () => {
        druh = h;
        text.placeholder = napoveda;
        volby.forEach((b, i) => b.setAttribute('aria-checked', String(DRUHY_PODNETU[i][0] === h)));
      } }));
    const odeslat = () => {
      const t = text.value.trim();
      if (!t) { chyba.textContent = 'Napiš pár slov.'; text.focus(); return; }
      if (!N.podnet(JSON.stringify({ druh, text: t, kontakt: kontakt.value.trim() }))) {
        chyba.textContent = 'Podnět se nepodařilo uložit, zkus to znovu.';
        return;
      }
      zavri();
    };
    const obal = el('div', { class: 'dialog-pozadi' },
      el('div', { class: 'dialog dialog-podnet', role: 'dialog', 'aria-label': 'Podnět' },
        el('p', { text: 'Co chybí nebo nefunguje?' }),
        el('p', { class: 'dialog-popis', text: 'Přečteme to a appku podle toho doplníme. Pošle se jen tenhle text, verze appky a typ telefonu, karty nikdy.' }),
        el('div', { class: 'prepinac', role: 'radiogroup', 'aria-label': 'Druh podnětu' }, volby),
        pole('Co se děje', text),
        pole('Kontakt (nepovinný)', kontakt),
        chyba,
        el('div', { class: 'dialog-tlacitka' },
          el('button', { type: 'button', class: 'tlacitko', onclick: () => zavri() }, 'Zrušit'),
          el('button', { type: 'button', class: 'tlacitko hlavni', onclick: odeslat }, 'Odeslat')),
        zatresenim ? el('div', { class: 'podnet-vypnout' }, odkaz('Vypnout haptický podnět', () => {
          N.zatreseni(false);
          zavri();
          oznam('Haptický podnět je vypnutý. Zapnout jde v Nastavení.');
        })) : null));
    // klepnutí vedle zavře jen prázdný podnět, rozepsaný text se tak neztratí
    obal.addEventListener('click', e => { if (e.target === obal && !text.value.trim() && !kontakt.value.trim()) zavri(); });
    const v = pridejVrstvu(obal, { jas: false });
    v.podnet = true;
    text.focus();
  }
  // vrací true, když se podnět otevřel: jen tehdy telefon zavrní (Hlavni.java)
  window.naZatreseni = function () {
    if (document.hidden || pol.hero || vrstvy.length) return false; // přes kód u pokladny, kartu ani dialog ne
    napsatPodnet(true);
    return true;
  };
  window.naPodnet = function (stav) {
    oznam(stav === 'odeslano' ? 'Podnět je odeslaný, díky.'
      : stav === 'chyba' ? 'Podnět se nepodařilo odeslat. Napiš nám prosím na info@adamhorak.cz.'
        : 'Podnět odejde, až bude telefon na internetu.');
  };

  // ---------- hlavní obrazovka ----------

  function stavTady() {
    if (!data.karty.length) return { klic: 'prazdno', kresli: () => [prazdno()] };
    const mapa = maMapu();
    if (!mapa) return { klic: 'bezmapy', kresli: () => [] };
    const p = cerstva();
    if (p) {
      const { blizko } = okoli(p);
      if (blizko.length) {
        const vyb = platnyVyber(p);
        const drzeny = blizko.find(b => b.k.id === pol.hero);
        const hl = (vyb && blizko.find(b => b.k.id === vyb)) || (drzi(drzeny, blizko[0], p) ? drzeny : blizko[0]);
        const dalsi = blizko.filter(b => b !== hl).sort((a, b) => a.v - b.v).map(b => b.k); // Taky tady od nejbližšího
        const k = hl.k;
        return {
          klic: ['hero', k.id, k.kod, k.format, k.nazev, k.barva, dalsi.map(d => d.id + d.nazev).join(',')].join('|'),
          hero: k,
          kde: p,
          kresli: () => hero(k, dalsi)
        };
      }
    }
    // Poloha ještě není, ale okolní Wi-Fi skoro přesně sedí s místem, kde už tahle karta byla: ukáže se hned.
    const S = !p && wifiTed();
    if (S) {
      let k = null, nej = 0;
      for (const x of data.karty) if (x.wifi.length) { const w = shodaWifi(x, S); if (w > nej) { nej = w; k = x; } }
      if (k && nej >= 0.5) return { klic: ['hero', k.id, k.kod, k.format, k.nazev, k.barva].join('|'), hero: k, kde: null, kresli: () => hero(k, []) };
    }
    const zak = pol.stav === 'zakazano' ? radek('Bez polohy appka nepozná, v jakém jsi obchodě.', 'Povolit', () => N.povolitPolohu())
      : pol.stav === 'vypnuto' ? radek('Poloha je v telefonu vypnutá.', 'Zapnout', () => N.povolitPolohu()) : null;
    // mimo prodejnu nejbližší obchody a mapa okolí, i podle starší polohy
    const q = p || pol.p;
    if (q) {
      const b = stavBlizko(q, !p);
      return zak ? { klic: pol.stav + '|' + b.klic, kresli: () => [zak, ...b.kresli()] } : b;
    }
    if (zak) return { klic: pol.stav, kresli: () => [zak] };
    if (pol.hledam) return { klic: 'hl', kresli: () => [radek('Zjišťuji, kde jsi…')] };
    return { klic: 'nev', kresli: () => [radek('Polohu se nepodařilo zjistit.', 'Zkusit znovu', () => { zjisti(true); vykresliTady(); })] };
  }

  // Mimo prodejnu: nejbližší obchody s kartou. Kolik a do jaké vzdálenosti si člověk nastaví (výchozí jen ten jeden
  // nejbližší), klepnutí na řádek otevře mapu okolí a na obchod v seznamu jeho kartu. Mapa jde otevřít vždy, když appka
  // zná aspoň starší polohu, a u ní je vidět, jak je stará (Adam 5.11.2026: mapa nahoře se neukazovala vždy).
  function stavBlizko(q, stara) {
    const ns = data.nastaveni;
    const R = ns.okruh || Infinity;
    const nej = [];
    for (const k of data.karty) {
      const v = vzdalenostKarty(k, q);
      if (isFinite(v) && v <= R) nej.push({ k, v });
    }
    nej.sort((a, b) => a.v - b.v);
    nej.length = Math.min(nej.length, ns.blizko);
    const vek = !stara ? '' : pol.hledam ? 'hledám polohu' : 'poloha ' + pred(q.cas);
    const doR = ns.okruh ? 'do ' + metru(ns.okruh) : '';
    const klic = ['nej', ns.blizko, ns.okruh, vek].concat(nej.map(x => x.k.id + x.k.nazev + km(x.v))).join('|');
    const veku = () => vek ? el('span', { class: 'tady-vek', text: vek }) : null;
    if (!nej.length) return { klic, kresli: () => [el('div', { class: 'tady-blizko' },
      el('button', { type: 'button', class: 'tady-radek tady-mapa', 'aria-label': 'Mapa okolí', onclick: () => otevriMapu() },
        ikona('spendlik'), el('span', null, (doR ? 'Do ' + metru(ns.okruh) : 'Poblíž') + ' žádný obchod s kartou', veku()), ikona('mapa')),
      tlZnovu())] };
    if (ns.blizko === 1) {
      const { k, v } = nej[0];
      return { klic, kresli: () => [el('div', { class: 'tady-blizko' },
        el('button', { type: 'button', class: 'tady-radek tady-mapa', 'aria-label': 'Mapa okolí, nejbližší ' + k.nazev + ' ' + km(v), onclick: () => otevriMapu() },
          ikona('spendlik'), el('span', null, 'Nejbližší ', el('b', { text: k.nazev }), ' · ' + km(v), veku()), ikona('mapa')),
        tlZnovu())] };
    }
    return { klic, kresli: () => [
      el('p', { class: 'tady-popis' }, ikona('spendlik'),
        el('span', { class: 'tady-nadpis' }, 'Nejbližší obchody' + (doR ? ' ' + doR : ''), veku()),
        el('span', { class: 'tady-akce' }, tlMapy(), tlZnovu())),
      el('div', { class: 'seznam-voleb tady-obchody' }, nej.map(({ k, v }) =>
        el('button', { type: 'button', class: 'volba', 'aria-label': k.nazev + ', ' + km(v) + ', otevřít kartu', onclick: () => otevriDetail(k, false, 'm') },
          logo(k, 'male') || el('span', { class: 'mini male', style: barvy(k), text: (k.nazev || '?').charAt(0).toUpperCase() }),
          el('span', { class: 'volba-nazev', text: k.nazev }), el('span', { class: 'hodnota', text: km(v) }))))
    ] };
  }
  function pred(cas) {
    const m = Math.max(1, Math.round((Date.now() - cas) / 60000));
    return m < 60 ? 'před ' + m + ' min' : m < 48 * 60 ? 'před ' + Math.round(m / 60) + ' h' : 'před ' + Math.round(m / 1440) + ' dny';
  }
  const tlMapy = () => el('button', { type: 'button', class: 'tady-ikona', 'aria-label': 'Mapa okolí', onclick: () => otevriMapu() }, ikona('mapa'));
  const tlZnovu = () => el('button', { type: 'button', class: 'tady-ikona tady-znovu', 'aria-label': 'Zjistit polohu znovu', onclick: resetPolohy }, ikona('znovu'));

  // Zjistit polohu znovu (Adam 5.11.2026: v obchoďáku appka občas ukáže vedlejší obchod): zapomene ukázanou kartu
  // a dosavadní polohu bere jako starou, ať karta zmizí a rozhodne až nová poloha, jako po otevření appky.
  // Mapa okolí podle staré polohy mezitím zůstane, šipka se točí, dokud appka hledá.
  function resetPolohy() {
    pol.vybrany = pol.heroKde = pol.auto = pol.wifi = null;
    if (pol.p) pol.p.stara = true;
    zjisti(true, true);
    vykresliTady();
  }

  function radek(text, akce, fn) {
    return el('p', { class: 'tady-radek' }, ikona('spendlik'), text, akce ? odkaz(akce, fn) : null);
  }

  function vykresliTady(vynutit) {
    const st = stavTady();
    if (vynutit || st.klic !== pol.klic) {
      pol.klic = st.klic;
      $('#tady').replaceChildren(...st.kresli());
    }
    const zn = $('#tady .tady-znovu');
    if (zn) zn.classList.toggle('tocim', !!pol.hledam); // bez překreslení, kód u pokladny se nepřekresluje
    const id = st.hero ? st.hero.id : null;
    if (id && id !== pol.hero) {
      const naposledy = st.hero.naposledy;
      const v = pol.vybrany;
      const u = ukazana(st.hero, v && v.id === id && Date.now() - v.cas < 5000 ? 't' : 'a');
      pol.auto = { id, cas: Date.now(), pridal: u.pridal, zaznam: u.zaznam, naposledy };
      pol.heroKde = st.kde ? { lat: st.kde.lat, lon: st.kde.lon, cas: Date.now() } : null;
    }
    pol.hero = id;
    schovejPruhVerze();
    upravJas();
    preradSeznam();
    if (mapaOkoli) mapaOkoli.prekresli();
  }

  // ---------- mapa okolí ----------
  // Klepnutí na „Nejbližší…“ otevře mapu s kruhy vzdálenosti kolem polohy a obchody, ke kterým má člověk kartu;
  // klepnutí na obchod otevře jeho kartu. Podklad jsou dlaždice OSM: v telefonu je podá Dlazdice.java (stahuje jen
  // při otevřené mapě a ven jde jen číslo dlaždice), v prohlížeči nejsou a kruhy s obchody leží na prázdném podkladu.
  // Posun, přiblížení a dlaždice jsou převzaté z mapy Speedometru (cifernik, z.html).

  // Podklad mapy: v telefonu přes Dlazdice.java (adresa dlazdice/…), na webu rovnou ze serveru OpenStreetMap.
  const DLAZDICE = V_TELEFONU ? 'dlazdice/' : 'https://tile.openstreetmap.org/';
  const KRUHY = [100, 250, 500, 1000, 2000, 5000, 10000, 20000, 50000, 100000, 200000];
  let mapaOkoli = null;

  function Mapa(koren, volby) {
    const TILE = 256, MINZ = 3, MAXZ = 19;
    const st = { lat: volby.lat, lon: volby.lon, z: volby.z };
    let W = 0, H = 0, naplanovano = 0, animace = 0;
    const vrstvy = {};
    const dlazdiceEl = el('div', { class: 'm-dlazdice' });
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); svg.setAttribute('class', 'm-svg');
    const znackyEl = el('div', { class: 'm-znacky' });
    koren.append(dlazdiceEl, svg, znackyEl);

    const omez = (x, a, b) => Math.max(a, Math.min(b, x));
    function svet(lat, lon, z) {
      const n = TILE * Math.pow(2, z), s = Math.sin(omez(lat, -85.05, 85.05) * Math.PI / 180);
      return [(lon + 180) / 360 * n, (0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI)) * n];
    }
    function zeSveta(x, y, z) {
      const n = TILE * Math.pow(2, z);
      const k = Math.PI - 2 * Math.PI * y / n;
      return { lat: 180 / Math.PI * Math.atan(Math.sinh(k)), lon: ((x / n * 360 - 180 + 540) % 360) - 180 };
    }
    function bod(lat, lon) {
      const a = svet(lat, lon, st.z), c = svet(st.lat, st.lon, st.z);
      return [a[0] - c[0] + W / 2, a[1] - c[1] + H / 2];
    }
    function naGeo(sx, sy) {
      const c = svet(st.lat, st.lon, st.z);
      return zeSveta(c[0] + sx - W / 2, c[1] + sy - H / 2, st.z);
    }
    function metruNaPixel(lat) { return 40075016.686 * Math.cos(lat * Math.PI / 180) / (TILE * Math.pow(2, st.z)); }

    function vrstvaDl(tz) {
      if (!vrstvy[tz]) vrstvy[tz] = { el: el('div', { class: 'm-vrstva' }), dl: new Map(), od: performance.now() };
      dlazdiceEl.appendChild(vrstvy[tz].el); // aktuální vrstva vždy navrch
      return vrstvy[tz];
    }
    function kresliVrstvu(tz, jenExistujici) {
      const v = vrstvy[tz];
      const s = Math.pow(2, st.z - tz), n = Math.pow(2, tz);
      const c = svet(st.lat, st.lon, tz);
      const x0 = c[0] - W / 2 / s, y0 = c[1] - H / 2 / s;
      const tx0 = Math.floor(x0 / TILE), tx1 = Math.floor((x0 + W / s) / TILE);
      const ty0 = Math.max(0, Math.floor(y0 / TILE)), ty1 = Math.min(n - 1, Math.floor((y0 + H / s) / TILE));
      const potreba = new Set();
      let hotovo = true;
      for (let ty = ty0; ty <= ty1; ty++) {
        for (let tx = tx0; tx <= tx1; tx++) {
          const klic = tx + '/' + ty;
          let img = v.dl.get(klic);
          if (!img) {
            if (jenExistujici) continue;
            img = new Image();
            img.alt = ''; img.draggable = false; img.decoding = 'async';
            img.onload = () => { img.classList.add('ok'); prekresli(); };
            img.onerror = () => { img.classList.add('chyba'); prekresli(); }; // bez internetu a v prohlížeči zůstane podklad prázdný
            img.src = DLAZDICE + tz + '/' + (((tx % n) + n) % n) + '/' + ty + '.png';
            v.el.appendChild(img);
            v.dl.set(klic, img);
          }
          potreba.add(klic);
          if (!img.classList.contains('ok') && !img.classList.contains('chyba')) hotovo = false;
          img.style.transform = 'translate3d(' + ((tx * TILE - x0) * s).toFixed(1) + 'px,' +
            ((ty * TILE - y0) * s).toFixed(1) + 'px,0) scale(' + s.toFixed(4) + ')';
        }
      }
      if (!jenExistujici) v.dl.forEach((img, k) => { if (!potreba.has(k)) { img.remove(); v.dl.delete(k); } });
      return hotovo;
    }
    function kresliHned() {
      naplanovano = 0;
      W = koren.clientWidth; H = koren.clientHeight;
      if (!W || !H) return;
      const tz = omez(Math.round(st.z), MINZ, MAXZ);
      vrstvaDl(tz);
      const hotovo = kresliVrstvu(tz, false);
      // starší vrstvy drží obraz, dokud se nová nenačte (jinak by při přiblížení problikl prázdný podklad)
      for (const k in vrstvy) {
        const z2 = +k;
        if (z2 === tz) continue;
        if (hotovo || Math.abs(z2 - st.z) > 3 || performance.now() - vrstvy[tz].od > 2500) { vrstvy[k].el.remove(); delete vrstvy[k]; }
        else kresliVrstvu(z2, true);
      }
      if (volby.poPohybu) volby.poPohybu();
    }
    function prekresli() { if (!naplanovano) naplanovano = requestAnimationFrame(kresliHned); }

    function nastav(lat, lon, z) { st.lat = omez(lat, -85, 85); st.lon = lon; st.z = omez(z, MINZ, MAXZ); prekresli(); }
    function posun(dx, dy) {
      const c = svet(st.lat, st.lon, st.z);
      const g = zeSveta(c[0] - dx, c[1] - dy, st.z);
      nastav(g.lat, g.lon, st.z);
    }
    // přiblížení tak, aby bod pod prstem zůstal pod prstem
    function zoomKolem(z, sx, sy) {
      z = omez(z, MINZ, MAXZ);
      const g = naGeo(sx, sy);
      const a = svet(g.lat, g.lon, z);
      const c = zeSveta(a[0] - (sx - W / 2), a[1] - (sy - H / 2), z);
      nastav(c.lat, c.lon, z);
    }
    function zastav() { if (animace) cancelAnimationFrame(animace); animace = 0; }
    function let_(cil, trvani) {
      zastav();
      trvani = trvani || 650;
      const od = { lat: st.lat, lon: st.lon, z: st.z };
      const zR = Math.max(od.z, cil.z);
      const a = svet(od.lat, od.lon, zR), b = svet(cil.lat, cil.lon, zR);
      const t0 = performance.now();
      const krok = t => {
        const p = Math.min(1, (t - t0) / trvani);
        const e = p < 0.5 ? 2 * p * p : 1 - Math.pow(-2 * p + 2, 2) / 2;
        const g = zeSveta(a[0] + (b[0] - a[0]) * e, a[1] + (b[1] - a[1]) * e, zR);
        st.lat = g.lat; st.lon = g.lon; st.z = od.z + (cil.z - od.z) * e;
        kresliHned();
        animace = p < 1 ? requestAnimationFrame(krok) : 0;
      };
      animace = requestAnimationFrame(krok);
    }

    // ovládání prsty a myší
    const prsty = new Map();
    let gesto = null, posledniKlep = null;
    function stredPrstu() {
      const p = Array.from(prsty.values());
      return [(p[0].x + p[1].x) / 2, (p[0].y + p[1].y) / 2, Math.hypot(p[0].x - p[1].x, p[0].y - p[1].y)];
    }
    function ramec() { return koren.getBoundingClientRect(); }
    koren.addEventListener('pointerdown', e => {
      zastav();
      koren.setPointerCapture(e.pointerId);
      const r = ramec();
      prsty.set(e.pointerId, { x: e.clientX - r.left, y: e.clientY - r.top });
      if (prsty.size === 1) {
        const p = prsty.get(e.pointerId);
        gesto = { typ: 'posun', x: p.x, y: p.y, sx: p.x, sy: p.y, t: performance.now(), vx: 0, vy: 0, t0: performance.now() };
        koren.classList.add('tahne');
      } else if (prsty.size === 2) {
        const s = stredPrstu();
        gesto = { typ: 'stip', z0: st.z, d0: Math.max(10, s[2]), mx: s[0], my: s[1] };
      }
    });
    koren.addEventListener('pointermove', e => {
      if (!prsty.has(e.pointerId)) return;
      const r = ramec();
      prsty.set(e.pointerId, { x: e.clientX - r.left, y: e.clientY - r.top });
      if (!gesto) return;
      if (gesto.typ === 'posun' && prsty.size === 1) {
        const p = prsty.get(e.pointerId), t = performance.now();
        const dx = p.x - gesto.x, dy = p.y - gesto.y, dt = Math.max(1, t - gesto.t);
        gesto.vx = gesto.vx * 0.6 + (dx / dt) * 0.4; gesto.vy = gesto.vy * 0.6 + (dy / dt) * 0.4;
        gesto.x = p.x; gesto.y = p.y; gesto.t = t;
        posun(dx, dy);
      } else if (gesto.typ === 'stip' && prsty.size === 2) {
        const s = stredPrstu();
        posun(s[0] - gesto.mx, s[1] - gesto.my);
        gesto.mx = s[0]; gesto.my = s[1];
        zoomKolem(gesto.z0 + Math.log2(s[2] / gesto.d0), s[0], s[1]);
      }
    });
    function konecDotyku(e) {
      if (!prsty.has(e.pointerId)) return;
      const p = prsty.get(e.pointerId);
      prsty.delete(e.pointerId);
      koren.classList.remove('tahne');
      if (gesto && gesto.typ === 'posun' && prsty.size === 0) {
        const t = performance.now();
        const kratke = t - gesto.t0 < 300 && Math.hypot(p.x - gesto.sx, p.y - gesto.sy) < 10;
        if (kratke) {
          if (volby.naKlep && volby.naKlep(p.x, p.y)) posledniKlep = null; // klepnutí na obchod
          else if (posledniKlep && t - posledniKlep.t < 320 && Math.hypot(p.x - posledniKlep.x, p.y - posledniKlep.y) < 30) {
            // dvojité klepnutí = přiblížit
            const g = naGeo(p.x, p.y), cilZ = omez(Math.round(st.z) + 1, MINZ, MAXZ);
            const a = svet(g.lat, g.lon, cilZ);
            const c = zeSveta(a[0] - (p.x - W / 2), a[1] - (p.y - H / 2), cilZ);
            let_({ lat: c.lat, lon: c.lon, z: cilZ }, 320);
            posledniKlep = null;
          } else posledniKlep = { t: t, x: p.x, y: p.y };
        } else if (t - gesto.t < 80 && Math.hypot(gesto.vx, gesto.vy) > 0.15) {
          // setrvačnost po švihnutí
          let vx = gesto.vx * 16, vy = gesto.vy * 16;
          const krok = () => {
            vx *= 0.9; vy *= 0.9;
            posun(vx, vy); kresliHned();
            animace = Math.hypot(vx, vy) > 0.4 ? requestAnimationFrame(krok) : 0;
          };
          animace = requestAnimationFrame(krok);
        }
        gesto = null;
      } else if (prsty.size === 1) {
        const zbyly = Array.from(prsty.values())[0];
        gesto = { typ: 'posun', x: zbyly.x, y: zbyly.y, sx: zbyly.x, sy: zbyly.y, t: performance.now(), t0: 0, vx: 0, vy: 0 };
      } else gesto = null;
    }
    koren.addEventListener('pointerup', konecDotyku);
    koren.addEventListener('pointercancel', konecDotyku);
    koren.addEventListener('wheel', e => {
      e.preventDefault();
      zastav();
      const r = ramec();
      zoomKolem(st.z - omez(e.deltaY, -120, 120) * 0.0035, e.clientX - r.left, e.clientY - r.top);
    }, { passive: false });
    window.addEventListener('resize', prekresli);

    prekresli();
    return {
      bod, metruNaPixel, let_, prekresli, svg, znacky: znackyEl,
      get stav() { return { lat: st.lat, lon: st.lon, z: st.z }; },
      get rozmer() { return [W, H]; },
      zrus() { zastav(); window.removeEventListener('resize', prekresli); }
    };
  }

  // Obchody s kartou do vzdálenosti R od polohy. Přesná místa (kde člověk kartu vybral, pokladny ze sběru) mají
  // přednost; prodejna z mapy do 60 m od přesného místa stejné karty je to samé místo. Na stejném bodě víc karet = jeden špendlík.
  function obchodyKolem(p, R, karty = data.karty) {
    const spendliky = new Map();
    const stupnu = R / 110000 + 0.001;
    function pridej(k, m) {
      if (Math.abs(m[0] - p.lat) > stupnu) return;
      const v = vzdalenost(p.lat, p.lon, m[0], m[1]);
      if (v > R) return;
      const klic = m[0].toFixed(5) + ',' + m[1].toFixed(5);
      let s = spendliky.get(klic);
      if (!s) spendliky.set(klic, s = { klic, lat: m[0], lon: m[1], v, karty: [] });
      if (!s.karty.includes(k)) s.karty.push(k);
    }
    for (const k of karty) {
      const presne = k.mista.concat(bodyPokladen(k));
      for (const m of presne) pridej(k, m);
      for (const m of body(k)) {
        if (Math.abs(m[0] - p.lat) > stupnu) continue;
        if (presne.some(q => vzdalenost(m[0], m[1], q[0], q[1]) < 60)) continue;
        pridej(k, m);
      }
    }
    return Array.from(spendliky.values()).sort((a, b) => a.v - b.v);
  }

  // Všechny prodejny karty jako na mapě: přesná místa a z mapy jen ty, co jsou od nich dál než 60 m.
  function prodejnyKarty(k) {
    const presne = k.mista.concat(bodyPokladen(k));
    return presne.concat(body(k).filter(m => !presne.some(q => vzdalenost(m[0], m[1], q[0], q[1]) < 60)));
  }

  // Střed a přiblížení, aby se body [lat, lon] vešly do w×h s okrajem (stejný Mercator jako Mapa).
  function prilozeni(body, w, h, okraj) {
    let x0 = Infinity, x1 = -Infinity, y0 = Infinity, y1 = -Infinity;
    for (const [lat, lon] of body) {
      const x = (lon + 180) / 360, s = Math.sin(lat * Math.PI / 180), y = 0.5 - Math.log((1 + s) / (1 - s)) / (4 * Math.PI);
      x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y);
    }
    const z = Math.log2(Math.min((w - 2 * okraj) / Math.max(x1 - x0, 1e-9), (h - 2 * okraj) / Math.max(y1 - y0, 1e-9)) / 256);
    const k = Math.PI - 2 * Math.PI * (y0 + y1) / 2;
    return { lat: 180 / Math.PI * Math.atan(Math.sinh(k)), lon: (x0 + x1) / 2 * 360 - 180, z: Math.max(6, Math.min(16, z)) };
  }

  // Bez karty mapa okolí se všemi kartami. S kartou (z detailu) jen její prodejny, a když poloha není, všechny na obrazovce.
  function otevriMapu(karta) {
    const p = pol.p;
    const vse = karta ? prodejnyKarty(karta) : [];
    if (!p && !vse.length) return;
    const karty = karta ? [karta] : data.karty;
    const v = vrstva(karta ? 'Prodejny ' + karta.nazev : 'Mapa okolí', { trida: 'mapa-vrstva' });
    const koren = el('div', { class: 'mapa-okoli' });
    const vyber = el('div', { class: 'm-vyber', hidden: true });
    const stitek = el('div', { class: 'm-stitek', hidden: true });
    const naMe = el('button', { type: 'button', class: 'm-na-me', 'aria-label': 'Zpět na mou polohu', hidden: !p }, ikona('poloha'));
    v.telo.append(koren, stitek, vyber, naMe,
      el('div', { class: 'm-osm', text: '© přispěvatelé OpenStreetMap' }));

    // první pohled: poloha uprostřed a kruh, ve kterém je nejbližší obchod (aspoň 500 m), celý na obrazovce
    const W = v.telo.clientWidth || 360, H = v.telo.clientHeight || 600;
    let start;
    if (p) {
      const nej = obchodyKolem(p, 200000, karty)[0];
      const kruh = KRUHY.find(r => r >= Math.max(500, nej ? nej.v * 1.1 : 0)) || KRUHY[KRUHY.length - 1];
      start = { lat: p.lat, lon: p.lon,
        z: Math.max(6, Math.min(17, Math.log2((Math.min(W, H) / 2 - 28) * 40075016.686 * Math.cos(p.lat * Math.PI / 180) / (256 * kruh)))) };
    } else start = prilozeni(vse, W, H, 40);
    const z0 = start.z;

    let spendliky = [], dosah = 0, kolem = null, videt = [], vybrany = null;
    const prvky = new Map();

    function kresli() {
      const ja = pol.p || p;
      const q = ja || start; // bez polohy se obchody počítají od středu jejích prodejen
      const [w, h] = m.rozmer;
      const mpp = m.metruNaPixel(q.lat);
      const [cx, cy] = m.bod(q.lat, q.lon);
      // obchody do nejvzdálenějšího rohu obrazovky (při oddálení a posunu se dopočítají)
      const roh = Math.max(Math.hypot(cx, cy), Math.hypot(w - cx, cy), Math.hypot(cx, h - cy), Math.hypot(w - cx, h - cy)) * mpp;
      if (kolem !== q || roh > dosah) {
        kolem = q;
        dosah = Math.min(400000, Math.max(25000, roh * 1.5));
        spendliky = obchodyKolem(q, dosah, karty);
      }

      // kruhy vzdálenosti a modrá tečka polohy
      const s = [];
      const prumer = Math.hypot(w, h);
      if (ja) for (const r of KRUHY) {
        const rp = r / mpp;
        if (rp < 30 || rp > prumer + Math.hypot(cx - w / 2, cy - h / 2)) continue;
        s.push('<circle class="m-kruh" cx="' + cx.toFixed(1) + '" cy="' + cy.toFixed(1) + '" r="' + rp.toFixed(1) + '"/>');
        // popisek nahoře na kruhu, když je mimo obrazovku, tak dole, pak vpravo nebo vlevo
        const mista = [[cx, cy - rp], [cx, cy + rp], [cx + rp, cy], [cx - rp, cy]];
        const t = mista.find(([x, y]) => x > 24 && x < w - 24 && y > 14 && y < h - 40);
        if (t) s.push('<text class="m-kruh-popis" x="' + t[0].toFixed(1) + '" y="' + (t[1] + 4).toFixed(1) + '">' + (r < 1000 ? r + ' m' : r / 1000 + ' km') + '</text>');
      }
      if (ja) {
        const pr = Math.min(q.presnost, 2000) / mpp;
        if (pr > 9) s.push('<circle class="m-presnost" cx="' + cx.toFixed(1) + '" cy="' + cy.toFixed(1) + '" r="' + pr.toFixed(1) + '"/>');
        s.push('<circle class="m-ja" cx="' + cx.toFixed(1) + '" cy="' + cy.toFixed(1) + '" r="8"/>');
      }
      m.svg.innerHTML = s.join('');

      // špendlíky obchodů: od nejbližšího, co se překrývá s už položeným, se vynechá (přiblížením se ukáže)
      videt = [];
      for (const sp of spendliky) {
        const [x, y] = m.bod(sp.lat, sp.lon);
        if (x < -30 || y < -30 || x > w + 30 || y > h + 30) continue;
        if (videt.some(o => Math.abs(o.x - x) < 30 && Math.abs(o.y - y) < 30)) continue;
        videt.push({ sp, x, y });
        if (videt.length >= 150) break;
      }
      const ted = new Set();
      for (const o of videt) {
        let e = prvky.get(o.sp.klic);
        if (!e) {
          const k = o.sp.karty[0];
          e = el('span', { class: 'm-obchod', style: barvy(k) },
            dlazdiceLoga(k) || el('b', { text: (k.nazev || '?').slice(0, 1).toUpperCase() }),
            o.sp.karty.length > 1 ? el('i', { text: '+' + (o.sp.karty.length - 1) }) : null);
          prvky.set(o.sp.klic, e);
        }
        e.style.transform = 'translate3d(' + (o.x - 18).toFixed(1) + 'px,' + (o.y - 18).toFixed(1) + 'px,0)';
        if (!e.isConnected) m.znacky.append(e);
        ted.add(o.sp.klic);
      }
      prvky.forEach((e, k) => { if (!ted.has(k)) e.remove(); });

      // nejbližší obchod s popiskem (na mapě jedné karty ten, na který člověk klepl)
      const n = videt.find(o => o.sp === (vybrany || (ja ? spendliky[0] : null)));
      if (n) {
        stitek.textContent = n.sp.karty[0].nazev + (ja ? ' · ' + km(n.sp.v) : '');
        stitek.style.transform = 'translate3d(' + n.x.toFixed(1) + 'px,' + (n.y + 22).toFixed(1) + 'px,0) translateX(-50%)';
        stitek.hidden = false;
      } else stitek.hidden = true;
    }

    function naKlep(x, y) {
      let nejblizsi = null, d = 28;
      for (const o of videt) { const dd = Math.hypot(o.x - x, o.y - y); if (dd <= d) { d = dd; nejblizsi = o; } }
      if (!nejblizsi) { vyber.hidden = true; if (vybrany) { vybrany = null; m.prekresli(); } return false; }
      if (karta) { vybrany = nejblizsi.sp; m.prekresli(); return true; } // detail karty je hned pod mapou
      const karty = nejblizsi.sp.karty;
      if (karty.length === 1) { vyber.hidden = true; otevriDetail(karty[0], false, 'm'); return true; }
      vyber.replaceChildren(el('p', { class: 'm-vyber-nadpis', text: 'Tady (' + km(nejblizsi.sp.v) + ' od tebe) platí:' }),
        el('div', { class: 'seznam-voleb' }, karty.map(k => el('button', { type: 'button', class: 'volba', onclick: () => { vyber.hidden = true; otevriDetail(k, false, 'm'); } },
          logo(k, 'male') || el('span', { class: 'mini male', style: barvy(k), text: (k.nazev || '?').charAt(0).toUpperCase() }),
          el('span', { class: 'volba-nazev', text: k.nazev }), ikona('dal')))));
      vyber.hidden = false;
      return true;
    }

    const m = Mapa(koren, { lat: start.lat, lon: start.lon, z: z0, poPohybu: kresli, naKlep });
    naMe.addEventListener('click', () => { const q = pol.p || p; if (q) m.let_({ lat: q.lat, lon: q.lon, z: z0 }); });
    mapaOkoli = m;
    v.priZavreni = () => { m.zrus(); if (mapaOkoli === m) mapaOkoli = null; };
    if (p && Date.now() - p.cas > 60000) zjisti(false);
  }

  function hero(k, dalsi) {
    const w = sirkaObsahu() - 40;
    const ctverec = CTVERCOVE.has(k.format);
    return [
      el('p', { class: 'tady-popis' }, el('span', { class: 'tecka' }), 'Jsi u prodejny',
        el('span', { class: 'tady-akce' }, tlMapy(), tlZnovu())),
      el('button', { type: 'button', class: 'hero', style: barvy(k), onclick: () => k.kod ? otevriDetail(k) : otevriAppku(k),
        'aria-label': k.nazev + (k.kod ? ', otevřít kartu' : ', otevřít appku s kódem') },
        el('span', { class: 'hero-pruh' }, logo(k), el('span', { class: 'hero-nazev', text: k.nazev })),
        k.kod ? [el('span', { class: 'hero-kod' }, obrazekKodu(k.format, k.kod, ctverec ? Math.min(w, 220) : w, 110)),
          cisloKarty(k) ? el('span', { class: 'hero-cislo', text: cisloKarty(k) }) : null]
          : el('span', { class: 'hero-appka' }, ikona('darek'), 'Otevřít appku s kódem')),
      dalsi.length ? el('p', { class: 'taky' }, 'Taky tady: ',
        spoj(dalsi.map(d => odkaz(d.nazev, () => vyber(d))), ' · ')) : null
    ].filter(Boolean);
  }

  function prazdno() {
    return el('div', { class: 'prazdno' },
      el('div', { class: 'vejir', 'aria-hidden': 'true' }, el('span'), el('span'), el('span')),
      el('h2', { text: 'Všechny karty na jednom místě' }),
      el('p', { text: 'Přidej svoje věrnostní karty. U pokladny ti appka sama ukáže kartu obchodu, kde zrovna jsi.' }),
      el('button', { type: 'button', class: 'tlacitko hlavni', onclick: () => pridat() }, ikona('plus'), 'Přidat první kartu'),
      el('button', { type: 'button', class: 'odkaz prazdno-odkaz', onclick: prenos }, 'Přenést karty z jiné appky'));
  }

  function dlazdice(k, tag) {
    return el(tag || 'button', { type: tag ? null : 'button', class: 'karta', style: barvy(k), onclick: tag ? null : () => otevriDetail(k) },
      el('span', { class: 'karta-hlava' }, logo(k), el('span', { class: 'karta-nazev', text: k.nazev }),
        k.oblibena && !tag ? el('span', { class: 'karta-hvezda' }, ikona('hvezda')) : null),
      el('span', { class: 'karta-cislo', text: k.kod ? cisloKarty(k) : k.appka ? 'Kód v appce' : '' }));
  }

  // Hledání v Moje karty: podle názvu (bez diakritiky) nebo čísla karty. Pole se při přeřazení nepřekresluje,
  // jinak by přišlo o kurzor; mění se jen mřížka pod ním.
  const hledaniKaret = el('input', { type: 'search', placeholder: 'Hledat kartu', 'aria-label': 'Hledat kartu',
    autocomplete: 'off', spellcheck: 'false', enterkeyhint: 'search', oninput: () => filtrujSeznam() });
  const hlavaSeznamu = [el('h2', { class: 'nadpis', text: 'Moje karty' }),
    el('label', { class: 'hledat v-seznamu' }, ikona('lupa'), hledaniKaret)];
  const nicNenalezeno = el('p', { class: 'info nic', text: 'Žádná karta neodpovídá.' });

  function filtrujSeznam() {
    const q = bezDiakritiky(hledaniKaret.value.trim());
    const cislice = q.replace(/\s/g, '');
    let n = 0;
    for (const d of $('#seznam').querySelectorAll('.karta[data-hledat]')) {
      const ok = !q || d.dataset.hledat.includes(q) || (cislice.length >= 3 && d.dataset.kod.includes(cislice));
      d.hidden = !ok;
      if (ok) n++;
    }
    nicNenalezeno.hidden = !q || n > 0;
  }

  let seznamKlic = '', seznamCasovac = 0, dotyk = 0;
  const dotykNyni = () => { dotyk = Date.now(); };
  document.addEventListener('pointerdown', dotykNyni, true);
  window.addEventListener('scroll', dotykNyni, { passive: true });

  function vykresliSeznam() {
    const s = $('#seznam');
    clearTimeout(seznamCasovac);
    if (!data.karty.length) { seznamKlic = ''; s.replaceChildren(); return; }
    const karty = serazene();
    seznamKlic = karty.map(k => k.id).join(',');
    const mrizka = el('div', { class: 'mrizka' }, karty.map(k => {
      const d = dlazdice(k);
      d.dataset.hledat = bezDiakritiky(k.nazev);
      d.dataset.kod = k.kod.replace(/\s/g, '').toLowerCase();
      return d;
    }), el('button', { type: 'button', class: 'karta pridat', onclick: () => pridat() }, ikona('plus'), el('span', { text: 'Přidat kartu' })));
    const stara = s.querySelector('.mrizka');
    if (stara && s.contains(hlavaSeznamu[0])) stara.replaceWith(mrizka);
    else s.replaceChildren(...hlavaSeznamu, nicNenalezeno, mrizka);
    hlavaSeznamu[1].hidden = karty.length < 6;
    if (hlavaSeznamu[1].hidden) hledaniKaret.value = '';
    filtrujSeznam();
  }

  // Po nové poloze se seznam přeřadí, ale nikdy pod prstem: když se člověk právě dotkl nebo posouval, počká se.
  function preradSeznam() {
    clearTimeout(seznamCasovac);
    if (vrstvy.length || !data.karty.length) return;
    if (serazene().map(k => k.id).join(',') === seznamKlic) return;
    const zbyva = dotyk + 2500 - Date.now();
    if (zbyva > 0) { seznamCasovac = setTimeout(preradSeznam, zbyva); return; }
    vykresliSeznam();
  }

  // ---------- vrstvy (okna přes celou obrazovku), Zpět zavře jen tu horní ----------

  const vrstvy = [];
  let jasNyni = false, jasCasovac = 0, jasKonec = 0, jasTik = 0;
  // jasTrvale: svítí, dokud znovu neklepneš nebo nezavřeš kartu. jasVypnuto: klepnutím zhasnuto (platí hlavně u volby Hned).
  let jasTrvale = false, jasVypnuto = false, jasKlepnuti = 0;

  // Jas naplno jen na pár sekund po klepnutí na kód v detailu karty (Adam nechtěl, aby obrazovka svítila hned po otevření).
  // Kolik sekund, nebo hned po otevření, se volí v Nastavení (jas 0 = hned).
  function rozsvit(trvale) {
    clearTimeout(jasCasovac);
    jasCasovac = 0;
    jasTrvale = !!trvale;
    jasVypnuto = false;
    if (!jasTrvale) {
      const s = data.nastaveni.jas || 5;
      jasKonec = Date.now() + s * 1000;
      jasCasovac = setTimeout(() => { jasCasovac = 0; upravJas(); }, s * 1000);
    }
    upravJas();
  }

  // Jas hned zpátky na hodnotu telefonu (i při volbě Hned, dokud se na kód zase neklepne).
  function zhasni() {
    clearTimeout(jasCasovac);
    jasCasovac = 0;
    jasTrvale = false;
    jasVypnuto = true;
    upravJas();
  }

  // Klepnutí na kód: dvojklik (do 450 ms) nechá svítit trvale, jinak svítící zhasne a zhasnutá se rozsvítí na nastavenou dobu.
  function klepNaKod() {
    const ted = Date.now();
    if (ted - jasKlepnuti < 450) {
      jasKlepnuti = 0; // třetí klepnutí už není další dvojklik
      const vyber = getSelection(); // dvojklik na číslo pod kódem by ho jinak označil
      if (vyber) vyber.removeAllRanges();
      rozsvit(true);
      return;
    }
    jasKlepnuti = ted;
    if (jasNyni) zhasni();
    else rozsvit(false);
  }

  function upravJas() {
    const top = vrstvy[vrstvy.length - 1];
    const chci = !!(top && top.jas && (WEB || !jasVypnuto && (jasCasovac || jasTrvale || data.nastaveni.jas === 0)));
    if (chci !== jasNyni) { jasNyni = chci; N.jas(chci); document.body.classList.toggle('svit', chci); }
    odpocet();
  }

  // Popisek u sluníčka odpočítává, kolik sekund ještě svítí (zaokrouhleně nahoru), přepíše se vždy, když se číslo změní.
  // Při dvojkliku svítí trvale (Svítí, dokud neklepneš), při volbě Hned svítí, dokud je karta otevřená, tam zůstane Svítí naplno.
  function odpocet() {
    clearTimeout(jasTik);
    jasTik = 0;
    const top = vrstvy[vrstvy.length - 1];
    if (!jasNyni || !top || !top.odpocet) return;
    if (jasTrvale) { top.odpocet.textContent = 'Svítí, dokud neklepneš'; return; }
    if (!jasCasovac || !data.nastaveni.jas) { top.odpocet.textContent = 'Svítí naplno'; return; }
    const zbyva = jasKonec - Date.now();
    top.odpocet.textContent = 'Svítí ještě ' + Math.max(1, Math.ceil(zbyva / 1000)) + ' s';
    if (zbyva > 1000) jasTik = setTimeout(odpocet, (zbyva - 1) % 1000 + 1 + 15);
  }

  function pridejVrstvu(obal, o) {
    const v = Object.assign({ obal }, o || {});
    $('#vrstvy').append(obal);
    vrstvy.push(v);
    schovejPruhVerze();
    $('#hlavni').inert = true;
    $('#hlava').inert = true;
    document.body.classList.add('ma-vrstvu');
    if (v.bezAnimace) obal.classList.add('bez', 'otevrena');
    else requestAnimationFrame(() => requestAnimationFrame(() => obal.classList.add('otevrena')));
    if (vrstvy.length > 1) vrstvy[vrstvy.length - 2].obal.inert = true;
    upravJas();
    return v;
  }

  function vrstva(nadpis, o) {
    o = o || {};
    const telo = el('div', { class: 'vrstva-telo' });
    const hlava = el('header', { class: 'vrstva-hlava', style: o.karta ? barvy(o.karta) : null },
      el('button', { type: 'button', class: 'ikona', 'aria-label': 'Zpět', onclick: () => zavri() }, ikona('zpet')),
      o.karta ? logo(o.karta) : null,
      el('h2', { text: nadpis }));
    const obal = el('section', { class: 'vrstva' + (o.trida ? ' ' + o.trida : ''), role: 'dialog', 'aria-label': nadpis,
      style: o.karta ? barvy(o.karta) : null }, hlava, telo);
    const v = pridejVrstvu(obal, o);
    v.telo = telo;
    return v;
  }

  function zavri(vse, bezAnimace) {
    do {
      const v = vrstvy.pop();
      if (!v) break;
      if (bezAnimace) v.obal.remove();
      else {
        // během animace zavírání vrstva ťuknutí spolkne: druhé rychlé ťuknutí nespustí akci znovu ani nepropadne pod ni
        v.obal.addEventListener('click', e => { e.stopPropagation(); e.preventDefault(); }, true);
        v.obal.classList.remove('otevrena');
        setTimeout(() => v.obal.remove(), 260);
      }
      if (v.priZavreni) v.priZavreni();
    } while (vse && vrstvy.length);
    clearTimeout(jasCasovac);
    jasCasovac = 0;
    jasTrvale = false;
    jasVypnuto = false;
    if (vrstvy.length) vrstvy[vrstvy.length - 1].obal.inert = false;
    else {
      $('#hlavni').inert = false;
      $('#hlava').inert = false;
      document.body.classList.remove('ma-vrstvu');
      vykresliSeznam();
      vykresliTady(true);
      window.naNovouVerzi();
    }
    upravJas();
  }

  window.zpetNativni = function () {
    if (!vrstvy.length && hledaniKaret.value) { hledaniKaret.value = ''; hledaniKaret.blur(); filtrujSeznam(); return true; }
    if (!vrstvy.length) return false;
    zavri();
    return true;
  };
  document.addEventListener('keydown', e => { if (e.key === 'Escape') window.zpetNativni(); });

  // jenDialog: po potvrzení zavře jen dialog a vrstva pod ním zůstane otevřená; popis: menší text pod otázkou
  function potvrd(text, ano, akce, jenDialog, popis) {
    const obal = el('div', { class: 'dialog-pozadi' },
      el('div', { class: 'dialog', role: 'alertdialog', 'aria-label': text },
        el('p', { text }),
        popis ? el('p', { class: 'dialog-popis', text: popis }) : null,
        el('div', { class: 'dialog-tlacitka' },
          el('button', { type: 'button', class: 'tlacitko', onclick: () => zavri() }, 'Zrušit'),
          el('button', { type: 'button', class: 'tlacitko nebezpeci', onclick: () => { zavri(!jenDialog); akce(); } }, ano))));
    obal.addEventListener('click', e => { if (e.target === obal) zavri(); });
    pridejVrstvu(obal, { jas: false });
  }

  // Dialog s políčky na hesla. o = { nadpis, popis, pole: [popisky], tlacitka: [{ text, hlavni, nebezpeci, zrusit,
  // akce(hodnoty) -> text chyby, nebo '' když je hotovo }], zrusit() }. Tlačítko se zrusit jen zavře dialog;
  // o.zrusit() se zavolá, kdykoli se dialog zavře bez hotového tlačítka (i klepnutím mimo nebo Zpět).
  function dialogHesla(o) {
    const chyba = el('p', { class: 'chyba', 'aria-live': 'assertive' });
    const vstupy = o.pole.map(() => el('input', { type: 'password', maxlength: '200', autocomplete: 'new-password',
      autocapitalize: 'off', spellcheck: 'false' }));
    let hotovo = false;
    let hlavni = null;
    const tlacitka = o.tlacitka.map(t => {
      const b = el('button', { type: 'button', class: 'tlacitko' + (t.hlavni ? ' hlavni' : '') + (t.nebezpeci ? ' nebezpeci' : ''), onclick: () => {
        if (t.zrusit) return zavri();
        const c = t.akce(vstupy.map(i => i.value));
        if (c) { chyba.textContent = c; return; }
        hotovo = true;
        for (const i of vstupy) i.value = '';
        zavri();
      }, text: t.text });
      if (t.hlavni) hlavni = b;
      return b;
    });
    vstupy.forEach(i => i.addEventListener('keydown', e => {
      if (e.key === 'Enter' && hlavni) { e.preventDefault(); hlavni.click(); }
    }));
    const obal = el('div', { class: 'dialog-pozadi' },
      el('div', { class: 'dialog dialog-hesla', role: 'alertdialog', 'aria-label': o.nadpis },
        el('p', { text: o.nadpis }),
        o.popis ? el('p', { class: 'dialog-popis', text: o.popis }) : null,
        o.pole.map((p, n) => pole(p, vstupy[n])),
        chyba,
        el('div', { class: 'dialog-tlacitka' }, tlacitka)));
    obal.addEventListener('click', e => { if (e.target === obal) zavri(); });
    pridejVrstvu(obal, { jas: false, priZavreni: () => {
      for (const i of vstupy) i.value = '';
      if (!hotovo && o.zrusit) o.zrusit();
    } });
    vstupy[0].focus();
  }

  // ---------- detail karty ----------

  function infoText(k) {
    const n = prodejnyKarty(k).length;
    return n ? 'Ukáže se sama u ' + n + ' ' + tvar(n, 'prodejny', 'prodejen', 'prodejen')
      : 'Až budeš v prodejně, klepni na Jsem v prodejně a příště se ukáže sama.';
  }

  // ---------- účet u karty ----------

  const TECKY = '••••••••';
  const DOBA_HESLA = 30; // sekund, po které se odkryté heslo samo schová (stejně v Hlavni.java)
  let odkryte = null; // blok účtu v otevřeném detailu, který má odkryté heslo nebo na ně čeká

  // Hlavni.java schová heslo sama (po čase, při odchodu z appky). Zámek ještě čekající na ověření nechá být.
  function skryjOdkryte(zNativu) { if (odkryte && odkryte.stav === 'odkryte') odkryte.skryj(zNativu); }
  window.naSkryti = function () { skryjOdkryte(true); };
  // text: heslo, '' = ověření zrušeno, null = heslo nejde přečíst; bezZamku: telefon nemá zámek obrazovky
  window.naHeslo = function (ks, text, bezZamku) {
    if (odkryte && odkryte.ks === ks && odkryte.stav === 'ceka') odkryte.prijmi(text, bezZamku);
    else if (text) N.skryjHeslo(); // nikdo na heslo nečeká (detail se zavřel), ať nezůstane odkryté
  };

  // Přihlašovací jméno jde zkopírovat hned, heslo a PIN karty se ukážou až po otisku nebo zámku telefonu a po 30 s
  // se schovají. Kdo dostane telefon do ruky jen s kartou k naskenování, PIN ani heslo nevidí; na zamčené
  // obrazovce se celý blok neukáže vůbec (jen-odemcene).
  function blokUctu(k, v) {
    if (!k.ucet && !k.heslo && !k.pin) return '';
    const zprava = el('p', { class: 'zprava', 'aria-live': 'polite' });
    const radky = el('div', { class: 'ucet-radky' });
    const bloky = [];
    if (k.ucet) radky.append(el('div', { class: 'ucet-radek' },
      el('span', { class: 'ucet-popisek', text: 'Jméno' }),
      el('span', { class: 'ucet-hodnota', text: k.ucet }),
      odkaz('Kopírovat', () => { zprava.textContent = N.kopiruj(k.ucet) ? 'Jméno je zkopírované.' : 'Kopírování se nepodařilo.'; })));
    const tajne = (popisek, ks) => {
      const bl = { ks, stav: 'skryte' };
      bloky.push(bl);
      let tik = 0, konec = 0;
      const hodnota = el('span', { class: 'ucet-hodnota heslo', text: TECKY });
      const odpocet = el('span', { class: 'ucet-odpocet' });
      const ukaz = odkaz('Ukázat', () => {
        if (bl.stav !== 'skryte') return;
        if (odkryte && odkryte !== bl) odkryte.skryj(false); // odkryté smí být jen jedno
        bl.stav = 'ceka';
        odkryte = bl;
        ukaz.disabled = true;
        zprava.textContent = '';
        N.ukazHeslo(ks);
      });
      const akce = el('div', { class: 'ucet-radek ucet-akce', hidden: true }, odpocet,
        odkaz('Kopírovat', () => {
          zprava.textContent = N.kopirujHeslo(ks) ? 'Zkopírováno, schránka se za minutu vyčistí.' : 'Kopírování se nepodařilo.';
        }),
        odkaz('Skrýt', () => bl.skryj(false)));
      // hodnota se z DOM úplně odstraní, N.skryjHeslo() vypne ochranu obrazovky v telefonu
      bl.skryj = zNativu => {
        clearInterval(tik);
        const bylo = bl.stav !== 'skryte';
        bl.stav = 'skryte';
        if (odkryte === bl) odkryte = null;
        hodnota.textContent = TECKY;
        hodnota.classList.remove('odkryte');
        odpocet.textContent = '';
        akce.hidden = true;
        ukaz.hidden = false;
        ukaz.disabled = false;
        if (bylo) {
          zprava.textContent = '';
          if (!zNativu) N.skryjHeslo();
        }
      };
      bl.prijmi = (text, bezZamku) => {
        ukaz.disabled = false;
        if (text == null || text === '') {
          bl.stav = 'skryte';
          odkryte = null;
          zprava.textContent = text == null ? popisek + ' nejde přečíst. Zadej ho znovu v Upravit.' : '';
          return;
        }
        bl.stav = 'odkryte';
        hodnota.textContent = text;
        hodnota.classList.add('odkryte');
        ukaz.hidden = true;
        akce.hidden = false;
        zprava.textContent = bezZamku ? 'Telefon nemá zámek obrazovky, ' + popisek + ' se proto ukázal bez ověření.' : '';
        konec = Date.now() + DOBA_HESLA * 1000;
        const tikni = () => {
          const zbyva = Math.ceil((konec - Date.now()) / 1000);
          if (zbyva <= 0) bl.skryj(false);
          else odpocet.textContent = 'Skryje se za ' + zbyva + ' s';
        };
        tikni();
        clearInterval(tik);
        tik = setInterval(tikni, 250);
      };
      radky.append(el('div', { class: 'ucet-radek' }, el('span', { class: 'ucet-popisek', text: popisek }), hodnota, ukaz), akce);
    };
    if (k.heslo) tajne('Heslo', k.heslo);
    if (k.pin) tajne('PIN', k.pin);
    if (bloky.length) v.priZavreni = () => bloky.forEach(b => b.skryj(false));
    // Přihlášení otevře prohlížeč: uložené heslo tam doplní správce hesel, do cizí stránky ho Kartička nevkládá.
    const web = k.odkaz || (program(k) || {}).web || '';
    const prihlasit = web && (k.ucet || k.heslo) ? el('button', { type: 'button', class: 'tlacitko siroke ucet-prihlasit', onclick: () => {
      const jmeno = !!k.ucet && N.kopiruj(k.ucet);
      zprava.textContent = !N.otevriProgram('', web) ? 'Stránku s přihlášením se nepodařilo otevřít.'
        : jmeno ? 'Jméno je zkopírované, heslo doplní správce hesel v prohlížeči.' : '';
    } }, 'Přihlásit se na webu') : '';
    return el('section', { class: 'ucet jen-odemcene' },
      el('h3', { class: 'sekce', text: k.ucet || k.heslo ? 'Účet' : 'PIN karty' }), radky, prihlasit, zprava);
  }

  // Další cesty k obchodu pod tlačítky: web věrnostního programu (i když Body a kupóny otevírají appku), appka
  // obchodu v Google Play, když v telefonu není, a vlastní odkaz karty. Přímý odkaz na obrazovku s body umí
  // jen appky, které ho zveřejnily (vernost.json: odkaz); ostatní se otevřou na úvodní obrazovce.
  function dalsiMoznosti(k, prog, zprava) {
    const casti = [];
    const otevri = (adresa, chyba) => () => { zprava.textContent = N.otevriProgram('', adresa) ? '' : chyba; };
    const balik = k.appka || (prog && prog.app) || '';
    const maApp = balik && umi('maAppku') ? N.maAppku(balik) : null;
    if (prog && prog.web && (maApp || k.odkaz)) casti.push(odkaz('Web programu', otevri(prog.web, 'Web obchodu se nepodařilo otevřít.')));
    if (k.odkaz && k.appka) casti.push(odkaz('Můj odkaz', otevri(k.odkaz, 'Odkaz se nepodařilo otevřít.')));
    if (balik && maApp === false && umi('obchodPlay')) casti.push(odkaz('Appka ' + k.nazev + ' v Google Play', () => {
      zprava.textContent = N.obchodPlay(balik) ? '' : 'Google Play se nepodařilo otevřít.';
    }));
    if (!casti.length) return '';
    const p = el('p', { class: 'info moznosti' });
    casti.forEach((c, i) => p.append(i ? ' · ' : '', c));
    return p;
  }

  function otevriDetail(k, bezAnimace, odkud) {
    jasTrvale = false;
    jasVypnuto = false;
    const p = cerstva(2 * 60000);
    const tady = !!p && okoli(p).blizko.some(b => b.k === k);
    if (tady) naucWifi(k); // kartu otevřel u její prodejny: tady je
    const predtim = k.naposledy; // detail ukazuje minulé ukázání, tohle teď je
    ukazana(k, odkud, odkud === 'm' && !tady);
    const v = vrstva(k.nazev, { jas: !!k.kod, trida: 'detail', karta: k, bezAnimace, id: k.id });
    const w = sirkaObsahu() - 32;
    const ctverec = CTVERCOVE.has(k.format);
    const zprava = el('p', { class: 'zprava', 'aria-live': 'polite' });
    const info = el('p', { class: 'info jen-odemcene' });
    const prog = program(k);
    const obnovInfo = () => {
      info.replaceChildren(infoText(k));
      if (k.mista.length || k.wifi.length) info.append(' · ', odkaz('Zapomenout moje místa', () => {
        k.mista = [];
        k.wifi = [];
        if (pol.vybrany && pol.vybrany.id === k.id) pol.vybrany = null;
        ulozData();
        zprava.textContent = 'Uložená místa jsou smazaná.';
        obnovInfo();
      }));
    };
    obnovInfo();
    const hvezda = el('button', { type: 'button', class: 'ikona hvezda jen-odemcene', 'aria-label': 'Oblíbená (v seznamu nahoře)',
      'aria-pressed': String(k.oblibena), onclick: () => {
        k.oblibena = !k.oblibena;
        hvezda.setAttribute('aria-pressed', String(k.oblibena));
        ulozData();
      } }, ikona('hvezda'));
    v.obal.querySelector('.vrstva-hlava').append(hvezda);
    const s = data.nastaveni.jas;
    // jas-miru drží šířku nejdelšího popisku, ať se při odpočtu nic neposouvá
    v.odpocet = el('span', { class: 'jas-zap', text: s ? 'Svítí ještě ' + s + ' s' : 'Svítí naplno' });
    const otevri = () => { zprava.textContent = otevriAppku(k) ? '' : 'Appku ani web obchodu se nepodařilo otevřít.'; };
    const maBody = (prog || k.odkaz) && k.kod;
    const letak = akce(k);
    const blokKodu = !k.kod ? null :
      el('div', { class: 'detail-kod', onclick: WEB ? null : klepNaKod },
        // web jas řídit nesmí, jen nenechá zhasnout obrazovku, dokud je karta otevřená (upravJas)
        WEB ? null : el('button', { type: 'button', class: 'detail-slunce', 'aria-label': s
          ? 'Rozsvítit obrazovku naplno na ' + s + ' ' + tvar(s, 'sekundu', 'sekundy', 'sekund') + '. Další klepnutí zhasne, dvojklik nechá svítit.'
          : 'Obrazovka svítí naplno. Klepnutí zhasne, dvojklik nechá svítit.' },
          el('span', { class: 'slunce-kruh' }, ikona('slunce')),
          el('span', { class: 'slunce-text' },
            el('span', { class: 'jas-vyp', text: s ? 'Rozsvítit na ' + s + ' s' : 'Rozsvítit naplno' }),
            v.odpocet,
            s ? el('span', { class: 'jas-miru', 'aria-hidden': 'true', text: 'Svítí ještě ' + s + ' s' }) : '',
            el('span', { class: 'jas-miru', 'aria-hidden': 'true', text: 'Svítí, dokud neklepneš' }))),
        obrazekKodu(k.format, k.kod, ctverec ? Math.min(w, 300) : w, 150),
        el('div', { class: 'detail-cislo', text: cisloKarty(k) || hezke(k.kod) }));
    v.telo.append(
      !k.kod ? el('div', { class: 'detail-appka' },
        el('button', { type: 'button', class: 'tlacitko hlavni velke', onclick: otevri }, ikona('darek'), 'Otevřít appku ' + k.nazev),
        el('p', { class: 'info', text: 'Kód pro pokladnu ukazuje appka obchodu, Kartička ji jen otevře.' }),
        el('button', { type: 'button', class: 'tlacitko velke', onclick: () => { if (odkryte) odkryte.skryj(false); formular({ karta: k, sKodem: true }); } },
          ikona('tuzka'), 'Uložit kód do Kartičky')) :
      blokKodu,
      k.cislo && !(k.kod && dlouhyKod(k)) ? el('div', { class: 'ucet-radky dalsi-cislo' }, el('div', { class: 'ucet-radek' },
        el('span', { class: 'ucet-popisek', text: 'Číslo' }),
        el('span', { class: 'ucet-hodnota', text: k.cislo }),
        odkaz('Kopírovat', () => { zprava.textContent = N.kopiruj(k.cislo) ? 'Číslo je zkopírované.' : 'Kopírování se nepodařilo.'; }))) : '',
      k.poznamka ? el('p', { class: 'poznamka jen-odemcene', text: k.poznamka }) : '',
      blokUctu(k, v),
      el('div', { class: 'akce' },
        // Body a kupóny a Akce a leták vedle sebe, a když je jen jedno, přes celou šířku.
        // U průkazů (ISIC) vede tlačítko na legitimaci v appce, popisek je ve vernost.json.
        maBody ? el('button', { type: 'button', class: 'tlacitko' + (letak ? '' : ' cela'), onclick: otevri },
          ikona(prog && prog.tlacitko ? 'dal' : 'darek'), prog && prog.tlacitko || 'Body a kupóny') : '',
        letak ? el('button', { type: 'button', class: 'tlacitko' + (maBody ? '' : ' cela'), onclick: () => {
          zprava.textContent = N.otevriProgram('', letak) ? '' : 'Stránku s akcemi se nepodařilo otevřít.';
        } }, ikona('stitek'), 'Akce a leták') : '',
        el('button', { type: 'button', class: 'tlacitko jen-odemcene', onclick: () => { if (odkryte) odkryte.skryj(false); formular({ karta: k }); } }, ikona('tuzka'), 'Upravit'),
        el('button', { type: 'button', class: 'tlacitko jen-odemcene', onclick: () => zapamatuj(k, zprava, obnovInfo) }, ikona('spendlik'), 'Jsem v prodejně')),
      zprava,
      dalsiMoznosti(k, prog, zprava),
      prog && prog.rada ? el('p', { class: 'info rada', text: prog.rada }) : '',
      info,
      el('div', { class: 'jen-odemcene' }, pouziti(k, predtim)),
      blokProdejen(k, v));
    // Kód u horního okraje: blok s kódem je nad hlavičkou, hned pod stavovým řádkem telefonu (čtečka u pokladny).
    if (k.kod && kodNahore(k)) {
      v.obal.classList.add('kod-nahore');
      v.obal.insertBefore(blokKodu, v.obal.querySelector('.vrstva-hlava'));
    }
  }

  // Prodejny karty dole v detailu, sbalené: mapa se načte až po rozbalení, ať nic nezdržuje a nepřekáží u pokladny.
  // Malá mapa se nedá posouvat (stránka pod ní normálně roluje), klepnutí otevře celou mapu prodejen karty.
  function blokProdejen(k, v) {
    const vse = prodejnyKarty(k);
    if (!vse.length) return '';
    const p = pol.p;
    const mista = vse.map(m => ({ lat: m[0], lon: m[1], v: p ? vzdalenost(p.lat, p.lon, m[0], m[1]) : 0 }));
    if (p) mista.sort((a, b) => a.v - b.v);
    let m = null;
    const det = el('details', { class: 'prodejny' },
      el('summary', {}, ikona('mapa'), el('span', { class: 'prodejny-nadpis', text: 'Prodejny na mapě' }),
        el('span', { class: 'prodejny-kolik', text: p ? 'nejbližší ' + km(mista[0].v)
          : mista.length + ' ' + tvar(mista.length, 'prodejna', 'prodejny', 'prodejen') })));
    det.addEventListener('toggle', () => {
      if (!det.open || m) return;
      const koren = el('div', { class: 'mapa-okoli' });
      const obal = el('div', { class: 'prodejny-mapa' }, koren,
        el('button', { type: 'button', class: 'prodejny-klep', 'aria-label': 'Otevřít celou mapu prodejen', onclick: () => otevriMapu(k) },
          el('span', { class: 'prodejny-cela' }, 'Celá mapa', ikona('dal'))),
        el('div', { class: 'm-osm', text: '© přispěvatelé OpenStreetMap' }));
      det.append(obal);
      // s polohou ona a nejbližší prodejny (do 1,8× vzdálenosti té první), bez polohy všechny
      const blizke = p ? mista.filter(o => o.v <= Math.max(500, mista[0].v * 1.8)).slice(0, 5) : mista;
      const start = prilozeni((p ? [[p.lat, p.lon]] : []).concat(blizke.map(o => [o.lat, o.lon])),
        obal.clientWidth || 300, obal.clientHeight || 190, 30);
      const znacky = [];
      m = Mapa(koren, { lat: start.lat, lon: start.lon, z: start.z, poPohybu: () => {
        const [w, h] = m.rozmer;
        if (p) {
          const [x, y] = m.bod(p.lat, p.lon);
          m.svg.innerHTML = '<circle class="m-ja" cx="' + x.toFixed(1) + '" cy="' + y.toFixed(1) + '" r="7"/>';
        }
        const videt = [];
        for (const o of mista) {
          const [x, y] = m.bod(o.lat, o.lon);
          if (x < -20 || y < -20 || x > w + 20 || y > h + 20) continue;
          if (videt.some(q => Math.abs(q[0] - x) < 30 && Math.abs(q[1] - y) < 30)) continue;
          videt.push([x, y]);
          if (videt.length >= 40) break;
        }
        videt.forEach(([x, y], i) => {
          if (!znacky[i]) znacky[i] = el('span', { class: 'm-obchod', style: barvy(k) },
            dlazdiceLoga(k) || el('b', { text: (k.nazev || '?').slice(0, 1).toUpperCase() }));
          znacky[i].style.transform = 'translate3d(' + (x - 18).toFixed(1) + 'px,' + (y - 18).toFixed(1) + 'px,0)';
          if (!znacky[i].isConnected) m.znacky.append(znacky[i]);
        });
        for (const e of znacky.slice(videt.length)) e.remove();
      } });
      const pred = v.priZavreni;
      v.priZavreni = () => { if (pred) pred(); m.zrus(); };
    });
    return det;
  }

  function pouziti(k, naposledy) {
    const n = data.historie.filter(z => z[1] === k.id).length;
    return el('p', { class: 'info' }, 'Ukázaná ' + n + '× · naposledy ' + kdy(naposledy) + ' · ',
      odkaz('Historie', () => historie(k)));
  }

  // Klepnutí na widget otevře změněné ve Vzhledu widgetu (nativní okno nad appkou): uloží se a přepne i v otevřeném Nastavení.
  window.nastavZvenku = function (klic, h) {
    if (klic !== 'widget' || (h !== 'hlavni' && h !== 'karta')) return;
    data.nastaveni.widget = h;
    ulozData();
    for (const v of vrstvy) if (v.nastavWidget) v.nastavWidget(h);
  };

  // „Jsem v prodejně" na velkém widgetu (jako Taky tady): místo ke kartě uloží běžící appka, ať ho dalším uložením nepřepíše.
  window.potvrdZvenku = function (id, lat, lon) {
    const k = najdi(id);
    if (!k || !isFinite(lat) || !isFinite(lon)) return;
    if (!k.mista) k.mista = [];
    pridejMisto(k, { lat, lon });
    if (Array.isArray(k.wifi)) naucWifi(k); // otisk sítí jen čerstvý (wifiTed), jinak nic
    ulozData();
    vykresliTady(true);
  };

  // Klepnutí na widget: podle Nastavení hlavní stránka (nahoře karta obchodu, kde jsi) nebo rovnou karta z widgetu.
  window.naOtevreni = function (id) {
    if (data.nastaveni.widget === 'karta') {
      const k = najdi(id);
      if (!k) return;
      zavri(true, true);
      otevriDetail(k, true, 'w');
      return;
    }
    if (vrstvy.length) zavri(true, true);
    if (hledaniKaret.value) { hledaniKaret.value = ''; hledaniKaret.blur(); filtrujSeznam(); }
    window.scrollTo(0, 0);
  };

  function zapamatuj(k, zprava, hotovo) {
    const uloz = p => {
      pridejMisto(k, p);
      naucWifi(k);
      pol.vybrany = { id: k.id, lat: p.lat, lon: p.lon, cas: Date.now() };
      ulozData();
      pol.klic = null;
      zprava.textContent = 'Zapamatováno. Příště se tu karta ukáže sama.';
      hotovo();
    };
    const p = cerstva(60000);
    if (p && p.presnost <= 100) { uloz(p); return; }
    zprava.textContent = 'Zjišťuji, kde jsi…';
    cekaMisto = (q, chyba) => {
      if (q && q.presnost <= 300) uloz(q);
      else if (chyba === 'zakazano') zprava.textContent = 'Appka nemá povolenou polohu.';
      else if (chyba === 'vypnuto') zprava.textContent = 'Poloha je v telefonu vypnutá.';
      else zprava.textContent = 'Polohu se nepodařilo zjistit dost přesně. Zkus to znovu u vchodu.';
    };
    zjisti(true, true);
  }

  // ---------- přidání karty ----------

  let kodCil = null;
  function skenuj(cil, zFotky) {
    kodCil = cil;
    if (zFotky) N.zFotky(); else N.skenovat();
  }
  window.naKod = function (format, text, chyba) {
    const c = kodCil;
    kodCil = null;
    if (c) c(format, text, chyba);
  };
  // opsat: kam vede odkaz „číslo opiš“, když kód ve fotce nejde přečíst (karta se pak přidá i tak).
  function chybaSkenu(zprava, chyba, opsat) {
    zprava.replaceChildren();
    if (chyba === 'nenalezeno') zprava.append('Ve fotce není žádný čitelný kód. Zkus ostřejší fotku, nebo ',
      opsat ? odkaz('číslo opiš', opsat) : 'číslo opiš', '.');
    else if (chyba === 'kamera') zprava.append('Appka nemá povolenou kameru. ', odkaz('Otevřít nastavení', () => N.nastaveni()));
  }

  // Přidat kartu: buď nejdřív obchod a pak kód, nebo nejdřív naskenovat a pak k tomu vybrat obchod (nacteno = kód ze skenu).
  function pridat(nacteno) {
    if (chceOdemknout()) return;
    const v = vrstva(nacteno ? 'Čí je to karta?' : 'Přidat kartu');
    const hledani = el('input', { type: 'search', placeholder: 'Hledat obchod nebo kartu', 'aria-label': 'Hledat obchod nebo kartu',
      autocomplete: 'off', spellcheck: 'false', enterkeyhint: 'search' });
    const seznam = el('div', { class: 'retezce' });
    const radky = [];
    const dal = vzor => nacteno ? formular({ vzor, format: nacteno.format, kod: nacteno.text, zdroj: 'sken' }) : zpusob(vzor);
    // Cokoli, co v seznamu není: při hledání řádek nabídne přidat kartu přesně pod napsaným názvem.
    const jina = radekRetezce(null, () => dal({
      retezec: '', nazev: hledani.value.trim().slice(0, 60), barva: PALETA[0], text: BILA
    }));
    seznam.append(el('div', { class: 'skupina' }, jina));
    if (nacteno) {
      v.telo.append(el('div', { class: 'nahled-kod' },
        obrazekKodu(nacteno.format, nacteno.text, CTVERCOVE.has(nacteno.format) ? 120 : sirkaObsahu() - 32, 64),
        el('div', { class: 'nacteno-cislo', text: hezke(nacteno.text) })));
    } else {
      const zprava = el('p', { class: 'zprava', 'aria-live': 'polite' });
      const hotovo = (format, text, chyba) => {
        if (format && text) { zprava.textContent = ''; pridat({ format, text }); }
        else if (chyba === 'nenalezeno') {
          // Obchod ještě není vybraný: odkaz vede do hledání, i obchod mimo seznam se tam přidá pod svým názvem.
          chybaSkenu(zprava, chyba, () => hledani.focus());
          zprava.append(' Napřed vyhledej obchod, i ten, který v seznamu není.');
        } else chybaSkenu(zprava, chyba);
      };
      v.telo.append(el('div', { class: 'akce' },
        el('button', { type: 'button', class: 'tlacitko hlavni', onclick: () => skenuj(hotovo, false) }, ikona('foto'), 'Naskenovat'),
        el('button', { type: 'button', class: 'tlacitko', onclick: () => skenuj(hotovo, true) }, ikona('obrazek'), 'Z fotky')),
        zprava,
        el('p', { class: 'info pred-hledanim' }, 'Karty v jiné appce? ', odkaz('Přenést je najednou', prenos)));
    }
    const skupiny = new Map();
    const mam = new Set(data.karty.map(k => k.retezec).filter(Boolean));
    for (const r of RETEZCE) {
      if (r.zanikla && !mam.has(r.id)) continue; // značka sloučená do jiné: nová karta na ni nevzniká, existující zůstává
      if (!skupiny.has(r.skupina)) skupiny.set(r.skupina, []);
      skupiny.get(r.skupina).push(r);
    }
    const bloky = [], vSkupinach = [], obchody = [];
    for (const [nazev, rr] of skupiny) {
      const blok = el('div', { class: 'skupina' }, el('h3', { text: nazev }));
      const radkySkupiny = [];
      for (const r of rr) {
        const rad = radekRetezce(r, () => dal({ retezec: r.id, nazev: r.nazev, barva: r.barva, text: r.text }));
        rad.dataset.hledat = bezDiakritiky(r.nazev + (r.hledat ? ' ' + r.hledat : ''));
        if (mam.has(r.id)) rad.dataset.mam = '1';
        blok.append(rad);
        radky.push(rad);
        radkySkupiny.push(rad);
        obchody.push({ r, rad, pocet: rad.querySelector('.retezec-pocet'), n: (prodejny()[r.id] || []).length });
      }
      bloky.push(blok);
      vSkupinach.push([blok, radkySkupiny]);
      seznam.append(blok);
    }
    // Při jiném řazení než podle druhu jsou všechny obchody v jednom bloku bez nadpisů.
    const vsechny = el('div', { class: 'skupina' });
    bloky.push(vsechny);
    seznam.append(vsechny);
    const filtruj = () => {
      const text = hledani.value.trim().slice(0, 60);
      const q = bezDiakritiky(text);
      // Obchody, které už kartu mají, skryje zaškrtávátko; hledání je najde i tak (druhá karta téhož obchodu).
      for (const r of radky) r.hidden = q ? !r.dataset.hledat.includes(q) : !!(data.nastaveni.skrytMoje && r.dataset.mam);
      for (const b of bloky) b.hidden = !b.querySelector('.retezec:not([hidden])');
      jina.querySelector('.retezec-nazev').textContent = text ? 'Přidat „' + text + '“' : 'Jiná karta';
    };
    hledani.addEventListener('input', filtruj);

    const zRazeni = el('p', { class: 'info razeni-zprava', 'aria-live': 'polite' });
    let cekam = 0, zkousim = false;
    function serad() {
      const h = data.nastaveni.razeni;
      const p = h === 'blizko' ? cerstva() || pol.p : null;
      let zprava = '';
      clearTimeout(cekam);
      // Starou nebo žádnou polohu appka obnoví a seznam po ní srovná znovu. Bez polohy zůstane řazení podle druhu.
      if (h === 'blizko' && !cerstva()) {
        if (pol.stav === 'zakazano' || pol.stav === 'vypnuto') zprava = 'Bez polohy appka nepozná, co je nejblíž. Zapni polohu.';
        else if (zkousim && !pol.hledam) { zkousim = false; zprava = 'Polohu se nepodařilo zjistit.'; }
        else {
          // bez karet s místy appka po startu polohu nehledá, ale pol.hledam zůstává z výchozího stavu
          if (!pol.hledam || !zkousim) zjisti(true, true);
          zkousim = true;
          zprava = 'Zjišťuju, kde jsi…';
          cekam = setTimeout(() => { if (v.telo.isConnected && data.nastaveni.razeni === 'blizko') serad(); }, 1500);
        }
      } else zkousim = false;
      zRazeni.textContent = p ? '' : zprava;
      zRazeni.hidden = !zRazeni.textContent;
      for (const o of obchody) {
        o.d = p ? (prodejny()[o.r.id] || []).reduce((nej, m) => Math.min(nej, vzdalenost(p.lat, p.lon, m[0], m[1])), Infinity) : Infinity;
        o.pocet.textContent = p && o.d < Infinity ? km(o.d) : o.n ? o.n + ' ' + tvar(o.n, 'prodejna', 'prodejny', 'prodejen') : '';
      }
      if (h === 'druh' || (h === 'blizko' && !p)) {
        for (const [blok, rr] of vSkupinach) blok.append(...rr);
      } else {
        const abc = (a, b) => a.r.nazev.localeCompare(b.r.nazev, 'cs');
        const poradi = obchody.slice().sort(h === 'blizko' ? (a, b) => a.d - b.d || abc(a, b)
          : h === 'prodejny' ? (a, b) => b.n - a.n || abc(a, b) : abc);
        vsechny.append(...poradi.map(o => o.rad));
      }
      filtruj();
    }
    const volby = RAZENI.map(([h, text]) => el('button', { type: 'button', role: 'radio', text,
      'aria-checked': String(h === data.nastaveni.razeni), onclick: () => {
        if (h === data.nastaveni.razeni) return;
        data.nastaveni.razeni = h;
        zkousim = false;
        ulozData();
        volby.forEach((b, i) => b.setAttribute('aria-checked', String(RAZENI[i][0] === h)));
        drzMisto(vyber, v.telo, serad);
      } }));
    const vyber = el('div', { class: 'prepinac razeni', role: 'radiogroup', 'aria-label': 'Řadit obchody' }, volby);
    const fSkryt = el('input', { type: 'checkbox' });
    fSkryt.checked = data.nastaveni.skrytMoje;
    fSkryt.addEventListener('change', () => { data.nastaveni.skrytMoje = fSkryt.checked; ulozData(); filtruj(); });
    const skryt = mam.size ? el('label', { class: 'zaskrtnuti skryt-moje' }, fSkryt,
      el('span', { class: 'zaskrtnuti-text' }, el('span', { class: 'zaskrtnuti-nazev', text: 'Skrýt obchody, které už mám' }))) : '';
    v.telo.append(el('label', { class: 'hledat' }, ikona('lupa'), hledani), vyber, skryt, zRazeni, seznam);
    serad();
  }

  function radekRetezce(r, akce) {
    const n = r ? (prodejny()[r.id] || []).length : 0;
    return el('button', { type: 'button', class: 'retezec', onclick: akce },
      (r && logo({ retezec: r.id, nazev: r.nazev }, 'radek')) ||
        el('span', { class: 'mini', style: r ? barvy(r) : '--b:' + SEDA_ZNACKY + ';--t:' + TMAVA }, r ? r.nazev.charAt(0) : ikona('plus')),
      el('span', { class: 'retezec-nazev', text: r ? r.nazev : 'Jiná karta' }),
      el('span', { class: 'retezec-pocet', text: r ? (n ? n + ' ' + tvar(n, 'prodejna', 'prodejny', 'prodejen') : '') : 'vlastní karta' }));
  }

  function zpusob(vzor) {
    const v = vrstva(vzor.retezec ? vzor.nazev : vzor.nazev || 'Jiná karta');
    const zprava = el('p', { class: 'zprava', 'aria-live': 'polite' });
    const hotovo = (format, text, chyba) => {
      if (format && text) { zprava.textContent = ''; formular({ vzor, format, kod: text, zdroj: 'sken' }); }
      else chybaSkenu(zprava, chyba, () => formular({ vzor, zdroj: 'rucne' }));
    };
    // McDonald's a spol.: kód pro pokladnu ukazuje appka obchodu a mění se. Přidává se jako ostatní karty (naskenovat
    // nebo opsat kód z appky), nebo jako karta bez čísla, která tu appku jen otevře.
    const prog = program(vzor);
    const jenAppka = !!(prog && prog.app && prog.jenAppka);
    const ukazKod = () => { zprava.textContent = otevriAppku(vzor) ? '' : 'Appku ani web obchodu se nepodařilo otevřít.'; };
    v.telo.append(
      el('div', { class: 'nahled' }, dlazdice({ nazev: vzor.nazev || 'Nový obchod', barva: vzor.barva, text: vzor.text, kod: '' }, 'div')),
      jenAppka ? el('p', { class: 'info' }, (prog.kodText || 'Kód je v appce ' + vzor.nazev + ' a po každém otevření appky je nový.') + ' ',
        odkaz('Ukázat kód', ukazKod)) : '',
      el('div', { class: 'volby' },
        el('button', { type: 'button', class: 'tlacitko hlavni velke', onclick: () => skenuj(hotovo, false) }, ikona('foto'), 'Naskenovat kartu'),
        el('button', { type: 'button', class: 'tlacitko velke', onclick: () => skenuj(hotovo, true) }, ikona('obrazek'), 'Najít kód ve fotce'),
        el('button', { type: 'button', class: 'tlacitko velke', onclick: () => formular({ vzor, zdroj: 'rucne' }) }, ikona('tuzka'),
          jenAppka ? 'Opsat kód' : 'Opsat číslo'),
        jenAppka ? el('button', { type: 'button', class: 'tlacitko velke', onclick: () => formular({ vzor, zdroj: 'appka' }) },
          ikona('darek'), 'Jen otevírat appku') : ''),
      zprava);
  }

  function pole(popisek, prvek, ...pod) {
    return el('label', { class: 'pole' }, el('span', { class: 'popisek', text: popisek }), prvek, ...pod);
  }

  function formular(o) {
    if (chceOdemknout()) return;
    const upr = o.karta || null;
    const z = upr
      ? { retezec: upr.retezec, nazev: upr.nazev, barva: upr.barva, text: upr.text }
      : { retezec: o.vzor.retezec, nazev: o.vzor.nazev, barva: o.vzor.barva, text: o.vzor.text };
    const vlastni = !z.retezec;
    // Karta v appce: bez čísla, uloží balíček appky obchodu, kterou karta otevře. sKodem = kartě v appce se dopisuje kód.
    const prog = program(z);
    const appkaObchodu = (upr && !upr.kod && upr.appka) || (prog && prog.app) || '';
    let vAppce = !!appkaObchodu && (upr ? !upr.kod && !o.sKodem : o.zdroj === 'appka');
    let zdroj = upr ? 'ulozeno' : o.zdroj;
    // Opsaný kód má výchozí typ podle programu (McDonald's ukazuje v appce QR). Karta v appce má uložené QR jen
    // jako zástupný typ, proto se jí při dopsání kódu typ volí jako nové kartě.
    let typ = upr && upr.kod ? upr.format : (o.format || (prog && FORMATY.includes(prog.format) ? prog.format : 'AUTO'));
    let pismena = !CISELNE.has(typ);

    const v = vrstva(upr ? 'Upravit kartu' : 'Nová karta');
    const nahled = el('div', { class: 'nahled' });
    const nahledKod = el('div', { class: 'nahled-kod' });
    const fNazev = el('input', { maxlength: '60', autocomplete: 'off', placeholder: vlastni ? 'Třeba Lékárna U Anděla' : '' });
    fNazev.value = z.nazev;
    const fKod = el('input', { autocomplete: 'off', spellcheck: 'false', autocapitalize: 'characters' });
    fKod.value = upr ? upr.kod : (o.kod || '');
    const fTyp = el('select');
    const autoVolba = el('option', { value: 'AUTO', text: 'Automaticky' });
    fTyp.append(autoVolba);
    const formaty = NABIDKA.slice();
    if (typ !== 'AUTO' && !formaty.includes(typ)) formaty.push(typ);
    for (const f of formaty) fTyp.append(el('option', { value: f, text: NAZVY[f] }));
    fTyp.value = typ;
    const fPozn = el('textarea', { rows: '2', maxlength: '500', placeholder: 'Třeba na koho je karta' });
    fPozn.value = upr ? upr.poznamka : '';
    const fCislo = el('input', { type: 'text', maxlength: '60', autocomplete: 'off', autocapitalize: 'characters', spellcheck: 'false' });
    fCislo.value = upr ? upr.cislo || '' : '';
    const fOdkaz = el('input', { type: 'url', maxlength: '500', autocomplete: 'off', autocapitalize: 'off', spellcheck: 'false',
      inputmode: 'url', placeholder: 'https://' });
    fOdkaz.value = upr ? upr.odkaz || '' : '';
    const poleOdkaz = pole('Odkaz na body (nepovinné)', fOdkaz,
      el('span', { class: 'pod-polem', text: 'Třeba osobní stránka karty s body nebo razítky. Tlačítko Body a kupóny pak otevře ji.' }));
    // Výchozí stav podle řetězce (Albert má kód nahoře), po uložení formuláře je volba zapsaná přímo v kartě.
    const fNahore = el('input', { type: 'checkbox' });
    fNahore.checked = upr ? kodNahore(upr) : NAHORE.has(z.retezec);
    const poleNahore = el('label', { class: 'zaskrtnuti' }, fNahore,
      el('span', { class: 'zaskrtnuti-text' },
        el('span', { class: 'zaskrtnuti-nazev', text: 'Kód u horního okraje obrazovky' }),
        el('span', { class: 'zaskrtnuti-popis', text: 'Pro pokladny, kde se ke čtečce přikládá horní část telefonu (třeba Albert).' })));
    const fUcet = el('input', { maxlength: '200', autocomplete: 'off', autocapitalize: 'off', spellcheck: 'false', inputmode: 'email' });
    fUcet.value = upr ? upr.ucet || '' : '';
    const mamHeslo = !!(upr && upr.heslo);
    let smazatHeslo = false;
    const fHeslo = el('input', { type: 'password', maxlength: '200', autocomplete: 'new-password', autocapitalize: 'off',
      spellcheck: 'false', placeholder: mamHeslo ? 'Uložené, beze změny' : '' });
    const oko = el('button', { type: 'button', class: 'oko', 'aria-label': 'Ukázat, co píšu', 'aria-pressed': 'false', onclick: () => {
      const ukazat = fHeslo.type === 'password';
      fHeslo.type = ukazat ? 'text' : 'password';
      oko.setAttribute('aria-pressed', String(ukazat));
      oko.replaceChildren(ikona(ukazat ? 'okoZavrene' : 'oko'));
    } }, ikona('oko'));
    const smaz = odkaz('Smazat heslo', () => {
      smazatHeslo = !smazatHeslo;
      smaz.textContent = smazatHeslo ? 'Nemazat' : 'Smazat heslo';
      fHeslo.value = '';
      fHeslo.disabled = smazatHeslo;
      fHeslo.placeholder = smazatHeslo ? 'Heslo se smaže po uložení' : 'Uložené, beze změny';
      chyba.textContent = '';
    });
    const mamPin = !!(upr && upr.pin);
    let smazatPin = false;
    const fPin = el('input', { type: 'password', maxlength: '20', autocomplete: 'new-password', autocapitalize: 'off',
      spellcheck: 'false', inputmode: 'numeric', placeholder: mamPin ? 'Uložený, beze změny' : '' });
    const okoPin = el('button', { type: 'button', class: 'oko', 'aria-label': 'Ukázat, co píšu', 'aria-pressed': 'false', onclick: () => {
      const ukazat = fPin.type === 'password';
      fPin.type = ukazat ? 'text' : 'password';
      okoPin.setAttribute('aria-pressed', String(ukazat));
      okoPin.replaceChildren(ikona(ukazat ? 'okoZavrene' : 'oko'));
    } }, ikona('oko'));
    const smazPin = odkaz('Smazat PIN', () => {
      smazatPin = !smazatPin;
      smazPin.textContent = smazatPin ? 'Nemazat' : 'Smazat PIN';
      fPin.value = '';
      fPin.disabled = smazatPin;
      fPin.placeholder = smazatPin ? 'PIN se smaže po uložení' : 'Uložený, beze změny';
      chyba.textContent = '';
    });
    const chyba = el('p', { class: 'chyba', 'aria-live': 'assertive' });
    const zpravaSkenu = el('p', { class: 'zprava' });

    function cistyKod() {
      let t = fKod.value.trim();
      if (zdroj !== 'sken' && zdroj !== 'ulozeno' && /^[\d\s-]+$/.test(t)) t = t.replace(/[\s-]/g, '');
      return t;
    }
    function format(kod) { return typ === 'AUTO' ? autoFormat(kod) : typ; }

    let casovac = 0;
    function obnov() {
      const kod = vAppce ? '' : cistyKod();
      const f = format(kod);
      autoVolba.textContent = 'Automaticky' + (kod ? ' (' + NAZVY[f] + ')' : '');
      fKod.inputMode = pismena ? 'text' : 'numeric';
      nahled.replaceChildren(dlazdice({ nazev: fNazev.value.trim() || (vlastni ? 'Nový obchod' : z.nazev), barva: z.barva, text: z.text, kod, format: f,
        appka: vAppce ? appkaObchodu : '' }, 'div'));
      clearTimeout(casovac);
      casovac = setTimeout(() => {
        const w = sirkaObsahu() - 32;
        nahledKod.replaceChildren(kod ? obrazekKodu(f, kod, CTVERCOVE.has(f) ? 150 : w, 80)
          : el('div', { class: 'kod-prazdny', text: 'Tady se ukáže čárový kód' }));
      }, 180);
    }

    const sken = (format, text, ch) => {
      if (format && text) {
        fKod.value = text;
        zdroj = 'sken';
        typ = format;
        if (![...fTyp.options].some(x => x.value === format)) fTyp.append(el('option', { value: format, text: NAZVY[format] || format }));
        fTyp.value = format;
        pismena = !CISELNE.has(typ);
        zpravaSkenu.textContent = '';
        obnov();
      } else chybaSkenu(zpravaSkenu, ch);
    };

    fNazev.addEventListener('input', obnov);
    fKod.addEventListener('input', () => { if (zdroj !== 'rucne') zdroj = 'rucne'; chyba.textContent = ''; obnov(); });
    fTyp.addEventListener('change', () => { typ = fTyp.value; pismena = !CISELNE.has(typ); chyba.textContent = ''; obnov(); });

    const jenAppka = !!(prog && prog.jenAppka);
    const poleKod = pole(jenAppka ? 'Kód z appky' : 'Číslo karty', fKod,
      el('span', { class: 'pod-polem' },
        odkaz('Naskenovat', () => skenuj(sken, false)), ' · ',
        odkaz('Z fotky', () => skenuj(sken, true)),
        CISELNE.has(typ) ? [' · ', odkaz('Klávesnice s písmeny', () => { pismena = true; fKod.inputMode = 'text'; fKod.blur(); fKod.focus(); })] : null,
        appkaObchodu ? [' · ', odkaz(jenAppka ? 'Jen otevírat appku' : 'Kód je jen v appce', () => prepni(true))] : null));
    const poleTyp = pole('Typ kódu', fTyp);
    const blokAppka = el('div', { class: 'pole' },
      el('span', { class: 'popisek', text: 'Kód pro pokladnu' }),
      el('p', { class: 'info', text: 'Ukáže ho appka ' + z.nazev + '. Karta ji otevře, i z widgetu.' }),
      el('span', { class: 'pod-polem' }, odkaz(jenAppka ? 'Uložit kód do Kartičky' : 'Mám číslo karty', () => prepni(false))));
    function prepni(ano) {
      vAppce = ano;
      for (const x of [nahledKod, poleKod, zpravaSkenu, poleTyp, poleOdkaz, poleNahore]) x.style.display = ano ? 'none' : '';
      blokAppka.style.display = ano ? '' : 'none';
      chyba.textContent = '';
      obnov();
    }

    const casti = [
      nahled,
      nahledKod,
      pole('Název', fNazev),
      poleKod,
      blokAppka,
      zpravaSkenu,
      poleTyp
    ];

    if (vlastni) {
      const vzorky = el('div', { class: 'vzorky', role: 'radiogroup', 'aria-label': 'Barva karty' });
      const vyber = b => {
        z.barva = b;
        z.text = textNa(b);
        for (const x of vzorky.children) x.setAttribute('aria-checked', x.dataset.barva === b ? 'true' : 'false');
        obnov();
      };
      for (const b of PALETA) {
        const t = el('button', { type: 'button', class: 'vzorek', role: 'radio', 'aria-label': 'Barva ' + b, style: '--b:' + b, onclick: () => vyber(b) });
        t.dataset.barva = b;
        vzorky.append(t);
      }
      vyber(BARVA.test(z.barva) ? z.barva : PALETA[0]);
      casti.push(el('div', { class: 'pole' }, el('span', { class: 'popisek', text: 'Barva' }), vzorky));
    }

    casti.push(pole('Poznámka', fPozn),
      pole('Další číslo na kartě (nepovinné)', fCislo,
        el('span', { class: 'pod-polem', text: 'Třeba horní číslo na ISIC, když ho po tobě chtějí. V kartě půjde zkopírovat.' })),
      poleOdkaz,
      poleNahore,
      WEB ? null : pole('PIN karty (nepovinné)', el('div', { class: 'heslo-obal' }, fPin, okoPin), // web nemá zámek telefonu
        el('span', { class: 'pod-polem' }, mamPin ? [smazPin, ' · '] : null,
          'Třeba PIN k dm kartě. Uloží se zamčený a v kartě se ukáže až po otisku nebo zámku telefonu, takže ho neuvidí nikdo, komu telefon s kartou podáš.')),
      el('h3', { class: 'sekce', text: 'Účet (nepovinné)' }),
      pole('Přihlašovací jméno', fUcet),
      WEB ? null : pole('Heslo', el('div', { class: 'heslo-obal' }, fHeslo, oko), // web nemá zámek telefonu, heslo by nebylo zamčené
        el('span', { class: 'pod-polem' }, mamHeslo ? [smaz, ' · '] : null,
          'Uloží se zamčené, ukáže se až po odemknutí telefonu.')),
      chyba,
      el('button', { type: 'button', class: 'tlacitko hlavni siroke', onclick: uloz }, 'Uložit'));
    if (upr) casti.push(el('button', { type: 'button', class: 'tlacitko nebezpeci siroke', onclick: () =>
      potvrd('Smazat kartu ' + upr.nazev + '?', 'Smazat', () => {
        data.karty = data.karty.filter(x => x !== upr);
        ulozData();
      }) }, 'Smazat kartu'));

    v.telo.append(...casti);
    prepni(vAppce);

    function zle(text, prvek) {
      chyba.textContent = text;
      if (prvek) prvek.focus();
    }

    function uloz() {
      const kod = vAppce ? '' : cistyKod();
      const nazev = fNazev.value.trim() || (vlastni ? '' : z.nazev);
      if (!nazev) return zle('Napiš název obchodu.', fNazev);
      if (!kod && !vAppce) return zle('Chybí číslo karty.', fKod);
      const odkazNaBody = vAppce ? '' : cistyOdkaz(fOdkaz.value);
      if (!vAppce && fOdkaz.value.trim() && !odkazNaBody) return zle('Odkaz na body musí začínat https://', fOdkaz);
      const f = vAppce ? (upr && FORMATY.includes(upr.format) ? upr.format : 'QR_CODE') : format(kod);
      if (!vAppce && KRESLI && !N.obrazek(f, kod, 400, 100)) return zle('Tohle číslo nejde zapsat jako ' + NAZVY[f] + '. Zkus jiný typ kódu.', fKod);
      let heslo = upr ? upr.heslo || '' : '';
      if (smazatHeslo) heslo = '';
      else if (fHeslo.value) {
        heslo = N.zamkni(fHeslo.value); // zamčené klíčem telefonu, otevřené se nikam neukládá
        if (!HESLO_KS.test(heslo)) return zle('Heslo se nepodařilo uložit. Zkus to znovu.', fHeslo);
      }
      fHeslo.value = '';
      let pin = upr ? upr.pin || '' : '';
      if (smazatPin) pin = '';
      else if (fPin.value.trim()) {
        pin = N.zamkni(fPin.value.trim());
        if (!HESLO_KS.test(pin)) return zle('PIN se nepodařilo uložit. Zkus to znovu.', fPin);
      }
      fPin.value = '';
      const k = upr || { id: noveId(), retezec: z.retezec, oblibena: false, pouzito: 0, naposledy: 0, mista: [], wifi: [] };
      Object.assign(k, { nazev: nazev.slice(0, 60), kod, format: f, appka: vAppce ? appkaObchodu : '', barva: z.barva, text: z.text, poznamka: fPozn.value.trim().slice(0, 500),
        cislo: fCislo.value.trim().slice(0, 60), odkaz: odkazNaBody, ucet: fUcet.value.trim().slice(0, 200), heslo, pin, nahore: fNahore.checked });
      if (!upr) data.karty.push(k);
      ulozData();
      if (upr) {
        zavri(false, true);
        const top = vrstvy[vrstvy.length - 1];
        if (top && top.id === k.id) zavri(false, true);
        otevriDetail(k, true);
      } else {
        zavri(true);
        zjisti(true);
      }
    }
  }

  // ---------- karty z jiné appky ----------

  // Pro toho, kdo přechází z jiné appky (Klarna, Google Peněženka, Catima…): snímky obrazovky karet vybrané
  // naráz, nebo soubor (export Catimy, tabulka, seznam „obchod;číslo"). Uloží se až po kontrole v přehledu.

  // Obchod z mapy podle názvu i s dovětkem („Tesco Clubcard" → Tesco): nejdelší začátek názvu, který je obchod.
  function retezecProNazev(nazev) {
    const slova = String(nazev || '').trim().split(/\s+/);
    for (let n = slova.length; n > 0; n--) {
      const id = PODLE_NAZVU[klicNazvu(slova.slice(0, n).join(''))];
      if (id) return id;
    }
    return '';
  }

  // Obchody k napsanému textu (už bez diakritiky): napřed začátek názvu, pak začátek slova, pak kdekoli i v hledacích slovech.
  // Přesně napsaný název nic nenabízí, výběr je hotový.
  function navrhyObchodu(q) {
    if (!q) return [];
    const nalez = [];
    for (const r of RETEZCE) {
      if (r.zanikla) continue;
      const n = bezDiakritiky(r.nazev);
      if (n === q) return [];
      const s = n.startsWith(q) ? 0 : (' ' + n).includes(' ' + q) ? 1 : (n + ' ' + bezDiakritiky(r.hledat || '')).includes(q) ? 2 : -1;
      if (s >= 0) nalez.push([s, r]);
    }
    return nalez.sort((a, b) => a[0] - b[0]).slice(0, 4).map(x => x[1]);
  }

  const FORMAT_Z_NAZVU = { qr: 'QR_CODE', code128: 'CODE_128' };
  for (const f of FORMATY) FORMAT_Z_NAZVU[klicNazvu(f)] = f;

  // Sloupce tabulky podle záhlaví. Nejdřív přesné shody (Catima má store, cardid, barcodetype…), pak části slov.
  const SLOUPCE = [
    ['format', ['barcodetype', 'typkodu', 'formatkodu', 'format', 'typ', 'type', 'symbology']],
    ['kodCary', ['barcodeid']],
    ['kod', ['cardid', 'cislokarty', 'cisloclenstvi', 'cardnumber', 'membershipnumber', 'cislo', 'kod', 'number', 'barcode', 'code', 'ean']],
    ['poznamka', ['poznamka', 'note', 'notes', 'popis']],
    ['barva', ['headercolor', 'barva', 'color', 'colour']],
    ['nazev', ['store', 'nazev', 'obchod', 'merchant', 'shop', 'name', 'program', 'retezec', 'karta', 'card']]
  ];
  function sloupce(bunky) {
    const k = bunky.map(klicNazvu);
    const h = {};
    const vzate = new Set();
    for (const presne of [true, false]) {
      for (const [role, slova] of SLOUPCE) {
        if (role in h) continue;
        for (const s of slova) {
          const i = k.findIndex((x, j) => !vzate.has(j) && x && (presne ? x === s : x.includes(s)));
          if (i >= 0) { h[role] = i; vzate.add(i); break; }
        }
      }
    }
    h.skupiny = k.includes('groupid'); // Catima: tabulka, která karta je v jaké skupině
    return h;
  }

  function barvaZ(s) {
    s = String(s || '').trim();
    if (/^-?\d+$/.test(s)) return '#' + ((+s >>> 0) & 0xFFFFFF).toString(16).padStart(6, '0').toUpperCase(); // Catima: ARGB číslo
    const m = /^#?([0-9a-f]{6})$/i.exec(s);
    return m ? '#' + m[1].toUpperCase() : '';
  }
  const cislic = s => (String(s).match(/\d/g) || []).length;
  const bezMezer = s => /^[\d\s-]+$/.test(s) ? s.replace(/[\s-]/g, '') : s;

  function polozkaZRadku(r, h) {
    const b = role => role in h ? String(r[h[role]] == null ? '' : r[h[role]]).trim() : '';
    const kod = bezMezer(b('kodCary') || b('kod'));
    if (kod.length < 4) return null;
    return { nazev: b('nazev').slice(0, 60), kod, format: FORMAT_Z_NAZVU[klicNazvu(b('format'))] || '',
      poznamka: b('poznamka'), barva: barvaZ(b('barva')) };
  }

  // Řádek bez záhlaví: „Lidl 1234 5678", „1234 5678 Lidl", nebo buňky obchod a číslo v libovolném pořadí.
  function polozkaBezZahlavi(r) {
    const bunky = r.filter(Boolean);
    if (bunky.length === 1) {
      const s = bunky[0];
      let m = /^(.*?\p{L}.*?)[\s:;,]+(\d[\d -]*\d)$/u.exec(s);
      if (m && cislic(m[2]) >= 4) return { nazev: m[1].replace(/[\s:;,.-]+$/, '').slice(0, 60), kod: bezMezer(m[2]) };
      m = /^(\d[\d -]*\d)[\s:;,]+(.*\p{L}.*)$/u.exec(s);
      if (m && cislic(m[1]) >= 4) return { nazev: m[2].replace(/^[\s:;,.-]+/, '').slice(0, 60), kod: bezMezer(m[1]) };
      if (/^[\d\s-]+$/.test(s) && cislic(s) >= 4) return { nazev: '', kod: bezMezer(s) };
      if (!/\s/.test(s) && /\d/.test(s) && s.length >= 4) return { nazev: '', kod: s };
      return null;
    }
    let ki = -1, nej = 3;
    bunky.forEach((s, i) => {
      const n = cislic(s);
      if (n > nej && (/^[\d\s-]+$/.test(s) || !/\s/.test(s))) { nej = n; ki = i; }
    });
    if (ki < 0) return null;
    return { nazev: (bunky.find((s, i) => i !== ki && /\p{L}/u.test(s)) || '').slice(0, 60), kod: bezMezer(bunky[ki]) };
  }

  // CSV i s uvozovkami (poznámka může mít víc řádků); oddělovač je ten, který je na nejvíc řádcích.
  function radkyCsv(t) {
    let odd = '', nej = 0;
    const neprazdne = t.split(/\r\n|\r|\n/).filter(r => r.trim());
    for (const d of [';', '\t', '|', ',']) {
      const n = neprazdne.filter(r => r.includes(d)).length;
      if (n > nej) { nej = n; odd = d; }
    }
    const radky = [];
    let radek = [], bunka = '', uvoz = false;
    for (let i = 0; i < t.length; i++) {
      const c = t[i];
      if (uvoz) {
        if (c !== '"') bunka += c;
        else if (t[i + 1] === '"') { bunka += '"'; i++; } else uvoz = false;
      } else if (c === '"' && !bunka.trim()) { uvoz = true; bunka = ''; }
      else if (odd && c === odd) { radek.push(bunka.trim()); bunka = ''; }
      else if (c === '\n' || c === '\r') {
        if (c === '\r' && t[i + 1] === '\n') i++;
        radek.push(bunka.trim()); radky.push(radek); radek = []; bunka = '';
      } else bunka += c;
    }
    if (bunka || radek.length) { radek.push(bunka.trim()); radky.push(radek); }
    return radky;
  }

  function zTextu(t) {
    const sZahlavim = [], bez = [];
    let h = null, nekdeZahlavi = false;
    for (const r of radkyCsv(t)) {
      if (!r.some(Boolean)) { h = null; continue; } // prázdný řádek: další blok může mít vlastní záhlaví (Catima)
      if (!h) {
        const z = sloupce(r);
        if (('kod' in z || 'kodCary' in z) && !r.some(b => /\d{6}/.test(b))) { h = z; nekdeZahlavi = true; continue; }
      }
      if (h) { if (!h.skupiny) { const p = polozkaZRadku(r, h); if (p) sZahlavim.push(p); } }
      else { const p = polozkaBezZahlavi(r); if (p) bez.push(p); }
    }
    return nekdeZahlavi ? sZahlavim : bez;
  }

  // JSON z jiné appky: kdekoli v něm objekty s číslem karty.
  function zJson(d) {
    const o = [];
    const projdi = (x, hloubka) => {
      if (!x || typeof x !== 'object' || hloubka > 8) return;
      if (Array.isArray(x)) { for (const y of x) projdi(y, hloubka + 1); return; }
      const klice = Object.keys(x);
      const h = sloupce(klice);
      if ('kod' in h || 'kodCary' in h) {
        const p = polozkaZRadku(klice.map(k => typeof x[k] === 'string' || typeof x[k] === 'number' ? String(x[k]) : ''), h);
        if (p) { o.push(p); return; }
      }
      for (const k of klice) projdi(x[k], hloubka + 1);
    };
    projdi(d, 0);
    return o;
  }

  function barvyImportu(nazev, p, i) {
    const r = RETEZEC[retezecProNazev(nazev)];
    if (r) return [r.barva, r.text];
    const z = (ZNACKY.barvy || {})[klicNazvu(nazev)];
    if (z && BARVA.test(z[0]) && BARVA.test(z[1])) return z;
    const b = BARVA.test(p.barva) ? p.barva : PALETA[i % PALETA.length];
    return [b, textNa(b)];
  }

  // Přehled nalezených karet: názvy jde opravit (podle nich appka pozná obchod), co nechce, odebere křížkem.
  function prehledImportu(polozky, o) {
    o = o || {};
    const klic = s => String(s).replace(/[\s-]/g, '');
    const mam = new Set(data.karty.map(k => klic(k.kod)));
    const videne = new Set();
    const nove = [];
    let uz = 0;
    for (const p of polozky) {
      const c = klic(p.kod);
      if (!c) nove.push(p); // fotka bez čitelného kódu: číslo se opíše v přehledu
      else if (mam.has(c)) uz++;
      else if (!videne.has(c)) { videne.add(c); nove.push(p); }
    }
    if (!nove.length) return o.hlas('Všechny tyhle karty už v appce máš.');
    const v = vrstva('Nové karty');
    const sKodem = nove.filter(p => p.kod).length, opsat = nove.length - sKodem;
    const info = [sKodem ? 'Našel jsem ' + sKodem + ' ' + tvar(sKodem, 'kartu', 'karty', 'karet') + '.' : ''];
    if (opsat) info.push(opsat === 1 ? 'Na jedné fotce jsem kód nepřečetl, opiš z ní číslo.' : 'Na ' + opsat + ' fotkách jsem kód nepřečetl, opiš z nich čísla.');
    if (o.bez) info.push(o.bez === 1 ? 'Jednu fotku se nepodařilo otevřít.' : o.bez + ' ' + tvar(o.bez, 'fotku', 'fotky', 'fotek') + ' se nepodařilo otevřít.');
    if (uz) info.push(uz === 1 ? 'Jednu už v appce máš, tu vynechám.' : uz + ' ' + tvar(uz, 'kartu', 'karty', 'karet') + ' už v appce máš, ty vynechám.');
    info.push('Zkontroluj názvy obchodů. Podle nich appka pozná, kde kartu ukázat.');
    const tlacitko = el('button', { type: 'button', class: 'tlacitko hlavni siroke', onclick: uloz });
    const seznam = el('div', { class: 'import-seznam' });
    const radky = nove.map((p, i) => {
      const r = { p, i, pryc: false };
      r.vstup = el('input', { maxlength: '60', autocomplete: 'off', spellcheck: 'false', enterkeyhint: 'done',
        placeholder: 'Název obchodu', 'aria-label': 'Název obchodu' });
      r.vstup.value = p.nazev || '';
      const znacka = el('span', { class: 'import-znacka' });
      const obnovZnacku = () => {
        const nazev = r.vstup.value.trim();
        const ret = retezecProNazev(nazev);
        const [barva, text] = barvyImportu(nazev, p, i);
        znacka.replaceChildren(logo({ retezec: ret, nazev }, 'male') ||
          el('span', { class: 'mini male', style: barvy({ barva, text }), text: (nazev || '?').charAt(0).toUpperCase() }));
      };
      // Vlastní návrhy místo <datalist>: ten se ve WebView na některých telefonech vykreslí průhledně přes řádky.
      const navrhy = el('div', { class: 'import-navrhy', hidden: true });
      const ukazNavrhy = () => {
        const nalez = navrhyObchodu(bezDiakritiky(r.vstup.value.trim()));
        navrhy.replaceChildren(...nalez.map(x => el('button', { type: 'button', class: 'import-navrh',
          onmousedown: e => e.preventDefault(),
          onclick: () => { r.vstup.value = x.nazev; navrhy.hidden = true; obnovZnacku(); r.vstup.blur(); } },
          logo({ retezec: x.id, nazev: x.nazev }, 'male') || el('span', { class: 'mini male', style: barvy(x), text: x.nazev.charAt(0) }),
          el('span', { text: x.nazev }))));
        navrhy.hidden = !nalez.length;
      };
      r.vstup.addEventListener('input', () => { obnovZnacku(); ukazNavrhy(); });
      r.vstup.addEventListener('focus', ukazNavrhy);
      // Se zpožděním, ať klepnutí na návrh doběhne, i když telefon pole odfokusuje dřív.
      r.vstup.addEventListener('blur', () => setTimeout(() => { if (document.activeElement !== r.vstup) navrhy.hidden = true; }, 250));
      r.vstup.addEventListener('keydown', e => { if (e.key === 'Enter') r.vstup.blur(); });
      obnovZnacku();
      if (!p.kod) {
        r.cislo = el('input', { maxlength: '200', autocomplete: 'off', enterkeyhint: 'done',
          placeholder: 'Číslo karty z fotky', 'aria-label': 'Číslo karty' });
        r.cislo.addEventListener('input', obnovTlacitko);
        r.cislo.addEventListener('keydown', e => { if (e.key === 'Enter') r.cislo.blur(); });
      }
      const radek = el('div', { class: 'import-radek' },
        p.foto ? el('button', { type: 'button', class: 'import-foto', 'aria-label': 'Ukázat fotku', onclick: () => {
          const f = vrstva('Fotka');
          f.telo.append(el('img', { class: 'import-velka', src: p.foto, alt: 'Fotka karty' }));
        } }, el('img', { src: p.foto, alt: '' })) : null,
        el('div', { class: 'import-text' }, r.vstup, navrhy,
          el('div', { class: 'import-cislo' }, znacka, r.cislo || el('span', { text: hezke(p.kod) }))),
        el('button', { type: 'button', class: 'ikona import-pryc', 'aria-label': 'Nepřidávat', onclick: () => {
          r.pryc = true;
          radek.remove();
          obnovTlacitko();
        } }, ikona('krizek')));
      seznam.append(radek);
      return r;
    });
    // Opsaná čísla bez mezer a pomlček, jako v novém formuláři; prázdný řádek se nepřidá.
    const kodRadku = r => r.p.kod || (r.cislo ? r.cislo.value.trim().replace(/^([\d\s-]+)$/, m => m.replace(/[\s-]/g, '')) : '');
    function obnovTlacitko() {
      const n = radky.filter(r => !r.pryc && kodRadku(r)).length;
      tlacitko.disabled = !n;
      tlacitko.textContent = n ? 'Přidat ' + n + ' ' + tvar(n, 'kartu', 'karty', 'karet') : 'Žádná karta k přidání';
    }
    obnovTlacitko();
    v.telo.append(el('p', { class: 'info', text: info.filter(Boolean).join(' ') }), seznam, tlacitko);

    function uloz() {
      for (const r of radky) {
        if (r.pryc || !kodRadku(r)) continue;
        const kod = kodRadku(r).slice(0, 2000);
        const nazev = r.vstup.value.trim().slice(0, 60) || 'Karta ' + kod.slice(-4);
        let format = FORMATY.includes(r.p.format) ? r.p.format : autoFormat(kod);
        if (KRESLI && !N.obrazek(format, kod, 400, 100)) format = N.obrazek('CODE_128', kod, 400, 100) ? 'CODE_128' : 'QR_CODE';
        const [barva, text] = barvyImportu(nazev, r.p, r.i);
        data.karty.push({ id: noveId(), retezec: retezecProNazev(nazev), nazev, kod, format, barva, text,
          poznamka: String(r.p.poznamka || '').slice(0, 500), ucet: '', heslo: '', oblibena: false, pouzito: 0, naposledy: 0, mista: [], wifi: [] });
      }
      ulozData();
      zavri(true);
      zjisti(true);
    }
  }

  // Víc fotek naráz (snímky obrazovky z jiné appky). Java pošle ke každé fotce [formát, kód, náhled], nebo [].
  let fotkyZprava = null;
  function zFotekNaraz(zprava) {
    fotkyZprava = zprava;
    zprava.textContent = '';
    N.fotky();
  }
  window.naFotkyPrubeh = function (i, n) {
    if (fotkyZprava && n > 1) fotkyZprava.textContent = 'Hledám kódy ve fotkách: ' + i + ' z ' + n + '…';
  };
  window.naFotky = function (t) {
    const zprava = fotkyZprava;
    fotkyZprava = null;
    let vse = null;
    try { vse = JSON.parse(t || 'null'); } catch (e) { vse = null; }
    if (!zprava || !Array.isArray(vse) || !vse.length) return; // výběr zrušený
    // [formát, kód, náhled]; fotka bez čitelného kódu má jen náhled a číslo se z ní v přehledu opíše.
    const polozky = vse.filter(x => Array.isArray(x) && (x[0] && x[1] || x[2]))
      .map(x => x[0] && x[1] ? { nazev: '', kod: x[1], format: x[0], foto: x[2] || '' } : { nazev: '', kod: '', format: '', foto: x[2] });
    zprava.textContent = polozky.length ? '' : vse.length === 1 ? 'Fotku se nepodařilo otevřít.' : 'Fotky se nepodařilo otevřít.';
    if (polozky.length) prehledImportu(polozky, { hlas: t => { zprava.textContent = t; }, bez: vse.length - polozky.length });
  };

  function prenos() {
    const v = vrstva('Karty z jiné appky');
    const zprava = el('p', { class: 'zprava', 'aria-live': 'polite' });
    v.telo.append(
      el('p', { class: 'info', text: 'Máš karty v jiné appce? Přenes je najednou, nemusíš je skenovat po jedné.' }),
      el('div', { class: 'volby' },
        el('button', { type: 'button', class: 'tlacitko hlavni velke', onclick: () => zFotekNaraz(zprava) }, ikona('obrazek'), 'Vybrat fotky karet'),
        el('button', { type: 'button', class: 'tlacitko velke', onclick: () => nactiSoubor(zprava) }, ikona('nacist'), 'Načíst ze souboru')),
      zprava,
      el('h3', { class: 'sekce', text: 'Jak na to' }),
      el('p', { class: 'info', text: 'Fotky: v té druhé appce otevři každou kartu a udělej snímek obrazovky. Pak je tady vyber všechny naráz a Kartička v nich najde kódy. Jde to i s fotkami plastových karet. Některé appky snímek karty nedovolí (Klarna ho začerní), takové karty přidej po jedné.' }),
      el('p', { class: 'info', text: 'Soubor: export z Catimy, záloha Kartičky, nebo tabulka či seznam, kde je na každém řádku obchod a číslo karty (třeba Lidl;1234 5678).' }));
  }

  // ---------- nastavení ----------

  const STAV_POLOHY = { ok: 'Povolená', priblizna: 'Jen přibližná', zakazano: 'Nepovolená', vypnuto: 'Vypnutá v telefonu' };
  const STAV_WIDGETU = { vzdy: 'Zapnuto', appka: 'Jen v appce' };
  const STAV_BATERIE = { volna: 'Bez omezení', setri: 'Šetří baterii' };

  // Řádek nastavení: barevná dlaždice s ikonou, název, pod ním krátký popis, vpravo hodnota a šipka.
  const BARVA_SEKCE = { karty: '#7166D8', pokladna: '#EFA00B', widget: '#3D7FE0', telefon: '#3E9F5A', zaloha: '#449D8B', appka: '#E75294' };
  function ikonka(n, barva) { return el('span', { class: 'ikonka', style: '--d:' + barva }, ikona(n)); }
  function polozka(o) {
    return el('button', { type: 'button', class: 'volba polozka', onclick: o.akce },
      ikonka(o.ik, o.barva),
      el('span', { class: 'polozka-text' },
        el('span', { class: 'volba-nazev', text: o.nazev }),
        o.popis ? el('span', { class: 'polozka-popis', text: o.popis }) : null),
      o.hodnota || null,
      ikona('dal'));
  }

  // Přepínač s několika možnostmi a popisem vybrané pod ním. Popisy leží přes sebe (vidět je jen vybraný),
  // takže se po klepnutí nic pod přepínačem neposune.
  function prepinac(nazev, ik, barva, moznosti, hodnota, zmen) {
    const tl = [], po = [];
    const nastav = h => moznosti.forEach((m, i) => {
      tl[i].setAttribute('aria-checked', String(m[0] === h));
      po[i].classList.toggle('vybrany', m[0] === h);
    });
    for (const [h, text, popis] of moznosti) {
      const b = el('button', { type: 'button', role: 'radio', text, onclick: () => {
        if (b.getAttribute('aria-checked') === 'true') return;
        zmen(h);
        nastav(h);
      } });
      tl.push(b);
      po.push(el('p', null, popis)); // popis je text, nebo text s prvky (políčko na vlastní sekundy)
    }
    nastav(hodnota);
    const radek = el('div', { class: 'volba nastav' },
      el('div', { class: 'nastav-hlava' }, ikonka(ik, barva), el('span', { class: 'volba-nazev', text: nazev })),
      el('div', { class: 'prepinac', role: 'radiogroup', 'aria-label': nazev }, tl),
      el('div', { class: 'popisy' }, po));
    return { radek, nastav };
  }

  function nastaveni() {
    if (chceOdemknout()) return;
    const v = vrstva('Nastavení', { trida: 'nastaveni' });
    const n = data.nastaveni;
    const B = BARVA_SEKCE;
    const zKarty = el('p', { class: 'zprava', 'aria-live': 'polite' });
    const zWidget = el('p', { class: 'zprava', 'aria-live': 'polite' });
    const zTelefon = el('p', { class: 'zprava', 'aria-live': 'polite' });
    const zZaloha = el('p', { class: 'zprava', 'aria-live': 'polite' });
    let novaVerze = null; // novější verze na webu, odkaz u čísla verze zůstane i po zmizení pruhu
    try { novaVerze = JSON.parse(N.novaVerze() || 'null'); } catch (e) { novaVerze = null; }
    const hPoloha = el('span', { class: 'hodnota' });
    const hWidget = el('span', { class: 'hodnota' });
    const hBaterie = el('span', { class: 'hodnota' });
    const hPocet = el('span', { class: 'hodnota', text: data.karty.length + ' ' + tvar(data.karty.length, 'karta', 'karty', 'karet') });
    const hHistorie = el('span', { class: 'hodnota', text: data.historie.length + '×' });
    const hHesloZalohy = el('span', { class: 'hodnota' });
    const obnovHesloZalohy = () => { hHesloZalohy.textContent = N.maHesloZalohy() ? 'Zamčená heslem zálohy' : 'Ne'; };
    obnovHesloZalohy();
    // stav v telefonu: zeleně, když je hotovo; růžově, když je potřeba něco povolit
    const stav = (h, text, ok) => { h.textContent = text || ''; h.classList.toggle('ok', ok); h.classList.toggle('chce', !ok); };
    let radaVzdy = false; // v zWidget je rada k Povolit vždy (zmizí, až je splněná); rada k přidání widgetu zůstane
    const obnovStavy = () => {
      const p = N.stavPolohy(), w = N.stavWidgetu(), b = N.stavBaterie();
      stav(hPoloha, STAV_POLOHY[p], p === 'ok');
      stav(hWidget, STAV_WIDGETU[w], w === 'vzdy');
      stav(hBaterie, STAV_BATERIE[b], b === 'volna');
      if (radaVzdy && w === 'vzdy') { zWidget.textContent = ''; radaVzdy = false; }
      if (b === 'volna') zTelefon.textContent = '';
    };
    obnovStavy();
    v.zZaloha = zZaloha;
    const zmen = (klic, h) => { n[klic] = h; ulozData(); };

    const poradi = prepinac('Pořadí karet', 'poradi', B.karty, [
      ['misto', 'Místo', 'Nahoře karta obchodu, kde právě jsi, pod ní ostatní podle toho, jak blízko je jejich nejbližší prodejna. Karty bez prodejen jsou pod nimi, obchody v cizině dole.'],
      ['chytre', 'Chytře', 'Nahoře karty obchodů, kde právě jsi. Pod nimi oblíbené, pak ty, které ukazuješ nejčastěji a máš poblíž. Obchody v cizině jsou dole.'],
      ['pouziti', 'Použití', 'Oblíbené nahoře, pak podle toho, kolikrát se karta ukázala. Poloha pořadí nemění.'],
      ['abeceda', 'A–Z', 'Oblíbené nahoře, ostatní podle názvu od A do Z.'],
      ['vlastni', 'Vlastní', 'Přesně tak, jak si karty poskládáš. Nové karty se přidají na konec.']
    ], n.poradi, h => {
      if (h === 'vlastni' && !n.vlastni.length) n.vlastni = serazene().map(k => k.id); // začne z toho, co je teď vidět
      zmen('poradi', h);
    });
    // Vlastní doba jasu se píše rovnou do popisu volby; ukládá se každé platné číslo, neplatné se po odchodu z políčka vrátí.
    const fJas = el('input', { class: 'jas-sekundy', type: 'text', inputmode: 'numeric', maxlength: '3',
      autocomplete: 'off', 'aria-label': 'Sekund naplno, 1 až ' + JAS_MAX });
    fJas.value = String(n.jasVlastni);
    fJas.addEventListener('input', () => {
      const cisla = fJas.value.replace(/\D/g, '');
      if (cisla !== fJas.value) fJas.value = cisla;
      const s = Number(cisla);
      if (vlastniJas(s)) { n.jasVlastni = s; zmen('jas', s); }
    });
    fJas.addEventListener('change', () => {
      if (Number(fJas.value) > JAS_MAX) { n.jasVlastni = JAS_MAX; zmen('jas', JAS_MAX); }
      fJas.value = String(n.jasVlastni);
    });
    fJas.addEventListener('keydown', e => { if (e.key === 'Enter') fJas.blur(); });
    const jas = prepinac('Jas naplno', 'slunce', B.pokladna, [
      ...JASY.map(s => s
        ? [s, s + ' s', 'Po klepnutí na kód svítí obrazovka naplno ' + s + ' sekund a odpočítává je, pak se vrátí jas telefonu. Stejně dlouho svítí sluníčko na widgetu. Další klepnutí zhasne dřív, dvojklik nechá svítit, dokud znovu neklepneš, na widgetu nejvýš 10 minut.']
        : [0, 'Hned', 'Obrazovka svítí naplno hned po otevření karty, dokud ji nezavřeš. Klepnutí na kód vrátí jas telefonu. Sluníčko na widgetu svítí 30 sekund.']),
      ['vlastni', 'Vlastní', ['Po klepnutí na kód svítí obrazovka naplno ', fJas, ' s (1 až ' + JAS_MAX + ') a odpočítává je. Stejně dlouho svítí sluníčko na widgetu. Další klepnutí zhasne dřív, dvojklik nechá svítit, dokud znovu neklepneš, na widgetu nejvýš 10 minut.']]
    ], JASY.includes(n.jas) ? n.jas : 'vlastni', h => {
      if (h !== 'vlastni') { zmen('jas', h); return; }
      zmen('jas', n.jasVlastni);
      fJas.value = String(n.jasVlastni);
      setTimeout(() => { fJas.focus(); fJas.select(); }); // popis s políčkem je vidět až po přepnutí
    });
    // Hlavní obrazovka mimo prodejnu (Adam 5.11.2026): kolik nejbližších obchodů a do jaké vzdálenosti, výchozí jeden a vše
    const blizko = prepinac('Nejbližší obchody nahoře', 'spendlik', B.karty, POCTY_BLIZKO.map(c => [c, String(c), c === 1
      ? 'Když nejsi u prodejny, je nahoře jen nejbližší obchod s kartou. Klepnutím se otevře mapa okolí.'
      : 'Když nejsi u prodejny, ' + tvar(c, '', 'jsou nahoře ' + c + ' nejbližší obchody', 'je nahoře ' + c + ' nejbližších obchodů')
        + ' s kartou, od nejbližšího. Klepnutím na obchod se otevře jeho karta, ikonou vedle mapa okolí.']),
    n.blizko, h => { zmen('blizko', h); vykresliTady(); });
    const okruh = prepinac('Do vzdálenosti', 'mapa', B.karty, OKRUHY.map(r => r
      ? [r, metru(r), 'Nahoře jsou jen obchody s kartou do ' + metru(r) + ' od tebe. Když žádný tak blízko není, zůstane tam odkaz na mapu okolí.']
      : [0, 'Vše', 'Nahoře je nejbližší obchod s kartou, ať je jakkoli daleko.']),
    n.okruh, h => { zmen('okruh', h); vykresliTady(); });
    const widget = prepinac('Klepnutí na widget otevře', 'widget', B.widget, [
      ['hlavni', 'Seznam karet', 'Hlavní stránka appky se všemi kartami. Když jsi u prodejny, je karta obchodu hned nahoře.'],
      ['karta', 'Ukázanou kartu', 'Rovnou karta, kterou widget právě ukazuje, s velkým kódem.']
    ], n.widget, h => zmen('widget', h));
    v.nastavWidget = widget.nastav; // volba jde změnit i ve Vzhledu widgetu (nastavZvenku)
    // Sluníčko na widgetu (Jas.java): velký kód u horního okraje obrazovky a co je pod ním.
    const panel = prepinac('Velký kód nahoře', 'slunce', B.widget, [
      ['karta', 'Jen vybrané', 'Po klepnutí na sluníčko na widgetu se kód ukáže velký u horního okraje obrazovky jen u karet, které to mají zapnuté v úpravě karty (třeba Albert).'],
      ['vse', 'Všechny karty', 'Po klepnutí na sluníčko na widgetu se kód každé karty ukáže velký u horního okraje obrazovky, jako u Alberta.']
    ], n.panel, h => zmen('panel', h));
    const pozadi = prepinac('Pod velkým kódem', 'oko', B.widget, [
      ['plocha', 'Plocha', 'Pod kódem zůstane vidět plocha i widget s odpočtem.'],
      ['tlumena', 'Ztlumená', 'Plocha pod kódem ztmavne, ať kód vynikne. Widget s odpočtem je vidět dál.'],
      ['cerna', 'Černá', 'Kromě kódu je obrazovka černá. Černá na displeji telefonu nesvítí, takže naplno svítí jen kód. Klepnutí mimo kód vrátí jas.']
    ], n.pozadi, h => zmen('pozadi', h));
    // tyhle volby drží Java (služba widgetu, čidlo pohybu, odesílání), v záloze nejsou
    const nazivo = prepinac('Widget naživo', 'navigace', B.widget, [
      ['ano', 'Zapnuto', 'Když se hýbeš, widget sám přepne kartu podle obchodu, u kterého zrovna jsi, i se zavřenou appkou. Polohu bere úsporně bez GPS a v oznámeních je při tom tichá zpráva. Běží, když je widget na ploše a appka má polohu.'],
      ['ne', 'Vypnuto', 'Widget přepne kartu, když otevřeš appku, a s polohou Povolit vždy občas sám. Cestou pak může chvíli ukazovat obchod, od kterého už jsi odešel.']
    ], N.stavNazivo() ? 'ano' : 'ne', h => N.nazivo(h === 'ano'));
    // tempo drží Java (Sledovani.tempo), Každou sekundu platí i pro otevřenou appku (Hlavni.hlidejZive)
    const tempo = prepinac('Hlídat polohu', 'poloha', B.widget, [
      ['sekunda', '1 s', 'Každou sekundu podle času, i když stojíš, s GPS. Otevřená appka i widget naživo přepnou kartu hned ve dveřích obchodu. Jen při rozsvíceném displeji, se zhasnutým jako 15 s. Baterie ubývá znatelně víc.'],
      ['casto', '15 s', 'Widget naživo u obchodu co 15 vteřin nebo po 12 metrech, jinde co 45 vteřin. Karta se přepne dřív, baterie ubývá o něco víc.'],
      ['normalne', '30 s', 'Widget naživo u obchodu co půl minuty nebo po 25 metrech, jinde co minutu a půl. Bez widgetu naživo co 2 až 10 minut podle toho, jak blízko obchodu jsi.'],
      ['uspora', '1 min', 'Widget naživo u obchodu co minutu nebo po 50 metrech, jinde co 3 minuty. Šetří baterii, karta se přepne o chvíli později.']
    ], N.tempo(), h => N.nastavTempo(h));
    const zatres = prepinac('Haptický podnět', 'zatrest', B.appka, [
      ['ano', 'Zapnuto', 'Zatřes telefonem na hlavní obrazovce (tam a zpátky), krátce zavrní a otevře se podnět: jaká karta chybí nebo co nefunguje. Když je nahoře kód u pokladny, nic se neotevře.'],
      ['ne', 'Vypnuto', 'Podnět jde napsat jen tady v Nastavení.']
    ], N.stavZatreseni() ? 'ano' : 'ne', h => N.zatreseni(h === 'ano'));
    const idTelefonu = String(N.idZarizeni() || '').slice(0, 8);
    const sber = prepinac('Pomáhat s mapou obchodů', 'mapa', B.appka, [
      ['ano', 'Zapnuto', POPIS_SBERU + (idTelefonu ? ' Kód tohohle telefonu je ' + idTelefonu + '.' : '')],
      ['ne', 'Vypnuto', 'Nic se neposílá. Mapa obchodů v appce funguje dál.']
    ], N.stavSberu() ? 'ano' : 'ne', h => zapniSber(h === 'ano'));
    const vzhled = prepinac('Vzhled', 'slunce', B.appka, [
      ['slunce', 'Podle slunce', 'Přes den světlý, po západu slunce tmavý, ať večer nesvítí do očí. Východ a západ se počítá pro místo, kde jsi. Kód pro pokladnu zůstává vždy černý na bílé.'],
      ['svetly', 'Světlý', 'Světlý vzhled pořád.'],
      ['tmavy', 'Tmavý', 'Tmavý vzhled pořád. Kód pro pokladnu zůstává černý na bílé, jinak by ho čtečka nenačetla.']
    ], n.vzhled, h => { zmen('vzhled', h); nastavVzhled(); });
    const naZamku = prepinac('Karty na zamčené obrazovce', 'zamek', B.telefon, [
      ['ano', 'Zapnuto', 'Kartička se otevře i bez odemknutí telefonu (třeba dvojím stiskem bočního tlačítka nebo dlaždicí v horní liště). Zamčená ukazuje jen karty a kódy: úpravy, Nastavení, poznámky, účty, hesla a PINy jsou až po odemknutí.'],
      ['ne', 'Vypnuto', 'Kartička se ukáže až po odemknutí telefonu.']
    ], umi('stavNaZamku') && N.stavNaZamku() ? 'ano' : 'ne', h => N.naZamku(h === 'ano'));
    let az = {};
    try { az = umi('autoZaloha') ? JSON.parse(N.autoZaloha() || '{}') : {}; } catch (e) { az = {}; }
    const autoZaloha = prepinac('Samočinná záloha', 'znovu', B.zaloha, [
      ['ano', 'Zapnuto', 'Kopie karet se sama ukládá do telefonu do složky Stažené/Karticka: hned po přidání karty, jinak nejvýš dvakrát denně. Zůstane tam i po odinstalování appky a složku může dál odnášet třeba FolderSync. Hesla a PINy v ní jsou jen s heslem zálohy. '
        + (az.cas ? 'Naposledy ' + kdy(az.cas) + '.' : 'První se uloží, až z appky odejdeš.')],
      ['ne', 'Vypnuto', 'Záloha je jen ruční. Když o telefon přijdeš nebo appku odinstaluješ bez ní, karty jsou pryč.']
    ], az.zap ? 'ano' : 'ne', h => N.nastavAutoZalohu(h === 'ano'));
    v.priNavratu = () => {
      obnovStavy();
      nazivo.nastav(N.stavNazivo() ? 'ano' : 'ne'); // šlo vypnout i v tiché zprávě
      tempo.nastav(N.tempo());
      zatres.nastav(N.stavZatreseni() ? 'ano' : 'ne');
      sber.nastav(N.stavSberu() ? 'ano' : 'ne');
    };
    // po načtení zálohy (Nastavení zůstane otevřené) ukázat, co v nastavení a kartách teď opravdu je
    v.obnovVolby = () => {
      poradi.nastav(n.poradi);
      blizko.nastav(n.blizko);
      okruh.nastav(n.okruh);
      jas.nastav(JASY.includes(n.jas) ? n.jas : 'vlastni');
      fJas.value = String(n.jasVlastni);
      widget.nastav(n.widget);
      panel.nastav(n.panel);
      pozadi.nastav(n.pozadi);
      vzhled.nastav(n.vzhled);
      nastavVzhled();
      hPocet.textContent = data.karty.length + ' ' + tvar(data.karty.length, 'karta', 'karty', 'karet');
      hHistorie.textContent = data.historie.length + '×';
    };

    // Hesla k účtům jdou do zálohy jen zamčená heslem zálohy; bez něj se do zálohy vůbec neukládají.
    const hesloZalohy = () => {
      const ma = N.maHesloZalohy();
      const uloz = h => {
        if (h[0].length < 6) return 'Heslo zálohy musí mít aspoň 6 znaků.';
        if (h[0] !== h[1]) return 'Hesla se neshodují.';
        if (!N.nastavHesloZalohy(h[0])) return 'Heslo se nepodařilo uložit.';
        obnovHesloZalohy();
        zZaloha.textContent = 'Heslo zálohy je nastavené. Hesla k účtům se teď do zálohy ukládají zamčená.';
        return '';
      };
      dialogHesla({
        nadpis: ma ? 'Změnit heslo zálohy' : 'Heslo zálohy',
        popis: 'Hesla k účtům se do zálohy uloží jen zamčená tímhle heslem. Zapiš si ho, bez něj je ze zálohy nikdo nepřečte.',
        pole: ['Nové heslo zálohy', 'Heslo znovu'],
        tlacitka: [
          ma ? { text: 'Zrušit heslo', nebezpeci: true, akce: () => {
            N.nastavHesloZalohy('');
            obnovHesloZalohy();
            zZaloha.textContent = 'Heslo zálohy je zrušené. Hesla k účtům se do zálohy neukládají.';
            return '';
          } } : { text: 'Zrušit', zrusit: true },
          { text: ma ? 'Změnit' : 'Uložit', hlavni: true, akce: uloz }
        ]
      });
    };

    // Nahoře jen to, co se nastavuje jednou a hned (Adam 9.11.2026: zeštíhlit, appku mají i kamarádi). Jemné ladění
    // (jas, nejbližší obchody, tempo widgetu, historie) je sbalené v Další volby.
    v.telo.append(
      el('h3', { class: 'sekce', text: 'Karty' }),
      el('div', { class: 'seznam-voleb' },
        poradi.radek,
        polozka({ ik: 'uchop', barva: B.karty, nazev: 'Poskládat vlastní pořadí', popis: 'Přetáhni karty, jak je chceš mít',
          akce: () => poskladat(() => poradi.nastav('vlastni')) }),
        polozka({ ik: 'obrazek', barva: B.karty, nazev: 'Přenést z jiné appky', popis: 'Z fotek nebo ze souboru', akce: prenos })),
      el('h3', { class: 'sekce', text: 'Vzhled' }),
      el('div', { class: 'seznam-voleb' }, vzhled.radek),
      WEB ? null : el('h3', { class: 'sekce', text: 'Widget' }), // webová appka na iPhonu widget nemá
      WEB ? null : el('div', { class: 'seznam-voleb' },
        polozka({ ik: 'plus', barva: B.widget, nazev: 'Přidat widget na plochu', popis: 'Na ploše ho pak zvětšíš nebo zmenšíš',
          akce: () => {
            radaVzdy = false;
            zWidget.textContent = N.pridatWidget() ? '' : 'Podrž prst na ploše, dej Widgety a najdi Kartičku.';
          } }),
        window.Karticka && polozka({ ik: 'stetec', barva: B.widget, nazev: 'Vzhled widgetu', akce: () => N.vzhledWidgetu() }), // web widget nemá
        polozka({ ik: 'spendlik', barva: B.widget, nazev: 'Widget podle obchodu', popis: 'Ukáže kartu obchodu, kde jsi',
          hodnota: hWidget, akce: () => {
            radaVzdy = N.stavWidgetu() !== 'vzdy';
            zWidget.textContent = radaVzdy ? 'Dej u polohy Povolit vždy. Widget pak sám ukáže kartu obchodu, kde jsi.' : '';
            N.povolitVzdy();
          } }),
        window.Karticka && nazivo.radek), // web widget nemá
      WEB ? null : zWidget,
      el('h3', { class: 'sekce', text: 'Telefon' }),
      el('div', { class: 'seznam-voleb' },
        polozka({ ik: 'poloha', barva: B.telefon, nazev: 'Poloha', popis: 'Pozná, kde nakupuješ', hodnota: hPoloha,
          akce: () => N.povolitPolohu() }),
        !WEB && polozka({ ik: 'baterie', barva: B.telefon, nazev: 'Baterie', popis: 'Ať telefon widget neuspí', hodnota: hBaterie, akce: () => {
          zTelefon.textContent = N.stavBaterie() === 'volna' ? '' : 'Dej Povolit, ať telefon widget neuspí.';
          N.baterie();
        } }),
        umi('naZamku') && naZamku.radek),
      zTelefon,
      el('h3', { class: 'sekce', text: 'Záloha a hesla' }),
      el('div', { class: 'seznam-voleb' },
        az.jde && autoZaloha.radek,
        polozka({ ik: 'ulozit', barva: B.zaloha, nazev: 'Uložit zálohu do souboru', popis: 'Karty, historie i nastavení', hodnota: hPocet,
          akce: () => N.zaloha() }),
        polozka({ ik: 'nacist', barva: B.zaloha, nazev: 'Načíst karty ze zálohy', akce: () => nactiSoubor(null) }),
        !WEB && polozka({ ik: 'zamek', barva: B.zaloha, nazev: 'Hesla a PINy v záloze', popis: 'Do zálohy jdou jen zamčené heslem zálohy',
          hodnota: hHesloZalohy, akce: hesloZalohy }),
        window.Karticka && polozka({ ik: 'nacist', barva: B.zaloha, nazev: 'Hesla ze správce hesel', popis: 'Doplní přihlášení k obchodům, od kterých máš kartu',
          akce: importHesel })),
      zZaloha,
      el('h3', { class: 'sekce', text: 'Appka' }),
      el('div', { class: 'seznam-voleb' },
        window.Karticka && polozka({ ik: 'znovu', barva: B.appka, nazev: 'Aktualizace',
          popis: novaVerze ? 'Je nová verze, klepni a nainstaluj ji' : 'Zkontrolovat a co je nového',
          hodnota: novaVerze ? el('span', { class: 'hodnota chce', text: 'Nová' }) : null, akce: aktualizace }),
        polozka({ ik: 'sdilet', barva: B.appka, nazev: 'Poslat appku kamarádovi',
          akce: () => N.sdilet('Kartička, věrnostní karty v mobilu\nhttps://karticka.adamhorak.cz/') }),
        window.Karticka && polozka({ ik: 'zprava', barva: B.appka, nazev: 'Napsat podnět', popis: 'Chybí karta, nebo něco nefunguje',
          akce: () => napsatPodnet(false) })),
      el('details', { class: 'dalsi-volby' },
        el('summary', null,
          el('span', null, 'Další volby', el('small', { text: 'Jas u pokladny, nejbližší obchody, tempo widgetu, historie' })),
          ikona('dal')),
        el('h3', { class: 'sekce', text: 'Hlavní obrazovka' }),
        el('div', { class: 'seznam-voleb' }, blizko.radek, okruh.radek),
        WEB ? null : el('h3', { class: 'sekce', text: 'U pokladny' }), // jas obrazovky web řídit nesmí
        WEB ? null : el('div', { class: 'seznam-voleb' }, jas.radek),
        WEB ? null : el('h3', { class: 'sekce', text: 'Widget podrobně' }),
        WEB ? null : el('div', { class: 'seznam-voleb' },
          widget.radek,
          window.Karticka && panel.radek, // web widget nemá
          window.Karticka && pozadi.radek,
          window.Karticka && tempo.radek),
        el('h3', { class: 'sekce', text: 'Historie a pořadí' }),
        el('div', { class: 'seznam-voleb' },
          polozka({ ik: 'hodiny', barva: B.karty, nazev: 'Historie ukázání', hodnota: hHistorie, akce: () => historie() }),
          polozka({ ik: 'znovu', barva: B.karty, nazev: 'Vynulovat chytré pořadí', popis: 'Zapomene, které karty ukazuješ nejčastěji',
            akce: () => potvrd('Vynulovat chytré pořadí?', 'Vynulovat', () => {
              for (const k of data.karty) k.pouzito = 0;
              ulozData();
              zKarty.textContent = 'Chytré pořadí je vynulované a teď se řídí hlavně tím, kde jsi.';
            }, true, 'Chytré pořadí zapomene, které karty ukazuješ nejčastěji, a začne od nuly. Nejde to vzít zpět. Historie ukázání zůstane, takže počet v detailu karty se nezmění.') })),
        zKarty,
        window.Karticka && el('h3', { class: 'sekce', text: 'Podněty a mapa' }),
        window.Karticka && el('div', { class: 'seznam-voleb' }, zatres.radek, sber.radek)),
      el('p', { class: 'o-appce', text: WEB
        ? 'Karty zůstávají jen v tomhle telefonu, v úložišti appky, a jejich čísla nikdy neodchází. Internet appka používá jen na podklad mapy okolí, když ji otevřeš (server OpenStreetMap vidí jen, kterou oblast si prohlížíš), a na novou verzi appky.'
        : window.Karticka
        ? 'Karty zůstávají jen v tomhle telefonu a jejich čísla nikdy neodchází. Internet appka používá, aby poznala novou verzi a stáhla podklad mapy okolí, když ji otevřeš (server OpenStreetMap vidí jen, kterou oblast si prohlížíš, karty ani nic dalšího). Jen když to dovolíš, pošle místa obchodů nebo tvůj podnět.'
        : 'Karty zůstávají jen v tomhle telefonu. Internet appka používá, jen aby poznala novou verzi, a nic neposílá.' }),
      el('p', { class: 'o-appce' }, 'Verze ' + (N.verze() || '?'),
        novaVerze ? [' · ', odkaz('Nová verze', () => window.Karticka ? aktualizace() : N.stahnoutNovou())] : null,
        ' · Mapa obchodů © přispěvatelé OpenStreetMap'));
  }

  // Vlastní pořadí: karty se přetahují za úchyt vpravo (zbytek řádku normálně posouvá seznam).
  // Tažený řádek drží prst, sousedé uhýbají; u horního a dolního okraje se seznam sám posouvá. Ukládá se po puštění.
  function poskladat(poZmene) {
    const n = data.nastaveni;
    if (n.poradi !== 'vlastni' || !n.vlastni.length) n.vlastni = serazene().map(k => k.id);
    n.poradi = 'vlastni';
    ulozData();
    if (poZmene) poZmene();
    const v = vrstva('Vlastní pořadí', { trida: 'poskladat' });
    const telo = v.telo;
    const rada = el('div', { class: 'rada' });
    const radek = k => el('div', { class: 'volba tah', 'data-id': k.id },
      logo(k, 'male') || el('span', { class: 'mini male', style: barvy(k), text: k.nazev.charAt(0) }),
      el('span', { class: 'volba-nazev', text: k.nazev }),
      el('span', { class: 'uchop', 'aria-label': 'Přesunout ' + k.nazev }, ikona('uchop')));
    const vykresli = () => rada.replaceChildren(...serazene().map(radek));
    const uloz = () => { n.vlastni = [...rada.children].map(r => r.dataset.id); ulozData(); };
    vykresli();

    let tah = null;
    // soused přeskočí na druhou stranu taženého řádku a krátce dojede na nové místo
    const presun = (s, akce) => {
      const pred = s.getBoundingClientRect().top;
      akce();
      s.style.transition = 'none';
      s.style.transform = 'translateY(' + (pred - s.getBoundingClientRect().top) + 'px)';
      void s.offsetHeight;
      s.style.transition = 'transform .15s ease';
      s.style.transform = '';
    };
    const tahni = () => {
      const r = tah.r;
      const cil = Math.max(0, Math.min(tah.top0 + tah.y - tah.y0 + telo.scrollTop - tah.s0, rada.offsetHeight - r.offsetHeight));
      // soused uhne, jakmile horní (dolní) hrana taženého řádku přejede jeho polovinu
      let s;
      while ((s = r.previousElementSibling) && cil < s.offsetTop + s.offsetHeight / 2) presun(s, () => r.after(s));
      while ((s = r.nextElementSibling) && cil + r.offsetHeight > s.offsetTop + s.offsetHeight / 2) presun(s, () => r.before(s));
      r.style.transform = 'translateY(' + (cil - r.offsetTop) + 'px)';
    };
    const smycka = () => {
      if (!tah) return;
      const b = telo.getBoundingClientRect();
      const o = tah.y < b.top + 70 ? tah.y - b.top - 70 : tah.y > b.bottom - 70 ? tah.y - b.bottom + 70 : 0;
      if (o) {
        const pred = telo.scrollTop;
        telo.scrollTop += Math.max(-16, Math.min(16, o / 3));
        if (telo.scrollTop !== pred) tahni();
      }
      tah.raf = requestAnimationFrame(smycka);
    };
    rada.addEventListener('pointerdown', e => {
      const u = e.target.closest('.uchop');
      if (!u || tah) return;
      e.preventDefault();
      const r = u.closest('.tah');
      try { u.setPointerCapture(e.pointerId); } catch (x) { /* bez zachycení to jde taky */ }
      tah = { r, id: e.pointerId, y0: e.clientY, y: e.clientY, top0: r.offsetTop, s0: telo.scrollTop };
      r.style.transition = 'none';
      r.classList.add('tazena');
      tah.raf = requestAnimationFrame(smycka);
    });
    rada.addEventListener('pointermove', e => {
      if (!tah || e.pointerId !== tah.id) return;
      tah.y = e.clientY;
      tahni();
    });
    const pust = e => {
      if (!tah || e.pointerId !== tah.id) return;
      cancelAnimationFrame(tah.raf);
      const r = tah.r;
      tah = null;
      r.classList.remove('tazena');
      r.style.transition = 'transform .15s ease';
      r.style.transform = '';
      uloz();
    };
    rada.addEventListener('pointerup', pust);
    rada.addEventListener('pointercancel', pust);

    telo.append(
      el('p', { class: 'info', text: 'Chyť kartu za úchyt vpravo a přetáhni ji, kam chceš. Ukládá se hned.' }),
      rada,
      el('button', { type: 'button', class: 'tlacitko siroke', onclick: () => potvrd('Seřadit karty znovu podle místa, kde jsou nejbližší prodejny?',
        'Seřadit', () => { n.vlastni = podleMista(data.karty).map(k => k.id); ulozData(); vykresli(); }, true) }, ikona('znovu'), 'Seřadit znovu podle místa'));
  }

  // Kdy se která karta ukázala, po dnech od nejnovějšího. Bez karty všechny karty a nahoře souhrn za 30 dní.
  // Historie ukázání za zvolené období; záznamy dole po kouscích (Ukázat další, pak i Ukázat vše), ať se zbytečně
  // nevykresluje pět tisíc řádků. Období platí, dokud je appka otevřená.
  const OBDOBI = [[7, 'Týden', 'Za týden'], [30, '30 dní', 'Za 30 dní'], [90, '90 dní', 'Za 90 dní'],
    [365, 'Rok', 'Za rok'], [0, 'Vše', 'Celkem']];
  const ZAZNAMU_PRVNE = 30, ZAZNAMU_DALSI = 60;
  let obdobiHistorie = 30;

  function historie(karta) {
    const v = vrstva(karta ? karta.nazev : 'Historie ukázání', karta ? { karta } : null);
    const vse = data.historie.filter(z => !karta || z[1] === karta.id);
    const pata = el('p', { class: 'info', text: 'Kdy byla karta na displeji (appka nepozná, jestli ji pokladna opravdu načetla). Záznamy jsou jen v telefonu a v záloze.'
      + (N.stavSberu() ? ' Pro mapu obchodů z nich odchází místa obchodů, dny a počty ukázání, ne čísla karet.' : '') });
    if (!vse.length) { v.telo.append(el('p', { class: 'info', text: 'Zatím tu nic není.' }), pata); return; }
    const volby = OBDOBI.map(([dni, text]) => el('button', { type: 'button', role: 'radio', text,
      'aria-checked': String(dni === obdobiHistorie), onclick: () => {
        if (dni === obdobiHistorie) return;
        obdobiHistorie = dni;
        volby.forEach((b, i) => b.setAttribute('aria-checked', String(OBDOBI[i][0] === dni)));
        drzMisto(vyber, v.telo, vykresli);
      } }));
    const vyber = el('div', { class: 'prepinac obdobi', role: 'radiogroup', 'aria-label': 'Období' }, volby);
    v.telo.append(vyber, pata);
    let kusy = [];
    const vloz = (...n) => { for (const x of n) { v.telo.insertBefore(x, pata); kusy.push(x); } };

    function vykresli() {
      for (const x of kusy) x.remove();
      kusy = [];
      const [dni, , nadpis] = OBDOBI.find(o => o[0] === obdobiHistorie);
      const od = dni ? Date.now() - dni * 864e5 : 0;
      const zaznamy = vse.filter(z => z[0] >= od);
      if (!zaznamy.length) { vloz(el('p', { class: 'info', text: 'Za tohle období tu nic není.' })); return; }
      if (karta) vloz(el('p', { class: 'info', text: nadpis + ' ukázaná ' + zaznamy.length + '×.' }));
      else {
        const pocty = new Map();
        for (const z of zaznamy) pocty.set(z[1], (pocty.get(z[1]) || 0) + 1);
        const poradi = [...pocty].map(([id, n]) => ({ k: najdi(id), n })).filter(x => x.k).sort((a, b) => b.n - a.n);
        if (poradi.length) vloz(el('h3', { class: 'sekce', text: nadpis }),
          el('div', { class: 'seznam-voleb' }, poradi.map(x => el('button', { type: 'button', class: 'volba zaznam', onclick: () => historie(x.k) },
            logo(x.k, 'male') || el('span', { class: 'mini male', style: barvy(x.k), text: x.k.nazev.charAt(0) }),
            el('span', { class: 'volba-nazev', text: x.k.nazev }),
            el('span', { class: 'hodnota', text: x.n + '×' })))));
      }
      // nejnovější nahoře, po dnech; další kousek pokračuje ve dni, kde předchozí skončil
      let ukazano = 0, blok = null, denBloku = '';
      const dalsi = el('button', { type: 'button', class: 'tlacitko', text: 'Ukázat další', onclick: () => {
        ukaz(ZAZNAMU_DALSI);
        vsechny.hidden = !zbyva();
      } });
      const vsechny = el('button', { type: 'button', class: 'tlacitko', text: 'Ukázat vše', hidden: true, onclick: () => ukaz(Infinity) });
      const tlacitka = el('div', { class: 'dalsi-zaznamy' }, dalsi, vsechny);
      const zbyva = () => zaznamy.length - ukazano;
      function ukaz(kolik) {
        const konec = Math.min(zaznamy.length, ukazano + kolik);
        for (; ukazano < konec; ukazano++) {
          const z = zaznamy[zaznamy.length - 1 - ukazano];
          const d = den(z[0]);
          if (d !== denBloku) {
            denBloku = d;
            blok = el('div', { class: 'seznam-voleb' });
            for (const x of [el('h3', { class: 'sekce', text: d }), blok]) { v.telo.insertBefore(x, tlacitka); kusy.push(x); }
          }
          const k = najdi(z[1]);
          blok.append(el('div', { class: 'volba zaznam' },
            el('span', { class: 'zaznam-cas', text: hodiny(new Date(z[0])) }),
            el('span', { class: 'volba-nazev', text: karta ? ODKUD[z[2]] : (k ? k.nazev : 'Smazaná karta') }),
            karta ? null : el('span', { class: 'hodnota', text: ODKUD[z[2]] })));
        }
        tlacitka.hidden = !zbyva();
      }
      vloz(tlacitka);
      ukaz(ZAZNAMU_PRVNE);
    }
    vykresli();
  }

  // Klepnutí, které mění výšku obsahu, nesmí pohnout klepnutým prvkem: rozdíl se dorovná posunem, a když se obsah
  // zkrátil pod místo, kde stránka je, dostane spodek dočasně víc místa (do další změny).
  function drzMisto(prvek, posuvnik, zmena) {
    const pred = prvek.getBoundingClientRect().top;
    posuvnik.style.paddingBottom = '';
    zmena();
    const posun = prvek.getBoundingClientRect().top - pred;
    if (posun > 0) {
      const volno = posuvnik.scrollHeight - posuvnik.clientHeight - posuvnik.scrollTop;
      if (volno < posun) posuvnik.style.paddingBottom = 'calc(32px + env(safe-area-inset-bottom) + ' + Math.ceil(posun - volno) + 'px)';
    }
    if (posun) posuvnik.scrollTop += posun;
  }

  function zpravaZalohy(text) {
    const v = vrstvy.find(x => x.zZaloha);
    if (v) v.zZaloha.textContent = text;
  }
  // bez: kolik hesel k účtům v záloze chybí (karty s heslem, ale bez hesla zálohy)
  window.naZalohu = function (ok, bez) {
    if (!ok) return zpravaZalohy('Zálohu se nepodařilo uložit.');
    if (!(bez > 0)) return zpravaZalohy('Záloha je uložená.');
    zpravaZalohy(N.maHesloZalohy() ? 'Záloha je uložená, ale hesla a PINy se do ní nepodařilo přidat.'
      : 'Záloha je uložená, hesla a PINy v ní nejsou (nastav heslo zálohy).');
  };
  // Soubor vybraný v telefonu. Záloha Kartičky se načte rovnou, cokoli jiného (export Catimy, tabulka, seznam)
  // jde přes přehled nových karet. Zpráva se ukáže tam, odkud se soubor vybíral.
  let obnovaZprava = null;
  function nactiSoubor(zprava) {
    obnovaZprava = zprava;
    if (zprava) zprava.textContent = '';
    N.obnova();
  }
  function zpravaObnovy(text) {
    if (obnovaZprava && obnovaZprava.isConnected) obnovaZprava.textContent = text;
    else zpravaZalohy(text);
  }
  const jeZaloha = d => !!d && (Array.isArray(d.karty) ||
    Array.isArray(d) && d.some(x => x && typeof x.kod === 'string' && typeof x.id === 'string'));
  // Tatáž karta může mít v záloze jiné id (přidaná znovu, jiný telefon): pozná se podle obchodu a čísla.
  const stejnaKarta = (a, b) => a.kod === b.kod && a.appka === b.appka &&
    (b.retezec ? a.retezec === b.retezec : !a.retezec && a.nazev === b.nazev);
  let idZeZalohy = {}; // id v záloze -> id v telefonu, kde se liší (hesla a historie jsou v záloze podle id)
  function obnovZalohu(d) {
    let celkem = 0;
    idZeZalohy = {};
    const videne = new Set();
    for (const x of Array.isArray(d) ? d : d.karty) {
      const k = ocisti(x);
      if (!k || videne.has(k.id)) continue;
      videne.add(k.id);
      celkem++;
      let i = data.karty.findIndex(y => y.id === k.id);
      if (i < 0) {
        i = data.karty.findIndex(y => stejnaKarta(y, k));
        if (i >= 0) { idZeZalohy[k.id] = data.karty[i].id; k.id = data.karty[i].id; }
      }
      // heslo z cizího souboru se sem nikdy nedostane: přijde jen přes nactiHesla. Heslo v telefonu obnovou nezmizí.
      k.heslo = i >= 0 ? data.karty[i].heslo || '' : '';
      k.pin = i >= 0 ? data.karty[i].pin || '' : '';
      if (i >= 0 && !(x && 'ucet' in x)) k.ucet = data.karty[i].ucet || ''; // starší záloha účty neznala
      if (i >= 0) data.karty[i] = k; else data.karty.push(k);
    }
    if (!celkem) return 0;
    neukladat = false; // karty přišly ze zálohy, nečitelný soubor se smí přepsat
    barvyZnacek(data.karty, !(d.verze >= 2));
    // historie ze zálohy se přidá k té v telefonu (stejný záznam jen jednou)
    const byly = new Set(data.historie.map(z => z[0] + z[1]));
    for (const z of cistaHistorie(d.historie)) {
      z[1] = idZeZalohy[z[1]] || z[1];
      if (!byly.has(z[0] + z[1])) data.historie.push(z);
    }
    data.historie = cistaHistorie(data.historie);
    // stejný objekt: otevřené Nastavení si ho drží a dál do něj zapisuje
    if (d.nastaveni) Object.assign(data.nastaveni, cisteNastaveni(d.nastaveni));
    ulozData();
    for (const v of vrstvy) if (v.obnovVolby) v.obnovVolby();
    return celkem;
  }
  // Hesla ze zálohy jsou zamčená heslem zálohy. Zkusí se to uložené v telefonu, jinak se appka zeptá.
  // hotovo(pocet): kolik hesel se načetlo (0 = žádné, třeba Bez hesel).
  function nactiHesla(blok, hotovo) {
    const zkus = h => {
      let m = null;
      try { m = JSON.parse(N.odemkniZalohu(JSON.stringify(blok), h) || 'null'); } catch (e) { m = null; }
      if (!m || typeof m !== 'object') return -1;
      let pocet = 0;
      for (const klic in m) {
        const jePin = klic.endsWith('#pin'); // PIN karty je v záloze pod „id#pin"
        const id = jePin ? klic.slice(0, -4) : klic;
        const k = najdi(idZeZalohy[id] || id);
        if (k && typeof m[klic] === 'string' && HESLO_KS.test(m[klic])) { k[jePin ? 'pin' : 'heslo'] = m[klic]; pocet++; }
      }
      if (pocet) ulozData();
      return pocet;
    };
    const hned = zkus('');
    if (hned >= 0) return hotovo(hned);
    dialogHesla({
      nadpis: 'Heslo zálohy',
      popis: 'V záloze jsou hesla k účtům. Napiš heslo zálohy, kterým jsou zamčená.',
      pole: ['Heslo zálohy'],
      tlacitka: [
        { text: 'Bez hesel', zrusit: true },
        { text: 'Načíst', hlavni: true, akce: h => {
          if (!h[0]) return 'Napiš heslo zálohy.';
          const n = zkus(h[0]);
          if (n < 0) return 'Špatné heslo zálohy.';
          hotovo(n);
          return '';
        } }
      ],
      zrusit: () => hotovo(0)
    });
  }

  window.naObnovu = function (t) {
    if (cekaHesla) return cekaHesla(t); // soubor vybraný v Hesla ze správce hesel
    if (t == null) return zpravaObnovy('Soubor se nepodařilo přečíst.');
    t = String(t).replace(/^\uFEFF/, '');
    let d;
    try { d = JSON.parse(t); } catch (e) { d = undefined; }
    let polozky;
    if (d !== undefined) {
      if (jeZaloha(d)) {
        const n = obnovZalohu(d);
        const zaklad = n ? 'Načteno ze zálohy: ' + n + ' ' + tvar(n, 'karta', 'karty', 'karet') + '.' : 'V záloze nejsou žádné karty.';
        zpravaObnovy(zaklad);
        if (n && d.hesla && typeof d.hesla === 'object') nactiHesla(d.hesla, pocet =>
          zpravaObnovy(zaklad + (pocet ? ' Hesla a PINy: ' + pocet + '.' : ' Hesla a PINy se nenačetly.')));
        return;
      }
      polozky = zJson(d);
    } else if (/[\x00-\x08\x0e-\x1f]/.test(t.slice(0, 4000))) {
      return zpravaObnovy('Tohle není soubor s kartami.');
    } else polozky = zTextu(t);
    if (!polozky.length) return zpravaObnovy('V souboru jsem nenašel žádné karty. Umím zálohu Kartičky, export z Catimy ' +
      'a tabulku nebo seznam, kde je na každém řádku obchod a číslo karty.');
    zpravaObnovy('');
    prehledImportu(polozky, { hlas: zpravaObnovy });
  };

  // ---------- hesla ze správce hesel ----------
  // Export správce hesel (CSV se sloupci adresa, jméno, heslo; Google Správce hesel, Chrome, Bitwarden…) se přečte
  // jen v telefonu. Ke kartě se přiřadí řádek se stejnou doménou jako web programu nebo se stejnou appkou obchodu,
  // heslo se hned zamkne klíčem telefonu (N.zamkni) a nikam jinam se neukládá. Co se uloží, si člověk odškrtne.
  let cekaHesla = null;
  const jadroDomeny = h => { const c = String(h).toLowerCase().replace(/^www\./, '').split('.'); return c.length > 1 ? c[c.length - 2] : c[0]; };
  function hostAdresy(a) {
    try { return new URL(/^[a-z][a-z0-9+.-]*:/i.test(a) ? a : 'https://' + a).hostname; } catch (e) { return ''; }
  }
  function importHesel() {
    if (chceOdemknout()) return;
    const v = vrstva('Hesla ze správce hesel');
    const zprava = el('p', { class: 'zprava', 'aria-live': 'polite' });
    const seznam = el('div', { class: 'hesla-seznam' });
    const uloz = el('button', { type: 'button', class: 'tlacitko hlavni siroke', hidden: true });
    let nalezene = [];
    const zahod = () => { for (const x of nalezene) x.heslo = ''; nalezene = []; seznam.replaceChildren(); uloz.hidden = true; };
    cekaHesla = t => {
      zahod();
      if (t == null) { zprava.textContent = 'Soubor se nepodařilo přečíst.'; return; }
      const radky = radkyCsv(String(t).replace(/^\uFEFF/, ''));
      const hl = (radky[0] || []).map(x => bezDiakritiky(x));
      const sl = (...slova) => hl.findIndex(x => slova.some(s => x === s || x.includes(s)));
      const iUrl = sl('url', 'uri', 'web', 'adresa'), iJmeno = sl('username', 'user', 'login', 'jmeno', 'email'), iHeslo = sl('password', 'heslo');
      if (iHeslo < 0 || iUrl < 0) { zprava.textContent = 'V souboru chybí sloupce s adresou a heslem. Čekám export hesel ve formátu CSV.'; return; }
      // řádky exportu podle jádra domény (lidl z accounts.lidl.com) a podle balíčku appky (android://…@balik/)
      const podle = {}, appky = {};
      for (const r of radky.slice(1)) {
        const adresa = r[iUrl] || '', heslo = r[iHeslo] || '';
        if (!heslo) continue;
        const z = { jmeno: iJmeno >= 0 ? r[iJmeno] || '' : '', heslo };
        const m = /^android:\/\/[^@]*@([^/]+)/.exec(adresa);
        if (m) { if (!appky[m[1]]) appky[m[1]] = z; continue; }
        const j = jadroDomeny(hostAdresy(adresa));
        if (j && !podle[j]) podle[j] = z;
      }
      for (const k of data.karty) {
        const p = program(k) || {};
        const klice = [p.web, k.odkaz].filter(Boolean).map(a => jadroDomeny(hostAdresy(a))).filter(Boolean);
        klice.push(klicNazvu(k.nazev));
        const z = appky[k.appka || p.app] || klice.map(j => podle[j]).find(Boolean);
        if (z) nalezene.push({ k, jmeno: z.jmeno, heslo: z.heslo, vybrat: !k.heslo });
      }
      if (!nalezene.length) { zprava.textContent = 'V souboru jsem nenašel přihlášení k žádnému obchodu, od kterého máš kartu.'; return; }
      zprava.textContent = '';
      seznam.replaceChildren(...nalezene.map(x => {
        const f = el('input', { type: 'checkbox' });
        f.checked = x.vybrat;
        f.addEventListener('change', () => { x.vybrat = f.checked; pocitej(); });
        return el('label', { class: 'hesla-radek' }, f,
          el('span', null, x.k.nazev, el('small', { text: (x.jmeno || 'bez jména') + (x.k.heslo ? ' · přepíše heslo uložené v kartě' : '') })));
      }));
      pocitej();
    };
    const pocitej = () => {
      const n = nalezene.filter(x => x.vybrat).length;
      uloz.hidden = !nalezene.length;
      uloz.disabled = !n;
      uloz.textContent = n ? 'Uložit ' + n + ' ' + tvar(n, 'heslo', 'hesla', 'hesel') + ' ke kartám' : 'Nic není vybrané';
    };
    uloz.addEventListener('click', () => {
      let n = 0;
      for (const x of nalezene) {
        if (!x.vybrat) continue;
        const ks = N.zamkni(x.heslo);
        if (!HESLO_KS.test(ks)) continue;
        x.k.heslo = ks;
        if (x.jmeno && !x.k.ucet) x.k.ucet = x.jmeno.slice(0, 200);
        n++;
      }
      zahod();
      if (n) ulozData();
      zprava.textContent = n ? 'Uloženo: ' + n + ' ' + tvar(n, 'heslo', 'hesla', 'hesel') + '. Soubor s exportem teď z telefonu smaž, hesla v něm jsou čitelná.'
        : 'Hesla se nepodařilo uložit.';
    });
    v.priZavreni = () => { zahod(); cekaHesla = null; };
    v.telo.append(
      el('p', { class: 'info', text: 'Kartička si z exportu správce hesel vezme jen přihlášení k obchodům, od kterých máš kartu. Uloží je zamčená klíčem telefonu, stejně jako heslo napsané ručně, a ukáže je až po otisku.' }),
      el('div', { class: 'volby' },
        el('button', { type: 'button', class: 'tlacitko hlavni velke', onclick: () => { zprava.textContent = ''; N.obnova(); } }, ikona('nacist'), 'Vybrat soubor s hesly')),
      zprava, seznam, uloz,
      el('h3', { class: 'sekce', text: 'Jak na to' }),
      el('p', { class: 'info', text: 'V Google Správci hesel (nebo v Chromu) dej Nastavení a Exportovat hesla, vznikne soubor CSV. Ten tady vyber. Po načtení ho z telefonu smaž.' }),
      el('p', { class: 'info', text: 'Přihlášení na webu obchodu pak doplní správce hesel v prohlížeči jako vždy; Kartička ti k tomu zkopíruje jméno a heslo ukáže po otisku.' }));
  }

  // ---------- náhradní most pro prohlížeč (jen na ladění rozhraní) ----------

  function prohlizec() {
    const nacti = j => {
      try { const x = new XMLHttpRequest(); x.open('GET', j, false); x.send(); return x.status === 200 ? x.responseText : ''; } catch (e) { return ''; }
    };
    const m = /[?&]poloha=([-\d.]+),([-\d.]+)/.exec(location.search);
    return {
      karty: () => { try { return localStorage.getItem('karticka') || ''; } catch (e) { return ''; } },
      kartyPredchozi: () => '',
      uloz: t => { try { localStorage.setItem('karticka', t); } catch (e) { /* bez úložiště */ } return true; },
      soubor: nacti,
      obrazek: () => '',
      jas() {},
      jasy: () => '',
      posledniPoloha: () => '',
      wifi: () => { try { return localStorage.getItem('karticka-wifi') || ''; } catch (e) { return ''; } },
      poloha() { setTimeout(() => window.naPolohu(m ? { lat: +m[1], lon: +m[2], presnost: 20, vek: 0 } : null, 'nic'), 300); },
      stavPolohy: () => 'ok',
      stavWidgetu: () => 'appka',
      povolitPolohu() {},
      povolitVzdy() {},
      stavBaterie: () => 'setri',
      baterie() {},
      nastaveni() {},
      skenovat() { setTimeout(() => window.naKod(null, null, 'kamera'), 100); },
      zFotky() { setTimeout(() => window.naKod(null, null, 'nenalezeno'), 100); },
      // jedna fotka bez čitelného kódu (šedý náhled), jedna, kterou nejde otevřít
      fotky() { setTimeout(() => window.naFotky(JSON.stringify([['', '', 'data:image/svg+xml,' + encodeURIComponent(
        '<svg xmlns="http://www.w3.org/2000/svg" width="46" height="68"><rect width="46" height="68" fill="#C9CDD3"/></svg>')], []])), 100); },
      posledni() {},
      start: () => '',
      pridatWidget: () => false,
      vzhledWidgetu() {},
      zaloha() { setTimeout(() => window.naZalohu(false, 0), 100); },
      // heslo jen naoko (base64), ať jde formulář a detail ladit; v telefonu je to AndroidKeyStore
      zamkni: t => 'ks1:' + btoa(unescape(encodeURIComponent(t))).padEnd(16, '='),
      ukazHeslo(ks) {
        let t = null;
        try { t = decodeURIComponent(escape(atob(ks.slice(4).replace(/=+$/, '')))); } catch (e) { t = null; }
        setTimeout(() => window.naHeslo(ks, t, false), 100);
      },
      skryjHeslo() {},
      kopirujHeslo: () => true,
      kopiruj: () => true,
      maHesloZalohy: () => { try { return !!localStorage.getItem('karticka-hz'); } catch (e) { return false; } },
      nastavHesloZalohy(h) {
        try { if (h) localStorage.setItem('karticka-hz', '1'); else localStorage.removeItem('karticka-hz'); } catch (e) { /* bez úložiště */ }
        return true;
      },
      odemkniZalohu: () => '',
      obnova() {},
      sdilet() {},
      otevriProgram(app, web) {
        if (!web) return '';
        window.open(web, '_blank', 'noopener');
        return 'web';
      },
      verze: () => 'prohlížeč',
      novaVerze: () => '',
      verzeOhlasena() {},
      stahnoutNovou() {},
      stavVerze: () => '',
      zkontrolovatVerzi() { setTimeout(() => window.naKontrolu(false), 300); },
      stavInstalace: () => -1,
      smiInstalovat: () => false,
      aktualizovat() { window.open('https://karticka.adamhorak.cz/', '_blank', 'noopener'); return 'web'; },
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
      podnet() { setTimeout(() => window.naPodnet('fronta'), 300); return true; }
    };
  }

  // ---------- start ----------

  $('#btnPridat').append(ikona('plus'));
  $('#btnNastaveni').append(ikona('nastaveni'));
  $('#btnPridat').addEventListener('click', () => pridat());
  $('#btnNastaveni').addEventListener('click', nastaveni);
  document.addEventListener('visibilitychange', () => { if (document.hidden) { clearTimeout(pol.casovac); skryjOdkryte(false); schovejPruhVerze(); } });

  $('#hlavni').prepend(pruhZamku);
  zamek();
  nastavVzhled();
  prevezmiJasy();
  vykresliSeznam();
  vykresliTady(true);
  const startKarta = N.start();
  if (startKarta) window.naOtevreni(startKarta);
  const startAkce = umi('startAkce') ? N.startAkce() : '';
  if (startAkce) setTimeout(() => window.naAkci(startAkce));
  if (nacteno.obnoveno) oznam('Soubor s kartami byl poškozený, vrátila se předchozí uložená verze.');
  if (nacteno.necitelne) potvrd('Karty nejdou přečíst', 'Začít znovu', () => { neukladat = false; ulozData(); }, true,
    'Soubor s kartami je poškozený a nic se do něj neukládá. Můžeš načíst zálohu v Nastavení, nebo začít s prázdným seznamem.');
  if (!V_TELEFONU) window.naNavrat();
})();
