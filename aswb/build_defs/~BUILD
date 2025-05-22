# Description:
#
# Scripts for building IntelliJ plugins

load(
    "//tools/adt/idea/aswb:build-visibility.bzl",
    "INTELLIJ_PLUGINS_VISIBILITY",
)

package(default_visibility = INTELLIJ_PLUGINS_VISIBILITY)

licenses(["notice"])

py_binary(
    name = "merge_xml",
    srcs = ["merge_xml.py"],
    python_version = "PY3",
)

py_binary(
    name = "stamp_plugin_xml",
    srcs = ["stamp_plugin_xml.py"],
    python_version = "PY3",
)

py_binary(
    name = "api_version_txt",
    srcs = ["api_version_txt.py"],
    python_version = "PY3",
)

py_binary(
    name = "append_optional_xml_elements",
    srcs = ["append_optional_xml_elements.py"],
    python_version = "PY3",
)

py_binary(
    name = "package_meta_inf_files",
    srcs = ["package_meta_inf_files.py"],
    python_version = "PY3",
)

py_binary(
    name = "zip_plugin_files",
    srcs = ["zip_plugin_files.py"],
    python_version = "PY3",
)
