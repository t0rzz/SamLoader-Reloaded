# SPDX-License-Identifier: GPL-3.0+
# Simple Tkinter GUI for samloader
# Provides basic operations: Check Update, Download, Decrypt

import os
import threading
import tkinter as tk
from tkinter import ttk, filedialog, messagebox
from dataclasses import dataclass
from typing import Optional

# Support running as part of the package (python -m samloader.gui) and as a standalone script bundled by PyInstaller
try:
    from . import versionfetch
    from . import fusclient
    from . import crypt
    from . import imei
    from . import __version__ as VERSION
    from .main import getbinaryfile, initdownload, decrypt_file
except Exception:
    # When executed as a script without package context (e.g., PyInstaller using samloader\gui.py),
    # fall back to absolute imports from the bundled "samloader" package.
    import samloader.versionfetch as versionfetch
    import samloader.fusclient as fusclient
    import samloader.crypt as crypt
    import samloader.imei as imei
    try:
        from samloader import __version__ as VERSION
    except Exception:
        VERSION = "?"
    from samloader.main import getbinaryfile, initdownload, decrypt_file


@dataclass
class ArgsLike:
    dev_model: str
    dev_region: str
    dev_imei: Optional[str]
    command: str
    enc_ver: int = 4
    fw_ver: Optional[str] = None


class SamloaderGUI(tk.Tk):
    def __init__(self):
        super().__init__()
        try:
            self.title(f"SamLoader Reloaded v{VERSION}")
        except Exception:
            self.title("SamLoader Reloaded")
        self.geometry("720x520")
        self.resizable(True, True)

        self._build_ui()

    def _build_ui(self):
        # Common frame for device info
        common = ttk.LabelFrame(self, text="Device")
        common.pack(fill=tk.X, padx=10, pady=10)

        ttk.Label(common, text="Model").grid(row=0, column=0, sticky=tk.W, padx=5, pady=5)
        self.var_model = tk.StringVar()
        ttk.Entry(common, textvariable=self.var_model, width=20).grid(row=0, column=1, sticky=tk.W, padx=5, pady=5)

        ttk.Label(common, text="Region").grid(row=0, column=2, sticky=tk.W, padx=5, pady=5)
        self.var_region = tk.StringVar()
        ttk.Entry(common, textvariable=self.var_region, width=10).grid(row=0, column=3, sticky=tk.W, padx=5, pady=5)

        ttk.Label(common, text="IMEI prefix or serial").grid(row=0, column=4, sticky=tk.W, padx=5, pady=5)
        self.var_imei = tk.StringVar()
        ttk.Entry(common, textvariable=self.var_imei, width=22).grid(row=0, column=5, sticky=tk.W, padx=5, pady=5)

        # Tabs
        tabs = ttk.Notebook(self)
        tabs.pack(fill=tk.BOTH, expand=True, padx=10, pady=(0,10))

        # Check Update tab
        self.tab_check = ttk.Frame(tabs)
        tabs.add(self.tab_check, text="Check Update")

        self.btn_check = ttk.Button(self.tab_check, text="Check latest version", command=self.on_check_update)
        self.btn_check.pack(anchor=tk.W, padx=10, pady=10)
        self.lbl_latest = ttk.Label(self.tab_check, text="Latest: -")
        self.lbl_latest.pack(anchor=tk.W, padx=10)

        # Download tab
        self.tab_download = ttk.Frame(tabs)
        tabs.add(self.tab_download, text="Download")

        frm_d = ttk.Frame(self.tab_download)
        frm_d.pack(fill=tk.X, padx=10, pady=10)
        ttk.Label(frm_d, text="Firmware version").grid(row=0, column=0, sticky=tk.W, padx=5, pady=5)
        self.var_fwver = tk.StringVar()
        ttk.Entry(frm_d, textvariable=self.var_fwver, width=50).grid(row=0, column=1, columnspan=4, sticky=tk.W, padx=5, pady=5)

        ttk.Label(frm_d, text="Output directory").grid(row=1, column=0, sticky=tk.W, padx=5, pady=5)
        self.var_outdir = tk.StringVar()
        ttk.Entry(frm_d, textvariable=self.var_outdir, width=50).grid(row=1, column=1, columnspan=3, sticky=tk.W, padx=5, pady=5)
        ttk.Button(frm_d, text="Browse...", command=self.browse_outdir).grid(row=1, column=4, sticky=tk.W, padx=5, pady=5)

        self.var_resume = tk.BooleanVar(value=False)
        self.var_autodec = tk.BooleanVar(value=False)
        ttk.Checkbutton(frm_d, text="Resume", variable=self.var_resume).grid(row=2, column=0, sticky=tk.W, padx=5, pady=5)
        ttk.Checkbutton(frm_d, text="Auto-decrypt after download", variable=self.var_autodec).grid(row=2, column=1, columnspan=3, sticky=tk.W, padx=5, pady=5)

        self.btn_download = ttk.Button(self.tab_download, text="Start download", command=self.on_download)
        self.btn_download.pack(anchor=tk.W, padx=10, pady=(0,5))

        self.pb_download = ttk.Progressbar(self.tab_download, orient=tk.HORIZONTAL, mode='determinate')
        self.pb_download.pack(fill=tk.X, padx=10, pady=(0,10))

        # Decrypt tab
        self.tab_decrypt = ttk.Frame(tabs)
        tabs.add(self.tab_decrypt, text="Decrypt")
        frm_dec = ttk.Frame(self.tab_decrypt)
        frm_dec.pack(fill=tk.X, padx=10, pady=10)

        ttk.Label(frm_dec, text="Firmware version").grid(row=0, column=0, sticky=tk.W, padx=5, pady=5)
        self.var_dec_fwver = tk.StringVar()
        ttk.Entry(frm_dec, textvariable=self.var_dec_fwver, width=36).grid(row=0, column=1, sticky=tk.W, padx=5, pady=5)

        ttk.Label(frm_dec, text="Enc ver").grid(row=0, column=2, sticky=tk.W, padx=5, pady=5)
        self.var_encver = tk.IntVar(value=4)
        ttk.Combobox(frm_dec, textvariable=self.var_encver, values=[2,4], width=5, state="readonly").grid(row=0, column=3, sticky=tk.W, padx=5, pady=5)

        ttk.Label(frm_dec, text="Encrypted file").grid(row=1, column=0, sticky=tk.W, padx=5, pady=5)
        self.var_infile = tk.StringVar()
        ttk.Entry(frm_dec, textvariable=self.var_infile, width=50).grid(row=1, column=1, columnspan=3, sticky=tk.W, padx=5, pady=5)
        ttk.Button(frm_dec, text="Browse...", command=self.browse_infile).grid(row=1, column=4, sticky=tk.W, padx=5, pady=5)

        ttk.Label(frm_dec, text="Output file").grid(row=2, column=0, sticky=tk.W, padx=5, pady=5)
        self.var_outfile = tk.StringVar()
        ttk.Entry(frm_dec, textvariable=self.var_outfile, width=50).grid(row=2, column=1, columnspan=3, sticky=tk.W, padx=5, pady=5)
        ttk.Button(frm_dec, text="Browse...", command=self.browse_outfile).grid(row=2, column=4, sticky=tk.W, padx=5, pady=5)

        self.btn_decrypt = ttk.Button(self.tab_decrypt, text="Start decryption", command=self.on_decrypt)
        self.btn_decrypt.pack(anchor=tk.W, padx=10, pady=(0,5))

        self.pb_decrypt = ttk.Progressbar(self.tab_decrypt, orient=tk.HORIZONTAL, mode='determinate')
        self.pb_decrypt.pack(fill=tk.X, padx=10, pady=(0,10))

        # Log box
        logframe = ttk.LabelFrame(self, text="Log")
        logframe.pack(fill=tk.BOTH, expand=True, padx=10, pady=(0,10))
        self.txt_log = tk.Text(logframe, height=8, state=tk.DISABLED)
        self.txt_log.pack(fill=tk.BOTH, expand=True)

    # Utility methods
    def log(self, *args):
        msg = " ".join(str(a) for a in args)
        # Temporarily enable the widget to insert, then disable to keep it read-only
        self.txt_log.configure(state=tk.NORMAL)
        self.txt_log.insert(tk.END, msg + "\n")
        self.txt_log.configure(state=tk.DISABLED)
        self.txt_log.see(tk.END)
        self.update_idletasks()

    def browse_outdir(self):
        d = filedialog.askdirectory()
        if d:
            self.var_outdir.set(d)

    def browse_infile(self):
        f = filedialog.askopenfilename()
        if f:
            self.var_infile.set(f)

    def browse_outfile(self):
        f = filedialog.asksaveasfilename(defaultextension=".zip")
        if f:
            self.var_outfile.set(f)

    # Actions
    def gather_common(self):
        model = self.var_model.get().strip()
        region = self.var_region.get().strip()
        imei_str = self.var_imei.get().strip() or None
        if not model or not region:
            messagebox.showerror("Missing data", "Model and Region are required")
            return None
        return model, region, imei_str

    def on_check_update(self):
        common = self.gather_common()
        if not common:
            return
        model, region, _ = common
        # Disable button and show progress label while checking
        self.btn_check.config(state=tk.DISABLED, text="Checking")
        def worker():
            try:
                latest = versionfetch.getlatestver(model, region)
                self.after(0, lambda: self.lbl_latest.config(text=f"Latest: {latest}"))
                self.after(0, lambda: self.log("Latest version:", latest))
            except Exception as e:
                # Suppress messagebox for timeouts; just log and update UI politely
                try:
                    import requests
                    is_timeout = isinstance(e, requests.exceptions.Timeout)
                except Exception:
                    is_timeout = False
                if is_timeout:
                    self.after(0, lambda: self.lbl_latest.config(text="Latest: request timed out (try again)") )
                    self.after(0, lambda: self.log("Timeout while fetching latest version for", model, region))
                else:
                    err_text = str(e)
                    self.after(0, lambda msg=err_text: messagebox.showerror("Error", msg))
                    self.after(0, lambda msg=err_text: self.log("Error:", msg))
            finally:
                # Restore button regardless of outcome
                self.after(0, lambda: self.btn_check.config(state=tk.NORMAL, text="Check latest version"))
        threading.Thread(target=worker, daemon=True).start()

    def on_download(self):
        common = self.gather_common()
        if not common:
            return
        model, region, imei_input = common
        fwver = self.var_fwver.get().strip()
        outdir = self.var_outdir.get().strip()
        if not fwver:
            messagebox.showerror("Missing data", "Firmware version is required")
            return
        if not outdir:
            messagebox.showerror("Missing data", "Output directory is required")
            return
        os.makedirs(outdir, exist_ok=True)

        self.btn_download.config(state=tk.DISABLED)
        self.pb_download['value'] = 0
        self.pb_download['maximum'] = 100

        def worker():
            try:
                # Prepare args and fix IMEI if needed
                args = ArgsLike(dev_model=model, dev_region=region, dev_imei=imei_input, command="download")
                ret = imei.fixup_imei(args)
                if ret:
                    raise Exception("IMEI/serial missing or invalid. Provide IMEI prefix (>=8 digits) or serial in the IMEI field.")

                client = fusclient.FUSClient()
                path, filename, size = getbinaryfile(client, fwver, args.dev_model, args.dev_imei, args.dev_region)
                out_file = os.path.join(outdir, filename)

                resume = self.var_resume.get()
                try:
                    dloffset = os.stat(out_file).st_size if resume else 0
                except FileNotFoundError:
                    dloffset = 0

                self.after(0, lambda: self.log(("Resuming" if dloffset else "Downloading"), filename))
                if dloffset == size:
                    self.after(0, lambda: self.log("Already downloaded!"))
                    return

                initdownload(client, filename)
                r = client.downloadfile(path + filename, dloffset)

                # Setup progress bar
                self.after(0, lambda: self._set_progress(self.pb_download, dloffset, size))

                with open(out_file, "ab" if dloffset else "wb") as fd:
                    for chunk in r.iter_content(chunk_size=0x10000):
                        if chunk:
                            fd.write(chunk)
                            fd.flush()
                            # Update progress value
                            self.after(0, lambda: self._increment_progress(self.pb_download, 0x10000, size))

                self.after(0, lambda: self.log("Download complete:", out_file))

                if self.var_autodec.get():
                    dec_out = out_file.replace(".enc4", "").replace(".enc2", "")
                    if os.path.isfile(dec_out):
                        raise Exception(f"File {dec_out} already exists, refusing to auto-decrypt!")
                    self.after(0, lambda: self.log("Decrypting:", out_file))
                    version = 2 if filename.endswith(".enc2") else 4
                    # For decrypt, need fw_ver
                    args.fw_ver = fwver
                    decrypt_file(args, version, out_file, dec_out)
                    try:
                        os.remove(out_file)
                    except Exception:
                        pass
                    self.after(0, lambda: self.log("Decryption complete:", dec_out))

            except Exception as e:
                err_text = str(e)
                self.after(0, lambda msg=err_text: messagebox.showerror("Error", msg))
                self.after(0, lambda msg=err_text: self.log("Error:", msg))
            finally:
                self.after(0, lambda: self.btn_download.config(state=tk.NORMAL))
                self.after(0, lambda: self._reset_progress(self.pb_download))

        threading.Thread(target=worker, daemon=True).start()

    def _set_progress(self, pb: ttk.Progressbar, initial: int, total: int):
        pb['maximum'] = total
        pb['value'] = initial

    def _increment_progress(self, pb: ttk.Progressbar, delta: int, total: int):
        # Keep within total bounds
        new_val = min(pb['value'] + delta, total)
        pb['value'] = new_val

    def _reset_progress(self, pb: ttk.Progressbar):
        # leave progress at end to show completion; not resetting to zero here
        pass

    def on_decrypt(self):
        common = self.gather_common()
        if not common:
            return
        model, region, imei_input = common
        fwver = self.var_dec_fwver.get().strip()
        encver = int(self.var_encver.get())
        infile = self.var_infile.get().strip()
        outfile = self.var_outfile.get().strip()
        if not fwver or not infile or not outfile:
            messagebox.showerror("Missing data", "Firmware version, input file and output file are required")
            return
        if not os.path.isfile(infile):
            messagebox.showerror("Invalid input", "Encrypted input file does not exist")
            return

        self.btn_decrypt.config(state=tk.DISABLED)
        self.pb_decrypt['value'] = 0
        self.pb_decrypt['maximum'] = max(1, os.stat(infile).st_size)

        def worker():
            try:
                args = ArgsLike(dev_model=model, dev_region=region, dev_imei=imei_input, command="decrypt", enc_ver=encver, fw_ver=fwver)
                ret = imei.fixup_imei(args)
                if ret:
                    raise Exception("IMEI/serial missing or invalid. Provide IMEI prefix (>=8 digits) or serial in the IMEI field.")

                # Progress-wrapped decryption using crypt.decrypt_progress directly
                length = os.stat(infile).st_size
                with open(infile, "rb") as inf, open(outfile, "wb") as outf:
                    def progress_wrapper(src, dst, key, total_len):
                        # Wrap the writer to update progress as bytes are written
                        written = 0
                        def write_and_progress(data: bytes):
                            nonlocal written
                            n = dst.write(data)
                            written += n
                            self.after(0, lambda: self._set_progress(self.pb_decrypt, written, total_len))
                            return n
                        # monkey-patch write
                        class OutWrap:
                            def write(self, data):
                                return write_and_progress(data)
                        crypt.decrypt_progress(src, OutWrap(), key, total_len)
                    # Get key similarly to decrypt_file implementation
                    getkey = crypt.getv2key if encver == 2 else crypt.getv4key
                    key = getkey(args.fw_ver, args.dev_model, args.dev_region, args.dev_imei)
                    if not key:
                        raise Exception("Failed to obtain decryption key")
                    progress_wrapper(inf, outf, key, length)
                self.after(0, lambda: self.log("Decryption complete:", outfile))
            except Exception as e:
                err_text = str(e)
                self.after(0, lambda msg=err_text: messagebox.showerror("Error", msg))
                self.after(0, lambda msg=err_text: self.log("Error:", msg))
            finally:
                self.after(0, lambda: self.btn_decrypt.config(state=tk.NORMAL))

        threading.Thread(target=worker, daemon=True).start()


def main():
    app = SamloaderGUI()
    app.mainloop()


if __name__ == "__main__":
    main()
