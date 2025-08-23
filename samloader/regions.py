# SPDX-License-Identifier: GPL-3.0+
""" CSC (region) catalog for Samsung firmware servers.

This module provides a comprehensive CSC list for Samsung firmware queries.
It prefers a remotely maintained dataset (from t0rzz/samloader) with local
caching, falls back to a packaged JSON file, and finally to a minimal
built-in list to ensure functionality even offline.
"""

from __future__ import annotations

import json
import os
from typing import Dict, Iterable, Tuple

# Minimal built-in fallback (guaranteed available)
_FALLBACK_REGION_INFO: Dict[str, str] = {
    # Unbranded / open market (Europe)
    "AUT": "Switzerland, no brand",
    "ATO": "Austria, no brand",
    "BTU": "United Kingdom, no brand",
    "DBT": "Germany, no brand",
    "ITV": "Italy, no brand",
    "XEF": "France, no brand",
    "XEH": "Hungary, no brand",
    "XEO": "Poland, no brand",
    "XEU": "United Kingdom & Ireland (Multi-CSC)",

    # Unbranded (Global)
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

    # Europe carriers/country variants
    "CPW": "United Kingdom, Carphone Warehouse",
    "EVR": "United Kingdom, EE",
    "H3G": "United Kingdom, Three",
    "O2U": "United Kingdom, O2",
    "VOD": "United Kingdom, Vodafone",
    "DTM": "Germany, T-Mobile",
    "VD2": "Germany, Vodafone",
    "OMN": "Italy, Vodafone (ex-Omnitel)",
    "TIM": "Italy, TIM",

    # Americas
    "ATT": "USA, AT&T",
    "SPR": "USA, Sprint",
    "TMB": "USA, T-Mobile",
    "USC": "USA, US Cellular",
    "VZW": "USA, Verizon",
    "CHO": "Chile, no brand",
    "TFG": "Mexico, Telcel",
    "TPA": "Panama, no brand",
    "UNE": "Colombia, UNE",

    # Oceania carriers
    "OPS": "Australia, Optus",
    "TEL": "Australia, Telstra",
    "VAU": "Australia, Vodafone",
}

_REMOTE_URL = "https://raw.githubusercontent.com/t0rzz/SamLoader-Reloaded/master/samloader/data/regions.json"
_CACHE_DIR = os.path.join(os.path.expanduser("~"), ".samloader")
_CACHE_FILE = os.path.join(_CACHE_DIR, "regions.json")


def _load_packaged_regions() -> Dict[str, str]:
    """Load regions from the packaged JSON file (shipped with the package)."""
    # Prefer importlib.resources (Py>=3.9 for files()), fall back to pkgutil
    try:
        from importlib.resources import files
        data_path = files("samloader").joinpath("data").joinpath("regions.json")
        with open(str(data_path), "r", encoding="utf-8") as fh:
            return json.load(fh)
    except Exception:
        try:
            import pkgutil
            data = pkgutil.get_data("samloader", "data/regions.json")
            if data is not None:
                return json.loads(data.decode("utf-8"))
        except Exception:
            pass
    return {}


def _load_cache() -> Dict[str, str]:
    try:
        with open(_CACHE_FILE, "r", encoding="utf-8") as fh:
            return json.load(fh)
    except Exception:
        return {}


def _save_cache(regions: Dict[str, str]) -> None:
    try:
        os.makedirs(_CACHE_DIR, exist_ok=True)
        with open(_CACHE_FILE, "w", encoding="utf-8") as fh:
            json.dump(regions, fh, ensure_ascii=False, indent=2)
    except Exception:
        # best-effort; ignore cache write errors
        pass


def _fetch_remote() -> Dict[str, str]:
    try:
        import requests
        resp = requests.get(_REMOTE_URL, timeout=5)
        resp.raise_for_status()
        data = resp.json()
        # ensure mapping of str->str
        if isinstance(data, dict):
            return {str(k): str(v) for k, v in data.items()}
    except Exception:
        pass
    return {}


def get_regions() -> Dict[str, str]:
    """Return the best-available CSC mapping.

    Priority: remote → cache → packaged → built-in fallback.
    """
    # Try remote (and update cache) only when this module is used
    regions = _fetch_remote()
    if regions:
        _save_cache(regions)
        return regions

    regions = _load_cache()
    if regions:
        return regions

    regions = _load_packaged_regions()
    if regions:
        return regions

    return _FALLBACK_REGION_INFO


def iter_regions_sorted() -> Iterable[Tuple[str, str]]:
    """Yield (code, name) pairs sorted by code from the best dataset."""
    regions = get_regions()
    for code in sorted(regions.keys()):
        yield code, regions[code]

