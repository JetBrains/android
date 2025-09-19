load("//tools/adt/idea/aswb/build_defs:build_defs.bzl", "aswb_library")
load("//tools/base/bazel:proto.bzl", "java_proto_library")

package(default_visibility = [
    "//visibility:public",
])

aswb_library(
    name = "testutil",
    testonly = 1,
    srcs = glob([
        "*.java",
        "*.kt",
    ]),
    deps = [
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
    ],
)
