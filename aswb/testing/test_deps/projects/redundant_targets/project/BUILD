load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

package(default_visibility = ["//visibility:private"])

java_library(
    name = "sample",
    srcs = glob(["java/com/example/sample/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":lib",
        "//redundant_targets/deps/top_level_lib_1",
    ],
)

java_library(
    name = "sample_redundant",
    visibility = ["//visibility:public"],
    exports = [
        ":lib",
        "//redundant_targets/deps/no_ide",
        "//redundant_targets/deps/top_level_lib_1",
    ],
)

java_library(
    name = "lib",
    srcs = glob(["java/com/example/lib/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//redundant_targets/deps/top_level_lib_2",
    ],
)
