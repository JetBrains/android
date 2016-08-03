# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "iml_module")

java_import(
  name = "android/lib/spantable",
  jars = [
      "android/lib/spantable.jar",
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

iml_module(
    name = "sherpa-ui",
    srcs = ["android/sherpa-ui/src"],
    deps = ["//tools/sherpa:sherpa-solver[module]"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "android/lib/gluegen-rt-natives-windows-i586",
  jars = [
      "android/lib/gluegen-rt-natives-windows-i586.jar",
    ],
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
        "//tools/idea:openapi[module]",
        "//tools/idea:MM_RegExpSupport[module]",
        "//tools/idea:lib/junit[test]",
        "//tools/idea:MM_idea-ui[module]",
        "//tools/idea:execution-openapi[module]",
        "//tools/idea:smRunner[module]",
        "//tools/idea:junit[module]",
        "//tools/idea:MM_maven2-server-impl[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea:properties[module]",
        "//tools/idea:lib/xpp3-1.1.4-min",
        "//tools/idea:testng[module]",
        "//tools/adt/idea:android-common[module]",
        "//tools/adt/idea:android-rt[module]",
        "//tools/idea:java-indexing-api[module]",
        "//tools/idea:jps-builders[module]",
        "//tools/base/draw9patch:draw9patch[module]",
        "//tools/base/build-system:manifest-merger[module]",
        "//prebuilts/tools/common/m2:repository/org/freemarker/freemarker/2.3.20/freemarker-2.3.20",
        "//tools/base/asset-studio:assetstudio[module]",
        "//tools/idea:platform-api[module]",
        "//tools/idea:eclipse[module]",
        "//tools/adt/idea:android/lib/GoogleFeedback",
        "//tools/idea:external-system-api[module]",
        "//tools/idea:lib/gson-2.5",
        "//tools/idea:jetgroovy[module]",
        "//tools/base/perflib:perflib[module]",
        "//tools/idea:lib/swingx-core-1.6.2",
        "//tools/idea:util[module]",
        "//tools/base/testutils:testutils[module, test]",
        "//tools/idea:lib/ecj-4.5.2[test]",
        "//tools/idea:properties-psi-api[module]",
        "//tools/idea:lib/asm4-all",
        "//tools/adt/idea:android/lib/fest-assert-1.5.0-SNAPSHOT[test]",
        "//tools/adt/idea:android/lib/fest-reflect-2.0-SNAPSHOT[test]",
        "//tools/adt/idea:android/lib/fest-swing-1.4-SNAPSHOT[test]",
        "//tools/adt/idea:android/lib/fest-util-1.3.0-SNAPSHOT[test]",
        "//tools/adt/idea:android/lib/jsr305-1.3.9",
        "//tools/idea:platform-main[module, test]",
        "//tools/idea:bootstrap[module]",
        "//tools/idea:lib/jcip-annotations",
        "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5[test]",
        "//tools/base/rpclib:rpclib[module]",
        "//tools/adt/idea:adt-ui[module]",
        "//tools/adt/idea:android/lib/spantable",
        "//tools/idea:lib/dev/easymock[test]",
        "//tools/idea:lib/dev/easymockclassextension[test]",
        "//tools/idea:lib/dev/jmock-2.5.1[test]",
        "//tools/idea:lib/dev/jmock-junit4-2.5.1[test]",
        "//tools/idea:lib/dev/jmock-legacy-2.5.1[test]",
        "//tools/idea:lib/dev/objenesis-1.0[test]",
        "//tools/idea:lib/hamcrest-library-1.3[test]",
        "//tools/idea:lib/hamcrest-core-1.3[test]",
        "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28[test]",
        "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28-sources[test]",
        "//tools/adt/idea:android/lib/jgraphx-3.4.0.1",
        "//tools/base/repository:repository[module]",
        "//tools/base/instant-run:instant-run-client[module]",
        "//tools/base/instant-run:instant-run-common[module]",
        "//tools/idea:lib/jna",
        "//tools/idea:lib/jna-platform",
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
        "//tools/idea:lang-api[module]",
        "//tools/idea:gradle[module]",
        "//tools/idea:gradle-tests[module, test]",
        "//tools/adt/idea:layoutlib[module]",
        "//tools/sherpa:sherpa-solver[module]",
        "//tools/adt/idea:sherpa-ui[module]",
        "//tools/adt/idea:android/lib/dexlib2-2.0.8-dev",
        "//tools/adt/idea:android/lib/dexlib2-util-2.0.8-dev",
        "//tools/base/apkparser:binary-resources[module]",
        "//tools/base/pixelprobe:pixelprobe[module]",
        "//tools/base/bazel:prebuilts/studio-profiler-grpc-1.0-jarjar",
        "//tools/analytics-library:analytics-tracker[module]",
        "//tools/analytics-library:analytics-protos[module]",
        "//tools/analytics-library:analytics-shared[module]",
        "//tools/base/common:common[module]",
    ],
    exports = ["//tools/adt/idea:android-common"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "android/lib/jogl-all-natives-windows-i586",
  jars = [
      "android/lib/jogl-all-natives-windows-i586.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "layoutlib",
    srcs = ["layoutlib/src"],
    resources = ["layoutlib/resources"],
    deps = [
        "//prebuilts/studio/layoutlib:data/layoutlib",
        "//tools/base/sdk-common:sdk-common[module]",
        "//tools/idea:annotations[module]",
        "//tools/adt/idea:android-common[module]",
        "//tools/idea:platform-api[module]",
    ],
    exports = ["//prebuilts/studio/layoutlib:data/layoutlib"],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "android/lib/jogl-all",
  jars = [
      "android/lib/jogl-all.jar",
    ],
  visibility = ["//visibility:public"],
)

iml_module(
    name = "adt-ui",
    srcs = ["adt-ui/src/main/java"],
    test_srcs = ["adt-ui/src/test/java"],
    deps = [
        "//tools/base/annotations:android-annotations[module]",
        "//tools/idea:lib/trove4j",
        "//tools/idea:platform-api[module]",
        "//tools/idea:MM_RegExpSupport[module]",
        "//tools/idea:lib/hamcrest-core-1.3[test]",
        "//tools/idea:lib/junit-4.12[test]",
    ],
    javacopts = ["-extra_checks:off"],
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
  name = "android/lib/jarutils",
  jars = [
      "android/lib/jarutils.jar",
    ],
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
  name = "android/lib/gluegen-rt",
  jars = [
      "android/lib/gluegen-rt.jar",
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

iml_module(
    name = "android-common",
    srcs = ["android/common/src"],
    test_srcs = ["android/common/testSrc"],
    resources = ["android/common/resources"],
    deps = [
        "//tools/idea:util[module]",
        "//tools/adt/idea:android/lib/jarutils",
        "//tools/idea:lib/nanoxml-2.2.3",
        "//tools/base/common:common[module]",
        "//tools/base/ddmlib:ddmlib[module]",
        "//tools/base/device_validator:dvlib[module]",
        "//tools/base/layoutlib-api:layoutlib-api[module]",
        "//tools/base/lint:lint-api[module]",
        "//tools/base/lint:lint-checks[module]",
        "//tools/base/ninepatch:ninepatch[module]",
        "//tools/base/sdk-common:sdk-common[module]",
        "//tools/base/sdklib:sdklib[module]",
    ],
    exports = [
        "//tools/adt/idea:android/lib/jarutils",
        "//tools/base/common:common",
        "//tools/base/ddmlib:ddmlib",
        "//tools/base/device_validator:dvlib",
        "//tools/base/layoutlib-api:layoutlib-api",
        "//tools/base/lint:lint-api",
        "//tools/base/lint:lint-checks",
        "//tools/base/ninepatch:ninepatch",
        "//tools/base/sdk-common:sdk-common",
        "//tools/base/sdklib:sdklib",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "android/lib/fest-util-1.3.0-SNAPSHOT",
  jars = [
      "android/lib/fest-util-1.3.0-SNAPSHOT.jar",
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
  name = "android/lib/jsr305-1.3.9",
  jars = [
      "android/lib/jsr305-1.3.9.jar",
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
  name = "android/lib/antlr4-runtime-4.5.3",
  jars = [
      "android/lib/antlr4-runtime-4.5.3.jar",
    ],
  visibility = ["//visibility:public"],
)

java_import(
  name = "android/lib/fest-swing-1.4-SNAPSHOT",
  jars = [
      "android/lib/fest-swing-1.4-SNAPSHOT.jar",
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
  name = "android/lib/dexlib2-util-2.0.8-dev",
  jars = [
      "android/lib/dexlib2-util-2.0.8-dev.jar",
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
  name = "android/lib/fest-reflect-2.0-SNAPSHOT",
  jars = [
      "android/lib/fest-reflect-2.0-SNAPSHOT.jar",
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
  name = "android/lib/dexlib2-2.0.8-dev",
  jars = [
      "android/lib/dexlib2-2.0.8-dev.jar",
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

iml_module(
    name = "android-rt",
    srcs = ["android/rt/src"],
    deps = [
        "//tools/idea:annotations[module]",
        "//tools/idea:util-rt[module]",
    ],
    javacopts = ["-extra_checks:off"],
    visibility = ["//visibility:public"],
)

java_import(
  name = "android/lib/fest-assert-1.5.0-SNAPSHOT",
  jars = [
      "android/lib/fest-assert-1.5.0-SNAPSHOT.jar",
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
  name = "android/lib/jogl-all-natives-macosx-universal",
  jars = [
      "android/lib/jogl-all-natives-macosx-universal.jar",
    ],
  visibility = ["//visibility:public"],
)
