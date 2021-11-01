
AOSP (Android Open Source Project) patches and script for building AAPT2 for APDE.

AOSP patches are based on [JonForShort's patches](https://github.com/JonForShort/android-tools),
with small changes.

## Requirements

The AOSP requires significant resources to build. The following are
[required](https://source.android.com/setup/build/requirements):

- 64-bit processor
- 400 GB free disk space
- 16 GB available RAM

Note: My local builds have only taken about 200 GB of storage, but I
have needed an additional 16 GB of swap space on top of 16 GB of RAM.

## Building

To build AAPT2:

1. Follow the [official instructions](https://source.android.com/setup/build/downloading)
   to download the AOSP source. Choose the branch `android-9.0.0_r61` for the init command.
   Note that setting up the AOSP can take several hours. The remaining instructions assume
   that you have placed the AOSP at `~/aosp`, so change this path accordingly.

2. Apply the AOSP patch in `aosp_diff.patch`, which modifies the build scripts to build AAPT2
   for the target (Android devices) instead of host (workstations).

3. Build AAPT2 using the script: `./build_aapt2.py --aosp ~/aosp --build`.

4. Copy the build outputs to the APDE jniLibs directory using the script:
   `./build_aapt2.py --aosp ~/aosp --copy`.
