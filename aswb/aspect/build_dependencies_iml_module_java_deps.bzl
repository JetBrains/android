"""Loads and re-exports ImlModuleInfo test_provider JavaInfo to support iml_module rule"""

load("//tools/base/bazel:bazel.bzl", "ImlModuleInfo")
load(
    ":build_dependencies_java_deps_wrapped.bzl",
    "get_java_info_from_provider",
    "merge_providers",
    _WRAPPED_IDE_JAVA = "IDE_JAVA",
)

def _get_java_info(target, rule):
    if ImlModuleInfo in target:
        return get_java_info_from_provider(merge_providers(
            [target[ImlModuleInfo].main_provider, target[ImlModuleInfo].test_provider],
        ))
    else:
        return _WRAPPED_IDE_JAVA.get_java_info(target, rule)

def union_lists(list1, list2):
    # Use a depset to efficiently handle duplicates.
    return depset(list1 + list2).to_list()

IDE_JAVA = struct(
    srcs_attributes = union_lists(
        ["java_srcs", "java_test_srcs"],
        _WRAPPED_IDE_JAVA.srcs_attributes,
    ),
    get_java_info = _get_java_info,
)
