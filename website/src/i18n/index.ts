import en from './en';
import zh from './zh';

export type Lang = 'en' | 'zh';
export const dict = { en, zh };
export const t = (lang: Lang) => dict[lang];

const BASE = import.meta.env.BASE_URL.replace(/\/$/, '');

/** Site-relative path ("/guide/") → full URL path with base and language prefix. */
export function href(lang: Lang, path = '/') {
  const p = path.startsWith('/') ? path : `/${path}`;
  return `${BASE}${lang === 'zh' ? '/zh' : ''}${p}`;
}

/** Static asset path under public/. */
export const asset = (path: string) => `${BASE}/${path.replace(/^\//, '')}`;

/** The same page in the other language. */
export function altPath(lang: Lang, page: string) {
  return href(lang === 'en' ? 'zh' : 'en', page);
}
