"""Helper functions to perform checks specific for kotlin rules."""

load("//bzl_tests:check_utils.bzl", "nested_struct_factory", "subjects_collection_contains_factory", "subjects_str_factory")

def kotlin_info_factory(actual, *, meta):
    return nested_struct_factory(
        actual = actual,
        meta = meta,
        attrs = dict(
            flags = subjects_collection_contains_factory,
            is_kotlin_toolchain = subjects_str_factory,
        ),
    )
