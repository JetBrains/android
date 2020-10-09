load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.testRetention",
    srcs = ["src"],
    iml_files = ["intellij.android.testRetention.iml"],
    project = "unb",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module]",
        "//tools/adt/idea/emulator:intellij.android.emulator[module]",
        "//tools/adt/idea/.idea/libraries:unb.emulator-proto",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//tools/adt/idea/.idea/libraries:unb.studio-grpc",
        "//tools/adt/idea/.idea/libraries:unb.studio-proto",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.testRetention.tests",
    iml_files = ["intellij.android.testRetention.tests.iml"],
    project = "unb",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/emulator:intellij.android.emulator[module, test]",
        "//tools/adt/idea/emulator:intellij.android.emulator.tests[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/adt/idea/android-test-retention:intellij.android.testRetention[module, test]",
        "//tools/adt/idea/.idea/libraries:unb.truth[test]",
    ],
)
