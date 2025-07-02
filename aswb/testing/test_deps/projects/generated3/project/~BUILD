load("@rules_java//java:defs.bzl", "java_library")

package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "consumer",
    srcs = glob(["java/com/example/*.java"]),
    deps = [":generated"],
)

# Targets to produce a generated .srcjar
java_library(
    name = "generated",
    srcs = [":generated.srcjar"],
)

genrule(
    name = "generated.srcjar",
    srcs = ["java/com/example/Sample1.java_"],
    outs = ["sample1.srcjar"],
    cmd = " ".join([
        "WORKING_DIR=generated.srcjar_tmp;",
        "rm -rf $$WORKING_DIR;",
        "mkdir $$WORKING_DIR;",
        "cat $< > $$WORKING_DIR/Sample1.java;",
        "zip -qX $@ $$WORKING_DIR/Sample1.java",
    ]),
)
