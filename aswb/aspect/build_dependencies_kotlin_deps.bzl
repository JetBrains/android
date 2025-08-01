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
