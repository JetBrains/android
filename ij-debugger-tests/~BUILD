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

jps_library(
    name = "kotlin.jvm-debugger.test.k2_lib",
    download_cache = "prebuilts/tools/jps-build-caches/kotlin.jvm-debugger.test_lib",
    module = "kotlin.jvm-debugger.test.k2",
    deps = [
        "//prebuilts/tools/jps-build-caches:kotlin.jvm-debugger.test_lib",
        "//tools/idea:idea_source",
    ],
)

# 369 tests - 94 ART tests expected to fail
debugger_test(
    name = "stepping-k1-k2",
    expected_to_fail_art = "stepping-k1-k2-art.txt",
    expected_to_fail_jvm = "stepping-k1-k2-jvm.txt",
    shard_count = 50,
    test_include_filter = ["K1IdeK2CodeKotlinSteppingTestGenerated\\$"],
)

# 369 tests - 80 ART tests expected to fail
debugger_test(
    name = "stepping-k1-ir",
    expected_to_fail_art = "stepping-k1-ir-art.txt",
    expected_to_fail_jvm = "stepping-k1-ir-jvm.txt",
    shard_count = 50,
    test_exclude_filter = ["IndyLambdaIrKotlinSteppingTestGenerated"],
    test_include_filter = ["IrKotlinSteppingTestGenerated\\$"],
)

# 369 tests - 98 ART tests expected to fail
debugger_test(
    name = "stepping-k1-indy",
    expected_to_fail_art = "stepping-k1-indy-art.txt",
    expected_to_fail_jvm = "stepping-k1-indy-jvm.txt",
    shard_count = 50,
    test_include_filter = ["IndyLambdaIrKotlinSteppingTestGenerated"],
)

# 369 tests - 95 ART tests expected to fail
debugger_test(
    name = "stepping-k2-k2",
    expected_to_fail_art = "stepping-k2-k2-art.txt",
    expected_to_fail_jvm = "stepping-k2-k2-jvm.txt",
    module = "kotlin.jvm-debugger.test.k2",
    shard_count = 50,
    test_exclude_filter = [
        "testSoSuspendableCallInEndOfFun",
    ],
    test_include_filter = ["K2IdeK2CodeKotlinSteppingTestGenerated\\$"],
)

# 369 tests - 80 ART tests expected to fail
debugger_test(
    name = "stepping-k2-k1",
    expected_to_fail_art = "stepping-k2-k1-art.txt",
    expected_to_fail_jvm = "stepping-k2-k1-jvm.txt",
    module = "kotlin.jvm-debugger.test.k2",
    shard_count = 50,
    test_include_filter = ["K2IdeK1CodeKotlinSteppingTestGenerated\\$"],
)

# 369 tests - 98 ART tests expected to fail
debugger_test(
    name = "stepping-k2-indy",
    expected_to_fail_art = "stepping-k2-indy-art.txt",
    expected_to_fail_jvm = "stepping-k2-indy-jvm.txt",
    module = "kotlin.jvm-debugger.test.k2",
    shard_count = 50,
    test_exclude_filter = [
        "testSoSuspendableCallInEndOfFun",
    ],
    test_include_filter = ["K2IndyLambdaKotlinSteppingTestGenerated\\$"],
)

# 444 tests - 97 ART tests expected to fail
debugger_test(
    name = "evaluate-expression-k1-k2",
    expected_to_fail_art = "evaluate-expression-k1-k2-art.txt",
    expected_to_fail_jvm = "evaluate-expression-k1-k2-jvm.txt",
    shard_count = 50,
    test_exclude_filter = [
        # Flaky
        "K1IdeK2CodeKotlinEvaluateExpressionTestGenerated.SingleBreakpoint.Coroutines.RunToCursor.testRunToCursorFromNonSuspendToSuspend",
        "K1IdeK2CodeKotlinEvaluateExpressionTestGenerated.SingleBreakpoint.Uncategorized.testAnnotationValue",
    ],
    test_include_filter = ["org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinEvaluateExpressionTestGenerated\\$"],
)

test_suite(
    name = "stepping-art",
    tests = [
        ":stepping-k1-indy-art",
        ":stepping-k1-ir-art",
        ":stepping-k1-k2-art",
        ":stepping-k2-indy-art",
        ":stepping-k2-k1-art",
        ":stepping-k2-k2-art",
    ],
)

test_suite(
    name = "stepping-jvm",
    tests = [
        ":stepping-k1-indy-jvm",
        ":stepping-k1-ir-jvm",
        ":stepping-k1-k2-jvm",
        ":stepping-k2-indy-jvm",
        ":stepping-k2-k1-jvm",
        ":stepping-k2-k2-jvm",
    ],
)

test_suite(
    name = "stepping",
    tests = [":stepping-jvm"],
)

test_suite(
    name = "evaluate-expression-art",
    tests = [
        ":evaluate-expression-k1-k2-art",
    ],
)

test_suite(
    name = "evaluate-expression-jvm",
    tests = [
        ":evaluate-expression-k1-k2-jvm",
    ],
)

test_suite(
    name = "evaluate-expression",
    tests = [
        ":evaluate-expression-art",
        ":evaluate-expression-jvm",
    ],
)

test_suite(
    name = "art",
    tests = [
        ":evaluate-expression-art",
        ":stepping-art",
    ],
)

test_suite(
    name = "jvm",
    tests = [
        ":evaluate-expression-jvm",
        ":stepping-jvm",
    ],
)

test_suite(
    name = "all",
    tests = [
        ":art",
        ":jvm",
    ],
)

# A convenience target for running a single test for debugging. To use this, replace the filter
# with whatever test desired.
debugger_test(
    name = "single-k1",
    art_tags = [],
    jvm_tags = [],
    test_include_filter = ["K1IdeK2CodeKotlinEvaluateExpressionTestGenerated.SingleBreakpoint.Coroutines.RunToCursor.testRunToCursorFromNonSuspendToSuspend"],
)

# A convenience target for running a single k2 test for debugging. To use this, replace the filter
# with whatever test desired.
debugger_test(
    name = "single-k2",
    art_tags = [],
    jvm_tags = [],
    module = "kotlin.jvm-debugger.test.k2",
    test_include_filter = [
        "K2IndyLambdaKotlinSteppingTestGenerated.StepOver.Uncategorized.testSoSuspendableCallInEndOfFun",
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
        "@intellij//:org.jetbrains.kotlin",
        "@maven//:junit.junit",
    ],
)
