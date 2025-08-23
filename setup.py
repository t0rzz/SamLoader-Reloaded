import setuptools

with open("README.md", "r") as fh:
    long_description = fh.read()

setuptools.setup(
    name="samloader",
    version="0.12",
    author="nlscc",
    author_email="dontsendmailhere@example.com",
    description="A tool to download firmware for Samsung phones.",
    long_description=long_description,
    long_description_content_type="text/markdown",
    url="https://github.com/t0rzz/samloader",
    packages=setuptools.find_packages(),
    include_package_data=True,
    package_data={
        "samloader": ["data/*.json"],
    },
    classifiers=[
        "Programming Language :: Python :: 3",
        "License :: OSI Approved :: GNU General Public License v3 or later (GPLv3+)",
        "Operating System :: OS Independent",
    ],
    entry_points={
        "console_scripts": [
            "samloader = samloader.main:main",
        ],
        "gui_scripts": [
            "samloader-gui = samloader.gui:main",
        ],
    },
    install_requires=[
        "tqdm",
        "pycryptodomex",
        "requests"
    ],
    python_requires='>=3.6',
)
