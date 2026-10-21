load("@rules_java//java:defs.bzl", "java_library")
load("//tools/adt/idea/aswb/build_defs:aswb_module.bzl", "aswb_module")

java_library(
    name = "plugin_api",
    visibility = ["//visibility:public"],
    exports = [
        "//prebuilts/tools/common/m2:jsr305-2.0.1",
        "//tools/adt/idea/.idea/libraries:libbinary-resources",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/adt/idea/android:intellij.android.core",
        "//tools/adt/idea/android-adb:intellij.android.adb",
        "//tools/adt/idea/android-common:intellij.android.common",
        "//tools/adt/idea/android-plugin:intellij.android.plugin",
        "//tools/adt/idea/app-inspection/inspector/api:app-inspection.inspector.api",
        "//tools/adt/idea/assistant",
        "//tools/adt/idea/deploy:intellij.android.deploy",
        "//tools/adt/idea/designer:intellij.android.designer",
        "//tools/adt/idea/execution/common:intellij.android.execution.common",
        "//tools/adt/idea/jps-model:intellij.android.jps.model",
        "//tools/adt/idea/layout-inspector:intellij.android.layout-inspector",
        "//tools/adt/idea/layoutlib:intellij.android.layoutlib",
        "//tools/adt/idea/layoutlib-loader:intellij.android.layoutlib-loader",
        "//tools/adt/idea/ml-api:intellij.android.ml-api",
        "//tools/adt/idea/nav/editor:intellij.android.nav.editor",
        "//tools/adt/idea/profilers-android:intellij.android.profilersAndroid",
        "//tools/adt/idea/project-system:intellij.android.projectSystem",
        "//tools/adt/idea/render-resources:intellij.android.render-resources",
        "//tools/adt/idea/rendering:intellij.android.rendering",
        "//tools/adt/idea/sdk-updates:intellij.android.sdkUpdates",
        "//tools/analytics-library/shared:analytics-shared",
        "//tools/analytics-library/tracker:analytics-tracker",
        "//tools/base/apkparser/analyzer:studio.android.sdktools.analyzer",
        "//tools/base/build-system/manifest-merger:studio.android.sdktools.manifest-merger",
        "//tools/base/deploy/deployer:studio.android.sdktools.deployer",
        "//tools/base/layoutlib-api:studio.android.sdktools.layoutlib-api",
        "//tools/base/lint:studio.android.sdktools.lint-api",
        "//tools/base/sdk-common:studio.android.sdktools.sdk-common",
        "//tools/studio/google/cloud/testing/test-recorder",
        "//tools/vendor/google/android-ndk",
        "//tools/vendor/google/android-ndk-api",
        "@intellij//:JUnit",
        "@intellij//:com.intellij.cidr.base",
        "@intellij//:com.intellij.cidr.debugger",
        "@intellij//:com.intellij.cidr.lang",
        "@intellij//:com.intellij.cidr.lang.clangd",
        "@intellij//:com.intellij.java",
        "@intellij//:intellij.c.core",
        "@intellij//:intellij.c.debugger",
        "@intellij//:intellij.cidr.clangd.core",
        "@intellij//:intellij.cidr.lang.base",
        "@intellij//:intellij.cidr.projectModel",
        "@intellij//:intellij.cidr.psi.base",
        "@intellij//:intellij.cidr.workspaceModel",
        "@intellij//:intellij.java",
        "@intellij//:intellij.java.analysis.impl",
        "@intellij//:intellij.java.compiler",
        "@intellij//:intellij.java.compiler.impl",
        "@intellij//:intellij.java.debugger",
        "@intellij//:intellij.java.debugger.impl",
        "@intellij//:intellij.java.execution",
        "@intellij//:intellij.java.execution.impl",
        "@intellij//:intellij.java.impl",
        "@intellij//:intellij.java.impl.inspections",
        "@intellij//:intellij.java.impl.refactorings",
        "@intellij//:intellij.java.indexing",
        "@intellij//:intellij.java.indexing.impl",
        "@intellij//:intellij.libraries.grpc",
        "@intellij//:intellij.platform.coverage",
        "@intellij//:intellij.platform.coverage.agent",
        "@intellij//:intellij.platform.tasks",
        "@intellij//:intellij.platform.tasks.impl",
        "@intellij//:intellij.platform.vcs.dvcs",
        "@intellij//:intellij.platform.vcs.dvcs.impl",
        "@intellij//:intellij.platform.vcs.dvcs.impl.shared",
        "@intellij//:intellij.platform.vcs.impl",
        "@intellij//:intellij.platform.vcs.impl.shared",
        "@intellij//:intellij.platform.vcs.log",
        "@intellij//:intellij.platform.vcs.log.impl",
        "@intellij//:intellij.spellchecker",
        "@intellij//:intellij-sdk",
        "@intellij//:org.jetbrains.kotlin",
        "@maven//:com.google.errorprone.error_prone_annotations",
    ],
)

aswb_module(
    name = "plugin_api_module",
    lib_deps = [
        "//prebuilts/tools/common/m2:jspecify-0.2.0",
        "//prebuilts/tools/common/m2:jsr305-2.0.1",
        "//tools/adt/idea/.idea/libraries:libbinary-resources",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/.idea/libraries:studio-grpc",
        "//tools/adt/idea/.idea/libraries:studio-proto",
        "//tools/base/annotations",
        "@maven//:com.google.auto.value.auto-value",
        "@maven//:com.google.auto.value.auto-value-annotations",
        "@maven//:com.google.errorprone.error_prone_annotations",
    ],
    scope_prefixes = ["//tools/adt/idea/aswb/plugin_api/"],  # Note: the same suffix in :google_internal_api_module (manually managed)
    visibility = ["//visibility:public"],
    deps = [
        ":hg4idea",
        ":jsr305",
        ":junit",
        ":plugin_api",
        ":terminal",
    ],
)

java_library(
    name = "test_libs",
    visibility = ["//visibility:public"],
    exports = [
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:truth-java8-extension",
        "@maven//:com.google.truth.truth",
    ],
)

java_library(
    name = "kotlin_for_tests",
    visibility = ["//visibility:public"],
    exports = [
        "@intellij//:org.jetbrains.kotlin",
    ],
)

java_library(
    name = "junit",
    visibility = ["//visibility:public"],
    exports = [
        "@intellij//:JUnit",
    ],
)

java_library(
    name = "terminal",
    visibility = ["//visibility:public"],
    exports = [
        "@intellij//:org.jetbrains.plugins.terminal",
    ],
)

java_library(
    name = "jsr305",
    visibility = ["//visibility:public"],
    exports = [
        "//prebuilts/tools/common/m2:jsr305-2.0.1",
    ],
)

java_library(
    name = "coverage_for_tests",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = [
        "@intellij//:intellij-sdk",
    ],
)

java_library(
    name = "truth",
    visibility = ["//visibility:public"],
    exports = [
        "//tools/adt/idea/aswb/third_party/java/truth",
    ],
)

java_library(
    name = "guava_for_external_binaries",
    visibility = ["//visibility:public"],
    exports = [
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "hg4idea",
    visibility = ["//visibility:public"],
    exports = [
        "@intellij//:hg4idea",
    ],
)
