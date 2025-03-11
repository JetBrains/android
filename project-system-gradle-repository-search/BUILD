load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.gradle.repositorySearch",
    srcs = ["src"],
    iml_files = ["intellij.android.projectSystem.gradle.repositorySearch.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/adt/idea/android:intellij.android.core",
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/gmaven:intellij.android.gmaven",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.gradle.repositorySearch.tests",
    iml_files = ["intellij.android.projectSystem.gradle.repositorySearch.tests.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/project-system-gradle-repository-search:intellij.android.projectSystem.gradle.repositorySearch",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "@intellij//:com.intellij.gradle",
    ],
)
