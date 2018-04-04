load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "intellij.android.plugin",
    srcs = ["src"],
    iml_files = ["intellij.android.plugin.iml"],
    tags = ["managed"],
    test_runtime_deps = ["//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea:intellij.platform.jps.build_and_others[module]",
        "//tools/idea/xml/impl:intellij.xml.impl[module]",
    ],
)
