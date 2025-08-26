#!/usr/bin/env python3

import argparse
import glob
import os
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import List, Tuple, Dict, Any, Set

import intellij
import mkspec
from update_sdk import write_metadata

# Constants for platforms
LINUX = "linux"
WIN = "windows"
MAC_ARM = "darwin_aarch64"
MAC_X64 = "darwin"

# Artifact Filenames
SOURCES_ZIP = "sherlock-platform-sources.zip"
MAC_ARM_ZIP = "sherlock-platform.mac.aarch64.zip"
MAC_X64_ZIP = "sherlock-platform.mac.x64.zip"
LINUX_TAR = "sherlock-platform.tar.gz"
WIN_ZIP = "sherlock-platform.win.zip"
MANIFEST_TEMPLATE = "manifest_{}.xml"

EXPECTED_ARTIFACTS: Set[str] = {SOURCES_ZIP, MAC_ARM_ZIP, MAC_X64_ZIP, LINUX_TAR, WIN_ZIP}
SDK_TYPE = "IC"
SHERLOCK_SUBDIR = "sherlock"


def check_sherlock_artifacts(dir_path: Path, include_manifest: bool = False, bid: str = "") -> List[str]:
  """
  Checks for the expected Sherlock Platform artifact files in the download directory.
  """
  files = sorted([f.name for f in dir_path.iterdir()])
  if not files:
    sys.exit(f"Error: There are no artifacts in {dir_path}")

  expected = EXPECTED_ARTIFACTS.copy()
  if include_manifest and bid:
    expected.add(MANIFEST_TEMPLATE.format(bid))

  found_files = [f for f in files if f in expected or f in EXPECTED_ARTIFACTS]

  if len(found_files) < len(EXPECTED_ARTIFACTS):
    print("Warning: Missing some expected base artifacts.")
    print("Expected Base:", sorted(list(EXPECTED_ARTIFACTS)))
    print("Found:", found_files)

  return found_files


def download_ab_artifacts(bid: str) -> Path:
  """
  Downloads Sherlock Platform artifacts from Android Build using fetch_artifact
  into a temporary directory.
  """
  fetch_artifact = "/google/data/ro/projects/android/fetch_artifact"
  auth_flags = []
  if Path("/usr/bin/prodcertstatus").exists():
    if subprocess.run("prodcertstatus", check=False).returncode != 0:
      sys.exit("You need prodaccess to download artifacts")
  elif Path("/usr/bin/fetch_artifact").exists():
    fetch_artifact = "/usr/bin/fetch_artifact"
    auth_flags.append("--use_oauth2")
  else:
    sys.exit("""You need to install fetch_artifact:
sudo glinux-add-repo android stable && \\
sudo apt update && \\
sudo apt install android-fetch-artifact""")

  if not bid:
    sys.exit("--bid argument needs to be set to download")

  download_dir = Path(tempfile.mkdtemp(prefix="sherlock_sdk_ab_"))
  print(f"Temporary download directory: {download_dir}")
  print(f"Downloading artifacts for BID {bid} from target IntelliJ-Sherlock to {download_dir}")

  manifest_file = MANIFEST_TEMPLATE.format(bid)
  artifacts_to_download = EXPECTED_ARTIFACTS | {manifest_file}

  for artifact in artifacts_to_download:
    try:
      print(f"Fetching {artifact}...")
      subprocess.check_call([
        fetch_artifact, *auth_flags, "--bid", bid, "--target", "IntelliJ-Sherlock", artifact, str(download_dir)
      ])
    except subprocess.CalledProcessError as e:
      print(f"ERROR: Critical artifact {artifact} failed to download: {e}", file=sys.stderr)
      raise
    except FileNotFoundError:
      print(f"ERROR: Critical artifact '{artifact}' not found in build {bid} for target IntelliJ-Sherlock.", file=sys.stderr)
      raise
  return download_dir


def _extract_artifact(artifact: Path, extract_subdir: Path, os_name: str) -> None:
  artifact_name = artifact.name
  print(f"Extracting {artifact_name} to {extract_subdir}")
  temp_extract_dir = Path(tempfile.mkdtemp(prefix=f"_extract_{os_name}_"))

  try:
    extract_subdir.mkdir(parents=True, exist_ok=True)
    if os_name == LINUX:
      cmd = ["tar", "-xzf", str(artifact), "-C", str(extract_subdir), "--strip-components=1"]
      subprocess.run(cmd, check=True, capture_output=True, text=True)
    elif os_name == MAC_ARM or os_name == MAC_X64:
      # Extract Mac zip to a temporary clean directory
      cmd_unzip = ["unzip", "-o", str(artifact), "-d", str(temp_extract_dir)]
      subprocess.run(cmd_unzip, check=True, capture_output=True, text=True)

      mac_contents_path = temp_extract_dir / "Sherlock.app" / "Contents"
      if mac_contents_path.exists():
        print(f"Moving {mac_contents_path} to {extract_subdir}")
        # Move the entire Contents directory
        shutil.move(str(mac_contents_path), str(extract_subdir))
      else:
        sys.exit(f"Error: Expected structure Sherlock.app/Contents not found in {artifact_name} at {temp_extract_dir}")
    elif os_name == WIN:
      cmd = ["unzip", "-o", str(artifact), "-d", str(extract_subdir)]
      subprocess.run(cmd, check=True, capture_output=True, text=True)
    else:
      raise ValueError(f"Unknown OS name: {os_name}")

    print(f"{artifact_name} extracted successfully to {extract_subdir}")

  except subprocess.CalledProcessError as e:
    print(f"Error extracting {artifact_name}: {e}", file=sys.stderr)
    raise
  except Exception as e:
    print(f"Error during {os_name} extraction for {artifact_name}: {e}.", file=sys.stderr)
    raise
  except KeyboardInterrupt:
    print(f"Canceled extraction of {artifact_name}.")
    raise
  finally:
    if temp_extract_dir.exists():
      shutil.rmtree(temp_extract_dir)


def extract_artifacts(download_dir: Path, found_files: List[str], prebuilts: Path, metadata: Dict[str, Any], bid: str = "") -> Path:
  """
  Extracts the downloaded artifacts into the prebuilts/studio/intellij-sdk/IC directory.
  """
  sdk = prebuilts / SDK_TYPE

  if not sdk.exists():
    print(f"Creating directory: {sdk}")
    sdk.mkdir(parents=True, exist_ok=True)

  dest_paths = {
    LINUX: sdk / LINUX / SHERLOCK_SUBDIR,
    MAC_ARM: sdk / MAC_ARM / SHERLOCK_SUBDIR,
    MAC_X64: sdk / MAC_X64 / SHERLOCK_SUBDIR,
    WIN: sdk / WIN / SHERLOCK_SUBDIR,
  }

  # Clean destinations before extraction attempts
  for plat_dir in dest_paths.values():
    if plat_dir.exists():
      print(f"Cleaning existing platform directory: {plat_dir}")
      shutil.rmtree(plat_dir)
    plat_dir.mkdir(parents=True, exist_ok=True)

  try:
    if LINUX_TAR in found_files:
      _extract_artifact(download_dir / LINUX_TAR, dest_paths[LINUX], LINUX)
    if MAC_ARM_ZIP in found_files:
      _extract_artifact(download_dir / MAC_ARM_ZIP, dest_paths[MAC_ARM], MAC_ARM)
    if MAC_X64_ZIP in found_files:
      _extract_artifact(download_dir / MAC_X64_ZIP, dest_paths[MAC_X64], MAC_X64)
    if WIN_ZIP in found_files:
      _extract_artifact(download_dir / WIN_ZIP, dest_paths[WIN], WIN)

    sources_src = download_dir / SOURCES_ZIP
    sources_dest = sdk / SOURCES_ZIP
    if SOURCES_ZIP in found_files:
      print(f"Copying sources zip to {sources_dest}...")
      shutil.copyfile(sources_src, sources_dest)

    # Parse manifest if it was downloaded
    manifest_file = MANIFEST_TEMPLATE.format(bid)
    manifest_path = download_dir / manifest_file
    if bid and manifest_path.exists():
      print(f"Parsing manifest {manifest_path}")
      try:
        xml = ET.parse(manifest_path)
        for project in xml.getroot().findall("project"):
          metadata[project.get("path")] = project.get("revision")
      except ET.ParseError as e:
        print(f"Warning: Could not parse manifest file {manifest_path}: {e}", file=sys.stderr)
    elif bid:
      print(f"Warning: Manifest file {manifest_path} not found in downloaded artifacts.")

  except Exception as e:
    raise Exception(f"Error during artifact processing: {e}")

  print(f"Artifacts extracted to {sdk}")
  write_metadata(sdk, metadata)
  return sdk


def generate_spec(sdk: Path) -> None:
  """
  Generates the spec.bzl file using mkspec within the IC directory.
  """
  print(f"Generating spec.bzl in {sdk}")
  ides = {}
  platforms = {
    intellij.LINUX: LINUX,
    intellij.WIN: WIN,
    intellij.MAC_ARM: MAC_ARM,
    intellij.MAC: MAC_X64
  }
  for intellij_plat, plat_dir in platforms.items():
    platform = sdk / plat_dir / SHERLOCK_SUBDIR
    if platform.exists():
      try:
        ides[intellij_plat] = intellij.IntelliJ.create(intellij_plat, platform)
      except Exception as e:
        print(f"Error creating IntelliJ instance for {intellij_plat} at {platform}: {e}", file=sys.stderr)
        raise
    else:
      print(f"Warning: Path not found for {intellij_plat}: {platform}", file=sys.stderr)

  if not ides:
    sys.exit("Error: No valid platforms found to generate spec file.")

  mkspec.write_spec_file(str(sdk / 'spec.bzl'), ides)
  print(f"Successfully generated {sdk / 'spec.bzl'}")


def main() -> None:
  parser = argparse.ArgumentParser(description="Download or use local Sherlock Platform artifacts and generate spec file.")
  group = parser.add_mutually_exclusive_group(required=True)
  group.add_argument("--download", help="Android Build ID (e.g., 1234567) for IntelliJ-Sherlock target.")
  group.add_argument("--path", help="Path to a local directory containing Sherlock Platform artifacts.")

  parser.add_argument("--workspace", default=str(Path(__file__).resolve().parents[4]),
                      help="Path to the root of the Android Studio source checkout.")
  parser.add_argument("--prebuilts_dir", default="prebuilts/studio/intellij-sdk", help="Path within workspace to store the SDKs.")

  args = parser.parse_args()

  workspace = Path(args.workspace).resolve()
  prebuilts = workspace / args.prebuilts_dir
  metadata = {}
  bid = args.download

  if bid:
    metadata["build_id"] = bid
  if args.path:
    metadata["local_path"] = str(Path(args.path).resolve())

  if args.path:
    artifact_dir = Path(args.path)
    if not artifact_dir.is_dir():
      sys.exit(f"Error: Provided --path is not a valid directory: {artifact_dir}")
    print(f"Using local artifacts from: {artifact_dir}")
    downloaded_files = check_sherlock_artifacts(artifact_dir)
  else:
    artifact_dir = download_ab_artifacts(bid)
    downloaded_files = check_sherlock_artifacts(artifact_dir, include_manifest=True, bid=bid)

  try:
    if not any(f in EXPECTED_ARTIFACTS for f in downloaded_files):
      sys.exit("No primary Sherlock artifacts found to process.")

    sdk_path = extract_artifacts(artifact_dir, downloaded_files, prebuilts, metadata, bid=bid)
    generate_spec(sdk_path)

    print("Sherlock SDK update complete.")

  except Exception as e:
    print(f"An error occurred: {e}", file=sys.stderr)
    sys.exit(1)


if __name__ == "__main__":
  main()
