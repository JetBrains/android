load(
    ":scope_util.bzl",
    "scopeForAndroidPackage",
    "scopeForAndroidPackageWithResources",
    "scopeForCcPackage",
    "scopeForJavaPackage",
)

package(default_visibility = [
    "//tools/adt/idea/aswb/base:__subpackages__",
    "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync:__subpackages__",
])

genquery(
    name = "java_library_no_deps_query",
    expression = "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps"),
)

genquery(
    name = "java_library_external_dep_query",
    expression = "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/externaldep:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/externaldep") + [
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/externaldep:java_info",
    ],
)

genquery(
    name = "java_library_internal_dep_query",
    expression = " + ".join([
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/internaldep:*",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps:*",
    ]),
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/internaldep") +
            scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps") + [
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/internaldep:java_info",
    ],
)

genquery(
    name = "java_library_transitive_dep_query",
    expression = " + ".join([
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/transitivedep:*",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/externaldep:*",
    ]),
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/transitivedep") +
            scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/externaldep") + [
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/externaldep:java_info",
    ],
)

genquery(
    name = "java_library_transitive_internal_dep_query",
    expression = " + ".join([
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/transitiveinternaldep:*",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/internaldep:*",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps:*",
    ]),
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/transitiveinternaldep") +
            scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/internaldep") +
            scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/nodeps") + [
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/internaldep:java_info",
    ],
)

genquery(
    name = "java_library_multi_targets",
    expression = "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/multitarget:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/multitarget:nodeps") +
            scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/multitarget:externaldep"),
)

genquery(
    name = "java_library_nested_package",
    expression = " + ".join([
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/nested:*",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/nested/inner:*",
    ]),
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/nested") +
            scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/nested/inner"),
)

genquery(
    name = "java_library_proto_dep_query",
    expression = "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/protodep:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/protodep") +
            scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/protodep:indirect_protodep"),
)

genquery(
    name = "android_lib_query",
    expression = "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/android:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForAndroidPackageWithResources("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/android"),
)

genquery(
    name = "android_aidl_source_query",
    expression = "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/aidl:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForAndroidPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/aidl"),
)

genquery(
    name = "does_dependency_path_contain_rules",
    expression = "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/deppathkinds:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/deppathkinds:protodep") +
            scopeForAndroidPackageWithResources("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/deppathkinds:android") +
            scopeForAndroidPackageWithResources("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/deppathkinds:android_using_java"),
)

genquery(
    name = "java_exported_dep_query",
    expression = "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/exports:*",
    opts = ["--output=streamed_proto"],
    scope =
        scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/exports:exports") +
        scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/exports:exported-collect"),
)

genquery(
    name = "buildincludes_query",
    expression = "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/buildincludes:* + " +
                 "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/buildincludes/sub:* + " +
                 "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/buildincludes/sub/includes:*",
    opts = ["--output=streamed_proto"],
    scope =
        scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/buildincludes:mylib_top") +
        scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/buildincludes:mylib_top2") +
        scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/buildincludes/sub:mylib_sub") +
        scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/buildincludes/sub/includes:mylib"),
)

genquery(
    name = "workspacerootincluded_query",
    expression = "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/workspacerootincluded:*",
    opts = ["--output=streamed_proto"],
    scope =
        scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/workspacerootincluded:workspacerootincluded") + [
            "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/workspacerootincluded:java_info",
        ],
)

genquery(
    name = "filegroup_query",
    expression = "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/filegroup:*",
    opts = ["--output=streamed_proto"],
    scope =
        ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/filegroup:sources"] +
        scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/filegroup:filegroup"),
)

genquery(
    name = "cc_library_query",
    expression = "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/cc:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForCcPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/cc"),
)

genquery(
    name = "cc_external_dep_query",
    expression = "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/cc_externaldep:*",
    opts = ["--output=streamed_proto"],
    scope =
        scopeForCcPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/cc_externaldep:cc_externaldep"),
)

genquery(
    name = "cc_multisrc_query",
    expression = "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/cc_multisrc:*",
    opts = ["--output=streamed_proto"],
    scope =
        scopeForCcPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/cc_multisrc:testclass") +
        scopeForCcPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/cc_multisrc:testclass2"),
)

genquery(
    name = "tags_query",
    expression = "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/tags:*",
    opts = ["--output=streamed_proto"],
    scope = scopeForJavaPackage("//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/tags"),
)

genquery(
    name = "empty_query",
    expression = "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/empty:*",
    opts = ["--output=streamed_proto"],
    scope = ["//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/empty:BUILD"],
)

java_library(
    name = "testdata",
    srcs = ["TestData.java"],
    data = [
        ":android_aidl_source_query",
        ":android_lib_query",
        ":buildincludes_query",
        ":cc_external_dep_query",
        ":cc_library_query",
        ":cc_multisrc_query",
        ":does_dependency_path_contain_rules",
        ":empty_query",
        ":filegroup_query",
        ":java_exported_dep_query",
        ":java_library_external_dep_query",
        ":java_library_internal_dep_query",
        ":java_library_multi_targets",
        ":java_library_nested_package",
        ":java_library_no_deps_query",
        ":java_library_proto_dep_query",
        ":java_library_transitive_dep_query",
        ":java_library_transitive_internal_dep_query",
        ":tags_query",
        ":workspacerootincluded_query",
    ],
    deps = [
        "//tools/adt/idea/aswb/shared",
        "@bazel_tools//tools/java/runfiles",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "project_protos",
    testonly = 1,
    srcs = ["ProjectProtos.java"],
    deps = [
        ":build_graphs",
        ":testdata",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project:project_java_proto",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync:TestUtils",
        "//tools/adt/idea/aswb/shared:exception",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "build_graphs",
    testonly = 1,
    srcs = ["BuildGraphs.java"],
    deps = [
        ":testdata",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync",
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/project",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync:TestUtils",
        "@maven//:com.google.guava.guava",
    ],
)

java_library(
    name = "java_info",
    srcs = ["JavaInfoTxt.java"],
    data = [
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/externaldep:java_info",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/externalexports:java_info",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/internaldep:java_info",
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/workspacerootincluded:java_info",
    ],
    deps = [
        "//tools/adt/idea/aswb/querysync/java/com/google/idea/blaze/qsync/java:java_target_info_java_proto",
        "@bazel_tools//tools/java/runfiles",
        "@maven//:com.google.guava.guava",
        "@maven//:com.google.protobuf.protobuf-java",
    ],
)
