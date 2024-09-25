load("@bazel_skylib//rules:build_test.bzl", "build_test")

package(default_visibility = [
    ":__subpackages__",
    "//tools/adt/idea/aswb/aswb:__subpackages__",
    "//tools/adt/idea/aswb/base:__subpackages__",
    "//tools/adt/idea/aswb/cpp:__subpackages__",
    "//tools/adt/idea/aswb/java:__subpackages__",
    "//tools/adt/idea/aswb/querysync/javatests:__subpackages__",
    "//tools/vendor/google3/aswb/java/com/google/devtools/intellij/blaze/plugin/base:__subpackages__",
    "//tools/vendor/google3/aswb/java/com/google/devtools/intellij/blaze/plugin/cpp:__subpackages__",
    "//tools/vendor/google3/aswb/java/com/google/devtools/intellij/blaze/plugin/querysync:__subpackages__",
    "//tools/vendor/google3/aswb/java/com/google/devtools/intellij/g3plugins/services:__subpackages__",
])

# A build rule to expose individual targets under java/ to users
java_library(
    name = "querysync",
    exports = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/artifacts",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/cc",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/cc:cc_compilation_info_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/deps",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java:java_target_info_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:language_class_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query:querysummary_java_proto",
    ],
)

build_test(
    name = "cli_tools_build_test",
    targets = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/util:project_spec_builder",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/util:query_summarizer",
    ],
)
