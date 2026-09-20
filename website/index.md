---
layout: page
sidebar: false
aside: false
footer: false
---

<div class="hb-home">

<section class="hb-hero">
  <div class="hb-hero-copy">
    <div class="hb-eyebrow">
      <span class="hb-dot"></span>
      Open source · No root required
    </div>

    <h1>
      Share your phone's connection
      <span>without the usual hotspot limits.</span>
    </h1>

    <p class="hb-hero-lead">
      Hotspot Bypass VPN connects Android, Windows, and other devices through
      Wi-Fi Direct + SOCKS5 routing — with a full-system VPN mode for Windows.
    </p>

    <div class="hb-hero-actions">
      <a class="hb-button hb-button-primary" href="./download">
        <span>Download</span>
        <span aria-hidden="true">→</span>
      </a>
      <a class="hb-button hb-button-secondary" href="./guide/android">
        View setup guide
      </a>
    </div>

    <div class="hb-meta-row">
      <span>Android 7+</span>
      <span>Windows 10 / 11</span>
      <span>MIT Licensed</span>
      <span>Local-first</span>
    </div>
  </div>

  <div class="hb-hero-visual" aria-label="Hotspot Bypass VPN product preview">
    <div class="hb-orbit hb-orbit-one"></div>
    <div class="hb-orbit hb-orbit-two"></div>

    <div class="hb-window-card">
      <div class="hb-window-bar">
        <span></span><span></span><span></span>
        <small>Windows Client</small>
      </div>
      <img src="./images/screenshot-windows.png" alt="Hotspot Bypass VPN Windows client">
    </div>

    <div class="hb-phone-card">
      <div class="hb-phone-glow"></div>
      <img src="./images/screenshot-host.jpg" alt="Hotspot Bypass VPN Android Host mode">
    </div>

    <div class="hb-status-pill hb-status-android">
      <strong>Android</strong>
      <span>Host ready</span>
    </div>

    <div class="hb-status-pill hb-status-windows">
      <strong>Windows</strong>
      <span>Full-system routing</span>
    </div>
  </div>
</section>

<section class="hb-flow-strip" aria-label="Connection flow">
  <div class="hb-flow-item">
    <span class="hb-flow-icon">1</span>
    <div><strong>Android Host</strong><small>Shares the mobile connection</small></div>
  </div>
  <span class="hb-flow-arrow">→</span>
  <div class="hb-flow-item">
    <span class="hb-flow-icon">2</span>
    <div><strong>Wi-Fi Direct</strong><small>Private local link</small></div>
  </div>
  <span class="hb-flow-arrow">→</span>
  <div class="hb-flow-item">
    <span class="hb-flow-icon">3</span>
    <div><strong>Client Device</strong><small>Android, Windows, console & more</small></div>
  </div>
</section>

<section class="hb-section">
  <div class="hb-section-heading">
    <span class="hb-kicker">How it works</span>
    <h2>A simple connection flow.</h2>
    <p>Start on your phone, connect the client, then route traffic through the host.</p>
  </div>

  <div class="hb-steps-grid">
    <article class="hb-step-card">
      <span class="hb-step-number">01</span>
      <div class="hb-step-icon">📡</div>
      <h3>Start Host mode</h3>
      <p>Choose 2.4 GHz or 5 GHz, then start sharing from the Android app.</p>
      <a href="./guide/android">Android guide →</a>
    </article>

    <article class="hb-step-card">
      <span class="hb-step-number">02</span>
      <div class="hb-step-icon">🔗</div>
      <h3>Join the local network</h3>
      <p>Connect the client device to the Wi-Fi Direct network created by the host.</p>
      <a href="./guide/android">Connection details →</a>
    </article>

    <article class="hb-step-card">
      <span class="hb-step-number">03</span>
      <div class="hb-step-icon">🛡️</div>
      <h3>Start the tunnel</h3>
      <p>Use the Android client or Windows desktop app to route traffic through the phone.</p>
      <a href="./guide/windows">Windows guide →</a>
    </article>
  </div>
</section>

<section class="hb-showcase">
  <div class="hb-showcase-copy">
    <span class="hb-kicker">One app, two roles</span>
    <h2>Share from one phone.<br>Connect from another.</h2>
    <p>
      Host mode exposes the network and proxy details you need. Client mode
      creates an Android VPN tunnel so apps can use the host connection without
      manual per-app proxy configuration.
    </p>

    <div class="hb-check-list">
      <span>✓ Host & Client modes</span>
      <span>✓ 2.4 GHz / 5 GHz selection</span>
      <span>✓ SOCKS5 TCP + UDP routing</span>
      <span>✓ Foreground service for persistence</span>
    </div>

    <a class="hb-text-link" href="./features">Explore all features →</a>
  </div>

  <div class="hb-showcase-phones">
    <figure class="hb-device-shot hb-device-shot-front">
      <img src="./images/screenshot-host.jpg" alt="Android Host mode">
      <figcaption>Host mode</figcaption>
    </figure>
    <figure class="hb-device-shot hb-device-shot-back">
      <img src="./images/screenshot-client.jpg" alt="Android Client mode">
      <figcaption>Client mode</figcaption>
    </figure>
  </div>
</section>

<section class="hb-section">
  <div class="hb-section-heading hb-section-heading-left">
    <span class="hb-kicker">Built for everyday use</span>
    <h2>More than a browser proxy.</h2>
    <p>The Windows client can create a TUN adapter and route system traffic, while the Android side stays lightweight and local.</p>
  </div>

  <div class="hb-feature-grid">
    <article class="hb-feature-card hb-feature-card-large">
      <div class="hb-feature-icon">💻</div>
      <h3>Windows full-system VPN</h3>
      <p>Routes Windows traffic through the phone using a virtual TUN adapter, with status and logs in one desktop client.</p>
      <div class="hb-mini-window">
        <img src="./images/screenshot-windows.png" alt="Windows client preview">
      </div>
    </article>

    <article class="hb-feature-card">
      <div class="hb-feature-icon">🎮</div>
      <h3>Console sharing</h3>
      <p>Use Windows Internet Connection Sharing to pass the connection onward to consoles and other devices.</p>
    </article>

    <article class="hb-feature-card">
      <div class="hb-feature-icon">🔒</div>
      <h3>No root required</h3>
      <p>Built on Android's standard VPNService and Wi-Fi Direct APIs.</p>
    </article>

    <article class="hb-feature-card">
      <div class="hb-feature-icon">⚡</div>
      <h3>Connection persistence</h3>
      <p>Foreground services, wake locks, and reconnect logic help keep sessions alive.</p>
    </article>

    <article class="hb-feature-card">
      <div class="hb-feature-icon">🧩</div>
      <h3>Open source</h3>
      <p>Inspect the implementation, build it yourself, or contribute on GitHub.</p>
    </article>
  </div>
</section>

<section class="hb-platforms">
  <div class="hb-platform-card">
    <div>
      <span class="hb-platform-label">ANDROID</span>
      <h3>Host or connect from your phone.</h3>
      <p>Run Host mode to share your cellular connection, or Client mode to tunnel another Android device.</p>
    </div>
    <a href="./download">Download APK →</a>
  </div>

  <div class="hb-platform-card">
    <div>
      <span class="hb-platform-label">WINDOWS</span>
      <h3>Route the whole PC.</h3>
      <p>Use the desktop client for TUN-based system routing, status monitoring, and connection sharing.</p>
    </div>
    <a href="./guide/windows">Windows setup →</a>
  </div>

  <div class="hb-platform-card">
    <div>
      <span class="hb-platform-label">OTHER DEVICES</span>
      <h3>Bring consoles and TVs online.</h3>
      <p>Bridge the Windows connection to devices that cannot run the app directly.</p>
    </div>
    <a href="./guide/non-app-devices">Device guide →</a>
  </div>
</section>

<section class="hb-cta">
  <div class="hb-cta-glow"></div>
  <div>
    <span class="hb-kicker">Ready to connect?</span>
    <h2>Get started in a few minutes.</h2>
    <p>Install the Android app, start Host mode, and connect your next device.</p>
  </div>
  <div class="hb-cta-actions">
    <a class="hb-button hb-button-primary" href="./download">Download now</a>
    <a class="hb-button hb-button-secondary" href="./guide/android">Read the guide</a>
  </div>
</section>

<section class="hb-footer-note">
  <span>Hotspot Bypass VPN</span>
  <span>Open source · MIT License</span>
  <a href="https://github.com/nestchao/Hotspot-Bypass-VPN-Unlimited-Hotspot">GitHub ↗</a>
</section>

</div>
