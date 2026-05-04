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
from pathlib import Path
from string import Template
import sys

logger = logging.getLogger(__name__)

def generate_prompt_text(template: Template, target_id: str, action_data: dict) -> str:
  """Injects failure data into the prompt template using safe substitution."""
  # Extract clean target name
  target_name = target_id.split(":")[-1] if ":" in target_id else target_id

  # 1. Format the failing test cases
  failed_cases = action_data.get("failed_test_cases", [])
  test_cases_section = ""
  if failed_cases:
    test_cases_section = "### Specific Failing Tests:\n"
    for tc in failed_cases:
      class_name = tc.get("class_name", "UnknownClass")
      test_name = tc.get("name", "unknownTest")
      test_cases_section += f"- {class_name}.{test_name}\n"
    test_cases_section += "\n"

  # 2. Format the local log file paths
  important_files = action_data.get("important_files", {})
  logs_section = "### Log Files (Local Paths):\n"
  if important_files:
    for log_type, local_path in important_files.items():
      logs_section += f"- {log_type}: {local_path}\n"
  else:
    logs_section += "- No specific logs were downloaded for this target.\n"

  # 3. Inject the variables into the template from prompt.txt
  return template.safe_substitute(
      target_name=target_name,
      test_cases_section=test_cases_section,
      logs_section=logs_section
  )

def generate_prompts_from_file(input_json: str) -> None:
  input_json_path = Path(input_json)
  if not input_json_path.exists():
    raise FileNotFoundError(f"Input file '{input_json}' does not exist.")

  script_dir = Path(__file__).resolve().parent
  prompt_template_path = script_dir / "prompt.txt"

  if not prompt_template_path.exists():
    raise FileNotFoundError(f"Template file not found at {prompt_template_path}")

  with prompt_template_path.open('r', encoding='utf-8') as f:
    prompt_template_str = f.read()
  prompt_template = Template(prompt_template_str)

  try:
    with input_json_path.open('r', encoding='utf-8') as f:
      data = json.load(f)
  except json.JSONDecodeError as e:
    raise ValueError(f"Error parsing JSON: {e}") from e

  targets = data.get("targets", {})
  if not targets:
    logger.warning("No failed targets found in the provided JSON.")
    return

  ai_prompts = []

  # Loop through the failed tests and generate a prompt using the template
  for target_id, target_data in targets.items():
    for action_id, action_data in target_data.get("actions", {}).items():
      prompt_text = generate_prompt_text(prompt_template, target_id, action_data)

      ai_prompts.append({
        "target_id": target_id,
        "action_id": action_id,
        "prompt": prompt_text
      })

  output_dir = input_json_path.resolve().parent
  output_file = output_dir / "prompts.json"

  with output_file.open('w', encoding='utf-8') as f:
    json.dump(ai_prompts, f, indent=2)

  logger.info(f"Successfully generated {len(ai_prompts)} AI prompts.")
  logger.info(f"Prompts saved to: {output_file}")

def main():
  parser = argparse.ArgumentParser(description="Generate AI prompts from failed Sponge tests.")
  parser.add_argument("input_json", help="Path to the failed_test_results.json file")
  parser.add_argument("--verbose", action="store_true", help="Enable verbose debug logging")
  args = parser.parse_args()

  log_level = logging.DEBUG if args.verbose else logging.INFO
  logging.basicConfig(level=log_level, format='%(levelname)s: %(message)s')

  try:
    generate_prompts_from_file(args.input_json)
  except Exception as e:
    logger.error(f"Error: {e}")
    sys.exit(1)

if __name__ == "__main__":
  main()
