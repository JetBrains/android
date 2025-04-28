load("@bazel_skylib//rules:build_test.bzl", "build_test")

build_test(
    name = "bzl_tests",
    targets = [":BUILD"],  # Building a source file succeeds.
)

build_test(
    name = "bzl_tests_failing",
    targets = [":target"],
)

genrule(
    name = "failing_target",
    outs = ["out.txt"],
    cmd = "echo 'This is error' >&2 ; exit 1",
)
