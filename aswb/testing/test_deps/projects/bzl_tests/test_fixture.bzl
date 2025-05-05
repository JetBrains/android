"""Rules for writing tests for the IntelliJ aspect."""

load(
    ":build_dependencies_deps.bzl",
    "IDE_CC",
    "IDE_JAVA",
    "IDE_JAVA_PROTO",
    "IDE_KOTLIN",
)

# an aspect that will return the all the information that aswb needed of a target. If the information is not applied to that that target, it will return None.
def _aspect_impl(target, ctx):
    java_info = IDE_JAVA.get_java_info(target, ctx.rule)
    kotlin_info = IDE_KOTLIN.get_kotlin_info(target, ctx.rule)
    java_proto_info = IDE_JAVA_PROTO.get_java_proto_info(target, ctx.rule)
    cc_toolchain_target = IDE_CC.toolchain_target(ctx.rule)
    return TargetInfo(
        label = target.label,
        java_info = java_info,
        kotlin_info = kotlin_info,
        java_proto_info = java_proto_info,
        cc_toolchain_target = cc_toolchain_target,
    )

build_dependencies_deps_aspect = aspect(
    implementation = _aspect_impl,
    attr_aspects = ["deps"],
)

TargetInfo = provider("The language sepecific information for a target. When that lang_info is not applied to the target, it will be None.", fields = ["label", "deps", "java_info", "kotlin_info", "java_proto_info", "cc_toolchain_target"])

TargetsInfo = provider("A list of TargetInfo for all the targets in the dependency tree.", fields = ["target_infos"])

def _impl(ctx):
    """Returns a list of TargetInfo for the test target only (not its dependencies) ."""
    return [
        dep[TargetInfo]
        for dep in ctx.attr.deps
    ]

build_dependencies_deps_test_fixture = rule(
    _impl,
    attrs = {
        "deps": attr.label_list(aspects = [build_dependencies_deps_aspect]),
    },
)
