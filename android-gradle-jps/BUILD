load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "android-gradle-jps",
    srcs = ["src"],
    tags = ["managed"],
    test_srcs = ["testSrc"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/jps/jps-builders[module]",
        "//tools/idea/jps/model-api:jps-model-api[module]",
        "//tools/idea/jps/model-impl:jps-model-impl[module]",
        "//tools/idea/jps/model-serialization:jps-model-serialization[module]",
        "//tools/adt/idea/jps-plugin:android-jps-plugin[module]",
        "//tools/adt/idea/common:android-common[module]",
        "//tools/adt/idea/android[module]",
        "//tools/idea/plugins/gradle/lib:gradle-tooling-api-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-core-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-model-core-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-model-groovy-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-messaging-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-wrapper-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-base-services-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-base-services-groovy-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-native-3.2",
        "//tools/idea/plugins/gradle/lib:gradle-resources-3.2",
        "//tools/idea/plugins/gradle[module]",
    ],
)
