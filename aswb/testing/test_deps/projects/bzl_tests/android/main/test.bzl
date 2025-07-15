"""Rules for testing third_party/intellij/bazel/plugin/aspect/build_dependencies_blaze_deps.bzl with java target."""

load("@rules_testing//lib:analysis_test.bzl", "analysis_test", _test_suite = "test_suite")
load("//bzl_tests:check_utils.bzl", "label_info_factory")
load("//bzl_tests:test_fixture.bzl", "TargetInfo")
load(":check_utils.bzl", "android_info_factory")

ANDROID_LIBRARY_TARGET = "basic_lib"
ANDROID_BINARY_TARGET = "basic_app"
TEST_TARGET_PACKAGE = "bzl_tests/android/main"

def _android_library_test(name, **test_kwargs):
    analysis_test(name = name, impl = _android_library_test_impl, target = ":android_library_test_fixture", **test_kwargs)

def _android_library_test_impl(env, target):
    actual = env.expect.that_struct(
        target[TargetInfo],
        attrs = dict(
            label = label_info_factory,
            android_info = android_info_factory,
        ),
    )
    actual.label().equals("//{}/java/com/basicapp:{}".format(TEST_TARGET_PACKAGE, ANDROID_LIBRARY_TARGET))
    actual.android_info().contains_exactly(
        struct(
            aar = "*.aar",
            java_package = "com.basicapp",
            manifest = "*.xml",
            idl_class_jar = "",
            idl_generated_java_files = [],
        ),
    )

def _android_binary_test(name, **test_kwargs):
    analysis_test(name = name, impl = _android_binary_test_impl, target = ":android_binary_test_fixture", **test_kwargs)

def _android_binary_test_impl(env, target):
    actual = env.expect.that_struct(
        target[TargetInfo],
        attrs = dict(
            label = label_info_factory,
            android_info = android_info_factory,
        ),
    )
    actual.label().equals("//{}:{}".format(TEST_TARGET_PACKAGE, ANDROID_BINARY_TARGET))
    actual.android_info().contains_exactly(
        struct(
            aar = "",
            java_package = None,
            manifest = "*.xml",
            idl_class_jar = "",
            idl_generated_java_files = [],
        ),
    )

def test_suite(name):
    _test_suite(
        name = name,
        tests = [
            _android_library_test,
            _android_binary_test,
        ],
    )
