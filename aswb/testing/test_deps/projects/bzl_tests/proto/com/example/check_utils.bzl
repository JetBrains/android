"""Helper functions to perform checks specific for java rules."""

load("//bzl_tests:check_utils.bzl", "nested_struct_factory", "subjects_depset_factory")

def java_proto_info_factory(actual, *, meta):
    return nested_struct_factory(
        actual = actual,
        meta = meta,
        attrs = dict(
            proto_source_jars = subjects_depset_factory,
        ),
    )
