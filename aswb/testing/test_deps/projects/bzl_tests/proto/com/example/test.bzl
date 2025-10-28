"""Rules for testing third_party/intellij/bazel/plugin/aspect/build_dependencies_blaze_deps.bzl with java target."""

load("@rules_testing//lib:analysis_test.bzl", "analysis_test", _test_suite = "test_suite")
load("//bzl_tests:check_utils.bzl", "label_info_factory")
load("//bzl_tests:test_fixture.bzl", "TargetInfo")
load(":check_utils.bzl", "java_proto_info_factory")

JAVA_PROTO_LIBRARY_TARGET = "java_proto"
JAVA_LITE_PROTO_LIBRARY_TARGET = "java_proto_lite"
TEST_TARGET_PACKAGE = "bzl_tests/proto/com/example"

def _java_proto_library_test(name, **test_kwargs):
    analysis_test(name = name, impl = _java_proto_library_test_impl, target = ":java_proto_library_test_fixture", **test_kwargs)

def _java_proto_library_test_impl(env, target):
    actual = env.expect.that_struct(
        target[TargetInfo],
        attrs = dict(
            label = label_info_factory,
            java_proto_info = java_proto_info_factory,
        ),
    )
    actual.label().equals("//{}:{}".format(TEST_TARGET_PACKAGE, JAVA_PROTO_LIBRARY_TARGET))
    actual.java_proto_info().contains_exactly(
        struct(
            proto_source_jars = ["*.jar"],
        ),
    )

def _java_lite_proto_library_test(name, **test_kwargs):
    analysis_test(name = name, impl = _java_lite_proto_library_test_impl, target = ":java_lite_proto_library_test_fixture", **test_kwargs)

def _java_lite_proto_library_test_impl(env, target):
    actual = env.expect.that_struct(
        target[TargetInfo],
        attrs = dict(
            label = label_info_factory,
            java_proto_info = java_proto_info_factory,
        ),
    )
    actual.label().equals("//{}:{}".format(TEST_TARGET_PACKAGE, JAVA_LITE_PROTO_LIBRARY_TARGET))
    actual.java_proto_info().contains_exactly(
        struct(
            proto_source_jars = ["*.jar"],
        ),
    )

def test_suite(name):
    _test_suite(
        name = name,
        tests = [
            _java_proto_library_test,
            _java_lite_proto_library_test,
        ],
    )
