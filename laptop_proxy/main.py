import customtkinter as ctk
import tkinter as tk
from tkinter import messagebox
import threading
import sys
import time
from console_bridge import BridgeError, IcsManager
from tunnel import IS_WINDOWS, TunManager

if IS_WINDOWS:
    import ctypes


class App:
    # Minimalist, flat palette shared with the Android app: near-black
    # background, one cyan accent, thin dividers, no cards or gradients.
    BG = "#080C0E"
    SURFACE = "#0F1619"
    TEXT = "#F4F7F7"
    MUTED = "#8A9499"
    SUBTLE = "#5F696E"
    LABEL = "#9BA6AB"
    DIVIDER = "#232B30"
    INPUT_BORDER = "#30383D"
    INPUT_BG = "#0B1114"
    HOVER = "#12191C"
    ACCENT = "#25C7D3"
    ACCENT_HOVER = "#58DBE3"
    ON_ACCENT = "#041012"
    DISABLED = "#1A2226"
    OUTLINE = "#465055"
    OK = "#2FD49A"
    BUSY = "#F2B84B"
    FAIL = "#FF6B6B"
    IDLE = "#5F696E"

    FONT = "Segoe UI"
    PAD = 24

    def __init__(self, root):
        self.root = root
        self.root.title("Bypass VPN · Windows")
        self.root.geometry(f"470x{min(780, max(640, root.winfo_screenheight() - 90))}")
        self.root.minsize(430, 600)
        self.root.configure(fg_color=self.BG)

        self.tun_mgr = None
        self.ics_mgr = IcsManager(self.log)
        self._bridge_lock = threading.RLock()
        self._bridge_busy = False
        self._ethernet_choices = {}
        self._activity_open = False
        self._advanced_open = False

        self.main = ctk.CTkScrollableFrame(
            root, fg_color=self.BG, corner_radius=0,
            scrollbar_button_color=self.DIVIDER, scrollbar_button_hover_color=self.INPUT_BORDER
        )
        self.main.pack(fill="both", expand=True)
        self.content = ctk.CTkFrame(self.main, fg_color="transparent")
        self.content.pack(fill="both", expand=True, padx=self.PAD - 6, pady=(18, 24))
        self.content.grid_columnconfigure(0, weight=1)

        # --- Header -------------------------------------------------------
        header = ctk.CTkFrame(self.content, fg_color="transparent")
        header.pack(fill="x")
        header.grid_columnconfigure(1, weight=1)
        ctk.CTkLabel(
            header, text="Bypass VPN", font=(self.FONT, 19, "bold"),
            text_color=self.ACCENT, anchor="w"
        ).grid(row=0, column=0, sticky="w")
        ctk.CTkLabel(
            header, text="  Windows", font=(self.FONT, 13),
            text_color=self.SUBTLE, anchor="w"
        ).grid(row=0, column=1, sticky="w", pady=(3, 0))
        self.header_status = ctk.CTkLabel(
            header, text="", font=(self.FONT, 11, "bold"), text_color="#9EE8EC", anchor="e"
        )
        self.header_status.grid(row=0, column=2, sticky="e")

        # --- Step 1: connect to the phone ---------------------------------
        vpn_section = self._section(top_rule=False)
        vpn_section.grid_columnconfigure(0, weight=1)
        self._eyebrow(vpn_section, "STEP 1 · VPN").grid(row=0, column=0, sticky="w")
        self._title(vpn_section, "Connect to your phone").grid(row=1, column=0, sticky="w")
        self._lede(
            vpn_section,
            "Start Share on the Android phone, join its Wi-Fi Direct network, then connect."
        ).grid(row=2, column=0, sticky="w", pady=(4, 18))

        fields = ctk.CTkFrame(vpn_section, fg_color="transparent")
        fields.grid(row=3, column=0, sticky="ew")
        fields.grid_columnconfigure(0, weight=3)
        fields.grid_columnconfigure(1, weight=2)
        self._field_label(fields, "Phone IP address").grid(row=0, column=0, sticky="w", pady=(0, 6))
        self._field_label(fields, "Port").grid(row=0, column=1, sticky="w", padx=(12, 0), pady=(0, 6))
        self.phone_ip = self._entry(fields)
        self.phone_ip.insert(0, "192.168.49.1")
        self.phone_ip.grid(row=1, column=0, sticky="ew")
        self.phone_port = self._entry(fields)
        self.phone_port.insert(0, "8080")
        self.phone_port.grid(row=1, column=1, sticky="ew", padx=(12, 0))

        self.advanced_button = self._link(vpn_section, "Advanced  ⌄", self._toggle_advanced)
        self.advanced_button.grid(row=4, column=0, sticky="w", pady=(10, 0))
        self.advanced_frame = ctk.CTkFrame(vpn_section, fg_color="transparent")
        self.advanced_frame.grid_columnconfigure(0, weight=1)
        self._field_label(self.advanced_frame, "Local download bridge port").grid(
            row=0, column=0, sticky="w", pady=(6, 6)
        )
        self.local_port = self._entry(self.advanced_frame)
        self.local_port.insert(0, "7890")
        self.local_port.grid(row=1, column=0, sticky="ew")
        self._lede(
            self.advanced_frame,
            "Used once to download tun2socks and Wintun through the phone.", size=11
        ).grid(row=2, column=0, sticky="w", pady=(6, 0))

        status_row = ctk.CTkFrame(vpn_section, fg_color="transparent")
        status_row.grid(row=6, column=0, sticky="ew", pady=(18, 0))
        status_row.grid_columnconfigure(1, weight=1)
        self.status_dot = ctk.CTkFrame(status_row, width=9, height=9, corner_radius=5, fg_color=self.IDLE)
        self.status_dot.grid(row=0, column=0, padx=(1, 10), pady=(6, 0), sticky="n")
        self.status_var = ctk.StringVar(value="Disconnected")
        self.status_label = ctk.CTkLabel(
            status_row, textvariable=self.status_var, font=(self.FONT, 13, "bold"),
            text_color=self.TEXT, anchor="w", height=20
        )
        self.status_label.grid(row=0, column=1, sticky="w")
        self.status_detail = ctk.StringVar(value="Enter the phone's IP address, then connect.")
        ctk.CTkLabel(
            status_row, textvariable=self.status_detail, font=(self.FONT, 11),
            text_color=self.MUTED, anchor="w", justify="left", wraplength=380
        ).grid(row=1, column=1, sticky="w")
        self.progress = ctk.CTkProgressBar(
            vpn_section, mode="indeterminate", height=3, corner_radius=2,
            fg_color=self.DIVIDER, progress_color=self.ACCENT
        )
        self.progress.set(0)

        vpn_actions = ctk.CTkFrame(vpn_section, fg_color="transparent")
        vpn_actions.grid(row=8, column=0, sticky="ew", pady=(16, 0))
        vpn_actions.grid_columnconfigure(0, weight=3)
        vpn_actions.grid_columnconfigure(1, weight=2)
        self.btn_start = self._primary_button(vpn_actions, "Connect VPN", self.start)
        self.btn_start.grid(row=0, column=0, sticky="ew", padx=(0, 10))
        self.btn_stop = self._secondary_button(vpn_actions, "Disconnect", self.stop)
        self.btn_stop.configure(state="disabled")
        self.btn_stop.grid(row=0, column=1, sticky="ew")

        # --- Step 2: share with consoles ----------------------------------
        self.bridge_card = self._section()
        self.bridge_card.grid_columnconfigure(0, weight=1)
        self.bridge_eyebrow = self._eyebrow(self.bridge_card, "STEP 2 · OPTIONAL")
        self.bridge_eyebrow.grid(row=0, column=0, sticky="w")
        self.bridge_title = self._title(self.bridge_card, "Share with consoles & TVs")
        self.bridge_title.grid(row=1, column=0, sticky="w")
        self.bridge_subtitle = self._lede(
            self.bridge_card, "Devices join this PC instead. No proxy settings needed on them."
        )
        self.bridge_subtitle.grid(row=2, column=0, sticky="w", pady=(4, 16))
        self.bridge_mode = ctk.CTkSegmentedButton(
            self.bridge_card, values=["Wi-Fi Hotspot", "Ethernet Cable"],
            command=self._bridge_mode_changed, height=38, corner_radius=19,
            fg_color=self.INPUT_BORDER, border_width=1,
            selected_color="#16363A", selected_hover_color="#1C4247",
            unselected_color=self.BG, unselected_hover_color=self.HOVER,
            text_color=self.TEXT, text_color_disabled=self.SUBTLE, font=(self.FONT, 12, "bold")
        )
        self.bridge_mode.grid(row=3, column=0, sticky="ew")
        self.bridge_mode.set("Wi-Fi Hotspot")

        self.wifi_row = ctk.CTkFrame(self.bridge_card, fg_color="transparent")
        self.wifi_row.grid(row=4, column=0, sticky="ew", pady=(14, 0))
        self.wifi_row.grid_columnconfigure(0, weight=1)
        self.hotspot_ssid_var = ctk.StringVar(value="Shown after sharing starts")
        self.hotspot_password_var = ctk.StringVar(value="Shown after sharing starts")
        self.hotspot_ssid = self._detail_row(self.wifi_row, 0, "Hotspot name", self.hotspot_ssid_var)
        self.hotspot_password = self._detail_row(self.wifi_row, 1, "Password", self.hotspot_password_var)

        self.ethernet_row = ctk.CTkFrame(self.bridge_card, fg_color="transparent")
        self.ethernet_row.grid(row=4, column=0, sticky="ew", pady=(14, 0))
        self.ethernet_row.grid_columnconfigure(0, weight=1)
        self._field_label(self.ethernet_row, "Ethernet adapter").grid(
            row=0, column=0, columnspan=2, sticky="w", pady=(0, 6)
        )
        self.ethernet_choice = ctk.CTkComboBox(
            self.ethernet_row, values=["Connect a cable, then refresh"], height=38,
            corner_radius=6, state="readonly", fg_color=self.INPUT_BG,
            border_color=self.INPUT_BORDER, border_width=1, button_color=self.INPUT_BORDER,
            button_hover_color=self.OUTLINE, dropdown_fg_color=self.SURFACE,
            dropdown_hover_color=self.HOVER, text_color=self.TEXT, font=(self.FONT, 12)
        )
        self.ethernet_choice.grid(row=1, column=0, sticky="ew", padx=(0, 10))
        self.ethernet_choice.set("Connect a cable, then refresh")
        self.refresh_ethernet_button = self._secondary_button(
            self.ethernet_row, "Refresh", self._refresh_ethernet, width=84
        )
        self.refresh_ethernet_button.grid(row=1, column=1)
        self.ethernet_row.grid_remove()

        bridge_status_row = ctk.CTkFrame(self.bridge_card, fg_color="transparent")
        bridge_status_row.grid(row=5, column=0, sticky="ew", pady=(18, 0))
        bridge_status_row.grid_columnconfigure(1, weight=1)
        self.bridge_status_dot = ctk.CTkFrame(
            bridge_status_row, width=9, height=9, corner_radius=5, fg_color=self.IDLE
        )
        self.bridge_status_dot.grid(row=0, column=0, padx=(1, 10), pady=(6, 0), sticky="n")
        self.bridge_status_title = ctk.StringVar(value="Waiting for VPN")
        ctk.CTkLabel(
            bridge_status_row, textvariable=self.bridge_status_title,
            font=(self.FONT, 13, "bold"), text_color=self.TEXT, anchor="w", height=20
        ).grid(row=0, column=1, sticky="w")
        self.bridge_status = ctk.StringVar(value="Connect to your phone in step 1 first.")
        self.bridge_help = ctk.CTkLabel(
            bridge_status_row, textvariable=self.bridge_status, wraplength=380,
            justify="left", anchor="w", font=(self.FONT, 11), text_color=self.MUTED
        )
        self.bridge_help.grid(row=1, column=1, sticky="w")
        self.bridge_status.trace_add("write", self._bridge_message_changed)

        self.bridge_button = self._primary_button(
            self.bridge_card, "Start sharing", self._on_share_button
        )
        self.bridge_button.configure(state="disabled")
        self.bridge_button.grid(row=6, column=0, sticky="ew", pady=(16, 0))

        # --- Activity log -------------------------------------------------
        activity = self._section(pady=(22, 0))
        activity.grid_columnconfigure(0, weight=1)
        self.activity_button = self._link(activity, "Activity log  ⌄", self._toggle_activity, size=13)
        self.activity_button.grid(row=0, column=0, sticky="w")
        self.activity_body = ctk.CTkFrame(activity, fg_color="transparent")
        self.activity_body.grid_columnconfigure(0, weight=1)
        self.log_text = ctk.CTkTextbox(
            self.activity_body, font=("Consolas", 11), height=150, wrap="word",
            fg_color=self.INPUT_BG, border_color=self.DIVIDER, border_width=1,
            corner_radius=6, text_color="#9AA5AA",
            scrollbar_button_color=self.DIVIDER
        )
        self.log_text.grid(row=0, column=0, sticky="ew", pady=(8, 0))
        self.log_text._textbox.tag_configure("error", foreground=self.FAIL)
        self.log_text._textbox.tag_configure("success", foreground=self.OK)
        self.log_text.configure(state="disabled")
        self._link(self.activity_body, "Clear", self._clear_log, size=11, color=self.MUTED).grid(
            row=1, column=0, sticky="e", pady=(4, 0)
        )

        self._update_bridge_button()
        threading.Thread(target=self._load_hotspot_info, daemon=True).start()

    # --- Widget helpers ------------------------------------------------------

    def _section(self, top_rule=True, pady=(22, 0)):
        if top_rule:
            self._rule(self.content).pack(fill="x", pady=(pady[0], 0))
        frame = ctk.CTkFrame(self.content, fg_color="transparent")
        frame.pack(fill="x", pady=(18 if top_rule else 26, pady[1]))
        return frame

    def _eyebrow(self, parent, text):
        return ctk.CTkLabel(parent, text=text, font=(self.FONT, 10, "bold"), text_color=self.LABEL,
                            anchor="w", height=16)

    def _title(self, parent, text):
        return ctk.CTkLabel(parent, text=text, font=(self.FONT, 22, "bold"), text_color=self.TEXT,
                            anchor="w", height=32)

    def _lede(self, parent, text, size=12):
        return ctk.CTkLabel(parent, text=text, font=(self.FONT, size), text_color=self.MUTED,
                            anchor="w", justify="left", wraplength=400)

    def _field_label(self, parent, text):
        return ctk.CTkLabel(parent, text=text, font=(self.FONT, 11, "bold"), text_color="#AEB6B9",
                            anchor="w", height=16)

    def _entry(self, parent):
        return ctk.CTkEntry(
            parent, height=38, corner_radius=6, border_width=1, fg_color=self.INPUT_BG,
            border_color=self.INPUT_BORDER, text_color=self.TEXT, font=(self.FONT, 13)
        )

    def _primary_button(self, parent, text, command):
        return ctk.CTkButton(
            parent, text=text, height=42, corner_radius=6, command=command,
            fg_color=self.ACCENT, hover_color=self.ACCENT_HOVER, text_color=self.ON_ACCENT,
            text_color_disabled=self.SUBTLE, font=(self.FONT, 13, "bold")
        )

    def _secondary_button(self, parent, text, command, width=140):
        return ctk.CTkButton(
            parent, text=text, height=42, width=width, corner_radius=6, command=command,
            fg_color="transparent", hover_color=self.HOVER, border_width=1,
            border_color=self.OUTLINE, text_color=self.TEXT, text_color_disabled=self.SUBTLE,
            font=(self.FONT, 13)
        )

    def _rule(self, parent):
        """1px divider. A plain Tk frame: CTkFrame does not draw at 1px height."""
        return tk.Frame(parent, height=1, bg=self.DIVIDER, highlightthickness=0, bd=0)

    def _link(self, parent, text, command, size=12, color=None):
        """Text-only action aligned flush with surrounding labels."""
        color = color or self.ACCENT
        link = ctk.CTkLabel(
            parent, text=text, text_color=color, font=(self.FONT, size, "bold"),
            anchor="w", height=26, cursor="hand2"
        )
        link.bind("<Button-1>", lambda _event: command())
        link.bind("<Enter>", lambda _event: link.configure(text_color=self.ACCENT_HOVER))
        link.bind("<Leave>", lambda _event: link.configure(text_color=color))
        return link

    def _detail_row(self, parent, row, label, variable):
        """A list row (label over value) with a copy action; returns the value label."""
        frame = ctk.CTkFrame(parent, fg_color="transparent")
        frame.grid(row=row, column=0, sticky="ew")
        frame.grid_columnconfigure(0, weight=1)
        ctk.CTkLabel(frame, text=label.upper(), font=(self.FONT, 10, "bold"), text_color=self.LABEL,
                     anchor="w", height=16).grid(row=0, column=0, sticky="w", pady=(8, 0))
        value = ctk.CTkLabel(
            frame, textvariable=variable, height=24, text_color=self.TEXT,
            font=(self.FONT, 14), anchor="w"
        )
        value.grid(row=1, column=0, sticky="ew", padx=(0, 8))
        self._link(frame, "Copy", lambda: self._copy(label, variable.get()), size=11).grid(
            row=0, column=1, rowspan=2, sticky="e"
        )
        self._rule(frame).grid(row=2, column=0, columnspan=2, sticky="ew", pady=(6, 0))
        return value

    def _copy(self, label, value):
        if not value or value.startswith("Shown after"):
            return
        self.root.clipboard_clear()
        self.root.clipboard_append(value)
        self.log(f"{label} copied to clipboard.")

    def _set_button(self, button, enabled, text=None, primary=True):
        """Enable/disable a button and keep its colours consistent with the palette."""
        options = {"state": "normal" if enabled else "disabled"}
        if text is not None:
            options["text"] = text
        if primary:
            options["fg_color"] = self.ACCENT if enabled else self.DISABLED
        button.configure(**options)

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
            title, color = "Sharing needs attention", self.FAIL
        elif message.startswith("Connect console"):
            title, color = "Sharing is on", self.OK
        elif message.startswith(("Configuring", "Stopping")):
            title, color = "Updating sharing", self.BUSY
        elif message.startswith(("Windows will", "Connect a cable")):
            title, color = "Ready to share", self.ACCENT
        elif message.startswith("Console sharing stopped"):
            title, color = "Sharing is off", self.IDLE
        else:
            title, color = "Waiting for VPN", self.IDLE
        self.bridge_status_title.set(title)
        self.bridge_status_dot.configure(fg_color=color)
        self.bridge_help.configure(text_color=self.MUTED)

    def _toggle_advanced(self):
        self._advanced_open = not self._advanced_open
        if self._advanced_open:
            self.advanced_frame.grid(row=5, column=0, sticky="ew", pady=(4, 0))
            self.advanced_button.configure(text="Advanced  ⌃")
        else:
            self.advanced_frame.grid_remove()
            self.advanced_button.configure(text="Advanced  ⌄")

    def _toggle_activity(self, show=None):
        self._activity_open = (not self._activity_open) if show is None else show
        if self._activity_open:
            self.activity_body.grid(row=1, column=0, sticky="ew")
            self.activity_button.configure(text="Activity log  ⌃")
        else:
            self.activity_body.grid_remove()
            self.activity_button.configure(text="Activity log  ⌄")

    def _set_status(self, text, state="disconnected"):
        colors = {
            "connected": self.OK,
            "starting": self.BUSY,
            "stopping": self.BUSY,
            "disconnected": self.IDLE,
            "error": self.FAIL,
        }
        details = {
            "connected": "All Windows traffic now goes through the phone. You can share it below.",
            "starting": "Creating the VPN adapter and applying routes…",
            "stopping": "Closing the tunnel and restoring network settings…",
            "disconnected": "Enter the phone's IP address, then connect.",
            "error": "See the activity log for the cause, then try again.",
        }
        self.status_var.set(text.removeprefix("Status: "))
        self.status_dot.configure(fg_color=colors.get(state, self.IDLE))
        self.status_detail.set(details.get(state, ""))
        self.header_status.configure(text="● VPN connected" if state == "connected" else "")
        if state in ("starting", "stopping"):
            self.progress.grid(row=7, column=0, sticky="ew", pady=(14, 0))
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
        except Exception:  # window already destroyed (e.g. during shutdown)
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
                self.bridge_status.set("Connect to your phone in step 1 first.")
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
            self.root.after(0, lambda message=str(error): self.bridge_status.set(f"Error: {message}"))
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
        self.bridge_eyebrow.configure(text_color=self.LABEL if healthy else self.SUBTLE)
        self.bridge_title.configure(text_color=self.TEXT if healthy else self.MUTED)
        self.bridge_subtitle.configure(text_color=self.MUTED if healthy else self.SUBTLE)
        if self._bridge_busy:
            self._set_button(self.bridge_button, False, "Working…")
        elif self.ics_mgr.needs_cleanup:
            self._set_button(self.bridge_button, True, "Stop sharing")
        else:
            self._set_button(self.bridge_button, healthy, "Start sharing")

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
            self.root.after(0, lambda message=str(error): self.bridge_status.set(f"Error: {message}"))
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
            self.root.after(0, lambda message=str(error): self.bridge_status.set(f"Cleanup error: {message}"))
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

        self._set_button(self.btn_start, False)
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
                self._set_button(self.btn_start, cleaned)
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
            self.root.after(0, lambda: self._set_button(self.btn_start, True))
            self.root.after(0, lambda: self.btn_stop.configure(state="disabled"))
            self.root.after(0, lambda: [field.configure(state="normal") for field in (
                self.phone_ip, self.phone_port, self.local_port
            )])
            self.root.after(0, self._update_bridge_button)
            self.root.after(0, lambda: self.bridge_status.set("Connect to your phone in step 1 first."))
            self.log("Stopped.")
        return True

def main():
    ctk.set_appearance_mode("dark")
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