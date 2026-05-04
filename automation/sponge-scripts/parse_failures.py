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
import json
import logging
import os
from pathlib import Path
import re
import sys
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)

DEFAULT_ARTIFACT_DIR = "~/Downloads/studio-test-artifacts"

def resolve_json_path(json_path_arg: Optional[str], invocation_id: Optional[str]) -> Path:
  """Resolves the path to the failed_test_results.json file."""
  if json_path_arg:
    path = Path(json_path_arg).expanduser()
    if not path.exists():
      logger.error(f"Specified JSON path does not exist: {path}")
      sys.exit(1)
    return path

  if invocation_id:
    base_dir = os.environ.get("STUDIO_ARTIFACT_DIR", DEFAULT_ARTIFACT_DIR)
    path = Path(base_dir).expanduser() / invocation_id / "failed_test_results.json"
    if not path.exists():
      # Try fallback to sponge-output directory in workspace if running from workspace root
      path_fallback = Path("sponge-output") / invocation_id / "failed_test_results.json"
      if path_fallback.exists():
        return path_fallback.resolve()

      logger.error(f"Could not find failed_test_results.json for invocation {invocation_id}")
      logger.error(f"Checked: {path} and {path_fallback.resolve() if not path_fallback.is_absolute() else path_fallback}")
      sys.exit(1)
    return path

  logger.error("Either --json-path or --invocation-id must be specified.")
  sys.exit(1)

def filter_failures(
    data: Dict[str, Any],
    target_filter: Optional[str],
    class_filter: Optional[str],
    method_filter: Optional[str],
    message_filter: Optional[str]
) -> List[Dict[str, Any]]:
  """Filters the failed test cases based on criteria."""
  targets = data.get("targets", {})
  filtered_cases = []

  for target_name, target_data in targets.items():
    if target_filter and not re.search(target_filter, target_name, re.IGNORECASE):
      continue

    actions = target_data.get("actions", {})
    for action_name, action_data in actions.items():
      failed_cases = action_data.get("failed_test_cases", [])
      for case in failed_cases:
        class_name = case.get("class_name", "")
        method_name = case.get("name", "")  # 'name' in case dict is the method name

        if class_filter and not re.search(class_filter, class_name, re.IGNORECASE):
          continue
        if method_filter and not re.search(method_filter, method_name, re.IGNORECASE):
          continue

        # Extract failure messages to check against message filter
        failures = case.get("failures", [])
        errors = case.get("errors", [])
        combined_messages = []
        for f in failures + errors:
          msg = f.get("failureMessage", f.get("message", ""))
          if msg:
            combined_messages.append(msg)

        combined_msg_str = " ".join(combined_messages)
        if message_filter and not re.search(message_filter, combined_msg_str, re.IGNORECASE):
          continue

        # Keep a reference to the target and action for context
        case_copy = dict(case)
        case_copy["_target"] = target_name
        case_copy["_action"] = action_name
        filtered_cases.append(case_copy)

  return filtered_cases

def print_summary(filtered_cases: List[Dict[str, Any]]) -> None:
  """Prints a summary of the filtered failures."""
  target_counts = {}
  class_counts = {}

  for case in filtered_cases:
    target = case["_target"]
    clazz = case.get("class_name", "UnknownClass")

    target_counts[target] = target_counts.get(target, 0) + 1
    class_counts[clazz] = class_counts.get(clazz, 0) + 1

  print("\n=== FAILURE SUMMARY BY TARGET ===")
  for target, count in sorted(target_counts.items(), key=lambda x: x[1], reverse=True):
    print(f"  {count:4d}  {target}")

  print("\n=== FAILURE SUMMARY BY CLASS ===")
  for clazz, count in sorted(class_counts.items(), key=lambda x: x[1], reverse=True):
    print(f"  {count:4d}  {clazz}")

  print(f"\nTotal Matching Failures: {len(filtered_cases)}")

def print_details(filtered_cases: List[Dict[str, Any]], verbose: bool, limit: int) -> None:
  """Prints detailed failure information."""
  print(f"\nShowing {min(limit, len(filtered_cases))} of {len(filtered_cases)} matching failures:\n")

  for i, case in enumerate(filtered_cases[:limit]):
    print(f"--- Failure {i+1} ---")
    print(f"Target:  {case['_target']}")
    print(f"Action:  {case['_action']}")
    print(f"Class:   {case.get('class_name')}")
    print(f"Method:  {case.get('name')}")
    print(f"Status:  {case.get('status')}")

    failures = case.get("failures", [])
    errors = case.get("errors", [])

    all_errs = failures + errors
    if not all_errs:
      print("  (No explicit failure/error messages captured in JSON)")

    for j, err in enumerate(all_errs):
      prefix = f"  [{j+1}] "
      msg = err.get("failureMessage", err.get("message", "No message"))
      exc_type = err.get("exceptionType", err.get("type", ""))
      stack = err.get("stackTrace", err.get("stack_trace", ""))

      if exc_type:
        print(f"{prefix}Type: {exc_type}")
      print(f"{prefix}Message:\n{msg}")

      if stack:
        if verbose:
          print(f"{prefix}Stack Trace:\n{stack}")
        else:
          # Print first 5 lines of stack trace
          lines = stack.strip().split('\n')
          truncated_stack = '\n'.join(lines[:6])
          if len(lines) > 6:
            truncated_stack += f"\n{prefix}... (truncated {len(lines) - 6} lines, use --verbose to see full stack)"
          print(f"{prefix}Stack Trace (truncated):\n{truncated_stack}")
    print()

def normalize_message(message: str) -> str:
  """Normalizes a failure message to group similar errors together.

  Strips out dynamic components like hex addresses, numbers, and paths.
  """
  if not message:
    return "No explicit failure message captured"

  # Take the first line of the message (common root cause is usually here)
  first_line = message.strip().split('\n')[0]

  # Remove hex addresses (e.g. 0x7f8a1c)
  normalized = re.sub(r'0x[0-9a-fA-F]+', '0xHEX', first_line)

  # Remove numeric IDs, ports, or emulator numbers
  normalized = re.sub(r'\b\d+\b', 'NUM', normalized)

  # Normalize system/temp paths (e.g. /tmp/foo -> PATH)
  normalized = re.sub(r'/[^:\s]+', 'PATH', normalized)

  # Collapse multiple spaces
  normalized = re.sub(r'\s+', ' ', normalized).strip()

  return normalized

def print_grouped(filtered_cases: List[Dict[str, Any]], limit: int) -> None:
  """Groups failures by their normalized failure message and prints them."""
  groups = {}

  for case in filtered_cases:
    failures = case.get("failures", [])
    errors = case.get("errors", [])
    messages = [f.get("failureMessage", f.get("message", "")) for f in failures + errors]
    combined_msg = " ".join([m for m in messages if m])

    cause = normalize_message(combined_msg)

    if cause not in groups:
      groups[cause] = []
    groups[cause].append(case)

  # Sort groups by size (largest group first)
  sorted_groups = sorted(groups.items(), key=lambda x: len(x[1]), reverse=True)

  print(f"\n=== FAILURES GROUPED BY PROBABLE ROOT CAUSE ({len(sorted_groups)} unique causes) ===\n")

  for i, (cause, cases) in enumerate(sorted_groups[:limit]):
    print(f"--- Cause Group {i+1} (Count: {len(cases)}) ---")
    print(f"Probable Cause: {cause}")
    print("Affected Tests:")
    for case in cases[:5]:
      print(f"  - {case['_target']} | {case.get('class_name')}.{case.get('name')}")
    if len(cases) > 5:
      print(f"  - ... and {len(cases) - 5} more")
    print()

def main():
  parser = argparse.ArgumentParser(
      description="Parse and filter failed_test_results.json from Sponge runs"
  )

  # Input source options (one is required)
  group = parser.add_mutually_exclusive_group(required=True)
  group.add_argument(
      "--json-path", "-p",
      help="Direct path to failed_test_results.json"
  )
  group.add_argument(
      "--invocation-id", "-i",
      help="Sponge Invocation ID (UUID) to auto-resolve path"
  )

  # Filtering options
  parser.add_argument(
      "--target", "-t",
      help="Regex to filter by target name (e.g., 'SantaTracker')"
  )
  parser.add_argument(
      "--class", "-c",
      dest="class_name",
      help="Regex to filter by test class name (e.g., 'BenchmarkTest')"
  )
  parser.add_argument(
      "--method", "-m",
      help="Regex to filter by test method name"
  )
  parser.add_argument(
      "--message", "-g",
      help="Regex to filter by failure message content"
  )

  # Output options
  parser.add_argument(
      "--summary", "-s",
      action="store_true",
      help="Print aggregated summary of failures instead of details"
  )
  parser.add_argument(
      "--group", "-G",
      action="store_true",
      help="Group failures by probable root cause signature"
  )
  parser.add_argument(
      "--verbose", "-v",
      action="store_true",
      help="Print full stack traces (default is truncated to 5 lines)"
  )
  parser.add_argument(
      "--limit", "-l",
      type=int,
      default=20,
      help="Limit the number of detailed failures printed (default 20)"
  )
  parser.add_argument(
      "--output", "-o",
      help="Path to save the output report. If not specified, auto-saved to the input JSON's directory."
  )

  args = parser.parse_args()

  logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')

  json_path = resolve_json_path(args.json_path, args.invocation_id)
  logger.info(f"Parsing test results from: {json_path}")

  try:
    with json_path.open("r", encoding="utf-8") as f:
      data = json.load(f)
  except Exception as e:
    logger.error(f"Failed to read or parse JSON file: {e}")
    sys.exit(1)

  filtered_cases = filter_failures(
      data,
      args.target,
      args.class_name,
      args.method,
      args.message
  )

  if not filtered_cases:
    logger.info("No failures found matching the criteria.")
    return

  # Resolve output file path
  if args.output:
    out_path = Path(args.output).expanduser()
  else:
    if args.summary:
      filename = "failure_summary.txt"
    elif args.group:
      filename = "grouped_failures.txt"
    else:
      filename = f"failure_details_limit_{args.limit}.txt"
    out_path = json_path.parent / filename

  logger.info(f"Saving report to: {out_path}")

  try:
    with out_path.open("w", encoding="utf-8") as out_file:
      # Temporarily redirect stdout to the file
      original_stdout = sys.stdout
      sys.stdout = out_file
      try:
        if args.summary:
          print_summary(filtered_cases)
        elif args.group:
          print_grouped(filtered_cases, args.limit)
        else:
          print_details(filtered_cases, args.verbose, args.limit)
      finally:
        sys.stdout = original_stdout
    logger.info("Report saved successfully.")
  except Exception as e:
    logger.error(f"Failed to write report to {out_path}: {e}")
    sys.exit(1)

if __name__ == "__main__":
  main()
