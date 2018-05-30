load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.designer",
    srcs = ["src"],
    iml_files = ["intellij.android.designer.iml"],
    javacopts = ["-XepAllErrorsAsWarnings"],
    resources = ["resources"],
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
        "//tools/idea/platform/editor-ui-api:intellij.platform.editor[module]",
        "//tools/idea/platform/core-api:intellij.platform.core[module]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/idea/xml/xml-psi-api:intellij.xml.psi[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/base/pixelprobe:studio.android.sdktools.pixelprobe[module]",
        "//tools/idea/images:intellij.platform.images[module]",
        "//tools/base/annotations:studio.android.sdktools.android-annotations[module]",
        "//tools/idea/.idea/libraries:jsr305",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/.idea/libraries:layoutlib",
        "//tools/idea/xml/xml-psi-impl:intellij.xml.psi.impl[module]",
        "//tools/idea/java/java-psi-impl:intellij.java.psi.impl[module]",
        "//tools/idea/java/java-indexing-api:intellij.java.indexing[module]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module]",
        "//tools/analytics-library/tracker:analytics-tracker[module]",
        "//tools/analytics-library/shared:analytics-shared[module]",
        "//tools/adt/idea/wizard:intellij.android.wizard[module]",
        "//tools/adt/idea/wizard-model:intellij.android.wizard.model[module]",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/adt/idea/project-system[module]",
        "//tools/adt/idea/project-system-gradle[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jre8",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module]",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/designer:lib/org.eclipse.elk.alg.layered-0.3.0",
        "//tools/adt/idea/designer:lib/org.eclipse.elk.core-0.3.0",
        "//tools/adt/idea/designer:lib/org.eclipse.elk.graph-0.3.0",
        "//tools/adt/idea/designer:lib/org.eclipse.emf.common-2.12.0",
        "//tools/adt/idea/designer:lib/org.eclipse.emf.ecore-2.12.0",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.designer.tests",
    iml_files = ["intellij.android.designer.tests.iml"],
    test_class = "com.android.tools.idea.DesignerTestSuite",
    test_data = [
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/layoutlib:buildprop",
        "//prebuilts/studio/layoutlib:data/fonts",
        "//prebuilts/studio/layoutlib:data/res",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:constraint-layout_latest",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/studio/sdk:support_latest",
        "//prebuilts/tools/common/kotlin-plugin:Kotlin/kotlinc/build.txt",
        "//prebuilts/tools/common/offline-m2:android-gradle-2.2.0",
        "//tools/adt/idea/android:test_deps.zip",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/lib:androidWidgets",
        "//tools/adt/idea/android/lib:sampleData",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/designer/testData",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:studio_repo.zip",
        "//tools/base/templates",
        "//tools/idea/java/jdkAnnotations",
        "//tools/idea/java/mockJDK-1.7",
    ],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
    ],
    test_shard_count = 8,
    test_srcs = ["testSrc"],
    test_tags = [
        "slow",
        "cpu:3",
        "block-network",
        "no_test_mac",  # b/72973198
    ],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/adt/idea/designer:intellij.android.designer[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/idea/xml/xml-psi-api:intellij.xml.psi[module, test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module, test]",
        "//tools/idea/.idea/libraries:layoutlib[test]",
        "//tools/idea/xml/xml-psi-impl:intellij.xml.psi.impl[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/adt/idea/adt-ui:intellij.android.adt.ui[module, test]",
        "//tools/base/pixelprobe:studio.android.sdktools.pixelprobe[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/analytics-library/tracker:analytics-tracker[module, test]",
        "//tools/idea/xml/dom-openapi:intellij.xml.dom[module, test]",
        "//tools/analytics-library/shared:analytics-shared[module, test]",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding[module, test]",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates[module, test]",
        "//tools/base/layoutinspector:studio.android.sdktools.layoutinspector[module, test]",
        "//tools/idea/.idea/libraries:Gradle[test]",
        "//tools/base/flags:studio.android.sdktools.flags[module, test]",
        "//tools/adt/idea/artwork:intellij.android.artwork[module, test]",
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/project-system[module, test]",
        "//tools/adt/idea/project-system:project-system-tests[module, test]",
        "//tools/adt/idea/adt-testutils[module, test]",
        "//tools/adt/idea/project-system-gradle[module, test]",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl[module, test]",
        "//tools/adt/idea/adt-ui-model:intellij.android.adt.ui.model[module, test]",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/idea/.idea/libraries:studio-analytics-proto",
    ],
)

# managed by go/iml_to_build
java_import(
    name = "lib/org.eclipse.elk.alg.layered-0.3.0",
    jars = ["lib/org.eclipse.elk.alg.layered-0.3.0.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "lib/org.eclipse.elk.core-0.3.0",
    jars = ["lib/org.eclipse.elk.core-0.3.0.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "lib/org.eclipse.elk.graph-0.3.0",
    jars = ["lib/org.eclipse.elk.graph-0.3.0.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "lib/org.eclipse.emf.common-2.12.0",
    jars = ["lib/org.eclipse.emf.common-2.12.0.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
java_import(
    name = "lib/org.eclipse.emf.ecore-2.12.0",
    jars = ["lib/org.eclipse.emf.ecore-2.12.0.jar"],
    visibility = ["//visibility:public"],
)
