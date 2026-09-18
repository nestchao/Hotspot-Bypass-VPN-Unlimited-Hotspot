import customtkinter as ctk
from tkinter import messagebox
import threading
import socket
import struct
import select
import sys
import os
import time
import subprocess
import urllib.request
import zipfile
import io
import platform
from console_bridge import BridgeError, IcsManager
# import symbols

# --- Windows specific imports ---
IS_WINDOWS = sys.platform == "win32"
if IS_WINDOWS:
    import winreg
    import ctypes

# Constants for binary management
CONFIG_DIR = os.path.join(os.environ.get("APPDATA", "."), "LaptopProxy")
BIN_DIR = os.path.join(CONFIG_DIR, "bin")

class TunManager:
    """
    Manages tun2socks & wintun.dll. 
    Creates a Virtual Network Adapter to capture 100% of Windows traffic.
    """
    def __init__(self, phone_ip, phone_port, local_port, log_fn, on_tunnel_lost=None):
        self.on_tunnel_lost = on_tunnel_lost
        self.phone_ip = phone_ip
        self.phone_port = phone_port
        self.local_port = local_port
        self.log = log_fn
        self.process = None
        self._monitoring_active = False
        self._connection_healthy = False
        self._restarting = False

    def _clean_routes(self):
        subprocess.run(["route", "delete", "0.0.0.0", "mask", "128.0.0.0"], capture_output=True)
        subprocess.run(["route", "delete", "128.0.0.0", "mask", "128.0.0.0"], capture_output=True)

    def _check_dependencies(self):
        os.makedirs(BIN_DIR, exist_ok=True)
        t2s_path = os.path.join(BIN_DIR, "tun2socks.exe")
        wt_path  = os.path.join(BIN_DIR, "wintun.dll")

        if os.path.exists(t2s_path) and os.path.exists(wt_path):
            return t2s_path

        # Determine architecture
        arch = platform.machine().lower()
        if 'arm' in arch or 'aarch' in arch:
            t2s_url = "https://github.com/xjasonlyu/tun2socks/releases/download/v2.5.2/tun2socks-windows-arm64.zip"
            wt_arch = "arm64"
        else:
            t2s_url = "https://github.com/xjasonlyu/tun2socks/releases/download/v2.5.2/tun2socks-windows-amd64.zip"
            wt_arch = "amd64"

        self.log("Downloading VPN dependencies through bridge...")
        # Start a temporary bridge for downloading
        temp_bridge = HttpSocksBridge(self.local_port, self.phone_ip, self.phone_port, self.log)
        temp_bridge.start()
        time.sleep(1.5)

        proxy_handler = urllib.request.ProxyHandler({
            'http': f'http://127.0.0.1:{self.local_port}',
            'https': f'http://127.0.0.1:{self.local_port}'
        })
        opener = urllib.request.build_opener(proxy_handler)
        urllib.request.install_opener(opener)

        try:
            if not os.path.exists(t2s_path):
                self.log("Downloading tun2socks...")
                req = urllib.request.urlopen(t2s_url, timeout=30)
                with zipfile.ZipFile(io.BytesIO(req.read())) as z:
                    for name in z.namelist():
                        if name.endswith(".exe"):
                            with open(t2s_path, "wb") as f:
                                f.write(z.read(name))
                            break

            if not os.path.exists(wt_path):
                self.log("Downloading wintun.dll...")
                req = urllib.request.urlopen("https://www.wintun.net/builds/wintun-0.14.1.zip", timeout=30)
                with zipfile.ZipFile(io.BytesIO(req.read())) as z:
                    wt_file = f"wintun/bin/{wt_arch}/wintun.dll"
                    with open(wt_path, "wb") as f:
                        f.write(z.read(wt_file))
            self.log("Dependencies downloaded successfully.")
        except Exception as e:
            self.log(f"Download failed: {e}")
            raise e
        finally:
            urllib.request.install_opener(urllib.request.build_opener())
            temp_bridge.stop()
            time.sleep(0.5)

        return t2s_path

    def start(self):
        t2s_exe = self._check_dependencies()
        
        self.log("Cleaning up old routes...")
        self._clean_routes()

        self.log("Starting VPN Interface...")
        cmd =[
            t2s_exe,
            "-device", "tun://LaptopProxyVPN",
            "-proxy", f"socks5://{self.phone_ip}:{self.phone_port}",
            "-loglevel", "warning"
        ]

        # Hide window on Windows, capture output
        cflags = 0x08000000 if IS_WINDOWS else 0
        self.process = subprocess.Popen(cmd, cwd=BIN_DIR, creationflags=cflags,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self._read_tun2socks_output()

        self.log("Waiting for network adapter...")
        adapter_found = False
        for _ in range(15):
            if self.process.poll() is not None:
                raise RuntimeError("tun2socks exited before its adapter was ready.")
            res = subprocess.run(["netsh", "interface", "ipv4", "show", "interfaces"], capture_output=True, text=True)
            if "LaptopProxyVPN" in res.stdout:
                adapter_found = True
                break
            time.sleep(1)
            
        if not adapter_found:
            self.stop()
            raise Exception("Failed to create Virtual Adapter.")

        time.sleep(2.0) 

        self.log("Configuring IP & DNS...")
        subprocess.run(["netsh", "interface", "ip", "set", "address", "name=LaptopProxyVPN", "static", "10.0.0.2", "255.255.255.0", "10.0.0.1"], check=True, capture_output=True)
        subprocess.run(["netsh", "interface", "ip", "set", "dns", "name=LaptopProxyVPN", "static", "8.8.8.8"], check=True, capture_output=True)

        time.sleep(1.0)

        self.log("Applying global routes...")
        subprocess.run(["route", "add", "0.0.0.0", "mask", "128.0.0.0", "10.0.0.1", "metric", "1"], check=True, capture_output=True)
        subprocess.run(["route", "add", "128.0.0.0", "mask", "128.0.0.0", "10.0.0.1", "metric", "1"], check=True, capture_output=True)
        with socket.create_connection((self.phone_ip, self.phone_port), timeout=5):
            pass
        self.log("Global VPN Active.")

        # Start health monitors
        self._monitoring_active = True
        self._connection_healthy = True
        threading.Thread(target=self._monitor_process, daemon=True).start()
        threading.Thread(target=self._monitor_health, daemon=True).start()

    def is_healthy(self):
        return bool(self._monitoring_active and self._connection_healthy and
                    self.process and self.process.poll() is None)

    def _notify_tunnel_lost(self):
        if self.on_tunnel_lost:
            self.on_tunnel_lost()

    def stop(self):
        self._notify_tunnel_lost()
        self._monitoring_active = False
        self._connection_healthy = False
        self.log("Restoring routes...")
        self._clean_routes()

        if self.process:
            self.log("Stopping VPN process...")
            self.process.terminate()
            try:
                self.process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.process.kill()
            self.process = None
            
        subprocess.run(["netsh", "interface", "set", "interface", "LaptopProxyVPN", "disable"], capture_output=True)
        self.log("VPN Stopped.")

    def _read_tun2socks_output(self):
        """Read and log tun2socks stdout/stderr in background threads."""
        def _reader(stream, label):
            try:
                for line in iter(stream.readline, b''):
                    if not line:
                        break
                    msg = line.decode('utf-8', errors='replace').strip()
                    if msg:
                        self.log(f"[tun2socks:{label}] {msg}")
            except Exception:
                pass

        if self.process and self.process.stdout:
            threading.Thread(target=_reader, args=(self.process.stdout, 'out'), daemon=True).start()
        if self.process and self.process.stderr:
            threading.Thread(target=_reader, args=(self.process.stderr, 'err'), daemon=True).start()

    def _monitor_process(self):
        while self._monitoring_active:
            time.sleep(5)
            if not self._monitoring_active:
                break
            if self.process:
                exit_code = self.process.poll()
                if exit_code is not None:
                    self.log(f"tun2socks crashed (exit code {exit_code}), auto-restarting...")
                    self._restart_tun2socks()
                    return

    def _monitor_health(self):
        fail_count = 0
        while self._monitoring_active:
            time.sleep(15)
            if not self._monitoring_active:
                break
            try:
                with socket.create_connection((self.phone_ip, self.phone_port), timeout=5):
                    pass
                if fail_count > 0:
                    self.log("Proxy connection restored")
                fail_count = 0
                self._connection_healthy = True
            except Exception:
                fail_count += 1
                if fail_count == 1:
                    self._connection_healthy = False
                    self.log("Proxy health check failed (1/2); stopping console sharing")
                    try:
                        self._notify_tunnel_lost()
                    except Exception as error:
                        self.log(f"Could not stop console sharing after tunnel loss: {error}")
                elif fail_count >= 2:
                    self.log("Proxy unreachable, restarting tunnel...")
                    self._connection_healthy = False
                    self._restart_tun2socks()
                    return

    def _restart_tun2socks(self):
        if self._restarting or not self._monitoring_active:
            return
        self._restarting = True
        self._connection_healthy = False
        try:
            self._notify_tunnel_lost()
            if self.process:
                self.process.terminate()
                try:
                    self.process.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    self.process.kill()
                self.process = None

            self._clean_routes()
            if not self._monitoring_active:
                return
            t2s_exe = os.path.join(BIN_DIR, "tun2socks.exe")
            cmd = [
                t2s_exe,
                "-device", "tun://LaptopProxyVPN",
                "-proxy", f"socks5://{self.phone_ip}:{self.phone_port}",
                "-loglevel", "warning"
            ]
            cflags = 0x08000000 if IS_WINDOWS else 0
            self.process = subprocess.Popen(
                cmd, cwd=BIN_DIR, creationflags=cflags,
                stdout=subprocess.PIPE, stderr=subprocess.PIPE
            )
            self._read_tun2socks_output()
            self.log("Waiting for network adapter...")
            for _ in range(15):
                if self.process.poll() is not None:
                    raise RuntimeError("tun2socks exited while restarting.")
                result = subprocess.run(
                    ["netsh", "interface", "ipv4", "show", "interfaces"],
                    capture_output=True, text=True
                )
                if "LaptopProxyVPN" in result.stdout:
                    break
                time.sleep(1)
            else:
                raise RuntimeError("VPN adapter did not reappear.")

            subprocess.run(
                ["netsh", "interface", "ip", "set", "address",
                 "name=LaptopProxyVPN", "static", "10.0.0.2",
                 "255.255.255.0", "10.0.0.1"], check=True, capture_output=True
            )
            subprocess.run(
                ["netsh", "interface", "ip", "set", "dns",
                 "name=LaptopProxyVPN", "static", "8.8.8.8"],
                check=True, capture_output=True
            )
            subprocess.run(
                ["route", "add", "0.0.0.0", "mask", "128.0.0.0",
                 "10.0.0.1", "metric", "1"], check=True, capture_output=True
            )
            subprocess.run(
                ["route", "add", "128.0.0.0", "mask", "128.0.0.0",
                 "10.0.0.1", "metric", "1"], check=True, capture_output=True
            )
            with socket.create_connection((self.phone_ip, self.phone_port), timeout=5):
                pass
            self._connection_healthy = True
            self.log("Tunnel restarted successfully")
            threading.Thread(target=self._monitor_process, daemon=True).start()
            threading.Thread(target=self._monitor_health, daemon=True).start()
        except Exception as error:
            self._monitoring_active = False
            self._connection_healthy = False
            self.log(f"Tunnel restart failed: {error}")
        finally:
            self._restarting = False

class HttpSocksBridge:
    def __init__(self, local_port, socks_host, socks_port, log_fn=None):
        self.local_port = local_port
        self.socks_host = socks_host
        self.socks_port = socks_port
        self.log = log_fn or print
        self._running = False
        self._server = None

    def start(self):
        self._running = True
        threading.Thread(target=self._serve, daemon=True).start()

    def stop(self):
        self._running = False
        if self._server:
            try:
                self._server.close()
            except:
                pass

    def _serve(self):
        try:
            self._server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self._server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            self._server.bind(("127.0.0.1", self.local_port))
            self._server.listen(100)
            self._server.settimeout(1.0)
            self.log(f"Bridge listening on 127.0.0.1:{self.local_port}")
        except Exception as e:
            self.log(f"Error starting bridge: {e}")
            return

        while self._running:
            try:
                conn, addr = self._server.accept()
                threading.Thread(target=self._handle, args=(conn,), daemon=True).start()
            except socket.timeout:
                continue
            except Exception:
                break

    def _handle(self, client):
        try:
            client.settimeout(10)
            data = b""
            while b"\r\n" not in data:
                chunk = client.recv(4096)
                if not chunk: return
                data += chunk

            first_line = data.split(b"\r\n")[0].decode("utf-8", errors="replace")
            parts = first_line.split()
            if len(parts) < 3: return
            method, target = parts[0], parts[1]

            if method.upper() == "CONNECT":
                host, port = target.rsplit(":", 1)
                port = int(port)
            else:
                from urllib.parse import urlparse
                parsed = urlparse(target)
                host, port = parsed.hostname or "", parsed.port or 80

            relay = self._socks5_connect(host, port)
            if relay is None:
                client.sendall(b"HTTP/1.1 502 Bad Gateway\r\n\r\n")
                return

            if method.upper() == "CONNECT":
                client.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n")
                self._pipe(client, relay)
            else:
                relay.sendall(data)
                self._pipe(client, relay)
        except Exception as e:
            self.log(f"Bridge handler error: {e}")
        finally:
            try: client.close()
            except: pass

    def _socks5_connect(self, host, port):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(5)
            s.connect((self.socks_host, self.socks_port))
            s.sendall(b"\x05\x01\x00")
            if s.recv(2)[1] != 0x00: return None
            
            # Request connection
            try:
                # IPv4
                s.sendall(b"\x05\x01\x00\x01" + socket.inet_aton(host) + struct.pack(">H", port))
            except:
                # Domain name
                host_bytes = host.encode()
                s.sendall(b"\x05\x01\x00\x03" + bytes([len(host_bytes)]) + host_bytes + struct.pack(">H", port))
            
            res = s.recv(10)
            if len(res) < 2 or res[1] != 0x00: return None
            return s
        except Exception:
            return None

    def _pipe(self, a, b):
        a.settimeout(None)
        b.settimeout(None)
        sockets = [a, b]
        try:
            while True:
                r, _, e = select.select(sockets, [], sockets, 30)
                if e: break
                for s in r:
                    other = b if s is a else a
                    chunk = s.recv(32768)
                    if not chunk: return
                    other.sendall(chunk)
        finally:
            for s in (a, b):
                try: s.close()
                except: pass

class App:
    BG = ("#F0F2F7", "#101827")
    CARD = ("#FFFFFF", "#1B2638")
    TEXT = ("#1C222B", "#F0F5FC")
    MUTED = ("#657182", "#A9B7C9")
    SUBTLE = ("#9BA4B0", "#8291A5")
    BORDER = ("#E2E6ED", "#344359")
    INPUT_BORDER = ("#D0D6E0", "#46566B")
    INPUT_BG = ("#FBFCFD", "#202D40")
    STATUS_BG = ("#F3F5F8", "#263448")
    ACCENT = "#0067C0"

    def __init__(self, root):
        self.root = root
        self.root.title("Hotspot Bypass | Windows")
        self.root.geometry(f"460x{min(770, max(620, root.winfo_screenheight() - 90))}")
        self.root.minsize(420, 590)
        self.root.configure(fg_color=self.BG)

        self.tun_mgr = None
        self.ics_mgr = IcsManager(self.log)
        self._bridge_lock = threading.RLock()
        self._bridge_busy = False
        self._ethernet_choices = {}
        self._activity_open = False
        self._advanced_open = False

        self.main = ctk.CTkScrollableFrame(root, fg_color=self.BG, corner_radius=0)
        self.main.pack(fill="both", expand=True)
        self.content = ctk.CTkFrame(self.main, fg_color="transparent")
        self.content.pack(fill="both", expand=True, padx=14, pady=(13, 20))

        header = ctk.CTkFrame(self.content, fg_color="transparent")
        header.pack(fill="x", pady=(0, 14))
        header.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(
            header, text="Connect & Share", font=("Segoe UI", 21, "bold"),
            text_color=self.TEXT, anchor="w", height=28
        ).grid(row=0, column=0, sticky="w")
        ctk.CTkLabel(
            header, text="WINDOWS CLIENT", font=("Segoe UI", 9, "bold"),
            fg_color=("#E3E7F0", "#2A3B51"), text_color=self.MUTED,
            corner_radius=11, width=108, height=23
        ).grid(row=0, column=1, sticky="e")
        ctk.CTkLabel(
            header, text="Android Phone   →   Windows VPN   →   Target Devices",
            font=("Segoe UI", 11), text_color=self.MUTED, anchor="w", height=20
        ).grid(row=1, column=0, columnspan=2, sticky="w", pady=(2, 0))

        vpn_card = ctk.CTkFrame(
            self.content, fg_color=self.CARD, corner_radius=13,
            border_width=1, border_color=self.BORDER
        )
        vpn_card.pack(fill="x", pady=(0, 12))
        vpn_card.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(
            vpn_card, text="01  INBOUND CONNECTION", font=("Segoe UI", 9, "bold"),
            text_color=self.ACCENT, anchor="w", height=15
        ).grid(row=0, column=0, sticky="w", padx=16, pady=(14, 0))
        ctk.CTkLabel(
            vpn_card, text="Connect to your phone", font=("Segoe UI", 16, "bold"),
            text_color=self.TEXT, anchor="w", height=24
        ).grid(row=1, column=0, sticky="w", padx=16, pady=(1, 0))
        ctk.CTkLabel(
            vpn_card, text="Enable Share Mode on Android and join its Wi-Fi Direct network.",
            font=("Segoe UI", 10), text_color=self.MUTED, anchor="w", height=19
        ).grid(row=2, column=0, sticky="w", padx=16, pady=(0, 11))

        fields = ctk.CTkFrame(vpn_card, fg_color="transparent")
        fields.grid(row=3, column=0, sticky="ew", padx=16)
        fields.grid_columnconfigure(0, weight=2)
        fields.grid_columnconfigure(1, weight=1)
        ctk.CTkLabel(fields, text="Phone IP Address", text_color=self.TEXT,
                     font=("Segoe UI", 10, "bold"), anchor="w", height=18).grid(
            row=0, column=0, sticky="w", pady=(0, 3)
        )
        ctk.CTkLabel(fields, text="SOCKS5 Port", text_color=self.TEXT,
                     font=("Segoe UI", 10, "bold"), anchor="w", height=18).grid(
            row=0, column=1, sticky="w", padx=(10, 0), pady=(0, 3)
        )
        self.phone_ip = ctk.CTkEntry(
            fields, height=33, corner_radius=7, fg_color=self.INPUT_BG,
            border_color=self.INPUT_BORDER, font=("Segoe UI", 11)
        )
        self.phone_ip.insert(0, "192.168.49.1")
        self.phone_ip.grid(row=1, column=0, sticky="ew")
        self.phone_port = ctk.CTkEntry(
            fields, height=33, corner_radius=7, fg_color=self.INPUT_BG,
            border_color=self.INPUT_BORDER, font=("Segoe UI", 11)
        )
        self.phone_port.insert(0, "8080")
        self.phone_port.grid(row=1, column=1, sticky="ew", padx=(10, 0))

        self.advanced_button = ctk.CTkButton(
            vpn_card, text="Advanced Settings  ⌄", width=133, height=25,
            fg_color="transparent", hover_color=self.STATUS_BG,
            text_color=self.ACCENT, font=("Segoe UI", 10, "bold"),
            anchor="w", command=self._toggle_advanced
        )
        self.advanced_button.grid(row=4, column=0, sticky="w", padx=12, pady=(5, 0))
        self.advanced_frame = ctk.CTkFrame(vpn_card, fg_color=self.STATUS_BG, corner_radius=7)
        self.advanced_frame.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(
            self.advanced_frame, text="Local download bridge port",
            text_color=self.TEXT, font=("Segoe UI", 10, "bold"), anchor="w", height=18
        ).grid(row=0, column=0, sticky="w", padx=10, pady=(8, 2))
        self.local_port = ctk.CTkEntry(
            self.advanced_frame, height=32, corner_radius=7,
            fg_color=self.INPUT_BG, border_color=self.INPUT_BORDER
        )
        self.local_port.insert(0, "7890")
        self.local_port.grid(row=1, column=0, sticky="ew", padx=10, pady=(0, 9))

        status_row = ctk.CTkFrame(vpn_card, fg_color=self.STATUS_BG, corner_radius=7)
        status_row.grid(row=6, column=0, sticky="ew", padx=16, pady=(12, 12))
        status_row.grid_columnconfigure(1, weight=1)
        self.status_dot = ctk.CTkFrame(
            status_row, width=8, height=8, corner_radius=4, fg_color="#8B95A1"
        )
        self.status_dot.grid(row=0, column=0, rowspan=2, padx=(12, 10), pady=13)
        self.status_var = ctk.StringVar(value="Disconnected")
        self.status_label = ctk.CTkLabel(
            status_row, textvariable=self.status_var, font=("Segoe UI", 11, "bold"),
            text_color=self.TEXT, anchor="w", height=18
        )
        self.status_label.grid(row=0, column=1, sticky="sw", pady=(8, 0))
        self.status_detail = ctk.StringVar(value="Enter phone IP and tap Connect VPN.")
        ctk.CTkLabel(
            status_row, textvariable=self.status_detail, font=("Segoe UI", 10),
            text_color=self.MUTED, anchor="w", height=17
        ).grid(row=1, column=1, sticky="nw", pady=(0, 8))
        self.progress = ctk.CTkProgressBar(vpn_card, mode="indeterminate", progress_color=self.ACCENT)
        self.progress.set(0)

        vpn_actions = ctk.CTkFrame(vpn_card, fg_color="transparent")
        vpn_actions.grid(row=8, column=0, sticky="ew", padx=16, pady=(0, 16))
        vpn_actions.grid_columnconfigure(0, weight=1)
        vpn_actions.grid_columnconfigure(1, weight=1)
        self.btn_start = ctk.CTkButton(
            vpn_actions, text="▷  Connect VPN", height=35, corner_radius=7,
            fg_color=self.ACCENT, hover_color="#005AAB",
            font=("Segoe UI", 11, "bold"), command=self.start
        )
        self.btn_start.grid(row=0, column=0, sticky="ew", padx=(0, 10))
        self.btn_stop = ctk.CTkButton(
            vpn_actions, text="Disconnect", height=35, corner_radius=7,
            state="disabled", fg_color=self.INPUT_BG,
            border_width=1, border_color=self.BORDER,
            text_color=self.TEXT, hover_color=self.STATUS_BG,
            font=("Segoe UI", 11), command=self.stop
        )
        self.btn_stop.grid(row=0, column=1, sticky="ew")

        self.bridge_card = ctk.CTkFrame(
            self.content, fg_color=self.CARD, corner_radius=13,
            border_width=1, border_color=self.BORDER
        )
        self.bridge_card.pack(fill="x", pady=(0, 12))
        self.bridge_card.grid_columnconfigure(0, weight=1)
        self.bridge_eyebrow = ctk.CTkLabel(
            self.bridge_card, text="02  OUTBOUND DISTRIBUTION", font=("Segoe UI", 9, "bold"),
            text_color=self.ACCENT, anchor="w", height=15
        )
        self.bridge_eyebrow.grid(row=0, column=0, sticky="w", padx=16, pady=(14, 0))
        self.bridge_title = ctk.CTkLabel(
            self.bridge_card, text="Share with consoles & devices",
            font=("Segoe UI", 16, "bold"), text_color=self.TEXT, anchor="w", height=24
        )
        self.bridge_title.grid(row=1, column=0, sticky="w", padx=16, pady=(1, 0))
        self.bridge_subtitle = ctk.CTkLabel(
            self.bridge_card, text="No proxy setup required on client gaming hardware.",
            font=("Segoe UI", 10), text_color=self.MUTED, anchor="w", height=19
        )
        self.bridge_subtitle.grid(row=2, column=0, sticky="w", padx=16, pady=(0, 10))
        self.bridge_mode = ctk.CTkSegmentedButton(
            self.bridge_card, values=["Wi-Fi Hotspot", "Ethernet Cable"],
            command=self._bridge_mode_changed, height=35,
            fg_color=("#EEF1F6", "#263448"), border_width=3,
            selected_color=("#FFFFFF", "#364960"),
            selected_hover_color=("#FFFFFF", "#40556F"),
            unselected_color=("#EEF1F6", "#263448"),
            unselected_hover_color=("#E4E9F0", "#314157"),
            text_color=self.TEXT, font=("Segoe UI", 11)
        )
        self.bridge_mode.grid(row=3, column=0, sticky="ew", padx=16)
        self.bridge_mode.set("Wi-Fi Hotspot")

        self.wifi_row = ctk.CTkFrame(self.bridge_card, fg_color="transparent")
        self.wifi_row.grid(row=4, column=0, sticky="ew", padx=16, pady=(12, 0))
        self.wifi_row.grid_columnconfigure(0, weight=2)
        self.wifi_row.grid_columnconfigure(1, weight=1)
        ctk.CTkLabel(
            self.wifi_row, text="Hotspot Network Name (SSID)",
            font=("Segoe UI", 10), text_color=self.MUTED, anchor="w", height=18
        ).grid(row=0, column=0, sticky="w", pady=(0, 3))
        ctk.CTkLabel(
            self.wifi_row, text="Password", font=("Segoe UI", 10),
            text_color=self.MUTED, anchor="w", height=18
        ).grid(row=0, column=1, sticky="w", padx=(10, 0), pady=(0, 3))
        self.hotspot_ssid_var = ctk.StringVar(value="Shown after start")
        self.hotspot_password_var = ctk.StringVar(value="After start")
        self.hotspot_ssid = ctk.CTkEntry(
            self.wifi_row, textvariable=self.hotspot_ssid_var,
            placeholder_text="Set in Windows Settings", height=32,
            corner_radius=7, fg_color=self.INPUT_BG, border_color=self.INPUT_BORDER,
            font=("Segoe UI", 10)
        )
        self.hotspot_ssid.grid(row=1, column=0, sticky="ew")
        self.hotspot_password = ctk.CTkEntry(
            self.wifi_row, textvariable=self.hotspot_password_var,
            placeholder_text="Windows Settings", height=32,
            corner_radius=7, fg_color=self.INPUT_BG, border_color=self.INPUT_BORDER,
            font=("Segoe UI", 10)
        )
        self.hotspot_password.grid(row=1, column=1, sticky="ew", padx=(10, 0))
        self.hotspot_ssid.configure(state="readonly")
        self.hotspot_password.configure(state="readonly")

        self.ethernet_row = ctk.CTkFrame(self.bridge_card, fg_color="transparent")
        self.ethernet_row.grid(row=4, column=0, sticky="ew", padx=16, pady=(12, 0))
        self.ethernet_row.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(
            self.ethernet_row, text="Connected Ethernet adapter",
            font=("Segoe UI", 10), text_color=self.MUTED, anchor="w", height=18
        ).grid(row=0, column=0, columnspan=2, sticky="w", pady=(0, 3))
        self.ethernet_choice = ctk.CTkComboBox(
            self.ethernet_row, values=["Connect cable, then refresh"],
            height=32, state="readonly", fg_color=self.INPUT_BG,
            border_color=self.INPUT_BORDER
        )
        self.ethernet_choice.grid(row=1, column=0, sticky="ew", padx=(0, 8))
        self.ethernet_choice.set("Connect cable, then refresh")
        self.refresh_ethernet_button = ctk.CTkButton(
            self.ethernet_row, text="Refresh", width=76, height=32,
            corner_radius=7, fg_color=self.STATUS_BG, text_color=self.TEXT,
            hover_color=self.BORDER, command=self._refresh_ethernet
        )
        self.refresh_ethernet_button.grid(row=1, column=1)
        self.ethernet_row.grid_remove()

        bridge_status_row = ctk.CTkFrame(
            self.bridge_card, fg_color=self.STATUS_BG, corner_radius=7
        )
        bridge_status_row.grid(row=5, column=0, sticky="ew", padx=16, pady=(12, 12))
        bridge_status_row.grid_columnconfigure(1, weight=1)
        self.bridge_status_dot = ctk.CTkFrame(
            bridge_status_row, width=8, height=8, corner_radius=4, fg_color="#8B95A1"
        )
        self.bridge_status_dot.grid(row=0, column=0, rowspan=2, padx=(12, 10), pady=13)
        self.bridge_status_title = ctk.StringVar(value="Waiting for VPN")
        ctk.CTkLabel(
            bridge_status_row, textvariable=self.bridge_status_title,
            font=("Segoe UI", 11, "bold"), text_color=self.TEXT,
            anchor="w", height=18
        ).grid(row=0, column=1, sticky="sw", pady=(8, 0))
        self.bridge_status = ctk.StringVar(value="Connect the phone in Step 1 first.")
        self.bridge_help = ctk.CTkLabel(
            bridge_status_row, textvariable=self.bridge_status,
            wraplength=360, justify="left", anchor="w",
            font=("Segoe UI", 10), text_color=self.MUTED
        )
        self.bridge_help.grid(row=1, column=1, sticky="nw", pady=(0, 8))
        self.bridge_status.trace_add("write", self._bridge_message_changed)

        self.bridge_button = ctk.CTkButton(
            self.bridge_card, text="Start Sharing Network", height=35,
            corner_radius=7, state="disabled", command=self._on_share_button,
            fg_color=self.BORDER, hover_color="#005AAB",
            text_color_disabled=self.MUTED,
            font=("Segoe UI", 11, "bold")
        )
        self.bridge_button.grid(row=6, column=0, sticky="ew", padx=16, pady=(0, 16))

        activity_card = ctk.CTkFrame(
            self.content, fg_color=self.CARD, corner_radius=13,
            border_width=1, border_color=self.BORDER
        )
        activity_card.pack(fill="x")
        activity_header = ctk.CTkFrame(activity_card, fg_color="transparent")
        activity_header.pack(fill="x", padx=16, pady=9)
        ctk.CTkLabel(
            activity_header, text="Activity log", font=("Segoe UI", 11, "bold"),
            text_color=self.TEXT
        ).pack(side="left")
        self.activity_button = ctk.CTkButton(
            activity_header, text="Show activity", width=95, height=24,
            fg_color="transparent", hover_color=self.STATUS_BG,
            text_color=self.ACCENT, command=self._toggle_activity
        )
        self.activity_button.pack(side="right")
        self.activity_body = ctk.CTkFrame(activity_card, fg_color="transparent")
        self.log_text = ctk.CTkTextbox(
            self.activity_body, font=("Consolas", 10), height=130, wrap="word"
        )
        self.log_text.pack(fill="x", padx=16)
        self.log_text._textbox.tag_configure("error", foreground="#F87171")
        self.log_text._textbox.tag_configure("success", foreground="#4ADE80")
        self.log_text.configure(state="disabled")
        ctk.CTkButton(
            self.activity_body, text="Clear log", width=80, height=24,
            fg_color="transparent", border_width=1, border_color=self.BORDER,
            text_color=self.MUTED, command=self._clear_log
        ).pack(anchor="e", padx=16, pady=(6, 12))

        self._update_bridge_button()
        threading.Thread(target=self._load_hotspot_info, daemon=True).start()

    def _load_hotspot_info(self):
        try:
            ssid, password = self.ics_mgr.hotspot_info()
            if ssid and password:
                self.root.after(0, lambda: self._set_hotspot_info(ssid, password))
        except (BridgeError, RuntimeError):
            pass  # Windows may not expose a Mobile Hotspot configuration yet.

    def _set_hotspot_info(self, ssid, password):
        self.hotspot_ssid_var.set(ssid)
        self.hotspot_password_var.set(password)

    def _on_share_button(self):
        if self.ics_mgr.needs_cleanup:
            self._stop_bridge()
        else:
            self._toggle_bridge()

    def _bridge_message_changed(self, *_args):
        message = self.bridge_status.get()
        if message.startswith(("Error:", "Cleanup error:", "VPN lost")):
            title, color = "Sharing needs attention", "#E45D63"
        elif message.startswith("Connect console"):
            title, color = "Sharing is on", "#23B885"
        elif message.startswith(("Configuring", "Stopping")):
            title, color = "Updating sharing", "#E59A31"
        elif message.startswith(("Windows will", "Connect a cable")):
            title, color = "Ready to share", "#8796A8"
        elif message.startswith("Console sharing stopped"):
            title, color = "Sharing is off", "#8796A8"
        else:
            title, color = "Waiting for VPN", "#8796A8"
        self.bridge_status_title.set(title)
        self.bridge_status_dot.configure(fg_color=color)
        self.bridge_help.configure(text_color=self.MUTED)

    def _toggle_advanced(self):
        self._advanced_open = not self._advanced_open
        if self._advanced_open:
            self.advanced_frame.grid(row=5, column=0, sticky="ew", padx=16, pady=(4, 0))
            self.advanced_button.configure(text="Advanced Settings  ⌃")
        else:
            self.advanced_frame.grid_remove()
            self.advanced_button.configure(text="Advanced Settings  ⌄")

    def _toggle_activity(self, show=None):
        self._activity_open = (not self._activity_open) if show is None else show
        if self._activity_open:
            self.activity_body.pack(fill="x")
            self.activity_button.configure(text="Hide activity")
        else:
            self.activity_body.pack_forget()
            self.activity_button.configure(text="Show activity")

    def _set_status(self, text, state="disconnected"):
        colors = {
            "connected": "#23B885",
            "starting": "#E59A31",
            "stopping": "#E59A31",
            "disconnected": "#8796A8",
            "error": "#E45D63",
        }
        details = {
            "connected": "VPN is ready. You can now share it with another device.",
            "starting": "Creating the VPN adapter and applying routes...",
            "stopping": "Closing the tunnel and restoring network settings...",
            "disconnected": "Enter phone IP and tap Connect VPN.",
            "error": "Check the activity log for the cause and retry.",
        }
        self.status_var.set(text.removeprefix("Status: "))
        self.status_dot.configure(fg_color=colors.get(state, "#8796A8"))
        self.status_detail.set(details.get(state, ""))
        if state in ("starting", "stopping"):
            self.progress.grid(row=7, column=0, sticky="ew", padx=16, pady=(0, 10))
            self.progress.start()
        else:
            self.progress.stop()
            self.progress.grid_remove()
        if state == "error" and not self._activity_open:
            self._toggle_activity(show=True)

    def _clear_log(self):
        self.log_text.configure(state="normal")
        self.log_text.delete("1.0", "end")
        self.log_text.configure(state="disabled")

    def log(self, msg):
        try:
            def _log():
                tag = None
                if msg.startswith("✗") or msg.startswith("Error"):
                    tag = "error"
                elif msg.startswith("✓"):
                    tag = "success"

                self.log_text.configure(state="normal")
                self.log_text.insert("end", f"[{time.strftime('%H:%M:%S')}] {msg}\n", tag)
                self.log_text.see("end")
                self.log_text.configure(state="disabled")

            self.root.after(0, _log)
        except:
            print(f"[{time.strftime('%H:%M:%S')}] {msg}")

    def _bridge_mode_changed(self, choice):
        ethernet = choice == "Ethernet Cable"
        locked = self._bridge_busy or self.ics_mgr.needs_cleanup
        if ethernet:
            self.wifi_row.grid_remove()
            self.ethernet_row.grid()
            state = "disabled" if locked else "readonly"
            self.ethernet_choice.configure(state=state)
            self.refresh_ethernet_button.configure(
                state="disabled" if locked else "normal"
            )
        else:
            self.ethernet_row.grid_remove()
            self.wifi_row.grid()
            self.ethernet_choice.configure(state="disabled")
            self.refresh_ethernet_button.configure(state="disabled")
        if not self.ics_mgr.needs_cleanup:
            healthy = bool(self.tun_mgr and self.tun_mgr.is_healthy())
            if not healthy:
                self.bridge_status.set("Connect the phone in Step 1 first.")
            else:
                self.bridge_status.set(
                    "Connect a cable, refresh adapters, then start sharing."
                    if ethernet else
                    "Windows will start Mobile Hotspot for your devices."
                )

    def _refresh_ethernet(self):
        self.refresh_ethernet_button.configure(state="disabled")
        threading.Thread(target=self._do_refresh_ethernet, daemon=True).start()

    def _do_refresh_ethernet(self):
        try:
            adapters = self.ics_mgr.list_ethernet()
            self.root.after(0, lambda: self._show_ethernet(adapters))
        except BridgeError as error:
            self.log(f"Could not list Ethernet adapters: {error}")
            self.root.after(0, lambda: self.bridge_status.set(f"Error: {error}"))
        finally:
            self.root.after(0, lambda: self.refresh_ethernet_button.configure(
                state="normal" if self.bridge_mode.get() == "Ethernet Cable"
                and not (self._bridge_busy or self.ics_mgr.needs_cleanup) else "disabled"
            ))

    def _show_ethernet(self, adapters):
        self._ethernet_choices = {
            f"{item.name} ({item.guid[:8]})": item.guid for item in adapters
        }
        values = list(self._ethernet_choices) or ["Connect cable, then refresh"]
        self.ethernet_choice.configure(values=values)
        self.ethernet_choice.set(values[0])

    def _update_bridge_button(self):
        locked = self._bridge_busy or self.ics_mgr.needs_cleanup
        healthy = bool(self.tun_mgr and self.tun_mgr.is_healthy())
        self.bridge_mode.configure(state="disabled" if locked or not healthy else "normal")
        self.ethernet_choice.configure(
            state="disabled" if locked or not healthy or self.bridge_mode.get() != "Ethernet Cable"
            else "readonly"
        )
        self.refresh_ethernet_button.configure(
            state="disabled" if locked or not healthy or self.bridge_mode.get() != "Ethernet Cable"
            else "normal"
        )
        self.bridge_eyebrow.configure(text_color=self.ACCENT if healthy else ("#628EBE", "#8296AC"))
        self.bridge_title.configure(text_color=self.TEXT if healthy else self.MUTED)
        self.bridge_subtitle.configure(text_color=self.MUTED if healthy else self.SUBTLE)
        if self._bridge_busy:
            self.bridge_button.configure(
                state="disabled", fg_color=self.BORDER, text="Working..."
            )
        elif self.ics_mgr.needs_cleanup:
            self.bridge_button.configure(
                state="normal", fg_color=self.ACCENT, text="Stop Sharing Network"
            )
        else:
            self.bridge_button.configure(
                state="normal" if healthy else "disabled",
                text="Start Sharing Network",
                fg_color=self.ACCENT if healthy else ("#BFD4E9", "#344B63")
            )

    def _stop_bridge(self):
        if self._bridge_busy or not self.ics_mgr.needs_cleanup:
            return
        self._bridge_busy = True
        self.bridge_status.set("Stopping console sharing...")
        self._update_bridge_button()
        threading.Thread(target=self._do_bridge_stop, daemon=True).start()

    def _toggle_bridge(self):
        if self._bridge_busy or self.ics_mgr.needs_cleanup:
            return
        if not self.tun_mgr or not self.tun_mgr.is_healthy():
            messagebox.showerror("Console sharing", "Connect the VPN first.")
            return
        mode = "ethernet" if self.bridge_mode.get() == "Ethernet Cable" else "wifi"
        guid = self._ethernet_choices.get(self.ethernet_choice.get()) if mode == "ethernet" else None
        if mode == "ethernet" and not guid:
            messagebox.showerror("Console sharing", "Connect an Ethernet cable and refresh adapters.")
            return
        self._bridge_busy = True
        self.bridge_status.set("Configuring Windows sharing...")
        self._update_bridge_button()
        threading.Thread(
            target=self._do_bridge_start, args=(mode, guid), daemon=True
        ).start()

    def _do_bridge_start(self, mode, guid):
        try:
            with self._bridge_lock:
                if not self.tun_mgr or not self.tun_mgr.is_healthy():
                    raise BridgeError("VPN tunnel is not healthy.")
                details = self.ics_mgr.start(mode, guid)
                if not self.tun_mgr.is_healthy():
                    self.ics_mgr.stop()
                    raise BridgeError("VPN disconnected while enabling console sharing.")
            self.root.after(0, lambda: self._show_bridge_started(details))
        except Exception as error:
            self.log(f"Console sharing failed: {error}")
            self.root.after(0, lambda: self.bridge_status.set(f"Error: {error}"))
        finally:
            self._bridge_busy = False
            self.root.after(0, self._update_bridge_button)

    def _show_bridge_started(self, details):
        if not self.ics_mgr.active:
            return
        if details.mode == "wifi":
            if details.ssid and details.password:
                self._set_hotspot_info(details.ssid, details.password)
                self.bridge_status.set(
                    f"Connect console to Wi-Fi '{details.ssid}' with password "
                    f"'{details.password}'. Leave console proxy settings off."
                )
            else:
                self.bridge_status.set(
                    "Connect console to Windows Mobile Hotspot. See its name and "
                    "password in Windows Settings. Leave console proxy settings off."
                )
        else:
            self.bridge_status.set(
                f"Connect console by cable to '{details.output_name}'. "
                "Use automatic IP and DNS; leave console proxy settings off."
            )

    def _do_bridge_stop(self):
        try:
            with self._bridge_lock:
                self.ics_mgr.stop()
            self.root.after(0, lambda: self.bridge_status.set("Console sharing stopped."))
        except Exception as error:
            self.log(f"Could not stop console sharing: {error}")
            self.root.after(0, lambda: self.bridge_status.set(f"Cleanup error: {error}"))
        finally:
            self._bridge_busy = False
            self.root.after(0, self._update_bridge_button)

    def _on_tunnel_lost(self):
        with self._bridge_lock:
            if self.ics_mgr.needs_cleanup:
                self.ics_mgr.stop()
                self.log("Console sharing stopped because the VPN tunnel was lost.")
                self.root.after(
                    0, lambda: self.bridge_status.set(
                        "VPN lost. Console sharing stopped; reconnect VPN, then restart sharing."
                    )
                )
        self.root.after(0, self._update_bridge_button)

    def start(self):
        ip = self.phone_ip.get().strip()
        try:
            p_port = int(self.phone_port.get().strip())
            l_port = int(self.local_port.get().strip())
        except ValueError:
            messagebox.showerror("Invalid Input", "Port must be a number.")
            return

        self.btn_start.configure(
            state="disabled", fg_color=self.BORDER,
            text_color_disabled=self.MUTED
        )
        self.btn_stop.configure(state="normal")
        for field in (self.phone_ip, self.phone_port, self.local_port):
            field.configure(state="disabled")
        self._set_status("Status: Starting...", "starting")
        self.progress.start()
        threading.Thread(target=self._do_start, args=(ip, p_port, l_port), daemon=True).start()

    def _do_start(self, ip, p_port, l_port):
        try:
            self.log(f"Starting Global VPN to {ip}:{p_port}")
            self.tun_mgr = TunManager(
                ip, p_port, l_port, self.log, on_tunnel_lost=self._on_tunnel_lost
            )
            self.tun_mgr.start()
            self.log("VPN active.")
            self.root.after(0, lambda: self._set_status("Status: Connected", "connected"))
            self.root.after(0, self.progress.stop)
            self.root.after(0, self._update_bridge_button)
            self.root.after(0, lambda: self._bridge_mode_changed(self.bridge_mode.get()))
            self.root.after(0, self._refresh_ethernet)
            threading.Thread(target=self._health_status_updater, daemon=True).start()
        except Exception as error:
            self.log(f"VPN start error: {error}")
            cleaned = False
            try:
                cleaned = self._do_stop(is_closing=True)
            except Exception as cleanup_error:
                self.log(f"VPN cleanup failed: {cleanup_error}")
            def show_error():
                self.progress.stop()
                self._set_status("Connection failed" if cleaned else "Cleanup failed", "error")
                self.btn_start.configure(
                    state="normal" if cleaned else "disabled",
                    fg_color=self.ACCENT if cleaned else self.BORDER
                )
                self.btn_stop.configure(state="disabled" if cleaned else "normal")
                for field in (self.phone_ip, self.phone_port, self.local_port):
                    field.configure(state="normal" if cleaned else "disabled")
                self._update_bridge_button()
            self.root.after(0, show_error)

    def _health_status_updater(self):
        while self.tun_mgr and self.tun_mgr._monitoring_active:
            time.sleep(2)
            if not self.tun_mgr or not self.tun_mgr._monitoring_active:
                break
            if self.tun_mgr.is_healthy():
                self.root.after(0, lambda: self._set_status("Status: Connected", "connected"))
            else:
                self.root.after(0, lambda: self._set_status("Status: Reconnecting...", "stopping"))
            self.root.after(0, self._update_bridge_button)
        if self.tun_mgr:
            self.root.after(0, lambda: self._set_status("Status: VPN failed", "error"))
            self.root.after(0, self._update_bridge_button)

    def stop(self):
        self._set_status("Status: Stopping...", "stopping")
        self.progress.start()
        threading.Thread(target=self._do_stop, daemon=True).start()

    def _do_stop(self, is_closing=False):
        try:
            self._on_tunnel_lost()
        except BridgeError as error:
            self.log(f"Stop sharing first: {error}")
            if not is_closing:
                self.root.after(0, self.progress.stop)
                self.root.after(
                    0, lambda: self._set_status("Status: Console sharing cleanup failed", "error")
                )
            return False

        if self.tun_mgr:
            self.tun_mgr.stop()
            self.tun_mgr = None
            self.log("VPN stopped.")

        if not is_closing:
            self.root.after(0, self.progress.stop)
            self.root.after(0, lambda: self._set_status("Status: Disconnected", "disconnected"))
            self.root.after(0, lambda: self.btn_start.configure(
                state="normal", fg_color=self.ACCENT
            ))
            self.root.after(0, lambda: self.btn_stop.configure(state="disabled"))
            self.root.after(0, lambda: [field.configure(state="normal") for field in (
                self.phone_ip, self.phone_port, self.local_port
            )])
            self.root.after(0, self._update_bridge_button)
            self.root.after(0, lambda: self.bridge_status.set("Connect the phone in Step 1 first."))
            self.log("Stopped.")
        return True

def main():
    ctk.set_appearance_mode("system")
    ctk.set_default_color_theme("dark-blue")

    root = ctk.CTk()
    app = App(root)

    def on_closing():
        if app.tun_mgr or app.ics_mgr.needs_cleanup:
            print("Cleaning up before exit...")
            if not app._do_stop(is_closing=True):
                messagebox.showerror(
                    "Console sharing",
                    "Could not restore Windows sharing settings. Stop console sharing before closing."
                )
                return
        root.destroy()

    root.protocol("WM_DELETE_WINDOW", on_closing)
    root.mainloop()

if __name__ == "__main__":
    if IS_WINDOWS:
        try:
            if not ctypes.windll.shell32.IsUserAnAdmin():
                # Relaunch as admin. REQUIRED for TUN Mode network routing.
                ctypes.windll.shell32.ShellExecuteW(None, "runas", sys.executable, " ".join(sys.argv), None, 1)
                sys.exit(0)
        except Exception: pass

    main()