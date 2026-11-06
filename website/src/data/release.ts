// Update this file for each new release. Every page reads its links and hashes from here.
export const REPO = 'https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot';

export const release = {
  version: 'v6.0.0',
  date: { en: 'October 2026', zh: '2026 年 10 月' },
  notes: `${REPO}/releases/tag/v6.0.0`,
  files: {
    apk: {
      name: 'Hotspot-Bypass-VPN.apk',
      url: `${REPO}/releases/download/v6.0.0/Hotspot-Bypass-VPN.apk`,
      sha256: '8e9c1c9123e7f35cfe85c4cf4c2283c56a75c19f9fd89fd0c01f111569b63571',
    },
    installer: {
      name: 'HotspotBypassVPNSetup.exe',
      url: `${REPO}/releases/download/v6.0.0/HotspotBypassVPNSetup.exe`,
      sha256: 'b6c0401d978281a58e1cd71a8d43ccb9f5600da1dc81cd9e3dbc8f1bd01c5568',
    },
    zip: {
      name: 'Hotspot_Bypass_VPN_Windows.zip',
      url: `${REPO}/releases/download/v6.0.0/Hotspot_Bypass_VPN_Windows.zip`,
      sha256: '39ef7e3ecacd6ce92c074eed5b4fdbf238d5b5f953b253b045ff2126606ac6f6',
    },
  },
} as const;
