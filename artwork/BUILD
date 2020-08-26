load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/adt/idea/studio:studio.bzl", "studio_data")

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
    deps = ["//tools/idea/platform/util:intellij.platform.util.ui[module]"],
)

filegroup(
    name = "device-art-resources",
    srcs = glob(["resources/device-art-resources/**"]),
    visibility = ["//visibility:public"],
)

studio_data(
    name = "device-art-resources-bundle",
    files = glob(["resources/device-art-resources/**"]),
    mappings = {
        "tools/adt/idea/artwork/resources/": "",
    },
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build [unb]
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
    project = "unb",
    # do not sort: must match IML order
    resources = [
        "resources",
        "compatibilityResources",
    ],
    visibility = ["//visibility:public"],
    deps = ["//prebuilts/studio/intellij-sdk:studio-sdk"],
)
