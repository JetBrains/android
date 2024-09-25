"""
Helper macros for creating generated srcjars.
"""

load("@bazel_skylib//rules:copy_file.bzl", "copy_file")
load("@rules_pkg//pkg:pkg.bzl", "pkg_zip")

def gen_srcjar(name, src, package_dir):
    """
    Defines a target build from a generated .srcjar.

    The srcjar itself is generated from a single .java file.

    Args:
      name: The name of the created java_library target.
      src: The name of a single source file, which must end with "_".
      package_dir: The directory with the srcjar that the source if placed in.

    Returns:
      A list of targets to be passed to the `test_project_package` macro
      via `all_targets`.
    """

    if not src.endswith("_"):
        fail("src must end with a `_`: ", src)

    native.java_library(
        name = name,
        srcs = [":generated.srcjar"],
        deps = [],
    )

    pkg_zip(
        name = "generated_srcjar",
        out = "generated.srcjar",  # DO NOT CHANGE - must be the same for all macro users.
        srcs = [
            ":src_copy",
        ],
    )

    copy_file(
        name = "src_copy",
        src = src,
        out = src.rstrip("_"),
    )

    return [":" + name, ":generated_srcjar", ":src_copy"]
