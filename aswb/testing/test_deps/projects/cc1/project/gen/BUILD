load("@bazel_skylib//rules:copy_file.bzl", "copy_file")

package(default_visibility = ["//visibility:public"])

copy_file(
    name = "generated_header",
    src = "TestClass.h_",
    out = "TestClass-g.h",
)
