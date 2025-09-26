# SPDX-License-Identifier: GPL-3.0+
# Copyright (C) 2020 nlscc

""" Calculate keys and decrypt encrypted firmware packages. """

import hashlib
import xml.etree.ElementTree as ET
from Cryptodome.Cipher import AES
from tqdm import tqdm

from . import fusclient
from . import request
from . import versionfetch

# PKCS#7 unpad
unpad = lambda d: d[:-d[-1]]

def getv4key(version, model, region, imei):
    """ Retrieve the AES key for V4 encryption with robust fallbacks.
    Tries multiple XML layouts and retries BinaryInform with alternative DEVICE_LOCAL_CODE,
    and falls back to the latest available firmware if Samsung no longer serves the requested build.
    """
    client = fusclient.FUSClient()
    version_norm = versionfetch.normalizevercode(version)

    def try_binary_inform(ver: str, force_region_local_code: bool = False) -> str:
        req = request.binaryinform(ver, model, region, imei, client.nonce, use_region_local_code=force_region_local_code)
        return client.makereq("NF_DownloadBinaryInform.do", req)

    # First attempt: inferred multi-CSC local code (EUX/OXM/etc.)
    resp = try_binary_inform(version_norm, force_region_local_code=False)

    def parse_status(xml_text: str) -> int:
        try:
            root_local = ET.fromstring(xml_text)
            status_el = root_local.find("./FUSBody/Results/Status")
            if status_el is not None and status_el.text and status_el.text.isdigit():
                return int(status_el.text)
        except Exception:
            pass
        return 0

    status = parse_status(resp)

    # If status not OK, try forcing DEVICE_LOCAL_CODE to the provided region (instead of OXM/EUX token)
    if status != 200:
        try:
            # Only retry if effective local code would differ from region
            effective = request._effective_local_code(version_norm, region)
            if effective != region:
                resp2 = try_binary_inform(version_norm, force_region_local_code=True)
                if parse_status(resp2) == 200:
                    resp = resp2
                    status = 200
        except Exception:
            pass

    # If still not OK, try with the latest available version (Samsung often serves only the latest)
    if status != 200:
        try:
            latest = versionfetch.getlatestver(model, region)
            # Try with inferred local code first, then forced region if needed
            resp_latest = try_binary_inform(latest, force_region_local_code=False)
            if parse_status(resp_latest) != 200:
                resp_latest2 = try_binary_inform(latest, force_region_local_code=True)
                if parse_status(resp_latest2) == 200:
                    resp_latest = resp_latest2
            if parse_status(resp_latest) == 200:
                resp = resp_latest
                version_norm = latest
                status = 200
        except Exception:
            pass

    try:
        root = ET.fromstring(resp)
        # Try multiple possible locations for tags; Samsung has moved these around over time.
        fwver_el = (
            root.find("./FUSBody/Results/LATEST_FW_VERSION/Data")
            or root.find(".//LATEST_FW_VERSION/Data")
            or root.find("./FUSBody/Results/FW_VERSION/Data")
            or root.find(".//FW_VERSION/Data")
        )
        fwver = fwver_el.text if fwver_el is not None and fwver_el.text else None

        logic_el = (
            root.find("./FUSBody/Put/LOGIC_VALUE_FACTORY/Data")
            or root.find("./FUSBody/Results/LOGIC_VALUE_FACTORY/Data")
            or root.find(".//LOGIC_VALUE_FACTORY/Data")
            or root.find(".//LOGIC_VALUE/Data")
        )
        logicval = logic_el.text if logic_el is not None and logic_el.text else None
    except Exception:
        # Fall back to regex-based extraction if XML parsing/path selection fails for any reason
        fwver = None
        logicval = None
    # Regex fallback (structure-agnostic)
    if fwver is None:
        import re
        m_fw = re.search(r"<LATEST_FW_VERSION><Data>(.*?)</Data></LATEST_FW_VERSION>", resp)
        if not m_fw:
            m_fw = re.search(r"<FW_VERSION><Data>(.*?)</Data></FW_VERSION>", resp)
        fwver = m_fw.group(1) if m_fw else version_norm
    if not logicval:
        import re
        m_lg = re.search(r"<LOGIC_VALUE_FACTORY><Data>(.*?)</Data></LOGIC_VALUE_FACTORY>", resp)
        if not m_lg:
            m_lg = re.search(r"<LOGIC_VALUE><Data>(.*?)</Data></LOGIC_VALUE>", resp)
        if not m_lg:
            print("Could not get decryption key from servers - bad model/region/imei?")
            return None
        logicval = m_lg.group(1)
    deckey = request.getlogiccheck(fwver, logicval)
    return hashlib.md5(deckey.encode()).digest()

def getv2key(version, model, region, _imei):
    """ Calculate the AES key for V2 (legacy) encryption. """
    deckey = region + ":" + model + ":" + version
    return hashlib.md5(deckey.encode()).digest()

def decrypt_progress(inf, outf, key, length):
    """ Decrypt a stream of data while showing a progress bar. """
    cipher = AES.new(key, AES.MODE_ECB)
    if length % 16 != 0:
        raise Exception("invalid input block size")
    # Number of 4096-byte chunks (ceil division)
    chunks = (length + 4095) // 4096
    pbar = tqdm(total=length, unit="B", unit_scale=True)
    try:
        for i in range(chunks):
            block = inf.read(4096)
            if not block:
                break
            decblock = cipher.decrypt(block)
            if i == chunks - 1:
                outf.write(unpad(decblock))
            else:
                outf.write(decblock)
            pbar.update(len(block))
    finally:
        pbar.close()
