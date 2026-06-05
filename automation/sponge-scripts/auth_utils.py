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
import urllib.error
import urllib.request

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

# Inject a pristine, tightly controlled environment dictionary into the
# subprocess rather than inheriting the user's global os.environ. This
# guarantees execution consistency across different developer machines.
def get_minimal_env() -> dict:
  """Returns a tightly controlled environment dictionary for subprocess execution consistency."""
  return {
    "PATH": os.environ.get("PATH", "/usr/bin:/bin:/usr/sbin:/sbin:/usr/local/bin:/opt/homebrew/bin"),
    "USER": os.environ.get("USER", ""),
    "HOME": os.environ.get("HOME", ""),
    "LANG": os.environ.get("LANG", "en_US.UTF-8"),
  }

def get_clean_opener():
  """Returns an OpenerDirector configured with secure SSL context and optional proxy CA bundles."""
  proxy_handler = urllib.request.ProxyHandler({})
  handlers = [proxy_handler]
  try:
    import ssl
    context = ssl.create_default_context()
    ca_bundle = (
        os.environ.get("REQUESTS_CA_BUNDLE")
        or os.environ.get("SSL_CERT_FILE")
        or os.environ.get("CURL_CA_BUNDLE")
    )
    if ca_bundle and os.path.exists(ca_bundle):
      context.load_verify_locations(cafile=ca_bundle)
    handlers.append(urllib.request.HTTPSHandler(context=context))
  except Exception:
    pass
  return urllib.request.build_opener(*handlers)

# Validates that the local Uplink daemon (127.0.0.1:999) is actively
# routing traffic before attempting any network calls.
def check_uplink_health():
  try:
    proxy_url = "http://127.0.0.1:999/healthz"
    opener = get_clean_opener()
    req = urllib.request.Request(proxy_url)
    with opener.open(req, timeout=5) as response:
      status_code = response.getcode()
      text = response.read().decode("utf-8")
      if text.strip().lower() == "ok":
        return True
      else:
        logger.error(f"Uplink health check on 127.0.0.1: Failed (Status: {status_code}, Response: {text})")
        return False
  except urllib.error.HTTPError as e:
    try:
      text = e.read().decode("utf-8")
    except Exception:
      text = ""
    logger.error(f"Uplink health check on 127.0.0.1: Failed (Status: {e.code}, Response: {text})")
    return False
  except (urllib.error.URLError, TimeoutError, Exception) as e:
    logger.error(f"Uplink health check on 127.0.0.1: Failed ({e}). Is Uplink running?")
    return False

