# This file has been automatically generated, please do not modify directly.
load("//tools/base/bazel:bazel.bzl", "kotlin_library", "groovy_library", "kotlin_groovy_library", "fileset")

java_import(
  name = "android/lib/jgraphx-3.4.0.1",
  jars = [
      "android/lib/jgraphx-3.4.0.1.jar",
    ],
  visibility = ["//visibility:public"],
)

java_library(
  name = "sherpa-ui",
  srcs = glob([
      "android/sherpa-ui/src/**/*.java",
    ]),
  resource_strip_prefix = "tools/adt/idea/sherpa-ui.resources",
  resources = [
      "//tools/adt/idea:sherpa-ui.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/sherpa:sherpa-solver",
    ],
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

java_import(
  name = "android/lib/jogl-all-natives-windows-i586",
  jars = [
      "android/lib/jogl-all-natives-windows-i586.jar",
    ],
  visibility = ["//visibility:public"],
)

java_library(
  name = "layoutlib",
  srcs = glob([
      "layoutlib/src/**/*.java",
    ]),
  resource_strip_prefix = "tools/adt/idea/layoutlib.resources",
  resources = [
      "//tools/adt/idea:layoutlib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//prebuilts/studio/layoutlib:data/layoutlib",
      "//tools/base/sdk-common:sdk-common",
      "//tools/idea:annotations",
      "//tools/adt/idea:android-common",
      "//tools/idea:platform-api",
    ],
  exports = [
      "//prebuilts/studio/layoutlib:data/layoutlib",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_library(
  name = "adt-ui",
  srcs = glob([
      "adt-ui/src/main/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/adt/idea/adt-ui.resources",
  resources = [
      "//tools/adt/idea:adt-ui.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/base/annotations:android-annotations",
      "//tools/idea:lib/trove4j",
      "//tools/idea:platform-api",
      "//tools/idea:MM_RegExpSupport",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
    ],
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

fileset(
  name = "sherpa-ui.res",
  srcs = glob([
      "android/sherpa-ui/src/**/*",
    ],
    exclude = [
      "**/* *",
      "**/*.java",
      "**/*.kt",
      "**/*.groovy",
      "**/*$*",
      "**/.DS_Store",
    ]),
  mappings = {
      "android/sherpa-ui/src": "sherpa-ui.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_import(
  name = "android/lib/gluegen-rt",
  jars = [
      "android/lib/gluegen-rt.jar",
    ],
  visibility = ["//visibility:public"],
)

java_library(
  name = "android-common",
  srcs = glob([
      "android/common/src/**/*.java",
    ]),
  resource_strip_prefix = "tools/adt/idea/android-common.resources",
  resources = [
      "//tools/adt/idea:android-common.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/idea:util",
      "//tools/adt/idea:android/lib/jarutils",
      "//tools/idea:lib/nanoxml-2.2.3",
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

java_test(
  name = "adt-ui_tests",
  srcs = glob([
    ]),
  runtime_deps = [
      ":adt-ui_testlib",
      "//tools/base/testutils:testutils",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=adt-ui_testlib.jar",
    ],
  test_class = "com.android.testutils.JarTestSuite",
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_import(
  name = "android/lib/jogl-all-natives-windows-amd64",
  jars = [
      "android/lib/jogl-all-natives-windows-amd64.jar",
    ],
  visibility = ["//visibility:public"],
)

fileset(
  name = "adt-ui_testlib.res",
  srcs = glob([
      "adt-ui/src/test/resources/**/*",
    ],
    exclude = [
      "**/* *",
      "**/*.java",
      "**/*.kt",
      "**/*.groovy",
      "**/*$*",
      "**/.DS_Store",
    ]),
  mappings = {
      "adt-ui/src/test/resources": "adt-ui_testlib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_import(
  name = "android/lib/gluegen-rt-natives-linux-i586",
  jars = [
      "android/lib/gluegen-rt-natives-linux-i586.jar",
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

fileset(
  name = "android-common.res",
  srcs = glob([
      "android/common/src/**/*",
      "android/common/resources/**/*",
    ],
    exclude = [
      "**/* *",
      "**/*.java",
      "**/*.kt",
      "**/*.groovy",
      "**/*$*",
      "**/.DS_Store",
    ]),
  mappings = {
      "android/common/resources": "android-common.resources",
      "android/common/src": "android-common.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_library(
  name = "android",
  srcs = glob([
      "android/resources/**/*.java",
      "android/src/**/*.java",
      "android/gen/**/*.java",
      "designer/src/**/*.java",
    ]),
  resource_strip_prefix = "tools/adt/idea/android.resources",
  resources = [
      "//tools/adt/idea:android.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/idea:openapi",
      "//tools/idea:MM_RegExpSupport",
      "//tools/idea:lib/junit",
      "//tools/idea:MM_idea-ui",
      "//tools/idea:execution-openapi",
      "//tools/idea:smRunner",
      "//tools/idea:junit",
      "//tools/idea:MM_maven2-server-impl",
      "//tools/idea:java-impl",
      "//tools/idea:properties",
      "//tools/idea:lib/xpp3-1.1.4-min",
      "//tools/idea:testng",
      "//tools/adt/idea:android-common",
      "//tools/adt/idea:android-rt",
      "//tools/idea:java-indexing-api",
      "//tools/idea:jps-builders",
      "//tools/base/draw9patch:draw9patch",
      "//tools/base/build-system:manifest-merger",
      "//prebuilts/tools/common/m2:repository/org/freemarker/freemarker/2.3.20/freemarker-2.3.20",
      "//tools/base/asset-studio:assetstudio",
      "//tools/idea:platform-api",
      "//tools/idea:eclipse",
      "//tools/adt/idea:android/lib/GoogleFeedback",
      "//tools/idea:external-system-api",
      "//tools/idea:lib/gson-2.5",
      "//tools/idea:jetgroovy",
      "//tools/base/perflib:perflib",
      "//tools/idea:lib/swingx-core-1.6.2",
      "//tools/idea:util",
      "//tools/base/testutils:testutils",
      "//tools/idea:lib/ecj-4.5.2",
      "//tools/idea:properties-psi-api",
      "//tools/idea:lib/asm4-all",
      "//tools/adt/idea:android/lib/fest-assert-1.5.0-SNAPSHOT",
      "//tools/adt/idea:android/lib/fest-reflect-2.0-SNAPSHOT",
      "//tools/adt/idea:android/lib/fest-swing-1.4-SNAPSHOT",
      "//tools/adt/idea:android/lib/fest-util-1.3.0-SNAPSHOT",
      "//tools/adt/idea:android/lib/jsr305-1.3.9",
      "//tools/idea:platform-main",
      "//tools/idea:bootstrap",
      "//tools/idea:lib/jcip-annotations",
      "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5",
      "//tools/base/rpclib:rpclib",
      "//tools/adt/idea:adt-ui",
      "//tools/adt/idea:android/lib/spantable",
      "//tools/idea:lib/dev/easymock",
      "//tools/idea:lib/dev/easymockclassextension",
      "//tools/idea:lib/dev/jmock-2.5.1",
      "//tools/idea:lib/dev/jmock-junit4-2.5.1",
      "//tools/idea:lib/dev/jmock-legacy-2.5.1",
      "//tools/idea:lib/dev/objenesis-1.0",
      "//tools/idea:lib/hamcrest-library-1.3",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28-sources",
      "//tools/adt/idea:android/lib/jgraphx-3.4.0.1",
      "//tools/base/repository:repository",
      "//tools/base/instant-run:instant-run-client",
      "//tools/base/instant-run:instant-run-common",
      "//tools/idea:lib/jna",
      "//tools/idea:lib/jna-platform",
      "//tools/data-binding:db-compiler",
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
      "//tools/idea:lang-api",
      "//tools/idea:gradle",
      "//tools/idea:gradle-tests",
      "//tools/adt/idea:layoutlib",
      "//tools/sherpa:sherpa-solver",
      "//tools/adt/idea:sherpa-ui",
      "//tools/adt/idea:android/lib/dexlib2-2.0.8-dev",
      "//tools/adt/idea:android/lib/dexlib2-util-2.0.8-dev",
      "//tools/base/apkparser:binary-resources",
      "//tools/base/pixelprobe:pixelprobe",
      "//tools/base/bazel:prebuilts/studio-profiler-grpc-1.0-jarjar",
      "//tools/analytics-library:analytics-tracker",
      "//tools/analytics-library:analytics-protos",
      "//tools/analytics-library:analytics-shared",
      "//tools/base/common:common",
    ],
  exports = [
      "//tools/adt/idea:android-common",
    ],
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

fileset(
  name = "layoutlib.res",
  srcs = glob([
      "layoutlib/src/**/*",
      "layoutlib/resources/**/*",
    ],
    exclude = [
      "**/* *",
      "**/*.java",
      "**/*.kt",
      "**/*.groovy",
      "**/*$*",
      "**/.DS_Store",
    ]),
  mappings = {
      "layoutlib/resources": "layoutlib.resources",
      "layoutlib/src": "layoutlib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
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
  name = "android/lib/jogl-all-natives-linux-i586",
  jars = [
      "android/lib/jogl-all-natives-linux-i586.jar",
    ],
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

java_library(
  name = "android_testlib",
  srcs = glob([
      "android/testSrc/**/*.java",
      "designer/testSrc/**/*.java",
    ]),
  resource_strip_prefix = "tools/adt/idea/android_testlib.resources",
  resources = [
      "//tools/adt/idea:android_testlib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/adt/idea:android",
      "//tools/idea:openapi",
      "//tools/idea:MM_RegExpSupport",
      "//tools/idea:MM_RegExpSupport_testlib",
      "//tools/idea:lib/junit",
      "//tools/idea:MM_idea-ui",
      "//tools/idea:MM_idea-ui_testlib",
      "//tools/idea:execution-openapi",
      "//tools/idea:smRunner",
      "//tools/idea:smRunner_testlib",
      "//tools/idea:junit",
      "//tools/idea:junit_testlib",
      "//tools/idea:MM_maven2-server-impl",
      "//tools/idea:MM_maven2-server-impl_testlib",
      "//tools/idea:java-impl",
      "//tools/idea:properties",
      "//tools/idea:properties_testlib",
      "//tools/idea:lib/xpp3-1.1.4-min",
      "//tools/idea:testng",
      "//tools/idea:testng_testlib",
      "//tools/adt/idea:android-common",
      "//tools/adt/idea:android-common_testlib",
      "//tools/adt/idea:android-rt",
      "//tools/idea:java-indexing-api",
      "//tools/idea:jps-builders",
      "//tools/idea:jps-builders_testlib",
      "//tools/base/draw9patch:draw9patch",
      "//tools/base/draw9patch:draw9patch_testlib",
      "//tools/base/build-system:manifest-merger",
      "//tools/base/build-system:manifest-merger_testlib",
      "//prebuilts/tools/common/m2:repository/org/freemarker/freemarker/2.3.20/freemarker-2.3.20",
      "//tools/base/asset-studio:assetstudio",
      "//tools/base/asset-studio:assetstudio_testlib",
      "//tools/idea:platform-api",
      "//tools/idea:eclipse",
      "//tools/idea:eclipse_testlib",
      "//tools/adt/idea:android/lib/GoogleFeedback",
      "//tools/idea:external-system-api",
      "//tools/idea:lib/gson-2.5",
      "//tools/idea:jetgroovy",
      "//tools/idea:jetgroovy_testlib",
      "//tools/base/perflib:perflib",
      "//tools/base/perflib:perflib_testlib",
      "//tools/idea:lib/swingx-core-1.6.2",
      "//tools/idea:util",
      "//tools/base/testutils:testutils",
      "//tools/base/testutils:testutils_testlib",
      "//tools/idea:lib/ecj-4.5.2",
      "//tools/idea:properties-psi-api",
      "//tools/idea:lib/asm4-all",
      "//tools/adt/idea:android/lib/fest-assert-1.5.0-SNAPSHOT",
      "//tools/adt/idea:android/lib/fest-reflect-2.0-SNAPSHOT",
      "//tools/adt/idea:android/lib/fest-swing-1.4-SNAPSHOT",
      "//tools/adt/idea:android/lib/fest-util-1.3.0-SNAPSHOT",
      "//tools/adt/idea:android/lib/jsr305-1.3.9",
      "//tools/idea:platform-main",
      "//tools/idea:bootstrap",
      "//tools/idea:lib/jcip-annotations",
      "//prebuilts/tools/common/m2:repository/org/mockito/mockito-all/1.9.5/mockito-all-1.9.5",
      "//tools/base/rpclib:rpclib",
      "//tools/adt/idea:adt-ui",
      "//tools/adt/idea:adt-ui_testlib",
      "//tools/adt/idea:android/lib/spantable",
      "//tools/idea:lib/dev/easymock",
      "//tools/idea:lib/dev/easymockclassextension",
      "//tools/idea:lib/dev/jmock-2.5.1",
      "//tools/idea:lib/dev/jmock-junit4-2.5.1",
      "//tools/idea:lib/dev/jmock-legacy-2.5.1",
      "//tools/idea:lib/dev/objenesis-1.0",
      "//tools/idea:lib/hamcrest-library-1.3",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28-sources",
      "//tools/adt/idea:android/lib/jgraphx-3.4.0.1",
      "//tools/base/repository:repository",
      "//tools/base/repository:repository_testlib",
      "//tools/base/instant-run:instant-run-client",
      "//tools/base/instant-run:instant-run-client_testlib",
      "//tools/base/instant-run:instant-run-common",
      "//tools/idea:lib/jna",
      "//tools/idea:lib/jna-platform",
      "//tools/data-binding:db-compiler",
      "//tools/data-binding:db-compiler_testlib",
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
      "//tools/idea:lang-api",
      "//tools/idea:lang-api_testlib",
      "//tools/idea:gradle",
      "//tools/idea:gradle-tests",
      "//tools/idea:gradle-tests_testlib",
      "//tools/adt/idea:layoutlib",
      "//tools/sherpa:sherpa-solver",
      "//tools/adt/idea:sherpa-ui",
      "//tools/adt/idea:android/lib/dexlib2-2.0.8-dev",
      "//tools/adt/idea:android/lib/dexlib2-util-2.0.8-dev",
      "//tools/base/apkparser:binary-resources",
      "//tools/base/apkparser:binary-resources_testlib",
      "//tools/base/pixelprobe:pixelprobe",
      "//tools/base/pixelprobe:pixelprobe_testlib",
      "//tools/base/bazel:prebuilts/studio-profiler-grpc-1.0-jarjar",
      "//tools/analytics-library:analytics-tracker",
      "//tools/analytics-library:analytics-tracker_testlib",
      "//tools/analytics-library:analytics-protos",
      "//tools/analytics-library:analytics-shared",
      "//tools/analytics-library:analytics-shared_testlib",
      "//tools/base/common:common",
      "//tools/base/common:common_testlib",
    ],
  exports = [
      "//tools/adt/idea:android",
      "//tools/adt/idea:android-common",
      "//tools/adt/idea:android-common_testlib",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_import(
  name = "android/lib/fest-swing-1.4-SNAPSHOT",
  jars = [
      "android/lib/fest-swing-1.4-SNAPSHOT.jar",
    ],
  visibility = ["//visibility:public"],
)

java_library(
  name = "android-common_testlib",
  srcs = glob([
      "android/common/testSrc/**/*.java",
    ]),
  resource_strip_prefix = "tools/adt/idea/android-common_testlib.resources",
  resources = [
      "//tools/adt/idea:android-common_testlib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/adt/idea:android-common",
      "//tools/idea:util",
      "//tools/adt/idea:android/lib/jarutils",
      "//tools/idea:lib/nanoxml-2.2.3",
      "//tools/base/common:common",
      "//tools/base/common:common_testlib",
      "//tools/base/ddmlib:ddmlib",
      "//tools/base/ddmlib:ddmlib_testlib",
      "//tools/base/device_validator:dvlib",
      "//tools/base/device_validator:dvlib_testlib",
      "//tools/base/layoutlib-api:layoutlib-api",
      "//tools/base/layoutlib-api:layoutlib-api_testlib",
      "//tools/base/lint:lint-api",
      "//tools/base/lint:lint-checks",
      "//tools/base/ninepatch:ninepatch",
      "//tools/base/ninepatch:ninepatch_testlib",
      "//tools/base/sdk-common:sdk-common",
      "//tools/base/sdk-common:sdk-common_testlib",
      "//tools/base/sdklib:sdklib",
      "//tools/base/sdklib:sdklib_testlib",
      "//tools/base/testutils",
    ],
  exports = [
      "//tools/adt/idea:android-common",
      "//tools/adt/idea:android/lib/jarutils",
      "//tools/base/common:common",
      "//tools/base/common:common_testlib",
      "//tools/base/ddmlib:ddmlib",
      "//tools/base/ddmlib:ddmlib_testlib",
      "//tools/base/device_validator:dvlib",
      "//tools/base/device_validator:dvlib_testlib",
      "//tools/base/layoutlib-api:layoutlib-api",
      "//tools/base/layoutlib-api:layoutlib-api_testlib",
      "//tools/base/lint:lint-api",
      "//tools/base/lint:lint-checks",
      "//tools/base/ninepatch:ninepatch",
      "//tools/base/ninepatch:ninepatch_testlib",
      "//tools/base/sdk-common:sdk-common",
      "//tools/base/sdk-common:sdk-common_testlib",
      "//tools/base/sdklib:sdklib",
      "//tools/base/sdklib:sdklib_testlib",
    ],
  javacopts = ["-extra_checks:off"],
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

fileset(
  name = "android.res",
  srcs = glob([
      "android/resources/**/*",
      "android/src/**/*",
      "android/gen/**/*",
      "designer/src/**/*",
      "designer/resources/**/*",
    ],
    exclude = [
      "**/* *",
      "**/*.java",
      "**/*.kt",
      "**/*.groovy",
      "**/*$*",
      "**/.DS_Store",
    ]),
  mappings = {
      "android/gen": "android.resources",
      "android/src": "android.resources",
      "designer/resources": "android.resources",
      "android/resources": "android.resources",
      "designer/src": "android.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
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

java_library(
  name = "android-rt",
  srcs = glob([
      "android/rt/src/**/*.java",
    ]),
  resource_strip_prefix = "tools/adt/idea/android-rt.resources",
  resources = [
      "//tools/adt/idea:android-rt.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/idea:annotations",
      "//tools/idea:util-rt",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

java_library(
  name = "adt-ui_testlib",
  srcs = glob([
      "adt-ui/src/test/java/**/*.java",
    ]),
  resource_strip_prefix = "tools/adt/idea/adt-ui_testlib.resources",
  resources = [
      "//tools/adt/idea:adt-ui_testlib.res",
    ],
  deps = [
      "@local_jdk//:langtools-neverlink",
      "//tools/adt/idea:adt-ui",
      "//tools/base/annotations:android-annotations",
      "//tools/base/testutils:testutils",
      "//tools/idea:lib/trove4j",
      "//tools/idea:platform-api",
      "//tools/idea:MM_RegExpSupport",
      "//tools/idea:MM_RegExpSupport_testlib",
      "//tools/idea:lib/hamcrest-core-1.3",
      "//tools/idea:lib/junit-4.12",
      "//prebuilts/tools/common/m2:repository/com/google/truth/truth/0.28/truth-0.28",
    ],
  exports = [
      "//tools/adt/idea:adt-ui",
    ],
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)

fileset(
  name = "adt-ui.res",
  srcs = glob([
      "adt-ui/src/main/java/**/*",
    ],
    exclude = [
      "**/* *",
      "**/*.java",
      "**/*.kt",
      "**/*.groovy",
      "**/*$*",
      "**/.DS_Store",
    ]),
  mappings = {
      "adt-ui/src/main/java": "adt-ui.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_import(
  name = "android/lib/fest-assert-1.5.0-SNAPSHOT",
  jars = [
      "android/lib/fest-assert-1.5.0-SNAPSHOT.jar",
    ],
  visibility = ["//visibility:public"],
)

fileset(
  name = "android_testlib.res",
  srcs = glob([
      "android/testSrc/**/*",
      "designer/testSrc/**/*",
    ],
    exclude = [
      "**/* *",
      "**/*.java",
      "**/*.kt",
      "**/*.groovy",
      "**/*$*",
      "**/.DS_Store",
    ]),
  mappings = {
      "designer/testSrc": "android_testlib.resources",
      "android/testSrc": "android_testlib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

fileset(
  name = "android-rt.res",
  srcs = glob([
      "android/rt/src/**/*",
    ],
    exclude = [
      "**/* *",
      "**/*.java",
      "**/*.kt",
      "**/*.groovy",
      "**/*$*",
      "**/.DS_Store",
    ]),
  mappings = {
      "android/rt/src": "android-rt.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

fileset(
  name = "android-common_testlib.res",
  srcs = glob([
      "android/common/testResources/**/*",
    ],
    exclude = [
      "**/* *",
      "**/*.java",
      "**/*.kt",
      "**/*.groovy",
      "**/*$*",
      "**/.DS_Store",
    ]),
  mappings = {
      "android/common/testResources": "android-common_testlib.resources",
    },
  deps = [
      "@local_jdk//:langtools-neverlink",
    ],
)

java_test(
  name = "android_tests",
  srcs = glob([
    ]),
  runtime_deps = [
      ":android_testlib",
      "//tools/base/testutils:testutils",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=android_testlib.jar",
    ],
  test_class = "com.android.testutils.JarTestSuite",
  javacopts = ["-extra_checks:off"],
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

java_test(
  name = "android-common_tests",
  srcs = glob([
    ]),
  runtime_deps = [
      ":android-common_testlib",
      "//tools/base/testutils:testutils",
    ],
  jvm_flags = [
      "-Dtest.suite.jar=android-common_testlib.jar",
    ],
  test_class = "com.android.testutils.JarTestSuite",
  javacopts = ["-extra_checks:off"],
  visibility = ["//visibility:public"],
)
