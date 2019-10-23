load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.naveditor",
    srcs = ["src"],
    iml_files = ["intellij.android.naveditor.iml"],
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
        "//tools/adt/idea/designer:intellij.android.designer[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/adt/idea/property-editor:intellij.android.property-editor[module]",
        "//tools/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/idea/java/openapi:intellij.java[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//prebuilts/tools/common/m2/repository/org/eclipse/elk/org.eclipse.elk.alg.layered/0.3.0:jar",
        "//prebuilts/tools/common/m2/repository/org/eclipse/elk/org.eclipse.elk.core/0.3.0:jar",
        "//prebuilts/tools/common/m2/repository/org/eclipse/elk/org.eclipse.elk.graph/0.3.0:jar",
        "//prebuilts/tools/common/m2/repository/org/eclipse/emf/org.eclipse.emf.common/2.12.0:jar",
        "//prebuilts/tools/common/m2/repository/org/eclipse/emf/org.eclipse.emf.ecore/2.12.0:jar",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl[module, test]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.naveditor.tests",
    iml_files = ["intellij.android.naveditor.tests.iml"],
    tags = ["no_test_windows"],
    test_class = "com.android.tools.idea.naveditor.NavEditorTestSuite",
    test_coverage = True,
    # keep sorted
    test_data = [
        "//prebuilts/studio/layoutlib:buildprop",
        "//prebuilts/studio/layoutlib:data/fonts",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/designer/testData",
        "//tools/adt/idea/resources-aar:framework_res",
        "//tools/adt/idea/android:test_deps.zip",
        "//tools/idea:platform-build-info",
        "//prebuilts/studio/sdk:platforms/latest",
    ] + glob(["testData/**"]),
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
    ],
    test_srcs = ["testSrc"],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-test[test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
        "//tools/adt/idea/designer:intellij.android.designer.tests[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/analytics-library/shared:analytics-shared[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl[module, test]",
        "//tools/adt/idea/naveditor:intellij.android.naveditor[module, test]",
        "//tools/adt/idea/designer:intellij.android.designer[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/idea/.idea/libraries:studio-analytics-proto[test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/adt/idea/property-editor:intellij.android.property-editor[module, test]",
        "//tools/idea/.idea/libraries:protobuf[test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
    ],
)
