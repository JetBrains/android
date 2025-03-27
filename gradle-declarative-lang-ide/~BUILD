load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gradle.declarative.lang.ide",
    # do not sort: must match IML order
    srcs = ["src"],
    iml_files = ["intellij.android.gradle.declarative.lang.ide.iml"],
    resources = ["resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/gradle-declarative-lang:intellij.android.gradle.declarative.lang[module]",
        "//tools/adt/idea/gradle-declarative-lang-sync:intellij.android.gradle.declarative.lang.sync[module]",
        "@intellij//:org.jetbrains.kotlin",
        "@intellij//:com.intellij.java",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.gradle.declarative.lang.ide.tests",
    iml_files = ["intellij.android.gradle.declarative.lang.ide.tests.iml"],
    test_data = glob(["testData/**"]),
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/.idea/libraries:junit4[test]",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/gradle-declarative-lang-ide:intellij.android.gradle.declarative.lang.ide[module, test]",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/gradle-declarative-lang:intellij.android.gradle.declarative.lang[module, test]",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/gradle-declarative-lang-sync:intellij.android.gradle.declarative.lang.sync[module, test]",
    ],
)
