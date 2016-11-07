load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-rt",
    srcs = ["rt/src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools:idea.annotations[module]",
        "//tools:idea.util-rt[module]",
    ],
)

iml_module(
    name = "android-common",
    srcs = ["common/src"],
    resources = ["common/resources"],
    tags = ["managed"],
    test_resources = ["common/testResources"],
    test_srcs = ["common/testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
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
    # do not sort: must match IML order
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
)

iml_module(
    name = "layoutlib",
    srcs = ["layoutlib/src"],
    resources = ["layoutlib/resources"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    exports = ["//tools/idea/.idea/libraries:layoutlib"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:layoutlib",
        "//tools/base/sdk-common:studio.sdk-common[module]",
        "//tools:idea.annotations[module]",
        "//tools/adt/idea:android-common[module]",
        "//tools:idea.platform-api[module]",
    ],
)

iml_module(
    name = "sherpa-ui",
    srcs = ["sherpa-ui/src"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    deps = ["//tools/sherpa:sherpa-solver[module]"],
)

filegroup(
    name = "testFiles",
    srcs = glob([
        "android/annotations/**",
        "android/device-art-resources/**",
        "android/testData/**",
        "android/lib/androidWidgets/**",
        "android/lib/libwebp/**",
    ]),
    visibility = ["//visibility:public"],
)

iml_module(
    name = "android",
    # do not sort: must match IML order
    srcs = [
        "android/resources",
        "android/src",
        "android/gen",
    ],
    tags = ["managed"],
    test_srcs = ["android/testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/.idea/libraries:asm",
        "//tools/adt/idea:android-common",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools:idea.openapi[module]",
        "//tools:idea.platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:JUnit3[test]",
        "//tools:idea.compiler-impl_and_others[module]",
        "//tools:idea.execution-openapi[module]",
        "//tools:idea.smRunner[module]",
        "//tools:idea.junit[module]",
        "//tools:idea.maven_and_others[module]",
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
        "//tools/adt/idea/perfd-host:perfd-host[module]",
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
        "//tools/idea/.idea/libraries:studio-profiler-grpc-1.0-jarjar",
        "//tools/analytics-library:analytics-tracker[module]",
        "//tools/analytics-library:analytics-protos[module]",
        "//tools/analytics-library:analytics-shared[module]",
        "//tools/base/common:studio.common[module]",
        "//tools/idea/.idea/libraries:Netty[test]",
        "//tools/adt/idea/observable:observable[module]",
        "//tools/adt/idea/profilers:profilers[module]",
        "//tools/adt/idea/profilers-ui:profilers-ui[module]",
        "//tools/analytics-library:analytics-testing[module, test]",
    ],
)

java_import(
    name = "android/lib/commons-io-2.4",
    jars = ["android/lib/commons-io-2.4.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/juniversalchardet-1.0.3",
    jars = ["android/lib/juniversalchardet-1.0.3.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/antlr4-runtime-4.5.3",
    jars = ["android/lib/antlr4-runtime-4.5.3.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/asm-5.0.3",
    jars = ["android/lib/asm-5.0.3.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/asm-analysis-5.0.3",
    jars = ["android/lib/asm-analysis-5.0.3.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/asm-tree-5.0.3",
    jars = ["android/lib/asm-tree-5.0.3.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jarutils",
    jars = ["android/lib/jarutils.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/spantable",
    jars = ["android/lib/spantable.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jsr305-1.3.9",
    jars = ["android/lib/jsr305-1.3.9.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jgraphx-3.4.0.1",
    jars = ["android/lib/jgraphx-3.4.0.1.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jogl-all",
    jars = ["android/lib/jogl-all.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jogl-all-natives-linux-amd64",
    jars = ["android/lib/jogl-all-natives-linux-amd64.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jogl-all-natives-linux-i586",
    jars = ["android/lib/jogl-all-natives-linux-i586.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jogl-all-natives-macosx-universal",
    jars = ["android/lib/jogl-all-natives-macosx-universal.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jogl-all-natives-windows-amd64",
    jars = ["android/lib/jogl-all-natives-windows-amd64.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/jogl-all-natives-windows-i586",
    jars = ["android/lib/jogl-all-natives-windows-i586.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/gluegen-rt",
    jars = ["android/lib/gluegen-rt.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/gluegen-rt-natives-linux-amd64",
    jars = ["android/lib/gluegen-rt-natives-linux-amd64.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/gluegen-rt-natives-linux-i586",
    jars = ["android/lib/gluegen-rt-natives-linux-i586.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/gluegen-rt-natives-macosx-universal",
    jars = ["android/lib/gluegen-rt-natives-macosx-universal.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/gluegen-rt-natives-windows-amd64",
    jars = ["android/lib/gluegen-rt-natives-windows-amd64.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/gluegen-rt-natives-windows-i586",
    jars = ["android/lib/gluegen-rt-natives-windows-i586.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/libwebp",
    jars = ["android/lib/libwebp.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/dexlib2-2.0.8-dev",
    jars = ["android/lib/dexlib2-2.0.8-dev.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

java_import(
    name = "android/lib/dexlib2-util-2.0.8-dev",
    jars = ["android/lib/dexlib2-util-2.0.8-dev.jar"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
)

iml_module(
    name = "uitest-framework",
    tags = ["managed"],
    test_srcs = ["uitest-framework/testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/base/common:studio.common[module, test]",
        "//tools/adt/idea:android[module, test]",
        "//tools:idea.platform-api[module, test]",
        "//tools:idea.platform-impl_and_others[module]",
        "//tools:fest-swing[module, test]",
        "//tools/idea/.idea/libraries:jsr305[test]",
        "//tools/idea/.idea/libraries:truth[test]",
        "//tools:idea.gradle[module, test]",
        "//tools:idea.compiler-openapi[module, test]",
        "//tools:idea.java-impl[module, test]",
        "//tools:idea.openapi[module, test]",
        "//tools:idea.testRunner[module, test]",
        "//tools:idea.bootstrap[module, test]",
        "//tools/adt/idea:android/lib/spantable[test]",
        "//tools/adt/idea/adt-ui:adt-ui[module, test]",
        "//tools/base/rpclib:studio.rpclib[module, test]",
        "//tools/adt/idea/designer:designer[module, test]",
        "//tools/base/testutils:studio.testutils[module, test]",
    ],
)
