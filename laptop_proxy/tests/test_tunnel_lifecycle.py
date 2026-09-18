import importlib
from pathlib import Path
import sys
import threading

import pytest

pytest.importorskip("customtkinter")
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
main = importlib.import_module("main")


def test_first_failed_proxy_probe_marks_tunnel_unhealthy_and_stops_sharing(monkeypatch):
    events = []
    tunnel = main.TunManager("192.168.49.1", 8080, 8081, lambda message: None,
                             on_tunnel_lost=lambda: events.append("lost"))
    tunnel._monitoring_active = True
    tunnel._connection_healthy = True
    sleeps = 0

    def sleep(_duration):
        nonlocal sleeps
        sleeps += 1
        if sleeps > 1:
            tunnel._monitoring_active = False

    def disconnected(*_args, **_kwargs):
        raise OSError("proxy unreachable")

    monkeypatch.setattr(main.time, "sleep", sleep)
    monkeypatch.setattr(main.socket, "create_connection", disconnected)
    tunnel._monitor_health()
    assert events == ["lost"]
    assert not tunnel._connection_healthy


def test_tunnel_loss_callback_stops_active_console_sharing():
    class FakeSharing:
        needs_cleanup = True
        stopped = False

        def stop(self):
            self.stopped = True
            self.needs_cleanup = False

    class FakeRoot:
        def after(self, _delay, callback):
            callback()

    class FakeStatus:
        value = None

        def set(self, value):
            self.value = value

    app = main.App.__new__(main.App)
    app.ics_mgr = FakeSharing()
    app._bridge_lock = threading.RLock()
    app.root = FakeRoot()
    app.bridge_status = FakeStatus()
    app.log = lambda _message: None
    app._update_bridge_button = lambda: None
    app._on_tunnel_lost()
    assert app.ics_mgr.stopped
    assert "VPN lost" in app.bridge_status.value
