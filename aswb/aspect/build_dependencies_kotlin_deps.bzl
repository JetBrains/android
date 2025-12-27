load(
    "@rules_kotlin//kotlin/internal:defs.bzl",
    "KtJvmInfo",
    _TOOLCHAIN_TYPE = "TOOLCHAIN_TYPE",
)
load("@rules_kotlin//kotlin/internal:opts.bzl", "KotlincOptions", "kotlinc_options_to_flags")

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
    if rule.kind in ["_jvm_toolchain"]:
        deps.extend(_get_dependency_attribute(rule, "jvm_stdlibs"))
    if _TOOLCHAIN_TYPE in rule.toolchains:
        deps.extend([rule.toolchains[_TOOLCHAIN_TYPE]])
    return deps

def _get_kotlin_info_v2(target, rule, info):
    if rule.kind == "_kt_toolchain":
        flags = []
        if platform_common.ToolchainInfo in target:
            toolchain_info = target[platform_common.ToolchainInfo]
            if hasattr(toolchain_info, "kotlinc_options"):
                opts = toolchain_info.kotlinc_options
                if opts:
                    flags.extend(kotlinc_options_to_flags(opts))

            if hasattr(toolchain_info, "language_version") and toolchain_info.language_version:
                flags.append("-language-version")
                flags.append(toolchain_info.language_version)

            if hasattr(toolchain_info, "api_version") and toolchain_info.api_version:
                flags.append("-api-version")
                flags.append(toolchain_info.api_version)

            if hasattr(toolchain_info, "jvm_target") and toolchain_info.jvm_target:
                flags.append("-jvm-target")
                flags.append(toolchain_info.jvm_target)
        return struct(flags = flags, is_kotlin_toolchain = True)

    kt_info = target[KtJvmInfo] if KtJvmInfo in target else None

    flags = []

    # Determine if we should process this target.
    # It should be processed if it has KtJvmInfo (is a Kotlin target)
    # OR if it explicitly uses the Kotlin toolchain.
    if not kt_info and _TOOLCHAIN_TYPE not in rule.toolchains:
        return None
    opts = None
    if hasattr(rule.attr, "kotlinc_opts"):
        opts_target = rule.attr.kotlinc_opts
        if opts_target and KotlincOptions in opts_target:
            opts = opts_target[KotlincOptions]
            flags.extend(kotlinc_options_to_flags(opts))
    if not opts and _TOOLCHAIN_TYPE in rule.toolchains:
        toolchain_target = rule.toolchains[_TOOLCHAIN_TYPE]

        # Use our own provider (info) to get flags from the toolchain target.
        if info in toolchain_target:
            flags.extend(toolchain_target[info].kotlin_compiler_flags)

    if flags:
        return struct(flags = flags, is_kotlin_toolchain = False)

    return struct(flags = [], is_kotlin_toolchain = False)

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
    toolchains_aspects = [_TOOLCHAIN_TYPE],
    get_kotlin_info_v2 = _get_kotlin_info_v2,
)
