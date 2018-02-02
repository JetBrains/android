load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-plugin",
    srcs = ["src"],
    iml_files = ["android-plugin.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/adt/idea/adt-branding",
        "//tools/vendor/intellij/cidr:doxygen",
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
