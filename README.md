# samloader

Download firmware for Samsung devices (without any extra Windows drivers).

## Important notice (active fork)

This fork (https://github.com/t0rzz/samloader) contains substantial improvements and is actively maintained compared to the "martinetd" fork. Please use this repository and open issues/PRs here.

## Installation

```
$ pip3 install git+https://github.com/t0rzz/samloader.git
```

## Usage

CLI: Run with `samloader` or `python3 -m samloader`. See `samloader --help` and
`samloader (command) --help` for help.

GUI: Run with `samloader-gui` or `python -m samloader.gui` to open a simple graphical interface supporting Check Update, Download, and Decrypt.

Check the latest firmware version: `-m <model> -r <region> -i <serial/imei number prefix> checkupdate`

Download the specified firmware version for a given phone and region to a
specified file or directory: `-m <model> -r <region> -i <serial/imei number prefix> download -v <version> (-O
<output-dir> or -o <output-file>)`

Decrypt encrypted firmware: `-m <model> -r <region> -i <serial/imei number prefix> decrypt -v <version> -V
<enc-version> -i <input-file> -o <output-file>`

### Example

```
$ samloader -m GT-I8190N -r BTU -i 355626052209825 checkupdate
I8190NXXAMJ2/I8190NBTUAMJ1/I8190NXXAMJ2/I8190NXXAMJ2

$ samloader -m GT-I8190N -r BTU -i 355626052209825 download -v I8190NXXAMJ2/I8190NBTUAMJ1/I8190NXXAMJ2/I8190NXXAMJ2 -O .
downloading GT-I8190N_BTU_1_20131118100230_9ae3yzkqmu_fac.zip.enc2
[################################] 10570/10570 - 00:02:02

$ samloader -m GT-I8190N -r BTU -i 355626052209825 decrypt -v I8190NXXAMJ2/I8190NBTUAMJ1/I8190NXXAMJ2/I8190NXXAMJ2 -V 2 -i GT-I8190N_BTU_1_20131118100230_9ae3yzkqmu_fac.zip.enc2 -o GT-I8190N_BTU_1_20131118100230_9ae3yzkqmu_fac.zip
[################################] 169115/169115 - 00:00:08
```

## Building a single-file Windows .exe (GUI)

You can create a single-file executable for Windows using PyInstaller:

1. Install PyInstaller:
   - `pip install pyinstaller`
2. Build the GUI app (one-file, windowed):
   - `pyinstaller --noconfirm --onefile --windowed -n samloader-gui samloader\gui.py`
3. The resulting `samloader-gui.exe` will be in the `dist` folder.

Notes:
- The GUI uses Tkinter (bundled with most Python installs on Windows).
- For downloading and decrypting, an IMEI prefix (>= 8 digits) or serial is required by Samsung servers; the GUI follows the same rules as the CLI.

## Note

This project was originally created at `nlscc/samloader`, later moved to `samloader/samloader`, then forked to `martinetd/samloader`, and is now maintained at `t0rzz/samloader`.
