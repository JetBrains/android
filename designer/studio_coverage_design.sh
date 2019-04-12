#!/bin/bash -x
# Invoked by Android Build Launchcontrol for continuous builds.
readonly dist_dir="$1"
readonly build_number="$2"

readonly script_dir="$(cd $(dirname "$0")/../../../base/bazel; pwd)"

# Grab the location of the command_log file for bazel daemon so we can search it later.
readonly command_log="$("${script_dir}"/bazel info command_log)"

# Run Bazel with coverage instrumentation
"${script_dir}/bazel" \
  --max_idle_secs=60 \
  test \
  ${auth_options} \
  --test_tag_filters=-no_linux,-no_test_linux \
  --define agent_coverage=true \
  -- \
  //tools/adt/idea/designer/... \

# We want to still attach the upsalite link if tests fail so we can't abort now
readonly bazel_test_status=$?

# Grab the upsalite_id from the stdout of the bazel command.  This is captured in command.log
readonly upsalite_id="$(sed -n 's/\r$//;s/^.* invocation_id: //p' "${command_log}")"

if [[ -d "${dist_dir}" ]]; then
  # Link to test results
  echo "<meta http-equiv=\"refresh\" content=\"0; URL='https://source.cloud.google.com/results/invocations/${upsalite_id}'\" />" > "${dist_dir}"/upsalite_test_results.html
fi

# Abort if necessary now that the upsalite link is handled
if [[ ! ${bazel_test_status} ]]; then
  exit ${bazel_test_status}
fi

# Create a temp file to pass production targets to report generator
readonly production_targets_file=$(mktemp)

# Collect the production targets
readonly universe="//tools/... - //tools/adt/idea/android-uitests/..."

"${script_dir}/bazel" \
  query \
  "kind(test, rdeps(${universe}, deps(//tools/base:coverage_report)))" \
  | tee $production_targets_file \
  || exit $?

# Generate the Jacoco report
readonly testlogs_dir="$(${script_dir}/bazel info bazel-testlogs)"

"${script_dir}/bazel" \
  run \
  //tools/base:coverage_report \
  ${auth_options} \
  -- \
  tools/base/coverage_report \
  $production_targets_file \
  $testlogs_dir \
  || exit $?

# Resolve to sourcefiles and convert to LCOV
python "${script_dir}/jacoco_to_lcov.py" || exit $?

# Generate LCOV style HTML report
genhtml -o "./out/html" "./out/lcov" -p $(pwd) --no-function-coverage || exit $?

if [[ -d "${dist_dir}" ]]; then
  # Copy the report to ab/ outputs
  mkdir "${dist_dir}/coverage"
  cp -pv "./out/lcov" "${dist_dir}/coverage"
  cp -pv "./out/worst" "${dist_dir}/coverage"
  cp -pv "./out/worstNoFiles" "${dist_dir}/coverage"
  cp -pv "./out/missing" "${dist_dir}/coverage"
  cp -pv "./out/fake" "${dist_dir}/coverage"
  # HTML report needs to be zipped for fast uploads
  pushd "./out"
  zip -r "html.zip" "./html"
  popd
  mv -v "./out/html.zip" "${dist_dir}/coverage"

  # Upload the LCOV data to GCS if running on BYOB
  if [[ "$build_number" ]]; then
    gsutil cp "./out/lcov" "gs://android-devtools-archives/ab-studio-coverage/${build_number}/" || exit $?
  fi
fi

exit 0
