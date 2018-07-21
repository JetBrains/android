load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.adt.swingp",
    srcs = ["src/main/java"],
    iml_files = ["intellij.android.adt.swingp.iml"],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/.idea/libraries:truth[test]",
    ],
)

java_binary(
    name = "swing-instrumenter",
    srcs = glob(
        ["src/main/java/com/android/tools/swingp/**"],
        exclude = [
            "src/main/java/com/android/tools/swingp/**/.*",
        ],
    ),
    create_executable = 0,
    deploy_manifest_lines = [
        "Premain-Class: com.android.tools.swingp.instrumenter.Agent",
        "Can-Retransform-Classes: true",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//tools/base/third_party:com.google.code.gson_gson",
        "//tools/base/third_party:com.google.guava_guava",
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/.idea/libraries:jetbrains-annotations-java5",
    ],
)
