#
# Description: Builds ASwB for blaze and bazel
#

load("@bazel_skylib//rules:build_test.bzl", "build_test")
load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "ASWB_PACKAGES_VISIBILITY",
    "ASWB_PLUGIN_PACKAGES_VISIBILITY",
    "ASWB_SUBPACKAGES_VISIBILITY",
    "DEFAULT_TEST_VISIBILITY",
    "TEST_ASWB_SUBPACKAGES_VISIBILITY",
)
load(
    "//tools/adt/idea/aswb/build_defs:build_defs.bzl",
    "combine_visibilities",
    "intellij_plugin",
    "intellij_plugin_library",
    "optional_plugin_xml",
    "plugin_deploy_zip",
    "repackaged_files",
    "stamped_plugin_xml",
)

# BEGIN-REPO
load(
    "//tools/adt/idea/aswb/build_defs:restrictions.bzl",
    "ALLOWED_EXTERNAL_TEST_DEPENDENCIES",
    "EXISTING_EXTERNAL_TEST_VIOLATIONS",
    "validate_test_dependencies",
)

# END-REPO
load(
    "//tools/adt/idea/aswb/testing:test_defs.bzl",
    "intellij_integration_test_suite",
    "intellij_unit_test_suite",
)
load("//tools/adt/idea/studio:studio.bzl", "intellij_plugin_import")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

optional_plugin_xml(
    name = "optional_ndk_xml",
    module = "com.android.tools.ndk",
    plugin_xml = "src/META-INF/ndk_contents.xml",
)

intellij_plugin_library(
    name = "plugin_library",
    optional_plugin_xmls = [":optional_ndk_xml"],
    plugin_xmls = ["src/META-INF/aswb.xml"],
    visibility = ASWB_PLUGIN_PACKAGES_VISIBILITY,
    deps = [":aswb_lib"],
)

stamped_plugin_xml(
    name = "stamped_plugin_xml",
    changelog_file = "//tools/adt/idea/aswb:changelog",
    description_file = "src/META-INF/description.html",
    plugin_id = "com.google.idea.bazel.aswb",
    plugin_name = "Bazel for Android Studio",
    stamp_since_build = True,
    stamp_until_build = True,
    version_file = ":plugin_version.txt",
)

java_library(
    name = "unit_test_utils",
    testonly = 1,
    srcs = glob(["tests/utils/unit/**/*.java"]),
    visibility = combine_visibilities(TEST_ASWB_SUBPACKAGES_VISIBILITY, DEFAULT_TEST_VISIBILITY),
    deps = [
        ":aswb_lib",
        "//tools/adt/idea/aswb/base",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "@maven//:com.google.guava.guava",
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
        "//tools/adt/idea/aswb/cpp",
        "//tools/adt/idea/aswb/java",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/testing:lib",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",  # unuseddeps: keep
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "aswb_lib",
    srcs = glob(
        [
            "src/**/*.java",
            "src/com/google/idea/blaze/android/run/binary/tasks/*.java",
        ],
    ),
    resources = glob(["resources/**/*"]),
    visibility = combine_visibilities(
        ASWB_SUBPACKAGES_VISIBILITY,
        TEST_ASWB_SUBPACKAGES_VISIBILITY,
    ),
    deps = [
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base/src/com/google/idea/blaze/base/command/buildresult/bepparser",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/cpp",
        "//tools/adt/idea/aswb/java",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/querysync",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/third_party/java/auto_value",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",  # unuseddeps: keep
        "//tools/vendor/google/aswb/plugin_api:kotlin",
    ],
)

intellij_unit_test_suite(
    name = "unit_tests",
    srcs = glob(
        ["tests/unittests/**/*.java"],
        exclude = [
            "tests/unittests/com/google/idea/blaze/android/sync/model/idea/BlazeClassJarProviderTest.java",  # b/145809318
        ],
    ),
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.blaze.android",
    visibility = DEFAULT_TEST_VISIBILITY,
    deps = [
        ":aswb_lib",
        ":unit_test_utils",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/adt/idea/aswb/java",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/shared",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/testing:lib",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "@maven//:com.google.guava.guava",
    ],
)

test_suite(
    name = "integration_tests",
    tests = [
        #b/139825934 ":NdkDependenciesTest",
        ":normal_integration_tests",
    ],
    visibility = DEFAULT_TEST_VISIBILITY,
)

jvm_import(
    name = "aswb_plugin_import",
    testonly = 1,
    jars = [
        ":aswb_bazel",
    ],
)

intellij_plugin_import(
    name = "com.google.idea.bazel.aswb",
    testonly = 1,
    overwrite_plugin_version = True,
    target_dir = "aswb",
    exports = [":aswb_plugin_import"],
)

intellij_integration_test_suite(
    name = "normal_integration_tests",
    srcs = glob(
        [
            "tests/integrationtests/com/google/idea/blaze/android/**/*.java",
        ],
        exclude = [
            "tests/integrationtests/com/google/idea/blaze/android/plugin/NdkDependenciesTest.java",  # Extracted to separate target
            "tests/integrationtests/com/google/idea/blaze/android/functional/AswbMergedManifestTest.java",  #b/222322106
        ],
    ),
    data = [
        "testdata/golden.png",
        "testdata/ic_banner.png",
        "tools/adt/idea/android/annotations/empty_file",
    ],
    plugins = [
        ":com.google.idea.bazel.aswb",
        "//tools/adt/idea/studio:com.android.tools.ndk",
        "//tools/adt/idea/studio:com.android.tools.design",
        "//tools/adt/idea/studio:org.jetbrains.android",
    ],
    required_plugins = "com.google.idea.bazel.aswb,com.android.tools.ndk",
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.blaze.android",
    deps = [
        ":aswb_lib",
        ":integration_test_utils",
        ":unit_test_utils",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:integration_test_utils",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/common/experiments",
        "//tools/adt/idea/aswb/common/experiments:unit_test_utils",
        "//tools/adt/idea/aswb/cpp",
        "//tools/adt/idea/aswb/java",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/shared:artifact",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
        "@maven//:com.google.guava.guava",
    ],
)

intellij_integration_test_suite(
    name = "NdkDependenciesTest",
    srcs = ["tests/integrationtests/com/google/idea/blaze/android/plugin/NdkDependenciesTest.java"],
    required_plugins = "com.google.idea.bazel.aswb",
    tags = ["noci:studio-win"],
    test_package_root = "com.google.idea.blaze.android",
    runtime_deps = [
        ":aswb_bazel",
        "//tools/adt/idea/aswb/cpp",
        "//tools/adt/idea/aswb/java",
    ],
    deps = [
        ":aswb_lib",
        "//tools/adt/idea/aswb/base",
        "//tools/adt/idea/aswb/base:integration_test_utils",
        "//tools/adt/idea/aswb/base:unit_test_utils",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:plugin_api_for_tests",
        "//tools/vendor/google/aswb/plugin_api:test_libs",
    ],
)

intellij_plugin(
    name = "aswb_bazel",
    plugin_xml = ":stamped_plugin_xml",
    tags = [
        "incomplete-deps",  # remove this suppression and add any missing deps, see go/java-import-deps-checking-lsc
    ],
    deps = [
        ":plugin_library",
        "//tools/adt/idea/aswb/base:plugin_library",
        "//tools/adt/idea/aswb/cpp:plugin_library",
        "//tools/adt/idea/aswb/dart:plugin_library",
        "//tools/adt/idea/aswb/java:plugin_library",
        "//tools/adt/idea/aswb/kotlin:plugin_library",
        "//tools/adt/idea/aswb/skylark:plugin_library",
        "//tools/adt/idea/aswb/terminal:plugin_library",
    ],
)

repackaged_files(
    name = "plugin_jar",
    srcs = [":aswb_bazel"],
    prefix = "aswb/lib",
)

repackaged_files(
    name = "aspect_directory",
    srcs = ["//tools/adt/idea/aswb/aspect:aspect_files"],
    # Build aspect tools with Java 8 so they can work with any Java version in users workspace.
    java_copts = [
        "-source",
        "8",
        "-target",
        "8",
    ],
    prefix = "aswb/aspect",
    strip_prefix = "/tools/adt/idea/aswb/aspect",
    visibility = ASWB_PACKAGES_VISIBILITY,
)

plugin_deploy_zip(
    name = "aswb_bazel_zip",
    srcs = [
        ":aspect_directory",
        ":plugin_jar",
    ],
    visibility = ["//visibility:public"],
    zip_filename = "aswb_bazel.zip",
)

build_test(
    name = "aswb_bazel_build_test",
    targets = [
        ":aswb_bazel_zip",
    ],
)

genrule(
    name = "create_empty_annotations_folder",
    outs = ["tools/adt/idea/android/annotations/empty_file"],
    cmd = "echo \"No contents\" > $@",
)

# BEGIN-REPO
validate_test_dependencies(
    name = "validate_test_dependencies",
    allowed_external_dependencies = ALLOWED_EXTERNAL_TEST_DEPENDENCIES,
    existing_external_violations = EXISTING_EXTERNAL_TEST_VIOLATIONS,
    deps = [
        "//tools/adt/idea/aswb:aswb_tests",
        "//tools/adt/idea/aswb/aswb:aswb_bazel_build_test",
        "//tools/adt/idea/aswb/common/experiments:unit_tests",
        "//tools/adt/idea/aswb/common/ui/properties:unit_tests",
        "//tools/adt/idea/aswb/common/util:CommandLineTaskTest",
        "//tools/adt/idea/aswb/querysync:cli_tools_build_test",
        "//tools/adt/idea/aswb/shared:tests",
    ],
)
# END-REPO

# Dump the plugin version to a file to be used in stamping the plugin xml
genrule(
    name = "plugin_version",
    outs = ["plugin_version.txt"],
    cmd = """
# bazel-out/stable-status.txt is created when stamp = True
# Value of BUILD_EMBED_LABEL key comes from --embed_label on the command line
version="$$(grep BUILD_EMBED_LABEL bazel-out/stable-status.txt | cut -d ' ' -f 2)"
# Set a default version if --embed_label was not specified
if [ -z "$$version" ]; then version="99999999"; fi

echo "$$version" > $@
""",
    stamp = True,
)
