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
)

iml_module(
    name = "android-common",
    srcs = ["android/common/src"],
    test_srcs = ["android/common/testSrc"],
    resources = ["android/common/resources"],
    test_resources = ["android/common/testResources"],
    deps = [
        "//tools:idea.util[module]",
        "//tools/adt/idea:android/lib/jarutils",
        "//tools:idea/lib/nanoxml-2.2.3",
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
        "//tools/adt/idea:android/lib/jarutils",
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
)

iml_module(
    name = "layoutlib",
    srcs = ["layoutlib/src"],
    resources = ["layoutlib/resources"],
    deps = [
        "//prebuilts/studio/layoutlib:data/layoutlib",
        "//tools/base/sdk-common:studio.sdk-common[module]",
        "//tools:idea.annotations[module]",
        "//tools/adt/idea:android-common[module]",
        "//tools:idea.platform-api[module]",
    ],
    exports = ["//prebuilts/studio/layoutlib:data/layoutlib"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "sherpa-ui",
    srcs = ["android/sherpa-ui/src"],
    deps = ["//tools/sherpa:sherpa-solver[module]"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
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
        "//tools:idea/lib/junit[test]",
        "//tools:idea.MM_idea-ui[module]",
        "//tools:idea.execution-openapi[module]",
        "//tools:idea.smRunner[module]",
        "//tools:idea.junit[module]",
        "//tools:idea.MM_maven2-server-impl[module]",
        "//tools:idea.java-impl[module]",
        "//tools:idea.properties[module]",
        "//tools:idea/lib/xpp3-1.1.4-min",
        "//tools:idea.testng[module]",
        "//tools:idea/lib/asm",
        "//tools:idea/lib/asm-commons",
        "//tools/adt/idea:android-common[module]",
        "//tools/adt/idea:android-rt[module]",
        "//tools:idea.java-indexing-api[module]",
        "//tools:idea.jps-builders[module]",
        "//tools/base/draw9patch:studio.draw9patch[module]",
        "//tools/base/build-system:studio.manifest-merger[module]",
        "//prebuilts/tools/common/m2/repository/org/freemarker/freemarker/2.3.20:jar",
        "//tools/base/asset-studio:studio.assetstudio[module]",
        "//tools:idea.platform-api[module]",
        "//tools:idea.eclipse[module]",
        "//tools/adt/idea:android/lib/GoogleFeedback",
        "//tools:idea.external-system-api[module]",
        "//tools:idea/lib/gson-2.5",
        "//tools:idea.jetgroovy[module]",
        "//tools/base/perflib:studio.perflib[module]",
        "//tools:idea/lib/swingx-core-1.6.2",
        "//tools:idea.util[module]",
        "//tools/base/testutils:studio.testutils[module, test]",
        "//tools:idea/lib/ecj-4.5.2[test]",
        "//tools:idea.properties-psi-api[module]",
        "//tools:idea/lib/asm4-all",
        "//tools:idea.platform-main[module, test]",
        "//tools:idea.bootstrap[module]",
        "//tools:idea/lib/jcip-annotations",
        "//prebuilts/tools/common/m2/repository/org/mockito/mockito-all/1.9.5:jar[test]",
        "//tools/base/rpclib:studio.rpclib[module]",
        "//tools/adt/idea/adt-ui:adt-ui[module]",
        "//tools/adt/idea:android/lib/spantable",
        "//tools/adt/idea:android/lib/jsr305-1.3.9",
        "//tools:idea/lib/dev/easymock[test]",
        "//tools:idea/lib/dev/easymockclassextension[test]",
        "//tools:idea/lib/dev/jmock-2.5.1[test]",
        "//tools:idea/lib/dev/jmock-junit4-2.5.1[test]",
        "//tools:idea/lib/dev/jmock-legacy-2.5.1[test]",
        "//tools:idea/lib/dev/objenesis-1.0[test]",
        "//tools:idea/lib/hamcrest-library-1.3[test]",
        "//tools:idea/lib/hamcrest-core-1.3[test]",
        "//prebuilts/tools/common/m2/repository/com/google/truth/truth/0.28:jar[test]",
        "//tools/adt/idea:android/lib/jgraphx-3.4.0.1",
        "//tools/base/repository:studio.repository[module]",
        "//tools/base/instant-run:studio.instant-run-client[module]",
        "//tools/base/instant-run:studio.instant-run-common[module]",
        "//tools:idea/lib/jna",
        "//tools:idea/lib/jna-platform",
        "//tools/data-binding:db-compiler[module]",
        "//tools/adt/idea:android/lib/jogl-all",
        "//tools/adt/idea:android/lib/jogl-all-natives-linux-amd64",
        "//tools/adt/idea:android/lib/jogl-all-natives-linux-i586",
        "//tools/adt/idea:android/lib/jogl-all-natives-macosx-universal",
        "//tools/adt/idea:android/lib/jogl-all-natives-windows-amd64",
        "//tools/adt/idea:android/lib/jogl-all-natives-windows-i586",
        "//tools/adt/idea:android/lib/gluegen-rt",
        "//tools/adt/idea:android/lib/gluegen-rt-natives-linux-amd64",
        "//tools/adt/idea:android/lib/gluegen-rt-natives-linux-i586",
        "//tools/adt/idea:android/lib/gluegen-rt-natives-macosx-universal",
        "//tools/adt/idea:android/lib/gluegen-rt-natives-windows-amd64",
        "//tools/adt/idea:android/lib/gluegen-rt-natives-windows-i586",
        "//tools/adt/idea:android/lib/libwebp",
        "//tools:idea.lang-api[module]",
        "//tools:idea/plugins/gradle/lib/gradle-base-services-3.0",
        "//tools:idea/plugins/gradle/lib/gradle-base-services-groovy-3.0",
        "//tools:idea/plugins/gradle/lib/gradle-core-3.0",
        "//tools:idea/plugins/gradle/lib/gradle-messaging-3.0",
        "//tools:idea/plugins/gradle/lib/gradle-model-core-3.0",
        "//tools:idea/plugins/gradle/lib/gradle-model-groovy-3.0",
        "//tools:idea/plugins/gradle/lib/gradle-resources-3.0",
        "//tools:idea/plugins/gradle/lib/gradle-native-3.0",
        "//tools:idea/plugins/gradle/lib/gradle-tooling-api-3.0",
        "//tools:idea/plugins/gradle/lib/gradle-wrapper-3.0",
        "//tools:idea.gradle[module]",
        "//tools:idea.gradle-tests[module, test]",
        "//tools/adt/idea:layoutlib[module]",
        "//tools/sherpa:sherpa-solver[module]",
        "//tools/adt/idea:sherpa-ui[module]",
        "//tools/adt/idea:android/lib/dexlib2-2.0.8-dev",
        "//tools/adt/idea:android/lib/dexlib2-util-2.0.8-dev",
        "//tools/base/apkparser:studio.binary-resources[module]",
        "//tools/base/pixelprobe:studio.pixelprobe[module]",
        "////tools/base/profiler:studio-profiler-grpc-1.0-jarjar",
        "//tools/analytics-library:analytics-tracker[module]",
        "//tools/analytics-library:analytics-protos[module]",
        "//tools/analytics-library:analytics-shared[module]",
        "//tools/base/common:studio.common[module]",
    ],
    exports = [
        "//tools:idea/lib/asm",
        "//tools:idea/lib/asm-commons",
        "//tools/adt/idea:android-common",
    ],
    test_data = glob(["android/testData/**/*", "designer/testData/**/*"]),
    test_timeout = "long",
    test_class = "com.android.tools.idea.IdeaTestSuite",
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/commons-compress-1.8.1",
    jars = [
        "android/lib/commons-compress-1.8.1.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/commons-io-2.4",
    jars = [
        "android/lib/commons-io-2.4.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/juniversalchardet-1.0.3",
    jars = [
        "android/lib/juniversalchardet-1.0.3.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/antlr4-runtime-4.5.3",
    jars = [
        "android/lib/antlr4-runtime-4.5.3.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/asm-5.0.3",
    jars = [
        "android/lib/asm-5.0.3.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/asm-analysis-5.0.3",
    jars = [
        "android/lib/asm-analysis-5.0.3.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/asm-tree-5.0.3",
    jars = [
        "android/lib/asm-tree-5.0.3.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jarutils",
    jars = [
        "android/lib/jarutils.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/GoogleFeedback",
    jars = [
        "android/lib/GoogleFeedback.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/spantable",
    jars = [
        "android/lib/spantable.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jsr305-1.3.9",
    jars = [
        "android/lib/jsr305-1.3.9.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jgraphx-3.4.0.1",
    jars = [
        "android/lib/jgraphx-3.4.0.1.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jogl-all",
    jars = [
        "android/lib/jogl-all.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jogl-all-natives-linux-amd64",
    jars = [
        "android/lib/jogl-all-natives-linux-amd64.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jogl-all-natives-linux-i586",
    jars = [
        "android/lib/jogl-all-natives-linux-i586.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jogl-all-natives-macosx-universal",
    jars = [
        "android/lib/jogl-all-natives-macosx-universal.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jogl-all-natives-windows-amd64",
    jars = [
        "android/lib/jogl-all-natives-windows-amd64.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jogl-all-natives-windows-i586",
    jars = [
        "android/lib/jogl-all-natives-windows-i586.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/gluegen-rt",
    jars = [
        "android/lib/gluegen-rt.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/gluegen-rt-natives-linux-amd64",
    jars = [
        "android/lib/gluegen-rt-natives-linux-amd64.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/gluegen-rt-natives-linux-i586",
    jars = [
        "android/lib/gluegen-rt-natives-linux-i586.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/gluegen-rt-natives-macosx-universal",
    jars = [
        "android/lib/gluegen-rt-natives-macosx-universal.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/gluegen-rt-natives-windows-amd64",
    jars = [
        "android/lib/gluegen-rt-natives-windows-amd64.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/gluegen-rt-natives-windows-i586",
    jars = [
        "android/lib/gluegen-rt-natives-windows-i586.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/libwebp",
    jars = [
        "android/lib/libwebp.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/dexlib2-2.0.8-dev",
    jars = [
        "android/lib/dexlib2-2.0.8-dev.jar",
    ],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/dexlib2-util-2.0.8-dev",
    jars = [
        "android/lib/dexlib2-util-2.0.8-dev.jar",
    ],
    visibility = ["//visibility:public"],
)
