# SPDX-License-Identifier: GPL-3.0+
""" CSC (region) catalog for Samsung firmware servers.

This list is not exhaustive but covers many commonly used CSC codes.
Contributions to expand or correct the list are welcome.
"""

# Map CSC code -> Human-friendly description
REGION_INFO = {
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

def iter_regions_sorted():
    """Yield (code, name) pairs sorted by code."""
    for code in sorted(REGION_INFO.keys()):
        yield code, REGION_INFO[code]

