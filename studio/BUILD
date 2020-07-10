load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build [unb]
iml_module(
    name = "unb.studio",
    srcs = ["src"],
    iml_files = ["studio.iml"],
    tags = [
        "manual",
        "unb",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
        "//tools/adt/idea/android-plugin:unb.intellij.android.plugin[module]",
        "//tools/vendor/google/android-apk:unb.android-apk[module]",
        "//tools/adt/idea/compose-ide-plugin:unb.intellij.android.compose-ide-plugin[module]",
        "//tools/adt/idea/layoutlib:unb.intellij.android.layoutlib[module]",
        "//tools/adt/idea/layoutlib-native:unb.intellij.android.layoutlib-native[module]",
        "//tools/vendor/google/android-ndk:unb.android-ndk[module]",
        "//tools/studio/google/cloud/tools/android-studio-plugin:unb.google-cloud-tools-as[module]",
        "//tools/vendor/google/firebase:unb.firebase[module]",
        "//tools/studio/google/cloud/testing/firebase-testing:unb.firebase-testing[module]",
        "//tools/studio/google/appindexing:unb.google-appindexing[module]",
        "//tools/studio/google/cloud/tools/core-plugin:unb.google-cloud-tools-core-as[module]",
        "//tools/studio/google/cloud/tools/google-login-plugin:unb.google-login-as[module]",
        "//tools/studio/google/samples:unb.google-samples[module]",
        "//tools/adt/idea/smali:unb.intellij.android.smali[module]",
        "//tools/studio/google/cloud/testing/test-recorder:unb.test-recorder[module]",
        "//tools/vendor/google/url-assistant:unb.url-assistant[module]",
    ],
)
