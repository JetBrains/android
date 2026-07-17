load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

# managed by go/iml_to_build
jvm_import(
    name = "play_findings_proto",
    jars = ["//tools/adt/idea/app-quality-insights/play-findings/client/proto:play_findings_proto.jar"],
    visibility = ["//visibility:public"],
)

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.play-findings.client",
    srcs = ["src"],
    iml_files = ["intellij.android.app-quality-insights.play-findings.client.iml"],
    test_class = "com.android.tools.idea.findings.client.PlayFindingsClientTestSuite",
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/.idea/libraries:mockito",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/adt-testutils:intellij.android.adt.testutils",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "//tools/adt/idea/app-quality-insights/client:intellij.android.app-quality-insights.client",
        "//tools/adt/idea/app-quality-insights/play-findings/client:play_findings_proto",
        "//tools/vendor/google/play-client/findings:google-play-client-findings",
        "//tools/adt/idea/.idea/libraries:protobuf-kotlin",
        "//tools/adt/idea/.idea/libraries:protobuf-java-util",
    ],
)
