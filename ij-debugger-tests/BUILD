load("//tools/adt/idea/jps-build:idea.bzl", "jps_library")
load(":ij-debugger-tests.bzl", "art_debugger_test", "jvm_debugger_test")

jps_library(
    name = "test_lib",
    download_cache = "prebuilts/tools/jps-build-caches/kotlin.jvm-debugger.test_lib",
    module = "kotlin.jvm-debugger.test",
    target_compatible_with = ["@platforms//:incompatible"],
    deps = [
        "//prebuilts/tools/jps-build-caches:kotlin.jvm-debugger.test_lib",
        "//tools/idea:idea_source",
    ],
)

jvm_debugger_test(
    name = "jvm-step-into",
    suite = "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated$StepInto",
    target_compatible_with = ["@platforms//:incompatible"],
)

art_debugger_test(
    name = "art-step-into",
    suite = "org.jetbrains.kotlin.idea.debugger.test.K1IdeK2CodeKotlinSteppingTestGenerated$StepInto",
    target_compatible_with = ["@platforms//:incompatible"],
)
