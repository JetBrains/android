load("@bazel_skylib//rules:build_test.bzl", "build_test")
load("//tools/adt/idea/aswb:build-visibility.bzl", "DEFAULT_TEST_VISIBILITY")

package(
    default_visibility = DEFAULT_TEST_VISIBILITY,
)

build_test(
    name = "test_projects_test",
    targets = [
        "//tools/adt/idea/aswb/aswb/testdata/projects/autovalue:test_data_build",
    ],
)
