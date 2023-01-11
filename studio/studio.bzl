load("//tools/base/bazel:bazel.bzl", "ImlModuleInfo")
load("//tools/base/bazel:merge_archives.bzl", "run_singlejar")
load("//tools/base/bazel:functions.bzl", "create_option_file")
load("//tools/base/bazel:utils.bzl", "dir_archive", "is_release")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")

PluginInfo = provider(
    doc = "Info for IntelliJ plugins, including those built by the studio_plugin rule",
    fields = {
        "directory": "where to place this plugin within the plugins directory",
        "plugin_metadata": "metadata produced by the check_plugin tool",
        "module_deps": "ImlModuleInfo for modules included in this plugin",
        "lib_deps": "libraries to be included in this plugin",
        "licenses": "",
        "files": "zipped files to copy into the plugin directory",
        "files_linux": "",
        "files_mac": "",
        "files_mac_arm": "",
        "files_win": "",
        "overwrite_plugin_version": "whether to stamp version metadata into plugin.xml",
    },
)

# Valid types (and their corresponding channels) which can be specified
# by the android_studio rule.
type_channel_mappings = {
    "Canary": "Canary",
    "Beta": "Beta",
    "RC": "Beta",
    "Stable": "Stable",
}

# Types considered to be EAP. This should be a subset of
# type_channel_mappings.keys().
eap_types = ["Canary", "Beta"]

def _zipper(ctx, desc, map, out, deps = []):
    files = [f for (p, f) in map if f]
    zipper_files = [r + "=" + (f.path if f else "") + "\n" for r, f in map]
    zipper_args = ["cC" if ctx.attr.compress else "c", out.path]
    zipper_list = create_option_file(ctx, out.basename + ".res.lst", "".join(zipper_files))
    zipper_args += ["@" + zipper_list.path]
    ctx.actions.run(
        inputs = files + [zipper_list] + deps,
        outputs = [out],
        executable = ctx.executable._zipper,
        arguments = zipper_args,
        progress_message = "Creating %s zip..." % desc,
        mnemonic = "zipper",
    )

def _lnzipper(ctx, desc, filemap, out, deps = []):
    """Creates a ZIP out while preserving symlinks.

    Note: This action needs to run outside the sandbox to capture an accurate
    representation of the workspace filesystem. Otherwise, files inside the
    sandbox are created as symbolic links, and the output ZIP would only
    contain entries which are sandbox symlinks."""
    files = []
    fileargs = []
    for zip_path, f in filemap:
        files.append(f)
        fileargs.append("%s=%s\n" % (zip_path, f.path if f else ""))

    lnzipper_options = "-cs"
    if ctx.attr.compress:
        lnzipper_options += "C"

    args = [lnzipper_options, out.path]
    argfile = create_option_file(ctx, out.basename + ".res.lst", "".join(fileargs))
    args.append("@" + argfile.path)
    ctx.actions.run(
        inputs = files + [argfile] + deps,
        outputs = [out],
        executable = ctx.executable._lnzipper,
        execution_requirements = {"no-sandbox": "true", "no-remote": "true"},
        arguments = args,
        progress_message = "lnzipping %s" % desc,
        mnemonic = "lnzipper",
    )

# Bazel does not support attributes of type 'dict of string -> list of labels',
# and in order to support them we must 'unpack' the dictionary to two lists
# of keys and value. The following two functions perform the mapping back and forth
def _dict_to_lists(dict):
    keys = []
    values = []
    for k, vs in dict.items():
        keys += [k] * len(vs)
        values += vs
    return keys, values

def _lists_to_dict(keys, values):
    dict = {}
    for k, v in zip(keys, values):
        if k not in dict:
            dict[k] = []
        dict[k] += [v]
    return dict

def _module_deps(ctx, jar_names, modules):
    jars = _lists_to_dict(jar_names, modules)
    bundled = {}
    res_files = []
    for j, ms in jars.items():
        jar_file = ctx.actions.declare_file(j)
        modules_jars = [m[ImlModuleInfo].module_jars for m in ms]
        run_singlejar(ctx, modules_jars, jar_file)
        res_files += [(j, jar_file)]
    return res_files

def _get_linux(dep):
    return dep.files.to_list() + dep.files_linux.to_list()

LINUX = struct(
    name = "linux",
    jre = "jbr/",
    get = _get_linux,
    base_path = "",
    resource_path = "",
)

def _get_mac(dep):
    return dep.files.to_list() + dep.files_mac.to_list()

MAC = struct(
    name = "mac",
    jre = "jbr/",
    get = _get_mac,
    base_path = "Contents/",
    resource_path = "Contents/Resources/",
)

def _get_mac_arm(dep):
    return dep.files.to_list() + dep.files_mac_arm.to_list()

MAC_ARM = struct(
    name = "mac_arm",
    jre = "jbr/",
    get = _get_mac_arm,
    base_path = "Contents/",
    resource_path = "Contents/Resources/",
)

def _get_win(dep):
    return dep.files.to_list() + dep.files_win.to_list()

WIN = struct(
    name = "win",
    jre = "jbr/",
    get = _get_win,
    base_path = "",
    resource_path = "",
)

def _resource_deps(res_dirs, res, platform):
    files = []
    for dir, dep in zip(res_dirs, res):
        if hasattr(dep, "mappings"):
            files += [(dir + "/" + dep.mappings[f], f) for f in platform.get(dep)]
        else:
            files += [(dir + "/" + f.basename, f) for f in dep.files.to_list()]
    return files

def _check_plugin(ctx, files, external_xmls = [], verify_id = None, verify_deps = None):
    deps = None
    if verify_deps != None:
        deps = [dep[PluginInfo].plugin_metadata for dep in verify_deps]

    plugin_metadata = ctx.actions.declare_file(ctx.attr.name + ".info")
    check_args = ctx.actions.args()
    check_args.add("--out", plugin_metadata)
    check_args.add_all("--files", files)
    if verify_id:
        check_args.add("--plugin_id", verify_id)
    if deps != None:
        check_args.add_all("--deps", deps, omit_if_empty = False)
    check_args.add_all("--external_xmls", external_xmls)

    ctx.actions.run(
        inputs = files + (deps if deps else []),
        outputs = [plugin_metadata],
        executable = ctx.executable._check_plugin,
        arguments = [check_args],
        progress_message = "Analyzing %s plugin..." % ctx.attr.name,
        mnemonic = "chkplugin",
    )
    return plugin_metadata

def _studio_plugin_os(ctx, platform, module_deps, plugin_dir, plugin_metadata, out):
    spec = [(plugin_dir + "/lib/" + d, f) for (d, f) in module_deps]

    res = _resource_deps(ctx.attr.resources_dirs, ctx.attr.resources, platform)
    spec += [(plugin_dir + "/" + d, f) for (d, f) in res]

    files = [f for (p, f) in spec]
    _zipper(ctx, "%s plugin" % platform.name, spec, out, [plugin_metadata])

def _depset_subtract(depset1, depset2):
    dict1 = {e1: None for e1 in depset1.to_list()}
    return [e2 for e2 in depset2.to_list() if e2 not in dict1]

def _studio_plugin_impl(ctx):
    plugin_dir = "plugins/" + ctx.attr.directory
    module_deps = _module_deps(ctx, ctx.attr.jars, ctx.attr.modules)
    module_deps = module_deps + [(f.basename, f) for f in ctx.files.libs]
    plugin_metadata = _check_plugin(ctx, [f for (r, f) in module_deps], ctx.attr.external_xmls, ctx.attr.name, ctx.attr.deps)
    _studio_plugin_os(ctx, LINUX, module_deps, plugin_dir, plugin_metadata, ctx.outputs.plugin_linux)
    _studio_plugin_os(ctx, MAC, module_deps, plugin_dir, plugin_metadata, ctx.outputs.plugin_mac)
    _studio_plugin_os(ctx, MAC_ARM, module_deps, plugin_dir, plugin_metadata, ctx.outputs.plugin_mac_arm)
    _studio_plugin_os(ctx, WIN, module_deps, plugin_dir, plugin_metadata, ctx.outputs.plugin_win)

    # Check that all modules needed by the modules in this plugin, are either present in the
    # plugin or in its dependencies.
    need = depset(transitive =
                      [m[ImlModuleInfo].module_deps for m in ctx.attr.modules] +
                      [m[ImlModuleInfo].plugin_deps for m in ctx.attr.modules] +
                      [m[ImlModuleInfo].external_deps for m in ctx.attr.modules])
    have = depset(
        direct = ctx.attr.modules + ctx.attr.libs,
        transitive = [d[PluginInfo].module_deps for d in ctx.attr.deps] +
                     [d[PluginInfo].lib_deps for d in ctx.attr.deps] +
                     [depset(ctx.attr.deps)],
    )

    missing = [str(s.label) for s in _depset_subtract(have, need)]
    if missing:
        fail("Plugin '" + ctx.attr.name + "' has some compile-time dependencies which are not on the " +
             "runtime classpath in release builds. You may need to edit the plugin definition at " +
             str(ctx.label) + " to include the following dependencies: " + ", ".join(missing))

    return [
        PluginInfo(
            directory = ctx.attr.directory,
            files = depset(),
            files_linux = depset([ctx.outputs.plugin_linux]),
            files_mac = depset([ctx.outputs.plugin_mac]),
            files_mac_arm = depset([ctx.outputs.plugin_mac_arm]),
            files_win = depset([ctx.outputs.plugin_win]),
            plugin_metadata = plugin_metadata,
            module_deps = depset(ctx.attr.modules),
            lib_deps = depset(ctx.attr.libs),
            licenses = depset(ctx.files.licenses),
            overwrite_plugin_version = True,
        ),
    ]

_studio_plugin = rule(
    attrs = {
        "modules": attr.label_list(providers = [ImlModuleInfo], allow_empty = True),
        "libs": attr.label_list(allow_files = True),
        "licenses": attr.label_list(allow_files = True),
        "jars": attr.string_list(),
        "resources": attr.label_list(allow_files = True),
        "resources_dirs": attr.string_list(),
        "directory": attr.string(),
        "compress": attr.bool(),
        "deps": attr.label_list(providers = [PluginInfo]),
        "external_xmls": attr.string_list(),
        "_singlejar": attr.label(
            default = Label("@bazel_tools//tools/jdk:singlejar"),
            cfg = "host",
            executable = True,
        ),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
        "_check_plugin": attr.label(
            default = Label("//tools/adt/idea/studio:check_plugin"),
            cfg = "host",
            executable = True,
        ),
    },
    outputs = {
        "plugin_linux": "%{name}.linux.zip",
        "plugin_mac": "%{name}.mac.zip",
        "plugin_mac_arm": "%{name}.mac_arm.zip",
        "plugin_win": "%{name}.win.zip",
    },
    implementation = _studio_plugin_impl,
)

def _searchable_options_impl(ctx):
    searchable_options = {}
    for f in ctx.files.searchable_options:
        if not f.short_path.startswith(ctx.attr.strip_prefix):
            fail("File " + f.short_path + " does not have the given prefix.")
        path = f.short_path[len(ctx.attr.strip_prefix):]
        parts = path.split("/")
        if len(parts) < 2:
            fail("File does not follow the <plugin>/<jar> convention.")
        plugin, jar = parts[0], parts[1]
        if plugin not in searchable_options:
            searchable_options[plugin] = {}
        if jar not in searchable_options[plugin]:
            searchable_options[plugin][jar] = []
        searchable_options[plugin][jar] += [f]

    so_jars = []
    for plugin, jars in searchable_options.items():
        for jar, so_files in jars.items():
            so_jar = ctx.actions.declare_file(ctx.attr.name + ".%s.%s.zip" % (plugin, jar))
            _zipper(ctx, "%s %s searchable options" % (plugin, jar), [("search/", None)] + [("search/%s" % f.basename, f) for f in so_files], so_jar)
            so_jars += [("plugins/%s/lib/%s" % (plugin, jar), so_jar)]

    return struct(
        files = depset([f for (_, f) in so_jars]),
        searchable_options = so_jars,
    )

_searchable_options = rule(
    attrs = {
        "searchable_options": attr.label_list(allow_files = True),
        "compress": attr.bool(),
        "strip_prefix": attr.string(),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
    },
    executable = False,
    implementation = _searchable_options_impl,
)

def searchable_options(name, files, **kwargs):
    _searchable_options(
        name = name,
        compress = is_release(),
        searchable_options = files,
        **kwargs
    )

# Build an Android Studio plugin.
# This plugin is a zip file with the final layout inside Android Studio plugin's directory.
# Args
#    name: The id of the plugin (eg. intellij.android.plugin)
#    directory: The directory to use inside plugins (eg. android)
#    modules: A dictionary of the form
#             {"name.jar": ["m1" , "m2"]}
#             Where keys are the names of the jars in the libs directory, and the values
#             are the list of modules that will be in that jar.
#    resources: A dictionary of the form
#             {"dir": <files> }
#             where keys are the directories where to place the resources, and values
#             is a list of files to place there (it supports studio_data rules)
def studio_plugin(
        name,
        directory,
        modules = {},
        resources = {},
        **kwargs):
    jars, modules_list = _dict_to_lists(modules)
    resources_dirs, resources_list = _dict_to_lists(resources)

    _studio_plugin(
        name = name,
        directory = directory,
        modules = modules_list,
        jars = jars,
        resources = resources_list,
        resources_dirs = resources_dirs,
        compress = is_release(),
        **kwargs
    )

def _studio_data_impl(ctx):
    for dep in ctx.attr.files_linux + ctx.attr.files_mac + ctx.attr.files_mac_arm + ctx.attr.files_win:
        if hasattr(dep, "mappings"):
            fail("studio_data does not belong on a platform specific attribute, please add " + str(dep.label) + " to \"files\" directly")

    files = []
    mac = []
    mac_arm = []
    win = []
    linux = []
    mappings = {}
    to_map = []
    for dep in ctx.attr.files:
        files += [dep.files]
        if hasattr(dep, "mappings"):
            linux += [dep.files_linux]
            mac += [dep.files_mac]
            mac_arm += [dep.files_mac_arm]
            win += [dep.files_win]
            mappings.update(dep.mappings)
        else:
            to_map += dep.files.to_list()

    for prefix, destination in ctx.attr.mappings.items():
        for src in to_map + ctx.files.files_mac + ctx.files.files_mac_arm + ctx.files.files_linux + ctx.files.files_win:
            if src not in mappings and src.short_path.startswith(prefix):
                mappings[src] = destination + src.short_path[len(prefix):]

    dfiles = depset(order = "preorder", transitive = files)
    dlinux = depset(ctx.files.files_linux, order = "preorder", transitive = linux)
    dmac = depset(ctx.files.files_mac, order = "preorder", transitive = mac)
    dmac_arm = depset(ctx.files.files_mac_arm, order = "preorder", transitive = mac_arm)
    dwin = depset(ctx.files.files_win, order = "preorder", transitive = win)

    return struct(
        files = dfiles,
        files_linux = dlinux,
        files_mac = dmac,
        files_mac_arm = dmac_arm,
        files_win = dwin,
        mappings = mappings,
    )

_studio_data = rule(
    attrs = {
        "files": attr.label_list(allow_files = True),
        "files_linux": attr.label_list(allow_files = True),
        "files_mac": attr.label_list(allow_files = True),
        "files_mac_arm": attr.label_list(allow_files = True),
        "files_win": attr.label_list(allow_files = True),
        "mappings": attr.string_dict(
            mandatory = True,
            allow_empty = False,
        ),
    },
    executable = False,
    implementation = _studio_data_impl,
)

# A specialized version of a filegroup, that groups all the given files but also provides different
# sets of files for each platform.
# This allows grouping all files of the same concept that have different platform variants.
# Args:
#     files: A list of files present on all platforms
#     files_{linux, mac, mac_arm, win}: A list of files for each platform
#     mapping: A dictionary to map file locations and build an arbitrary file tree, in the form of
#              a dictionary from current directory to new directory.
def studio_data(name, files = [], files_linux = [], files_mac = [], files_mac_arm = [], files_win = [], mappings = {}, tags = [], **kwargs):
    _studio_data(
        name = name,
        files = files,
        files_linux = files_linux,
        files_mac = files_mac,
        files_mac_arm = files_mac_arm,
        files_win = files_win,
        mappings = mappings,
        tags = tags,
        **kwargs
    )

def _split_version(version):
    """Splits a version string into its constituent parts."""
    index_of_period = version.find(".")
    if index_of_period == -1:
        fail('Cannot split version "%s" because no "." was found in it' % version)

    micro = version[0:index_of_period]
    patch = version[index_of_period + 1:]
    return (micro, patch)

def _get_channel_info(version_type):
    """Maps a version type to information about a channel."""
    if version_type not in type_channel_mappings:
        fail('Invalid type "%s"; must be one of %s' % (version_type, str(type_channel_mappings.keys())))

    channel = type_channel_mappings[version_type]
    is_eap = version_type in eap_types

    return channel, is_eap

def _form_version_full(ctx):
    """Forms version_full based on code name, version type, and release number"""
    (channel, _) = _get_channel_info(ctx.attr.version_type)

    code_name_and_patch_components = (ctx.attr.version_code_name +
                                      " | " +
                                      "{0}.{1}.{2}")

    if channel == "Stable":
        if ctx.attr.version_release_number <= 1:
            return code_name_and_patch_components

        return code_name_and_patch_components + " Patch " + str(ctx.attr.version_release_number - 1)

    return (code_name_and_patch_components +
            " " +
            ctx.attr.version_type +
            " " +
            str(ctx.attr.version_release_number))

def _stamp(ctx, platform, zip, extra, srcs, out):
    args = ["--platform", zip.path]
    args += ["--os", platform.name]
    args += ["--version_file", ctx.version_file.path]
    args += ["--info_file", ctx.info_file.path]
    (_, is_eap) = _get_channel_info(ctx.attr.version_type)
    args += ["--eap", "true" if is_eap else "false"]
    (micro, patch) = _split_version(ctx.attr.version_micro_patch)
    args += ["--version_micro", micro]
    args += ["--version_patch", patch]
    args += ["--version_full", _form_version_full(ctx)]
    args += extra
    ctx.actions.run(
        inputs = [zip, ctx.info_file, ctx.version_file] + srcs,
        outputs = [out],
        executable = ctx.executable._stamper,
        arguments = args,
        progress_message = "Stamping %s file..." % zip.basename,
        mnemonic = "stamper",
    )

def _produce_manifest(ctx, platform):
    out = ctx.outputs.manifest
    platform_zip = platform.get(ctx.attr.platform.data)[0]

    args = ["--produce_manifest", out.path]
    args += ["--platform", platform_zip.path]
    args += ["--os", platform.name]
    args += ["--version_file", ctx.version_file.path]
    args += ["--info_file", ctx.info_file.path]
    (channel, is_eap) = _get_channel_info(ctx.attr.version_type)
    args += ["--eap", "true" if is_eap else "false"]
    (micro, patch) = _split_version(ctx.attr.version_micro_patch)
    args += ["--version_micro", micro]
    args += ["--version_patch", patch]
    args += ["--version_full", _form_version_full(ctx)]
    args += ["--channel", channel]
    args += ["--code_name", ctx.attr.version_code_name]

    ctx.actions.run(
        inputs = [platform_zip, ctx.info_file, ctx.version_file],
        outputs = [out],
        executable = ctx.executable._stamper,
        arguments = args,
        progress_message = "Producing manifest from %s..." % platform_zip.path,
        mnemonic = "stamper",
    )

def _produce_update_message_html(ctx):
    ctx.actions.write(
        output = ctx.outputs.update_message,
        content = ctx.attr.version_update_message,
    )

def _stamp_platform(ctx, platform, zip, out):
    args = ["--stamp_platform", out.path]
    _stamp(ctx, platform, zip, args, [], out)

def _stamp_plugin(ctx, platform, zip, src, dst, overwrite_plugin_version):
    args = ["--stamp_plugin", src.path, dst.path]
    if overwrite_plugin_version:
        args.append("--overwrite_plugin_version")
    _stamp(ctx, platform, zip, args, [src], dst)

def _zip_merger(ctx, zips, overrides, out):
    files = [f for (p, f) in zips + overrides]
    zipper_files = [r + "=" + f.path + "\n" for r, f in zips]
    zipper_files += [r + "=+" + f.path + "\n" for r, f in overrides]
    zipper_args = ["cC" if ctx.attr.compress else "c", out.path]
    zipper_list = create_option_file(ctx, out.basename + ".res.lst", "".join(zipper_files))
    zipper_args += ["@" + zipper_list.path]
    ctx.actions.run(
        inputs = files + [zipper_list],
        outputs = [out],
        executable = ctx.executable._zip_merger,
        arguments = zipper_args,
        progress_message = "Creating distribution zip...",
        mnemonic = "zipmerger",
    )

def _codesign(ctx, entitlements, prefix, out):
    ctx.actions.declare_file(ctx.attr.name + ".codesign.zip")
    files = [
        ("_codesign/entitlements.xml", entitlements),
    ]

    _zipper(ctx, "_codesign for macOS", files, out)

def _android_studio_prefix(ctx, platform):
    if platform == MAC or platform == MAC_ARM:
        return ctx.attr.platform.platform_info.mac_bundle_name + "/"
    return "android-studio/"

def _android_studio_os(ctx, platform, out):
    files = []
    zips = []
    overrides = []

    platform_prefix = _android_studio_prefix(ctx, platform)

    platform_zip = platform.get(ctx.attr.platform.data)[0]

    platform_plugins = platform.get(ctx.attr.platform.plugins)
    zips += [(platform_prefix, zip) for zip in [platform_zip] + platform_plugins]
    if ctx.attr.jre:
        jre_zip = ctx.actions.declare_file(ctx.attr.name + ".jre.%s.zip" % platform.name)
        jre_files = [(ctx.attr.jre.mappings[f], f) for f in platform.get(ctx.attr.jre)]

        # We want to preserve symlinks for the MAC_ARM JRE, b/185519599
        if platform == MAC_ARM:
            _lnzipper(ctx, "%s jre" % platform.name, jre_files, jre_zip)
        else:
            _zipper(ctx, "%s jre" % platform.name, jre_files, jre_zip)
        zips += [(platform_prefix + platform.base_path + platform.jre, jre_zip)]

        # b/235325129 workaround: keep `jre\` directory for windows patcher
        # TODO remove after no more patches from Dolphin
        if platform == WIN and platform.jre != "jre":
            jre_bin = ctx.actions.declare_file(ctx.attr.name + ".jre.marker")
            ctx.actions.write(jre_bin, "")
            files += [(platform.base_path + "jre/bin/.marker", jre_bin)]

    # Stamp the platform and its plugins
    platform_stamp = ctx.actions.declare_file(ctx.attr.name + ".%s.platform.stamp.zip" % platform.name)
    _stamp_platform(ctx, platform, platform_zip, platform_stamp)
    overrides += [(platform_prefix, platform_stamp)]
    for plugin in platform_plugins:
        stamp = ctx.actions.declare_file(ctx.attr.name + ".stamp.%s.%s" % (plugin.basename, platform.name))
        _stamp_plugin(ctx, platform, platform_zip, plugin, stamp, overwrite_plugin_version = False)
        overrides += [(platform_prefix, stamp)]

    dev01 = ctx.actions.declare_file(ctx.attr.name + ".dev01." + platform.name)
    ctx.actions.write(dev01, "")
    files += [(platform.base_path + "license/dev01_license.txt", dev01)]

    # Add safe mode batch file based on the current platform
    source_map = {
        LINUX: ctx.attr.files_linux,
        MAC: ctx.attr.files_mac,
        MAC_ARM: ctx.attr.files_mac_arm,
        WIN: ctx.attr.files_win,
    }[platform]

    if source_map != None:
        for key in source_map:
            files += [(platform.base_path + source_map[key], key.files.to_list()[0])]

    so_jars = [("%s%s" % (platform.base_path, jar), f) for (jar, f) in ctx.attr.searchable_options.searchable_options]
    so_extras = ctx.actions.declare_file(ctx.attr.name + ".so.%s.zip" % platform.name)
    _zipper(ctx, "%s searchable options" % platform.name, so_jars, so_extras)
    overrides += [(platform_prefix, so_extras)]

    licenses = []
    for p in ctx.attr.plugins + ctx.attr.platform.extra_plugins:
        plugin_zips = platform.get(p[PluginInfo])
        if len(plugin_zips) != 1:
            fail("Expected exactly one plugin zip; instead found: " + str(plugin_zips))
        plugin_zip = plugin_zips[0]
        stamp = ctx.actions.declare_file(ctx.attr.name + ".stamp.%s.%s.zip" % (p[PluginInfo].directory, platform.name))
        _stamp_plugin(ctx, platform, platform_zip, plugin_zip, stamp, p[PluginInfo].overwrite_plugin_version)
        overrides += [(platform_prefix + platform.base_path, stamp)]
        zips += [(platform_prefix + platform.base_path, plugin_zip)]
        licenses += [p[PluginInfo].licenses]

    files += [(platform.base_path + "license/" + f.basename, f) for f in depset([], transitive = licenses).to_list()]

    extras_zip = ctx.actions.declare_file(ctx.attr.name + ".extras.%s.zip" % platform.name)
    _zipper(ctx, "%s extras" % platform.name, files, extras_zip)
    zips += [(platform_prefix, extras_zip)]

    if platform == MAC or platform == MAC_ARM:
        codesign = ctx.actions.declare_file(ctx.attr.name + ".codesign.zip")
        _codesign(ctx, ctx.file.codesign_entitlements, platform_prefix, codesign)
        zips += [("", codesign)]

    _zip_merger(ctx, zips, overrides, out)

script_template = """\
    #!/bin/bash
    args=$@
    options=
    if [ "$1" == "--debug" ]; then
      options={vmoptions}
      args=${{@:2}}
    fi
    tmp_dir=$(mktemp -d -t android-studio-XXXXXXXXXX)
    unzip -q "{linux_file}" -d "$tmp_dir"
    STUDIO_VM_OPTIONS="$options" $tmp_dir/android-studio/bin/studio.sh $args
"""

def _android_studio_impl(ctx):
    plugins = [plugin[PluginInfo].directory for plugin in ctx.attr.plugins]
    ctx.actions.write(ctx.outputs.plugins, "".join([dir + "\n" for dir in plugins]))

    _android_studio_os(ctx, LINUX, ctx.outputs.linux)
    _android_studio_os(ctx, MAC, ctx.outputs.mac)
    _android_studio_os(ctx, MAC_ARM, ctx.outputs.mac_arm)
    _android_studio_os(ctx, WIN, ctx.outputs.win)

    _produce_manifest(ctx, LINUX)
    _produce_update_message_html(ctx)

    vmoptions = ctx.actions.declare_file("%s-debug.vmoption" % ctx.label.name)
    ctx.actions.write(vmoptions, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005")

    script = ctx.actions.declare_file("%s-run" % ctx.label.name)
    script_content = script_template.format(
        linux_file = ctx.outputs.linux.short_path,
        vmoptions = vmoptions.short_path,
    )
    ctx.actions.write(script, script_content, is_executable = True)
    runfiles = ctx.runfiles(files = [ctx.outputs.linux, vmoptions])

    # Leave everything that is not the main zips as implicit outputs
    return DefaultInfo(
        executable = script,
        files = depset([ctx.outputs.linux, ctx.outputs.mac, ctx.outputs.mac_arm, ctx.outputs.win, ctx.outputs.manifest, ctx.outputs.update_message]),
        runfiles = runfiles,
    )

_android_studio = rule(
    attrs = {
        "codesign_entitlements": attr.label(allow_single_file = True),
        "compress": attr.bool(),
        "files_linux": attr.label_keyed_string_dict(allow_files = True, default = {}),
        "files_mac": attr.label_keyed_string_dict(allow_files = True, default = {}),
        "files_mac_arm": attr.label_keyed_string_dict(allow_files = True, default = {}),
        "files_win": attr.label_keyed_string_dict(allow_files = True, default = {}),
        "jre": attr.label(),
        "platform": attr.label(),
        "plugins": attr.label_list(providers = [PluginInfo]),
        "searchable_options": attr.label(),
        "version_code_name": attr.string(),
        "version_micro_patch": attr.string(),
        "version_release_number": attr.int(),
        "version_type": attr.string(),
        "version_update_message": attr.string(),
        "_singlejar": attr.label(
            default = Label("@bazel_tools//tools/jdk:singlejar"),
            cfg = "host",
            executable = True,
        ),
        "_zip_merger": attr.label(
            default = Label("//tools/base/bazel:zip_merger"),
            cfg = "host",
            executable = True,
        ),
        "_stamper": attr.label(
            default = Label("//tools/adt/idea/studio:stamper"),
            cfg = "exec",
            executable = True,
        ),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
        "_unzipper": attr.label(
            default = Label("//tools/base/bazel:unzipper"),
            cfg = "host",
            executable = True,
        ),
        "_lnzipper": attr.label(
            default = Label("//tools/base/bazel/lnzipper:lnzipper"),
            cfg = "exec",
            executable = True,
        ),
    },
    outputs = {
        "linux": "%{name}.linux.zip",
        "mac": "%{name}.mac.zip",
        "mac_arm": "%{name}.mac_arm.zip",
        "win": "%{name}.win.zip",
        "plugins": "%{name}.plugin.lst",
        "manifest": "%{name}_build_manifest.textproto",
        "update_message": "%{name}_update_message.html",
    },
    executable = True,
    implementation = _android_studio_impl,
)

# Builds a distribution of android studio.
# Args:
#       platform: A studio_data target with the per-platform filegroups
#       jre: If include a target with the jre to bundle in.
#       plugins: A list of plugins to be bundled
#       modules: A dictionary (see studio_plugin) with modules bundled at top level
#       resources: A dictionary (see studio_plugin) with resources bundled at top level
#
# Regarding versioning information:
# - The "version_*" parameters (like "version_micro_path" and
#   "version_type") are used both for Android Studio itself and for
#   metadata produced by this rule (e.g. the build manifest and the
#   update message).
# - The full version string is produced by "_form_version_full"
#   following these rules:
#   - If the version_type is "Stable", then the first release will not
#     have a patch number, then every subsequent release will have a
#     patch number starting at 1. In addition, the word "Stable" will
#     never appear in the full version string.
#   - If the version_type is anything other than "Stable", then the
#     version_type will appear in the full version string, and
#     version_release_number will be appended directly after.
# Examples:
# - Input: version_type = "Stable", version_release_number = 1
# - Output: "Dolphin | 2022.1.1"
# - Input: version_type = "Stable", version_release_number = 2
# - Output: "Dolphin | 2022.1.1 Patch 1"
# - Input: version_type = "Stable", version_release_number = 3
# - Output: "Dolphin | 2022.1.1 Patch 2"
# - Input: version_type = "Canary", version_release_number = 1
# - Output: "Dolphin | 2022.1.1 Canary 1"
# - Input: version_type = "Canary", version_release_number = 2
# - Output: "Dolphin | 2022.1.1 Canary 2"
# - Input: version_type = "RC", version_release_number = 3
# - Output: "Dolphin | 2022.1.1 RC 3"
#
# version_release_number may or may not match the actual patch number.
# For example, we may be on patch 12 of a Beta while still calling it
# "Beta 3", meaning we've shipped 9 Canary releases and 2 Beta releases
# before patch 12. In such a case, the release_number would be 3.
def android_studio(
        name,
        **kwargs):
    _android_studio(
        name = name,
        compress = is_release(),
        **kwargs
    )

def _intellij_plugin_import_impl(ctx):
    plugin_zips = []

    # Note: platform plugins will have no files because they are already in intellij-sdk.
    if ctx.attr.files:
        plugin_dir = "plugins/" + ctx.attr.target_dir
        zip_spec = []
        for f in ctx.files.files:
            if not f.short_path.startswith(ctx.attr.strip_prefix):
                fail("File " + f.short_path + " does not start with prefix " + ctx.attr.strip_prefix)
            relpath = f.short_path[len(ctx.attr.strip_prefix):]
            zip_spec.append((plugin_dir + "/" + relpath, f))
        plugin_zip = ctx.actions.declare_file(ctx.label.name + ".zip")
        _zipper(ctx, ctx.attr.name, zip_spec, plugin_zip)
        plugin_zips.append(plugin_zip)

    java_info = java_common.merge([export[JavaInfo] for export in ctx.attr.exports])
    jars = java_info.runtime_output_jars
    plugin_metadata = _check_plugin(ctx, jars)

    return [
        java_info,
        DefaultInfo(runfiles = ctx.runfiles(files = ctx.files.files)),
        PluginInfo(
            directory = ctx.attr.target_dir,
            files = depset(plugin_zips),
            files_linux = depset(),
            files_mac = depset(),
            files_mac_arm = depset(),
            files_win = depset(),
            plugin_metadata = plugin_metadata,
            module_deps = depset(),
            lib_deps = depset(ctx.attr.exports),
            licenses = depset(),
            overwrite_plugin_version = False,
        ),
    ]

_intellij_plugin_import = rule(
    attrs = {
        # Note: platform plugins will have no files because they are already in intellij-sdk.
        "files": attr.label_list(allow_files = True),
        "strip_prefix": attr.string(),
        "target_dir": attr.string(),
        "exports": attr.label_list(providers = [JavaInfo], mandatory = True),
        "compress": attr.bool(),
        "_check_plugin": attr.label(
            default = Label("//tools/adt/idea/studio:check_plugin"),
            cfg = "host",
            executable = True,
        ),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
    },
    implementation = _intellij_plugin_import_impl,
)

def intellij_plugin_import(name, files_root_dir, target_dir, exports, **kwargs):
    """This macro is for prebuilt IntelliJ plugins that are not already part of intellij-sdk."""
    _intellij_plugin_import(
        name = name,
        files = native.glob([files_root_dir + "/**"]),
        strip_prefix = native.package_name() + "/" + files_root_dir + "/",
        target_dir = target_dir,
        exports = exports,
        compress = is_release(),
        **kwargs
    )

def _intellij_platform_impl_os(ctx, platform, data):
    files = platform.get(data)
    plugin_dir = "%splugins/" % platform.base_path
    base = []
    plugins = {}
    for file in files:
        rel = data.mappings[file]
        if not rel.startswith(plugin_dir):
            # This is not a plugin file
            base.append((rel, file))
            continue
        parts = rel[len(plugin_dir):].split("/")
        if len(parts) == 0:
            fail("Unexpected plugin file: " + rel)
        plugin = parts[0]
        if plugin not in plugins:
            plugins[plugin] = []
        plugins[plugin].append((rel, file))

    base_zip = ctx.actions.declare_file("%s.%s.zip" % (ctx.label.name, platform.name))
    _zipper(ctx, "base %s platform zip" % platform.name, base, base_zip)

    plugin_zips = []
    for plugin, files in plugins.items():
        plugin_zip = ctx.actions.declare_file("%s.plugin.%s.%s.zip" % (ctx.label.name, plugin, platform.name))
        _zipper(ctx, "platform plugin %s %s zip" % (plugin, platform.name), files, plugin_zip)
        plugin_zips.append(plugin_zip)
    return base_zip, plugin_zips

def _intellij_platform_impl(ctx):
    base_linux, plugins_linux = _intellij_platform_impl_os(ctx, LINUX, ctx.attr.studio_data)
    base_win, plugins_win = _intellij_platform_impl_os(ctx, WIN, ctx.attr.studio_data)
    base_mac, plugins_mac = _intellij_platform_impl_os(ctx, MAC, ctx.attr.studio_data)
    base_mac_arm, plugins_mac_arm = _intellij_platform_impl_os(ctx, MAC_ARM, ctx.attr.studio_data)

    runfiles = ctx.runfiles(files = ctx.files.data)
    files = depset([base_linux, base_mac, base_mac_arm, base_win])
    return struct(
        providers = [
            DefaultInfo(files = files, runfiles = runfiles),
            java_common.merge([export[JavaInfo] for export in ctx.attr.exports]),
        ],
        data = struct(
            files = depset([]),
            files_linux = depset([base_linux]),
            files_mac = depset([base_mac]),
            files_mac_arm = depset([base_mac_arm]),
            files_win = depset([base_win]),
            mappings = {},
        ),
        plugins = struct(
            files = depset([]),
            files_linux = depset(plugins_linux),
            files_mac = depset(plugins_mac),
            files_mac_arm = depset(plugins_mac_arm),
            files_win = depset(plugins_win),
            mappings = {},
        ),
        extra_plugins = ctx.attr.extra_plugins,
        platform_info = struct(
            mac_bundle_name = ctx.attr.mac_bundle_name,
        ),
    )

_intellij_platform = rule(
    attrs = {
        "exports": attr.label_list(providers = [JavaInfo]),
        "extra_plugins": attr.label_list(providers = [PluginInfo]),
        "data": attr.label_list(allow_files = True),
        "studio_data": attr.label(),
        "compress": attr.bool(),
        "mac_bundle_name": attr.string(),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
    },
    implementation = _intellij_platform_impl,
)

def intellij_platform(
        name,
        src,
        spec,
        extra_plugins,
        **kwargs):
    jvm_import(
        name = name + "_jars",
        jars = select({
            "//tools/base/bazel:windows": [src + "/windows/android-studio" + jar for jar in spec.jars + spec.jars_windows],
            "//tools/base/bazel:darwin": [src + "/darwin/android-studio/Contents" + jar for jar in spec.jars + spec.jars_darwin],
            "//tools/base/bazel:darwin_arm64": [src + "/darwin_aarch64/android-studio/Contents" + jar for jar in spec.jars + spec.jars_darwin_aarch64],
            "//conditions:default": [src + "/linux/android-studio" + jar for jar in spec.jars + spec.jars_linux],
        }),
    )

    _intellij_platform(
        name = name,
        exports = [":" + name + "_jars"],
        extra_plugins = extra_plugins,
        compress = is_release(),
        mac_bundle_name = spec.mac_bundle_name,
        studio_data = name + ".data",
        visibility = ["//visibility:public"],
        # Local linux sandbox does not support spaces in names, so we exclude some files
        # Otherwise we get: "link or target filename contains space"
        data = select({
            "//tools/base/bazel:windows": native.glob(
                include = [src + "/windows/android-studio/**"],
                exclude = [src + "/windows/android-studio/plugins/textmate/lib/bundles/**"],
            ),
            "//tools/base/bazel:darwin": native.glob(
                include = [src + "/darwin/android-studio/**"],
                exclude = [src + "/darwin/android-studio/Contents/plugins/textmate/lib/bundles/**"],
            ),
            "//tools/base/bazel:darwin_arm64": native.glob(
                include = [src + "/darwin_aarch64/android-studio/**"],
                exclude = [src + "/darwin_aarch64/android-studio/Contents/plugins/textmate/lib/bundles/**"],
            ),
            "//conditions:default": native.glob(
                include = [src + "/linux/android-studio/**"],
                exclude = [src + "/linux/android-studio/plugins/textmate/lib/bundles/**"],
            ),
        }),
    )

    resource_jars = {
        "mac": src + "/darwin/android-studio/Contents/lib/resources.jar",
        "mac_arm": src + "/darwin_aarch64/android-studio/Contents/lib/resources.jar",
        "linux": src + "/linux/android-studio/lib/resources.jar",
        "windows": src + "/windows/android-studio/lib/resources.jar",
    }
    native.py_test(
        name = name + "_version_test",
        srcs = ["//tools/adt/idea/studio:sdk_version_test.py"],
        main = "sdk_version_test.py",
        tags = ["no_test_windows", "no_test_mac"],
        data = resource_jars.values(),
        env = {
            "expected_major_version": spec.major_version,
            "expected_minor_version": spec.minor_version,
            "intellij_resource_jars": ",".join(
                [k + "=" + "$(execpath :" + v + ")" for k, v in resource_jars.items()],
            ),
        },
        deps = ["//tools/adt/idea/studio:intellij"],
    )

    # Expose lib/resources.jar as a separate target
    native.java_import(
        name = name + "-resources-jar",
        jars = select({
            "//tools/base/bazel:windows": [src + "/windows/android-studio/lib/resources.jar"],
            "//tools/base/bazel:darwin": [src + "/darwin/android-studio/Contents/lib/resources.jar"],
            "//tools/base/bazel:darwin_arm64": [src + "/darwin_aarch64/android-studio/Contents/lib/resources.jar"],
            "//conditions:default": [src + "/linux/android-studio/lib/resources.jar"],
        }),
        visibility = ["//visibility:public"],
    )

    # Expose build.txt from the prebuilt SDK
    native.filegroup(
        name = name + "-build-txt",
        srcs = select({
            "//tools/base/bazel:windows": [src + "/windows/android-studio/build.txt"],
            "//tools/base/bazel:darwin": [src + "/darwin/android-studio/Contents/Resources/build.txt"],
            "//tools/base/bazel:darwin_arm64": [src + "/darwin_aarch64/android-studio/Contents/Resources/build.txt"],
            "//conditions:default": [src + "/linux/android-studio/build.txt"],
        }),
        visibility = ["//visibility:public"],
    )

    # TODO: merge this into the intellij_platform rule.
    dir_archive(
        name = name + "-full-linux",
        dir = "prebuilts/studio/intellij-sdk/" + src + "/linux/android-studio",
        files = native.glob([src + "/linux/android-studio/**"]),
        visibility = ["//visibility:public"],
    )

    studio_data(
        name = name + ".data",
        files_linux = native.glob([src + "/linux/**"]),
        files_mac = native.glob([src + "/darwin/**"]),
        files_mac_arm = native.glob([src + "/darwin_aarch64/**"]),
        files_win = native.glob([src + "/windows/**"]),
        mappings = {
            "prebuilts/studio/intellij-sdk/%s/linux/android-studio/" % src: "",
            "prebuilts/studio/intellij-sdk/%s/darwin/android-studio/" % src: "",
            "prebuilts/studio/intellij-sdk/%s/darwin_aarch64/android-studio/" % src: "",
            "prebuilts/studio/intellij-sdk/%s/windows/android-studio/" % src: "",
        },
    )

    for plugin, jars in spec.plugin_jars.items():
        jars_target_name = "%s-plugin-%s_jars" % (name, plugin)
        _gen_plugin_jars_import_target(jars_target_name, src, spec, plugin, jars)
        _intellij_plugin_import(
            name = name + "-plugin-%s" % plugin,
            exports = [":" + jars_target_name],
            visibility = ["//visibility:public"],
        )

    jvm_import(
        name = name + "-updater",
        jars = [src + "/updater-full.jar"],
        visibility = ["//visibility:public"],
    )

def _gen_plugin_jars_import_target(name, src, spec, plugin, jars):
    """Generates a jvm_import target for the specified plugin."""
    add_windows = spec.plugin_jars_windows[plugin] if plugin in spec.plugin_jars_windows else []
    jars_windows = [src + "/windows/android-studio/plugins/" + plugin + "/lib/" + jar for jar in jars + add_windows]
    add_darwin = spec.plugin_jars_darwin[plugin] if plugin in spec.plugin_jars_darwin else []
    jars_darwin = [src + "/darwin/android-studio/Contents/plugins/" + plugin + "/lib/" + jar for jar in jars + add_darwin]
    add_darwin_aarch64 = spec.plugin_jars_darwin_aarch64[plugin] if plugin in spec.plugin_jars_darwin_aarch64 else []
    jars_darwin_aarch64 = [src + "/darwin_aarch64/android-studio/Contents/plugins/" + plugin + "/lib/" + jar for jar in jars + add_darwin_aarch64]
    add_linux = spec.plugin_jars_linux[plugin] if plugin in spec.plugin_jars_linux else []
    jars_linux = [src + "/linux/android-studio/plugins/" + plugin + "/lib/" + jar for jar in jars + add_linux]

    jvm_import(
        name = name,
        jars = select({
            "//tools/base/bazel:windows": jars_windows,
            "//tools/base/bazel:darwin": jars_darwin,
            "//tools/base/bazel:darwin_arm64": jars_darwin_aarch64,
            "//conditions:default": jars_linux,
        }),
    )
