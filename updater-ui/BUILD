load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.android.updater.ui",
    srcs = ["src"],
    iml_files = ["intellij.android.updater.ui.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/updater:intellij.platform.updater[module]",
        "//tools/base/repository:studio.android.sdktools.repository[module]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/.idea/libraries:jna",
        "//tools/adt/idea/android:intellij.android.core[module]",
    ],
)
