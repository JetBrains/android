load(
    "//tools/adt/idea/aswb/aswb/testdata/projects:test_projects.bzl",
    "test_project_package",
)

package(
    default_visibility = ["//src:__subpackages__"],
)

test_project_package(
    name = "test_data",
    all_targets = [
        ":test1",
        ":test2",
        ":build_config_deb",
        ":build_config_rel",
    ],
    visibility = [
        "//tools/adt/idea/aswb/aswb/testdata/projects:__pkg__",
        "//tools/adt/idea/aswb/aswb/testdata/projects/gensrc_conflict:__pkg__",
        "//tools/vendor/google3/aswb/javatests/com/google/devtools/intellij/blaze/plugin/aswb:__pkg__",
    ],
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
