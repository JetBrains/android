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
CC_TOOLCHAIN_TARGET_LABEL = "@@rules_cc+//cc:current_cc_toolchain"
COMPILER = "gcc"
GNU_SYSTEM_NAME = "*"

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
            external_includes = [],
            framework_includes = [],
            headers = ["*"],
            includes = [],
            quote_includes = ["*"],
            system_includes = ["*"],
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
            external_includes = [],
            framework_includes = [],
            includes = ["*"],
            quote_includes = ["*"],
            defines = ["*"],
            headers = ["*"],
            system_includes = ["*"],
        ),
    )
    actual.toolchain_target().equals(CC_TOOLCHAIN_TARGET)

def _cc_toolchain_test(name, **test_kwargs):
    analysis_test(name = name, impl = _cc_toolchain_test_impl, target = ":cc_toolchain_test_fixture", **test_kwargs)

def _cc_toolchain_test_impl(env, target):
    actual = env.expect.that_struct(
        target[TargetInfo],
        attrs = dict(
            label = label_info_factory,
            toolchain_target = target_factory,
            compilation_context = compilation_context_factory,
            cc_toolchain_info = cc_toolchain_info_factory,
        ),
    )
    actual.label().equals(CC_TOOLCHAIN_TARGET)
    actual.compilation_context().contains_exactly(None)
    actual.toolchain_target().contains_exactly(None)
    actual.cc_toolchain_info().contains_exactly(
        struct(
            id = CC_TOOLCHAIN_TARGET_LABEL + "%" + GNU_SYSTEM_NAME,
            compiler_executable = "*",
            cpu = "k8",
            compiler = COMPILER,
            target_name = GNU_SYSTEM_NAME,
            built_in_include_directories = ["*"],
            c_options = ["*"],
            cpp_options = ["*"],
        ),
    )

def test_suite(name):
    _test_suite(
        name = name,
        tests = [
            _cc_library_test,
            _cc_binary_test,
            _cc_toolchain_test,
        ],
    )
