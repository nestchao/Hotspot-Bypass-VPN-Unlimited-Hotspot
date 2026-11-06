// Client behaviour shared by every page. Each block checks that its elements exist.
const root = document.documentElement;
const ds = root.dataset;
const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
const $ = <T extends Element = HTMLElement>(sel: string, el: ParentNode = document) => el.querySelector<T>(sel);
const $$ = <T extends Element = HTMLElement>(sel: string, el: ParentNode = document) => [...el.querySelectorAll<T>(sel)];

/* ---------- theme ---------- */
const mq = matchMedia('(prefers-color-scheme: dark)');
const themeBtn = $('#theme-btn');
const isDark = () => { const t = root.getAttribute('data-theme'); return t ? t === 'dark' : mq.matches; };
function paintTheme() {
  const dark = isDark();
  root.classList.toggle('is-dark', dark);
  themeBtn?.setAttribute('aria-label', dark ? ds.toLight! : ds.toDark!);
}
themeBtn?.addEventListener('click', () => {
  const next = isDark() ? 'light' : 'dark';
  root.setAttribute('data-theme', next);
  try { localStorage.setItem('hb-theme', next); } catch { /* storage blocked */ }
  paintTheme();
});
mq.addEventListener?.('change', paintTheme);
paintTheme();

/* ---------- mobile menu ---------- */
const menuBtn = $('#menu-btn'), mobileNav = $('#mobile-nav');
menuBtn?.addEventListener('click', () => {
  const open = mobileNav!.hidden;
  mobileNav!.hidden = !open;
  menuBtn.setAttribute('aria-expanded', String(open));
  menuBtn.querySelector('use')!.setAttribute('href', open ? '#i-close' : '#i-menu');
});
mobileNav?.addEventListener('click', (e) => { if ((e.target as Element).closest('a')) menuBtn?.click(); });

/* keep the guide tab when switching language */
$$('[data-lang-switch]').forEach((a) => a.addEventListener('click', () => {
  if (location.hash) (a as HTMLAnchorElement).href = (a as HTMLAnchorElement).href.split('#')[0] + location.hash;
}));

/* ---------- OS-aware download button ---------- */
const heroBtn = $<HTMLAnchorElement>('#hero-dl');
if (heroBtn) {
  const ua = navigator.userAgent;
  const txt = $('#hero-dl-text')!, note = $('#os-note')!;
  const otherLink = `<a href="${ds.downloadPage}">${ds.other}</a>`;
  if (/Android/i.test(ua)) { heroBtn.href = ds.apk!; txt.textContent = ds.dlAndroid!; note.innerHTML = otherLink; }
  else if (/Windows/i.test(ua)) { heroBtn.href = ds.installer!; txt.textContent = ds.dlWindows!; note.innerHTML = otherLink; }
  else { heroBtn.href = ds.downloadPage!; txt.textContent = ds.dlGeneric!; note.textContent = ds.noteOther!; }
}

/* ---------- copy buttons ---------- */
const toast = $('#toast');
let toastTimer: number | undefined;
function showToast(msg: string) {
  if (!toast) return;
  toast.textContent = msg; toast.classList.add('show');
  clearTimeout(toastTimer); toastTimer = window.setTimeout(() => toast.classList.remove('show'), 1600);
}
function selectText(el: Element) {
  const r = document.createRange(); r.selectNodeContents(el.querySelector('span, code') || el);
  const s = getSelection(); s?.removeAllRanges(); s?.addRange(r);
}
document.addEventListener('click', (e) => {
  const b = (e.target as Element).closest<HTMLElement>('[data-copy]');
  if (!b) return;
  const v = b.dataset.copy!, holder = b.closest('.hash-row') || b;
  const done = () => {
    b.classList.add('done'); setTimeout(() => b.classList.remove('done'), 1400);
    showToast(v.length > 24 ? ds.copied! : ds.copiedVal!.replace('{v}', v));
  };
  try { navigator.clipboard.writeText(v).then(done, () => selectText(holder)); } catch { selectText(holder); }
});

/* ---------- tablists ---------- */
function wireTabs(tabs: HTMLElement[], onSelect?: (tab: HTMLElement, user: boolean) => void) {
  function select(tab: HTMLElement, focus = false, user = false) {
    tabs.forEach((t) => {
      const on = t === tab;
      t.setAttribute('aria-selected', String(on)); t.tabIndex = on ? 0 : -1;
      document.getElementById(t.getAttribute('aria-controls')!)!.hidden = !on;
    });
    if (focus) tab.focus();
    onSelect?.(tab, user);
  }
  tabs.forEach((t, i) => {
    t.addEventListener('click', () => select(t, false, true));
    t.addEventListener('keydown', (e) => {
      const step = ({ ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 } as Record<string, number>)[e.key];
      if (!step) return;
      e.preventDefault();
      select(tabs[(i + step + tabs.length) % tabs.length], true, true);
    });
  });
  return select;
}

/* guide tabs, synced with the URL hash (#host, #android, #windows, #console) */
const guideTabs = $$('.tabs [role="tab"]');
if (guideTabs.length) {
  const select = wireTabs(guideTabs, (tab, user) => {
    if (user) history.replaceState(null, '', '#' + tab.dataset.key);
  });
  const fromHash = () => {
    const tab = guideTabs.find((t) => t.dataset.key === location.hash.slice(1));
    if (tab) select(tab);
  };
  fromHash();
  addEventListener('hashchange', fromHash);
}

/* feature showcase: auto-advance until the visitor picks one */
const pf = $$('.pf-item');
if (pf.length) {
  let auto = !reduce, timerItem: HTMLElement | null = null;
  const runBar = (tab: HTMLElement) => {
    pf.forEach((t) => t.querySelector('.bar')!.classList.remove('run'));
    if (!auto) return;
    const bar = tab.querySelector<HTMLElement>('.bar')!;
    void bar.offsetWidth; bar.classList.add('run'); timerItem = tab;
  };
  const select = wireTabs(pf, (tab, user) => { if (user) auto = false; runBar(tab); });
  pf.forEach((t) => t.querySelector('.bar')!.addEventListener('animationend', () => {
    if (auto && timerItem === t) select(pf[(pf.indexOf(t) + 1) % pf.length]);
  }));
  const stage = $('.pf')!;
  if ('IntersectionObserver' in window && auto) {
    let started = false;
    new IntersectionObserver((es) => es.forEach((e) => {
      pf.forEach((t) => (t.querySelector<HTMLElement>('.bar')!.style.animationPlayState = e.isIntersecting ? 'running' : 'paused'));
      if (e.isIntersecting && !started) { started = true; runBar(pf[0]); }
    }), { threshold: 0.3 }).observe(stage);
  }
}

/* ---------- FAQ search ---------- */
const search = $<HTMLInputElement>('#faq-q');
if (search) {
  const items = $$('#faq-list .qa'), empty = $('#faq-empty')!;
  search.addEventListener('input', () => {
    const q = search.value.trim().toLowerCase();
    let shown = 0;
    items.forEach((d) => { const ok = !q || d.textContent!.toLowerCase().includes(q); d.hidden = !ok; if (ok) shown++; });
    empty.hidden = shown > 0;
  });
}

/* ---------- header: scroll progress + shadow ---------- */
const top = $('.top'), prog = $('#progress');
let ticking = false;
function onScroll() {
  ticking = false;
  const h = root.scrollHeight - innerHeight;
  prog?.style.setProperty('--p', String(h > 0 ? Math.min(1, scrollY / h) : 0));
  top?.classList.toggle('scrolled', scrollY > 8);
}
addEventListener('scroll', () => { if (!ticking) { ticking = true; requestAnimationFrame(onScroll); } }, { passive: true });
onScroll();

/* ---------- pointer spotlight on cards ---------- */
$$('.feat, .dl-card, .panel').forEach((c) => c.addEventListener('pointermove', (e) => {
  const r = c.getBoundingClientRect();
  c.style.setProperty('--mx', `${e.clientX - r.left}px`); c.style.setProperty('--my', `${e.clientY - r.top}px`);
}));

if (!reduce) {
  /* hero phone tilts toward the pointer */
  const hero = $('.hero'), phone = $('.hero-device .phone');
  if (hero && phone && matchMedia('(hover: hover)').matches) {
    hero.addEventListener('pointermove', (e) => {
      const r = hero.getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width - 0.5, y = (e.clientY - r.top) / r.height - 0.5;
      phone.style.setProperty('--ry', `${(x * 14).toFixed(2)}deg`); phone.style.setProperty('--rx', `${(-y * 10).toFixed(2)}deg`);
    });
    hero.addEventListener('pointerleave', () => { phone.style.setProperty('--ry', '0deg'); phone.style.setProperty('--rx', '0deg'); });
  }

  if ('IntersectionObserver' in window) {
    /* scroll reveal: content stays visible at rest; the animation only plays as it enters the screen */
    const io = new IntersectionObserver((es) => es.forEach((e) => {
      if (!e.isIntersecting) return;
      e.target.classList.add('in'); io.unobserve(e.target);
    }), { threshold: 0.12 });
    const groups: [string, boolean][] = [
      ['section:not(.hero) .sec-head', false], ['.pf-grid', true], ['.feat', false], ['.diagram', true], ['.how-steps li', false],
      ['.guide-grid', true], ['.cta .wrap > *', false], ['.dl-card', false], ['.dl-more > *', false], ['.dl-extra > *', false],
      ['.qa', false], ['.stat', false], ['.log > li', false],
    ];
    groups.forEach(([sel, scale]) => $$(sel).forEach((el, i) => {
      if (el.getBoundingClientRect().top < innerHeight * 0.9) return;
      el.classList.add(scale ? 'reveal-scale' : 'reveal');
      el.style.setProperty('--i', String(i % 6));
      io.observe(el);
    }));

    /* count-up numbers */
    const ease = (t: number) => 1 - Math.pow(1 - t, 3);
    const cio = new IntersectionObserver((es) => es.forEach((e) => {
      if (!e.isIntersecting) return;
      cio.unobserve(e.target);
      const el = e.target as HTMLElement, end = Number(el.dataset.count), suf = el.dataset.suffix || '', t0 = performance.now();
      const step = (now: number) => {
        const p = Math.min(1, (now - t0) / 1500);
        el.textContent = Math.round(end * ease(p)) + suf;
        if (p < 1) requestAnimationFrame(step);
      };
      requestAnimationFrame(step);
    }), { threshold: 0.6 });
    $$('[data-count]').forEach((el) => cio.observe(el));
  }
} else {
  $$<SVGSVGElement>('svg').forEach((s) => s.pauseAnimations?.());
}
