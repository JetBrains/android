load("@rules_java//java:defs.bzl", "java_library")

java_library(
    name = "consumer",
    srcs = glob([
        "javatests/com/example/*.java",
    ]) + [":generated_srcjar"],
)

# Targets to produce a generated .srcjar

genrule(
    name = "generated_srcjar",
    srcs = ["javatests/com/example/Sample1.java_"],
    outs = ["generated.srcjar"],
    cmd = " ".join([
        "WORKING_DIR=generated.srcjar_tmp;",
        "rm -rf $$WORKING_DIR;",
        "mkdir $$WORKING_DIR;",
        "cat $< > $$WORKING_DIR/Sample1.java;",
        "zip -qX $@ $$WORKING_DIR/Sample1.java",
    ]),
)
