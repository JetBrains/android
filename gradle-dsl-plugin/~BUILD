load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gradle.dsl.plugin",
    iml_files = ["intellij.android.gradle.dsl.plugin.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:org.intellij.groovy",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl",
        "//tools/adt/idea/gradle-dsl-declarative:intellij.android.gradle.dsl.declarative",
        "//tools/adt/idea/gradle-dsl-flags:intellij.android.gradle.dsl.flags",
        "//tools/adt/idea/gradle-dsl-groovy:intellij.android.gradle.dsl.groovy",
        "//tools/adt/idea/gradle-dsl-kotlin:intellij.android.gradle.dsl.kotlin",
        "//tools/adt/idea/gradle-dsl-toml:intellij.android.gradle.dsl.toml",
    ],
)
