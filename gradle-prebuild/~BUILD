load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "gradle_project_build_generator_lib",
    testonly = True,
    srcs = ["src/com/android/tools/idea/ProjectBuildGenerator.java"],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/adt/idea/as-driver:as-driver.utils_testlib",
        "//tools/base/common:studio.android.sdktools.common",
    ],
)
