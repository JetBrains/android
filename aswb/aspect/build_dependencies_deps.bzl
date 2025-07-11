"""Loads and re-exports dependencies of build_dependencies.bzl to support different versions of bazel"""

load(
    "@bazel_tools//tools/build_defs/cc:action_names.bzl",
    _CPP_COMPILE_ACTION_NAME = "CPP_COMPILE_ACTION_NAME",
    _C_COMPILE_ACTION_NAME = "C_COMPILE_ACTION_NAME",
)

ZIP_TOOL_LABEL = "@bazel_tools//tools/zip:zipper"

# JAVA

def _get_java_info(target, rule):
    if not JavaInfo in target:
        return None
    p = target[JavaInfo]
    generated_outputs = []
    java_output_compile_jars = []
    for java_output in p.java_outputs:
        if java_output.compile_jar:
            java_output_compile_jars.append(java_output.compile_jar)
        generated_outputs.append(
            struct(
                compile_jdeps = java_output.compile_jdeps,
                generated_source_jar = java_output.generated_source_jar,
                generated_class_jar = java_output.generated_class_jar,
            ),
        )
    return struct(
        compile_jars_depset = p.compile_jars,
        java_output_compile_jars = java_output_compile_jars,
        generated_outputs = generated_outputs,
        transitive_compile_time_jars_depset = p.transitive_compile_time_jars,
        transitive_runtime_jars_depset = p.transitive_runtime_jars,
    )

IDE_JAVA = struct(
    srcs_attributes = ["java_srcs", "java_test_srcs"],
    get_java_info = _get_java_info,
)

# KOTLIN

def _get_dependency_attribute(rule, attr):
    if hasattr(rule.attr, attr):
        to_add = getattr(rule.attr, attr)
        if type(to_add) == "list":
            return [t for t in to_add if type(t) == "Target"]
        elif type(to_add) == "Target":
            return [to_add]
    return []

def _get_followed_kotlin_dependencies(rule):
    deps = []
    if rule.kind in ["kt_jvm_library_helper", "kt_jvm_library", "kt_android_library", "android_library"]:
        deps.extend(_get_dependency_attribute(rule, "_toolchain"))
    if rule.kind in ["kt_jvm_toolchain"]:
        deps.extend(_get_dependency_attribute(rule, "kotlin_libs"))
    return deps

def _get_kotlin_info(target, rule):
    if rule.kind in ["kt_jvm_toolchain"]:
        # Kotlin stdlib is provided through toolchain attributes.
        return struct()
    return None

IDE_KOTLIN = struct(
    srcs_attributes = [
        "kotlin_srcs",
        "kotlin_test_srcs",
        "common_srcs",
    ],
    follow_attributes = [],
    follow_additional_attributes = [
        "_toolchain",
        "kotlin_libs",
    ],
    followed_dependencies = _get_followed_kotlin_dependencies,
    toolchains_aspects = [],
    get_kotlin_info = _get_kotlin_info,
)

# PROTO

_PROTO_TOOLCHAIN_TYPES = [
    "@protobuf//bazel/private:java_toolchain_type",
    "@protobuf//bazel/private:javalite_toolchain_type",
    "@protobuf//bazel/private:proto_toolchain_type",
]

def _get_java_proto_info(target, rule):
    if rule.kind in ["proto_lang_toolchain", "java_rpc_toolchain"]:
        return struct()
    return None

def _get_followed_java_proto_dependencies(rule):
    deps = []
    if rule.kind in ["proto_lang_toolchain", "java_rpc_toolchain"]:
        deps.extend(_get_dependency_attribute(rule, "runtime"))
    if rule.kind in ["_java_grpc_library", "_java_lite_grpc_library"]:
        deps.extend(_get_dependency_attribute(rule, "_toolchain"))
    for proto_toolchain_type in _PROTO_TOOLCHAIN_TYPES:
        if proto_toolchain_type in rule.toolchains:
            deps.extend([rule.toolchains[proto_toolchain_type]])
    return deps

IDE_JAVA_PROTO = struct(
    get_java_proto_info = _get_java_proto_info,
    srcs_attributes = [],
    follow_attributes = ["toolchain", "_toolchain", "runtime"],
    followed_dependencies = _get_followed_java_proto_dependencies,
    toolchains_aspects = _PROTO_TOOLCHAIN_TYPES,
)

# CC

def _get_cc_toolchain_target(rule):
    if hasattr(rule.attr, "_cc_toolchain"):
        return getattr(rule.attr, "_cc_toolchain")
    return None

def _get_cc_toolchain_info(target, ctx):
    if cc_common.CcToolchainInfo not in target:
        return None

    toolchain_info = target[cc_common.CcToolchainInfo]
    cpp_fragment = ctx.fragments.cpp

    # TODO(b/301235884): This logic is not quite right. `ctx` here is the context for the
    #  cc_toolchain target itself, so the `features` and `disabled_features` were using here are
    #  for the cc_toolchain, not the individual targets that this information will ultimately be
    #  used for. Instead, we should attach `toolchain_info` itself to the `DependenciesInfo`
    #  provider, and execute this logic once per top level cc target that we're building, to ensure
    #  that the right features are used.
    feature_config = cc_common.configure_features(
        ctx = ctx,
        cc_toolchain = toolchain_info,
        requested_features = ctx.features,
        unsupported_features = ctx.disabled_features + [
            # Note: module_maps appears to be necessary here to ensure the API works
            # in all cases, and to avoid the error:
            # Invalid toolchain configuration: Cannot find variable named 'module_name'
            # yaqs/3227912151964319744
            "module_maps",
        ],
    )
    c_variables = cc_common.create_compile_variables(
        feature_configuration = feature_config,
        cc_toolchain = toolchain_info,
        user_compile_flags = cpp_fragment.copts + cpp_fragment.conlyopts,
    )
    cpp_variables = cc_common.create_compile_variables(
        feature_configuration = feature_config,
        cc_toolchain = toolchain_info,
        user_compile_flags = cpp_fragment.copts + cpp_fragment.cxxopts,
    )
    c_options = cc_common.get_memory_inefficient_command_line(
        feature_configuration = feature_config,
        action_name = _C_COMPILE_ACTION_NAME,
        variables = c_variables,
    )
    cpp_options = cc_common.get_memory_inefficient_command_line(
        feature_configuration = feature_config,
        action_name = _CPP_COMPILE_ACTION_NAME,
        variables = cpp_variables,
    )
    toolchain_id = str(target.label) + "%" + toolchain_info.target_gnu_system_name

    return struct(
        id = toolchain_id,
        compiler_executable = toolchain_info.compiler_executable,
        cpu = toolchain_info.cpu,
        compiler = toolchain_info.compiler,
        target_name = toolchain_info.target_gnu_system_name,
        built_in_include_directories = toolchain_info.built_in_include_directories,
        c_options = c_options,
        cpp_options = cpp_options,
    )

def _get_cc_compilation_context(target):
    if CcInfo in target:
        return target[CcInfo].compilation_context
    return None

IDE_CC = struct(
    follow_attributes = ["_cc_toolchain"],
    toolchains_aspects = [],
    toolchain_target = _get_cc_toolchain_target,
    compilation_context = _get_cc_compilation_context,
    cc_toolchain_info = _get_cc_toolchain_info,
)
