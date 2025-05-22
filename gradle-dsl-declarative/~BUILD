load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gradle.dsl.declarative",
    # do not sort: must match IML order
    srcs = ["src"],
    iml_files = ["intellij.android.gradle.dsl.declarative.iml"],
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/adt/idea/gradle-declarative-lang-ide:intellij.android.gradle.declarative.lang.ide"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl",
        "//tools/adt/idea/gradle-declarative-lang:intellij.android.gradle.declarative.lang",
    ],
)
