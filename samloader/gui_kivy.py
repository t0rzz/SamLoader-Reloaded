# SPDX-License-Identifier: GPL-3.0+
# Kivy GUI for SamLoader Reloaded
# Provides operations: Check Update, Download, Decrypt

import os
import threading
import time
from dataclasses import dataclass
from typing import Optional, Dict, List

# Kivy imports
from kivy.app import App
from kivy.clock import Clock
from kivy.properties import StringProperty, BooleanProperty, NumericProperty
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.textinput import TextInput
from kivy.uix.button import Button
from kivy.uix.progressbar import ProgressBar
from kivy.uix.tabbedpanel import TabbedPanel, TabbedPanelItem
from kivy.uix.popup import Popup
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.filechooser import FileChooserListView
from kivy.uix.recycleview import RecycleView
from kivy.uix.gridlayout import GridLayout

# Dual import support (package/standalone)
try:
    from . import versionfetch
    from . import fusclient
    from . import crypt
    from . import imei
    from . import __version__ as VERSION
    from .regions import get_regions as get_csc_regions
    from .main import getbinaryfile, initdownload, decrypt_file
except Exception:  # pragma: no cover
    import samloader.versionfetch as versionfetch
    import samloader.fusclient as fusclient
    import samloader.crypt as crypt
    import samloader.imei as imei
    try:
        from samloader import __version__ as VERSION
    except Exception:
        VERSION = "?"
    from samloader.regions import get_regions as get_csc_regions
    from samloader.main import getbinaryfile, initdownload, decrypt_file


@dataclass
class ArgsLike:
    dev_model: str
    dev_region: str
    dev_imei: Optional[str]
    command: str
    enc_ver: int = 4
    fw_ver: Optional[str] = None


class FileChooserPopup(Popup):
    def __init__(self, title: str, select_dir=False, initial_path: Optional[str] = None, on_select=None, **kwargs):
        super().__init__(title=title, size_hint=(0.9, 0.9), **kwargs)
        layout = BoxLayout(orientation='vertical')
        self.on_select = on_select
        self.select_dir = select_dir
        chooser = FileChooserListView(path=initial_path or os.getcwd(), dirselect=select_dir)
        self.chooser = chooser
        layout.add_widget(chooser)
        buttons = BoxLayout(size_hint_y=None, height='40dp')
        btn_ok = Button(text='Select')
        btn_cancel = Button(text='Cancel')
        btn_ok.bind(on_release=self._do_select)
        btn_cancel.bind(on_release=lambda *_: self.dismiss())
        buttons.add_widget(btn_cancel)
        buttons.add_widget(btn_ok)
        layout.add_widget(buttons)
        self.content = layout

    def _do_select(self, *_):
        sel = None
        if self.select_dir:
            sel = self.chooser.path
        else:
            if self.chooser.selection:
                sel = self.chooser.selection[0]
        if sel and self.on_select:
            self.on_select(sel)
        self.dismiss()


class RegionsPicker(Popup):
    def __init__(self, regions_map: Dict[str, str], on_pick, **kwargs):
        super().__init__(title="Select Region (CSC)", size_hint=(0.9, 0.9), **kwargs)
        self.on_pick = on_pick
        self.regions_map = regions_map or {}
        self.codes: List[str] = sorted(list(self.regions_map.keys()))
        root = BoxLayout(orientation='vertical', spacing=6, padding=6)
        # Search field
        search_box = BoxLayout(size_hint_y=None, height='36dp', spacing=6)
        search_box.add_widget(Label(text='Search:', size_hint_x=None, width='80dp'))
        self.ed_search = TextInput(multiline=False)
        self.ed_search.bind(text=self._on_search)
        search_box.add_widget(self.ed_search)
        root.add_widget(search_box)
        # List area
        self.rv = RecycleView()
        self.rv.viewclass = 'Label'
        self.rv.data = self._build_data(self.codes)
        root.add_widget(self.rv)
        # Buttons
        btns = BoxLayout(size_hint_y=None, height='40dp', spacing=6)
        btn_cancel = Button(text='Cancel')
        btn_ok = Button(text='OK')
        btn_cancel.bind(on_release=lambda *_: self.dismiss())
        btn_ok.bind(on_release=self._accept)
        btns.add_widget(btn_cancel)
        btns.add_widget(btn_ok)
        root.add_widget(btns)
        self.content = root

    def _build_data(self, codes: List[str]):
        data = []
        for c in codes:
            name = self.regions_map.get(c, '')
            data.append({'text': f"{c} ({name})", 'size_hint_y': None, 'height': 28})
        return data

    def _on_search(self, *_):
        q = (self.ed_search.text or '').strip()
        if not q:
            items = self.codes
        else:
            ql = q.lower()
            fu = q.upper()
            items = [c for c in self.codes if c.startswith(fu)]
            # name matches
            name_hits = [c for c, nm in self.regions_map.items() if ql in nm.lower()]
            for c in name_hits:
                if c not in items:
                    items.append(c)
        self.rv.data = self._build_data(items)

    def _accept(self, *_):
        # Simple accept: take first visible item if any; instruct user to refine search
        if self.rv.data:
            text = self.rv.data[0]['text']
            code = text.split()[0]
            if self.on_pick:
                self.on_pick(code)
        self.dismiss()


class DeviceRow(GridLayout):
    model = StringProperty("")
    region = StringProperty("")
    imei = StringProperty("")

    def __init__(self, regions_map: Dict[str, str], **kwargs):
        super().__init__(cols=9, spacing=6, size_hint_y=None, height='34dp', **kwargs)
        self.regions_map = regions_map
        # Model field + info
        self.add_widget(Label(text='Model', size_hint_x=None, width='70dp'))
        self.ed_model = TextInput(text='', multiline=False, hint_text='e.g., SM-S918B')
        self.add_widget(self.ed_model)
        self.btn_model_info = Button(text='ⓘ', size_hint_x=None, width='40dp')
        self.btn_model_info.bind(on_release=self._show_model_info)
        self.add_widget(self.btn_model_info)
        # Region + info + browse
        self.add_widget(Label(text='Region', size_hint_x=None, width='70dp'))
        self.ed_region = TextInput(text='', multiline=False, hint_text='e.g., INS/BTU/ITV')
        self.add_widget(self.ed_region)
        self.btn_region_info = Button(text='ⓘ', size_hint_x=None, width='40dp')
        self.btn_region_info.bind(on_release=self._show_region_info)
        self.add_widget(self.btn_region_info)
        self.btn_region_browse = Button(text='Browse…', size_hint_x=None, width='100dp')
        self.btn_region_browse.bind(on_release=self._browse_region)
        self.add_widget(self.btn_region_browse)
        # IMEI/serial + info
        self.add_widget(Label(text='IMEI/serial', size_hint_x=None, width='90dp'))
        self.ed_imei = TextInput(text='', multiline=False, hint_text='prefix >= 8 digits or serial')
        self.add_widget(self.ed_imei)
        self.btn_imei_info = Button(text='ⓘ', size_hint_x=None, width='40dp')
        self.btn_imei_info.bind(on_release=self._show_imei_info)
        self.add_widget(self.btn_imei_info)

    # Info popups
    def _popup(self, title: str, text: str):
        content = BoxLayout(orientation='vertical')
        content.add_widget(Label(text=text))
        btn = Button(text='Close', size_hint_y=None, height='40dp')
        popup = Popup(title=title, content=content, size_hint=(0.8, 0.6))
        btn.bind(on_release=lambda *_: popup.dismiss())
        content.add_widget(btn)
        popup.open()

    def _show_model_info(self, *_):
        self._popup('Model', (
            "Method 1: Check the Settings app\n"
            "-    Open the Settings app on your Samsung Galaxy device.\n"
            "-    Scroll down and tap 'About phone' or 'About device'.\n"
            "-    Look for the 'Model number' or 'Model name' information.\n"
            "Method 2: Check the back of your Samsung phone."
        ))

    def _show_region_info(self, *_):
        self._popup('Region (CSC)', (
            "CSC (Customer/Carrier code): a 3-letter region code like BTU (UK), ITV (Italy).\n"
            "How to find it:\n"
            "- Settings > About phone > Software information > Service provider software version.\n"
            "- Sometimes printed on the device box or carrier docs.\n"
            "- You can also run 'samloader --listregions' to browse known CSC codes."
        ))

    def _show_imei_info(self, *_):
        self._popup('IMEI/serial', (
            "How to find your IMEI/serial:\n"
            "- Dial *#06# on the phone to show IMEI.\n"
            "- Or go to Settings > About phone > Status.\n"
            "Notes:\n"
            "- You may enter a serial instead of IMEI.\n"
            "- IMEI prefix (>= 8 digits) is accepted; the tool completes it and adds the Luhn checksum automatically."
        ))

    def _browse_region(self, *_):
        dlg = RegionsPicker(self.regions_map, on_pick=lambda code: setattr(self.ed_region, 'text', code))
        dlg.open()


class SamLoaderRoot(TabbedPanel):
    # Common state
    status_text = StringProperty('Latest: -')
    check_enabled = BooleanProperty(True)
    check_btn_text = StringProperty('Check latest version')
    log_text = StringProperty('')

    # Download state
    dl_progress_max = NumericProperty(100)
    dl_progress_val = NumericProperty(0)
    dl_stats_text = StringProperty('')

    # Decrypt state
    dec_progress_max = NumericProperty(100)
    dec_progress_val = NumericProperty(0)

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.do_default_tab = False
        # Initialize regions map
        try:
            self._regions_map: Dict[str, str] = get_csc_regions()
        except Exception:
            self._regions_map = {}
        # Track last download context
        self._last_download_path = None
        self._last_download_fwver = None
        self._last_download_encver = None
        self._dl_total = 0
        self._dl_done = 0
        self._dl_start_time = 0.0
        self._dl_start_base = 0
        # Build UI
        self._build_tabs()

    # Logging helper
    def log(self, *args):
        line = ' '.join(str(a) for a in args)
        self.log_text += (line + '\n')

    # Human readable sizes and ETA
    def _human_bytes(self, n: int) -> str:
        units = ['B', 'KB', 'MB', 'GB', 'TB']
        i = 0
        f = float(n)
        while f >= 1024 and i < len(units) - 1:
            f /= 1024.0
            i += 1
        return f"{f:.2f}{units[i]}"

    def _format_eta(self, secs: float) -> str:
        if secs < 0 or secs == float('inf'):
            return '--:--:--'
        m, s = divmod(int(secs), 60)
        h, m = divmod(m, 60)
        return f"{h:02d}:{m:02d}:{s:02d}"

    def _update_dl_stats(self, *_):
        done = self._dl_done
        total = self._dl_total or 0
        now = time.time()
        elapsed = max(0.001, now - (self._dl_start_time or now))
        effective_done = max(0, done - (self._dl_start_base or 0))
        speed = effective_done / elapsed
        remaining = max(0, (total - done))
        eta = remaining / speed if speed > 0 else float('inf')
        if total:
            txt = f"{self._human_bytes(done)}/{self._human_bytes(total)}  -  {self._human_bytes(speed)}/s  -  ETA {self._format_eta(eta)}"
        else:
            txt = f"{self._human_bytes(done)}  -  {self._human_bytes(speed)}/s"
        self.dl_stats_text = txt

    def _build_tabs(self):
        # Tab: Check Update
        tab_check = TabbedPanelItem(text='Check Update')
        content_check = BoxLayout(orientation='vertical', spacing=6, padding=6)
        self.dev_row = DeviceRow(self._regions_map)
        content_check.add_widget(self.dev_row)
        btn_row = BoxLayout(size_hint_y=None, height='40dp', spacing=6)
        self.btn_check = Button(text=self.check_btn_text, disabled=not self.check_enabled)
        self.btn_check.bind(on_release=self.on_check_update)
        btn_row.add_widget(self.btn_check)
        self.lbl_status = Label(text=self.status_text, halign='left', valign='middle')
        self.lbl_status.bind(texture_size=lambda *_: setattr(self.lbl_status, 'size', self.lbl_status.texture_size))
        content_check.add_widget(btn_row)
        content_check.add_widget(self.lbl_status)
        tab_check.add_widget(content_check)
        self.add_widget(tab_check)

        # Tab: Download
        tab_dl = TabbedPanelItem(text='Download')
        dl = BoxLayout(orientation='vertical', spacing=6, padding=6)
        # Firmware + out dir row
        fw_row = GridLayout(cols=5, spacing=6, size_hint_y=None, height='34dp')
        fw_row.add_widget(Label(text='Firmware version', size_hint_x=None, width='140dp'))
        self.ed_fwver = TextInput(text='', multiline=False)
        fw_row.add_widget(self.ed_fwver)
        fw_row.add_widget(Label(text='Output dir', size_hint_x=None, width='100dp'))
        self.ed_outdir = TextInput(text='', multiline=False)
        fw_row.add_widget(self.ed_outdir)
        btn_outdir = Button(text='Browse…', size_hint_x=None, width='100dp')
        btn_outdir.bind(on_release=lambda *_: FileChooserPopup('Select output directory', select_dir=True, on_select=lambda p: setattr(self.ed_outdir, 'text', p)).open())
        fw_row.add_widget(btn_outdir)
        dl.add_widget(fw_row)
        # Options row
        opt_row = BoxLayout(size_hint_y=None, height='34dp', spacing=6)
        self.chk_resume = Button(text='Resume: OFF', size_hint_x=None, width='130dp')
        self.chk_resume._state = False
        def toggle_resume(*_):
            self.chk_resume._state = not self.chk_resume._state
            self.chk_resume.text = 'Resume: ON' if self.chk_resume._state else 'Resume: OFF'
        self.chk_resume.bind(on_release=toggle_resume)
        self.chk_autodec = Button(text='Auto-decrypt: OFF', size_hint_x=None, width='160dp')
        self.chk_autodec._state = False
        def toggle_autodec(*_):
            self.chk_autodec._state = not self.chk_autodec._state
            self.chk_autodec.text = 'Auto-decrypt: ON' if self.chk_autodec._state else 'Auto-decrypt: OFF'
        self.chk_autodec.bind(on_release=toggle_autodec)
        opt_row.add_widget(self.chk_resume)
        opt_row.add_widget(self.chk_autodec)
        dl.add_widget(opt_row)
        # Start + progress
        self.btn_download = Button(text='Start download', size_hint_y=None, height='40dp')
        self.btn_download.bind(on_release=self.on_download)
        dl.add_widget(self.btn_download)
        self.pb_download = ProgressBar(max=self.dl_progress_max, value=self.dl_progress_val)
        dl.add_widget(self.pb_download)
        self.lbl_dl_stats = Label(text=self.dl_stats_text)
        dl.add_widget(self.lbl_dl_stats)
        tab_dl.add_widget(dl)
        self.add_widget(tab_dl)

        # Tab: Decrypt
        tab_dec = TabbedPanelItem(text='Decrypt')
        dec = BoxLayout(orientation='vertical', spacing=6, padding=6)
        # FW + enc ver
        top_dec = GridLayout(cols=4, spacing=6, size_hint_y=None, height='34dp')
        top_dec.add_widget(Label(text='Firmware version', size_hint_x=None, width='140dp'))
        self.ed_dec_fwver = TextInput(text='', multiline=False)
        top_dec.add_widget(self.ed_dec_fwver)
        top_dec.add_widget(Label(text='Enc ver', size_hint_x=None, width='80dp'))
        self.ed_encver = TextInput(text='4', multiline=False, size_hint_x=None, width='60dp')
        top_dec.add_widget(self.ed_encver)
        dec.add_widget(top_dec)
        # In/out files
        in_row = GridLayout(cols=5, spacing=6, size_hint_y=None, height='34dp')
        in_row.add_widget(Label(text='Encrypted file', size_hint_x=None, width='120dp'))
        self.ed_infile = TextInput(text='', multiline=False)
        in_row.add_widget(self.ed_infile)
        in_row.add_widget(Label(text=''))
        btn_in = Button(text='Browse…', size_hint_x=None, width='100dp')
        btn_in.bind(on_release=lambda *_: FileChooserPopup('Select encrypted file', on_select=lambda p: setattr(self.ed_infile, 'text', p)).open())
        in_row.add_widget(btn_in)
        dec.add_widget(in_row)

        out_row = GridLayout(cols=5, spacing=6, size_hint_y=None, height='34dp')
        out_row.add_widget(Label(text='Output file', size_hint_x=None, width='120dp'))
        self.ed_outfile = TextInput(text='', multiline=False)
        out_row.add_widget(self.ed_outfile)
        out_row.add_widget(Label(text=''))
        btn_out = Button(text='Browse…', size_hint_x=None, width='100dp')
        btn_out.bind(on_release=lambda *_: FileChooserPopup('Select output file', on_select=lambda p: setattr(self.ed_outfile, 'text', p)).open())
        out_row.add_widget(btn_out)
        dec.add_widget(out_row)

        self.btn_decrypt = Button(text='Start decryption', size_hint_y=None, height='40dp')
        self.btn_decrypt.bind(on_release=self.on_decrypt)
        dec.add_widget(self.btn_decrypt)
        self.pb_decrypt = ProgressBar(max=self.dec_progress_max, value=self.dec_progress_val)
        dec.add_widget(self.pb_decrypt)
        tab_dec.add_widget(dec)
        self.add_widget(tab_dec)

        # Tab: Log
        tab_log = TabbedPanelItem(text='Log')
        log_box = BoxLayout(orientation='vertical', spacing=6, padding=6)
        self.txt_log = TextInput(text='', readonly=True)
        # Bind the StringProperty to the widget
        def sync_log(*_):
            self.txt_log.text = self.log_text
            self.txt_log.cursor = (0, len(self.txt_log.text.splitlines()))
        self.bind(log_text=lambda *_: Clock.schedule_once(sync_log, 0))
        log_box.add_widget(self.txt_log)
        tab_log.add_widget(log_box)
        self.add_widget(tab_log)

    # Event handlers
    def on_check_update(self, *_):
        model = self.dev_row.ed_model.text.strip()
        region = self.dev_row.ed_region.text.strip()
        if not model or not region:
            self.log('Missing data: Model and Region are required')
            return
        self.check_enabled = False
        self.check_btn_text = 'Checking'
        self.btn_check.text = self.check_btn_text
        self.btn_check.disabled = True

        def worker():
            try:
                latest = versionfetch.getlatestver(model, region)
                Clock.schedule_once(lambda *_: self._check_ok(latest), 0)
            except Exception as e:
                try:
                    import requests
                    if isinstance(e, requests.exceptions.Timeout):
                        Clock.schedule_once(lambda *_: self._check_timeout(), 0)
                        return
                except Exception:
                    pass
                Clock.schedule_once(lambda *_: self._show_error(str(e)), 0)
            finally:
                Clock.schedule_once(lambda *_: self._restore_check_button(), 0)
        threading.Thread(target=worker, daemon=True).start()

    def _check_ok(self, latest: str):
        self.status_text = f'Latest: {latest}'
        self.lbl_status.text = self.status_text
        self.log('Latest version:', latest)
        # Autofill firmware fields
        self.ed_fwver.text = latest
        self.ed_dec_fwver.text = latest

    def _check_timeout(self):
        self.status_text = 'Latest: request timed out (try again)'
        self.lbl_status.text = self.status_text
        self.log('Timeout while fetching latest version')

    def _restore_check_button(self):
        self.check_enabled = True
        self.check_btn_text = 'Check latest version'
        self.btn_check.text = self.check_btn_text
        self.btn_check.disabled = False

    def _show_error(self, msg: str):
        self.log('Error:', msg)
        # Non-blocking: show a small dismissible popup
        content = BoxLayout(orientation='vertical')
        content.add_widget(Label(text=msg))
        btn = Button(text='Close', size_hint_y=None, height='40dp')
        popup = Popup(title='Error', content=content, size_hint=(0.7, 0.4))
        btn.bind(on_release=lambda *_: popup.dismiss())
        content.add_widget(btn)
        popup.open()

    def on_download(self, *_):
        model = self.dev_row.ed_model.text.strip()
        region = self.dev_row.ed_region.text.strip()
        imei_input = self.dev_row.ed_imei.text.strip() or None
        fwver = self.ed_fwver.text.strip()
        outdir = self.ed_outdir.text.strip()
        if not model or not region:
            self.log('Missing data: Model and Region are required')
            return
        if not fwver:
            self.log('Missing data: Firmware version is required')
            return
        if not outdir:
            self.log('Missing data: Output directory is required')
            return
        os.makedirs(outdir, exist_ok=True)
        self.btn_download.disabled = True

        def worker():
            try:
                args = ArgsLike(dev_model=model, dev_region=region, dev_imei=imei_input, command='download')
                if imei.fixup_imei(args):
                    raise Exception('IMEI/serial missing or invalid. Provide IMEI prefix (>=8 digits) or serial.')
                client = fusclient.FUSClient()
                # Normalize version to 4-part form
                try:
                    fwver_norm = versionfetch.normalizevercode(fwver)
                except Exception:
                    fwver_norm = fwver
                path, filename, size = getbinaryfile(client, fwver_norm, args.dev_model, args.dev_imei, args.dev_region)
                out_file = os.path.join(outdir, filename)
                resume = self.chk_resume._state
                try:
                    dloffset = os.stat(out_file).st_size if resume else 0
                except FileNotFoundError:
                    dloffset = 0
                Clock.schedule_once(lambda *_: self.log(('Resuming' if dloffset else 'Downloading'), filename), 0)
                if dloffset == size:
                    Clock.schedule_once(lambda *_: self.log('Already downloaded!'), 0)
                    Clock.schedule_once(lambda *_: self._download_done(out_file, fwver_norm), 0)
                    return
                initdownload(client, filename)
                r = client.downloadfile(path + filename, dloffset)
                # Setup progress
                self._dl_total = size
                self._dl_done = dloffset
                self._dl_start_time = time.time()
                self._dl_start_base = dloffset
                Clock.schedule_once(lambda *_: self._set_dl_progress(dloffset, size), 0)
                with open(out_file, 'ab' if dloffset else 'wb') as fd:
                    for chunk in r.iter_content(chunk_size=0x10000):
                        if not chunk:
                            continue
                        fd.write(chunk)
                        fd.flush()
                        self._dl_done += len(chunk)
                        Clock.schedule_once(lambda *_: self._inc_dl_progress(len(chunk)), 0)
                if self.chk_autodec._state:
                    dec_out = out_file.replace('.enc4', '').replace('.enc2', '')
                    if os.path.isfile(dec_out):
                        raise Exception(f'File {dec_out} already exists, refusing to auto-decrypt!')
                    Clock.schedule_once(lambda *_: self.log('Decrypting:', out_file), 0)
                    args.fw_ver = fwver_norm
                    version = 2 if filename.lower().endswith('.enc2') else 4
                    decrypt_file(args, version, out_file, dec_out)
                    try:
                        os.remove(out_file)
                    except Exception:
                        pass
                    Clock.schedule_once(lambda *_: self.log('Decryption complete:', dec_out), 0)
                Clock.schedule_once(lambda *_: self._download_done(out_file, fwver_norm), 0)
            except Exception as e:
                Clock.schedule_once(lambda *_: self._show_error(str(e)), 0)
                Clock.schedule_once(lambda *_: setattr(self.btn_download, 'disabled', False), 0)
        threading.Thread(target=worker, daemon=True).start()

    def _set_dl_progress(self, start: int, total: int):
        self.pb_download.max = total
        self.pb_download.value = start
        self._update_dl_stats()

    def _inc_dl_progress(self, delta: int):
        self.pb_download.value = min(self.pb_download.value + delta, self.pb_download.max)
        self._update_dl_stats()

    def _download_done(self, path: str, fwver: str):
        self.log('Download complete:', path)
        self.btn_download.disabled = False
        # Save context and prefill decrypt
        self._last_download_path = path
        self._last_download_fwver = fwver
        encver = 2 if str(path).lower().endswith('.enc2') else (4 if str(path).lower().endswith('.enc4') else None)
        self._last_download_encver = encver
        if encver and os.path.isfile(path):
            self.ed_dec_fwver.text = fwver
            self.ed_encver.text = str(encver)
            self.ed_infile.text = path
            out_guess = path[:-5] if path.lower().endswith(('.enc2', '.enc4')) else path
            self.ed_outfile.text = out_guess
        # finalize stats
        self._dl_done = self._dl_total
        self._update_dl_stats()

    def on_decrypt(self, *_):
        model = self.dev_row.ed_model.text.strip()
        region = self.dev_row.ed_region.text.strip()
        imei_input = self.dev_row.ed_imei.text.strip() or None
        fwver = self.ed_dec_fwver.text.strip()
        encver_txt = self.ed_encver.text.strip() or '4'
        try:
            encver = int(encver_txt)
        except Exception:
            encver = 4
        infile = self.ed_infile.text.strip()
        outfile = self.ed_outfile.text.strip()
        if not model or not region:
            self.log('Missing data: Model and Region are required')
            return
        if not fwver or not infile or not outfile:
            self.log('Missing data: Firmware version, input file and output file are required')
            return
        if not os.path.isfile(infile):
            self.log('Invalid input: Encrypted input file does not exist')
            return
        self.btn_decrypt.disabled = True

        def worker():
            try:
                args = ArgsLike(dev_model=model, dev_region=region, dev_imei=imei_input, command='decrypt', enc_ver=encver, fw_ver=fwver)
                if imei.fixup_imei(args):
                    raise Exception('IMEI/serial missing or invalid. Provide IMEI prefix (>=8 digits) or serial.')
                length = os.stat(infile).st_size
                Clock.schedule_once(lambda *_: self._set_dec_progress(0, length), 0)

                def progress_wrapper(src, dst, key, total_len):
                    written = 0
                    def write_and_progress(data: bytes):
                        nonlocal written
                        n = dst.write(data)
                        written += n
                        Clock.schedule_once(lambda *_: self._inc_dec_progress(n), 0)
                        return n
                    class OutWrap:
                        def write(self, data):
                            return write_and_progress(data)
                    crypt.decrypt_progress(src, OutWrap(), key, total_len)

                getkey = crypt.getv2key if encver == 2 else crypt.getv4key
                key = getkey(args.fw_ver, args.dev_model, args.dev_region, args.dev_imei)
                if not key:
                    raise Exception('Failed to obtain decryption key')
                with open(infile, 'rb') as inf, open(outfile, 'wb') as outf:
                    progress_wrapper(inf, outf, key, length)
                Clock.schedule_once(lambda *_: self.log('Decryption complete:', outfile), 0)
            except Exception as e:
                Clock.schedule_once(lambda *_: self._show_error(str(e)), 0)
            finally:
                Clock.schedule_once(lambda *_: setattr(self.btn_decrypt, 'disabled', False), 0)
        threading.Thread(target=worker, daemon=True).start()

    def _set_dec_progress(self, start: int, total: int):
        self.pb_decrypt.max = total
        self.pb_decrypt.value = start

    def _inc_dec_progress(self, delta: int):
        self.pb_decrypt.value = min(self.pb_decrypt.value + delta, self.pb_decrypt.max)


class SamloaderKivyApp(App):
    title = f"SamLoader Reloaded v{VERSION}"
    def build(self):
        return SamLoaderRoot()


def main():
    SamloaderKivyApp().run()


if __name__ == '__main__':
    main()
