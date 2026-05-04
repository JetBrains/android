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

import logging
import os
import subprocess
import sys

import requests

logger = logging.getLogger(__name__)

PROXY_ENV_KEYS = [
  'REQUESTS_CA_BUNDLE', 'CURL_CA_BUNDLE', 'SSL_CERT_FILE',
  'http_proxy', 'https_proxy', 'HTTP_PROXY', 'HTTPS_PROXY'
]

_access_token_cache = None

# Returns a copy of the environment with proxy and CA cert variables stripped.
# This is used to invoke subprocesses (like gcloud) without corporate proxy interference.
def get_clean_env() -> dict:
  """Returns a copy of the environment with proxy and SSL variables removed."""
  clean_env = os.environ.copy()
  for key in PROXY_ENV_KEYS:
    clean_env.pop(key, None)
  return clean_env

def get_access_token():
  """Fetches the application default access token from gcloud safely."""
  global _access_token_cache
  if _access_token_cache is not None:
    return _access_token_cache

  # Hide proxy routing and proxy certs from gcloud to avoid roots.pem / SSL issues
  clean_env = get_clean_env()

  try:
    process = subprocess.run(
      ['gcloud', 'auth', 'application-default', 'print-access-token'],
      capture_output=True,
      text=True,
      check=True,
      env=clean_env
    )
    _access_token_cache = process.stdout.strip()
    return _access_token_cache
  except subprocess.CalledProcessError as e:
    raise RuntimeError(f"Error getting access token from gcloud: {e}\nStderr: {e.stderr}") from e
  except FileNotFoundError as e:
    raise FileNotFoundError("Error: 'gcloud' command not found.") from e

# Validates that the local Uplink daemon (127.0.0.1:999) is actively
# routing traffic before attempting any network calls.
def check_uplink_health():
  try:
    proxy_url = "http://127.0.0.1:999/healthz"
    session = requests.Session()
    session.trust_env = False
    response = session.get(proxy_url, timeout=5)
    if response.text.strip().lower() == "ok":
      return True
    else:
      logger.error(f"Uplink health check on 127.0.0.1: Failed (Status: {response.status_code}, Response: {response.text})")
      return False
  except requests.exceptions.RequestException as e:
    logger.error(f"Uplink health check on 127.0.0.1: Failed ({e}). Is Uplink running?")
    return False

