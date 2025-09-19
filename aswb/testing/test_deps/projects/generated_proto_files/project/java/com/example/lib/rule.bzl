def _custom_rule_impl(ctx):
    ctx.actions.run_shell(
        inputs = [ctx.files.deps[0]],
        outputs = [ctx.outputs.jar],
        command = "cp %s %s" % (ctx.files.deps[0].path, ctx.outputs.jar.path),
        mnemonic = "CopyJar",
        progress_message = "Copying JAR %s" % ctx.files.deps[0].short_path,
    )
    return [
        JavaInfo(
            output_jar = ctx.outputs.jar,
            compile_jar = ctx.outputs.jar,  # Use the same JAR for compile time and runtime.
        ),
        DefaultInfo(
            files = depset([ctx.outputs.jar]),
            runfiles = ctx.runfiles(transitive_files = depset([ctx.outputs.jar])),
        ),
    ]

custom_rule = rule(
    attrs = {
        "deps": attr.label_list(
            allow_files = True,
            doc = "Dependencies providing the files.",
        ),
    },
    fragments = ["java"],
    outputs = {
        "jar": "lib%{name}.jar",
    },
    provides = [JavaInfo, DefaultInfo],
    implementation = _custom_rule_impl,
)
