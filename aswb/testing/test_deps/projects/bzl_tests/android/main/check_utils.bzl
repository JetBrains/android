"""Helper functions to perform checks specific for java rules."""

load("//bzl_tests:check_utils.bzl", "nested_struct_factory", "subjects_depset_factory", "subjects_file_factory", "subjects_str_factory")

def android_info_factory(actual, *, meta):
    return nested_struct_factory(
        actual = actual,
        meta = meta,
        attrs = dict(
            aar = subjects_file_factory,
            java_package = subjects_str_factory,
            manifest = subjects_file_factory,
            idl_class_jar = subjects_file_factory,
            idl_generated_java_files = subjects_depset_factory,
        ),
    )
