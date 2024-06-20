load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gradle-tooling.impl",
    srcs = ["src"],
    iml_files = ["intellij.android.gradle-tooling.impl.iml"],
    jvm_target = "8",
    resources = ["resources"],
    tags = ["noci:studio-win"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
        "//tools/adt/idea/.idea/libraries:javax-inject",
        "//tools/adt/idea/gradle-tooling/studio-gradle-tooling-api:intellij.android.gradle-tooling.api[module]",
        "//tools/adt/idea/.idea/libraries:equalsverifier[test]",
    ],
)
