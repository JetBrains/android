#
# Description: Builds ASwB for blaze and bazel
#

load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "ASWB_PLUGIN_PACKAGES_VISIBILITY",
    "DEFAULT_TEST_VISIBILITY",
    "TEST_ASWB_SUBPACKAGES_VISIBILITY",
)
load(
    "//tools/adt/idea/aswb/build_defs:build_defs.bzl",
    "aswb_library",
    "combine_visibilities",
    "intellij_plugin_library",
    "optional_plugin_xml",
)
load(
    "//tools/adt/idea/aswb/testing:test_defs.bzl",
    "intellij_integration_test_suite",
    "intellij_unit_test_suite",
)

optional_plugin_xml(
    name = "optional_ndk_xml",
    module = "com.android.tools.ndk",
    plugin_xml = "src/META-INF/ndk_contents.xml",
)

intellij_plugin_library(
    name = "aswb",
    optional_plugin_xmls = [":optional_ndk_xml"],
    plugin_xmls = ["src/META-INF/aswb.xml"],
    visibility = ASWB_PLUGIN_PACKAGES_VISIBILITY,
    deps = [":aswb_lib"],
)

java_library(
    name = "unit_test_utils",
    testonly = 1,
    srcs = glob(["tests/utils/unit/**/*.java"]),
    visibility = combine_visibilities(TEST_ASWB_SUBPACKAGES_VISIBILITY, DEFAULT_TEST_VISIBILITY),
    deps = [
        ":aswb_lib",
        "//tools/adt/idea/aswb/base",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
    ],
)

java_library(
    name = "integration_test_utils",
    testonly = 1,
    srcs = glob(["tests/utils/integration/**/*.java"]),
    visibility = TEST_ASWB_SUBPACKAGES_VISIBILITY,
    deps = [
        ":aswb_lib",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:integration_test_utils",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/adt/idea/aswb/cpp:cpp_lib",
        "//tools/adt/idea/aswb/java:java_lib",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/testing:lib",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",  # unuseddeps: keep
        "//tools/vendor/google/aswb/plugin_api:test_libs",
    ],
)

aswb_library(
    name = "aswb_lib",
    srcs = glob(
        [
            "src/**/*.java",
            "src/**/*.kt",
        ],
    ),
    resources = glob(
        [
            "resources/**/*",
            "testres/*",
        ],
    ),
    visibility = [
        ":__subpackages__",
        "//tools/vendor/google3/aswb/java/com/google/devtools/intellij/blaze/plugin/aswb:__subpackages__",
    ],
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base/src/com/google/idea/blaze/base/command/buildresult/bepparser",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/cpp:cpp_lib",
        "//tools/adt/idea/aswb/java:java_lib",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/querysync",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",  # unuseddeps: keep
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(
        [
            "tests/unittests/**/*.java",
            "tests/unittests/**/*.kt",
        ],
        exclude = [
            "tests/unittests/com/google/idea/blaze/android/sync/model/idea/BlazeClassJarProviderTest.java",  # b/145809318
        ],
    ),
    tags = ["noci:studio-win"],
    test_package_root = "com",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":aswb_lib",
        ":unit_test_utils",
        "//tools/adt/idea/aswb/aswb/tests/unittests/com/android/tools/idea/rendering/tokens:helloworld",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/adt/idea/aswb/java:java_lib",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/testing:lib",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
    ],
)

intellij_integration_test_suite(
    name = "normal_integration_tests",
    srcs = glob(
        [
            "tests/integrationtests/com/google/idea/blaze/android/**/*.java",
            "tests/integrationtests/com/google/idea/blaze/android/**/*.kt",
        ],
    ),
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.blaze.android",
    deps = [
        ":aswb_lib",
        ":integration_test_utils",
        ":unit_test_utils",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:integration_test_utils",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/base/src/com/google/idea/blaze/base/command/buildresult/bepparser",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/adt/idea/aswb/cpp:cpp_lib",
        "//tools/adt/idea/aswb/java:java_lib",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common",
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/exception",
        "//tools/adt/idea/aswb/shared/javatests/com/google/idea/blaze/common/artifact:test_utils",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
    ],
)
