load(":repository_cache.bzl", "repository_cache")
load(":test_deps_bazel_artifacts.bzl", "ASWB_TEST_DEP_LABELS")

exports_files(["test_deps_bazel_artifacts.bzl"])

filegroup(
    name = "repos",
    srcs = ASWB_TEST_DEP_LABELS,
)

# The Bazel Central Registry.
alias(
    name = "bazel_central_registry",
    actual = "@aswb_test_deps_bazel_central_registry//file",
    visibility = ["//visibility:public"],
)

# A target that prepares an archive for the bazel --repository-cache dir.
repository_cache(
    name = "repository_cache",
    srcs = [":repos"],
    visibility = ["//visibility:public"],
)
