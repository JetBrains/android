load("//tools/adt/idea/jps-build:idea.bzl", "jps_library")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load("//tools/base/bazel:maven.bzl", "maven_repository")
load(":ij-debugger-tests.bzl", "debugger_test")

jps_library(
    name = "kotlin.jvm-debugger.test_lib",
    download_cache = "prebuilts/tools/jps-build-caches/kotlin.jvm-debugger.test_lib",
    module = "kotlin.jvm-debugger.test",
    deps = [
        "//prebuilts/tools/jps-build-caches:kotlin.jvm-debugger.test_lib",
        "//tools/idea:idea_source",
    ],
)

# Tests from inner classes of K1IdeK2CodeKotlinSteppingTestGenerated. Ordered as the appear in the class.

# 18 tests
debugger_test(
    name = "k1k2-kotlin-stepping-step-into",
    expected_to_fail_art = "art_k1k2_kotlin_stepping_expected_failures_step_into.txt",
    shard_count = 2,
    test_include_filter = ["org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.StepInto#"],
)

# 18 tests
debugger_test(
    name = "k1k2-kotlin-stepping-smart-step-into",
    shard_count = 2,
    test_include_filter = ["org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.SmartStepInto#"],
)

# 26 tests
debugger_test(
    name = "k1k2-kotlin-stepping-step-into-only",
    expected_to_fail_art = "art_k1k2_kotlin_stepping_expected_failures_step_into_only.txt",
    shard_count = 3,
    test_include_filter = ["org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.StepIntoOnly#"],
)

# 9 tests
debugger_test(
    name = "k1k2-kotlin-stepping-step-out",
    expected_to_fail_art = "art_k1k2_kotlin_stepping_expected_failures_step_out.txt",
    jvm_tags = ["ci:studio-linux-large"],
    test_include_filter = ["org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.StepOut#"],
)

# 11 tests
debugger_test(
    name = "k1k2-kotlin-stepping-step-over",
    expected_to_fail_art = "art_k1k2_kotlin_stepping_expected_failures_step_over.txt",
    test_exclude_filter = [
        # TODO(b/380457951): Investigate why these tests hang but work in AS on a real device.
        "Coroutines#testSequenceNested$",
        "Coroutines#testSequenceNested2$",
        "Coroutines#testSequenceSimple$",
        "Coroutines#testSequenceTake2$",
    ],
    test_include_filter = [
        "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.StepOver.Coroutines#",
        "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.StepOver.SteppingThroughInlineLambdas#",
    ],
)

# 114 tests
debugger_test(
    name = "k1k2-kotlin-stepping-step-over-uncategorized",
    expected_to_fail_art = "art_k1k2_kotlin_stepping_expected_failures_step_over_uncategorized.txt",
    shard_count = 10,
    test_include_filter = ["org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.StepOver.Uncategorized#"],
)

# 12 tests
debugger_test(
    name = "k1k2-kotlin-stepping-filters",
    expected_to_fail_art = "art_k1k2_kotlin_stepping_expected_failures_filters.txt",
    expected_to_fail_jvm = "jvm_k1k2_kotlin_stepping_expected_failures_filters.txt",
    test_include_filter = ["org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.Filters#"],
)

# 161 tests
debugger_test(
    name = "k1k2-kotlin-stepping-custom",
    expected_to_fail_art = "art_k1k2_kotlin_stepping_expected_failures_custom.txt",
    expected_to_fail_jvm = "jvm_k1k2_kotlin_stepping_expected_failures_custom.txt",
    shard_count = 20,
    test_include_filter = ["org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.Custom#"],
)

test_suite(
    name = "stepping-jvm",
    tests = [
        "k1k2-kotlin-stepping-custom-jvm",
        "k1k2-kotlin-stepping-filters-jvm",
        "k1k2-kotlin-stepping-smart-step-into-jvm",
        "k1k2-kotlin-stepping-step-into-jvm",
        "k1k2-kotlin-stepping-step-into-only-jvm",
        "k1k2-kotlin-stepping-step-out-jvm",
        "k1k2-kotlin-stepping-step-over-jvm",
        "k1k2-kotlin-stepping-step-over-uncategorized-jvm",
    ],
)

test_suite(
    name = "stepping-art",
    tests = [
        "k1k2-kotlin-stepping-custom-art",
        "k1k2-kotlin-stepping-filters-art",
        "k1k2-kotlin-stepping-smart-step-into-art",
        "k1k2-kotlin-stepping-step-into-art",
        "k1k2-kotlin-stepping-step-into-only-art",
        "k1k2-kotlin-stepping-step-out-art",
        "k1k2-kotlin-stepping-step-over-art",
        "k1k2-kotlin-stepping-step-over-uncategorized-art",
    ],
)

test_suite(
    name = "stepping",
    tests = [
        "stepping-art",
        "stepping-jvm",
    ],
)

# A convenience target for running a single test for debugging. To use this, replace the filter
# with whatever test desired.
debugger_test(
    name = "single",
    test_include_filter = ["org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.StepOver.Coroutines.testSequenceNested2"],
)

debugger_test(
    name = "test-android-dexer",
    test_include_filter = [
        "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinEvaluateExpressionTestGenerated.SingleBreakpoint.CompilingEvaluator\\$",
    ],
)

debugger_test(
    name = "test-shadow-fields",
    test_include_filter = [
        "K1IdeK2CodeKotlinEvaluateExpressionTestGenerated.*testInlineLambdasInAnonymousObjects",
    ],
)

maven_repository(
    name = "test_repo",
    # keep sorted: for buildifier
    artifacts = [
        "@maven//:org.jetbrains.annotations-java5_24.0.0",
    ],
    zip_prefix = "maven/",
)

kotlin_library(
    name = "attacher",
    srcs = ["ArtAttacher.kt"],
    # Disabled b/c it was causing problems w/ the coverage build, and we probably don't care about coverage of this anyways
    coverage_baseline_enabled = False,
    lint_enabled = False,
    deps = [
        ":kotlin.jvm-debugger.test_lib_import",
        "//tools/adt/idea/android-kotlin:android-dexer",
        "//tools/adt/idea/debuggers:android-field-visibility-provider",
        "@maven//:junit.junit",
    ],
)
