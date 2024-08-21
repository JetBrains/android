"""
Defines repository_cache rule that builds Bazel's artifact cache directory.
"""

def _repository_cache_impl(ctx):
    """
    Copies srcs files to a directory in Bazel's repository cache format

    The format is the following:
    content_addressable/
        sha256/
            <sha256>/
                file   # the file whose content hash is <sha256>
    """
    fs = []
    for t in ctx.attr.srcs:
        for f in t[DefaultInfo].files.to_list():
            fs.append(f)
    out = ctx.actions.declare_file("repository_cache.zip")
    ctx.actions.run_shell(
        inputs = fs,
        outputs = [out],
        arguments = [f.path for f in fs],
        tools = [ctx.executable._zipper],
        command = """
          mapping=$(for cached_file in $@
          do
            sha256=$(sha256sum -b $cached_file | awk '{{print $1}}')
            echo content_addressable/sha256/${{sha256}}/file=$cached_file
          done)
          {zipper} c {out} $mapping
        """.format(
            zipper = ctx.executable._zipper.path,
            out = out.path,
        ),
    )
    return [
        DefaultInfo(
            files = depset([out]),
        ),
    ]

repository_cache = rule(
    doc = """
    A rule that builds a zip archive of a directory in Bazel's --repository-cache format.
    See: https://bazel.build/reference/command-line-reference#flag--repository_cache
         and https://bazel.build/run/build#repository-cache.
    """,
    implementation = _repository_cache_impl,
    attrs = {
        "srcs": attr.label_list(),
        "_zipper": attr.label(
            cfg = "exec",
            default = Label("@bazel_tools//tools/zip:zipper"),
            executable = True,
        ),
    },
)
