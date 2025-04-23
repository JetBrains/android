load(":repository_cache.bzl", "repository_cache")

package(default_visibility = ["//visibility:public"])

# A target that prepares an archive for the bazel --repository-cache dir.
repository_cache(
    name = "repository_cache",
    srcs = ["@aswb_test_deps//:artifacts"],
    visibility = ["//visibility:public"],
)
