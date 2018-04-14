load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "uitest-framework-gradle",
    srcs = ["src"],
    iml_files = ["uitest-framework-gradle.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
        "//tools/vendor/intellij/cidr:intellij.c.doxygen",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/plugins/gradle:intellij.gradle[module, test]",
        "//tools/adt/idea/uitest-framework:intellij.android.guiTestFramework[module, test]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/swing-testing/fest-swing[module, test]",
    ],
)
