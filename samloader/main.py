# SPDX-License-Identifier: GPL-3.0+
# Copyright (C) 2020 nlscc

import argparse
import os
import base64
import xml.etree.ElementTree as ET
from tqdm import tqdm

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
            r = client.downloadfile(path+filename, dloffset)
            if args.show_md5 and "Content-MD5" in r.headers:
                try:
                    print("MD5:", base64.b64decode(r.headers["Content-MD5"]).hex())
                except Exception:
                    print("MD5: <unavailable>")
            pbar = tqdm(total=size, initial=dloffset, unit="B", unit_scale=True)
            try:
                with open(out, "ab" if args.resume else "wb") as fd:
                    for chunk in r.iter_content(chunk_size=0x10000):
                        if not chunk:
                            continue
                        fd.write(chunk)
                        fd.flush()
                        pbar.update(len(chunk))
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
