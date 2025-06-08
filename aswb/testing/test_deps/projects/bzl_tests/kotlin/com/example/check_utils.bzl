"""Helper functions to perform checks specific for java rules."""

load("@rules_testing//lib/private:struct_subject.bzl", "StructSubject")
load("//bzl_tests:check_utils.bzl", "collection_struct_contains_exactly", "nested_struct_factory", "subjects_depset_factory", "subjects_file_factory")

def java_info_factory(actual, *, meta):
    return nested_struct_factory(
        actual = actual,
        meta = meta,
        attrs = dict(
            compile_jars_depset = subjects_depset_factory,
            java_output_compile_jars = subjects_depset_factory,
            generated_outputs = generated_outputs_factory,
            transitive_compile_time_jars_depset = subjects_depset_factory,
            transitive_runtime_jars_depset = subjects_depset_factory,
        ),
    )

def generated_outputs_factory(actual, *, meta):
    """Creates a `StructSubject`, which is a thin wrapper around a [`struct`].

    This is a customized `StructSubject` specific for java_output of java_info.generated_outputs in blaze aspect test. It's a complicated
    nested struct, so we provide a factory to do that.

    Args:
        actual: ([`struct`]) the struct to wrap.
        meta: ([`ExpectMeta`]) object of call context information.

    Returns:
        [`StructSubject`].
    """

    # The full attributes of java_output list are listed in
    # https://bazel.build/versions/6.0.0/rules/lib/java_output
    # But only these 3 are used in build_dependencies.bzl, so only list them so far.
    # Add more if we need more.
    attrs = dict(
        generated_source_jar = subjects_file_factory,
        generated_class_jar = subjects_file_factory,
    )
    values = [StructSubject.new(
        a,
        meta = meta,
        attrs = attrs,
    ) for a in actual]
    return struct(
        actual = values,
        contains_exactly = lambda *a, **k: collection_struct_contains_exactly(struct(
            actual = values,
            meta = meta,
            attrs = attrs,
        ), *a, **k),
    )
