load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.compose-ide-plugin",
    srcs = ["src/main/java"],
    iml_files = ["intellij.android.compose-ide-plugin.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    resources = ["src/main/resources"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/.idea/libraries:delight-rhino-sandbox",
        "//tools/idea/.idea/libraries:rhino",
        "//tools/idea/.idea/libraries:netty-handler-proxy",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:kotlin-plugin",
        "//prebuilts/tools/common/m2/repository/androidx/compose/compose-compiler-hosted/0.1.0-dev14:jar",
        "//tools/idea/java/java-psi-api:intellij.java.psi[module]",
        "//tools/idea/platform/core-impl:intellij.platform.core.impl[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/platform/projectModel-api:intellij.platform.projectModel[module]",
    ],
)

# managed by go/iml_to_build [unb]
iml_module(
    name = "unb.intellij.android.compose-ide-plugin",
    srcs = ["src/main/java"],
    iml_files = ["intellij.android.compose-ide-plugin.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    resources = ["src/main/resources"],
    tags = [
        "manual",
        "unb",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:unb.kotlin-stdlib-jdk8",
        "//prebuilts/tools/common/m2/repository/androidx/compose/compose-compiler-hosted/0.1.0-dev14:jar",
    ],
)
