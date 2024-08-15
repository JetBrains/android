"""Aspects to build and collect project's compose dependencies."""

load("//third_party/bazel_rules/rules_java/java:defs.bzl", "JavaInfo")

ComposeDependenciesInfo = provider(
    "The compose dependencies",
    fields = {
        "render_jars": "a list of render jars generated for project files and external dependencies",
    },
)

def _package_compose_dependencies_impl(target, ctx):  # @unused
    return [OutputGroupInfo(
        render_jars = target[ComposeDependenciesInfo].render_jars.to_list(),
    )]

package_compose_dependencies = aspect(
    implementation = _package_compose_dependencies_impl,
    required_aspect_providers = [[ComposeDependenciesInfo]],
)

def _collect_compose_dependencies_impl(target, ctx):  # @unused
    if JavaInfo not in target:
        return [ComposeDependenciesInfo(
            render_jars = depset(),
        )]
    return [
        ComposeDependenciesInfo(
            render_jars = depset([], transitive = [target[JavaInfo].transitive_runtime_jars]),
        ),
    ]

collect_compose_dependencies = aspect(
    implementation = _collect_compose_dependencies_impl,
    provides = [ComposeDependenciesInfo],
    attr_aspects = ["deps", "exports", "_android_sdk"],
)
