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

# 9 tests
debugger_test(
    name = "k1k2-kotlin-stepping-step-out",
    expected_to_fail_art = "art_k1k2_kotlin_stepping_expected_failures_step_out.txt",
    test_include_filter = ["org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated.StepOut"],
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
    deps = [
        ":kotlin.jvm-debugger.test_lib_import",
        "@maven//:junit.junit",
    ],
)
