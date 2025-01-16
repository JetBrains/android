"""
The test_project_package macro and rules supporting it.

test_project_package generates a set of targets contributing to the
:integration_test_data rule that produces the testdata directory representing
prebuilt artifacts that are required to simulate the query sync process in
the CI environment. This reflects the following constraints imposed by the CI
and Bazel.

(a) No network access - i.e. no direct access to the source code repository and
    thus no way to run `bazel query` and `bazel build` on projects with real
    dependencies.

(b) No wildcards in list of labels attributes - i.e. test_project_package
    requires all targets in the package to be listed manually. However, because
    `siblings()` function is available in `genquery` rules, this list can be
    verified and build fails if any targets that matter are missing from the
    list

(c) Wildcards except `siblings()` function are not supported in `genquery`
    rules - .i.e. //package/path/... queries run by the query sync need to be
    replaced with  manually expanded lists of targets

(d) Aspects used from rules cannot have string parameters - means
    collect_dependencies aspect is replaced with collect_all_depdencies_for_tests
    aspect sharing the same implementation function but applying include/exclude
    filers. Instead, filtering is applied when artifact_info_file are loaded
    by the query sync integration test framework.

The test data directory consists of the following major groups of files.

(1) sources - includes all source files in a test project including any
    subpackages that also need to be described with test_project_package. Note:
    build fails if such a description is missing.

(2) prebuilt dependencies - outputs of the collect_depdencies aspect for all
    targets and their dependencies in this package. Note that unlike queries
    that run in the production, `genquery`ies cannot contain wildcards and
    therefore include/exclude filters are not applied at this stage. However,
    this is not different form bazel populating bazel-out with this files during
    normal build. When artifact_info_file files are loaded they are properly
    filtered to include only the required artifacts.

(3) artifact_info_file files - outputs of package_dependencies aspect for all
    targets and their dependencies in this package. Unlike the query sync in the
    production include /exclude filters are not applied at this stage.
    artifact_info_file are filtered when loaded by the query sync integration
    test rules.
    TODO(b/271858575): Consider filtering pre-built query results by
    include/exclude during build.

"""

load("@rules_pkg//pkg:mappings.bzl", "pkg_filegroup", "pkg_files", "strip_prefix")
load("@rules_pkg//pkg:pkg.bzl", "pkg_zip")
load("@rules_pkg//pkg:providers.bzl", "PackageFilesInfo")
load(
    "//tools/adt/idea/aswb/aspect:build_dependencies.bzl",
    "ALWAYS_BUILD_RULES",
    "DependenciesInfo",
    "collect_all_dependencies_for_tests",
    "package_dependencies_for_tests",
)

def _test_build_deps_impl(ctx):
    f = []
    for t in ctx.attr.deps:
        dep_info = t[DependenciesInfo]
        f += dep_info.compile_time_jars.to_list() if dep_info.compile_time_jars else []
        f += dep_info.aars.to_list() if dep_info.aars else []
        f += dep_info.gensrcs.to_list() if dep_info.gensrcs else []
        f += dep_info.cc_headers.to_list() if dep_info.cc_headers else []
        test_mode_own_files = dep_info.test_mode_own_files if dep_info.test_mode_own_files else []
        if test_mode_own_files:
            f += test_mode_own_files.test_mode_within_scope_own_jar_files
            f += test_mode_own_files.test_mode_within_scope_own_ide_aar_files
            f += test_mode_own_files.test_mode_within_scope_own_gensrc_files
    return [
        PackageFilesInfo(
            attributes = {},
            dest_src_map = {i.path: i for i in f},
        ),
        DefaultInfo(
            files = depset(f),  # TODO: b/331378322 - rework: depset(f)
        ),
    ]

def _collect_deps_sources_impl(ctx):
    f = []
    for t in ctx.attr.deps:
        dep_info = t[DependenciesInfo]
        for src_file in dep_info.test_mode_cc_src_deps.to_list() if dep_info.test_mode_cc_src_deps else []:
            dest_file = ctx.actions.declare_file(src_file.path)

            # Many of these files are symlinks, using them directly will break
            # them. So copy them to ensure they work:
            ctx.actions.run_shell(
                inputs = [src_file],
                outputs = [dest_file],
                command = "cp '%s' '%s'" % (src_file.path, dest_file.path),
            )
            f.append(dest_file)

    return [DefaultInfo(
        files = depset(f),
    )]

def _encode_file_list(files):
    """Encodes a list of files as a struct.

    Returns:
      A list fo structs matching the bazel.intellij.OutputArtifact proto message.
    """
    r = []
    for f in files:
        if f.is_directory:
            r.append(struct(directory = f.path))
        else:
            r.append(struct(file = f.path))
    return r

def _encode_target_info_proto(artifact_files):
    contents = []
    for label in artifact_files:
        contents.append(
            struct(
                target = label,
                jars = _encode_file_list(artifact_files[label].jars),
                ide_aars = _encode_file_list(artifact_files[label].ide_aars),
                gen_srcs = _encode_file_list(artifact_files[label].gen_srcs),
            ),
        )
    return proto.encode_text(struct(artifacts = contents))

def _declare_and_write_info_file(ctx, file_name, artifact_files):
    info_file = ctx.actions.declare_file(file_name)
    ctx.actions.write(
        info_file,
        _encode_target_info_proto(artifact_files),
    )
    return info_file

def _strip_dotdot_path_prefix(path):
    if path.startswith("../"):
        # This is a bit of a hack to ensure the test data we produce is self
        # consistent. We produce a zip file of artifacts using pkg_zip and
        # related rules, as well as writing the output group contents into the
        # same zip, in proto format. When writing that proto, the behavior of
        # file.short_path is inconsistent with the paths that pkg_zip et al
        # use for the same file. This seems to happen just for generated files
        # from an external repo, where the short_path looks like:
        #  "../[repo_name]/path/within/repo"
        # Whereas the path of the same file within the zip is just:
        #  "path/within/repo"
        # So here we paper over the cracks the ensure consistency:
        parts = path.split("/")
        return "/".join(parts[2:])
    return path

def _declare_and_write_output_group_info_file(ctx, output_groups):
    """Writes output group information to a proto text file.

    This enables test code to enumerate the contents of an output group. The
    structure written here is defined inside `output_groups.proto`
    """
    info_file = ctx.actions.declare_file("output_groups")
    ctx.actions.write(
        info_file,
        proto.encode_text(struct(
            target_output_groups =
                [
                    struct(
                        label = label,
                        output_groups = [
                            struct(
                                name = output_group_name,
                                entries = [
                                    _strip_dotdot_path_prefix(f.short_path)
                                    for f in output_groups[label][output_group_name].to_list()
                                ],
                            )
                            # list(OutputGroupInfo) returns the names of the groups within it:
                            for output_group_name in list(output_groups[label])
                        ],
                    )
                    for label in output_groups.keys()
                ],
        )),
    )
    return info_file

def _test_build_dep_desc_impl(ctx):
    f = []
    artifacts = {}
    own_within_scope_artifacts = {}
    output_groups = {}
    for t in ctx.attr.deps:
        label = str(t.label)
        f += t[OutputGroupInfo].qs_info.to_list()
        f += t[OutputGroupInfo].qs_cc_info.to_list()

        # Add all output groups to a dict of [target name] -> [OutputGroupInfo]
        output_groups[label] = t[OutputGroupInfo]

        dependencies_info = t[DependenciesInfo]
        artifacts[label] = struct(
            jars = dependencies_info.compile_time_jars.to_list(),
            ide_aars = dependencies_info.aars.to_list(),
            gen_srcs = dependencies_info.gensrcs.to_list(),
        )

        test_mode_own_files = dependencies_info.test_mode_own_files
        if test_mode_own_files:
            own_within_scope_artifacts[label] = struct(
                jars = test_mode_own_files.test_mode_within_scope_own_jar_files,
                ide_aars = test_mode_own_files.test_mode_within_scope_own_ide_aar_files,
                gen_srcs = test_mode_own_files.test_mode_within_scope_own_gensrc_files,
            )

    f.append(_declare_and_write_info_file(ctx, "artifacts", artifacts))
    f.append(_declare_and_write_info_file(ctx, "own_within_scope_artifacts", own_within_scope_artifacts))

    # We write the contents of the output groups created by `build_dependencies.bzl`
    # so that the test code that consumes our outputs can find artifacts based on
    # their output group:
    f.append(_declare_and_write_output_group_info_file(ctx, output_groups))

    return [DefaultInfo(files = depset(f))]

test_build_deps = rule(
    doc = """
    A rule that build dependencies in the same way that the query sync does.

    The rule applies collect_dependencies aspect to its dependencies and exposes
    collected dependencies as the default output group so that it can be used as
    test data in query sync integration tests.
    """,
    implementation = _test_build_deps_impl,
    attrs = {
        "deps": attr.label_list(aspects = [collect_all_dependencies_for_tests]),
    },
)

collect_deps_sources = rule(
    doc = """
    A rule that collects all sources from deps that are needed at test time.

    This is used to ensure required cc headers are available at test time,
    since they are accessed directly as source files by the IDE.
    """,
    implementation = _collect_deps_sources_impl,
    attrs = {
        "deps": attr.label_list(aspects = [collect_all_dependencies_for_tests]),
    },
)

test_build_deps_desc = rule(
    doc = """
    A rule that describes its dependencies as the build dependency phase of the
    query sync does.

    The rule applies collect_dependencies and package_dependencies aspects to
    its dependencies and exposes generated artifact_info_file files as its
    default output group so that it can be used as test data in query sync
    integration tests.
    """,
    implementation = _test_build_dep_desc_impl,
    attrs = {
        "deps": attr.label_list(
            aspects = [collect_all_dependencies_for_tests, package_dependencies_for_tests],
        ),
    },
)

def test_project_package(name, all_targets, visibility, external_sources = []):
    """
    A macro to turn the package into a test project.

    The macro generates rules that turn the current package into a test project
    for sync integration tests. There can be only one macro applied to a package
    as it builds a test data target representing the content of the whole
    package and its subpackages.

    Args:
        name: must be 'test_data'
        all_targets: the package relative labels (i.e. :label) of all targets in
            this package, for which query sync may need to build dependencies.
            (Any missing are detected when running tests).
        visibility: the visibility assigned to targets consumed by the test framework.
        external_sources: extra sources to make available at test runtime. This
            is used to make external dependency sources available to tests that
            use them. A list of labels defined with macro 'external_sources_package'.
    """

    expected_name = "test_data"
    if name != expected_name:
        fail("test_project_package name parameter value must be equal to '" + expected_name + "'")
    else:
        pass

    # "sit" prefix stands for sync integration test.
    test_build_name = name + "_build"
    query_name = name + "_query"
    deps_name = name + "_deps"
    deps_sources_name = name + "_deps_srcs"
    deps_desc_name = name + "_deps_desc"
    all_srcs_name = name + "_srcs_all"
    external_srcs_name = name + "_srcs_external"
    all_queries_group_name = name + "_queries_all_group"
    all_queries_name = name + "_queries_all"
    all_deps_name = name + "_deps_all"
    all_deps_descs_name = name + "_deps_desc_all"

    all_srcs_label = "//" + native.package_name() + ":" + all_srcs_name
    all_target_labels = ["//" + native.package_name() + r for r in all_targets]

    subpackages = native.subpackages(include = ["**"], allow_empty = True)
    subpackage_label_prefixes = ["//" + native.package_name() + "/" + p + ":" for p in subpackages]

    # A genquery target that simulates queries run by the query sync.
    # Note: It needs to be kep in sync with the query sync implementation.
    expression = (
        # Include all targets from all project packages.
        (("siblings(" + " + ".join(all_target_labels) + ") + ") if all_target_labels else "") +
        # TODO: solodkyy - find out whether this is needed.
        "deps(" + all_srcs_label + ")"
    )
    native.genquery(
        name = query_name,
        # The query sync runs queries in a form of
        # /directory/path/... + ... - /directory/path/...
        # This is not possible to do in genquery. Instead, we run a query that
        # consists of targets and sources parts and since there is no way to
        # have wildcards in dependency specification `test_project_package`
        # macro requires all targets to be listed manually, even though it can
        # later report any missing targets that matter.
        # Note: siblings() ensures that if any targets are missing from
        # all_target_labels they are still included in the query. If it
        # happens an error is reported when a test attempts to build
        # dependencies of such a target.
        expression = expression,
        opts = ["--output=streamed_proto", "--consistent_labels=true"],
        testonly = 1,
        scope = all_targets + [all_srcs_label],
        visibility = visibility,
    )

    native.filegroup(
        name = test_build_name,
        srcs = all_target_labels,
        testonly = 1,
        visibility = visibility,
    )

    # A target that pre-builds all dependencies that the query sync may need to
    # build in this package.
    test_build_deps(
        name = deps_name,
        deps = all_target_labels,
        testonly = 1,
        visibility = visibility,
    )

    collect_deps_sources(
        name = deps_sources_name,
        deps = all_target_labels,
        testonly = 1,
        visibility = visibility,
    )

    # A rule that produces artifact_info_file file for all dependencies of this
    # package.
    test_build_deps_desc(
        name = deps_desc_name,
        deps = all_target_labels,
        testonly = 1,
        visibility = visibility,
    )

    # A recursive filegroup that aggregates query results from this package and
    # its subpackages.
    native.filegroup(
        name = all_queries_group_name,
        srcs = [":" + query_name] +
               [p + query_name for p in subpackage_label_prefixes],
        testonly = 1,
        visibility = visibility,
    )

    # A recursive filegroup that aggregates prebuilt dependencies from this
    # package and its subpackages.
    pkg_filegroup(
        name = all_deps_name,
        srcs = [":" + deps_name] +
               [p + deps_name for p in subpackage_label_prefixes],
        #        strip_prefix = strip_prefix.from_root(""),
        prefix = "prebuilt_deps",
        testonly = 1,
        visibility = visibility,
    )

    # A recursive filegroup that aggregates artifact_info_file files from this
    # package and its subpackages.
    pkg_files(
        name = all_deps_descs_name,
        srcs = [":" + deps_desc_name] +
               [p + deps_desc_name for p in subpackage_label_prefixes],
        strip_prefix = strip_prefix.from_root(""),
        prefix = "target_prebuilt_dep_descs",
        testonly = 1,
        visibility = visibility,
    )

    # A recursive filegroup that aggregates source  files from this package and
    # its subpackages.
    pkg_files(
        name = all_srcs_name,
        # NOTE: It is important not to add any other dependencies to this target
        #       as it is used in `bazel query` to bring all sources into the query.
        srcs = native.glob(["**/*"]) +
               [p + all_srcs_name for p in subpackage_label_prefixes],
        strip_prefix = strip_prefix.from_root(),
        prefix = "sources",
        tags = ["test_instrumentation"],
        testonly = 1,
        visibility = visibility,
    )

    # Repackage cc, srcjar etc. sources from dependencies and external_sources.
    pkg_files(
        name = external_srcs_name,
        srcs = [":" + deps_sources_name] + external_sources,
        strip_prefix = strip_prefix.from_root(),
        prefix = "sources",
        tags = ["test_instrumentation"],
        testonly = 1,
        visibility = visibility,
    )

    # A target that joins all query output files into one file.
    native.genrule(
        name = all_queries_name,
        srcs = [":" + all_queries_group_name],
        outs = [name + "_all_queries"],
        cmd = "cat $(locations :" + all_queries_group_name + ") > $@",
        testonly = 1,
    )

    # A target that produces the final testdata layout of a test project for use
    # in query sync integration tests.
    pkg_zip(
        name = name,
        srcs = [
            ":" + all_srcs_name,
            ":" + external_srcs_name,
            ":" + all_queries_name,
            ":" + all_deps_name,
            ":" + all_deps_descs_name,
        ],
        testonly = 1,
        visibility = visibility,
    )

def _publish_always_build_rules_impl(ctx):
    always_build_rules_file = ctx.actions.declare_file("ALWAYS_BUILD_RULES")
    ctx.actions.write(
        always_build_rules_file,
        ALWAYS_BUILD_RULES,
    )
    return [DefaultInfo(data_runfiles = ctx.runfiles([always_build_rules_file]))]

publish_always_build_rules = rule(
    doc = """
    A rule that publishes ALWAYS_BUILD_RULES list used by the test aspect.
    """,
    implementation = _publish_always_build_rules_impl,
    attrs = {},
)

def external_sources_package(name, visibility):
    """
    Makes external (non-project) sources available to tests at runtime.

    Exports all sources in the package. To be used in conjunction with macro
    `test_project_package` and parameter `external_sources`.
    """
    native.filegroup(
        name = name,
        srcs = native.glob(["**/*"]),
        tags = ["test_instrumentation"],
        testonly = 1,
        visibility = visibility,
    )
