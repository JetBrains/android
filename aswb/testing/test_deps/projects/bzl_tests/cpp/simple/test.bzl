"""Rules for testing third_party/intellij/bazel/plugin/aspect/build_dependencies_blaze_deps.bzl with java target."""

load("@rules_testing//lib:analysis_test.bzl", "analysis_test", _test_suite = "test_suite")
load("//bzl_tests:check_utils.bzl", "label_info_factory", "target_factory")
load("//bzl_tests:test_fixture.bzl", "TargetInfo")
load(":check_utils.bzl", "cc_toolchain_info_factory", "compilation_context_factory")

CC_LIBRARY_TARGET = "simple_lib"
CC_BINARY_TARGET = "simple"
TEST_TARGET_PACKAGE = "bzl_tests/cpp/simple"
BAZEL_OUT_FASTBUILD = "bazel-out/k8-fastbuild/bin"
CC_TOOLCHAIN_TARGET = "@rules_cc//cc:current_cc_toolchain"

def _cc_library_test(name, **test_kwargs):
    analysis_test(name = name, impl = _cc_library_test_impl, target = ":cc_library_test_fixture", **test_kwargs)

def _cc_library_test_impl(env, target):
    actual = env.expect.that_struct(
        target[TargetInfo],
        attrs = dict(
            label = label_info_factory,
            toolchain_target = target_factory,
            compilation_context = compilation_context_factory,
        ),
    )
    actual.label().equals("//{}:{}".format(TEST_TARGET_PACKAGE, CC_LIBRARY_TARGET))
    actual.compilation_context().contains_exactly(
        struct(
            defines = ["VERSION2"],
            direct_headers = ["{}/simple/simple.h".format(TEST_TARGET_PACKAGE)],
            direct_private_headers = [],
            direct_public_headers = ["{}/simple/simple.h".format(TEST_TARGET_PACKAGE)],
            direct_textual_headers = ["{}/simple/simple_textual.h".format(TEST_TARGET_PACKAGE)],
            external_includes = [],
            framework_includes = [],
            headers = ["{}/simple/simple.h".format(TEST_TARGET_PACKAGE), "{}/simple/simple_textual.h".format(TEST_TARGET_PACKAGE)],
            includes = [],
            local_defines = [],
            quote_includes = [".", "{}".format(BAZEL_OUT_FASTBUILD)],
            system_includes = ["{}/foo/bar".format(TEST_TARGET_PACKAGE), "{}/{}/foo/bar".format(BAZEL_OUT_FASTBUILD, TEST_TARGET_PACKAGE)],
            validation_artifacts = [],
        ),
    )
    actual.toolchain_target().equals(CC_TOOLCHAIN_TARGET)

def _cc_binary_test(name, **test_kwargs):
    analysis_test(name = name, impl = _cc_binary_test_impl, target = ":cc_binary_test_fixture", **test_kwargs)

def _cc_binary_test_impl(env, target):
    actual = env.expect.that_struct(
        target[TargetInfo],
        attrs = dict(
            label = label_info_factory,
            toolchain_target = target_factory,
            compilation_context = compilation_context_factory,
            cc_toolchain_info = cc_toolchain_info_factory,
        ),
    )
    actual.label().equals("//{}:{}".format(TEST_TARGET_PACKAGE, CC_BINARY_TARGET))
    actual.compilation_context().contains_exactly(
        struct(
            defines = [],
            direct_headers = [],
            direct_private_headers = [],
            direct_public_headers = [],
            direct_textual_headers = [],
            external_includes = [],
            framework_includes = [],
            headers = [],
            includes = [],
            local_defines = [],
            quote_includes = [".", "{}".format(BAZEL_OUT_FASTBUILD), "external/rules_cc+", "{}/external/rules_cc+".format(BAZEL_OUT_FASTBUILD), "external/bazel_tools", "{}/external/bazel_tools".format(BAZEL_OUT_FASTBUILD)],
            system_includes = [],
            validation_artifacts = [],
        ),
    )
    actual.toolchain_target().equals(CC_TOOLCHAIN_TARGET)

def test_suite(name):
    _test_suite(
        name = name,
        tests = [
            _cc_library_test,
            _cc_binary_test,
        ],
    )
