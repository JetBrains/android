load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

package(default_visibility = ["//visibility:private"])

java_library(
    name = "sample",
    srcs = glob(["java/com/example/sample/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        ":lib",
        "//java_and_deps/deps/top_level_lib_1:reexported_top_level_lib_1",
        "//java_and_deps/project/java/com/example/sample/nested",
    ],
)

java_library(
    name = "sample_no_ide",
    srcs = glob(["java/com/example/sample/*.java"]),
    tags = ["no-ide"],
    visibility = ["//visibility:public"],
    deps = [
        ":lib",
        "//java_and_deps/deps/no_ide",
        "//java_and_deps/deps/top_level_lib_1",
        "//java_and_deps/project/java/com/example/sample/nested",
    ],
)

java_library(
    name = "lib",
    srcs = glob(["java/com/example/lib/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java_and_deps/deps/top_level_lib_2",
    ],
)

java_library(
    name = "dep_on_no_ide",
    srcs = glob(["java/com/example/dep_on_no_ide/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java_and_deps/deps/no_ide",
        "//java_and_deps/deps/top_level_lib_1",
    ],
)

java_library(
    name = "jar_of_interface_and_implementation",
    srcs = glob(["java/com/example/jar_of_interface_and_implementation/*.java"]),
    visibility = ["//visibility:public"],
    deps = [
        "//java_and_deps/deps/interface_and_implementation/java/com/example/interface_and_implementation:libchild",
        "//java_and_deps/deps/interface_and_implementation/java/com/example/interface_and_implementation:libchildimpl",
    ],
)
