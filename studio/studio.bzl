load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("//tools/adt/idea/studio/rules:app-icon.bzl", "AppIconInfo", "replace_app_icon")
load("//tools/base/bazel:bazel.bzl", "ImlModuleInfo", "iml_test")
load("//tools/base/bazel:expand_template.bzl", "expand_template_ex")
load("//tools/base/bazel:functions.bzl", "create_option_file")
load("//tools/base/bazel:jvm_import.bzl", "jvm_import")
load("//tools/base/bazel:merge_archives.bzl", "run_singlejar")
load("//tools/base/bazel:utils.bzl", "dir_archive", "is_release")

PluginInfo = provider(
    doc = "Info for IntelliJ plugins, including those built by the studio_plugin rule",
    fields = {
        "directory": "where to place this plugin within the plugins directory",
        "plugin_metadata": "metadata produced by the check_plugin tool",
        "module_deps": "ImlModuleInfo for modules included in this plugin",
        "lib_deps": "libraries to be included in this plugin",
        "licenses": "",
        "plugin_files": "A map from the final studio location to the file it goes there.",
        "overwrite_plugin_version": "whether to stamp version metadata into plugin.xml",
        "platform": "The platform this plugin was compiled against",
    },
)

IntellijInfo = provider(
    doc = "Info about the IntelliJ SDK provided by the intellij_platform rule",
    fields = {
        "major_version": "The major IntelliJ version.",
        "minor_version": "The minor IntelliJ version.",
        "base": "A map from final studio location to the file (all non plugin files).",
        "plugins": "The file maps for all the  bundled plugins.",
    },
)

# Valid types (and their corresponding channels) which can be specified
# by the android_studio rule.
type_channel_mappings = {
    "Nightly": "Dev",
    "Canary": "Canary",
    "Beta": "Beta",
    "RC": "Beta",
    "Stable": "Stable",
}

# Types considered to be EAP. This should be a subset of
# type_channel_mappings.keys().
eap_types = ["Canary", "Beta", "Dev"]

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

def _lnzipper(ctx, desc, filemap, out, keep_symlink = True, attrs = {}, deps = []):
    """Creates a ZIP out while preserving symlinks.

    Note: This action needs to run outside the sandbox to capture an accurate
    representation of the workspace filesystem. Otherwise, files inside the
    sandbox are created as symbolic links, and the output ZIP would only
    contain entries which are sandbox symlinks."""
    files = []
    fileargs = []
    for zip_path, f in filemap:
        files.append(f)
        attr = ("[" + attrs[zip_path] + "]") if zip_path in attrs else ""
        fileargs.append("%s%s=%s\n" % (zip_path, attr, f.path if f else ""))

    lnzipper_options = "-ca"
    if keep_symlink:
        lnzipper_options += "s"
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

def _get_linux(x):
    return x.linux

LINUX = struct(
    name = "linux",
    jre = "jbr/",
    get = _get_linux,
    base_path = "",
    resource_path = "",
)

def _get_mac(x):
    return x.mac

MAC = struct(
    name = "mac",
    jre = "jbr/",
    get = _get_mac,
    base_path = "Contents/",
    resource_path = "Contents/Resources/",
)

def _get_mac_arm(x):
    return x.mac_arm

MAC_ARM = struct(
    name = "mac_arm",
    jre = "jbr/",
    get = _get_mac_arm,
    base_path = "Contents/",
    resource_path = "Contents/Resources/",
)

def _get_win(x):
    return x.win

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
            files += [(dir + "/" + dep.mappings[f], f) for f in platform.get(dep).to_list()]
        else:
            files += [(dir + "/" + f.basename, f) for f in dep.files.to_list()]
    return files

def _check_plugin(ctx, out, files, external_xmls = [], verify_id = None, verify_deps = None):
    deps = None
    if verify_deps != None:
        deps = [dep[PluginInfo].plugin_metadata for dep in verify_deps]

    check_args = ctx.actions.args()
    check_args.add("--out", out)
    check_args.add_all("--files", files)
    if verify_id:
        check_args.add("--plugin_id", verify_id)
    if deps != None:
        check_args.add_all("--deps", deps, omit_if_empty = False)
    check_args.add_all("--external_xmls", external_xmls)

    ctx.actions.run(
        inputs = files + (deps if deps else []),
        outputs = [out],
        executable = ctx.executable._check_plugin,
        arguments = [check_args],
        progress_message = "Analyzing %s plugin..." % ctx.attr.name,
        mnemonic = "chkplugin",
    )

def _studio_plugin_os(ctx, platform, module_deps, plugin_dir):
    files = {plugin_dir + "/lib/" + d: f for (d, f) in module_deps}

    res = _resource_deps(ctx.attr.resources_dirs, ctx.attr.resources, platform)
    files.update({plugin_dir + "/" + d: f for (d, f) in res})

    return files

def _depset_subtract(depset1, depset2):
    dict1 = {e1: None for e1 in depset1.to_list()}
    return [e2 for e2 in depset2.to_list() if e2 not in dict1]

def _label_str(label):
    if label.workspace_name:
        return str(label)
    else:
        return "//%s:%s" % (label.package, label.name)

def _studio_plugin_impl(ctx):
    plugin_dir = "plugins/" + ctx.attr.directory
    module_deps = _module_deps(ctx, ctx.attr.jars, ctx.attr.modules)
    module_deps = module_deps + [(f.basename, f) for f in ctx.files.libs]
    _check_plugin(ctx, ctx.outputs.plugin_metadata, [f for (r, f) in module_deps], ctx.attr.external_xmls, ctx.attr.name, ctx.attr.deps)
    plugin_files_linux = _studio_plugin_os(ctx, LINUX, module_deps, plugin_dir)
    plugin_files_mac = _studio_plugin_os(ctx, MAC, module_deps, plugin_dir)
    plugin_files_mac_arm = _studio_plugin_os(ctx, MAC_ARM, module_deps, plugin_dir)
    plugin_files_win = _studio_plugin_os(ctx, WIN, module_deps, plugin_dir)

    for lib in ctx.attr.libs:
        if PluginInfo in lib:
            fail("Plugin dependencies should be in the deps attribute, not in libs")

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

    missing = [s.label for s in _depset_subtract(have, need)]
    if missing:
        error = "\n".join(["\"%s\"," % _label_str(l) for l in missing])
        fail("Plugin '" + ctx.attr.name + "' has compile-time dependencies which are not on the " +
             "runtime classpath in release builds.\nYou may need to edit the plugin definition at " +
             str(ctx.label) + " to include the following dependencies:\n" + error)
    return [
        PluginInfo(
            directory = ctx.attr.directory,
            plugin_files = struct(
                linux = plugin_files_linux,
                mac = plugin_files_mac,
                mac_arm = plugin_files_mac_arm,
                win = plugin_files_win,
            ),
            plugin_metadata = ctx.outputs.plugin_metadata,
            module_deps = depset(ctx.attr.modules),
            lib_deps = depset(ctx.attr.libs),
            licenses = depset(ctx.files.licenses),
            overwrite_plugin_version = True,
            platform = ctx.attr._intellij_platform,
        ),
        # Force 'chkplugin' to run by marking its output as a validation output.
        # See https://bazel.build/extending/rules#validation_actions for details.
        OutputGroupInfo(_validation = depset([ctx.outputs.plugin_metadata])),
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
        "_intellij_platform": attr.label(
            default = Label("//tools/base/intellij-bazel:intellij_platform"),
            cfg = "host",
        ),
    },
    outputs = {
        "plugin_metadata": "%{name}.info",
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
    for dep in ctx.attr.files:
        if hasattr(dep, "mappings"):
            linux += [dep.linux]
            mac += [dep.mac]
            mac_arm += [dep.mac_arm]
            win += [dep.win]
            mappings.update(dep.mappings)
        else:
            files += dep[DefaultInfo].files.to_list()

    for prefix, destination in ctx.attr.mappings.items():
        for src in files + ctx.files.files_mac + ctx.files.files_mac_arm + ctx.files.files_linux + ctx.files.files_win:
            if src not in mappings and src.short_path.startswith(prefix):
                mappings[src] = destination + src.short_path[len(prefix):]

    dlinux = depset(files + ctx.files.files_linux, order = "preorder", transitive = linux)
    dmac = depset(files + ctx.files.files_mac, order = "preorder", transitive = mac)
    dmac_arm = depset(files + ctx.files.files_mac_arm, order = "preorder", transitive = mac_arm)
    dwin = depset(files + ctx.files.files_win, order = "preorder", transitive = win)

    return struct(
        linux = dlinux,
        mac = dmac,
        mac_arm = dmac_arm,
        win = dwin,
        mappings = mappings,
        providers = [DefaultInfo(files = depset(files))],
    )

_studio_data = rule(
    attrs = {
        "files": attr.label_list(allow_files = True),
        "files_linux": attr.label_list(allow_files = True),
        "files_mac": attr.label_list(allow_files = True),
        "files_mac_arm": attr.label_list(allow_files = True),
        "files_win": attr.label_list(allow_files = True),
        "mappings": attr.string_dict(mandatory = True),
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
    if ctx.attr.version_suffix:
        return code_name_and_patch_components + " " + ctx.attr.version_suffix[BuildSettingInfo].value

    return (code_name_and_patch_components +
            " " +
            ctx.attr.version_type +
            " " +
            str(ctx.attr.version_release_number))

def _full_display_version(ctx):
    """Returns the output of _form_version_full with versions applied."""
    intellij_info = ctx.attr.platform[IntellijInfo]
    (micro, _) = _split_version(ctx.attr.version_micro_patch)
    return _form_version_full(ctx).format(intellij_info.major_version, intellij_info.minor_version, micro)

def _append(ctx, platform, files, path, lines):
    if not lines:
        return
    file = files[path]
    text = "\n".join(lines)
    template = ctx.actions.declare_file(file.basename + ".%s.template" % platform.name)
    out = ctx.actions.declare_file(file.basename + ".%s.append.%s" % (platform.name, file.extension))
    files[path] = out
    ctx.actions.write(output = template, content = "{CONTENT}")
    expand_template_ex(
        ctx = ctx,
        template = template,
        out = out,
        substitutions = {
            "{CONTENT}": "$(inline " + file.path + ")\n" + text + "\n",
        },
        files = [file],
    )

def _stamp(ctx, args, srcs, src, out):
    args.add("--stamp")
    args.add(src)
    args.add(out)
    ctx.actions.run(
        inputs = srcs + [src],
        outputs = [out],
        executable = ctx.executable._stamper,
        arguments = [args],
        progress_message = "Stamping %s" % src.basename,
        mnemonic = "stamper",
    )

def _stamp_exe(ctx, extra, srcs, src, out):
    args = ctx.actions.args()
    args.add(src)
    args.add(out)
    ctx.actions.run(
        inputs = srcs + [src],
        outputs = [out],
        executable = ctx.executable._patch_exe,
        arguments = [args, extra],
        progress_message = "Patching exe %s" % src.basename,
        mnemonic = "patchexe",
    )

def _declare_stamped_file(ctx, files, platform, path):
    original = files[path]
    stamped = ctx.actions.declare_file(original.basename + ".%s.stamped.%s" % (platform.name, original.extension))
    files[path] = stamped
    return original, stamped

def _produce_manifest(ctx, platform, platform_files):
    out = ctx.outputs.manifest
    build_txt = platform_files[platform.resource_path + "build.txt"]
    resources_jar = platform_files[platform.base_path + "lib/resources.jar"]

    (channel, is_eap) = _get_channel_info(ctx.attr.version_type)
    args = ["--out", out.path]
    args += ["--build_txt", build_txt.path]
    args += ["--resources_jar", resources_jar.path]
    args += ["--channel", channel]
    args += ["--code_name", ctx.attr.version_code_name]

    ctx.actions.run(
        inputs = [build_txt, resources_jar, ctx.info_file, ctx.version_file],
        outputs = [out],
        executable = ctx.executable._generate_build_metadata,
        arguments = args,
        progress_message = "Producing manifest for %s..." % ctx.attr.name,
        mnemonic = "stamper",
    )

def _produce_update_message_html(ctx):
    if not ctx.file.update_message_template:
        ctx.actions.write(output = ctx.outputs.update_message, content = "")
        return

    channel, _ = _get_channel_info(ctx.attr.version_type)
    ctx.actions.expand_template(
        template = ctx.file.update_message_template,
        output = ctx.outputs.update_message,
        substitutions = {
            "{full_version}": _full_display_version(ctx),
            "{channel}": channel,
        },
    )

def _stamp_platform(ctx, platform, platform_files):
    args = ["--stamp_platform"]

    ret = {}
    ret.update(platform_files)

    build_txt, stamped_build_txt = _declare_stamped_file(ctx, ret, platform, platform.resource_path + "build.txt")
    args = ctx.actions.args()
    args.add("--info_file", ctx.info_file)
    args.add("--replace_build_number")
    _stamp(ctx, args, [ctx.info_file], build_txt, stamped_build_txt)

    resources_jar, stamped_resources_jar = _declare_stamped_file(ctx, ret, platform, platform.base_path + "lib/resources.jar")
    (_, is_eap) = _get_channel_info(ctx.attr.version_type)
    (micro, patch) = _split_version(ctx.attr.version_micro_patch)
    args = ctx.actions.args()
    args.add("--entry", "idea/AndroidStudioApplicationInfo.xml")
    args.add("--version_file", ctx.version_file)
    args.add("--version_full", _form_version_full(ctx))
    args.add("--eap", "true" if is_eap else "false")
    args.add("--version_micro", micro)
    args.add("--version_patch", patch)
    args.add("--build_txt", stamped_build_txt.path)
    args.add("--stamp_app_info")
    _stamp(ctx, args, [ctx.version_file, stamped_build_txt], resources_jar, stamped_resources_jar)

    idea_properties, stamped_idea_properties = _declare_stamped_file(ctx, ret, platform, platform.base_path + "bin/idea.properties")
    args = ctx.actions.args()
    args.add("--replace_selector", ctx.attr.selector)
    _stamp(ctx, args, [], idea_properties, stamped_idea_properties)

    if platform == LINUX:
        studio_sh, stamped_studio_sh = _declare_stamped_file(ctx, ret, platform, platform.base_path + "bin/studio.sh")
        args = ctx.actions.args()
        args.add("--replace_selector", ctx.attr.selector)
        _stamp(ctx, args, [], studio_sh, stamped_studio_sh)

        game_tools_sh, stamped_game_tools_sh = _declare_stamped_file(ctx, ret, platform, platform.base_path + "bin/game-tools.sh")
        args = ctx.actions.args()
        args.add("--replace_selector", ctx.attr.selector)
        _stamp(ctx, args, [], game_tools_sh, stamped_game_tools_sh)

        install_txt, stamped_install_txt = _declare_stamped_file(ctx, ret, platform, platform.base_path + "Install-Linux-tar.txt")
        args = ctx.actions.args()
        args.add("--replace_selector", ctx.attr.selector)
        _stamp(ctx, args, [], install_txt, stamped_install_txt)

    if platform == MAC or platform == MAC_ARM:
        info_plist, stamped_info_plist = _declare_stamped_file(ctx, ret, platform, platform.base_path + "Info.plist")
        args = ctx.actions.args()
        args.add("--info_file", ctx.info_file)
        args.add("--replace_build_number")
        args.add("--replace_selector", ctx.attr.selector)
        _stamp(ctx, args, [ctx.info_file], info_plist, stamped_info_plist)

    if platform == WIN:
        studio_exe, stamped_studio_exe = _declare_stamped_file(ctx, ret, platform, platform.base_path + "bin/studio64.exe")
        args = ctx.actions.args()
        args.add_all(["--replace_resource", "_ANDROID_STUDIO_SYSTEM_SELECTOR_", ctx.attr.selector])
        _stamp_exe(ctx, args, [], studio_exe, stamped_studio_exe)

        studio_bat, stamped_studio_bat = _declare_stamped_file(ctx, ret, platform, platform.base_path + "bin/studio.bat")
        args = ctx.actions.args()
        args.add("--replace_selector", ctx.attr.selector)
        _stamp(ctx, args, [], studio_bat, stamped_studio_bat)

        game_tools_bat, stamped_game_tools_bat = _declare_stamped_file(ctx, ret, platform, platform.base_path + "bin/game-tools.bat")
        args = ctx.actions.args()
        args.add("--replace_selector", ctx.attr.selector)
        _stamp(ctx, args, [], game_tools_bat, stamped_game_tools_bat)

    product_info_json, stamped_product_info_json = _declare_stamped_file(ctx, ret, platform, platform.resource_path + "product-info.json")
    args = ctx.actions.args()
    args.add("--info_file", ctx.info_file)
    args.add("--build_txt", stamped_build_txt)
    args.add("--stamp_product_info")
    args.add("--replace_selector", ctx.attr.selector)
    _stamp(ctx, args, [ctx.info_file, stamped_build_txt], product_info_json, stamped_product_info_json)

    return ret

def _stamp_plugin(ctx, platform, platform_files, files, overwrite_plugin_version):
    ret = {}
    ret.update(files)
    build_txt = platform_files[platform.resource_path + "build.txt"]

    for rel, file in files.items():
        if rel.endswith(".jar"):
            stamped_jar = ctx.actions.declare_file(ctx.attr.name + ".plugin.%s.stamped." % platform.name + rel.replace("/", "_"))
            ret[rel] = stamped_jar

            args = ctx.actions.args()
            args.add("--build_txt", build_txt)
            args.add("--info_file", ctx.info_file)
            args.add("--entry", "META-INF/plugin.xml")
            args.add("--optional_entry")
            args.add("--replace_build_number")
            if overwrite_plugin_version:
                args.add("--overwrite_plugin_version")
            _stamp(ctx, args, [build_txt, ctx.info_file], file, stamped_jar)

    return ret

def _android_studio_prefix(ctx, platform):
    if platform == MAC or platform == MAC_ARM:
        return ctx.attr.platform.platform_info.mac_bundle_name + "/"
    return "android-studio/"

def _get_external_attributes(all_files):
    attrs = {}
    for zip_path, file in all_files.items():
        # Source files are checked in with the right permissions.
        # For generated files we default to -rw-r--r--
        if not file.is_source:
            attrs[zip_path] = "644"
        if zip_path.endswith(".app/Contents/Info.plist"):
            attrs[zip_path] = "664"
        if (zip_path.endswith("/bin/studio.sh") or
            zip_path.endswith("/bin/game-tools.sh") or
            zip_path.endswith("/bin/studio64.exe") or
            zip_path.endswith("/bin/studio.bat") or
            zip_path.endswith("/bin/game-tools.bat")):
            attrs[zip_path] = "775"
    return attrs

def _android_studio_os(ctx, platform, out):
    files = []
    all_files = {}

    platform_prefix = _android_studio_prefix(ctx, platform)

    platform_files = platform.get(ctx.attr.platform[IntellijInfo].base)
    if ctx.attr.application_icon:
        platform_files = replace_app_icon(ctx, platform.name, platform_files, ctx.attr.application_icon[AppIconInfo])
    plugin_files = platform.get(ctx.attr.platform[IntellijInfo].plugins)

    if ctx.attr.jre:
        jre_files = [(ctx.attr.jre.mappings[f], f) for f in platform.get(ctx.attr.jre).to_list()]
        all_files.update({platform_prefix + platform.base_path + platform.jre + k: v for k, v in jre_files})

    # Stamp the platform and its plugins
    platform_files = _stamp_platform(ctx, platform, platform_files)
    all_files.update({platform_prefix + k: v for k, v in platform_files.items()})

    # for plugin in platform_plugins:
    for plugin, this_plugin_files in plugin_files.items():
        # TODO(b/329416516): Rework "excluding" performanceTesting plugin
        if plugin == "performanceTesting":
            continue
        this_plugin_files = _stamp_plugin(ctx, platform, platform_files, this_plugin_files, overwrite_plugin_version = False)
        all_files.update({platform_prefix + k: v for k, v in this_plugin_files.items()})

    dev01 = ctx.actions.declare_file(ctx.attr.name + ".dev01." + platform.name)
    ctx.actions.write(dev01, "")
    files += [(platform.base_path + "license/dev01_license.txt", dev01)]

    suffix = "64" if platform == LINUX else ("64.exe" if platform == WIN else "")
    vm_options_path = platform_prefix + platform.base_path + "bin/studio" + suffix + ".vmoptions"
    vm_options = ctx.attr.vm_options + {
        LINUX: ctx.attr.vm_options_linux,
        MAC: ctx.attr.vm_options_mac,
        MAC_ARM: ctx.attr.vm_options_mac_arm,
        WIN: ctx.attr.vm_options_win,
    }[platform]
    _append(ctx, platform, all_files, vm_options_path, vm_options)

    properties = ctx.attr.properties + {
        LINUX: ctx.attr.properties_linux,
        MAC: ctx.attr.properties_mac,
        MAC_ARM: ctx.attr.properties_mac_arm,
        WIN: ctx.attr.properties_win,
    }[platform]
    _append(ctx, platform, all_files, platform_prefix + platform.base_path + "bin/idea.properties", properties)

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

    so_jars = {"%s%s%s" % (platform_prefix, platform.base_path, jar): f for (jar, f) in ctx.attr.searchable_options.searchable_options}

    licenses = []
    for p in ctx.attr.plugins:
        pkey = p[PluginInfo].directory
        this_plugin_files = platform.get(p[PluginInfo].plugin_files)

        this_plugin_files = _stamp_plugin(ctx, platform, platform_files, this_plugin_files, p[PluginInfo].overwrite_plugin_version)

        licenses += [p[PluginInfo].licenses]
        this_plugin_full_files = {platform_prefix + platform.base_path + k: v for k, v in this_plugin_files.items()}
        for k, f in this_plugin_full_files.items():
            if k in so_jars:
                jar_with_so = ctx.actions.declare_file(k + "%s.so.jar" % platform.name)
                run_singlejar(ctx, [f, so_jars[k]], jar_with_so)
                this_plugin_full_files[k] = jar_with_so
        all_files.update(this_plugin_full_files)

    files += [(platform.base_path + "license/" + f.basename, f) for f in depset([], transitive = licenses).to_list()]

    all_files.update({platform_prefix + k: v for k, v in files})

    if platform == MAC or platform == MAC_ARM:
        all_files.update({"_codesign/entitlements.xml": ctx.file.codesign_entitlements})

    if platform == LINUX:
        _produce_manifest(ctx, LINUX, platform_files)

    attrs = _get_external_attributes(all_files)
    _lnzipper(ctx, out.basename, all_files.items(), out, attrs = attrs, keep_symlink = platform == MAC_ARM)
    return all_files

def _experimental_runner(ctx, name, target_to_file, out):
    files = []
    expected = []
    for target, src in target_to_file.items():
        dst = ctx.actions.declare_file(name + "/" + target)
        files.append(dst)
        expected.append(target)
        ctx.actions.run_shell(
            inputs = [src],
            outputs = [dst],
            command = "cp -f \"$1\" \"$2\"",
            arguments = [src.path, dst.path],
            mnemonic = "CopyFile",
            progress_message = "Copying files",
            use_default_shell_env = True,
        )

    file_list = ctx.actions.declare_file(name + "/files.lst")
    ctx.actions.write(file_list, "\n".join(expected))
    files.append(file_list)

    # Creating runfiles would work, but we have files with spaces, and to avoid having all the needed
    # files as ouputs of the list, we set them as sources of the final script.
    ctx.actions.run_shell(
        inputs = [ctx.file._studio_launcher] + files,
        outputs = [out],
        command = "cp -f \"$1\" \"$2\"",
        arguments = [ctx.file._studio_launcher.path, out.path],
        mnemonic = "CopyFile",
        progress_message = "Copying files",
        use_default_shell_env = True,
    )

script_template = """\
    #!/bin/bash
    options=
    tmp_dir=$(mktemp -d -t android-studio-XXXXXXXXXX)
    if [ "$1" == "--debug" ]; then
        options="$tmp_dir/.debug.vmoptions"
	echo "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005" > "$options"
	shift
    elif [[ "$1" == "--wrapper_script_flag=--debug="* ]]; then
        debug_option="$1"
        options="$tmp_dir/.debug.vmoptions"
	echo "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=${{debug_option##--wrapper_script_flag=--debug=}}" > "$options"
	shift
    fi

    config_base_dir="$HOME/.studio_dev"
    if [[ "$1" == "--config_base_dir="* ]]; then
	config_base_dir="${{1##--config_base_dir=}}"
	shift
    fi

    unzip -q "{zip_file}" -d "$tmp_dir"
    mkdir -p "$config_base_dir/.config"
    mkdir -p "$config_base_dir/.plugins"
    mkdir -p "$config_base_dir/.system"
    mkdir -p "$config_base_dir/.log"
    echo "idea.config.path=$config_base_dir/.config" >> "$tmp_dir/.properties"
    echo "idea.plugins.path=$config_base_dir/.plugins" >> "$tmp_dir/.properties"
    echo "idea.system.path=$config_base_dir/.system" >> "$tmp_dir/.properties"
    echo "idea.log.path=$config_base_dir/.log" >> "$tmp_dir/.properties"
    properties="$tmp_dir/.properties"

    if [ -z "$options" ]; then
        STUDIO_PROPERTIES="$properties" {command} $args
    else
        STUDIO_VM_OPTIONS="$options" STUDIO_PROPERTIES="$properties" {command} $@
    fi
"""

platform_by_name = {platform.name: platform for platform in [LINUX, MAC, MAC_ARM, WIN]}

def _android_studio_impl(ctx):
    plugins = [plugin[PluginInfo].directory for plugin in ctx.attr.plugins]
    ctx.actions.write(ctx.outputs.plugins, "".join([dir + "\n" for dir in plugins]))

    outputs = {
        LINUX: ctx.outputs.linux,
        MAC: ctx.outputs.mac,
        MAC_ARM: ctx.outputs.mac_arm,
        WIN: ctx.outputs.win,
    }
    all_files = {}
    for (platform, output) in outputs.items():
        all_files[platform] = _android_studio_os(ctx, platform, output)

    _produce_update_message_html(ctx)

    host_platform = platform_by_name[ctx.attr.host_platform_name]
    if ctx.attr.experimental_runner:
        script = ctx.actions.declare_file("%s/%s.py" % (ctx.attr.name, ctx.attr.name))
        _experimental_runner(
            ctx,
            ctx.attr.name,
            all_files[host_platform],
            script,
        )
        default_files = depset([script, ctx.outputs.manifest, ctx.outputs.update_message])
        default_runfiles = None
    else:
        script = ctx.actions.declare_file("%s-run" % ctx.label.name)
        script_content = script_template.format(
            zip_file = outputs[host_platform].short_path,
            command = {
                LINUX: "$tmp_dir/android-studio/bin/studio.sh",
                MAC: "open \"$tmp_dir/" + _android_studio_prefix(ctx, MAC) + "\"",
                MAC_ARM: "open \"$tmp_dir/" + _android_studio_prefix(ctx, MAC_ARM) + "\"",
                WIN: "$tmp_dir/android-studio/bin/studio64",
            }[host_platform],
        )
        ctx.actions.write(script, script_content, is_executable = True)
        runfiles = ctx.runfiles(files = [outputs[host_platform]])

        default_files = depset([ctx.outputs.linux, ctx.outputs.mac, ctx.outputs.mac_arm, ctx.outputs.win, ctx.outputs.manifest, ctx.outputs.update_message])
        default_runfiles = runfiles

    # Leave everything that is not the main zips as implicit outputs
    return DefaultInfo(
        executable = script,
        files = default_files,
        runfiles = default_runfiles,
    )

_android_studio = rule(
    attrs = {
        "host_platform_name": attr.string(),
        "codesign_entitlements": attr.label(allow_single_file = True),
        "compress": attr.bool(),
        "experimental_runner": attr.bool(default = False),
        "files_linux": attr.label_keyed_string_dict(allow_files = True, default = {}),
        "files_mac": attr.label_keyed_string_dict(allow_files = True, default = {}),
        "files_mac_arm": attr.label_keyed_string_dict(allow_files = True, default = {}),
        "files_win": attr.label_keyed_string_dict(allow_files = True, default = {}),
        "jre": attr.label(),
        "platform": attr.label(providers = [IntellijInfo]),
        "plugins": attr.label_list(providers = [PluginInfo]),
        "vm_options": attr.string_list(),
        "vm_options_linux": attr.string_list(),
        "vm_options_mac": attr.string_list(),
        "vm_options_mac_arm": attr.string_list(),
        "vm_options_win": attr.string_list(),
        "properties": attr.string_list(),
        "properties_linux": attr.string_list(),
        "properties_mac": attr.string_list(),
        "properties_mac_arm": attr.string_list(),
        "properties_win": attr.string_list(),
        "selector": attr.string(mandatory = True),
        "application_icon": attr.label(providers = [AppIconInfo]),
        "searchable_options": attr.label(),
        "version_code_name": attr.string(),
        "version_micro_patch": attr.string(),
        "version_release_number": attr.int(),
        "version_type": attr.string(),
        "update_message_template": attr.label(allow_single_file = True),
        "version_suffix": attr.label(
            providers = [BuildSettingInfo],
        ),
        "_singlejar": attr.label(
            default = Label("@bazel_tools//tools/jdk:singlejar"),
            cfg = "host",
            executable = True,
        ),
        "_stamper": attr.label(
            default = Label("//tools/adt/idea/studio:stamper"),
            cfg = "exec",
            executable = True,
        ),
        "_generate_build_metadata": attr.label(
            default = Label("//tools/adt/idea/studio:generate_build_metadata"),
            cfg = "exec",
            executable = True,
        ),
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
        "_lnzipper": attr.label(
            default = Label("//tools/base/bazel/lnzipper:lnzipper"),
            cfg = "exec",
            executable = True,
        ),
        "_expander": attr.label(
            default = Label("//tools/base/bazel/expander"),
            cfg = "host",
            executable = True,
        ),
        "_patch_exe": attr.label(
            default = Label("//tools/vendor/google/windows-exe-patcher:patch-exe"),
            cfg = "exec",
            executable = True,
        ),
        "_update_resources_jar": attr.label(
            default = Label("//tools/adt/idea/studio/rules:update_resources_jar"),
            cfg = "exec",
            executable = True,
        ),
        "_studio_launcher": attr.label(
            allow_single_file = True,
            default = Label("//tools/adt/idea/studio:studio.py"),
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
#       update_message_template: A file to use for the update message. The following
#                                substitutions are available to message templates:
#                                 {full_version} - See _form_version_full below.
#                                 {channel} - The channel derived from version_type.
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
        host_platform_name = select({
            "@platforms//os:linux": LINUX.name,
            "//tools/base/bazel/platforms:macos-x86_64": MAC.name,
            "//tools/base/bazel/platforms:macos-arm64": MAC_ARM.name,
            "@platforms//os:windows": WIN.name,
            "//conditions:default": "",
        }),
        **kwargs
    )

def _intellij_plugin_import_impl(ctx):
    files = {}
    plugin_dir = "plugins/" + ctx.attr.target_dir

    # Note: platform plugins will have no files because they are already in intellij-sdk.
    if ctx.attr.files:
        for f in ctx.files.files:
            if not f.short_path.startswith(ctx.attr.strip_prefix):
                fail("File " + f.short_path + " does not start with prefix " + ctx.attr.strip_prefix)
            relpath = f.short_path[len(ctx.attr.strip_prefix):]
            files[plugin_dir + "/" + relpath] = f
    plugin_files_linux = _studio_plugin_os(ctx, LINUX, [], plugin_dir) | files
    plugin_files_mac = _studio_plugin_os(ctx, MAC, [], plugin_dir) | files
    plugin_files_mac_arm = _studio_plugin_os(ctx, MAC_ARM, [], plugin_dir) | files
    plugin_files_win = _studio_plugin_os(ctx, WIN, [], plugin_dir) | files

    java_info = java_common.merge([export[JavaInfo] for export in ctx.attr.exports])
    jars = java_info.runtime_output_jars

    _check_plugin(ctx, ctx.outputs.plugin_metadata, jars)

    return [
        java_info,
        DefaultInfo(runfiles = ctx.runfiles(files = ctx.files.files)),
        PluginInfo(
            directory = ctx.attr.target_dir,
            plugin_metadata = ctx.outputs.plugin_metadata,
            module_deps = depset(),
            lib_deps = depset(ctx.attr.exports),
            licenses = depset(),
            plugin_files = struct(
                linux = plugin_files_linux,
                mac = plugin_files_mac,
                mac_arm = plugin_files_mac_arm,
                win = plugin_files_win,
            ),
            overwrite_plugin_version = ctx.attr.overwrite_plugin_version,
        ),
        # Force 'chkplugin' to run by marking its output as a validation output.
        # See https://bazel.build/extending/rules#validation_actions for details.
        OutputGroupInfo(_validation = depset([ctx.outputs.plugin_metadata])),
    ]

_intellij_plugin_import = rule(
    attrs = {
        # Note: platform plugins will have no files because they are already in intellij-sdk.
        "files": attr.label_list(allow_files = True),
        "strip_prefix": attr.string(),
        "target_dir": attr.string(),
        "resources": attr.label_list(allow_files = True),
        "resources_dirs": attr.string_list(),
        "exports": attr.label_list(providers = [JavaInfo], mandatory = True),
        "compress": attr.bool(),
        "overwrite_plugin_version": attr.bool(),
        "_check_plugin": attr.label(
            default = Label("//tools/adt/idea/studio:check_plugin"),
            cfg = "host",
            executable = True,
        ),
    },
    outputs = {
        "plugin_metadata": "%{name}.info",
    },
    implementation = _intellij_plugin_import_impl,
)

def intellij_plugin_import(name, target_dir, exports, files = [], strip_prefix = "", resources = {}, overwrite_plugin_version = False, **kwargs):
    """This macro is for prebuilt IntelliJ plugins that are not already part of intellij-sdk."""
    resources_dirs, resources_list = _dict_to_lists(resources)
    _intellij_plugin_import(
        name = name,
        files = files,
        strip_prefix = strip_prefix,
        target_dir = target_dir,
        resources = resources_list,
        resources_dirs = resources_dirs,
        exports = exports,
        compress = is_release(),
        overwrite_plugin_version = overwrite_plugin_version,
        **kwargs
    )

def _intellij_platform_impl_os(ctx, platform, data, zip_out):
    files = platform.get(data).to_list()
    plugin_dir = "%splugins/" % platform.base_path
    base = []
    plugins = {}
    for file in files:
        if file not in data.mappings:
            fail("file %s not found in mappings" % file.path)
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

    _zipper(ctx, "base %s platform zip" % platform.name, base, zip_out)

    base_files = {rel: file for rel, file in base}
    plugin_files = {plugin: {rel: file for rel, file in files} for plugin, files in plugins.items()}
    return base_files, plugin_files

def _intellij_platform_impl(ctx):
    base_files_linux, plugin_files_linux = _intellij_platform_impl_os(ctx, LINUX, ctx.attr.studio_data, ctx.outputs.linux_zip)
    base_files_win, plugin_files_win = _intellij_platform_impl_os(ctx, WIN, ctx.attr.studio_data, ctx.outputs.win_zip)
    base_files_mac, plugin_files_mac = _intellij_platform_impl_os(ctx, MAC, ctx.attr.studio_data, ctx.outputs.mac_zip)
    base_files_mac_arm, plugin_files_mac_arm = _intellij_platform_impl_os(ctx, MAC_ARM, ctx.attr.studio_data, ctx.outputs.mac_arm_zip)

    runfiles = ctx.runfiles(files = ctx.files.data)
    return struct(
        providers = [
            DefaultInfo(runfiles = runfiles),
            java_common.merge([export[JavaInfo] for export in ctx.attr.exports]),
            IntellijInfo(
                major_version = ctx.attr.major_version,
                minor_version = ctx.attr.minor_version,
                base = struct(
                    linux = base_files_linux,
                    mac = base_files_mac,
                    mac_arm = base_files_mac_arm,
                    win = base_files_win,
                ),
                plugins = struct(
                    linux = plugin_files_linux,
                    mac = plugin_files_mac,
                    mac_arm = plugin_files_mac_arm,
                    win = plugin_files_win,
                ),
            ),
        ],
        platform_info = struct(
            mac_bundle_name = ctx.attr.mac_bundle_name,
        ),
    )

_intellij_platform = rule(
    attrs = {
        "major_version": attr.string(),
        "minor_version": attr.string(),
        "exports": attr.label_list(providers = [JavaInfo]),
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
    outputs = {
        "linux_zip": "%{name}.linux.zip",
        "win_zip": "%{name}.win.zip",
        "mac_zip": "%{name}.mac.zip",
        "mac_arm_zip": "%{name}.mac_arm.zip",
    },
    provides = [DefaultInfo, JavaInfo, IntellijInfo],
    implementation = _intellij_platform_impl,
)

# For platforms that are only used to build standalone plugins against
def intellij_platform_import(name, spec):
    _intellij_platform(
        name = name,
        exports = [":" + name + "_jars"],
        studio_data = name + ".data",
        visibility = ["//visibility:public"],
    )

    jvm_import(
        name = name + "_jars",
        jars = [jar[1:] for jar in spec.jars + spec.jars_linux],
        visibility = ["//visibility:public"],
    )

    studio_data(
        name = name + ".data",
    )

    native.filegroup(
        name = name + "-product-info",
        srcs = ["product-info.json"],
        visibility = ["//visibility:public"],
    )

    for plugin, jars in spec.plugin_jars.items():
        jars_target_name = "%s-plugin-%s-jars" % (name, plugin)
        jvm_import(
            name = jars_target_name,
            jars = jars,
            visibility = ["//visibility:public"],
        )
        intellij_plugin_import(
            name = name + "-plugin-%s" % plugin,
            exports = [":" + jars_target_name],
            target_dir = "",
            visibility = ["//visibility:public"],
        )

    jvm_import(
        name = name + "-test-framework",
        jars = ["lib/testFramework.jar"],
        visibility = ["//visibility:public"],
    )

def intellij_platform(
        name,
        src,
        spec,
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
        major_version = spec.major_version,
        minor_version = spec.minor_version,
        exports = [":" + name + "_jars"],
        compress = is_release(),
        mac_bundle_name = spec.mac_bundle_name,
        studio_data = name + ".data",
        visibility = ["@intellij//:__subpackages__"],
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

    ide_paths = {
        "darwin": src + "/darwin/android-studio",
        "darwin_aarch64": src + "/darwin_aarch64/android-studio",
        "linux": src + "/linux/android-studio",
        "windows": src + "/windows/android-studio",
    }

    native.py_test(
        name = name + "_spec_test",
        srcs = ["//tools/adt/idea/studio:intellij_test.py"],
        main = "intellij_test.py",
        tags = ["no_test_windows", "no_test_mac"],
        data = native.glob([src + "/**/lib/*.jar", "**/product-info.json"]),
        env = {
            "spec": json.encode(spec),
            "intellij_paths": ",".join([k + "=" + native.package_name() + "/" + v for k, v in ide_paths.items()]),
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
        visibility = ["@intellij//:__subpackages__"],
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
        visibility = ["@intellij//:__subpackages__"],
    )

    # Expose product-info.json.
    native.filegroup(
        name = name + "-product-info",
        srcs = select({
            "//tools/base/bazel:windows": [src + "/windows/android-studio/product-info.json"],
            "//tools/base/bazel:darwin": [src + "/darwin/android-studio/Contents/Resources/product-info.json"],
            "//tools/base/bazel:darwin_arm64": [src + "/darwin_aarch64/android-studio/Contents/Resources/product-info.json"],
            "//conditions:default": [src + "/linux/android-studio/product-info.json"],
        }),
        visibility = ["@intellij//:__subpackages__"],
    )

    # Expose the default VM options file.
    native.filegroup(
        name = name + "-vm-options",
        srcs = select({
            "//tools/base/bazel:windows": [src + "/windows/android-studio/bin/studio64.exe.vmoptions"],
            "//tools/base/bazel:darwin": [src + "/darwin/android-studio/Contents/bin/studio.vmoptions"],
            "//tools/base/bazel:darwin_arm64": [src + "/darwin_aarch64/android-studio/Contents/bin/studio.vmoptions"],
            "//conditions:default": [src + "/linux/android-studio/bin/studio64.vmoptions"],
        }),
        visibility = ["@intellij//:__subpackages__"],
    )

    # TODO: merge this into the intellij_platform rule.
    dir_archive(
        name = name + "-full-linux",
        dir = "prebuilts/studio/intellij-sdk/" + src + "/linux/android-studio",
        files = native.glob([src + "/linux/android-studio/**"]),
        visibility = ["@intellij//:__subpackages__"],
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
            visibility = ["@intellij//:__subpackages__"],
        )

    jvm_import(
        name = name + "-updater",
        jars = [src + "/updater-full.jar"],
        visibility = ["@intellij//:__subpackages__"],
    )

    # Expose the IntelliJ test framework separately, for consumption by tests only.
    jvm_import(
        name = name + "-test-framework",
        jars = select({
            "//tools/base/bazel:windows": [src + "/windows/android-studio/lib/testFramework.jar"],
            "//tools/base/bazel:darwin": [src + "/darwin/android-studio/Contents/lib/testFramework.jar"],
            "//tools/base/bazel:darwin_arm64": [src + "/darwin_aarch64/android-studio/Contents/lib/testFramework.jar"],
            "//conditions:default": [src + "/linux/android-studio/lib/testFramework.jar"],
        }),
        visibility = ["@intellij//:__subpackages__"],
    )

def _gen_plugin_jars_import_target(name, src, spec, plugin, jars):
    """Generates a jvm_import target for the specified plugin."""
    add_windows = spec.plugin_jars_windows[plugin] if plugin in spec.plugin_jars_windows else []
    jars_windows = [src + "/windows/android-studio/" + jar for jar in jars + add_windows]
    add_darwin = spec.plugin_jars_darwin[plugin] if plugin in spec.plugin_jars_darwin else []
    jars_darwin = [src + "/darwin/android-studio/Contents/" + jar for jar in jars + add_darwin]
    add_darwin_aarch64 = spec.plugin_jars_darwin_aarch64[plugin] if plugin in spec.plugin_jars_darwin_aarch64 else []
    jars_darwin_aarch64 = [src + "/darwin_aarch64/android-studio/Contents/" + jar for jar in jars + add_darwin_aarch64]
    add_linux = spec.plugin_jars_linux[plugin] if plugin in spec.plugin_jars_linux else []
    jars_linux = [src + "/linux/android-studio/" + jar for jar in jars + add_linux]

    jvm_import(
        name = name,
        jars = select({
            "//tools/base/bazel:windows": jars_windows,
            "//tools/base/bazel:darwin": jars_darwin,
            "//tools/base/bazel:darwin_arm64": jars_darwin_aarch64,
            "//conditions:default": jars_linux,
        }),
    )

def iml_studio_test(
        name,
        data = [],
        **kwargs):
    iml_test(
        name = name,
        data = select({
            "@platforms//os:linux": ["//tools/adt/idea/studio:android-studio.linux.zip"],
            "//tools/base/bazel/platforms:macos-x86_64": ["//tools/adt/idea/studio:android-studio.mac.zip"],
            "//tools/base/bazel/platforms:macos-arm64": ["//tools/adt/idea/studio:android-studio.mac_arm.zip"],
            "@platforms//os:windows": ["//tools/adt/idea/studio:android-studio.win.zip"],
            "//conditions:default": [],
        }) + data,
        **kwargs
    )
