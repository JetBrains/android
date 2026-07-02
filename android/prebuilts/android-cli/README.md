# Android CLI Binaries

This directory contains configuration for platform-specific binaries for the Android CLI tool, which are bundled with the Android plugin.

## Pulling at Build Time

To ensure build stability and predictability while avoiding large binaries in the source control history, the binaries are fetched at build time.

They are defined via Bzlmod `http_file` rules inside the SDK Bzlmod module file:
- `tools/base/bazel/bzlmod/sdk.MODULE.bazel`

The targets exposed and copied locally are:
- `@android_cli_linux//file`
- `@android_cli_mac_x86_64//file`
- `@android_cli_mac_arm64//file`
- `@android_cli_win//file`

## How to Update the Binaries

To update the binaries to a newer release:

1. Obtain the new version number and its authoritative, verified SHA-256 hashes from the official release/build artifacts source.
2. Open `tools/base/bazel/bzlmod/sdk.MODULE.bazel`.
3. Locate the `Android CLI binaries` block and update the `urls` and the corresponding `sha256` values for `android_cli_linux`, `android_cli_mac_x86_64`, `android_cli_mac_arm64`, and `android_cli_win`.
4. Test the build locally by running:
   ```bash
   bazel build //tools/adt/idea/android/prebuilts/android-cli:android-cli-launcher-bundle
   ```
