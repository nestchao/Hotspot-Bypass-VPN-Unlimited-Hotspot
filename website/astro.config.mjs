import { defineConfig } from 'astro/config';

// Deployed to GitHub Pages as a project site.
export default defineConfig({
  site: 'https://nestchao.github.io',
  base: '/Hotspot-Bypass-VPN-Unlimited-Hotspot',
  trailingSlash: 'ignore',
  build: { format: 'directory' },
  i18n: {
    defaultLocale: 'en',
    locales: ['en', 'zh'],
    routing: { prefixDefaultLocale: false },
  },
  // Old VitePress URLs keep working.
  redirects: {
    '/features': '/Hotspot-Bypass-VPN-Unlimited-Hotspot/#features',
    '/guide/android': '/Hotspot-Bypass-VPN-Unlimited-Hotspot/guide/#android',
    '/guide/windows': '/Hotspot-Bypass-VPN-Unlimited-Hotspot/guide/#windows',
    '/guide/non-app-devices': '/Hotspot-Bypass-VPN-Unlimited-Hotspot/guide/#console',
    '/zh/features': '/Hotspot-Bypass-VPN-Unlimited-Hotspot/zh/#features',
    '/zh/guide/android': '/Hotspot-Bypass-VPN-Unlimited-Hotspot/zh/guide/#android',
    '/zh/guide/windows': '/Hotspot-Bypass-VPN-Unlimited-Hotspot/zh/guide/#windows',
    '/zh/guide/non-app-devices': '/Hotspot-Bypass-VPN-Unlimited-Hotspot/zh/guide/#console',
  },
});
