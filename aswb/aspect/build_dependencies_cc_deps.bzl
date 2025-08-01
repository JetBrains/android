load(
    "@bazel_tools//tools/build_defs/cc:action_names.bzl",
    _CPP_COMPILE_ACTION_NAME = "CPP_COMPILE_ACTION_NAME",
    _C_COMPILE_ACTION_NAME = "C_COMPILE_ACTION_NAME",
)

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
