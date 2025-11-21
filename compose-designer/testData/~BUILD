load("//tools/adt/idea/compose-designer/testData:dependencies.bzl", "SIMPLE_COMPOSE_APPLICATION_DEPS")
load("//tools/base/bazel:maven.bzl", "maven_repository")

filegroup(
    name = "test_projects",
    srcs = glob(["projects/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "classloader",
    srcs = glob(["classloader/**"]),
    visibility = ["//visibility:public"],
)

filegroup(
    name = "composepreview",
    srcs = glob(["projects/composepreview/**"]),
    visibility = ["//visibility:public"],
)

maven_repository(
    name = "simple_compose_application_test_deps",
    artifacts = SIMPLE_COMPOSE_APPLICATION_DEPS,
    visibility = ["//visibility:public"],
)
