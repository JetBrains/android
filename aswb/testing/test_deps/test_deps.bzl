"""ASwB test dependencies repository rule."""

_BUILD = """
package(default_visibility = ["//visibility:public"])

exports_files([
    "bazel",
    "bazel_central_registry.zip",
    "projects.zip",
])

filegroup(
    name = "artifacts",
    srcs = [{artifacts}],
)
"""

def _zip(rctx, src, dest):
    args = ["zip", dest, "./", "-r0", "-x", "bazel-*"]
    result = rctx.execute(args, working_directory = src)

    if result.return_code != 0:
        fail("Failed executing '%s' with %d:\n%s" % (args, result.return_code, result.stderr))

def _aswb_test_deps_repository_impl(rctx):
    artifacts = []
    for url, sha256 in rctx.attr.artifacts.items():
        rctx.download(url, sha256, sha256)
        artifacts.append(sha256)

    rctx.download(
        rctx.attr.bazel_url,
        output = "bazel",
        sha256 = rctx.attr.bazel_sha256,
        executable = True,
    )
    rctx.download(
        rctx.attr.bcr_url,
        output = "bazel_central_registry.zip",
        sha256 = rctx.attr.bcr_sha256,
    )

    projects_path = rctx.workspace_root.get_child(rctx.attr.projects)
    rctx.watch_tree(projects_path)
    _zip(
        rctx,
        str(projects_path),
        str(rctx.path("projects.zip")),
    )

    rctx.file(
        "BUILD",
        _BUILD.format(
            artifacts = ",".join(["\"" + s + "\"" for s in artifacts]),
        ),
    )

aswb_test_deps_repository = repository_rule(
    implementation = _aswb_test_deps_repository_impl,
    attrs = {
        # Artifacts needed for repository cache.
        "artifacts": attr.string_dict(mandatory = True),

        # Bazel binary URL and SHA256.
        "bazel_url": attr.string(mandatory = True),
        "bazel_sha256": attr.string(mandatory = True),

        # Bazel central registry URL and SHA256.
        "bcr_url": attr.string(mandatory = True),
        "bcr_sha256": attr.string(mandatory = True),

        # Path to test projects directory.
        "projects": attr.string(mandatory = True),
    },
)
