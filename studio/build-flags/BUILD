load("//tools/base/common:version.bzl", "LAST_STABLE_BUILD_VERSION")

java_library(
    name = "build-flags",
    srcs = ["BuildFlags.java"],
    resources = [":build.flags"],
    visibility = ["//visibility:public"],
    deps = [
        "//prebuilts/studio/intellij-sdk:studio-sdk",
    ],
)

genrule(
    name = "generate-build-flags",
    outs = ["build.flags"],
    cmd = select({
        "//conditions:default": "echo '' > $@",
        "//tools/adt/idea/studio:nightly-build": (
            "echo -e \"" +
            "gradle.ide.agp.version.to.use=" + LAST_STABLE_BUILD_VERSION + "\n" +
            "\" > $@"
        ),
    }),
)

java_test(
    name = "BuildFlagsTest",
    srcs = ["BuildFlagsTest.java"],
    test_class = "com.android.tools.idea.flags.overrides.BuildFlagsTest",
    deps = [
        ":build-flags",
        "@maven//:com.google.truth.extensions.truth-java8-extension",
        "@maven//:com.google.truth.truth",
        "@maven//:junit.junit",
    ],
)
