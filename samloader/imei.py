# SPDX-License-Identifier: GPL-3.0+

import random


def imei_required(args) -> bool:
    """
    Determine whether an IMEI/serial is required for the requested command.
    Required for download and decrypt with v4.
    """
    if args.command == "download":
        return True
    if args.command == "decrypt" and getattr(args, "enc_ver", 4) == 4:
        return True
    return False


def luhn_checksum(imei: str) -> int:
    """Return luhn check digit (as int) for the given IMEI prefix."""
    imei += '0'
    parity = len(imei) % 2
    s = 0
    for idx, char in enumerate(imei):
        d = int(char)
        if idx % 2 == parity:
            d *= 2
            if d > 9:
                d -= 9
        s += d
    return (10 - (s % 10)) % 10


def fixup_imei(args) -> int:
    """
    Try to fill in args.dev_imei if required.
    - Accepts either a serial (non-decimal) or an IMEI/IMEI prefix (>=8 digits).
    - If a prefix is provided (<15), it will be completed with random digits and a Luhn checksum.
    Returns 0 on success, 1 on error/missing input when required.
    """
    # only required for download or decrypt with v4
    if not imei_required(args):
        return 0

    if not getattr(args, "dev_imei", None):
        print("samsung now requires an imei to be set to download updates")
        print("Please set it or a prefix (at least 8 digits) through -i / --dev-imei")
        return 1

    if not args.dev_imei.isdecimal():
        # probably a serial number, leave as is
        return 0

    if len(args.dev_imei) < 8:
        print("Need to provide at least 8 digits to have a chance of working")
        return 1

    if len(args.dev_imei) < 15:
        # Only prefix, append n digits and checksum
        missing = 14 - len(args.dev_imei)
        if missing > 0:
            rng = random.randint(0, 10 ** missing - 1)
            args.dev_imei += f"%0{missing}d" % rng
        args.dev_imei += str(luhn_checksum(args.dev_imei))
        print(f"Filled up imei to {args.dev_imei}")

    return 0
