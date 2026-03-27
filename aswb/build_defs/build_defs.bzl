"""Custom build macros for IntelliJ plugin handling.
"""

load("//tools/base/bazel:kotlin.bzl", "kotlin_library")
load(
    ":intellij_plugin.bzl",
    _intellij_plugin = "intellij_plugin",
    _intellij_plugin_library = "intellij_plugin_library",
    _optional_plugin_xml = "optional_plugin_xml",
)

# Re-export these symbols
intellij_plugin = _intellij_plugin
intellij_plugin_library = _intellij_plugin_library
optional_plugin_xml = _optional_plugin_xml

def aswb_library(name, testonly = False, **kwargs):
    """A regular ASwB target."""
    kotlin_library(
        name = name,
        testonly = testonly,
        jvm_target = "21",
        lint_is_test_sources = testonly,
        stdlib = None,  # kotlin-stdlib already comes bundled in IntelliJ.
        **kwargs
    )

def merged_plugin_xml(name, srcs, **kwargs):
    """Merges N plugin.xml files together."""
    merge_tool = "//tools/adt/idea/aswb/build_defs:merge_xml"
    native.genrule(
        name = name,
        srcs = srcs,
        outs = [name + ".xml"],
        cmd = "./$(location {merge_tool}) $(SRCS) > $@".format(
            merge_tool = merge_tool,
        ),
        tools = [merge_tool],
        **kwargs
    )

def _optstr(name, value):
    return ("--" + name) if value else ""

def stamped_plugin_xml(
        name,
        plugin_xml = None,
        plugin_id = None,
        plugin_name = None,
        stamp_since_build = False,
        stamp_until_build = False,
        version = None,
        version_file = None,
        changelog_file = None,
        description_file = None,
        vendor_file = None,
        application_info_json = None,
        **kwargs):
    """Stamps a plugin xml file with the IJ build number.

    Args:
      name: name of this target
      plugin_xml: target plugin_xml to stamp
      plugin_id: the plugin ID to stamp
      plugin_name: the plugin name to stamp
      stamp_since_build: Add build number to idea-version since-build.
      stamp_until_build: Use idea-version until-build to limit plugin to the
          current major release.
      version: A version number to stamp.
      version_file: A file with the version number to be included.
      changelog_file: A file with the changelog to be included.
      description_file: A file containing a plugin description to be included.
      vendor_file: A file containing the vendor info to be included.
      application_info_json: A product info file, if provided, overrides the default.
      **kwargs: Any additional arguments to pass to the final target.
    """
    stamp_tool = "//tools/adt/idea/aswb/build_defs:stamp_plugin_xml"

    api_version_txt_name = name + "_api_version"
    api_version_txt(
        name = api_version_txt_name,
        check_eap = True,
        application_info_json = application_info_json,
    )

    args = [
        "./$(location {stamp_tool})",
        "--api_version_txt=$(location {api_version_txt_name})",
        "{stamp_since_build}",
        "{stamp_until_build}",
    ]
    srcs = [api_version_txt_name]

    if plugin_xml:
        args.append("--plugin_xml=$(location {plugin_xml})")
        srcs.append(plugin_xml)

    if version and version_file:
        fail("Cannot supply both version and version_file")

    if plugin_id:
        args.append("--plugin_id=%s" % plugin_id)

    if plugin_name:
        args.append("--plugin_name='%s'" % plugin_name)

    if version:
        args.append("--version='%s'" % version)

    if version_file:
        args.append("--version_file=$(location {version_file})")
        srcs.append(version_file)

    if changelog_file:
        args.append("--changelog_file=$(location {changelog_file})")
        srcs.append(changelog_file)

    if description_file:
        args.append("--description_file=$(location {description_file})")
        srcs.append(description_file)

    if vendor_file:
        args.append("--vendor_file=$(location {vendor_file})")
        srcs.append(vendor_file)

    cmd = " ".join(args).format(
        plugin_xml = plugin_xml,
        api_version_txt_name = api_version_txt_name,
        stamp_tool = stamp_tool,
        stamp_since_build = _optstr(
            "stamp_since_build",
            stamp_since_build,
        ),
        stamp_until_build = _optstr(
            "stamp_until_build",
            stamp_until_build,
        ),
        version_file = version_file,
        changelog_file = changelog_file,
        description_file = description_file,
        vendor_file = vendor_file,
    ) + "> $@"

    native.genrule(
        name = name,
        srcs = srcs,
        outs = [name + ".xml"],
        cmd = cmd,
        tools = [stamp_tool],
        **kwargs
    )

def api_version_txt(name, check_eap, application_info_json = None, **kwargs):
    """Produces an api_version.txt file with the api version, including the product code.

    Args:
      name: name of this target
      check_eap: whether the produced api_version should mark the build number with `EAP` if it is or this is not needed.
      application_info_json: A product info file, if provided, overrides the default.
      **kwargs: Any additional arguments to pass to the final target.
    """
    if application_info_json == None:
        application_info_json = "@intellij//:product-info"
    api_version_txt_tool = "//tools/adt/idea/aswb/build_defs:api_version_txt"

    args = [
        "./$(location {api_version_txt_tool})",
        "--application_info_json=$(location {application_info_json})",
    ]

    if check_eap:
        args.append("--check_eap")

    cmd = " ".join(args).format(
        application_info_json = application_info_json,
        api_version_txt_tool = api_version_txt_tool,
    ) + "> $@"
    native.genrule(
        name = name,
        srcs = [application_info_json],
        outs = [name + ".txt"],
        cmd = cmd,
        tools = [api_version_txt_tool],
        **kwargs
    )

def _transition_impl(_, attr):
    return {"//command_line_option:javacopt": attr.java_copts}

_java_copts_transition = transition(
    implementation = _transition_impl,
    inputs = [],
    outputs = ["//command_line_option:javacopt"],
)

repackaged_files_data = provider()

def _strip_external_workspace_prefix(short_path):
    """If this target is sitting in an external workspace, return the workspace-relative path."""
    if short_path.startswith("../") or short_path.startswith("external/"):
        return "/".join(short_path.split("/")[2:])
    return short_path

def unescape_filenames(name, srcs):
    """Macro to generate files with spaces in their names instead of underscores.

    For each file in the srcs, a file will be generated with the same name but with all underscores
    replaced with spaces.

    Args:
      name: The name of the generator rule
      srcs: A list of source files to process
    """
    outs = [s.replace("_", " ") for s in srcs]
    cmd = "&&".join(["cp \"{}\" $(@D)/\"{}\"".format(s, d) for (s, d) in zip(srcs, outs)])
    native.genrule(
        name = name,
        srcs = srcs,
        outs = outs,
        cmd = cmd,
    )

def combine_visibilities(*args):
    """
    Concatenates the given lists of visibilities and returns the combined list.

    If one of the given elements is //visibility:public then return //visibility:public
    If one of the lists is None, skip it.
    If the result list is empty, then return None.

    Args:
      *args: the list of visibilities lists to combine
    Returns:
      the concatenated visibility targets list
    """
    res = []
    for arg in args:
        if arg:
            for visibility in arg:
                if visibility == "//visibility:public":
                    return ["//visibility:public"]
                res.append(visibility)
    if res == []:
        return None
    return res
