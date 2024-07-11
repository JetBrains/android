"""This module implements JPS rules."""

load("//tools/base/bazel:functions.bzl", "create_option_file")

def idea_source(
        name,
        include,
        exclude,
        target_dir = None,
        base_dir = None,
        strip_prefix = None,
        tags = [],
        **kwargs):
    """Bundles IDEA sources.

    Args:
        name: the name of the target
        include: included paths
        exclude: excluded paths
        target_dir: target dir
        base_dir: base dir
        **kwargs: arguments to pass through to genrule

    """

    srcs = native.glob(include = include, exclude = exclude)
    zips = {}
    outs = {}
    for src in srcs:
        if strip_prefix and src.startswith(strip_prefix):
            src = src[len(strip_prefix):]
        ix = src.find("/")
        suffix = "" if ix == -1 else "_" + src[0:ix]
        zip_name = name + suffix + ".zip"
        zips[src] = zip_name
        outs[zip_name] = True

    _idea_source(
        name = name,
        zips = zips,
        strip_prefix = strip_prefix,
        outs = outs.keys(),
        tags = tags + ["no-remote-exec"],  # Too many inoputs for RBE
        **kwargs
    )

def _idea_source_impl(ctx):
    zip_files = {}
    for out in ctx.outputs.outs:
        zip_files[out.basename] = out

    zips = {}
    for file, zip in ctx.attr.zips.items():
        if zip not in zips:
            zips[zip] = []
        zips[zip].extend(file[DefaultInfo].files.to_list())

    for zip, files in zips.items():
        zip_file = zip_files[zip]
        zipper_args = ["c", zip_file.path]
        zipper_files = []
        for f in files:
            zip_path = f.short_path
            if zip_path.startswith(ctx.attr.strip_prefix):
                zip_path = zip_path[len(ctx.attr.strip_prefix):]
            zipper_files.append(zip_path + "=" + f.path + "\n")
        zipper_content = "".join(zipper_files)
        zipper_list = create_option_file(ctx, zip + ".res.lst", zipper_content)
        zipper_args.append("@" + zipper_list.path)
        ctx.actions.run(
            inputs = files + [zipper_list],
            outputs = [zip_file],
            executable = ctx.executable._zipper,
            arguments = zipper_args,
            progress_message = "Creating sources zip...",
            mnemonic = "zipper",
        )

    return [
        DefaultInfo(files = depset(ctx.outputs.outs)),
        JpsSourceInfo(files = [], strip_prefix = "", zips = ctx.outputs.outs),
    ]

_idea_source = rule(
    attrs = {
        "zips": attr.label_keyed_string_dict(allow_files = True),
        "outs": attr.output_list(),
        "strip_prefix": attr.string(),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "exec",
            executable = True,
        ),
    },
    implementation = _idea_source_impl,
)

def _strip(prefix, path):
    if not path.startswith(prefix):
        fail(path + " must start with " + prefix)
    ret = path[len(prefix):]
    if ret.startswith("/"):
        fail(ret + " must be a relative path")
    return ret

def _sources(ctx, use_short_path):
    sources = []
    files = []
    ix = 0
    for d in ctx.attr.deps:
        if JpsSourceInfo in d:
            file_list = d[JpsSourceInfo].files
            if file_list:
                prefix = d[JpsSourceInfo].strip_prefix
                lines = ["%s=%s\n" % (_strip(prefix, f.short_path), f.short_path if use_short_path else f.path) for f in file_list]
                lst_file = ctx.actions.declare_file("%s%d.lst" % (ctx.attr.name, ix))
                ctx.actions.write(output = lst_file, content = "".join(lines))
                sources.append(lst_file)
                files.append(lst_file)
                files.extend(file_list)
            for file in d[JpsSourceInfo].zips:
                sources.append(file)
                files.append(file)
        else:
            for file in d[DefaultInfo].files.to_list():
                sources.append(file)
                files.append(file)
        ix += 1
    return sources, files

def _jps_library_impl(ctx):
    # This library can be executed both as `bazel build` and as `bazel run`. The paths in those modes must be different,
    # so we construct two sets of sources arguments (one with short_path and one with path). Additionally, when building
    # we need to declare the output.
    run_sources, run_files = _sources(ctx, True)
    run_cmd = [
        ctx.attr._jps_build.files_to_run.executable.short_path,
    ] + ["--source=" + s.short_path for s in run_sources]

    build_sources, build_files = _sources(ctx, False)
    build_args = [
        "--out_file",
        ctx.outputs.zip.path,
    ] + ["--source=" + s.path for s in build_sources]

    args = [
        "--download_cache",
        ctx.attr.download_cache,
        "--command",
        ctx.attr.cmd,
        "--working_directory",
        "tools/idea",
        "--output_dir",
        "tools/idea/out/studio/",
        "--output_dir",
        "tools/idea/build/jps-bootstrap-work/",
    ]
    bootstrap_args = [
        "-Didea.test.module=" + ctx.attr.module,
        "-Dintellij.build.output.root={jps_bin_cwd}/out/studio",
        "-Dkotlin.plugin.kind=AS",
        "-Dintellij.build.dev.mode=false",
        "-Dcompile.parallel=true",
        "{jps_bin_cwd}",
        "intellij.idea.community.build",
        "TestModuleTarget",
    ]
    args.extend(["--arg=" + a for a in bootstrap_args])
    ctx.actions.write(output = ctx.outputs.executable, content = " ".join(run_cmd + args), is_executable = True)
    runfiles = ctx.runfiles(files = run_files)
    runfiles = runfiles.merge(ctx.attr._jps_build.default_runfiles)
    ctx.actions.run(
        outputs = [ctx.outputs.zip],
        inputs = build_files,
        tools = [ctx.executable._jps_build],
        executable = ctx.executable._jps_build,
        arguments = build_args + args,
        mnemonic = "JpsBuild",
    )

    return [
        DefaultInfo(files = depset([ctx.outputs.executable]), executable = ctx.outputs.executable, runfiles = runfiles),
        JpsSourceInfo(files = [], strip_prefix = "", zips = [ctx.outputs.zip]),
    ]

jps_library = rule(
    attrs = {
        "_jps_build": attr.label(default = "//tools/adt/idea/jps-build:jps_build", executable = True, cfg = "exec"),
        "deps": attr.label_list(allow_files = True),
        "module": attr.string(),
        "download_cache": attr.string(),
        "cmd": attr.string(default = "platform/jps-bootstrap/jps-bootstrap.sh"),
    },
    outputs = {
        "zip": "%{name}.zip",
    },
    executable = True,
    implementation = _jps_library_impl,
)

def _jps_test_impl(ctx):
    sources, files = _sources(ctx, True)

    jvmargs_file = ctx.actions.declare_file("%s.jvmargs" % ctx.attr.name)
    ctx.actions.write(output = jvmargs_file, content = "".join([o + "\n" for o in ctx.fragments.java.default_jvm_opts]))

    cmd = [
        ctx.attr._jps_build.files_to_run.executable.short_path,
    ] + [
        "--source=" + f.short_path
        for f in sources
    ] + [
        "--source",
        ctx.file._test_runner.short_path,
        "--command",
        "jps_test.sh",
        "--working_directory",
        "tools/idea",
        "--ignore_dir",
        "system",
        "--ignore_dir",
        "config",
        "--ignore_dir",
        "tools/idea/.test",
        "--ignore_dir",
        "home/.java",
        "--ignore_dir",
        "home/.cache",
        "--download_cache",
        ctx.attr.download_cache,
        "--env BAZEL_RUNNER $PWD/" + ctx.file._bazel_runner.short_path,
        "--env JVM_ARGS_FILE $PWD/" + jvmargs_file.short_path,
        "--env JAVA_BIN $PWD/" + ctx.attr._java_runtime[java_common.JavaRuntimeInfo].java_executable_exec_path,
        "--env TEST_SUITE '" + ctx.attr.test_suite + "'",
        "--env TEST_EXCLUDE_FILTER '" + "|".join(ctx.attr.test_exclude_filter) + "'",
        "--env TEST_FILTER '" + "|".join(ctx.attr.test_filter) + "'",
        "--env TEST_MODULE '" + ctx.attr.module + "'",
    ]

    files = [
        ctx.file._test_runner,
        ctx.file._bazel_runner,
        jvmargs_file,
    ] + files

    for name, value in ctx.attr.env.items():
        cmd.append("--env %s %s" % (name, value))

    for d in ctx.attr.data:
        files.extend(d[DefaultInfo].files.to_list())

    ctx.actions.write(output = ctx.outputs.executable, content = " ".join(cmd), is_executable = True)
    runfiles = ctx.runfiles(files = files)
    runfiles = runfiles.merge(ctx.attr._jps_build.default_runfiles)
    runfiles = runfiles.merge(ctx.attr._java_runtime.default_runfiles)
    return DefaultInfo(executable = ctx.outputs.executable, runfiles = runfiles)

_jps_test = rule(
    attrs = {
        "download_cache": attr.string(),
        "test_suite": attr.string(),
        "module": attr.string(),
        "data": attr.label_list(allow_files = True),
        "env": attr.string_dict(),
        "deps": attr.label_list(allow_files = True),
        "test_exclude_filter": attr.string_list(default = []),
        "test_filter": attr.string_list(default = []),
        "_jps_build": attr.label(default = "//tools/adt/idea/jps-build:jps_build"),
        "_test_runner": attr.label(allow_single_file = True, default = "//tools/adt/idea/jps-build:test_runner"),
        "_bazel_runner": attr.label(allow_single_file = True, default = "//tools/adt/idea/jps-build:jps-test-runner_deploy.jar"),
        "_java_runtime": attr.label(default = Label("@bazel_tools//tools/jdk:current_java_runtime")),
    },
    fragments = ["java"],
    test = True,
    executable = True,
    implementation = _jps_test_impl,
)

def split(
        name,
        filter,
        shard_count = None):
    return struct(name = name, filter = filter, shard_count = shard_count)

def jps_test(
        name,
        split_tests = None,
        test_exclude_filter = None,
        env = None,
        **kwargs):
    """A jps based test.

    Args:
        test_suite: The test suite to run
        module: The module to use the classpath from.
        data: Test data
        download_cache: where to save downloaded data when running the test with 'bazel run'
        deps: The jps workspace setup.
        test_filter: What tests to run. See bazel's --test_filter.
        test_exclude_filter: What tests not to run. See bazel's --test_exclude_filter
        split_tests: A list of split objects constructed with 'split'. Each split has a name used as suffix,
                     a test filter, and a shard_count. A target is created per split, with one additional
                     target suffixed '_empty' that asserts that no tests were left out of the splits.
    """
    if split_tests:
        names = []
        splits = []
        for split in split_tests:
            this_name = name + "_" + split.name
            names.append(this_name)
            splits.append(split.filter)
            _jps_test(
                name = this_name,
                test_exclude_filter = test_exclude_filter,
                env = env,
                test_filter = [split.filter],
                shard_count = split.shard_count,
                **kwargs
            )
        check_empty_env = {"ASSERT_TEST_IS_EMPTY": "1"}
        if env:
            check_empty_env.update(env)
        check_empty_test_name = name + "_empty"
        _jps_test(
            name = check_empty_test_name,
            test_exclude_filter = test_exclude_filter + splits,
            env = check_empty_env,
            **kwargs
        )
        names.append(check_empty_test_name)

        native.test_suite(
            name = name,
            tests = names,
        )

    else:
        _jps_test(
            name = name,
            env = env,
            test_exclude_filter = test_exclude_filter,
            **kwargs
        )

JpsSourceInfo = provider("Source info", fields = ["files", "strip_prefix", "zips"])

def _jps_cache_impl(ctx):
    return JpsSourceInfo(files = ctx.files.srcs, strip_prefix = ctx.attr.strip_prefix, zips = [])

jps_cache = rule(
    attrs = {
        "srcs": attr.label_list(allow_files = True),
        "strip_prefix": attr.string(mandatory = True),
    },
    implementation = _jps_cache_impl,
)
