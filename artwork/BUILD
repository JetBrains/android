load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.artwork",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    bundle_data = [":device-art-resources"],
    iml_files = ["intellij.android.artwork.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    # do not sort: must match IML order
    resources = [
        "resources",
        "compatibilityResources",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = ["//tools/idea/platform/util:intellij.platform.util[module]"],
)

filegroup(
    name = "device-art-resources",
    srcs = glob(["resources/device-art-resources/**"]),
    visibility = ["//visibility:public"],
)
