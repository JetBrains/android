load("@rules_java//java:defs.bzl", "java_binary")

package(
    default_visibility = ["//visibility:public"],
)

java_binary(
    name = "Binary2",
    srcs = glob(["java/com/example/binaries/*.java"]),
)

java_binary(
    name = "another_binary",
    srcs = glob(["java/com/example/binaries/*.java"]),
    main_class = "com.example.binaries.Binary1",
)
