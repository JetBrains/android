"""An aspect which extracts the runtime classpath from a java target."""

def _runtime_classpath_impl(target, ctx):
    """The top level aspect implementation function.

    Args:
      target: Essentially a struct representing a BUILD target.

      ctx: The context object that can be used to access attributes and generate
      outputs and actions.

    Returns:
      A struct with only the output_groups provider.
    """
    ctx = ctx  # unused argument
    return struct(output_groups = {
        "runtime_classpath": _get_runtime_jars(target),
    })

def _get_runtime_jars(target):
    if JavaInfo not in target:
        return depset()
    if target[JavaInfo].compilation_info:
        return target[JavaInfo].compilation_info.runtime_classpath

    # JavaInfo constructor doesn't fill in compilation info, so just return the
    # full transitive set of runtime jars
    # https://github.com/bazelbuild/bazel/issues/10170
    return target[JavaInfo].transitive_runtime_jars

def _aspect_def(impl):
    return aspect(implementation = impl)

java_classpath_aspect = _aspect_def(_runtime_classpath_impl)
