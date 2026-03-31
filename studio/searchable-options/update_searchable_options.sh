#!/bin/bash
# Find all bazel targets that end in update_searchable_options
# and run them one by one.

set -euo pipefail

# In a bazel run environment, we might need to change back to the workspace root.
if [[ -n "${BUILD_WORKSPACE_DIRECTORY:-}" ]]; then
  cd "$BUILD_WORKSPACE_DIRECTORY"
fi

echo "Querying for update_searchable_options targets..."
# Find bazel binary, try tools/base/bazel/bazel first
BAZEL="bazel"
if [[ -x "tools/base/bazel/bazel" ]]; then
  BAZEL="tools/base/bazel/bazel"
fi

TARGETS=$($BAZEL query 'kind("py_binary", filter(".*update_searchable_options.*", //...))' --noshow_progress)

if [[ -z "$TARGETS" ]]; then
  echo "No targets found."
  exit 1
fi

for TARGET in $TARGETS; do
  echo "Running $TARGET"
  $BAZEL run "$TARGET"
done

echo "All targets updated successfully."
