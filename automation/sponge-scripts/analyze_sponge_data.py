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
from concurrent.futures import ThreadPoolExecutor, as_completed
import json
import logging
import os
from pathlib import Path
import re
import subprocess
import sys
from typing import Any, Dict, List, Optional, Tuple
import uuid

from auth_utils import check_uplink_health, get_access_token, get_minimal_env
from download_sponge_artifact import download_file
from generate_prompt import generate_prompts_from_file
from get_gcp_secret import fetch_secret

logger = logging.getLogger(__name__)

DEFAULT_GCP_PROJECT = "android-studio-test-automation"
DEFAULT_SECRET_NAME = "studio-sponge-api-key"

FILE_CONFIGS = {
    "target_log": {
        "remote_pattern": "test.log",
        "exact_match": True,
        "download_name": "test.log",
        "target_filename": "test.log",
    },
    "idea_log": {
        "remote_pattern": "idea.log",
        "exact_match": False,
        "download_name": "logs",
        "target_filename": "idea.log",
    },
    "emu0_stderr_txt": {
        "remote_pattern": "emu0_stderr.txt",
        "exact_match": False,
        "download_name": "emu0_stderr.txt",
        "target_filename": "emu0_stderr.txt",
    },
    "emu0_stdout_txt": {
        "remote_pattern": "emu0_stdout.txt",
        "exact_match": False,
        "download_name": "emu0_stdout.txt",
        "target_filename": "emu0_stdout.txt",
    },
    "gradle_log": {
        "remote_pattern": "gradle.log",
        "exact_match": False,
        "download_name": "gradle.log",
        "target_filename": "gradle.log",
    },
    "target_log_alternative": {
        "target_filename": "target.log",
    },
}

FILE_MAPPING_CONFIG = [
    (config["remote_pattern"], key, config["exact_match"])
    for key, config in FILE_CONFIGS.items()
    if "remote_pattern" in config
]

DOWNLOAD_LOGS_MAP = {
    "idea_log": "logs",
    "target_log": "test.log",
}

TARGET_FILENAMES = {
    config["target_filename"]
    for config in FILE_CONFIGS.values()
    if "target_filename" in config
}

def is_valid_invocation_id(invocation_id: str) -> bool:
  """Validates that the given string is a valid Sponge invocation ID (UUID)."""
  if not isinstance(invocation_id, str):
    return False
  try:
    uuid_obj = uuid.UUID(invocation_id)
    # Ensures it is in the canonical hyphenated format
    return str(uuid_obj) == invocation_id.lower()
  except ValueError:
    return False


def valid_invocation_id_arg(val: str) -> str:
  """Argparse type for validating a Sponge Invocation ID."""
  if not is_valid_invocation_id(val):
    raise argparse.ArgumentTypeError(f"Invalid Sponge Invocation ID: '{val}'. Expected a valid UUID.")
  return val


def sanitize_filename(name: str) -> str:
  """Sanitizes a string for use in a filename."""
  return re.sub(r'[^a-zA-Z0-9_\-]', '_', name).strip('_')

def get_clean_target_name(target_id: str) -> str:
  """Extracts 'BuildAndRunJourneyTest_linux' from '//tools/...:BuildAndRunJourneyTest_linux'"""
  return target_id.split(":")[-1] if ":" in target_id else sanitize_filename(target_id)

def get_clean_action_name(action_id: str) -> str:
  """Simplifies 'test_shard0_run0_attempt0' -> 'shard0_run0'"""
  clean = action_id.replace("test_", "")
  clean = clean.replace("_attempt0", "")
  return clean

def match_target_filename(file: str) -> Optional[str]:
  """Checks if a filename matches any target filename (supporting appended unique suffixes)."""
  for tf in TARGET_FILENAMES:
    if file == tf or file.startswith(tf + "_") or file.startswith(tf + "."):
      return tf
  return None

def run_gosso_command(gosso_args: List[str], timeout: int = 180) -> Any:
  """Runs a gosso command."""
  command = ["gosso", "-pac=true"] + gosso_args
  minimal_env = get_minimal_env()

  try:
    result = subprocess.run(
      command,
      capture_output=True,
      text=True,
      check=False,
      timeout=timeout,
      env=minimal_env
    )
  except subprocess.TimeoutExpired as e:
    logger.error(f"gosso command timed out after {timeout} seconds: {e}")
    raise

  if result.returncode == 0:
    try:
      return json.loads(result.stdout)
    except json.JSONDecodeError as e:
      logger.error(f"Failed to decode JSON from gosso output: {e}")
      raise
  else:
    logger.error(f"gosso command failed with exit code {result.returncode}")
    if result.stderr:
      logger.error(f"stderr:\n{result.stderr}")
    if result.stdout:
      logger.error(f"stdout:\n{result.stdout}")
    raise subprocess.CalledProcessError(result.returncode, command, output=result.stdout, stderr=result.stderr)

def get_invocation_status_attributes(invocation_id: str, api_key: Optional[str]) -> Dict[str, Any]:
  """Fetches the top-level statusAttributes for the Invocation itself."""
  url = f"https://resultstoredownload.corp.googleapis.com/v2/invocations/{invocation_id}"
  field_mask = "status_attributes"

  gosso_args = []
  if api_key:
    gosso_args.extend(["-header", f"X-Goog-Api-Key: {api_key}"])
  gosso_args.extend([
    "-header", f"X-Goog-FieldMask: {field_mask}",
    "-url", url
  ])
  try:
    data = run_gosso_command(gosso_args, timeout=60)
    return data.get("statusAttributes", data.get("status_attributes", {}))
  except Exception as e:
    logger.warning(f"Failed to fetch invocation status attributes: {e}")
    return {}

def export_test_actions(invocation_id: str, api_key: Optional[str]) -> List[Dict[str, Any]]:
  """Fetches test actions using the targets/-/configuredTargets/-/actions endpoint."""
  base_url = f"https://resultstoredownload.corp.googleapis.com/v2/invocations/{invocation_id}/targets/-/configuredTargets/-/actions"

  field_mask = (
    "next_page_token,"
    "actions.id,"
    "actions.status_attributes,"
    "actions.test_action,"
    "actions.files.uid,"
    "actions.files.uri"
  )
  all_actions = []
  next_page_token = None

  while True:
    full_url = base_url
    if next_page_token:
      full_url = f"{base_url}?page_token={next_page_token}"

    gosso_args = []
    if api_key:
      gosso_args.extend(["-header", f"X-Goog-Api-Key: {api_key}"])
    gosso_args.extend([
      "-header", f"X-Goog-FieldMask: {field_mask}",
      "-url", full_url
    ])
    try:
      page_data = run_gosso_command(gosso_args, timeout=300)
      actions = page_data.get("actions", [])
      all_actions.extend(actions)

      next_page_token = page_data.get("nextPageToken", page_data.get("next_page_token"))
      if not next_page_token:
        break
    except Exception as e:
      logger.error(f"Error during action export: {e}")
      break
  return all_actions

def extract_test_cases(
    test_action: Dict[str, Any]
) -> Tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
  """Recursively parses the nested testAction JSON to extract a flat list of test cases."""
  failed_tests = []
  all_tests = []

  def traverse(node):
    if not isinstance(node, dict):
      return

    if "testCase" in node:
      tc = node["testCase"]
      case_info = {
        "name": tc.get("caseName", tc.get("case_name", "")),
        "class_name": tc.get("className", tc.get("class_name", "")),
        "status": tc.get("result", "UNKNOWN"),
        "duration": tc.get("timing", {}).get("duration", "0s")
      }

      all_tests.append(case_info)

      failures = tc.get("failures", [])
      errors = tc.get("errors", [])

      is_failed = (
          len(failures) > 0
          or len(errors) > 0
          or case_info["status"] not in ("COMPLETED", "SKIPPED", "PASSED", "EXPECTED", "SUPPRESSED")
      )

      if is_failed:
        case_info["failures"] = failures
        case_info["errors"] = errors
        failed_tests.append(case_info)

    if "testSuite" in node:
      ts = node["testSuite"]
      if "tests" in ts:
        for child_test in ts["tests"]:
          traverse(child_test)

  if "testSuite" in test_action:
    traverse({"testSuite": test_action["testSuite"]})

  return failed_tests, all_tests

def extract_log_urls(output_data: Dict[str, Any], base_download_dir: Path) -> Dict[str, Any]:
  download_tasks = []
  action_folders = []

  for target_id, target_data in output_data.get("targets", {}).items():
    for action_id, action_data in target_data.get("actions", {}).items():

      if action_data.get("status") == "FAILED" or action_data.get("failed_test_cases"):
        important_files = action_data.get("important_files", {})

        downloads = []
        for log_key, artifact_name in DOWNLOAD_LOGS_MAP.items():
          if log_key in important_files and important_files[log_key]:
            downloads.append((important_files[log_key], artifact_name))

        if not downloads:
          continue

        clean_target = get_clean_target_name(target_id)
        clean_action = get_clean_action_name(action_id)

        folder_name = f"{clean_target}_{clean_action}"
        folder_path = base_download_dir / folder_name
        folder_path.mkdir(parents=True, exist_ok=True)

        action_folders.append((action_data, folder_path, clean_target, clean_action))

        for uri, artifact_name in downloads:
          download_tasks.append((uri, folder_path, artifact_name, target_id, action_id))
  return download_tasks, action_folders

def download_files(
    download_tasks: List[Tuple[str, Path, str, str, str]],
    api_key: Optional[str] = None,
    access_token: Optional[str] = None,
) -> None:
  logger.info(f"Downloading files in parallel ({len(download_tasks)} tasks)")
  with ThreadPoolExecutor(max_workers=8) as executor:
    future_to_task = {
        executor.submit(
            download_file, uri, folder_path, artifact_name, api_key=api_key, access_token=access_token
        ): (uri, target_id, action_id)
        for uri, folder_path, artifact_name, target_id, action_id in download_tasks
    }

    for future in as_completed(future_to_task):
      uri, target_id, action_id = future_to_task[future]
      try:
        future.result()
      except Exception as e:
        logger.error(f"Error downloading log {uri} for {target_id} ({action_id}): {e}")

def update_local_paths(action_folders: Dict[str, Any]) -> None:
  logger.info("Post-processing downloaded logs")
  for action_data, folder_path, clean_target, clean_action in action_folders:
    local_important_files = {}

    for path in folder_path.rglob('*'):
      if path.is_file():
        matched_tf = match_target_filename(path.name)
        if matched_tf:
          absolute_path = str(path.resolve())
          rel_path = path.relative_to(folder_path)
          if rel_path.parent == Path('.'):
            key_name = matched_tf.replace('.', '_')
            if matched_tf == "test.log":
              key_name = "target_log"
          else:
            rel_dir = str(rel_path.parent)
            clean_rel_dir = re.sub(r'[^a-zA-Z0-9]', '_', rel_dir).strip('_')
            suffix = matched_tf.replace('.', '_')
            if matched_tf == "test.log":
              suffix = "target_log"
            key_name = f"{clean_rel_dir}_{suffix}"
          local_important_files[key_name] = absolute_path

    action_data["important_files"] = local_important_files
    logger.info(f"  -> {clean_target} ({clean_action}): Found {len(local_important_files)} logs. Updated paths.")

def download_failed_logs(
    output_data: Dict[str, Any],
    output_dir: str,
    api_key: Optional[str] = None,
    access_token: Optional[str] = None,
) -> None:
  """Downloads the minimal required zip archives in parallel and updates JSON with local paths."""
  base_download_dir = Path(output_dir)
  download_tasks, action_folders = extract_log_urls(output_data, base_download_dir)

  if not download_tasks:
    return
  download_files(download_tasks, api_key=api_key, access_token=access_token)
  update_local_paths(action_folders)

def fetch_api_key(gcp_project: str, secret_name: str, access_token: Optional[str] = None) -> str:
  logger.info("Fetching API key from Secret Manager...")
  try:
    token = access_token or get_access_token()
    api_key = fetch_secret(gcp_project, secret_name, token)
  except Exception as e:
    raise RuntimeError(f"Failed to fetch API key: {e}") from e

  if not api_key:
    raise ValueError("Fetched API key is empty.")

  return api_key


def analyze_sponge_data(invocation_id: str, api_key: Optional[str] = None) -> Dict[str, Any]:
  if not is_valid_invocation_id(invocation_id):
    raise ValueError(f"Invalid Sponge Invocation ID: '{invocation_id}'. Expected a valid UUID.")

  # Master output that collects everything first
  output = {
    "invocationId": invocation_id,
    "invocationStatusAttributes": get_invocation_status_attributes(invocation_id, api_key),
    "targets": {}
  }

  try:
    actions = export_test_actions(invocation_id, api_key)
  except Exception as e:
    raise RuntimeError(f"Failed to export test actions: {e}") from e

  for action in actions:
    action_id_proto = action.get('id', {})
    target_id = action_id_proto.get('targetId', action_id_proto.get('target_id'))
    action_id = action_id_proto.get('actionId', action_id_proto.get('action_id'))

    if not target_id or action_id == "build":
      continue

    raw_attrs = action.get("statusAttributes", action.get("status_attributes", {}))
    status = raw_attrs.get("status", "UNKNOWN")

    if status == "SKIPPED":
      continue

    if target_id not in output["targets"]:
      output["targets"][target_id] = {"actions": {}}

    if action_id not in output["targets"][target_id]["actions"]:
      output["targets"][target_id]["actions"][action_id] = {
        "status": status,
        "status_attributes": raw_attrs,
        "important_files": {},
        "all_test_cases": [],
        "failed_test_cases": []
      }

    action_data = output["targets"][target_id]["actions"][action_id]
    important_files = action_data["important_files"]

    for f in action.get("files", []):
      uid = f.get('uid', '')
      uri = f.get('uri', '')
      if not uri:
        continue
      for file_name, file_key, exact_match in FILE_MAPPING_CONFIG:
        if exact_match and uid == file_name:
          important_files[file_key] = uri
          break
        elif not exact_match and file_name in uid:
          important_files[file_key] = uri
          break

    test_action = action.get('testAction', action.get('test_action', {}))
    failed_cases, all_cases = extract_test_cases(test_action)

    action_data["all_test_cases"] = all_cases
    action_data["failed_test_cases"] = failed_cases

    if failed_cases and action_data["status"] == "PASSED":
      action_data["status"] = "FAILED"

  return output


def split_passed_failed_results(
    invocation_id: str, output: Dict[str, Any]
) -> Tuple[Dict[str, Any], Dict[str, Any]]:
  passed_output = {
    "invocationId": invocation_id,
    "invocationStatusAttributes": output["invocationStatusAttributes"],
    "targets": {}
  }

  failed_output = {
    "invocationId": invocation_id,
    "invocationStatusAttributes": output["invocationStatusAttributes"],
    "targets": {}
  }

  for target_id, target_data in output["targets"].items():
    for action_id, action_data in target_data["actions"].items():
      # Route to FAILED if the status is FAILED or if there are explicitly failed test cases
      if action_data.get("status") == "FAILED" or action_data.get("failed_test_cases"):
        if target_id not in failed_output["targets"]:
          failed_output["targets"][target_id] = {"actions": {}}
        failed_output["targets"][target_id]["actions"][action_id] = action_data
      else:
        if target_id not in passed_output["targets"]:
          passed_output["targets"][target_id] = {"actions": {}}
        passed_output["targets"][target_id]["actions"][action_id] = action_data

  return passed_output, failed_output


def save_results_to_directory(
    output_dir: str, passed_output: Dict[str, Any], failed_output: Dict[str, Any]
) -> Tuple[Optional[str], Optional[str]]:
  # --- SAVE TO DIRECTORY ---
  output_dir_path = Path(output_dir)
  output_dir_path.mkdir(parents=True, exist_ok=True)

  failed_json_path = output_dir_path / "failed_test_results.json"
  passed_json_path = output_dir_path / "passed_test_results.json"

  written_passed = None
  written_failed = None

  if passed_output.get("targets"):
    with passed_json_path.open("w", encoding="utf-8") as f:
      json.dump(passed_output, f, indent=2)
    written_passed = str(passed_json_path)
    logger.info(f"Passed test results saved to: {passed_json_path}")

  if failed_output.get("targets"):
    with failed_json_path.open("w", encoding="utf-8") as f:
      json.dump(failed_output, f, indent=2)
    written_failed = str(failed_json_path)
    logger.info(f"Failed test results saved to: {failed_json_path}")

  logger.info("SUCCESS")
  return written_passed, written_failed


def run_prompt_generator(failed_json_path: str) -> None:
  # --- CALL PROMPT GENERATOR ---
  logger.info("Generating AI prompts...")
  try:
    generate_prompts_from_file(failed_json_path)
  except Exception as e:
    logger.error(f"Error running prompt generator: {e}")


def run_failure_grouper(failed_json_path: str) -> None:
  """Automatically groups failures and saves the report to grouped_failures.txt."""
  logger.info("Generating grouped failures report...")
  try:
    from parse_failures import filter_failures, print_grouped

    json_path = Path(failed_json_path)
    with json_path.open("r", encoding="utf-8") as f:
      data = json.load(f)

    # Filter with no regex (get all failures to group them)
    filtered_cases = filter_failures(data, None, None, None, None)

    if not filtered_cases:
      logger.info("No failures found to group.")
      return

    out_path = json_path.parent / "grouped_failures.txt"

    with out_path.open("w", encoding="utf-8") as out_file:
      original_stdout = sys.stdout
      sys.stdout = out_file
      try:
        # Auto-group all failures (limiting to top 50 groups)
        print_grouped(filtered_cases, limit=50)
      finally:
        sys.stdout = original_stdout

    logger.info(f"Grouped failures report saved to: {out_path}")
  except Exception as e:
    logger.error(f"Error running failure grouper: {e}")


def main():
  parser = argparse.ArgumentParser(description="Fetch Sponge Invocation Data directly via API")
  parser.add_argument(
      "invocation_id",
      type=valid_invocation_id_arg,
      help="The Sponge Invocation ID (UUID)",
  )
  parser.add_argument(
      "--gcp-project",
      default=os.environ.get("STUDIO_GCP_PROJECT", DEFAULT_GCP_PROJECT),
      help="The Google Cloud Project ID containing the secret"
  )
  parser.add_argument(
      "--secret-name",
      default=os.environ.get("STUDIO_SPONGE_SECRET_NAME", DEFAULT_SECRET_NAME),
      help="The GCP Secret Manager Secret ID"
  )
  parser.add_argument(
      "--verbose",
      action="store_true",
      help="Enable verbose debug logging"
  )
  parser.add_argument(
      "--output-dir",
      default=os.environ.get("STUDIO_ARTIFACT_DIR"),
      help="The local directory path where artifacts are saved. Defaults to a temporary directory if not provided."
  )
  parser.add_argument(
      "--use-default-loas-project",
      action="store_true",
      help="Use default LOAS project for metadata authentication (bypassing GCP Secret Manager). Note: gcloud is still required to fetch an access token for downloading logs."
  )
  args = parser.parse_args()

  log_level = logging.DEBUG if args.verbose else logging.INFO
  logging.basicConfig(level=log_level, format='%(levelname)s: %(message)s')

  # Resolve output directory
  base_dir = args.output_dir
  if not base_dir:
    base_dir = "~/Downloads/studio-test-artifacts"
  output_dir = str(Path(base_dir).expanduser() / args.invocation_id)

  if not check_uplink_health():
    logger.error("Uplink health check failed. Exiting.")
    sys.exit(1)
  try:
    # We always need a GCP access token to download logs from RBE (remotebuildexecution.googleapis.com).
    # Even if --use-default-loas-project is set, RBE is a public GCP API and strictly requires a GCP
    # OAuth2 token. Therefore, we must always fetch the access token via gcloud.
    access_token = get_access_token()
    api_key = None
    if not args.use_default_loas_project:
      api_key = fetch_api_key(args.gcp_project, args.secret_name, access_token=access_token)
    output = analyze_sponge_data(args.invocation_id, api_key)
  except Exception as e:
    logger.error(f"Critical error: {e}")
    sys.exit(1)

  download_failed_logs(output, output_dir, api_key=api_key, access_token=access_token)
  passed_output, failed_output = split_passed_failed_results(args.invocation_id, output)

  passed_json_path, failed_json_path = save_results_to_directory(
      output_dir, passed_output, failed_output
  )

  if failed_json_path:
    run_prompt_generator(failed_json_path)
    run_failure_grouper(failed_json_path)
  else:
    logger.info("No failed tests found. Skipping AI prompt generation.")

if __name__ == "__main__":
  main()
