# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-rt",
    srcs = ["android/rt/src"],
    deps = [
        "//tools:idea.annotations[module]",
        "//tools:idea.util-rt[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

iml_module(
    name = "android-common",
    srcs = ["android/common/src"],
    test_srcs = ["android/common/testSrc"],
    resources = ["android/common/resources"],
    test_resources = ["android/common/testResources"],
    deps = [
        "//tools:idea.util[module]",
        "//tools/idea/.idea/libraries:android-sdk-tools-jps",
        "//tools/idea/.idea/libraries:NanoXML",
        "//tools/base/common:studio.common[module]",
        "//tools/base/ddmlib:studio.ddmlib[module]",
        "//tools/base/device_validator:studio.dvlib[module]",
        "//tools/base/layoutlib-api:studio.layoutlib-api[module]",
        "//tools/base/lint:studio.lint-api[module]",
        "//tools/base/lint:studio.lint-checks[module]",
        "//tools/base/ninepatch:studio.ninepatch[module]",
        "//tools/base/sdk-common:studio.sdk-common[module]",
        "//tools/base/sdklib:studio.sdklib[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
    ],
    exports = [
        "//tools/idea/.idea/libraries:android-sdk-tools-jps",
        "//tools/base/common:studio.common",
        "//tools/base/ddmlib:studio.ddmlib",
        "//tools/base/device_validator:studio.dvlib",
        "//tools/base/layoutlib-api:studio.layoutlib-api",
        "//tools/base/lint:studio.lint-api",
        "//tools/base/lint:studio.lint-checks",
        "//tools/base/ninepatch:studio.ninepatch",
        "//tools/base/sdk-common:studio.sdk-common",
        "//tools/base/sdklib:studio.sdklib",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

iml_module(
    name = "layoutlib",
    srcs = ["layoutlib/src"],
    resources = ["layoutlib/resources"],
    deps = [
        "//tools/idea/.idea/libraries:layoutlib",
        "//tools/base/sdk-common:studio.sdk-common[module]",
        "//tools:idea.annotations[module]",
        "//tools/adt/idea:android-common[module]",
        "//tools:idea.platform-api[module]",
    ],
    exports = ["//tools/idea/.idea/libraries:layoutlib"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

iml_module(
    name = "sherpa-ui",
    srcs = ["android/sherpa-ui/src"],
    deps = ["//tools/sherpa:sherpa-solver[module]"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

iml_module(
    name = "android",
    srcs = [
        "android/resources",
        "android/src",
        "android/gen",
        "designer/src",
    ],
    test_srcs = [
        "android/testSrc",
        "designer/testSrc",
    ],
    resources = ["designer/resources"],
    deps = [
        "//tools:idea.openapi[module]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools/idea/.idea/libraries:JUnit3[test]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea.execution-openapi[module]",
        "//tools:idea.smRunner[module]",
        "//tools:idea.junit[module]",
        "//tools:idea.MM_maven2-server-impl[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea.properties[module]",
        "//tools/idea/.idea/libraries:xpp3-1.1.4-min",
        "//tools:idea.testng[module]",
        "//tools/idea/.idea/libraries:asm",
        "//tools/adt/idea:android-common[module]",
        "//tools/adt/idea:android-rt[module]",
        "//tools:idea.java-indexing-api[module]",
        "//tools:idea.jps-builders[module]",
        "//tools/base/draw9patch:studio.draw9patch[module]",
        "//tools/base/build-system:studio.manifest-merger[module]",
        "//tools/idea/.idea/libraries:freemarker-2.3.20",
        "//tools/base/asset-studio:studio.assetstudio[module]",
        "//tools:idea.platform-api[module]",
        "//tools:idea.eclipse[module]",
        "//tools/adt/idea:android/lib/GoogleFeedback",
        "//tools:idea.external-system-api[module]",
        "//tools/idea/.idea/libraries:gson",
        "//tools:idea.jetgroovy[module]",
        "//tools/base/perflib:studio.perflib[module]",
        "//tools/idea/.idea/libraries:swingx",
        "//tools:idea.util[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools/idea/.idea/libraries:Eclipse[test]",
        "//tools:idea.properties-psi-api[module]",
        "//tools:idea.platform-main[module, test]",
        "//tools:idea.bootstrap[module]",
        "//tools/idea/.idea/libraries:jcip",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/base/rpclib:studio.rpclib[module]",
        "//tools/adt/idea/adt-ui:adt-ui[module]",
        "//tools/adt/idea:android/lib/spantable",
        "//tools/idea/.idea/libraries:jsr305",
        "//tools/idea/.idea/libraries:Mocks[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools/idea/.idea/libraries:jgraphx-3.4.0.1",
        "//tools/base/repository:studio.repository[module]",
        "//tools/base/instant-run:studio.instant-run-client[module]",
        "//tools/base/instant-run:studio.instant-run-common[module]",
        "//tools/idea/.idea/libraries:jna",
        "//tools/data-binding:studio.compiler[module]",
        "//tools/idea/.idea/libraries:jogl-all",
        "//tools/idea/.idea/libraries:gluegen-rt",
        "//tools/adt/idea:android/lib/libwebp",
        "//tools:idea.lang-api[module]",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools:idea.gradle[module]",
        "//tools:idea.gradle-tests[module, test]",
        "//tools/adt/idea:layoutlib[module]",
        "//tools/sherpa:sherpa-solver[module]",
        "//tools/adt/idea:sherpa-ui[module]",
        "//tools/idea/.idea/libraries:dexlib2",
        "//tools/base/apkparser:studio.binary-resources[module]",
        "//tools/base/pixelprobe:studio.pixelprobe[module]",
        "//tools/idea/.idea/libraries:profiler-grpc-java",
        "//tools/analytics-library:analytics-tracker[module]",
        "//tools/analytics-library:analytics-protos[module]",
        "//tools/analytics-library:analytics-shared[module]",
        "//tools/base/common:studio.common[module]",
    ],
    test_runtime_deps = ["//tools/adt/idea/adt-branding"],
    exports = [
        "//tools/idea/.idea/libraries:asm",
        "//tools/adt/idea:android-common",
    ],
    test_data = glob([
        "android/annotations/**",
        "android/device-art-resources/**",
        "android/testData/**/*",
        "designer/testData/**/*",
    ]) + [
        "//prebuilts/studio/jdk",
        "//prebuilts/studio/layoutlib:data/res",
        "//prebuilts/studio/sdk:build-tools/latest",
        "//prebuilts/studio/sdk:licenses",
        "//prebuilts/studio/sdk:platforms/latest",
        "//prebuilts/tools/common/m2:guava-18",
        "//prebuilts/tools/common/offline-m2:android-gradle-2.2.0",
        "//tools/base/templates",
        "//tools/external/gradle:gradle",
        "//tools:idea/java/jdkAnnotations",
    ],
    test_class = "com.android.tools.idea.IdeaTestSuite",
    test_shard_count = 2,
    test_timeout = "long",
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/commons-io-2.4",
    jars = ["android/lib/commons-io-2.4.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/juniversalchardet-1.0.3",
    jars = ["android/lib/juniversalchardet-1.0.3.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/antlr4-runtime-4.5.3",
    jars = ["android/lib/antlr4-runtime-4.5.3.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/asm-5.0.3",
    jars = ["android/lib/asm-5.0.3.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/asm-analysis-5.0.3",
    jars = ["android/lib/asm-analysis-5.0.3.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/asm-tree-5.0.3",
    jars = ["android/lib/asm-tree-5.0.3.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/jarutils",
    jars = ["android/lib/jarutils.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/GoogleFeedback",
    jars = ["android/lib/GoogleFeedback.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/spantable",
    jars = ["android/lib/spantable.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/jsr305-1.3.9",
    jars = ["android/lib/jsr305-1.3.9.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/jgraphx-3.4.0.1",
    jars = ["android/lib/jgraphx-3.4.0.1.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/jogl-all",
    jars = ["android/lib/jogl-all.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/jogl-all-natives-linux-amd64",
    jars = ["android/lib/jogl-all-natives-linux-amd64.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/jogl-all-natives-linux-i586",
    jars = ["android/lib/jogl-all-natives-linux-i586.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/jogl-all-natives-macosx-universal",
    jars = ["android/lib/jogl-all-natives-macosx-universal.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/jogl-all-natives-windows-amd64",
    jars = ["android/lib/jogl-all-natives-windows-amd64.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/jogl-all-natives-windows-i586",
    jars = ["android/lib/jogl-all-natives-windows-i586.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/gluegen-rt",
    jars = ["android/lib/gluegen-rt.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/gluegen-rt-natives-linux-amd64",
    jars = ["android/lib/gluegen-rt-natives-linux-amd64.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/gluegen-rt-natives-linux-i586",
    jars = ["android/lib/gluegen-rt-natives-linux-i586.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/gluegen-rt-natives-macosx-universal",
    jars = ["android/lib/gluegen-rt-natives-macosx-universal.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/gluegen-rt-natives-windows-amd64",
    jars = ["android/lib/gluegen-rt-natives-windows-amd64.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/gluegen-rt-natives-windows-i586",
    jars = ["android/lib/gluegen-rt-natives-windows-i586.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/libwebp",
    jars = ["android/lib/libwebp.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/dexlib2-2.0.8-dev",
    jars = ["android/lib/dexlib2-2.0.8-dev.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

java_import(
    name = "android/lib/dexlib2-util-2.0.8-dev",
    jars = ["android/lib/dexlib2-util-2.0.8-dev.jar"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)

iml_module(
    name = "uitest-framework",
    test_srcs = ["uitest-framework/testSrc"],
    deps = [
        "//tools/base/common:studio.common[module, test]",
        "//tools/adt/idea:android[module, test]",
        "//tools:idea.platform-api[module, test]",
        "//tools:idea.MM_RegExpSupport[module]",
        "//tools:fest-swing[module]",
        "//tools/idea/.idea/libraries:jsr305",
        "//tools/idea/.idea/libraries:truth",
        "//tools:idea.gradle[module, test]",
        "//tools:idea.compiler-openapi[module, test]",
        "//tools:idea.java-impl[module, test]",
        "//tools:idea.openapi[module, test]",
        "//tools:idea.testRunner[module, test]",
        "//tools:idea.bootstrap[module, test]",
        "//tools/adt/idea:android/lib/spantable",
        "//tools/adt/idea/adt-ui:adt-ui[module, test]",
        "//tools/base/rpclib:studio.rpclib[module, test]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
    tags = ["managed"],
)
