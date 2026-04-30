load("@rules_java//java:defs.bzl", "java_binary")

def _gradle_build_project_impl(ctx):
    out_dir = ctx.actions.declare_directory(ctx.attr.out_dir_name)
    runfiles_path = ctx.executable.tool.path + ".runfiles"
    tool_runfiles = ctx.attr.tool[DefaultInfo].default_runfiles.files

    gradle_zip_path = ""
    for f in ctx.files.gradle_dist:
        if f.basename.startswith("gradle-") and f.basename.endswith("-bin.zip"):
            gradle_zip_path = f.path
            break

    if not gradle_zip_path:
        fail("Could not find gradle distribution zip in gradle_dist")

    input_dir_path = ctx.attr.project_path

    # tool args: <out_dir> <input_model_dir> <project_name> <manifest_path> <gradle_zip_path>
    all_args = [out_dir.path, input_dir_path, ctx.attr.project_name, ctx.attr.manifest_path, gradle_zip_path] + ctx.attr.tool_args

    ctx.actions.run(
        outputs = [out_dir],
        inputs = depset(ctx.files.srcs + ctx.files.gradle_dist, transitive = [tool_runfiles]),
        executable = ctx.executable.tool,
        arguments = all_args,
        env = {
            "RUNFILES_DIR": runfiles_path,
            "TEST_SRCDIR": runfiles_path,
            "TEST_WORKSPACE": ctx.workspace_name,
            "HOME": ".",
            "USER_HOME": ".",
            "JAVA_TOOL_OPTIONS": "-Djava.awt.headless=true",
        },
        mnemonic = "TargetGen",
        progress_message = "Generating built directory %s" % out_dir.path,
    )
    return [DefaultInfo(files = depset([out_dir]), runfiles = ctx.runfiles(files = [out_dir]))]

# A rule that executes the project build generator tool inside a hermetic Bazel sandbox.
# It packages the fully compiled project and its populated local build cache into a directory artifact.
gradle_build_project_generator = rule(
    implementation = _gradle_build_project_impl,
    attrs = {
        "srcs": attr.label_list(allow_files = True),
        "project_path": attr.string(mandatory = True),
        "tool": attr.label(executable = True, mandatory = True, cfg = "target"),
        "gradle_dist": attr.label(allow_files = True, mandatory = True),
        "out_dir_name": attr.string(mandatory = True),
        "project_name": attr.string(mandatory = True),
        "manifest_path": attr.string(mandatory = True),
        "tool_args": attr.string_list(),
    },
)

# Macro to pre-build a Gradle project inside a Bazel action.
# This macro instantiates a Java binary to run the Gradle build and executes it via the gradle_build_project_generator rule.
# The resulting artifact allows integration tests to skip heavy compilation tasks during the initial Android Studio launch.
def gradle_build_project(name, out_dir_name, project_name, manifest_path, gradle_dist, project_path, srcs = [], visibility = None):
    generator_name = name + "_generator"
    java_binary(
        name = generator_name,
        testonly = True,
        main_class = "com.android.tools.idea.ProjectBuildGenerator",
        data = srcs,
        runtime_deps = ["//tools/adt/idea/gradle-prebuild:gradle_project_build_generator_lib"],
    )
    gradle_build_project_generator(
        name = name,
        srcs = srcs,
        testonly = True,
        out_dir_name = out_dir_name,
        project_name = project_name,
        manifest_path = manifest_path,
        gradle_dist = gradle_dist,
        project_path = project_path,
        tool = ":" + generator_name,
        visibility = visibility,
    )
