#!/bin/bash
# Find all bazel targets that end in update_expected_studio_files
# and run them one by one.

set -euo pipefail

# In a bazel run environment, we might need to change back to the workspace root.
if [[ -n "${BUILD_WORKSPACE_DIRECTORY:-}" ]]; then
  cd "$BUILD_WORKSPACE_DIRECTORY"
fi

echo "Querying for update_expected_studio_files targets..."
# Find bazel binary, try tools/base/bazel/bazel first
BAZEL="bazel"
if [[ -x "tools/base/bazel/bazel" ]]; then
  BAZEL="tools/base/bazel/bazel"
fi

TARGETS=$($BAZEL query 'kind("py_binary", filter("update_expected_studio_files", //...))' --noshow_progress)

if [[ -z "$TARGETS" ]]; then
  echo "No targets found."
  exit 1
fi

for TARGET in $TARGETS; do
  echo "Running $TARGET"
  # Using --norun_validations here to skip the "check_plugin" validation normally done when building the distro.
  $BAZEL run --norun_validations "$TARGET"
done

echo "All targets updated successfully."
