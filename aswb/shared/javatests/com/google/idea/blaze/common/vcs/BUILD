package(
    default_visibility = [":visibility"],
)

package_group(
    name = "visibility",
    packages = [
        "//tools/adt/idea/aswb/shared",
    ],
)

java_test(
    name = "VcsStateTest",
    size = "small",
    srcs = ["VcsStateTest.java"],
    deps = [
        "//tools/adt/idea/aswb/shared/java/com/google/idea/blaze/common/vcs",
        "//tools/adt/idea/aswb/third_party/java/junit",
        "//tools/adt/idea/aswb/third_party/java/truth",
        "@maven//:com.google.guava.guava",
    ],
)
