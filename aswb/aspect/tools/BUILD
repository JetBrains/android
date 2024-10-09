#
# Description:
# Tools needed by the bazel plugin's aspect.
#

package(default_visibility = ["//tools/adt/idea/aswb/aspect:__pkg__"])

licenses(["notice"])

# To prevent versioning conflicts when developing internally, we always use the same
# guava version bundled with the IntelliJ plugin API.
java_library(
    name = "guava",
    visibility = ["//visibility:private"],
    exports = ["//tools/vendor/google/aswb/plugin_api:guava_for_external_binaries"],
)

java_library(
    name = "lib",
    srcs = glob(["src/**/*.java"]),
    javacopts = ["-source 8 -target 8"],
    deps = [
        ":guava",
        "//prebuilts/tools/common/m2:jsr305-2.0.1",
        "//tools/adt/idea/aswb/proto:proto_deps",
    ],
)

java_binary(
    name = "CreateAar",
    main_class = "com.google.idea.blaze.aspect.CreateAar",
    visibility = ["//visibility:public"],
    runtime_deps = [":lib"],
)

java_binary(
    name = "JarFilter",
    main_class = "com.google.idea.blaze.aspect.JarFilter",
    visibility = ["//visibility:public"],
    runtime_deps = [":lib"],
)

java_binary(
    name = "PackageParser",
    jvm_flags = [
        # quiet warnings from com.google.protobuf.UnsafeUtil,
        # see: https://github.com/google/protobuf/issues/3781
        "-XX:+IgnoreUnrecognizedVMOptions",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
    ],
    main_class = "com.google.idea.blaze.aspect.PackageParser",
    visibility = ["//visibility:public"],
    runtime_deps = [":lib"],
)

java_library(
    name = "test_lib",
    testonly = 1,
    exports = [
        ":guava",
        ":lib",
        "//tools/adt/idea/aswb/proto:proto_deps",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/vendor/google/aswb/plugin_api:jsr305",
        "//tools/vendor/google/aswb/plugin_api:truth",
    ],
)

java_test(
    name = "JarFilterTest",
    size = "medium",
    srcs = ["tests/unittests/com/google/idea/blaze/aspect/JarFilterTest.java"],
    test_class = "com.google.idea.blaze.aspect.JarFilterTest",
    deps = [":test_lib"],
)

java_test(
    name = "PackageParserTest",
    size = "small",
    srcs = ["tests/unittests/com/google/idea/blaze/aspect/PackageParserTest.java"],
    test_class = "com.google.idea.blaze.aspect.PackageParserTest",
    deps = [
        ":test_lib",
        "//tools/vendor/google/aswb/plugin_api",
    ],
)

java_test(
    name = "OptionParserTest",
    size = "small",
    srcs = ["tests/unittests/com/google/idea/blaze/aspect/OptionParserTest.java"],
    test_class = "com.google.idea.blaze.aspect.OptionParserTest",
    deps = [":test_lib"],
)

java_test(
    name = "ArtifactLocationParserTest",
    size = "small",
    srcs = ["tests/unittests/com/google/idea/blaze/aspect/ArtifactLocationParserTest.java"],
    test_class = "com.google.idea.blaze.aspect.ArtifactLocationParserTest",
    deps = [":test_lib"],
)

java_test(
    name = "CreateAarTestTest",
    size = "small",
    srcs = ["tests/unittests/com/google/idea/blaze/aspect/CreateAarTest.java"],
    test_class = "com.google.idea.blaze.aspect.CreateAarTest",
    deps = [":test_lib"],
)
