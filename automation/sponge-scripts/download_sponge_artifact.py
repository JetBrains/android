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
import mimetypes
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urlparse
import zipfile

import requests

from auth_utils import get_access_token

logger = logging.getLogger(__name__)


def get_filename_from_response(r, uri, artifact_name=None):
  """Determines the filename from Content-Disposition, artifact_name, URI, or Content-Type."""
  logger.debug(f"Response headers: {r.headers}")

  # 1. Try Content-Disposition header
  cd = r.headers.get('Content-Disposition')
  if cd:
    fname = re.findall('filename=(?:"([^"]+)"|([^;]+))', cd)
    if fname:
      name = (fname[0][0] or fname[0][1]).strip().strip('"')
      logger.debug(f"Detected filename from Content-Disposition: {name}")
      return name

  # 2. If artifact_name is specified, use it directly
  if artifact_name:
    logger.debug(f"Using provided artifact name: {artifact_name}")
    return artifact_name

  # 3. Use the filename from the URI path
  path = uri.split('?')[0]
  filename_from_uri = Path(path).name
  if not filename_from_uri or filename_from_uri == '/':
    filename_from_uri = "download"

  # 4. Try Content-Type header to guess extension if the name has no extension
  filename_path = Path(filename_from_uri)
  if not filename_path.suffix:
    ct = r.headers.get('Content-Type')
    if ct:
      logger.debug(f"Content-Type is: {ct}")
      ext = mimetypes.guess_extension(ct.split(';')[0])
      if ext:
        filename_from_uri = f"{filename_from_uri}{ext}"
        logger.debug(f"Guessed extension from Content-Type: {ext} -> {filename_from_uri}")

  return filename_from_uri

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

def download_file(uri, output_path, artifact_name=None, is_directory=False):
  """Downloads a file from the given URI using a bearer token."""
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

  token = get_access_token()
  if not token:
    return

  headers = {
    'Authorization': f'Bearer {token}'
  }

  try:
    logger.debug(f"Attempting to download from: {source_uri}")
    session = requests.Session()
    session.trust_env = False
    with session.get(source_uri, headers=headers, stream=True, allow_redirects=True, timeout=60) as r:
      r.raise_for_status()  # Raise an exception for bad status codes

      # Peek at the first chunk to help identify the file type
      content_iterator = r.iter_content(chunk_size=8192)
      try:
        first_chunk = next(content_iterator)
      except StopIteration:
        first_chunk = b""

      output_path_path = Path(output_path)

      if is_directory or output_path_path.is_dir() or str(output_path).endswith(('/', '\\')):
        filename = get_filename_from_response(r, source_uri, artifact_name)

        # If headers were generic or result in .bin, try sniffing the content
        filename_path = Path(filename)
        if filename_path.suffix == '.bin' or not filename_path.suffix:
          sniffed_ext = sniff_extension(first_chunk)
          if sniffed_ext:
            filename_path = filename_path.with_suffix(sniffed_ext)

        final_output_path = output_path_path / filename_path
      else:
        final_output_path = output_path_path

      # Ensure output directory for final path exists
      final_output_path.parent.mkdir(parents=True, exist_ok=True)
      logger.debug(f"Created directory: {final_output_path.parent}")

      with final_output_path.open('wb') as f:
        f.write(first_chunk)
        for chunk in content_iterator:
          f.write(chunk)

    logger.debug(f"Successfully downloaded to: {final_output_path}")

    # Handle unzipping if it's a ZIP file
    if final_output_path.suffix.lower() == '.zip':
      try:
        extract_dir = final_output_path.parent / final_output_path.stem
        extract_dir.mkdir(parents=True, exist_ok=True)

        logger.debug(f"Unzipping {final_output_path} to {extract_dir}...")
        with zipfile.ZipFile(final_output_path, 'r') as zip_ref:
          for member in zip_ref.infolist():
            # Secure target path computation to prevent directory traversal (Zip Slip)
            target_path = (extract_dir / member.filename).resolve()
            try:
              target_path.relative_to(extract_dir.resolve())
            except ValueError:
              raise PermissionError(f"Security alert: Directory traversal attempt detected in ZIP: {member.filename}")
            zip_ref.extract(member, extract_dir)

        logger.debug(f"Cleaning up ZIP file: {final_output_path}")
        final_output_path.unlink()
        logger.debug(f"Unzip complete. Contents are in: {extract_dir}")
      except zipfile.BadZipFile:
        logger.error(f"Error: {final_output_path} is not a valid ZIP file despite the extension.")
        raise
      except Exception as e:
        logger.error(f"Error during unzip: {e}")
        raise

  except requests.exceptions.RequestException as e:
    logger.error(f"Error downloading file: {e}")
    raise
  except IOError as e:
    logger.error(f"Error writing to file {output_path}: {e}")
    raise

if __name__ == '__main__':
  parser = argparse.ArgumentParser(description='Download multiple files from URIs requiring gcloud ADC authentication.')
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

  for uri in args.source_uris:
    download_file(uri, output_path, args.artifact_name, is_directory=is_dir)

