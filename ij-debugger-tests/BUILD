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

debugger_test(
    name = "stepping",
    expected_to_fail_art = "stepping-art.txt",
    expected_to_fail_jvm = "stepping-jvm.txt",
    shard_count = 50,
    test_include_filter = ["org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated\\$"],
)

debugger_test(
    name = "evaluate-expression",
    expected_to_fail_art = "evaluate-expression-art.txt",
    expected_to_fail_jvm = "evaluate-expression-jvm.txt",
    shard_count = 50,
    test_exclude_filter = [
        # Flaky
        "K1IdeK2CodeKotlinEvaluateExpressionTestGenerated.SingleBreakpoint.Uncategorized.testAnnotationValue",
    ],
    test_include_filter = ["org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinEvaluateExpressionTestGenerated\\$"],
)

# A convenience target for running a single test for debugging. To use this, replace the filter
# with whatever test desired.
debugger_test(
    name = "single",
    test_include_filter = ["K1IdeK2CodeKotlinEvaluateExpressionTestGenerated.SingleBreakpoint.Uncategorized.testAnnotationValue"],
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
