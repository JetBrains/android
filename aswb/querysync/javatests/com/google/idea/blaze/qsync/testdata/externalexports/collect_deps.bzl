load(
    "//tools/adt/idea/aswb/aspect:build_dependencies.bzl",
    "collect_dependencies_aspect_for_tests",
    "collect_dependencies_for_test",
    "write_java_info_txt_rule_for_tests",
)

def _collect_deps_impl(target, ctx):
    return collect_dependencies_for_test(target, ctx, include = [
        "//tools/adt/idea/aswb/querysync/javatests/com/google/idea/blaze/qsync/testdata/externalexports",
    ])

collect_deps = collect_dependencies_aspect_for_tests(_collect_deps_impl)

java_info_txt = write_java_info_txt_rule_for_tests(collect_deps)
