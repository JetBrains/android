"""Helper functions to perform checks specific for java rules."""

load("@rules_testing//lib/private:struct_subject.bzl", "StructSubject")
load("//bzl_tests:check_utils.bzl", "collection_struct_contains_exactly", "nested_struct_factory", "subjects_depset_factory", "subjects_file_factory", "subjects_str_factory", "target_factory")

def compilation_context_factory(actual, *, meta):
    return nested_struct_factory(
        actual = actual,
        meta = meta,
        attrs = dict(
            defines = subjects_depset_factory,
            direct_headers = subjects_depset_factory,
            direct_private_headers = subjects_depset_factory,
            direct_public_headers = subjects_depset_factory,
            direct_textual_headers = subjects_depset_factory,
            external_includes = subjects_depset_factory,
            framework_includes = subjects_depset_factory,
            headers = subjects_depset_factory,
            includes = subjects_depset_factory,
            local_defines = subjects_depset_factory,
            quote_includes = subjects_depset_factory,
            system_includes = subjects_depset_factory,
            validation_artifacts = subjects_depset_factory,
        ),
    )

def cc_toolchain_info_factory(actual, *, meta):
    return nested_struct_factory(
        actual = actual,
        meta = meta,
        attrs = dict(
            id = subjects_str_factory,
            compiler_executable = subjects_str_factory,
            cpu = subjects_str_factory,
            compiler = subjects_str_factory,
            target_name = subjects_str_factory,
            built_in_include_directories = subjects_str_factory,
            c_options = subjects_str_factory,
            cpp_options = subjects_str_factory,
        ),
    )
