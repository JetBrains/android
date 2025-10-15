#!/usr/bin/env python3

import argparse
import glob
import os
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile
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
GITHUB_REPO = "android-graphics/sherlock-platform"
VERSION_XML_PATH = "idea/SherlockPlatformApplicationInfo.xml"
NAMESPACE_URI = "http://jetbrains.org/intellij/schema/application-info"
SHERLOCK_PLATFORM_SHA_METADATA_KEY = "tools/gpu-profiler/idea"


def check_gh_auth() -> None:
  """
  Checks if the GitHub CLI is installed and authenticated.
  Raises RuntimeError if not.
  """
  try:
    subprocess.run(["gh", "auth", "status"], check=True, capture_output=True)
  except subprocess.CalledProcessError:
    sys.exit("Error: GitHub CLI (gh) is not authenticated. Please run 'gh auth login'.")
  except FileNotFoundError:
    sys.exit("Error: GitHub CLI (gh) not found. Please install it from https://cli.github.com/")


# TODO: Eventually use bid for releases (v[bid]) and remove this method
def get_version_from_local_xml(sdk_path: Path) -> str:
  """
  Extracts the version from SherlockPlatformApplicationInfo.xml within the resources.jar.
  """
  lib_dir = sdk_path / LINUX / SHERLOCK_SUBDIR / "lib"
  if not lib_dir.exists():
    raise FileNotFoundError(f"Could not find lib directory at {lib_dir}")

  resources_jar_path = None
  for jar_file in lib_dir.glob("*.jar"):
    try:
      with zipfile.ZipFile(jar_file, 'r') as zf:
        if VERSION_XML_PATH in zf.namelist():
          resources_jar_path = jar_file
          break
    except zipfile.BadZipFile:
      print(f"Warning: Skipping non-zip file in lib dir: {jar_file}", file=sys.stderr)
      continue

  if not resources_jar_path:
    raise FileNotFoundError(f"Could not find a JAR containing {VERSION_XML_PATH} in {lib_dir}")

  try:
    with zipfile.ZipFile(resources_jar_path, 'r') as zf:
      with zf.open(VERSION_XML_PATH) as xml_file:
        tree = ET.parse(xml_file)
        root = tree.getroot()
        version_element = root.find(f'{{{NAMESPACE_URI}}}version')
        if version_element is not None:
          major = version_element.get("major")
          minor = version_element.get("minor")
          patch = version_element.get("patch", default='0')
          if major is not None and minor is not None:
            version = f"{major}.{minor}.{patch}"
            return version
    raise ValueError("Version element not found in XML")
  except ET.ParseError as e:
    print(f"Error parsing XML content from {resources_jar_path}: {e}", file=sys.stderr)
    raise
  except KeyError:
    # Should not happen due to the check above, but as a safeguard.
    raise FileNotFoundError(f"{VERSION_XML_PATH} not found within {resources_jar_path}")


def parse_sha_from_metadata(metadata_path: Path) -> str | None:
  """
  Parses the Sherlock source SHA from the METADATA file.
  """
  if not metadata_path.exists():
    sys.exit(f"Error: METADATA file not found at {metadata_path}")
  with open(metadata_path, 'r') as f:
    for line in f:
      if line.startswith(SHERLOCK_PLATFORM_SHA_METADATA_KEY):
        return line.split(":", 1)[1].strip()
  return None


def upload_to_github_release(artifact_dir: Path, sdk_path: Path, tag_name: str, bid: str = None) -> None:
  """
  Creates a GitHub release and uploads artifacts using gh CLI.
  """
  result = subprocess.run(["gh", "api", f"repos/{GITHUB_REPO}/git/ref/tags/{tag_name}"], capture_output=True, text=True)
  if result.returncode == 0:
    print(f"Error: Tag {tag_name} already exists in {GITHUB_REPO}. Skipping release.", file=sys.stderr)
    return

  result = subprocess.run(["gh", "release", "view", tag_name, "--repo", GITHUB_REPO], capture_output=True, text=True)
  if result.returncode == 0:
    print(f"Error: Release for tag {tag_name} already exists in {GITHUB_REPO}. Skipping release.", file=sys.stderr)
    return

  metadata_path = sdk_path / "METADATA"
  if bid:
    sherlock_platform_sha = parse_sha_from_metadata(metadata_path)
  else:
    sherlock_platform_sha = "local_build"

  release_notes = f"Android Build ID: {bid if bid else 'N/A'}\n"
  if sherlock_platform_sha:
    release_notes += f"Sherlock Platform source SHA: {sherlock_platform_sha}\n"

  try:
    subprocess.run([
      "gh", "release", "create", tag_name,
      "--repo", GITHUB_REPO,
      "--title", tag_name,
      "--notes", release_notes,
      "--prerelease",
      "--draft=false"
    ], check=True, capture_output=True, text=True)
    print(f"Release {tag_name} created successfully.")
  except subprocess.CalledProcessError as e:
    print(f"Error creating release {tag_name}: {e}", file=sys.stderr)
    return

  print(f"Uploading artifacts to release {tag_name}...")
  artifacts_to_upload = [str(p) for p in artifact_dir.iterdir() if p.is_file()]
  if not artifacts_to_upload:
    print("No artifacts found to upload.")
    return

  try:
    subprocess.run([
      "gh", "release", "upload", tag_name,
      "--repo", GITHUB_REPO,
    ] + artifacts_to_upload, check=True, capture_output=True, text=True)
    print("Artifacts uploaded successfully")
  except subprocess.CalledProcessError as e:
    print(f"Error uploading artifacts: {e}", file=sys.stderr)

  print(f"GitHub release {tag_name} complete.")


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

  found_files = [f for f in files if f in expected]

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
    else:
      print("No manifest file found in artifact directory.")

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
  parser.add_argument("--release_to_github", action="store_true", help="Upload artifacts to a GitHub release.")

  args = parser.parse_args()

  if args.release_to_github:
    try:
      check_gh_auth()
    except RuntimeError as e:
      sys.exit(f"GitHub auth check failed: {e}")

  workspace = Path(args.workspace).resolve()
  prebuilts = workspace / args.prebuilts_dir
  metadata = {}
  bid = args.download
  artifact_dir = None
  cleanup_artifact = False

  if bid:
    metadata["build_id"] = bid
  if args.path:
    metadata["local_path"] = str(Path(args.path).resolve())

  try:
    if args.path:
      artifact_dir = Path(args.path)
      if not artifact_dir.is_dir():
        sys.exit(f"Error: Provided --path is not a valid directory: {artifact_dir}")
      print(f"Using local artifacts from: {artifact_dir}")
      artifacts = check_sherlock_artifacts(artifact_dir)
    else:
      artifact_dir = download_ab_artifacts(bid)
      cleanup_artifact = True
      artifacts = check_sherlock_artifacts(artifact_dir, include_manifest=True, bid=bid)

    if not any(f in EXPECTED_ARTIFACTS for f in artifacts):
      sys.exit("No primary Sherlock artifacts found to process.")

    sdk_path = extract_artifacts(artifact_dir, artifacts, prebuilts, metadata, bid=bid)
    generate_spec(sdk_path)

    print("Sherlock SDK update complete.")

    if args.release_to_github:
      try:
        version = get_version_from_local_xml(sdk_path)
        tag_name = f"v{version}"
        upload_to_github_release(artifact_dir, sdk_path, tag_name, bid)
      except Exception as e:
        print(f"Error during GitHub release: {e}", file=sys.stderr)

  except Exception as e:
    print(f"An error occurred: {e}", file=sys.stderr)
    sys.exit(1)
  finally:
    if not args.path and artifact_dir.exists():
      print(f"Cleaning up temporary download directory: {artifact_dir}")
      shutil.rmtree(artifact_dir)


if __name__ == "__main__":
  main()
