load("@bazel_skylib//rules:copy_file.bzl", "copy_file")
load("@rules_cc//cc:cc_library.bzl", "cc_library")

copy_file(
    name = "generated_header",
    src = "TestClass.h_",
    out = "TestClass.h",
)

cc_library(
    name = "cc_genhdr",
    srcs = ["TestClass.cc"],
    hdrs = ["TestClass.h"],
    deps = [],
)
