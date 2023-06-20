load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adt.swingp",
    srcs = ["src/main/java"],
    iml_files = ["intellij.android.adt.swingp.iml"],
    javacopts_from_jps = [
        "--add-exports",
        "java.desktop/sun.java2d=ALL-UNNAMED",
        "--add-exports",
        "java.desktop/sun.awt.image=ALL-UNNAMED",
    ],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:truth[test]",
        "//tools/adt/idea/.idea/libraries:mockito[test]",
    ],
)

java_binary(
    name = "swing-instrumenter",
    srcs = glob(
        include = ["src/main/java/com/android/tools/swingp/**"],
        exclude = ["src/main/java/com/android/tools/swingp/**/.*"],
    ),
    create_executable = 0,
    deploy_manifest_lines = [
        "Agent-Class: com.android.tools.swingp.instrumenter.Agent",
        "Premain-Class: com.android.tools.swingp.instrumenter.Agent",
        "Boot-Class-Path: ./swing-instrumenter_deploy.jarjar.jar",
        "Can-Redefine-Classes: true",
        "Can-Retransform-Classes: true",
    ],
    javacopts = [
        "--add-exports",
        "java.desktop/sun.java2d=ALL-UNNAMED",
        "--add-exports",
        "java.desktop/sun.awt.image=ALL-UNNAMED",
    ],
    deps = [
        "@maven//:com.google.code.gson.gson",
        "@maven//:com.google.guava.guava",
        "@maven//:org.apache.httpcomponents.httpclient",
        "@maven//:org.jetbrains.annotations",
        "@maven//:org.jetbrains.intellij.deps.asm-all",
    ],
)

java_jarjar(
    name = "swing-instrumenter_deploy.jarjar",
    srcs = [
        ":swing-instrumenter_deploy.jar",
    ],
    rules = "jarjar_rules.txt",
    visibility = ["//visibility:public"],
)
