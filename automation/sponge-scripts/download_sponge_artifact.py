#  Copyright (C) 2026 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import argparse
import logging
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urlparse
import uuid
import zipfile

from auth_utils import get_access_token, get_minimal_env

logger = logging.getLogger(__name__)


def sniff_extension(chunk):
  """Guesses extension based on file signature (magic bytes)."""
  if chunk.startswith(b'PK\x03\x04'):
    logger.debug("Content sniffing detected ZIP signature.")
    return '.zip'

  # Check if it looks like XML
  if chunk.strip().startswith(b'<?xml') or chunk.strip().startswith(b'<testsuite'):
    logger.debug("Content sniffing detected XML content.")
    return '.xml'

  # Check if it looks like text/logs (first 1KB)
  try:
    chunk[:1024].decode('utf-8')
    logger.debug("Content sniffing detected text/log content.")
    return '.log'
  except UnicodeDecodeError:
    pass

  return None

def resolve_output_path(
    source_uri,
    output_path_path,
    target_dir,
    temp_file_path,
    artifact_name,
    is_directory
):
  """Determines the actual final destination filename, sniffing the extension if needed."""
  if is_directory or output_path_path == target_dir:
    if artifact_name:
      filename = artifact_name
    else:
      parsed_url = urlparse(source_uri)
      filename = Path(parsed_url.path).name
      if not filename or filename == '/':
        filename = "download"

    # Content sniffing to correct extension if needed
    filename_path = Path(filename)
    if filename_path.suffix == '.bin' or not filename_path.suffix:
      with temp_file_path.open("rb") as f:
        first_chunk = f.read(1024)
      sniffed_ext = sniff_extension(first_chunk)
      if sniffed_ext:
        filename_path = filename_path.with_suffix(sniffed_ext)

    return target_dir / filename_path
  return output_path_path

def extract_zip_archive(zip_path: Path) -> None:
  """Extracts a ZIP archive securely (preventing Zip Slip) and cleans up the raw archive."""
  if zip_path.suffix.lower() != '.zip':
    return

  try:
    extract_dir = zip_path.parent / zip_path.stem
    extract_dir.mkdir(parents=True, exist_ok=True)

    logger.debug(f"Unzipping {zip_path} to {extract_dir}...")
    with zipfile.ZipFile(zip_path, 'r') as zip_ref:
      for member in zip_ref.infolist():
        # Secure target path computation to prevent directory traversal (Zip Slip)
        target_path = (extract_dir / member.filename).resolve()
        try:
          target_path.relative_to(extract_dir.resolve())
        except ValueError:
          raise PermissionError(f"Security alert: Directory traversal attempt detected in ZIP: {member.filename}")
        zip_ref.extract(member, extract_dir)

    logger.debug(f"Cleaning up ZIP file: {zip_path}")
    zip_path.unlink()
    logger.debug(f"Unzip complete. Contents are in: {extract_dir}")
  except zipfile.BadZipFile:
    logger.error(f"Error: {zip_path} is not a valid ZIP file despite the extension.")
    raise
  except Exception as e:
    logger.error(f"Error during unzip: {e}")
    raise

def download_file(uri, output_path, artifact_name=None, is_directory=False, api_key=None, access_token=None):
  """Downloads a file from the given URI using gosso."""
  # Normalize bytestream:// URIs
  source_uri = uri.strip().replace(' ', '')
  if source_uri.startswith("bytestream://"):
    parsed = urlparse(source_uri)
    # Convert bytestream://hostname/path to https://hostname/v1/media/path?alt=media
    clean_path = parsed.path
    if not clean_path.startswith('/v1/media/'):
      clean_path = f"/v1/media{clean_path}"
    source_uri = f"https://{parsed.hostname}{clean_path}?alt=media"
    logger.debug(f"Converting bytestream URI to: {source_uri}")

  output_path_path = Path(output_path).expanduser()

  # Create a unique temp file in the target/parent directory
  if is_directory or output_path_path.is_dir() or str(output_path).endswith(('/', '\\')):
    target_dir = output_path_path
  else:
    target_dir = output_path_path.parent

  target_dir.mkdir(parents=True, exist_ok=True)
  temp_file_path = target_dir / f"download_tmp_{uuid.uuid4().hex}"
  minimal_env = get_minimal_env()

  try:
    logger.debug(f"Attempting to download from: {source_uri} via gosso")

    gosso_cmd = ["gosso", "-pac=true"]
    if api_key:
      gosso_cmd.extend(["-header", f"X-Goog-Api-Key: {api_key}"])
    if access_token:
      gosso_cmd.extend(["-header", f"Authorization: Bearer {access_token}"])
    gosso_cmd.extend(["-url", source_uri])

    with temp_file_path.open("wb") as f:
      result = subprocess.run(
        gosso_cmd,
        stdout=f,
        stderr=subprocess.PIPE,
        text=True,
        check=False,
        env=minimal_env
      )

    if result.returncode != 0:
      if temp_file_path.exists():
        temp_file_path.unlink()
      raise RuntimeError(f"gosso failed with code {result.returncode}. Stderr: {result.stderr.strip()}")

    # Determine actual filename
    final_output_path = resolve_output_path(
        source_uri, output_path_path, target_dir, temp_file_path, artifact_name, is_directory
    )

    if final_output_path.exists():
      final_output_path.unlink()

    temp_file_path.rename(final_output_path)
    logger.debug(f"Successfully downloaded to: {final_output_path}")

    extract_zip_archive(final_output_path)

  except IOError as e:
    logger.error(f"Error writing to file {output_path}: {e}")
    raise
  except Exception as e:
    logger.error(f"Error downloading file: {e}")
    raise

if __name__ == '__main__':
  parser = argparse.ArgumentParser(description='Download multiple files from URIs requiring gosso authentication.')
  parser.add_argument('source_uris', help='The source URIs to download from.', nargs='+')
  parser.add_argument('-o', '--output', help='The output file or directory path. If a directory, the filename will be detected from the server response.', default='~/Downloads/studio-test-artifacts/')
  parser.add_argument('--artifact_name', help='Optional name to prepend to the downloaded file for uniqueness.', default=None)

  parser.add_argument('--verbose', action='store_true', help='Enable verbose logging')

  args = parser.parse_args()
  log_level = logging.DEBUG if args.verbose else logging.INFO
  logging.basicConfig(level=log_level, format='%(levelname)s: %(message)s')

  # Expand user directory in output path
  output_path = Path(args.output).expanduser()

  is_dir = len(args.source_uris) > 1 or output_path.is_dir() or str(args.output).endswith(('/', '\\'))

  token = get_access_token()
  for uri in args.source_uris:
    download_file(uri, output_path, args.artifact_name, is_directory=is_dir, access_token=token)

