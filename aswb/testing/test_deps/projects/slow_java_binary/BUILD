load("@rules_java//java:defs.bzl", "java_binary")

package(default_visibility = ["//visibility:private"])

java_binary(
    name = "slow_java_binary",
    srcs = glob(["java/com/example/sample/*.java"]),
    main_class = "com.example.sample.Main",
    visibility = ["//visibility:public"],
    deps = [],
)
