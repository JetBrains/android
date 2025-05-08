load("@rules_kotlin//kotlin:jvm.bzl", "kt_jvm_library")

package(default_visibility = ["//visibility:private"])

kt_jvm_library(
    name = "lib2_not_in_compile_jar_and_reexport_lib1",
    srcs = ["java/com/example/sample/Class1.kt"],
    visibility = ["//visibility:public"],
    exports = [
        "//libraries/deps/lib1",
    ],
    deps = [
        "//libraries/deps/lib1",
        "//libraries/deps/lib2",
    ],
)

kt_jvm_library(
    name = "no_duplicate_jars",
    srcs = ["java/com/example/sample/Class2.kt"],
    visibility = ["//visibility:public"],
    deps = [
        "//libraries/deps/lib1:reexported_lib1",
    ],
)
