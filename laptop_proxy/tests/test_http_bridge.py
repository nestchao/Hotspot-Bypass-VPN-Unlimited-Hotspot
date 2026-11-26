import socket
import threading

from http_bridge import HttpSocksBridge


class FakeSocksServer:
    """Accepts one SOCKS5 CONNECT, records the request bytes and replies success."""

    def __init__(self):
        self.server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server.bind(("127.0.0.1", 0))
        self.server.listen(1)
        self.port = self.server.getsockname()[1]
        self.request = None
        self.thread = threading.Thread(target=self._serve, daemon=True)
        self.thread.start()

    def _recv_exact(self, conn, size):
        data = b""
        while len(data) < size:
            chunk = conn.recv(size - len(data))
            if not chunk:
                raise EOFError
            data += chunk
        return data

    def _serve(self):
        conn, _ = self.server.accept()
        with conn:
            self._recv_exact(conn, 3)  # greeting
            conn.sendall(b"\x05\x00")
            head = self._recv_exact(conn, 4)
            if head[3] == 1:
                rest = self._recv_exact(conn, 4 + 2)
            else:
                length = self._recv_exact(conn, 1)
                rest = length + self._recv_exact(conn, length[0] + 2)
            self.request = head + rest
            conn.sendall(b"\x05\x00\x00\x01" + b"\x00" * 6)

    def close(self):
        self.thread.join(timeout=2)
        self.server.close()


def test_ipv4_target_uses_address_type_1():
    fake = FakeSocksServer()
    bridge = HttpSocksBridge(0, "127.0.0.1", fake.port, log_fn=lambda _m: None)
    sock = bridge._socks5_connect("93.184.216.34", 443)
    assert sock is not None
    sock.close()
    fake.close()
    assert fake.request == b"\x05\x01\x00\x01" + bytes([93, 184, 216, 34]) + (443).to_bytes(2, "big")


def test_hostname_target_uses_address_type_3():
    fake = FakeSocksServer()
    bridge = HttpSocksBridge(0, "127.0.0.1", fake.port, log_fn=lambda _m: None)
    sock = bridge._socks5_connect("github.com", 443)
    assert sock is not None
    sock.close()
    fake.close()
    assert fake.request == b"\x05\x01\x00\x03" + bytes([10]) + b"github.com" + (443).to_bytes(2, "big")


def test_unreachable_proxy_returns_none():
    unused = socket.socket()
    unused.bind(("127.0.0.1", 0))
    port = unused.getsockname()[1]
    unused.close()
    bridge = HttpSocksBridge(0, "127.0.0.1", port, log_fn=lambda _m: None)
    assert bridge._socks5_connect("github.com", 443) is None
