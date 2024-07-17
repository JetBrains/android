# This file is used to import Compose sources from a published source zip.
# See also the 'compose-compiler-sources' declaration in tools/base/bazel/toplevel.WORKSPACE.
# You can run `bazel query @compose-compiler-sources//...` to show the end result.

filegroup(
    name = "sources",
    srcs = glob([
        "java/**/*.java",
        "java/**/*.kt",
    ]),
    visibility = ["@//tools/adt/idea/compose-ide-plugin/compose-compiler:__pkg__"],
)

filegroup(
    name = "resources",
    srcs = glob(["resources/**"]),
    visibility = ["@//tools/adt/idea/compose-ide-plugin/compose-compiler:__pkg__"],
)
