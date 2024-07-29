"""Definitions of dependencies required for old agp tests of different versions and helper macro to set them up."""

load("//tools/adt/idea/adt-testutils:old-agp-test.bzl", "generate_old_agp_tests_from_list")

COMMON_DATA = [
    "//prebuilts/studio/layoutlib:build.prop",
    "//prebuilts/studio/layoutlib/data:framework_res.jar",
    "//prebuilts/studio/layoutlib/data:native_libs",
    "//prebuilts/studio/layoutlib/data/fonts",
    "//prebuilts/studio/layoutlib/data/icu",
    "//prebuilts/studio/sdk:cmake",
    "//prebuilts/studio/sdk:docs",
    "//prebuilts/studio/sdk:licenses",
    "//prebuilts/studio/sdk:ndk",
    "//prebuilts/studio/sdk:platform-tools",
    "//prebuilts/studio/sdk:sources",
    "//tools/adt/idea/android/annotations",
    "//tools/adt/idea/android/lib:sampleData",
    "//tools/adt/idea/android/testData:projects",
    "//tools/adt/idea/android/testData:snapshots",
    "//tools/adt/idea/artwork:device-art-resources",
    "@maven//:org.jetbrains.kotlin.kotlin-gradle-plugin_1.5.21",
]

COMMON_MAVEN_DEPS = [
    ":test_deps",
    "//tools/base/build-system/integration-test:kotlin_gradle_plugin_prebuilts",
    "//tools/base/third_party/kotlin:kotlin-m2repository",
]

AGP_3_1_4 = "3.1.4"
AGP_3_3_2 = "3.3.2"
AGP_3_5 = "3.5.0"
AGP_4_0 = "4.0.0"
AGP_4_1 = "4.1.0"
AGP_4_2 = "4.2.0"
AGP_7_0 = "7.0.0"
AGP_7_1 = "7.1.0"
AGP_7_2 = "7.2.0"
AGP_7_3 = "7.3.0"
AGP_7_4 = "7.4.1"
AGP_8_0 = "8.0.2"
AGP_8_1 = "8.1.0"
AGP_8_2 = "8.2.0"

AGP_MAVEN_REPOS = {
    AGP_3_1_4: ["//tools/base/build-system/previous-versions:3.1.4"],
    AGP_3_3_2: ["//tools/base/build-system/previous-versions:3.3.2"],
    AGP_3_5: ["//tools/base/build-system/previous-versions:3.5.0"],
    AGP_4_0: ["//tools/base/build-system/previous-versions:4.0.0"],
    AGP_4_1: ["//tools/base/build-system/previous-versions:4.1.0"],
    AGP_4_2: ["//tools/base/build-system/previous-versions:4.2.0"],
    AGP_7_0: ["//tools/base/build-system/previous-versions:7.0.0"],
    AGP_7_1: ["//tools/base/build-system/previous-versions:7.1.0"],
    AGP_7_2: ["//tools/base/build-system/previous-versions:7.2.0"],
    AGP_7_3: ["//tools/base/build-system/previous-versions:7.3.0"],
    AGP_7_4: ["//tools/base/build-system/previous-versions:7.4.1"],
    AGP_8_0: ["//tools/base/build-system/previous-versions:8.0.2"],
    AGP_8_1: ["//tools/base/build-system/previous-versions:8.1.0"],
    AGP_8_2: ["//tools/base/build-system/previous-versions:8.2.0"],
}

AGP_DATA = {
    AGP_3_1_4: [
        "//prebuilts/studio/sdk:build-tools/27.0.3",
        "//prebuilts/studio/sdk:platforms/android-32",
    ],
    AGP_3_3_2: [
        "//prebuilts/studio/sdk:build-tools/28.0.3",
        "//prebuilts/studio/sdk:platforms/android-32",
    ],
    AGP_3_5: [
        "//prebuilts/studio/sdk:build-tools/28.0.3",
        "//prebuilts/studio/sdk:platforms/android-28",
        "//prebuilts/studio/sdk:platforms/android-32",
    ],
    AGP_4_0: [
        "//prebuilts/studio/sdk:build-tools/29.0.2",
        "//prebuilts/studio/sdk:platforms/android-32",
    ],
    AGP_4_1: [
        "//prebuilts/studio/sdk:build-tools/29.0.2",
        "//prebuilts/studio/sdk:platforms/android-32",
    ],
    AGP_4_2: [
        "//prebuilts/studio/sdk:build-tools/30.0.2",
        "//prebuilts/studio/sdk:platforms/android-32",
    ],
    AGP_7_0: [
        "//prebuilts/studio/sdk:build-tools/30.0.2",
        "//prebuilts/studio/sdk:platforms/android-32",
    ],
    AGP_7_1: [
        "//prebuilts/studio/sdk:build-tools/30.0.3",
        "//prebuilts/studio/sdk:platforms/android-32",
    ],
    AGP_7_2: [
        "//prebuilts/studio/sdk:build-tools/30.0.3",
        "//prebuilts/studio/sdk:platforms/android-32",
    ],
    AGP_7_3: [
        "//prebuilts/studio/sdk:build-tools/30.0.3",
        "//prebuilts/studio/sdk:platforms/android-34",
    ],
    AGP_7_4: [
        "//prebuilts/studio/sdk:build-tools/30.0.3",
        "//prebuilts/studio/sdk:platforms/android-34",
    ],
    AGP_8_0: [
        "//prebuilts/studio/sdk:build-tools/30.0.3",
        "//prebuilts/studio/sdk:platforms/android-34",
    ],
    AGP_8_1: [
        "//prebuilts/studio/sdk:build-tools/33.0.1",
        "//prebuilts/studio/sdk:platforms/android-34",
    ],
    AGP_8_2: [
        "//prebuilts/studio/sdk:build-tools/34.0.0",
        "//prebuilts/studio/sdk:platforms/android-34",
    ],
}

GRADLE_LATEST = "LATEST"
GRADLE_8_2 = "8.2"
GRADLE_8_0 = "8.0"
GRADLE_7_5 = "7.5"
GRADLE_7_4 = "7.4"
GRADLE_7_3_3 = "7.3.3"
GRADLE_7_2 = "7.2"
GRADLE_7_0_2 = "7.0.2"
GRADLE_6_7_1 = "6.7.1"
GRADLE_6_5 = "6.5"
GRADLE_5_5 = "5.5"
GRADLE_5_3_1 = "5.3.1"

GRADLE_DISTRIBUTIONS = {
    GRADLE_LATEST: ["//tools/base/build-system:gradle-distrib"],
    GRADLE_8_2: ["//tools/base/build-system:gradle-distrib-8.2"],
    GRADLE_8_0: ["//tools/base/build-system:gradle-distrib-8.0"],
    GRADLE_7_5: ["//tools/base/build-system:gradle-distrib-7.5"],
    GRADLE_7_4: ["//tools/base/build-system:gradle-distrib-7.4"],
    GRADLE_7_3_3: ["//tools/base/build-system:gradle-distrib-7.3.3"],
    GRADLE_7_2: ["//tools/base/build-system:gradle-distrib-7.2"],
    GRADLE_7_0_2: ["//tools/base/build-system:gradle-distrib-7.0.2"],
    GRADLE_6_7_1: ["//tools/base/build-system:gradle-distrib-6.7.1"],
    GRADLE_6_5: ["//tools/base/build-system:gradle-distrib-6.5"],
    GRADLE_5_5: ["//tools/base/build-system:gradle-distrib-5.5"],
    GRADLE_5_3_1: ["//tools/base/build-system:gradle-distrib-5.3.1"],
}

def _local_old_agp_test_update_kwargs(
        gradle_version,
        agp_version,
        additional_jdks = [],
        **kwargs):
    jdk_data = []
    if "1.8" in additional_jdks:
        jdk_data.append("//prebuilts/studio/jdk:jdk_1_8")
    if "11" in additional_jdks:
        jdk_data.append("//prebuilts/studio/jdk/jdk11")
    if "17" in additional_jdks:
        jdk_data.append("//prebuilts/studio/jdk:jdk17")

    kwargs.update(
        agp_version = agp_version,
        data = COMMON_DATA + GRADLE_DISTRIBUTIONS[gradle_version] + AGP_DATA[agp_version] + jdk_data,
        gradle_version = gradle_version,
        maven_deps = COMMON_MAVEN_DEPS + AGP_MAVEN_REPOS[agp_version],
        test_class = "com.android.tools.idea.OldAgpTests",
        timeout = "long",
        ignore_other_tests = False,
    )
    return kwargs

def local_generate_old_agp_tests_from_list(name, tests_list):
    generate_old_agp_tests_from_list(
        name = name,
        iml_module = ":intellij.android.old-agp-tests",
        tests_list = [_local_old_agp_test_update_kwargs(**test_kwargs) for test_kwargs in tests_list],
    )
