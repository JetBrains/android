load("//tools/adt/idea/jps-build:idea.bzl", "jps_library", "jps_test")

jps_library(
    name = "kotlin.jvm-debugger.test_lib",
    download_cache = "prebuilts/tools/jps-build-caches/kotlin.jvm-debugger.test_lib",
    module = "kotlin.jvm-debugger.test",
    target_compatible_with = ["@platforms//:incompatible"],
    deps = [
        "//prebuilts/tools/jps-build-caches:kotlin.jvm-debugger.test_lib",
        "//tools/idea:idea_source",
    ],
)

jps_test(
    name = "kotlin.jvm-debugger.test_tests",
    size = "large",
    download_cache = "prebuilts/tools/jps-build-caches/kotlin.jvm-debugger.test_tests",
    module = "kotlin.jvm-debugger.test",
    target_compatible_with = ["@platforms//:incompatible"],
    test_suite = "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated$StepInto",
    deps = [
        ":kotlin.jvm-debugger.test_lib",
        "//prebuilts/tools/jps-build-caches:kotlin.jvm-debugger.test_lib",
        "//prebuilts/tools/jps-build-caches:kotlin.jvm-debugger.test_tests",
        "//tools/idea:idea_source",
    ],
)
