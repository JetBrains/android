# Android CLI Launchers

This directory contains configuration for platform-specific launcher binaries for the Android CLI tool, which are bundled with the Android plugin.

## Pulling at Build Time

To ensure build stability and predictability while avoiding large binaries in the source control history, the launchers are fetched at build time.

They are defined via Bzlmod `http_file` rules inside the local module file:
- `android_cli.MODULE.bazel`

This file is dynamically included in the root `toplevel.MODULE.bazel` configuration. The targets exposed are:
- `@android_cli_linux//file`
- `@android_cli_mac//file`
- `@android_cli_mac_arm//file`
- `@android_cli_win//file`

## How to Update the Launchers

To update the launcher binaries to a newer release:

1. Obtain the new version number and its authoritative, verified SHA-256 hashes from the official release/build artifacts source.
2. Open `tools/adt/idea/android/prebuilts/android-cli/android_cli.MODULE.bazel`.
3. Update the `urls` and the corresponding `sha256` values for `android_cli_linux`, `android_cli_mac`, `android_cli_mac_arm`, and `android_cli_win`.
4. Test the build locally by running:
   ```bash
   bazel build //tools/adt/idea/android/prebuilts/android-cli:android-cli-launcher-bundle
   ```
