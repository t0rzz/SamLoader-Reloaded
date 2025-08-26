# SPDX-License-Identifier: GPL-3.0+
# Copyright (C) 2020 nlscc

""" FUS request helper (automatically sign requests and update tokens) """

import requests

from . import auth

class FUSClient:
    """ FUS API client. """
    def __init__(self):
        self.auth = ""
        self.sessid = ""
        self.makereq("NF_DownloadGenerateNonce.do") # initialize nonce
    def makereq(self, path: str, data: str = "") -> str:
        """ Make a FUS request to a given endpoint with retry and 5s timeout per attempt. """
        authv = 'FUS nonce="", signature="' + self.auth + '", nc="", type="", realm="", newauth="1"'
        last_err = None
        for attempt in range(5):
            try:
                req = requests.post(
                    "https://neofussvr.sslcs.cdngc.net/" + path,
                    data=data,
                    headers={"Authorization": authv, "User-Agent": "Kies2.0_FUS"},
                    cookies={"JSESSIONID": self.sessid},
                    timeout=5,
                )
                # If a new NONCE is present, decrypt it and update our auth token.
                if "NONCE" in req.headers:
                    self.encnonce = req.headers["NONCE"]
                    self.nonce = auth.decryptnonce(self.encnonce)
                    self.auth = auth.getauth(self.nonce)
                # Update the session cookie if needed.
                if "JSESSIONID" in req.cookies:
                    self.sessid = req.cookies["JSESSIONID"]
                req.raise_for_status()
                return req.text
            except Exception as e:
                last_err = e
                if attempt < 4:
                    continue
                break
        raise last_err if last_err else Exception("FUS request failed")
    def downloadfile(self, filename: str, start: int = 0, end=None) -> requests.Response:
        """ Make a FUS cloud request to download a given file (optionally a byte range).
        If 'end' is provided, the Range header will be 'bytes=start-end' (inclusive). Retries with 5s timeout.
        """
        # In a cloud request, we also need to pass the server nonce.
        authv = 'FUS nonce="' + self.encnonce + '", signature="' + self.auth \
            + '", nc="", type="", realm="", newauth="1"'
        headers = {"Authorization": authv, "User-Agent": "Kies2.0_FUS"}
        if end is not None or start > 0:
            if end is None:
                headers["Range"] = f"bytes={start}-"
            else:
                headers["Range"] = f"bytes={start}-{end}"
        last_err = None
        for attempt in range(5):
            try:
                req = requests.get(
                    "http://cloud-neofussvr.samsungmobile.com/NF_DownloadBinaryForMass.do",
                    params="file=" + filename,
                    headers=headers,
                    stream=True,
                    timeout=5,
                )
                req.raise_for_status()
                return req
            except Exception as e:
                last_err = e
                if attempt < 4:
                    continue
                break
        raise last_err if last_err else Exception("FUS download request failed")
