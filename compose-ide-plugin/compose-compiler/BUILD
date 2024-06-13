load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# This is a custom build of the Compose compiler to be bundled in Studio. At runtime it will link
# against the Kotlin IDE plugin and its bundled Kotlin compiler. Note that Kotlinc expects compiler
# plugins to be packaged inside a jar, hence this artifact is required for Studio dev builds.
kotlin_library(
    name = "compose-compiler",
    srcs = ["@compose-compiler-sources//:sources"],
    coverage_baseline_enabled = False,
    jar = "compose-compiler.jar",
    kotlinc_opts = ["-Xjvm-default=all"],  # To support @JvmDefaultWithCompatibility annotations.
    lint_enabled = False,
    resource_strip_prefix = "../compose-compiler-sources",  # Unsure why there is '..' prefix, but it works.
    resources = ["@compose-compiler-sources//:resources"],
    visibility = ["//visibility:public"],
    deps = [
        "@intellij//:com.intellij.java",
        "@intellij//:intellij-sdk",
        "@intellij//:org.jetbrains.kotlin",
    ],
)
