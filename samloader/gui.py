# SPDX-License-Identifier: GPL-3.0+
# PySide6 (Qt6) GUI for SamLoader Reloaded
# Provides basic operations: Check Update, Download, Decrypt

import os
import threading
from dataclasses import dataclass
from typing import Optional, Dict

# Qt imports
from PySide6.QtCore import Qt, QObject, Signal
from PySide6.QtWidgets import (
    QApplication, QMainWindow, QWidget, QLabel, QLineEdit, QComboBox,
    QPushButton, QCheckBox, QTabWidget, QVBoxLayout, QHBoxLayout, QGridLayout,
    QFileDialog, QMessageBox, QGroupBox, QProgressBar, QTextEdit
)

# Support running as part of the package and when bundled
try:
    from . import versionfetch
    from . import fusclient
    from . import crypt
    from . import imei
    from . import __version__ as VERSION
    from .regions import get_regions as get_csc_regions
    from .main import getbinaryfile, initdownload, decrypt_file
except Exception:
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


class Signals(QObject):
    log = Signal(str)
    set_status = Signal(str)
    set_check_btn = Signal(bool, str)
    latest_ok = Signal(str)
    latest_timeout = Signal()
    error = Signal(str)

    # Download
    dl_set_range = Signal(int, int)
    dl_progress = Signal(int)
    dl_done = Signal(str)

    # Decrypt
    dec_set_range = Signal(int)
    dec_progress = Signal(int)
    dec_done = Signal(str)


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        try:
            self.setWindowTitle(f"SamLoader Reloaded v{VERSION}")
        except Exception:
            self.setWindowTitle("SamLoader Reloaded")
        self.resize(900, 600)

        self.signals = Signals()
        self.signals.log.connect(self._log)
        self.signals.set_status.connect(self._set_status)
        self.signals.set_check_btn.connect(self._set_check_btn)
        self.signals.latest_ok.connect(self._latest_ok)
        self.signals.latest_timeout.connect(self._latest_timeout)
        self.signals.error.connect(self._show_error)
        self.signals.dl_set_range.connect(self._dl_set_range)
        self.signals.dl_progress.connect(self._dl_progress)
        self.signals.dl_done.connect(self._dl_done)
        self.signals.dec_set_range.connect(self._dec_set_range)
        self.signals.dec_progress.connect(self._dec_progress)
        self.signals.dec_done.connect(self._dec_done)

        self._regions_map: Dict[str, str] = {}
        self._all_region_codes = []

        self._build_ui()

    # UI building
    def _build_ui(self):
        central = QWidget()
        self.setCentralWidget(central)
        v = QVBoxLayout(central)

        # Device group
        dev = QGroupBox("Device")
        v.addWidget(dev)
        grid = QGridLayout(dev)

        # Model
        grid.addWidget(QLabel("Model"), 0, 0)
        self.ed_model = QLineEdit()
        self.ed_model.setPlaceholderText("e.g., SM-S918B")
        grid.addWidget(self.ed_model, 0, 1)
        lbl_model_info = QLabel("ⓘ")
        lbl_model_info.setToolTip(
            "Method 1: Check the Settings app\n"
            "-    Open the Settings app on your Samsung Galaxy device.\n"
            "-    Scroll down and tap 'About phone' or 'About device'.\n"
            "-    Look for the 'Model number' or 'Model name' information.\n"
            "Method 2: Check the back of your Samsung phone."
        )
        grid.addWidget(lbl_model_info, 0, 2)

        # Region (CSC) with simple auto-complete via editable combobox
        grid.addWidget(QLabel("Region"), 0, 3)
        self.cb_region = QComboBox()
        self.cb_region.setEditable(True)
        self.cb_region.setInsertPolicy(QComboBox.NoInsert)
        grid.addWidget(self.cb_region, 0, 4)
        self.cb_region.lineEdit().textEdited.connect(self._on_region_typed)
        lbl_region_info = QLabel("ⓘ")
        lbl_region_info.setToolTip(
            "CSC (Customer/Carrier code): a 3-letter region code like BTU (UK), ITV (Italy).\n"
            "How to find it:\n"
            "- Settings > About phone > Software information > Service provider software version (look for codes like INS/INS,OXM/INS).\n"
            "- Sometimes printed on the device box or carrier docs.\n"
            "- You can also run 'samloader --listregions' to browse known CSC codes."
        )
        grid.addWidget(lbl_region_info, 0, 5)
        btn_browse_region = QPushButton("Browse…")
        btn_browse_region.clicked.connect(self._open_region_picker)
        grid.addWidget(btn_browse_region, 0, 6)

        # IMEI
        grid.addWidget(QLabel("IMEI prefix or serial"), 0, 7)
        self.ed_imei = QLineEdit()
        grid.addWidget(self.ed_imei, 0, 8)
        lbl_imei_info = QLabel("ⓘ")
        lbl_imei_info.setToolTip(
            "How to find your IMEI/serial:\n"
            "- Dial *#06# on the phone to show IMEI.\n"
            "- Or go to Settings > About phone > Status.\n"
            "Notes:\n"
            "- You may enter a serial instead of IMEI.\n"
            "- IMEI prefix (>= 8 digits) is accepted; the tool completes it and adds the Luhn checksum automatically."
        )
        grid.addWidget(lbl_imei_info, 0, 9)

        # Tabs
        tabs = QTabWidget()
        v.addWidget(tabs, 1)

        # Tab: Check Update
        tab_check = QWidget()
        tabs.addTab(tab_check, "Check Update")
        vcheck = QVBoxLayout(tab_check)
        self.btn_check = QPushButton("Check latest version")
        self.btn_check.clicked.connect(self.on_check_update)
        vcheck.addWidget(self.btn_check)
        self.lbl_latest = QLabel("Latest: -")
        vcheck.addWidget(self.lbl_latest)

        # Tab: Download
        tab_dl = QWidget()
        tabs.addTab(tab_dl, "Download")
        vdl = QVBoxLayout(tab_dl)
        grid_dl = QGridLayout()
        vdl.addLayout(grid_dl)

        grid_dl.addWidget(QLabel("Firmware version"), 0, 0)
        self.ed_fwver = QLineEdit()
        grid_dl.addWidget(self.ed_fwver, 0, 1, 1, 4)

        grid_dl.addWidget(QLabel("Output directory"), 1, 0)
        self.ed_outdir = QLineEdit()
        grid_dl.addWidget(self.ed_outdir, 1, 1, 1, 3)
        btn_outdir = QPushButton("Browse…")
        btn_outdir.clicked.connect(self.browse_outdir)
        grid_dl.addWidget(btn_outdir, 1, 4)

        self.chk_resume = QCheckBox("Resume")
        self.chk_autodec = QCheckBox("Auto-decrypt after download")
        hopt = QHBoxLayout()
        hopt.addWidget(self.chk_resume)
        hopt.addWidget(self.chk_autodec)
        vdl.addLayout(hopt)

        self.btn_download = QPushButton("Start download")
        self.btn_download.clicked.connect(self.on_download)
        vdl.addWidget(self.btn_download)
        self.pb_download = QProgressBar()
        vdl.addWidget(self.pb_download)

        # Tab: Decrypt
        tab_dec = QWidget()
        tabs.addTab(tab_dec, "Decrypt")
        vdec = QVBoxLayout(tab_dec)
        grid_dec = QGridLayout()
        vdec.addLayout(grid_dec)

        grid_dec.addWidget(QLabel("Firmware version"), 0, 0)
        self.ed_dec_fwver = QLineEdit()
        grid_dec.addWidget(self.ed_dec_fwver, 0, 1)
        grid_dec.addWidget(QLabel("Enc ver"), 0, 2)
        self.cb_encver = QComboBox()
        self.cb_encver.addItems(["2", "4"])
        self.cb_encver.setCurrentText("4")
        grid_dec.addWidget(self.cb_encver, 0, 3)

        grid_dec.addWidget(QLabel("Encrypted file"), 1, 0)
        self.ed_infile = QLineEdit()
        grid_dec.addWidget(self.ed_infile, 1, 1, 1, 3)
        btn_in = QPushButton("Browse…")
        btn_in.clicked.connect(self.browse_infile)
        grid_dec.addWidget(btn_in, 1, 4)

        grid_dec.addWidget(QLabel("Output file"), 2, 0)
        self.ed_outfile = QLineEdit()
        grid_dec.addWidget(self.ed_outfile, 2, 1, 1, 3)
        btn_out = QPushButton("Browse…")
        btn_out.clicked.connect(self.browse_outfile)
        grid_dec.addWidget(btn_out, 2, 4)

        self.btn_decrypt = QPushButton("Start decryption")
        self.btn_decrypt.clicked.connect(self.on_decrypt)
        vdec.addWidget(self.btn_decrypt)
        self.pb_decrypt = QProgressBar()
        vdec.addWidget(self.pb_decrypt)

        # Log area
        log_group = QGroupBox("Log")
        v.addWidget(log_group, 1)
        vlog = QVBoxLayout(log_group)
        self.txt_log = QTextEdit()
        self.txt_log.setReadOnly(True)
        vlog.addWidget(self.txt_log)

        # Load regions
        try:
            self._regions_map = get_csc_regions()
        except Exception:
            self._regions_map = {}
        self._all_region_codes = sorted(list(self._regions_map.keys())) if self._regions_map else []
        self.cb_region.addItems(self._all_region_codes)

    # Helpers
    def _log(self, msg: str):
        self.txt_log.append(msg)

    def _set_status(self, text: str):
        self.lbl_latest.setText(text)

    def _set_check_btn(self, enabled: bool, text: str):
        self.btn_check.setEnabled(enabled)
        self.btn_check.setText(text)

    def _latest_ok(self, ver: str):
        self.lbl_latest.setText(f"Latest: {ver}")
        self._log(f"Latest version: {ver}")

    def _latest_timeout(self):
        self.lbl_latest.setText("Latest: request timed out (try again)")
        self._log("Timeout while fetching latest version")

    def _show_error(self, msg: str):
        QMessageBox.critical(self, "Error", msg)
        self._log(f"Error: {msg}")

    def _dl_set_range(self, start: int, total: int):
        self.pb_download.setRange(0, total)
        self.pb_download.setValue(start)

    def _dl_progress(self, delta: int):
        self.pb_download.setValue(min(self.pb_download.value() + delta, self.pb_download.maximum()))

    def _dl_done(self, path: str):
        self._log(f"Download complete: {path}")
        self.btn_download.setEnabled(True)

    def _dec_set_range(self, total: int):
        self.pb_decrypt.setRange(0, total)
        self.pb_decrypt.setValue(0)

    def _dec_progress(self, delta: int):
        self.pb_decrypt.setValue(min(self.pb_decrypt.value() + delta, self.pb_decrypt.maximum()))

    def _dec_done(self, path: str):
        self._log(f"Decryption complete: {path}")
        self.btn_decrypt.setEnabled(True)

    # Region helpers
    def _on_region_typed(self, text: str):
        up = text.upper()
        if text != up:
            self.cb_region.lineEdit().blockSignals(True)
            self.cb_region.lineEdit().setText(up)
            self.cb_region.lineEdit().blockSignals(False)
        q = up.strip()
        self.cb_region.clear()
        if not q:
            self.cb_region.addItems(self._all_region_codes)
            return
        values = [c for c in self._all_region_codes if c.startswith(q)]
        if self._regions_map:
            name_hits = [c for c, nm in self._regions_map.items() if q.lower() in nm.lower()]
            for c in name_hits:
                if c not in values:
                    values.append(c)
        self.cb_region.addItems(values)
        self.cb_region.lineEdit().setText(up)

    def _open_region_picker(self):
        # Minimal dialog: reuse the combobox list and instruct the user
        QMessageBox.information(self, "Select Region",
                                "Type the region code in the Region field. Suggestions appear automatically.\n"
                                "You can also paste or type the code directly.")

    # Browsers
    def browse_outdir(self):
        d = QFileDialog.getExistingDirectory(self, "Select output directory")
        if d:
            self.ed_outdir.setText(d)

    def browse_infile(self):
        f, _ = QFileDialog.getOpenFileName(self, "Select encrypted file")
        if f:
            self.ed_infile.setText(f)

    def browse_outfile(self):
        f, _ = QFileDialog.getSaveFileName(self, "Select output file", filter="ZIP files (*.zip);;All files (*.*)")
        if f:
            self.ed_outfile.setText(f)

    # Common data
    def gather_common(self):
        model = self.ed_model.text().strip()
        region = self.cb_region.currentText().strip()
        imei_str = self.ed_imei.text().strip() or None
        if not model or not region:
            QMessageBox.critical(self, "Missing data", "Model and Region are required")
            return None
        return model, region, imei_str

    # Actions
    def on_check_update(self):
        common = self.gather_common()
        if not common:
            return
        model, region, _ = common
        self.signals.set_check_btn.emit(False, "Checking")

        def worker():
            try:
                latest = versionfetch.getlatestver(model, region)
                self.signals.latest_ok.emit(latest)
            except Exception as e:
                try:
                    import requests
                    if isinstance(e, requests.exceptions.Timeout):
                        self.signals.latest_timeout.emit()
                        return
                except Exception:
                    pass
                self.signals.error.emit(str(e))
            finally:
                self.signals.set_check_btn.emit(True, "Check latest version")
        threading.Thread(target=worker, daemon=True).start()

    def on_download(self):
        common = self.gather_common()
        if not common:
            return
        model, region, imei_input = common
        fwver = self.ed_fwver.text().strip()
        outdir = self.ed_outdir.text().strip()
        if not fwver:
            QMessageBox.critical(self, "Missing data", "Firmware version is required")
            return
        if not outdir:
            QMessageBox.critical(self, "Missing data", "Output directory is required")
            return
        os.makedirs(outdir, exist_ok=True)

        self.btn_download.setEnabled(False)
        resume = self.chk_resume.isChecked()

        def worker():
            try:
                args = ArgsLike(dev_model=model, dev_region=region, dev_imei=imei_input, command="download")
                if imei.fixup_imei(args):
                    raise Exception("IMEI/serial missing or invalid. Provide IMEI prefix (>=8 digits) or serial.")
                client = fusclient.FUSClient()
                path, filename, size = getbinaryfile(client, fwver, args.dev_model, args.dev_imei, args.dev_region)
                out_file = os.path.join(outdir, filename)
                try:
                    dloffset = os.stat(out_file).st_size if resume else 0
                except FileNotFoundError:
                    dloffset = 0
                self.signals.log.emit(("Resuming" if dloffset else "Downloading") + f" {filename}")
                if dloffset == size:
                    self.signals.log.emit("Already downloaded!")
                    self.signals.dl_done.emit(out_file)
                    return
                initdownload(client, filename)
                r = client.downloadfile(path + filename, dloffset)
                self.signals.dl_set_range.emit(dloffset, size)
                with open(out_file, "ab" if dloffset else "wb") as fd:
                    for chunk in r.iter_content(chunk_size=0x10000):
                        if not chunk:
                            continue
                        fd.write(chunk)
                        fd.flush()
                        self.signals.dl_progress.emit(len(chunk))
                if self.chk_autodec.isChecked():
                    dec_out = out_file.replace(".enc4", "").replace(".enc2", "")
                    if os.path.isfile(dec_out):
                        raise Exception(f"File {dec_out} already exists, refusing to auto-decrypt!")
                    self.signals.log.emit(f"Decrypting: {out_file}")
                    # For decrypt, need fw_ver
                    args.fw_ver = fwver
                    version = 2 if filename.lower().endswith(".enc2") else 4
                    decrypt_file(args, version, out_file, dec_out)
                    try:
                        os.remove(out_file)
                    except Exception:
                        pass
                    self.signals.log.emit(f"Decryption complete: {dec_out}")
                self.signals.dl_done.emit(out_file)
            except Exception as e:
                self.signals.error.emit(str(e))
                self.btn_download.setEnabled(True)
        threading.Thread(target=worker, daemon=True).start()

    def on_decrypt(self):
        common = self.gather_common()
        if not common:
            return
        model, region, imei_input = common
        fwver = self.ed_dec_fwver.text().strip()
        encver = int(self.cb_encver.currentText())
        infile = self.ed_infile.text().strip()
        outfile = self.ed_outfile.text().strip()
        if not fwver or not infile or not outfile:
            QMessageBox.critical(self, "Missing data", "Firmware version, input file and output file are required")
            return
        if not os.path.isfile(infile):
            QMessageBox.critical(self, "Invalid input", "Encrypted input file does not exist")
            return
        self.btn_decrypt.setEnabled(False)

        def worker():
            try:
                args = ArgsLike(dev_model=model, dev_region=region, dev_imei=imei_input, command="decrypt", enc_ver=encver, fw_ver=fwver)
                if imei.fixup_imei(args):
                    raise Exception("IMEI/serial missing or invalid. Provide IMEI prefix (>=8 digits) or serial.")
                length = os.stat(infile).st_size
                self.signals.dec_set_range.emit(length)

                def progress_wrapper(src, dst, key, total_len):
                    written = 0
                    def write_and_progress(data: bytes):
                        nonlocal written
                        n = dst.write(data)
                        written += n
                        self.signals.dec_progress.emit(n)
                        return n
                    class OutWrap:
                        def write(self, data):
                            return write_and_progress(data)
                    crypt.decrypt_progress(src, OutWrap(), key, total_len)

                getkey = crypt.getv2key if encver == 2 else crypt.getv4key
                key = getkey(args.fw_ver, args.dev_model, args.dev_region, args.dev_imei)
                if not key:
                    raise Exception("Failed to obtain decryption key")
                with open(infile, "rb") as inf, open(outfile, "wb") as outf:
                    progress_wrapper(inf, outf, key, length)
                self.signals.dec_done.emit(outfile)
            except Exception as e:
                self.signals.error.emit(str(e))
                self.btn_decrypt.setEnabled(True)
        threading.Thread(target=worker, daemon=True).start()


def main():
    app = QApplication.instance() or QApplication([])
    win = MainWindow()
    win.show()
    app.exec()


if __name__ == "__main__":
    main()
