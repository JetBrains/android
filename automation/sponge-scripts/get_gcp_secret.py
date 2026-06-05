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
import base64
import json
import subprocess
import sys
import urllib.error
import urllib.request

from auth_utils import get_access_token, get_clean_opener


def fetch_secret(project_id: str, secret_id: str, token: str) -> str:
  """Fetches the latest secret version from Secret Manager and decodes it."""
  url = f"https://secretmanager.googleapis.com/v1/projects/{project_id}/secrets/{secret_id}/versions/latest:access"

  headers = {
    "Authorization": f"Bearer {token}"
  }

  try:
    opener = get_clean_opener()
    req = urllib.request.Request(url, headers=headers)
    with opener.open(req, timeout=30) as response:
      json_data = json.loads(response.read().decode("utf-8"))

    b64_payload = json_data.get("payload", {}).get("data")

    if not b64_payload:
      raise ValueError("Secret payload is empty or not found in the response.")

    # The secret comes back as base64, so we decode it to plain text
    decoded_secret = base64.b64decode(b64_payload).decode('utf-8')
    return decoded_secret

  except (urllib.error.URLError, TimeoutError, Exception) as e:
    raise RuntimeError(f"Error fetching secret via API: {e}") from e

def main():
  parser = argparse.ArgumentParser(description="Fetch and decode a Google Cloud Secret.")
  parser.add_argument("project_id", help="The Google Cloud Project ID")
  parser.add_argument("secret_id", help="The Secret ID (e.g., api-key-secret)")

  args = parser.parse_args()

  try:
    # 1. Get auth token
    token = get_access_token()

    # 2. Fetch and decode the secret
    secret_value = fetch_secret(args.project_id, args.secret_id, token)
  except Exception as e:
    print(f"Error: {e}", file=sys.stderr)
    sys.exit(1)

  # 3. Print ONLY the secret value to stdout so other scripts can capture it
  print(secret_value, end="")

if __name__ == "__main__":
  main()
