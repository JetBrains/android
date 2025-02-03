load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gradle.declarative.lang",
    # do not sort: must match IML order
    srcs = [
        "gen",
        "src",
    ],
    iml_files = ["intellij.android.gradle.declarative.lang.iml"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:com.intellij.java",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gradle.declarative.lang.tests",
    iml_files = ["intellij.android.gradle.declarative.lang.tests.iml"],
    test_data = glob(["testData/**"]),
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    runtime_deps = ["//tools/adt/idea/gradle-declarative-lang-ide:intellij.android.gradle.declarative.lang.ide"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/gradle-declarative-lang:intellij.android.gradle.declarative.lang[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/lint:intellij.lint[module, test]",
        "//tools/base/lint:studio.android.sdktools.lint-api[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin[test]",
    ],
)

kotlin_library(
    name = "tools.dcl-lang",
    srcs = glob([
        "src/**/*.java",
        "src/**/*.kt",
        "gen/**/*.java",
    ]),
    module_name = "dcl-lang",
    resources = glob(["resources/**"]),
    visibility = ["//tools/base/lint/cli:__pkg__"],
    deps = [
        "//prebuilts/tools/common/lint-psi/intellij-core",
        "//prebuilts/tools/common/lint-psi/kotlin-compiler",
        "//prebuilts/tools/common/lint-psi/uast",
    ],
)
