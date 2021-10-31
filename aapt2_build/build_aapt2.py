#!/usr/bin/env python3
import os
import sys
import shutil
from collections import namedtuple
import argparse
import subprocess

Abi = namedtuple("Abi", ["abi", "output_dir", "lib_dir", "arch"])

abis = [
    Abi("arm", "generic", "lib", "armeabi-v7a"),
    Abi("arm64", "generic_arm64", "lib64", "arm64-v8a"),
    Abi("x86", "generic_x86", "lib", "x86"),
    Abi("x86_64", "generic_x86_64", "lib64", "x86_64"),
]


def apde_root():
    # ../ relative to script directory
    return os.path.dirname(os.path.dirname(os.path.realpath(sys.argv[0])))


def build(aosp_dir, dry_run):
    # note: can't use set -u because build/envsetup.sh uses undefined variables
    build_script = """
set -eo pipefail

cd {aosp_dir}
source build/envsetup.sh
lunch aosp_{abi}-user
mmm frameworks/base/tools/aapt2:aapt2,libaapt2_jni
"""

    for abi in abis:
        print("Building AAPT2 for ABI {}".format(abi.abi))

        command = build_script.format(
            aosp_dir=aosp_dir,
            abi=abi.abi,
        )
        print(command)
        if not dry_run:
            subprocess.check_call(["/bin/bash", "-c", command])


def copy(aosp_dir, dry_run):
    for abi in abis:
        print("Copying AAPT2 for ABI {}".format(abi.abi))

        out_root = os.path.join(aosp_dir, "out", "target", "product",
                                abi.output_dir, "system")
        lib_out = os.path.join(out_root, abi.lib_dir, "libaapt2_jni.so")
        bin_out = os.path.join(out_root, "bin", "aapt2")

        dest_root = os.path.join(apde_root(), "APDE", "src", "main", "jniLibs")

        lib_dest = os.path.join(dest_root, abi.arch, "libaapt2_jni.so")
        bin_dest = os.path.join(dest_root, abi.arch, "libaapt2_bin.so")

        print("cp {} {}".format(lib_out, lib_dest))
        if not dry_run:
            shutil.copyfile(lib_out, lib_dest)

        print("cp {} {}".format(bin_out, bin_dest))
        if not dry_run:
            shutil.copyfile(bin_out, bin_dest)

        print("chmod +x {}".format(bin_dest))
        os.chmod(bin_dest, os.stat(bin_dest).st_mode | 0o0111)


def main():
    parser = argparse.ArgumentParser(description="Build AAPT2 for APDE")
    parser.add_argument("--aosp", metavar="AOSP_DIR", type=str, required=True,
                        help="the localy AOSP directory")
    parser.add_argument("--dry-run", action="store_true",
                        help="don't perform actions, just print commands")

    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--build", action="store_true", help="build AAPT2")
    group.add_argument("--copy", action="store_true",
                       help="copy built AAPT2 binaries and shared libraries into APDE's jniLibs directory")

    args = parser.parse_args()

    if args.build:
        build(args.aosp, args.dry_run)
    elif args.copy:
        copy(args.aosp, args.dry_run)


if __name__ == "__main__":
    main()
