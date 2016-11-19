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
    name = "android-annotations",
    srcs = glob(["android/annotations/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "testFiles",
    srcs = glob([
        "android/device-art-resources/**",
        "android/testData/**",
    ]) + [":android-annotations"],
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
    test_tags = ["manual"],
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
        "//tools:idea.properties-psi-api[module]",
        "//tools:idea.bootstrap[module]",
        "//tools/idea/.idea/libraries:jcip",
        "//tools/base/rpclib:studio.rpclib[module]",
        "//tools/adt/idea/adt-ui[module]",
        "//tools/adt/idea/adt-ui-model[module]",
        "//tools/adt/idea/perfd-host[module]",
        "//tools/adt/idea/android/lib:spantable",
        "//tools/idea/.idea/libraries:jsr305",
        "//tools/idea/.idea/libraries:jgraphx-3.4.0.1",
        "//tools/base/repository:studio.repository[module]",
        "//tools/base/instant-run:studio.instant-run-client[module]",
        "//tools/base/instant-run:studio.instant-run-common[module]",
        "//tools/idea/.idea/libraries:jna",
        "//tools/data-binding:studio.compiler[module]",
        "//tools/idea/.idea/libraries:jogl-all",
        "//tools/idea/.idea/libraries:gluegen-rt",
        "//tools/adt/idea/android/lib:libwebp",
        "//tools:idea.lang-api[module]",
        "//tools/idea/.idea/libraries:Gradle",
        "//tools:idea.gradle[module]",
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
        "//tools/adt/idea/observable[module]",
        "//tools/adt/idea/profilers[module]",
        "//tools/adt/idea/profilers-ui[module]",
        "//tools:idea.gradle-tooling-extension-impl[module]",
    ],
)
