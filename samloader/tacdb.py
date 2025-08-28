# SPDX-License-Identifier: GPL-3.0+
"""
TAC database utilities.

- Loads TAC entries from a public CSV (provided by user) at runtime and caches locally.
- Optional packaged CSV (samloader/data/tacs.csv) can be shipped and used offline.
- Provides IMEI generation from a model: picks a TAC for the model and synthesizes
  a plausible IMEI (TAC + random SNR + Luhn check).

This module does not vendor external code; it only consumes a public CSV resource.
"""
from __future__ import annotations

import csv
import os
import random
from typing import Dict, List, Optional

TACS_URL = (
    "https://raw.githubusercontent.com/zacharee/SamloaderKotlin/refs/heads/master/"
    "common/src/commonMain/moko-resources/files/tacs.csv"
)

_CACHE_DIR = os.path.join(os.path.expanduser("~"), ".samloader")
_CACHE_FILE = os.path.join(_CACHE_DIR, "tacs.csv")
_PACKAGED_REL = os.path.join(os.path.dirname(__file__), "data", "tacs.csv")

# In-memory cache
_MODEL_TO_TACS: Dict[str, List[str]] = {}


def _ensure_cache_dir():
    try:
        os.makedirs(_CACHE_DIR, exist_ok=True)
    except Exception:
        pass


def _load_packaged_csv() -> List[List[str]]:
    try:
        if os.path.isfile(_PACKAGED_REL):
            with open(_PACKAGED_REL, "r", encoding="utf-8") as fh:
                return list(csv.reader(fh))
    except Exception:
        pass
    return []


def _load_cached_csv() -> List[List[str]]:
    try:
        if os.path.isfile(_CACHE_FILE):
            with open(_CACHE_FILE, "r", encoding="utf-8") as fh:
                return list(csv.reader(fh))
    except Exception:
        pass
    return []


def _save_cache(rows: List[List[str]]):
    try:
        _ensure_cache_dir()
        with open(_CACHE_FILE, "w", encoding="utf-8", newline="") as fh:
            writer = csv.writer(fh)
            writer.writerows(rows)
    except Exception:
        pass


essential_headers = {"model", "tac"}


def _fetch_remote_csv() -> List[List[str]]:
    try:
        import requests
        r = requests.get(TACS_URL, timeout=10)
        r.raise_for_status()
        text = r.text
        rows = list(csv.reader(text.splitlines()))
        # Basic validation: ensure header has required columns
        if rows:
            header = [c.strip().lower() for c in rows[0]]
            if not essential_headers.issubset(set(header)):
                return []
        return rows
    except Exception:
        return []


def _normalize_model(m: str) -> str:
    return (m or "").strip().upper()


def _index_by_model(rows: List[List[str]]) -> Dict[str, List[str]]:
    if not rows:
        return {}
    header = [c.strip().lower() for c in rows[0]]
    try:
        i_model = header.index("model")
        i_tac = header.index("tac")
    except ValueError:
        return {}
    index: Dict[str, List[str]] = {}
    seen = set()
    for row in rows[1:]:
        if not row or len(row) <= max(i_model, i_tac):
            continue
        model = _normalize_model(row[i_model])
        tac = row[i_tac].strip()
        if len(tac) < 8 or not tac.isdecimal():
            continue
        key = (model, tac)
        if key in seen:
            continue
        seen.add(key)
        index.setdefault(model, []).append(tac[:8])
    return index


def _init_db() -> None:
    global _MODEL_TO_TACS
    if _MODEL_TO_TACS:
        return
    # Try remote
    rows = _fetch_remote_csv()
    if rows:
        _MODEL_TO_TACS = _index_by_model(rows)
        if _MODEL_TO_TACS:
            _save_cache(rows)
            return
    # Fallback cache
    rows = _load_cached_csv()
    if rows:
        _MODEL_TO_TACS = _index_by_model(rows)
        if _MODEL_TO_TACS:
            return
    # Fallback packaged
    rows = _load_packaged_csv()
    if rows:
        _MODEL_TO_TACS = _index_by_model(rows)
        return
    _MODEL_TO_TACS = {}


def available_tacs_for_model(model: str) -> List[str]:
    _init_db()
    return list(_MODEL_TO_TACS.get(_normalize_model(model), []))


def luhn_checksum(body: str) -> int:
    s = 0
    parity = (len(body) + 1) % 2
    for idx, ch in enumerate(body):
        d = int(ch)
        if idx % 2 == parity:
            d *= 2
            if d > 9:
                d -= 9
        s += d
    return (10 - (s % 10)) % 10


def generate_imei_from_tac(tac: str) -> str:
    tac8 = tac[:8]
    # 6 random digits for SNR to make 14 digits before checksum
    snr = f"{random.randint(0, 999999):06d}"
    body = tac8 + snr
    return body + str(luhn_checksum(body))


def generate_imei_from_model(model: str) -> Optional[str]:
    """Return a plausible IMEI for the model using a TAC from the database."""
    tacs = available_tacs_for_model(model)
    if not tacs:
        return None
    tac = random.choice(tacs)
    return generate_imei_from_tac(tac)
