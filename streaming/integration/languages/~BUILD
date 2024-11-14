PACKAGE = "com.android.tools.languages"

MANIFEST = "app/src/main/AndroidManifest.xml"

RESOURCES = glob(["app/src/main/res/**"])

android_binary(
    name = "languages",
    srcs = glob(["app/src/main/java/**"]),
    custom_package = PACKAGE,
    javacopts = [
        "-source",
        "8",
        "-target",
        "8",
    ],
    manifest = MANIFEST,
    manifest_values = {
        "minSdkVersion": "33",
        "versionCode": "1",
        "versionName": "0.1",
        "targetSdkVersion": "33",
    },
    resource_configuration_filters = [
        "ar_XB",
        "da",
        "en_XA",
        "es",
        "it",
    ],
    resource_files = RESOURCES,
    target_compatible_with = select({
        "//tools/base/bazel/platforms:windows_host": ["@platforms//:incompatible"],
        "//conditions:default": [],
    }),
    visibility = ["//tools/adt/idea/streaming:__pkg__"],
)

filegroup(
    name = "resource-files",
    srcs = RESOURCES,
    visibility = ["//tools/adt/idea/streaming:__pkg__"],
)
