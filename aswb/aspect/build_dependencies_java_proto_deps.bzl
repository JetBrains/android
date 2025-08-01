_PROTO_TOOLCHAIN_TYPES = [
    "@protobuf//bazel/private:java_toolchain_type",
    "@protobuf//bazel/private:javalite_toolchain_type",
    "@protobuf//bazel/private:proto_toolchain_type",
]

def _get_dependency_attribute(rule, attr):
    if hasattr(rule.attr, attr):
        to_add = getattr(rule.attr, attr)
        if type(to_add) == "list":
            return [t for t in to_add if type(t) == "Target"]
        elif type(to_add) == "Target":
            return [to_add]
    return []

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
