# -*- mode: python ; coding: utf-8 -*-
from PyInstaller.utils.hooks import collect_data_files

# Include packaged region data and certifi bundle.
# Rely on PyInstaller's built-in Kivy hooks and the CLI flag `--collect-all kivy` when invoking pyinstaller.
datas = [('samloader\\data\\regions.json', 'samloader\\data')]
binaries = []
hiddenimports = []
datas += collect_data_files('certifi')


a = Analysis(
    ['samloader\\gui_kivy.py'],
    pathex=[],
    binaries=binaries,
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='samloader-gui',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    upx_exclude=[],
    runtime_tmpdir=None,
    console=False,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
