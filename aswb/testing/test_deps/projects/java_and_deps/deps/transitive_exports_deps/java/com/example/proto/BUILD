java_library(
    name = "proto",
    srcs = ["empty.srcjar"],
    visibility = ["//visibility:public"],
    exports = [":proto_real_jar"],
    deps = ["//java_and_deps/deps/transitive_exports_deps/java/com/example/truth"],
)

java_library(
    name = "proto_real_jar",
    srcs = glob(["*.java"]),
    deps = ["//java_and_deps/deps/transitive_exports_deps/java/com/example/liteproto"],
)
