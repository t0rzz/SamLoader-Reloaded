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
    decrypt.add_argument("-V", "--enc-ver", type=int, choices=[2, 4], default=4, help="encryption version (default 4)")
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

    # Fix or validate IMEI/serial early for commands that need it
    if imei.fixup_imei(args):
        return 1

    # Defer imports used for error classification
    import xml.etree.ElementTree as ET
    import requests

    try:
        if args.command == "download":
            if not args.dev_model or not args.dev_region:
                print("Error: --dev-model and --dev-region are required for download")
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
                segsize = size // threads_num
                ranges = []
                for i in range(threads_num):
                    start_i = i * segsize
                    end_i = (start_i + segsize - 1) if i < threads_num - 1 else (size - 1)
                    ranges.append((start_i, end_i))
                pbar = tqdm(total=size, initial=0, unit="B", unit_scale=True)
                pbar_lock = threading.Lock()
                stop_event = threading.Event()
                errors = []
                def dl_worker(st, en):
                    pos = st
                    attempts = 0
                    backoff = 1
                    while pos <= en and not stop_event.is_set():
                        try:
                            r = client.downloadfile(path + filename, pos, en)
                            with open(out, "r+b") as fdw:
                                for chunk in r.iter_content(chunk_size=0x10000):
                                    if stop_event.is_set():
                                        return
                                    if not chunk:
                                        continue
                                    fdw.seek(pos)
                                    fdw.write(chunk)
                                    pos += len(chunk)
                                    with pbar_lock:
                                        pbar.update(len(chunk))
                            attempts = 0
                            backoff = 1
                            # If stream ended but segment not complete, loop will retry
                        except Exception as e:
                            attempts += 1
                            if attempts > args.retries:
                                errors.append(e)
                                stop_event.set()
                                return
                            sleep_s = min(60, backoff)
                            # Avoid noisy logs from threads; just backoff and retry
                            time.sleep(sleep_s)
                            backoff *= 2
                tlist = []
                for st, en in ranges:
                    t = threading.Thread(target=dl_worker, args=(st, en), daemon=True)
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
        elif args.command == "decrypt":
            if not args.dev_model or not args.dev_region:
                print("Error: --dev-model and --dev-region are required for decrypt")
                return 1
            return decrypt_file(args, args.enc_ver, args.in_file, args.out_file)
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
    req = request.binaryinform(fw, model, region, imei, client.nonce)
    resp = client.makereq("NF_DownloadBinaryInform.do", req)
    root = ET.fromstring(resp)
    status = int(root.find("./FUSBody/Results/Status").text)
    if status != 200:
        raise Exception("DownloadBinaryInform returned {}, firmware could not be found?".format(status))
    filename = root.find("./FUSBody/Put/BINARY_NAME/Data").text
    if filename is None:
        raise Exception("DownloadBinaryInform failed to find a firmware bundle")
    size = int(root.find("./FUSBody/Put/BINARY_BYTE_SIZE/Data").text)
    path = root.find("./FUSBody/Put/MODEL_PATH/Data").text
    return path, filename, size
