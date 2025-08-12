load("@rules_java//java:defs.bzl", "java_library")

package(
    default_visibility = ["//visibility:public"],
)

java_library(
    name = "test1",
    srcs = [
        "TestClass1.java",
        "build_config_rel",
    ],
    deps = [],
)

java_library(
    name = "test2",
    srcs = [
        "TestClass2.java",
        ":build_config_deb",
    ],
    deps = [],
)

# Here, we are emulating what may happen in a real build that includes multiple
# configurations. In a more realistic setting, there would be a single target here
# whose output would depend on the configuration, and config transitions in the
# build would result in it being built more than once with different configs.
# But to keep the test data simpler, we simply have two rules that produce the
# same java class, as this exercises the same codepaths in the IDE.

genrule(
    name = "build_config_rel",
    outs = ["rel/com/example/BuildConfig.java"],
    cmd = "\n".join([
        "cat <<EOF > $@",
        "package com.example;",
        "",
        "public class BuildConfig {",
        "  public static final String TYPE = \"release\";",
        "}",
        "EOF",
    ]),
)

genrule(
    name = "build_config_deb",
    outs = ["deb/com/example/BuildConfig.java"],
    cmd = "\n".join([
        "cat <<EOF > $@",
        "package com.example;",
        "",
        "public class BuildConfig {",
        "  public static final String TYPE = \"debug\";",
        "}",
        "EOF",
    ]),
)
