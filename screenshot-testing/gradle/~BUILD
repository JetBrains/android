load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.screenshot-test.gradle",
    srcs = ["src"],
    iml_files = ["intellij.android.screenshot-test.gradle.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/screenshot-testing:intellij.android.screenshot-test",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/android:intellij.android.core",
        "@intellij//:org.jetbrains.plugins.gradle",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/android-lang:intellij.android.lang",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.idea.common",
        "//tools/adt/idea/testartifacts:intellij.android.testartifacts",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
    ],
)
