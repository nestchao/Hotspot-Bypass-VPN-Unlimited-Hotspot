import { useEffect, useState } from "react";
import {
  CheckCircledIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  CopyIcon,
  EyeClosedIcon,
  EyeOpenIcon,
} from "@radix-ui/react-icons";
import { MobileScroll, KeyboardInput, useKeyboard } from "./mobile";

type Tab = "share" | "connect";
type Band = "2.4" | "5";

const connectionDetails = [
  { label: "SSID", value: "DIRECT-HotspotBypass", secret: false },
  { label: "Password", value: "87654321", secret: true },
  { label: "Proxy IP", value: "192.168.49.1", secret: false },
  { label: "Port", value: "8080", secret: false },
] as const;

export default function Prototype() {
  const [tab, setTab] = useState<Tab>("share");
  const [band, setBand] = useState<Band>("2.4");
  const [sharing, setSharing] = useState(false);
  const [connected, setConnected] = useState(false);
  const [showPassword, setShowPassword] = useState(true);
  const [showLog, setShowLog] = useState(false);
  const [toast, setToast] = useState("");
  const [host, setHost] = useState("192.168.49.1");
  const [port, setPort] = useState("8080");
  const keyboard = useKeyboard();

  useEffect(() => {
    document.title = "Bypass VPN — Minimalist GUI Preview";
  }, []);

  const copyValue = async (label: string, value: string) => {
    try {
      await navigator.clipboard.writeText(value);
      setToast(`${label} copied`);
    } catch {
      setToast("Copy unavailable. Select the connection details manually.");
    }
    window.setTimeout(() => setToast(""), 1800);
  };

  const switchTab = (nextTab: Tab) => {
    keyboard.hide();
    setTab(nextTab);
    setToast("");
  };

  return (
    <MobileScroll className="app-screen">
      <main className="screen-content" data-testid="minimalist-prototype">
        <header className="brand-row">
          <span className="brand">Bypass VPN</span>
          {((tab === "share" && sharing) || (tab === "connect" && connected)) && (
            <span className="live-label">
              <CheckCircledIcon aria-hidden="true" /> Active
            </span>
          )}
        </header>

        <nav className="tabs" aria-label="Connection mode">
          <button
            className={tab === "share" ? "tab is-active" : "tab"}
            aria-pressed={tab === "share"}
            onClick={() => switchTab("share")}
          >
            Share
          </button>
          <button
            className={tab === "connect" ? "tab is-active" : "tab"}
            aria-pressed={tab === "connect"}
            onClick={() => switchTab("connect")}
          >
            Connect
          </button>
        </nav>

        {tab === "share" ? (
          <section className="view" aria-label="Share your connection">
            <div className="intro">
              <h1>{sharing ? "Your connection is live" : "Share your connection"}</h1>
              <p className="lede">
                {sharing
                  ? `Sharing on ${band} GHz. Use the details below on each device.`
                  : "Create a private hotspot for your other devices."}
              </p>
            </div>

            {!sharing && (
              <fieldset className="band-fieldset">
                <legend>Wi-Fi band</legend>
                <div className="segment" aria-label="Wi-Fi band">
                  <button
                    className={band === "2.4" ? "segment-option is-selected" : "segment-option"}
                    aria-pressed={band === "2.4"}
                    onClick={() => setBand("2.4")}
                  >
                    2.4 GHz
                  </button>
                  <button
                    className={band === "5" ? "segment-option is-selected" : "segment-option"}
                    aria-pressed={band === "5"}
                    onClick={() => setBand("5")}
                  >
                    5 GHz
                  </button>
                </div>
              </fieldset>
            )}

            <button
              className={sharing ? "primary-action stop" : "primary-action"}
              onClick={() => setSharing((current) => !current)}
            >
              {sharing ? "Stop sharing" : "Start sharing"}
            </button>

            <section className={sharing ? "details" : "details is-preview"} aria-label="Connection details">
              <div className="section-heading">
                <h2>Connection details</h2>
                {sharing && (
                  <button
                    className="text-action"
                    onClick={() =>
                      copyValue(
                        "All details",
                        connectionDetails.map((item) => `${item.label}: ${item.value}`).join("\n"),
                      )
                    }
                  >
                    Copy all
                  </button>
                )}
              </div>

              <div className="detail-list">
                {connectionDetails.map((item) => {
                  const hidden = item.secret && !showPassword;
                  return (
                    <div className="detail-row" key={item.label}>
                      <div>
                        <span className="detail-label">{item.label}</span>
                        <span className={hidden ? "detail-value password" : "detail-value"}>
                          {hidden ? "••••••••" : item.value}
                        </span>
                      </div>
                      <div className="row-actions">
                        {item.secret && (
                          <button
                            className="icon-button"
                            aria-label={showPassword ? "Hide password" : "Show password"}
                            onClick={() => setShowPassword((current) => !current)}
                          >
                            {showPassword ? <EyeClosedIcon /> : <EyeOpenIcon />}
                          </button>
                        )}
                        <button
                          className="icon-button"
                          aria-label={`Copy ${item.label}`}
                          onClick={() => copyValue(item.label, item.value)}
                        >
                          <CopyIcon />
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            </section>

            <section className="activity-section">
              <button className="activity-toggle" aria-expanded={showLog} onClick={() => setShowLog((current) => !current)}>
                <span>Activity log</span>
                {showLog ? <ChevronUpIcon aria-hidden="true" /> : <ChevronDownIcon aria-hidden="true" />}
              </button>
              {showLog && (
                <div className="log-lines" aria-live="polite">
                  <p><time>Now</time> {sharing ? "Proxy is ready for connections" : "Waiting to start sharing"}</p>
                  <p><time>8080</time> SOCKS5 endpoint configured</p>
                </div>
              )}
            </section>
          </section>
        ) : (
          <section className="view" aria-label="Connect through a host">
            <div className="intro">
              <p className="eyebrow">CLIENT MODE</p>
              <h1>{connected ? "You’re connected" : "Connect through a host"}</h1>
              <p className="lede">
                {connected
                  ? `Preview connection to ${host}.`
                  : "Enter the proxy details shown on the host device."}
              </p>
            </div>

            {connected ? (
              <div className="connected-summary">
                <CheckCircledIcon aria-hidden="true" />
                <div>
                  <span className="detail-label">HOST</span>
                  <strong>{host} : {port}</strong>
                </div>
              </div>
            ) : (
              <form className="connect-form" onSubmit={(event) => { event.preventDefault(); keyboard.hide(); setConnected(true); }}>
                <label className="mobile-field" htmlFor="host-address"><span className="field-label">Host address</span><KeyboardInput id="host-address" required value={host} onChange={(event) => setHost(event.target.value.trim())} /></label>
                <label className="mobile-field" htmlFor="host-port"><span className="field-label">Port</span><KeyboardInput id="host-port" type="number" min="1" max="65535" required value={port} onChange={(event) => setPort(event.target.value)} /></label>
                <button className="primary-action" type="submit">Connect</button>
              </form>
            )}

            {connected && (
              <button className="primary-action stop" onClick={() => setConnected(false)}>
                Disconnect
              </button>
            )}

            <aside className="privacy-note">
              <p className="eyebrow">BEFORE YOU CONNECT</p>
              <p>Join the host’s Wi-Fi network first, then enter its proxy address here.</p>
            </aside>
          </section>
        )}

        <p className="preview-note">Interactive preview · connections are simulated</p>
        {toast && <p role="status" className="copy-feedback">{toast}</p>}
      </main>
    </MobileScroll>
  );
}
