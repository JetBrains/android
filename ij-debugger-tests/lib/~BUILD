load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

kotlin_library(
    name = "art-attacher",
    testonly = True,
    srcs = ["ArtAttacher.kt"],
    # Disabled b/c it was causing problems w/ the coverage build, and we probably don't care about coverage of this anyways
    coverage_baseline_enabled = False,
    lint_enabled = False,
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "@platforms//os:macos": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    visibility = ["//tools/adt/idea/ij-debugger-tests:__pkg__"],
    deps = [
        "//tools/adt/idea/android-kotlin:android-dexer",
        "//tools/adt/idea/debuggers:intellij.android.debuggers",
        "@maven//:junit.junit",
        "@tools_idea//java/debugger/impl",
        "@tools_idea//java/debugger/openapi:java-debugger",
        "@tools_idea//java/debugger/shared",
        "@tools_idea//java/execution/openapi:java-execution",
        "@tools_idea//java/testFramework",
        "@tools_idea//platform/core-api:core",
        "@tools_idea//platform/core-impl",
        "@tools_idea//platform/core-ui",
        "@tools_idea//platform/execution",
        "@tools_idea//platform/extensions",
        "@tools_idea//platform/lang-core",
        "@tools_idea//platform/platform-api:ide",
        "@tools_idea//platform/projectModel-api:projectModel",
        "@tools_idea//platform/testFramework",
        "@tools_idea//platform/util",
        "@tools_idea//platform/util-ex",
        "@tools_idea//plugins/kotlin/jvm-debugger/evaluation",
        "@tools_idea//plugins/kotlin/jvm-debugger/testFramework:testFramework_test_lib",
        "@tools_idea//plugins/kotlin/tests-common:common-tests_test_lib",
        "@tools_idea_lib//:kotlinc-kotlin-compiler-common",
    ],
)

kotlin_library(
    name = "expected-failures-interceptor",
    testonly = True,
    srcs = ["ExpectedFailuresInterceptor.kt"],
    # Disabled b/c it was causing problems w/ the coverage build, and we probably don't care about coverage of this anyways
    coverage_baseline_enabled = False,
    lint_enabled = False,
    target_compatible_with = select({
        "@platforms//os:windows": ["@platforms//:incompatible"],
        "@platforms//os:macos": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    visibility = ["//tools/adt/idea/ij-debugger-tests:__pkg__"],
    deps = [
        "@tools_idea//platform/testFramework/bootstrap:tools-testsBootstrap",
        "@tools_idea_lib//:libraries-junit5-launcher-_j_unit5_launcher",
    ],
)
