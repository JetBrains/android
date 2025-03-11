load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.core.diagnostics",
    iml_files = ["intellij.android.core.diagnostics.iml"],
    test_class = "com.android.tools.idea.IdeaTestSuite",
    # keep sorted
    test_data = [
        "//tools/adt/idea/android/src/com/android/tools/idea/diagnostics/heap/native:libjni_object_tagger.prebuilt",
        "//tools/adt/idea/android/testData",
    ],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android:intellij.android.core.tests",
        "//tools/adt/idea/.idea/libraries:kotlin-test",
        "//tools/analytics-library/shared:analytics-shared",
        "//tools/analytics-library/testing:android.sdktools.analytics-testing",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/analytics-library/crash:analytics-crash",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/server-flags:intellij.android.server-flags",
        "@intellij//:org.jetbrains.kotlin",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
        "//tools/base/flags:studio.android.sdktools.flags",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
    ],
    test_friends = ["//tools/adt/idea/android:intellij.android.core"],
    test_jvm_flags = [
        "-Djdk.attach.allowAttachSelf=true",
        "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED",
    ],
    test_srcs = ["testSrc"],
    test_tags = [
        "block-network",
    ],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/android/diagnostics:libandroid-core-proto",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "libandroid-core-proto",
    jars = ["//tools/adt/idea/android:libandroid-core-proto.jar"],
    visibility = ["//visibility:public"],
)
