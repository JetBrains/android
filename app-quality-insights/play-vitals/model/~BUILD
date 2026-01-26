load("//tools/base/bazel:bazel.bzl", "iml_module")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")
load("//tools/base/bazel:kotlin.bzl", "kotlin_library")

# managed by go/iml_to_build
iml_module(
    name = "intellij.android.app-quality-insights.play-vitals.model",
    srcs = ["src"],
    iml_files = ["intellij.android.app-quality-insights.play-vitals.model.iml"],
    # do not sort: must match IML order
    test_deps = [
        "//tools/adt/idea/.idea/libraries:junit4",
        "//tools/base/testutils:studio.android.sdktools.testutils",
        "//tools/adt/idea/.idea/libraries:truth",
        "//tools/adt/idea/.idea/libraries:jetbrains.kotlinx.coroutines.test",
        "//tools/base/studio-grpc-testutils:studio.studio-grpc-testutils",
        "//tools/adt/idea/.idea/libraries:google-api-client",
        "//tools/adt/idea/.idea/libraries:mockito-kotlin",
        "//tools/adt/idea/.idea/libraries:mockito",
    ],
    test_srcs = ["testSrc"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "@intellij//:intellij-sdk",
        "@intellij//:com.intellij.java",
        "//tools/adt/idea/app-quality-insights/api:intellij.android.app-quality-insights.api",
        "//tools/adt/idea/.idea/libraries:studio-analytics-proto",
        "//tools/adt/idea/app-quality-insights/play-vitals/model:play_vitals_kt_proto",
        "//tools/vendor/google/login:google-login",
        "//tools/base/annotations:studio.android.sdktools.android-annotations",
        "//tools/base/flags:studio.android.sdktools.flags",
        "@intellij//:intellij.libraries.grpc",
        "@intellij//:intellij.libraries.grpc.netty.shaded",
        "//tools/adt/idea/.idea/libraries:protobuf-kotlin",
        "//tools/adt/idea/app-quality-insights/client:intellij.android.app-quality-insights.client",
    ],
)

# managed by go/iml_to_build
jvm_import(
    name = "play_vitals_kt_proto",
    jars = ["//tools/adt/idea/app-quality-insights/play-vitals/model/proto:play_vitals_kt_proto.jar"],
    visibility = ["//visibility:public"],
)

# Build the model as a standalone library to ensure that unwanted dependencies from intellij-sdk
# are not used.
kotlin_library(
    name = "play-vitals-client-base",
    srcs = glob([
        "src/**/*.kt",
    ]),
    deps = [
        "//prebuilts/tools/common/m2:com.google.protobuf.protobuf-kotlin.3.24.4",
        "//prebuilts/tools/common/m2:io.grpc.grpc-kotlin-stub.1.4.1",
        "//tools/adt/idea/app-quality-insights/client:intellij.android.app-quality-insights.client",
        "//tools/adt/idea/app-quality-insights/play-vitals/model:play_vitals_kt_proto",
        "@maven//:io.grpc.grpc-protobuf",
        "@maven//:io.grpc.grpc-stub",
        "@maven//:org.jetbrains.kotlinx.kotlinx-coroutines-core",
    ],
)
