load("//tools/adt/idea/aswb:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")
load("//tools/adt/idea/aswb/testing:test_defs.bzl", "aswb_test")

package(default_visibility = DEFAULT_TEST_VISIBILITY)

aswb_test(
    name = "DirectoryTraverserTest",
    srcs = ["DirectoryTraverserTest.kt"],
    test_class = "com.google.idea.blaze.traverser.DirectoryTraverserTest",
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/traverser",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
    ],
)
