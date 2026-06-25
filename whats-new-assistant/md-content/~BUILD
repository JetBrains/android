load("//tools/base/bazel:utils.bzl", "flat_archive")

markdowns = glob(["md/*.md"])

flat_archive(
    name = "whats-new",
    ext = "zip",
    files = {
        f: f
        for f in markdowns
    },
)

flat_archive(
    name = "whats-new-content",
    ext = "jar",
    files = {
        "whats-new.zip": "whats-new.zip",
    },
    visibility = ["//visibility:public"],
)
