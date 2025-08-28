# SPDX-License-Identifier: GPL-3.0+
# Copyright (C) 2020 nlscc

import argparse
import os
import base64
import xml.etree.ElementTree as ET
from tqdm import tqdm
import threading
import time

from . import request
from . import crypt
from . import fusclient
from . import versionfetch
from . import imei

def main():
    parser = argparse.ArgumentParser(description="Download and query firmware for Samsung devices.")
    parser.add_argument("-m", "--dev-model", help="device model")
    parser.add_argument("-r", "--dev-region", help="device region code")
    parser.add_argument("-i", "--dev-imei", help="device imei code (guessed from model if possible)")
    parser.add_argument("--listregions", action="store_true", help="list known CSC regions and exit")
    subparsers = parser.add_subparsers(dest="command")
    # New: generate plausible IMEI from model using TAC DB
    genimei = subparsers.add_parser("genimei", help="generate a plausible IMEI for a model using TAC database")
    genimei.add_argument("--model", "-m", dest="model_only", required=True, help="device model (e.g., SM-S918B)")
    # New: show history
    hist = subparsers.add_parser("history", help="show download history")
    hist.add_argument("--limit", type=int, default=20, help="number of entries to show (default: 20)")

    dload = subparsers.add_parser("download", help="download a firmware")
    dload.add_argument("-v", "--fw-ver", help="firmware version to download", required=True)
    dload.add_argument("-R", "--resume", help="resume an unfinished download", action="store_true")
    dload.add_argument("-M", "--show-md5", help="print the expected MD5 hash of the downloaded file", action="store_true")
    dload.add_argument("-D", "--do-decrypt", help="auto-decrypt the downloaded file after downloading", action="store_true")
    dload.add_argument("-T", "--threads", type=int, default=1, help="number of download threads (default: 1)")
    dload.add_argument("--retries", type=int, default=10, help="max consecutive retry attempts on connection errors (default: 10)")
    dload_out = dload.add_mutually_exclusive_group(required=True)
    dload_out.add_argument("-O", "--out-dir", help="output the server filename to the specified directory")
    dload_out.add_argument("-o", "--out-file", help="output to the specified file")
    chkupd = subparsers.add_parser("checkupdate", help="check for the latest available firmware version")
    chkupd.add_argument("--raw", action="store_true", help="print raw four-part version code only")
    decrypt = subparsers.add_parser("decrypt", help="decrypt an encrypted firmware")
    decrypt.add_argument("-v", "--fw-ver", help="encrypted firmware version", required=True)
    decrypt.add_argument("-V", "--enc-ver", type=int, choices=[2, 4], default=None, help="encryption version (auto-detected if omitted)")
    decrypt.add_argument("-i", "--in-file", help="encrypted firmware file input", required=True)
    decrypt.add_argument("-o", "--out-file", help="decrypted firmware file output", required=True)
    args = parser.parse_args()

    # Handle standalone region list request early
    if getattr(args, "listregions", False):
        try:
            from .regions import iter_regions_sorted
            for code, name in iter_regions_sorted():
                print(f"- {code} ({name})")
            return 0
        except ModuleNotFoundError:
            # Fallback list embedded to support older installs missing samloader.regions
            REGION_INFO = {
                "AUT": "Switzerland, no brand",
                "ATO": "Austria, no brand",
                "BTU": "United Kingdom, no brand",
                "DBT": "Germany, no brand",
                "ITV": "Italy, no brand",
                "XEF": "France, no brand",
                "XEH": "Hungary, no brand",
                "XEO": "Poland, no brand",
                "XEU": "United Kingdom & Ireland (Multi-CSC)",
                "INS": "India, no brand",
                "INU": "India (alternate), no brand",
                "XAA": "USA, unlocked (no brand)",
                "XFA": "South Africa, no brand",
                "XFE": "South Africa, no brand",
                "XID": "Indonesia, no brand",
                "XME": "Malaysia, no brand",
                "XSA": "Australia, no brand",
                "XSG": "United Arab Emirates, no brand",
                "XSP": "Singapore, no brand",
                "XTC": "Philippines, no brand",
                "TGY": "Hong Kong, no brand",
                "TPE": "Taiwan, no brand",
                "THL": "Thailand, no brand",
                "ZTO": "Brazil, no brand",
                "EGY": "Egypt, no brand",
                "KSA": "Saudi Arabia, no brand",
                "PAK": "Pakistan, no brand",
                "CPW": "United Kingdom, Carphone Warehouse",
                "EVR": "United Kingdom, EE",
                "H3G": "United Kingdom, Three",
                "O2U": "United Kingdom, O2",
                "VOD": "United Kingdom, Vodafone",
                "DTM": "Germany, T-Mobile",
                "VD2": "Germany, Vodafone",
                "OMN": "Italy, Vodafone (ex-Omnitel)",
                "TIM": "Italy, TIM",
                "ATT": "USA, AT&T",
                "SPR": "USA, Sprint",
                "TMB": "USA, T-Mobile",
                "USC": "USA, US Cellular",
                "VZW": "USA, Verizon",
                "CHO": "Chile, no brand",
                "TFG": "Mexico, Telcel",
                "TPA": "Panama, no brand",
                "UNE": "Colombia, UNE",
                "OPS": "Australia, Optus",
                "TEL": "Australia, Telstra",
                "VAU": "Australia, Vodafone",
            }
            for code in sorted(REGION_INFO.keys()):
                print(f"- {code} ({REGION_INFO[code]})")
            return 0
        except Exception as e:
            print(f"Error: failed to list regions: {e}")
            return 1

    # If no subcommand was provided, show usage and exit
    if not args.command:
        parser.print_help()
        return 0

    # Handle simple utility subcommands early
    if args.command == "genimei":
        try:
            from .tacdb import generate_imei_from_model
        except Exception:
            print("Error: TAC database support is not available in this build.")
            return 1
        gen = generate_imei_from_model(args.model_only)
        if not gen:
            print(f"No TAC found for model {args.model_only}")
            return 1
        print(gen)
        return 0
    if args.command == "history":
        import json
        hist_path = os.path.join(os.path.expanduser("~"), ".samloader", "history.json")
        try:
            with open(hist_path, "r", encoding="utf-8") as fh:
                data = json.load(fh) or []
        except Exception:
            data = []
        if not data:
            print("No history yet.")
            return 0
        lim = max(1, int(getattr(args, "limit", 20) or 20))
        for item in data[-lim:][::-1]:
            print(f"- {item.get('time','')}  {item.get('model','')} {item.get('region','')}  {item.get('version','')}\n  {item.get('file','')}")
        return 0

    # Note: IMEI/serial validation is performed later within each command
    # (download always; decrypt only if encryption is detected as V4).

    # Defer imports used for error classification
    import xml.etree.ElementTree as ET
    import requests

    try:
        if args.command == "download":
            if not args.dev_model or not args.dev_region:
                print("Error: --dev-model and --dev-region are required for download")
                return 1
            # Validate/fix IMEI or serial for download
            if imei.fixup_imei(args):
                return 1
            client = fusclient.FUSClient()
            path, filename, size = getbinaryfile(client, args.fw_ver, args.dev_model, args.dev_imei, args.dev_region)
            out = args.out_file if args.out_file else os.path.join(args.out_dir, filename)
            try:
                dloffset = os.stat(out).st_size if args.resume else 0
            except FileNotFoundError:
                args.resume = None
                dloffset = 0

            print("resuming" if args.resume else "downloading", filename)
            if dloffset == size:
                print("already downloaded!")
                return 0
            initdownload(client, filename)
            # Multi-threaded segmented download when requested and starting fresh
            if getattr(args, "threads", 1) > 1 and not args.resume and dloffset == 0:
                threads_num = max(1, int(args.threads))
                # Optionally show MD5 if available (fetch tiny range to get headers)
                if args.show_md5:
                    try:
                        rhead = client.downloadfile(path + filename, 0, 0)
                        if "Content-MD5" in rhead.headers:
                            print("MD5:", base64.b64decode(rhead.headers["Content-MD5"]).hex())
                        rhead.close()
                    except Exception:
                        print("MD5: <unavailable>")
                # Preallocate file
                try:
                    with open(out, "wb") as fd:
                        fd.truncate(size)
                except Exception:
                    # Fallback preallocation method
                    with open(out, "wb") as fd:
                        if size > 0:
                            fd.seek(size - 1)
                            fd.write(b"\0")
                # Dynamic chunk-queue downloader with 1 GiB chunks
                import queue
                CHUNK = 64 * 1024 * 1024  # 64 MiB
                chunks_q = queue.Queue()
                # Enqueue chunks as (start, end) inclusive
                off = 0
                while off < size:
                    end = min(off + CHUNK, size) - 1
                    chunks_q.put((off, end))
                    off = end + 1
                pbar = tqdm(total=size, initial=0, unit="B", unit_scale=True)
                pbar_lock = threading.Lock()
                stop_event = threading.Event()
                errors = []
                def dl_worker():
                    while not stop_event.is_set():
                        try:
                            st, en = chunks_q.get_nowait()
                        except Exception:
                            return
                        pos = st
                        attempts = 0
                        backoff = 1
                        while pos <= en and not stop_event.is_set():
                            try:
                                r = client.downloadfile(path + filename, pos, en)
                                with open(out, "r+b") as fdw:
                                    fdw.seek(pos)
                                    for chunk in r.iter_content(chunk_size=0x10000):
                                        if stop_event.is_set():
                                            return
                                        if not chunk:
                                            continue
                                        fdw.write(chunk)
                                        pos += len(chunk)
                                        with pbar_lock:
                                            pbar.update(len(chunk))
                                attempts = 0
                                backoff = 1
                            except Exception as e:
                                attempts += 1
                                if attempts > args.retries:
                                    errors.append(e)
                                    stop_event.set()
                                    return
                                time.sleep(min(60, backoff))
                                backoff = min(60, backoff * 2)
                        chunks_q.task_done()
                tlist = []
                for _ in range(threads_num):
                    t = threading.Thread(target=dl_worker, daemon=True)
                    t.start()
                    tlist.append(t)
                for t in tlist:
                    t.join()
                pbar.close()
                if errors:
                    print(f"Error: download failed: {errors[0]}")
                    return 1
            else:
                # Fallback to single-threaded download (supports resume + auto-retry)
                if getattr(args, "threads", 1) > 1 and (args.resume or dloffset > 0):
                    print("Note: resume or existing partial download disables multi-thread; falling back to single-thread.")
                # Prepare output file
                if not args.resume:
                    # Start fresh: truncate file to zero to avoid mixing with old partials
                    with open(out, "wb"):
                        pass
                elif not os.path.exists(out):
                    # Resume requested but file missing: create it empty and continue from 0
                    with open(out, "wb"):
                        pass
                pos = dloffset
                attempts = 0
                backoff = 1
                md5_printed = False
                pbar = tqdm(total=size, initial=dloffset, unit="B", unit_scale=True)
                try:
                    while pos < size:
                        try:
                            r = client.downloadfile(path+filename, pos)
                            if args.show_md5 and not md5_printed and "Content-MD5" in r.headers:
                                try:
                                    print("MD5:", base64.b64decode(r.headers["Content-MD5"]).hex())
                                except Exception:
                                    print("MD5: <unavailable>")
                                md5_printed = True
                            with open(out, "r+b") as fd:
                                fd.seek(pos)
                                for chunk in r.iter_content(chunk_size=0x10000):
                                    if not chunk:
                                        continue
                                    fd.write(chunk)
                                    fd.flush()
                                    pos += len(chunk)
                                    pbar.update(len(chunk))
                            # Successful stream; reset attempts and backoff for next loop (if any)
                            attempts = 0
                            backoff = 1
                        except Exception as e:
                            attempts += 1
                            if attempts > args.retries:
                                print(f"Error: download failed after {args.retries} retries: {e}")
                                return 1
                            time.sleep(min(60, backoff))
                            backoff *= 2
                            # Re-evaluate current pos from disk to avoid duplicating bytes
                            try:
                                pos = os.stat(out).st_size
                            except Exception:
                                pass
                finally:
                    pbar.close()
            if args.do_decrypt: # decrypt the file if needed
                # Remove a single trailing .enc2/.enc4 extension if present
                dec = out[:-5] if out.lower().endswith(".enc4") else (out[:-5] if out.lower().endswith(".enc2") else out)
                if os.path.isfile(dec):
                    print(f"file {dec} already exists, refusing to auto-decrypt!")
                    return 1
                print("decrypting", out)
                version = 2 if filename.endswith(".enc2") else 4
                decrypt_file(args, version, out, dec)
                os.remove(out)

        elif args.command == "checkupdate":
            if not args.dev_model or not args.dev_region:
                print("Error: --dev-model and --dev-region are required for checkupdate")
                return 1
            ver = versionfetch.getlatestver(args.dev_model, args.dev_region)
            if getattr(args, "raw", False):
                print(ver)
            else:
                parts = ver.split("/")
                # Expect 4 parts after normalization
                ap = parts[0] if len(parts) > 0 else "-"
                csc = parts[1] if len(parts) > 1 else "-"
                cp = parts[2] if len(parts) > 2 else "-"
                build = parts[3] if len(parts) > 3 else "-"
                print(f"AP: {ap}")
                print(f"CSC: {csc}")
                print(f"CP: {cp}")
                print(f"Build: {build}")
            # Interactive flow: ask to download and then to decrypt
            try:
                resp = input("Do you want to download this firmware now? [y/N]: ").strip().lower()
            except EOFError:
                resp = ""
            if resp in ("y", "yes"):
                # Ensure IMEI/serial is available for download
                class _DLArgs:
                    pass
                dlargs = _DLArgs()
                dlargs.dev_model = args.dev_model
                dlargs.dev_region = args.dev_region
                dlargs.dev_imei = getattr(args, "dev_imei", None)
                dlargs.command = "download"
                # fixup IMEI (may expand a prefix)
                if imei.fixup_imei(dlargs):
                    print("Error: IMEI/serial is required to download. Please re-run with -i/--dev-imei.")
                    return 1
                # Start download into current working directory
                client = fusclient.FUSClient()
                path, filename, size = getbinaryfile(client, ver, dlargs.dev_model, dlargs.dev_imei, dlargs.dev_region)
                out = os.path.join(os.getcwd(), filename)
                try:
                    dloffset = os.stat(out).st_size
                except FileNotFoundError:
                    dloffset = 0
                print(("resuming" if dloffset else "downloading"), filename)
                if dloffset == size:
                    print("already downloaded!")
                    download_ok = True
                else:
                    initdownload(client, filename)
                    # Prepare output file (resume-aware)
                    if dloffset == 0:
                        with open(out, "wb"):
                            pass
                    pos = dloffset
                    attempts = 0
                    backoff = 1
                    pbar = tqdm(total=size, initial=dloffset, unit="B", unit_scale=True)
                    try:
                        while pos < size:
                            try:
                                r = client.downloadfile(path + filename, pos)
                                with open(out, "r+b") as fd:
                                    fd.seek(pos)
                                    for chunk in r.iter_content(chunk_size=0x10000):
                                        if not chunk:
                                            continue
                                        fd.write(chunk)
                                        fd.flush()
                                        pos += len(chunk)
                                        pbar.update(len(chunk))
                                attempts = 0
                                backoff = 1
                            except Exception as e:
                                attempts += 1
                                if attempts > 10:
                                    print(f"Error: download failed after 10 retries: {e}")
                                    return 1
                                time.sleep(min(60, backoff))
                                backoff *= 2
                                try:
                                    pos = os.stat(out).st_size
                                except Exception:
                                    pass
                    finally:
                        pbar.close()
                    download_ok = (pos >= size)
                if download_ok:
                    try:
                        resp2 = input("Do you want to decrypt it in the current directory? [y/N]: ").strip().lower()
                    except EOFError:
                        resp2 = ""
                    if resp2 in ("y", "yes"):
                        # Determine enc version from filename
                        encver = 2 if filename.lower().endswith(".enc2") else 4
                        dec = out[:-5] if out.lower().endswith(".enc4") else (out[:-5] if out.lower().endswith(".enc2") else out)
                        if os.path.isfile(dec):
                            print(f"file {dec} already exists, refusing to decrypt!")
                            return 1
                        # Prepare args for decrypt
                        dlargs.fw_ver = ver
                        ret = decrypt_file(dlargs, encver, out, dec)
                        if ret == 0:
                            print("decryption complete:", dec)
                        else:
                            print("Error: decryption failed")
                            return ret
        elif args.command == "decrypt":
            if not args.dev_model or not args.dev_region:
                print("Error: --dev-model and --dev-region are required for decrypt")
                return 1
            # Determine encryption version automatically if not provided
            encver = args.enc_ver
            infile = args.in_file
            if encver is None:
                low = infile.lower()
                if low.endswith(".enc2"):
                    encver = 2
                elif low.endswith(".enc4"):
                    encver = 4
                else:
                    # Heuristic: try V2 first by checking ZIP magic after decrypting the first block
                    try:
                        from Cryptodome.Cipher import AES
                        from . import crypt as _crypt_mod
                        v2key = _crypt_mod.getv2key(args.fw_ver, args.dev_model, args.dev_region, None)
                        cipher = AES.new(v2key, AES.MODE_ECB)
                        with open(infile, "rb") as f:
                            head = f.read(4096 if os.path.getsize(infile) >= 4096 else 16)
                        dec = cipher.decrypt(head[:16]) if len(head) >= 16 else b""
                        if dec.startswith(b"PK\x03\x04"):
                            encver = 2
                        else:
                            encver = 4
                    except Exception:
                        # Fallback to v4 if detection fails for any reason
                        encver = 4
                print(f"Detected encryption version: V{encver}")
            # For V4, ensure IMEI/serial is present/valid
            if encver == 4:
                # Create a shallow copy-like to set enc_ver for imei logic if needed
                class _ArgsView:
                    pass
                av = _ArgsView()
                av.__dict__.update(vars(args))
                av.enc_ver = 4
                if imei.fixup_imei(av):
                    return 1
                # propagate possibly filled imei back
                args.dev_imei = getattr(av, "dev_imei", args.dev_imei)
            return decrypt_file(args, encver, args.in_file, args.out_file)
        return 0
    except requests.exceptions.Timeout:
        print("Error: network timeout while contacting the server. Please try again later.")
        return 2
    except requests.exceptions.HTTPError as e:
        status = getattr(getattr(e, 'response', None), 'status_code', None)
        if status:
            print(f"Error: HTTP {status} from server.")
        else:
            print("Error: HTTP error while contacting the server.")
        return 2
    except requests.exceptions.RequestException as e:
        # Generic requests-level errors (connection, DNS, etc.)
        print(f"Error: network error: {e}")
        return 2
    except ET.ParseError:
        print("Error: received an invalid or unexpected response from the server.")
        return 3
    except Exception as e:
        # Catch-all to avoid Python tracebacks for users
        print(f"Error: {e}")
        return 1

def decrypt_file(args, version, encrypted, decrypted):
    if version not in [2, 4]:
        raise Exception("Unknown encryption version: {}".format(version))
    getkey = crypt.getv2key if version == 2 else crypt.getv4key
    key = getkey(args.fw_ver, args.dev_model, args.dev_region, args.dev_imei)
    if not key:
        return 1
    length = os.stat(encrypted).st_size
    with open(encrypted, "rb") as inf, open(decrypted, "wb") as outf:
        crypt.decrypt_progress(inf, outf, key, length)
    return 0

def initdownload(client, filename):
    req = request.binaryinit(filename, client.nonce)
    resp = client.makereq("NF_DownloadBinaryInitForMass.do", req)

def getbinaryfile(client, fw, model, imei, region):
    # Normalize the firmware version string to the expected 4-part form
    try:
        from .versionfetch import normalizevercode
        fw = normalizevercode(fw)
    except Exception:
        pass

    def _inform_try(version: str):
        reqx = request.binaryinform(version, model, region, imei, client.nonce, use_region_local_code=False)
        respx = client.makereq("NF_DownloadBinaryInform.do", reqx)
        rootx = ET.fromstring(respx)
        statusx = int(rootx.find("./FUSBody/Results/Status").text)
        if statusx != 200:
            # Fallback: retry forcing the sales CSC as DEVICE_LOCAL_CODE
            try:
                effective = request._effective_local_code(version, region)
            except Exception:
                effective = region
            if effective != region:
                req2 = request.binaryinform(version, model, region, imei, client.nonce, use_region_local_code=True)
                resp2 = client.makereq("NF_DownloadBinaryInform.do", req2)
                root2 = ET.fromstring(resp2)
                status2 = int(root2.find("./FUSBody/Results/Status").text)
                if status2 == 200:
                    return root2
                else:
                    raise Exception(f"DownloadBinaryInform returned {statusx} (local_code={effective}) and {status2} (local_code={region}), firmware could not be found?")
            else:
                raise Exception(f"DownloadBinaryInform returned {statusx}, firmware could not be found?")
        return rootx

    # First attempt: requested version
    try:
        root = _inform_try(fw)
    except Exception as first_err:
        # As per Samsung policy, fall back to latest firmware if requested build isn't served
        try:
            from .versionfetch import getlatestver
            latest = getlatestver(model, region)
            root = _inform_try(latest)
        except Exception:
            # Re-raise original error if latest also fails
            raise first_err

    filename = root.find("./FUSBody/Put/BINARY_NAME/Data").text
    if filename is None:
        raise Exception("DownloadBinaryInform failed to find a firmware bundle")
    size = int(root.find("./FUSBody/Put/BINARY_BYTE_SIZE/Data").text)
    path = root.find("./FUSBody/Put/MODEL_PATH/Data").text
    return path, filename, size
