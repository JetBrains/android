"""
ASwB test workspace and its dependencies setup.
"""

load("@@//tools/adt/idea/aswb/testing/test_deps:test_deps_bazel_artifacts.bzl", "ASWB_TEST_DEPS")
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_file")

def _exec(repository_ctx, working_directory, args):
    execute_result = repository_ctx.execute(args, working_directory = working_directory)
    if execute_result.return_code != 0:
        fail("Failed executing '%s' with %d:\n%s" % (args, execute_result.return_code, execute_result.stderr))
    return execute_result.stdout.splitlines()

def _exec_zip(repository_ctx, source_path, zip_file):
    return _exec(repository_ctx, source_path, ["zip", str(zip_file), "./", "-r0"])

def _aswb_test_projects_repository_impl(repository_ctx):
    workspace_root_path = str(repository_ctx.workspace_root) + "/"
    repo_root = repository_ctx.path("")

    path = repository_ctx.path(workspace_root_path + repository_ctx.attr.path)
    repository_ctx.watch_tree(path)

    _exec_zip(repository_ctx, str(path), str(repo_root) + "/" + "all_sources.zip")
    repository_ctx.template(
        "BUILD.bazel",
        workspace_root_path + "tools/adt/idea/aswb/testing/test_deps/aswb_test_projects.BUILD",
    )

aswb_test_projects_repository = repository_rule(
    doc = """
    A repository rule that sets up a local repository derived from [path] by
    renaming all build related files (BUILD, WORKSPACE, etc.) to *._TEST_ and
    supplementing it with a build file that zips the resulting workspace.
    """,
    implementation = _aswb_test_projects_repository_impl,
    local = True,
    attrs = {
        "path": attr.string(),
    },
)

def aswb_test_deps_dependencies():
    """
    Set up the @aswb_test_projects workspace and its dependencies.
    """
    http_file(
        name = "aswb_test_deps_bazel",
        executable = True,
        sha256 = "40f243b118f46d1c88842315e78ec5f9f6390980d67a90f7b64098613e60d65b",
        url = "https://github.com/bazelbuild/bazel/releases/download/8.0.1/bazel-8.0.1-linux-x86_64",
        visibility = ["//tools/vendor/google3/aswb:__subpackages__"],
    )

    http_file(
        name = "aswb_test_deps_bazel_central_registry",
        sha256 = "c2a7ef99ac2b35eb4b0bac43ad096c522ddbec84ee324247564d33256a479e79",
        downloaded_file_path = "bazel_central_registry.zip",
        url = "https://github.com/bazelbuild/bazel-central-registry/archive/922aac243abc5c548b985deccabfe51d5cfd127e.zip",
        visibility = ["//tools/vendor/google3/aswb:__subpackages__"],
    )

    aswb_test_projects_repository(
        name = "aswb_test_projects",
        path = "tools/adt/idea/aswb/testing/test_deps/projects",
    )

    for k, v in ASWB_TEST_DEPS.items():
        http_file(
            name = "aswb_test_deps_" + v["name"],
            url = k,
            sha256 = v["sha256"],
            downloaded_file_path = v["name"],
            visibility = ["//tools/vendor/google3/aswb:__subpackages__"],
        )
