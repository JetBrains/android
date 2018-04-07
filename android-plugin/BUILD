load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.android.plugin",
    srcs = ["src"],
    iml_files = ["intellij.android.plugin.iml"],
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
        "//tools/vendor/intellij/cidr:intellij.c.doxygen",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android:intellij.android[module]",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates[module]",
        "//tools/adt/idea/designer:intellij.android.designer[module]",
        "//tools/adt/idea/profilers-android:intellij.android.profilersAndroid[module]",
        "//tools/adt/idea/android-adb[module]",
        "//tools/adt/idea/android-debuggers[module]",
        "//tools/adt/idea/android-lang[module]",
        "//tools/adt/idea/project-system[module]",
        "//tools/adt/idea/project-system-gradle[module]",
        "//tools/adt/idea/android-layout-inspector[module]",
        "//tools/adt/idea/assistant[module]",
        "//tools/adt/idea/connection-assistant[module]",
        "//tools/adt/idea/whats-new-assistant[module]",
    ],
)
