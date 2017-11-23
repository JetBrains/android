load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-plugin",
    srcs = ["src"],
    iml_files = ["android-plugin.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea/plugins/maven:maven2-server-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android[module]",
        "//tools/adt/idea/android-adb[module]",
        "//tools/adt/idea/android-debuggers[module]",
        "//tools/adt/idea/android-lang[module]",
        "//tools/adt/idea/project-system[module]",
        "//tools/adt/idea/project-system-gradle[module]",
        "//tools/adt/idea/sdk-updates[module]",
        "//tools/adt/idea/designer[module]",
        "//tools/adt/idea/profilers-android[module]",
    ],
)
