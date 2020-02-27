load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:diff.bzl", "apply_diff")
load("//tools/base/bazel:gradle.bzl", "gradle_build")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.gradle",
    srcs = ["src"],
    iml_files = ["intellij.android.projectSystem.gradle.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/plugins/java-decompiler/plugin:intellij.java.decompiler",
        "//tools/idea/jvm/jvm-analysis-java-tests:intellij.jvm.analysis.java.tests",
        "//tools/idea/uast/uast-tests:intellij.platform.uast.tests",
        "//tools/idea/java/manifest:intellij.java.manifest",
        "//tools/idea/java/remote-servers/impl:intellij.java.remoteServers.impl",
        "//tools/idea:intellij.java.ui.tests",
        "//tools/idea/plugins/ui-designer:intellij.java.guiForms.designer",
        "//tools/idea/java/plugin:intellij.java.plugin",
        "//tools/idea/plugins/ui-designer-core:intellij.uiDesigner",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/.idea/libraries:javassist",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/.idea/libraries:error-prone-annotations",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
        "//tools/idea/jvm/jvm-analysis-impl:intellij.jvm.analysis.impl",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//prebuilts/tools/common/m2/repository/com/jetbrains/intellij/documentation/tips-intellij-idea-community/193.4:jar",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/.idea/libraries:precompiled_jshell-frontend",
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module]",
        "//tools/adt/idea/android:intellij.android.core[module]",
        "//tools/adt/idea/android-common:intellij.android.common[module]",
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common[module]",
        "//tools/idea/.idea/libraries:protobuf",
        "//tools/idea/.idea/libraries:studio-analytics-proto",
        "//tools/base/flags:studio.android.sdktools.flags[module]",
        "//tools/adt/idea/android-kotlin:intellij.android.kotlin.extensions[module]",
        "//tools/idea/xml/impl:intellij.xml.impl[module]",
        "//tools/idea/plugins/gradle/tooling-extension-api:intellij.gradle.toolingExtension[module]",
        "//tools/idea/platform/external-system-api:intellij.platform.externalSystem[module]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module]",
    ],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.projectSystem.gradle.tests",
    iml_files = ["intellij.android.projectSystem.gradle.tests.iml"],
    tags = ["no_test_windows"],
    test_class = "com.android.tools.idea.projectsystem.GradleProjectSystemTestSuite",
    test_data = [
        "//tools/base/build-system/previous-versions:2.2.0.zip",
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:platform-tools",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/tools/common/kotlin-plugin:Kotlin/kotlinc/build.txt",
        "//tools/adt/idea/android:test_deps.zip",
        "//tools/adt/idea/android/annotations",
        "//tools/adt/idea/android/testData",
        "//tools/adt/idea/designer/testData",
        "//tools/adt/idea/project-system-gradle/testData",
        "//tools/base/build-system:gradle-distrib",
        "//tools/base/build-system:studio_repo.zip",
        "//tools/base/third_party/kotlin:kotlin-m2repository.zip",
        "//tools/idea/java/mockJDK-1.7",
    ],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/.idea/libraries:javassist",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
        "//tools/idea/.idea/libraries:error-prone-annotations",
        "//tools/adt/idea/adt-branding:intellij.android.adt.branding",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin:intellij.java.langInjection.jps",
        "//tools/idea/plugins/java-decompiler/plugin:intellij.java.decompiler",
        "//tools/idea/jvm/jvm-analysis-java-tests:intellij.jvm.analysis.java.tests",
        "//tools/idea/uast/uast-tests:intellij.platform.uast.tests",
        "//tools/idea/java/manifest:intellij.java.manifest",
        "//tools/idea/java/remote-servers/impl:intellij.java.remoteServers.impl",
        "//tools/idea:intellij.java.ui.tests",
        "//tools/idea/plugins/ui-designer:intellij.java.guiForms.designer",
        "//tools/idea/java/plugin:intellij.java.plugin",
        "//tools/idea/java/compiler/instrumentation-util-8:intellij.java.compiler.instrumentationUtil.java8",
        "//tools/idea/java/ide-resources:intellij.java.ide.resources",
        "//tools/idea/plugins/ui-designer-core:intellij.uiDesigner",
        "//prebuilts/tools/common/m2/repository/com/jetbrains/intellij/documentation/tips-intellij-idea-community/193.4:jar",
        "//tools/idea/.idea/libraries:precompiled_jshell-frontend",
        "//tools/idea/plugins/maven/error-prone-compiler:intellij.maven.errorProne.compiler",
    ],
    test_shard_count = 2,
    test_srcs = ["testSrc"],
    test_tags = [
        "cpu:3",
        "block-network",
        "no_test_mac",
    ],
    test_timeout = "long",
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/adt/idea/android-common:intellij.android.common[module, test]",
        "//tools/adt/idea/android:intellij.android.core[module, test]",
        "//tools/adt/idea/project-system:intellij.android.projectSystem[module, test]",
        "//tools/adt/idea/project-system-gradle:intellij.android.projectSystem.gradle[module, test]",
        "//tools/adt/idea/android-test-framework:intellij.android.testFramework[module, test]",
        "//tools/idea:intellij.platform.jps.build_and_others[module, test]",
        "//tools/base/testutils:studio.android.sdktools.testutils[module, test]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils[module, test]",
        "//tools/adt/idea/gradle-dsl:intellij.android.gradle.dsl[module, test]",
    ],
)

# Remove remote publishing from upstream jetbrains script
apply_diff(
    name = "fixup_build.gradle",
    diff_file = "build_gradle.diff",
    input_file = "//tools/idea/build/dependencies/gradle-api-deps:build.gradle",
    output_file = "build.gradle",
    tags = ["manual"],
)

# generate the maven repository
gradle_build(
    name = "idea_gradle_api",
    build_file = ":build.gradle",
    output_files = {
        "gradle-api-impldep.jar": "m2/org/jetbrains/intellij/deps/gradle-api-impldep/*/gradle-api-impldep-*.jar",
        "gradle-api.jar": "m2/org/jetbrains/intellij/deps/gradle-api/*/gradle-api-*.jar",
        "gradle-launcher.jar": "m2/org/jetbrains/intellij/deps/gradle-launcher/*/gradle-launcher-*.jar",
        "gradle-wrapper.jar": "m2/org/jetbrains/intellij/deps/gradle-wrapper/*/gradle-wrapper-*.jar",
    },
    tags = ["manual"],
    tasks = [":publishGradleApiToMavenLocal"],
)
