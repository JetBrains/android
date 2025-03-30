package(default_visibility = [
    "//tools/vendor/google3/aswb/java/com/google/devtools/intellij/blaze/plugin/querysync:__subpackages__",
])

java_binary(
    name = "project_spec_builder",
    srcs = ["ProjectSpecBuilder.java"],
    main_class = "com.google.idea.blaze.qsync.util.ProjectSpecBuilder",
    visibility = ["//tools/adt/idea/aswb/querysync:__pkg__"],
    deps = [
        ":cli_context",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/shared",
        "@maven//:com.google.guava.guava",
        "@maven//:io.grpc.grpc-protobuf",
    ],
)

java_binary(
    name = "query_summarizer",
    srcs = ["QuerySummarizer.java"],
    main_class = "com.google.idea.blaze.qsync.util.QuerySummarizer",
    visibility = ["//tools/adt/idea/aswb/querysync:__pkg__"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:snapshot_java_proto",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/query",
    ],
)

java_library(
    name = "cli_context",
    srcs = ["CliContext.java"],
    deps = [
        "//tools/adt/idea/aswb/shared",
    ],
)

java_binary(
    name = "get_jar_roots",
    srcs = ["GetJarRoots.java"],
    main_class = "com.google.idea.blaze.qsync.util.GetJarRoots",
    visibility = ["//tools/adt/idea/aswb/querysync:__pkg__"],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "@maven//:com.google.guava.guava",
    ],
)
