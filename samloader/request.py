# SPDX-License-Identifier: GPL-3.0+
# Copyright (C) 2020 nlscc

""" Build FUS XML requests. """

import xml.etree.ElementTree as ET

def getlogiccheck(inp: str, nonce: str) -> str:
    """ Calculate the request checksum for a given input and nonce. """
    if len(inp) < 16:
        raise Exception("getlogiccheck() input too short: did you specify the full version?")
    out = ""
    for c in nonce:
        out += inp[ord(c) & 0xf]
    return out

def build_reqhdr(fusmsg: ET.Element):
    """ Build the FUSHdr of an XML message. """
    fushdr = ET.SubElement(fusmsg, "FUSHdr")
    ET.SubElement(fushdr, "ProtoVer").text = "1.0"

def build_reqbody(fusmsg: ET.Element, params: dict):
    """ Build the FUSBody of an XML message. """
    fusbody = ET.SubElement(fusmsg, "FUSBody")
    fput = ET.SubElement(fusbody, "Put")
    for tag, value in params.items():
        setag = ET.SubElement(fput, tag)
        sedata = ET.SubElement(setag, "Data")
        sedata.text = str(value)

def _effective_local_code(fwv: str, region: str) -> str:
    """Return the effective DEVICE_LOCAL_CODE for BinaryInform.
    Some multi-CSC packages require using the multi-CSC code from the CSC build
    (e.g., OXM/OXA/OWO/OMC) rather than the sales code (e.g., INS/BTU).
    If the CSC part of the version embeds one of these tokens, prefer it.
    Otherwise, use the provided region.
    """
    try:
        parts = (fwv or "").split("/")
        csc_build = parts[1] if len(parts) > 1 else ""
        tokens = ("OXM", "OXA", "OWO", "OMC")
        for t in tokens:
            if t in csc_build:
                return t
    except Exception:
        pass
    return region


def binaryinform(fwv: str, model: str, region: str, imei: str, nonce: str) -> str:
    """ Build a BinaryInform request. """
    local_code = _effective_local_code(fwv, region)
    fusmsg = ET.Element("FUSMsg")
    build_reqhdr(fusmsg)
    build_reqbody(fusmsg, {
        "ACCESS_MODE": 2,
        "BINARY_NATURE": 1,
        "CLIENT_PRODUCT": "Smart Switch",
        "CLIENT_VERSION": "4.3.23123_1",
        "DEVICE_IMEI_PUSH": imei,
        "DEVICE_FW_VERSION": fwv,
        "DEVICE_LOCAL_CODE": local_code,
        "DEVICE_MODEL_NAME": model,
        "LOGIC_CHECK": getlogiccheck(fwv, nonce)
    })
    return ET.tostring(fusmsg)

def binaryinit(filename: str, nonce: str) -> str:
    """ Build a BinaryInit request. """
    fusmsg = ET.Element("FUSMsg")
    build_reqhdr(fusmsg)
    checkinp = filename.split(".")[0][-16:]
    build_reqbody(fusmsg, {
        "BINARY_FILE_NAME": filename,
        "LOGIC_CHECK": getlogiccheck(checkinp, nonce)
    })
    return ET.tostring(fusmsg)
