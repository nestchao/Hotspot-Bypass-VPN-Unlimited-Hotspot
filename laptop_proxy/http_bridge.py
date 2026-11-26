"""Local HTTP proxy that forwards to the phone's SOCKS5 proxy.

Used while downloading tun2socks/Wintun, before the VPN adapter exists.
"""
import select
import socket
import struct
import threading

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
            except OSError:
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
            except OSError: pass

    def _socks5_connect(self, host, port):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.settimeout(5)
            s.connect((self.socks_host, self.socks_port))
            s.sendall(b"\x05\x01\x00")
            if s.recv(2)[1] != 0x00: return None
            
            # Request connection
            try:
                ipv4 = socket.inet_aton(host)
            except OSError:
                ipv4 = None
            if ipv4 is not None:
                s.sendall(b"\x05\x01\x00\x01" + ipv4 + struct.pack(">H", port))
            else:
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
                except OSError: pass
