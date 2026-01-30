load("//tools/adt/idea/aswb/testing:test_defs.bzl", "aswb_test")

package(
    default_visibility = [":visibility"],
)

package_group(
    name = "visibility",
    packages = [
        "//tools/adt/idea/aswb/shared",
    ],
)

aswb_test(
    name = "VcsStateTest",
    size = "small",
    srcs = ["VcsStateTest.java"],
    test_class = "com.google.idea.blaze.common.vcs.VcsStateTest",
    deps = [
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common/vcs",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api/maven:guava",
    ],
)
