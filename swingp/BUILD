load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:utils.bzl", "java_jarjar")

# managed by go/iml_to_build [unb]
iml_module(
    name = "intellij.android.adt.swingp",
    srcs = ["src/main/java"],
    iml_files = ["intellij.android.adt.swingp.iml"],
    lint_baseline = "//tools/base/lint:studio-checks/empty_baseline.xml",
    project = "unb",
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/.idea/libraries:unb.Guava",
        "//tools/adt/idea/.idea/libraries:unb.truth[test]",
        "//tools/adt/idea/.idea/libraries:unb.mockito[test]",
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
    deps = [
        "//prebuilts/tools/common/m2/repository/org/jetbrains/intellij/deps/asm-all/8.0.1:jar",
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/base/third_party:org.apache.httpcomponents_httpclient",
        "//tools/base/third_party:org.jetbrains_annotations",
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
