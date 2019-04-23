load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.layout-inspector",
    srcs = ["src"],
    iml_files = ["intellij.android.layout-inspector.iml"],
    test_coverage = True,
    test_data = [
        "//tools/adt/idea/layout-inspector/testData",
        "//tools/idea:platform-build-info",
    ],
    test_resources = ["testData"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/property-editor:intellij.android.property-editor[module]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/idea/.idea/libraries:studio-profiler-grpc-1.0-jarjar",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/adt/idea/android-transport:intellij.android.transport[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/idea/xml/xml-psi-api:intellij.xml.psi[module]",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/adt/idea/deploy:intellij.android.deploy[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/base/ddmlib:studio.android.sdktools.ddmlib[module]",
        "//tools/adt/idea/android-adb:intellij.android.adb[module]",
    ],
)
