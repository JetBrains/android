---
name: analyze-sponge-data
description: >-
  Fetches, downloads, and analyzes Sponge invocation test artifacts, failed logs,
  and test cases, and generates prompts for AI diagnosis. Use when the user asks
  to analyze a Sponge invocation ID, download failing logs from a test run, or
  gather test failures and class names for AI debugging. Don't use for standard
  g4 or git operations.
---

# Analyze Sponge Data

Use this skill to download and analyze test logs and test suite results for any Sponge Invocation ID.

## Script Paths
- **Main Orchestrator:** `tools/adt/idea/automation/sponge-scripts/analyze_sponge_data.py`
- **Failure Query Utility:** `tools/adt/idea/automation/sponge-scripts/parse_failures.py`

## Prerequisites
- The script executes using `python3` and relies on `urllib.request` and `gosso` internally.
- A stable corporate network/gCorp VPN connection is required to access corp-restricted endpoints (e.g. ResultStore and Secret Manager) and pass the script's uplink health check.
- You must have Google Cloud SDK / gcloud authenticated to fetch the API key from Secret Manager.

## Usage Instructions

Run the script using `python3` relative to the workspace root. Always pass the `invocation_id` as the first positional argument.

### 1. Basic Execution (Standard Analysis)
To run standard analysis and download failed logs to the default directory (`~/Downloads/studio-test-artifacts/{invocation_id}`):

```bash
python3 tools/adt/idea/automation/sponge-scripts/analyze_sponge_data.py {invocation_id}
```

### 2. Customize Output Directory
To download and save logs/results to a specific workspace-relative or custom path, use the `--output-dir` flag:

```bash
python3 tools/adt/idea/automation/sponge-scripts/analyze_sponge_data.py {invocation_id} --output-dir {output_dir_path}
```

### 3. Verbose Debug Logging
If troubleshooting or experiencing issues, append `--verbose`:

```bash
python3 tools/adt/idea/automation/sponge-scripts/analyze_sponge_data.py {invocation_id} --verbose
```

### 4. Custom GCP Credentials
If the API Key needs to be fetched from a different GCP Project or Secret:

```bash
python3 tools/adt/idea/automation/sponge-scripts/analyze_sponge_data.py {invocation_id} --gcp-project {project_id} --secret-name {secret_name}
```

### 5. Authentication using Default LOAS
If executing on a workstation or desktop with an active default LOAS project, avoid requiring `gcloud` by appending `--use-default-loas-project`:

```bash
python3 tools/adt/idea/automation/sponge-scripts/analyze_sponge_data.py {invocation_id} --use-default-loas-project
```

### 6. Querying and Filtering Failures Locally
Once `failed_test_results.json` is generated, you can use the helper script `parse_failures.py` to quickly filter and inspect failures without opening the raw JSON:

```bash
# Save default details report to failure_details_limit_20.txt
python3 tools/adt/idea/automation/sponge-scripts/parse_failures.py --invocation-id {invocation_id}

# Filter by target name (regex)
python3 tools/adt/idea/automation/sponge-scripts/parse_failures.py --invocation-id {invocation_id} --target {target_regex}

# Filter by class name (regex)
python3 tools/adt/idea/automation/sponge-scripts/parse_failures.py --invocation-id {invocation_id} --class {class_regex}

# Save aggregated summary to failure_summary.txt
python3 tools/adt/idea/automation/sponge-scripts/parse_failures.py --invocation-id {invocation_id} --summary

# Save grouped failures by probable root cause to grouped_failures.txt
python3 tools/adt/idea/automation/sponge-scripts/parse_failures.py --invocation-id {invocation_id} --group

# Save to a custom file path
python3 tools/adt/idea/automation/sponge-scripts/parse_failures.py --invocation-id {invocation_id} --group --output {custom_output_path}
```

## Expected Outputs

Upon successful execution, the script creates a subdirectory named after the `{invocation_id}` under the configured output directory containing:
1. `passed_test_results.json`: A JSON list of all successfully completed or skipped test actions.
2. `failed_test_results.json`: A JSON list of all failing actions, suite failures, and failed test case traces (can be analyzed using `parse_failures.py`).
3. `{clean_target_name}_{clean_action_name}/`: Folders containing downloaded logs (`test.log`, `idea.log`, etc.) for each failed test action.
4. Prompt files generated under the output directory for AI diagnosis.
5. `grouped_failures.txt`: An automatically generated report summarizing all failures grouped by probable root cause signature.
