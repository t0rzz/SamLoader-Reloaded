# SamLoader Reloaded

Download firmware for Samsung devices (without any extra Windows drivers).

## Important notice (active fork)

This fork (https://github.com/t0rzz/SamLoader-Reloaded) contains substantial improvements and is actively maintained compared to the "martinetd" fork. Please use this repository and open issues/PRs here.

## Installation

```
$ pip3 install git+https://github.com/t0rzz/SamLoader-Reloaded.git
```

## Usage

CLI: Run with `samloader` or `python3 -m samloader`. See `samloader --help` and
`samloader (command) --help` for help.

GUI: Run with `samloader-gui` or `python -m samloader.gui` to open a PyQt6 (Qt6) graphical interface supporting Check Update, Download, and Decrypt.

List known CSC regions: run `samloader --listregions` to print known CSC codes and names and exit. The list is comprehensive and maintained:
- It first tries to fetch the latest list from this repository (t0rzz/SamLoader-Reloaded) and caches it locally.
- If offline, it falls back to the cached file, then to a packaged dataset shipped with samloader.

Network behavior (CLI and GUI):
- All network operations use a maximum 5-second per-request timeout.
- On timeouts or transient network errors, commands automatically retry a few times.

Example output lines:
- BTU (United Kingdom, no brand)
- ITV (Italy, no brand)

Check the latest firmware version (prints labeled AP/CSC/CP/Build by default): `-m <model> -r <region> -i <serial/imei number prefix> checkupdate` (use `--raw` to print the original four-part version code)

Interactive flow: after showing the latest version, the CLI asks whether you want to download it now (y/n). If you choose "y", it downloads into the current directory using the server filename. When the download finishes, it asks whether to decrypt the file in the current directory (y/n). IMEI/serial is required by Samsung servers for downloads and ENC4 decrypts.

Download the specified firmware version for a given phone and region to a
specified file or directory: `-m <model> -r <region> -i <serial/imei number prefix> download -v <version> (-O
<output-dir> or -o <output-file>)`

For faster downloads, enable multi-threading with `-T/--threads` (e.g., `-T 8`).
Note: when `--resume` is used or a partial file already exists, the downloader falls back to single-thread mode.

Automatic resume on connection interruptions:
- The downloader automatically retries and continues from the last saved byte when a connection breaks (no data loss).
- Configure the maximum consecutive retry attempts with `--retries` (default: 10). Exponential backoff is applied between attempts.
- You can also restart the command later with `--resume` to continue from a partially downloaded file.

Decrypt encrypted firmware: `-m <model> -r <region> -i <serial/imei number prefix> decrypt -v <version> -i <input-file> -o <output-file>`
- Encryption version is auto-detected:
  - If the filename ends with .enc2 or .enc4, that version is used.
  - Otherwise, the tool tries a minimal V2 check by decrypting the first block and looking for the ZIP signature (PK). If it matches, V2 is used; otherwise V4 is assumed.
- You can still override detection with `--enc-ver 2` or `--enc-ver 4` if needed.

### Examples

1) Check latest version (labeled output) and decline download

```
$ samloader -m GT-I8190N -r BTU -i 355626052209825 checkupdate
AP: I8190NXXAMJ2
CSC: I8190NBTUAMJ1
CP: I8190NXXAMJ2
Build: I8190NXXAMJ2
Do you want to download this firmware now? [y/N]: n
```

2) Check latest version, accept download, then decline decrypt

```
$ samloader -m SM-S918B -r INS -i 355626052209825 checkupdate
AP: S918BXXU4AXXX
CSC: S918BOXM4AXXX
CP: S918BXXU4AXXX
Build: S918BXXU4AXXX
Do you want to download this firmware now? [y/N]: y
downloading SM-S918B_1_20250101010101_abcdefghij_fac.zip.enc4
[########................] 2.10G/7.80G ...
Do you want to decrypt it in the current directory? [y/N]: n
```

3) Download a specific version with multi-threading and retries

```
$ samloader -m SM-S938B -r INS -i 355626052209825 download \
    -v S938BXXS5AYG4/S938BOXM5AYG4/S938BXXS5AYG4/S938BXXS5AYG4 \
    -O firmware -T 8 --retries 10
Note: resume or existing partial download disables multi-thread; falling back to single-thread.  # only shown if a partial exists
MD5: <unavailable>  # shown if available from headers
[########################] 25.9G/25.9G - 00:32:10
```

4) Decrypt a file (auto-detect enc version; long option names recommended to avoid -i ambiguity)

```
$ samloader -m SM-S918B -r INS --dev-imei 355626052209825 \
    decrypt --fw-ver S918BXXU4AXXX/S918BOXM4AXXX/S918BXXU4AXXX/S918BXXU4AXXX \
    --in-file "C:\\firmware\\SM-S918B_....zip.enc4" \
    --out-file "C:\\firmware\\SM-S918B_....zip"
Detected encryption version: V4
[################################] 7.80G/7.80G - 00:08:12
```

5) List CSC regions (first lines)

```
$ samloader --listregions | head -n 5
- AFG (Afghanistan, no brand)
- ALB (Albania, no brand)
- ATO (Austria, no brand)
- AUT (Switzerland, no brand)
- BAL (Serbia, no brand)
```

## Building a single-file Windows .exe (GUI)

You can create a single-file executable for Windows using PyInstaller:

1. Install PyInstaller:
   - `pip install pyinstaller`
2. Build the GUI app (one-file, windowed):
   - `pyinstaller --noconfirm --onefile --windowed -n samloader-gui --collect-all PyQt6 --collect-data certifi --add-data "samloader\data\regions.json;samloader\data" samloader\gui.py`
3. The resulting `samloader-gui.exe` will be in the `dist` folder.

Notes:
- The GUI uses PyQt6 (Qt6) and is bundled into the exe by PyInstaller using `--collect-all PyQt6`.
- The GUI module supports both package and standalone execution, so building directly from `samloader\gui.py` works with PyInstaller (no import errors).
- For downloading and decrypting, an IMEI prefix (>= 8 digits) or serial is required by Samsung servers; the GUI follows the same rules as the CLI.

## Note

This project was originally created at `nlscc/samloader`, later moved to `samloader/samloader`, then forked to `martinetd/samloader`, and is now maintained at `t0rzz/SamLoader-Reloaded`. 


## GUI download behavior (threads, file writing, temp)

- Threads: the GUI includes a Threads selector (1..10). When Threads > 1 and you start from scratch (no resume/partial), it uses the same segmented multi‑thread logic as the CLI (preallocation, byte‑range segments, per‑segment retries with backoff, aggregated progress). If Resume is enabled or a partial file already exists, it automatically falls back to single‑thread for integrity.
- File writing: data is written directly to the selected destination file on disk as it arrives (streaming write). There is no staging in a temporary file.
  - If “Resume” is enabled and a partial exists, the GUI resumes from the last written byte and appends to the same file.
  - If “Resume” is disabled, the GUI starts from zero and overwrites any existing file with the same name in the chosen directory.
- Temp directory: the GUI does not download to a temp directory first. The only temporary files you might see are those used by PyInstaller’s one‑file runtime (extracted into a system temp folder when running the EXE), unrelated to the downloaded firmware data.
- Progress, speed, ETA: the progress bar is initialized using the server‑reported size and updates with the exact number of bytes written. The label below the bar shows total bytes done/total size, current speed, and estimated time remaining.
- Timeouts and retries: network operations use a 5‑second per‑request timeout with automatic retries. If a connection drops mid‑transfer, the GUI retries and resumes from the last saved byte (no data loss).
