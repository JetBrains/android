load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.testFramework",
    iml_files = ["intellij.android.testFramework.iml"],
    test_runtime_deps = ["//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/xml/xml-psi-api:intellij.xml.psi[module, test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:jsr305[test]",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/plugins/junit:intellij.junit[module]",
        "//tools/idea/java/execution/impl:intellij.java.execution.impl[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
    ],
)
