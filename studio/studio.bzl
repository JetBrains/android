load("//tools/base/bazel:merge_archives.bzl", "run_singlejar")
load("//tools/base/bazel:functions.bzl", "create_option_file")
load("//tools/base/bazel:utils.bzl", "dir_archive")
load("@bazel_tools//tools/jdk:toolchain_utils.bzl", "find_java_toolchain")

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
    plugin_jar = None
    plugin_xml = None
    for j, ms in jars.items():
        jar_file = ctx.actions.declare_file(j)
        modules_jars = []
        for m in ms:
            if not hasattr(m, "module"):
                fail("Only iml_modules are allowed in modules")
            if m.module.plugin:
                plugin_jar = j
                plugin_xml = m.module.plugin
            modules_jars += [m.module.module_jars]

        run_singlejar(ctx, modules_jars, jar_file)
        res_files += [(j, jar_file)]
    return res_files, plugin_jar, plugin_xml

def _get_linux(dep):
    return dep.files.to_list() + dep.files_linux.to_list()

LINUX = struct(
    name = "linux",
    jre = "jre/",
    get = _get_linux,
    base_path = "",
    resource_path = "",
)

def _get_mac(dep):
    return dep.files.to_list() + dep.files_mac.to_list()

MAC = struct(
    name = "mac",
    jre = "jre/",
    get = _get_mac,
    base_path = "Contents/",
    resource_path = "Contents/Resources/",
)

def _get_mac_arm(dep):
    return dep.files.to_list() + dep.files_mac_arm.to_list()

MAC_ARM = struct(
    name = "mac_arm",
    jre = "jre/",
    get = _get_mac_arm,
    base_path = "Contents/",
    resource_path = "Contents/Resources/",
)

def _get_win(dep):
    return dep.files.to_list() + dep.files_win.to_list()

WIN = struct(
    name = "win",
    jre = "jre/",
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
        deps = [dep.plugin_info for dep in verify_deps if hasattr(dep, "plugin_info")]

    plugin_info = ctx.actions.declare_file(ctx.attr.name + ".info")
    check_args = ctx.actions.args()
    check_args.add("--out", plugin_info)
    check_args.add_all("--files", files)
    if verify_id:
        check_args.add("--plugin_id", verify_id)
    if deps != None:
        check_args.add_all("--deps", deps, omit_if_empty = False)
    check_args.add_all("--external_xmls", external_xmls)

    ctx.actions.run(
        inputs = files + (deps if deps else []),
        outputs = [plugin_info],
        executable = ctx.executable._check_plugin,
        arguments = [check_args],
        progress_message = "Analyzing %s plugin..." % ctx.attr.name,
        mnemonic = "chkplugin",
    )
    return plugin_info

def _studio_plugin_os(ctx, platform, module_deps, plugin_dir, plugin_info, out):
    spec = [(plugin_dir + "/lib/" + d, f) for (d, f) in module_deps]

    res = _resource_deps(ctx.attr.resources_dirs, ctx.attr.resources, platform)
    spec += [(plugin_dir + "/" + d, f) for (d, f) in res]

    files = [f for (p, f) in spec]
    _zipper(ctx, "%s plugin" % platform.name, spec, out, [plugin_info])

def _depset_subtract(depset1, depset2):
    dict1 = {e1: None for e1 in depset1.to_list()}
    return [e2 for e2 in depset2.to_list() if e2 not in dict1]

def _studio_plugin_impl(ctx):
    plugin_dir = "plugins/" + ctx.attr.directory
    module_deps, plugin_jar, plugin_xml = _module_deps(ctx, ctx.attr.jars, ctx.attr.modules)
    module_deps = module_deps + [(f.basename, f) for f in ctx.files.libs]
    plugin_info = _check_plugin(ctx, [f for (r, f) in module_deps], ctx.attr.external_xmls, ctx.attr.name, ctx.attr.deps)
    _studio_plugin_os(ctx, LINUX, module_deps, plugin_dir, plugin_info, ctx.outputs.plugin_linux)
    _studio_plugin_os(ctx, MAC, module_deps, plugin_dir, plugin_info, ctx.outputs.plugin_mac)
    _studio_plugin_os(ctx, MAC_ARM, module_deps, plugin_dir, plugin_info, ctx.outputs.plugin_mac_arm)
    _studio_plugin_os(ctx, WIN, module_deps, plugin_dir, plugin_info, ctx.outputs.plugin_win)

    # Check that all modules needed by the modules in this plugin, are either present in the
    # plugin or in its dependencies.
    need = depset(transitive =
                      [m.module.module_deps for m in ctx.attr.modules] +
                      [m.module.plugin_deps for m in ctx.attr.modules] +
                      [m.module.external_deps for m in ctx.attr.modules])
    have = depset(
        direct = ctx.attr.modules + ctx.attr.libs + ctx.attr.provided,
        transitive = [d.module_deps for d in ctx.attr.deps if hasattr(d, "module_deps")] +
                     [d.lib_deps for d in ctx.attr.deps if hasattr(d, "lib_deps")] +
                     [depset([p for p in ctx.attr.deps if hasattr(p, "plugin_info")])],
    )

    missing = [str(s.label) for s in _depset_subtract(have, need)]
    if missing:
        fail("While analyzing %s, the following dependencies are required but not found:\n%s" % (ctx.attr.name, "\n".join(missing)))

    return struct(
        directory = ctx.attr.directory,
        xml = plugin_xml,
        xml_jar = plugin_dir + "/lib/" + plugin_jar,
        files = depset(),
        files_linux = depset([ctx.outputs.plugin_linux]),
        files_mac = depset([ctx.outputs.plugin_mac]),
        files_mac_arm = depset([ctx.outputs.plugin_mac_arm]),
        files_win = depset([ctx.outputs.plugin_win]),
        plugin_info = plugin_info,
        module_deps = depset(ctx.attr.modules),
        lib_deps = depset(ctx.attr.libs),
        licenses = depset(ctx.files.licenses),
    )

_studio_plugin = rule(
    attrs = {
        "modules": attr.label_list(allow_empty = False),
        "libs": attr.label_list(allow_files = True),
        "licenses": attr.label_list(allow_files = True),
        "jars": attr.string_list(),
        "resources": attr.label_list(allow_files = True),
        "resources_dirs": attr.string_list(),
        "directory": attr.string(),
        "compress": attr.bool(),
        "deps": attr.label_list(),
        "provided": attr.label_list(),
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

def _is_release():
    return select({
        "//tools/base/bazel:release": True,
        "//conditions:default": False,
    })

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
        compress = _is_release(),
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
        compress = _is_release(),
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

def _stamp(ctx, platform, zip, extra, srcs, out):
    args = ["--platform", zip.path]
    args += ["--os", platform.name]
    args += ["--version_file", ctx.version_file.path]
    args += ["--info_file", ctx.info_file.path]
    args += ["--eap", "true" if ctx.attr.version_eap else "false"]
    args += ["--version_micro", str(ctx.attr.version_micro)]
    args += ["--version_patch", str(ctx.attr.version_patch)]
    args += ["--version_full", ctx.attr.version_full]
    args += extra
    ctx.actions.run(
        inputs = [zip, ctx.info_file, ctx.version_file] + srcs,
        outputs = [out],
        executable = ctx.executable._stamper,
        arguments = args,
        progress_message = "Stamping %s file..." % zip.basename,
        mnemonic = "stamper",
    )

def _stamp_platform(ctx, platform, zip, out):
    args = ["--stamp_platform", out.path]
    _stamp(ctx, platform, zip, args, [], out)

def _stamp_platform_plugin(ctx, platform, zip, src, dst):
    args = ["--stamp_platform_plugin", src.path, dst.path]
    _stamp(ctx, platform, zip, args, [src], dst)

def _stamp_plugin(ctx, platform, zip, src, dst):
    args = ["--stamp_plugin", src.path, dst.path]
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

def _codesign(ctx, filelist_template, entitlements, prefix, out):
    filelist = ctx.actions.declare_file(ctx.attr.name + ".codesign.filelist")
    ctx.actions.expand_template(
        template = filelist_template,
        output = filelist,
        substitutions = {
            "%prefix%": prefix,
        },
    )

    ctx.actions.declare_file(ctx.attr.name + ".codesign.zip")
    files = [
        ("_codesign/filelist", filelist),
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

    # Stamp the platform and its plugins
    platform_stamp = ctx.actions.declare_file(ctx.attr.name + ".%s.platform.stamp.zip" % platform.name)
    _stamp_platform(ctx, platform, platform_zip, platform_stamp)
    overrides += [(platform_prefix, platform_stamp)]
    for plugin in platform_plugins:
        stamp = ctx.actions.declare_file(ctx.attr.name + ".stamp.%s.%s" % (plugin.basename, platform.name))
        _stamp_platform_plugin(ctx, platform, platform_zip, plugin, stamp)
        overrides += [(platform_prefix, stamp)]

    dev01 = ctx.actions.declare_file(ctx.attr.name + ".dev01." + platform.name)
    ctx.actions.write(dev01, "")
    files += [(platform.base_path + "license/dev01_license.txt", dev01)]

    so_jars = [("%s%s" % (platform.base_path, jar), f) for (jar, f) in ctx.attr.searchable_options.searchable_options]
    so_extras = ctx.actions.declare_file(ctx.attr.name + ".so.%s.zip" % platform.name)
    _zipper(ctx, "%s searchable options" % platform.name, so_jars, so_extras)
    overrides += [(platform_prefix, so_extras)]

    licenses = []
    for p in ctx.attr.plugins:
        plugin_zip = platform.get(p)[0]
        stamp = ctx.actions.declare_file(ctx.attr.name + ".stamp.%s" % plugin_zip.basename)
        _stamp_plugin(ctx, platform, platform_zip, plugin_zip, stamp)
        overrides += [(platform_prefix + platform.base_path, stamp)]
        zips += [(platform_prefix + platform.base_path, plugin_zip)]
        licenses += [p.licenses]

    files += [(platform.base_path + "license/" + f.basename, f) for f in depset([], transitive = licenses).to_list()]

    extras_zip = ctx.actions.declare_file(ctx.attr.name + ".extras.%s.zip" % platform.name)
    _zipper(ctx, "%s extras" % platform.name, files, extras_zip)
    zips += [(platform_prefix, extras_zip)]

    if platform == MAC or platform == MAC_ARM:
        codesign = ctx.actions.declare_file(ctx.attr.name + ".codesign.zip")
        _codesign(ctx, ctx.file.codesign_filelist, ctx.file.codesign_entitlements, platform_prefix, codesign)
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
    plugins = [plugin.directory for plugin in ctx.attr.plugins]
    ctx.actions.write(ctx.outputs.plugins, "".join([dir + "\n" for dir in plugins]))

    _android_studio_os(ctx, LINUX, ctx.outputs.linux)
    _android_studio_os(ctx, MAC, ctx.outputs.mac)
    _android_studio_os(ctx, MAC_ARM, ctx.outputs.mac_arm)
    _android_studio_os(ctx, WIN, ctx.outputs.win)

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
        files = depset([ctx.outputs.linux, ctx.outputs.mac, ctx.outputs.mac_arm, ctx.outputs.win]),
        runfiles = runfiles,
    )

_android_studio = rule(
    attrs = {
        "platform": attr.label(),
        "jre": attr.label(),
        "plugins": attr.label_list(),
        "searchable_options": attr.label(),
        "version_micro": attr.int(),
        "version_patch": attr.int(),
        "version_eap": attr.bool(),
        "version_full": attr.string(),
        "compress": attr.bool(),
        "codesign_filelist": attr.label(allow_single_file = True),
        "codesign_entitlements": attr.label(allow_single_file = True),
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
            cfg = "host",
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
def android_studio(
        name,
        **kwargs):
    _android_studio(
        name = name,
        compress = _is_release(),
        **kwargs
    )

def _intellij_plugin_impl(ctx):
    infos = []
    for jar in ctx.files.jars:
        ijar = java_common.run_ijar(
            actions = ctx.actions,
            jar = jar,
            java_toolchain = find_java_toolchain(ctx, ctx.attr._java_toolchain),
        )
        infos.append(JavaInfo(
            output_jar = jar,
            compile_jar = ijar,
        ))
    plugin_info = _check_plugin(ctx, ctx.files.jars)
    return struct(
        providers = [java_common.merge(infos)],
        plugin_info = plugin_info,
    )

_intellij_plugin = rule(
    attrs = {
        "jars": attr.label_list(allow_files = True),
        "_java_toolchain": attr.label(default = Label("@bazel_tools//tools/jdk:current_java_toolchain")),
        "_check_plugin": attr.label(
            default = Label("//tools/adt/idea/studio:check_plugin"),
            cfg = "host",
            executable = True,
        ),
    },
    implementation = _intellij_plugin_impl,
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

    infos = []
    for jar in ctx.files.jars:
        ijar = java_common.run_ijar(
            actions = ctx.actions,
            jar = jar,
            java_toolchain = find_java_toolchain(ctx, ctx.attr._java_toolchain),
        )
        infos.append(JavaInfo(
            output_jar = jar,
            compile_jar = ijar,
        ))

    runfiles = ctx.runfiles(files = ctx.files.data)
    files = depset([base_linux, base_mac, base_win])
    return struct(
        providers = [
            DefaultInfo(files = files, runfiles = runfiles),
            java_common.merge(infos),
        ],
        data = struct(
            files = depset([]),
            files_linux = depset([base_linux]),
            files_mac = depset([base_mac]),
            files_mac_arm = depset([base_mac]),
            files_win = depset([base_win]),
            mappings = {},
        ),
        plugins = struct(
            files = depset([]),
            files_linux = depset(plugins_linux),
            files_mac = depset(plugins_mac),
            files_mac_arm = depset(plugins_mac),
            files_win = depset(plugins_win),
            mappings = {},
        ),
        platform_info = struct(
            mac_bundle_name = ctx.attr.mac_bundle_name,
        ),
    )

_intellij_platform = rule(
    attrs = {
        "jars": attr.label_list(allow_files = True),
        "data": attr.label_list(allow_files = True),
        "studio_data": attr.label(),
        "compress": attr.bool(),
        "mac_bundle_name": attr.string(),
        "_java_toolchain": attr.label(default = Label("@bazel_tools//tools/jdk:current_java_toolchain")),
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
        **kwargs):
    _intellij_platform(
        name = name,
        jars = select({
            "//tools/base/bazel:windows": [src + "/windows/android-studio" + jar for jar in spec.jars + spec.jars_windows],
            "//tools/base/bazel:darwin": [src + "/darwin/android-studio/Contents" + jar for jar in spec.jars + spec.jars_darwin],
            "//conditions:default": [src + "/linux/android-studio" + jar for jar in spec.jars + spec.jars_linux],
        }),
        compress = _is_release(),
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
            "//conditions:default": native.glob(
                include = [src + "/linux/android-studio/**"],
                exclude = [src + "/linux/android-studio/plugins/textmate/lib/bundles/**"],
            ),
        }),
    )

    # Expose lib/resources.jar as a separate target
    native.java_import(
        name = name + "-resources-jar",
        jars = select({
            "//tools/base/bazel:windows": [src + "/windows/android-studio/lib/resources.jar"],
            "//tools/base/bazel:darwin": [src + "/darwin/android-studio/Contents/lib/resources.jar"],
            "//conditions:default": [src + "/linux/android-studio/lib/resources.jar"],
        }),
        visibility = ["//visibility:public"],
    )

    # Expose build.txt from the prebuilt SDK
    native.filegroup(
        name = name + "-build-txt",
        srcs = select({
            "//tools/base/bazel:windows": [src + "/windows/android-studio/build.txt"],
            "//tools/base/bazel:darwin": [src + "/darwin/android-studio/Contents/build.txt"],
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
        files_win = native.glob([src + "/windows/**"]),
        mappings = {
            "prebuilts/studio/intellij-sdk/%s/linux/android-studio/" % src: "",
            "prebuilts/studio/intellij-sdk/%s/darwin/android-studio/" % src: "",
            "prebuilts/studio/intellij-sdk/%s/windows/android-studio/" % src: "",
        },
    )

    for plugin, jars in spec.plugin_jars.items():
        add_windows = spec.plugin_jars_windows[plugin] if plugin in spec.plugin_jars_windows else []
        add_darwin = spec.plugin_jars_darwin[plugin] if plugin in spec.plugin_jars_darwin else []
        add_linux = spec.plugin_jars_linux[plugin] if plugin in spec.plugin_jars_linux else []

        _intellij_plugin(
            name = name + "-plugin-%s" % plugin,
            jars = select({
                "//tools/base/bazel:windows": [src + "/windows/android-studio/plugins/" + plugin + "/lib/" + jar for jar in jars + add_windows],
                "//tools/base/bazel:darwin": [src + "/darwin/android-studio/Contents/plugins/" + plugin + "/lib/" + jar for jar in jars + add_darwin],
                "//conditions:default": [src + "/linux/android-studio/plugins/" + plugin + "/lib/" + jar for jar in jars + add_linux],
            }),
            visibility = ["//visibility:public"],
        )

    native.java_import(
        name = name + "-updater",
        jars = [src + "/updater-full.jar"],
        visibility = ["//visibility:public"],
    )
