"""tun2socks + Wintun virtual adapter that routes all Windows traffic to the phone."""
import io
import os
import platform
import socket
import subprocess
import sys
import threading
import time
import urllib.request
import zipfile

from http_bridge import HttpSocksBridge

IS_WINDOWS = sys.platform == "win32"

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
