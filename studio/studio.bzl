load("//tools/base/bazel:merge_archives.bzl", "run_singlejar")
load("//tools/base/bazel:functions.bzl", "create_option_file")

def _zipper(ctx, desc, map, out):
    files = [f for (p, f) in map]
    zipper_files = [r + "=" + f.path + "\n" for r, f in map]
    zipper_args = ["c", out.path]
    zipper_list = create_option_file(ctx, out.basename + ".res.lst", "".join(zipper_files))
    zipper_args += ["@" + zipper_list.path]
    ctx.actions.run(
        inputs = files + [zipper_list],
        outputs = [out],
        executable = ctx.executable._zipper,
        arguments = zipper_args,
        progress_message = "Creating %s zip..." % desc,
        mnemonic = "zipper",
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
        modules_jars = []
        for m in ms:
            if not hasattr(m, "module"):
                fail("Only iml_modules are allowed in modules")
            modules_jars += [m.module.module_jars]
            for dep in m.module.bundled_deps:
                if dep in bundled:
                    continue
                res_files += [(dep.basename, dep)]
                bundled[dep] = True
        run_singlejar(ctx, modules_jars, jar_file)
        res_files += [(j, jar_file)]
    return res_files

def _resource_deps(res_dirs, res):
    files = []
    linux = []
    mac = []
    win = []

    for dir, dep in zip(res_dirs, res):
        if hasattr(dep, "mappings"):
            files += [(dir + "/" + dep.mappings[f], f) for f in dep.files.to_list()]
            linux += [(dir + "/" + dep.mappings[f], f) for f in dep.files_linux.to_list()]
            mac += [(dir + "/" + dep.mappings[f], f) for f in dep.files_mac.to_list()]
            win += [(dir + "/" + dep.mappings[f], f) for f in dep.files_win.to_list()]
        else:
            files += [(dir + "/" + f.basename, f) for f in dep.files.to_list()]
    return (files, linux, mac, win)

def _studio_plugin_impl(ctx):
    plugin_dir = "plugins/" + ctx.attr.directory
    files = [(plugin_dir + "/lib/" + d, f) for (d, f) in _module_deps(ctx, ctx.attr.jars, ctx.attr.modules)]

    res, res_linux, res_mac, res_win = _resource_deps(ctx.attr.resources_dirs, ctx.attr.resources)
    files += [(plugin_dir + "/" + d, f) for (d, f) in res]
    res_linux = [(plugin_dir + "/" + d, f) for (d, f) in res_linux]
    res_mac = [(plugin_dir + "/" + d, f) for (d, f) in res_mac]
    res_win = [(plugin_dir + "/" + d, f) for (d, f) in res_win]

    _zipper(ctx, "linux plugin", files + res_linux, ctx.outputs.plugin_linux)
    _zipper(ctx, "mac plugin", files + res_mac, ctx.outputs.plugin_mac)
    _zipper(ctx, "win plugin", files + res_win, ctx.outputs.plugin_win)
    return struct(
        linux = ctx.outputs.plugin_linux,
        mac = ctx.outputs.plugin_mac,
        win = ctx.outputs.plugin_win,
    )

_studio_plugin = rule(
    attrs = {
        "modules": attr.label_list(allow_empty = False),
        "jars": attr.string_list(),
        "resources": attr.label_list(allow_files = True),
        "resources_dirs": attr.string_list(),
        "directory": attr.string(),
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
    },
    outputs = {
        "plugin_linux": "%{name}.linux.zip",
        "plugin_mac": "%{name}.mac.zip",
        "plugin_win": "%{name}.win.zip",
    },
    implementation = _studio_plugin_impl,
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
        **kwargs
    )

def _studio_data_impl(ctx):
    for dep in ctx.attr.files_linux + ctx.attr.files_mac + ctx.attr.files_win:
        if hasattr(dep, "mappings"):
            fail("studio_data does not belong on a platform specific attribute, please add " + str(dep.label) + " to \"files\" directly")

    files = []
    mac = []
    win = []
    linux = []
    mappings = {}
    to_map = []
    for dep in ctx.attr.files:
        files += [dep.files]
        if hasattr(dep, "mappings"):
            linux += [dep.files_linux]
            mac += [dep.files_mac]
            win += [dep.files_win]
            mappings.update(dep.mappings)
        else:
            to_map += dep.files.to_list()

    for prefix, destination in ctx.attr.mappings.items():
        for src in to_map + ctx.files.files_mac + ctx.files.files_linux + ctx.files.files_win:
            if src not in mappings and src.short_path.startswith(prefix):
                mappings[src] = destination + src.short_path[len(prefix):]

    dfiles = depset(order = "preorder", transitive = files)
    dlinux = depset(ctx.files.files_linux, order = "preorder", transitive = linux)
    dmac = depset(ctx.files.files_mac, order = "preorder", transitive = mac)
    dwin = depset(ctx.files.files_win, order = "preorder", transitive = win)

    return struct(
        files = dfiles,
        files_linux = dlinux,
        files_mac = dmac,
        files_win = dwin,
        mappings = mappings,
    )

_studio_data = rule(
    attrs = {
        "files": attr.label_list(allow_files = True),
        "files_linux": attr.label_list(allow_files = True),
        "files_mac": attr.label_list(allow_files = True),
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
#     files_{linux, mac, win}: A list of files for each platform
#     mapping: A dictionary to map file locations and build an arbitrary file tree, in the form of
#              a dictionary from current directory to new directory.
def studio_data(name, files = [], files_linux = [], files_mac = [], files_win = [], mappings = {}, tags = [], **kwargs):
    _studio_data(
        name = name,
        files = files,
        files_linux = files_linux,
        files_mac = files_mac,
        files_win = files_win,
        mappings = mappings,
        tags = tags,
        **kwargs
    )

def _zip_merger(ctx, zips, files, out):
    data = [f for (d, f) in zips + files]
    zipper_files = [r + "=+" + f.path + "\n" for r, f in zips]
    zipper_files += [r + "=" + f.path + "\n" for r, f in files]
    zipper_args = ["c", out.path]
    zipper_list = create_option_file(ctx, out.basename + ".res.lst", "".join(zipper_files))
    zipper_args += ["@" + zipper_list.path]
    ctx.actions.run(
        inputs = data + [zipper_list],
        outputs = [out],
        executable = ctx.executable._zip_merger,
        arguments = zipper_args,
        progress_message = "Creating distribution zip...",
        mnemonic = "zipmerger",
    )

def _android_studio_impl(ctx):
    zips_linux = []
    zips_mac = []
    zips_win = []

    files_linux = []
    files_mac = []
    files_win = []

    platform_zip_linux = ctx.actions.declare_file(ctx.attr.name + ".platform.linux.zip")
    platform_zip_mac = ctx.actions.declare_file(ctx.attr.name + ".platform.mac.zip")
    platform_zip_win = ctx.actions.declare_file(ctx.attr.name + ".platform.win.zip")

    platform_files = [(ctx.attr.platform.mappings[f], f) for f in ctx.attr.platform.files.to_list()]
    platform_files_linux = [(ctx.attr.platform.mappings[f], f) for f in ctx.attr.platform.files_linux.to_list()]
    platform_files_mac = [(ctx.attr.platform.mappings[f], f) for f in ctx.attr.platform.files_mac.to_list()]
    platform_files_win = [(ctx.attr.platform.mappings[f], f) for f in ctx.attr.platform.files_win.to_list()]

    _zipper(ctx, "linux platform", platform_files + platform_files_linux, platform_zip_linux)
    _zipper(ctx, "mac platform", platform_files + platform_files_mac, platform_zip_mac)
    _zipper(ctx, "win platform", platform_files + platform_files_win, platform_zip_win)

    zips_linux += [("", platform_zip_linux)]
    zips_mac += [("", platform_zip_mac)]
    zips_win += [("", platform_zip_win)]

    if ctx.attr.jre:
        jre_zip_linux = ctx.actions.declare_file(ctx.attr.name + ".jre.linux.zip")
        jre_zip_mac = ctx.actions.declare_file(ctx.attr.name + ".jre.mac.zip")
        jre_zip_win = ctx.actions.declare_file(ctx.attr.name + ".jre.win.zip")

        jre_files = [(ctx.attr.jre.mappings[f], f) for f in ctx.attr.jre.files.to_list()]
        jre_files_linux = [(ctx.attr.jre.mappings[f], f) for f in ctx.attr.jre.files_linux.to_list()]
        jre_files_mac = [(ctx.attr.jre.mappings[f], f) for f in ctx.attr.jre.files_mac.to_list()]
        jre_files_win = [(ctx.attr.jre.mappings[f], f) for f in ctx.attr.jre.files_win.to_list()]

        _zipper(ctx, "linux jre", jre_files + jre_files_linux, jre_zip_linux)
        _zipper(ctx, "mac jre", jre_files + jre_files_mac, jre_zip_mac)
        _zipper(ctx, "win jre", jre_files + jre_files_win, jre_zip_win)

        zips_linux += [("android-studio/jre/", jre_zip_linux)]
        zips_mac += [("Android Studio.app/Contents/jre/jdk/", jre_zip_mac)]
        zips_win += [("android-studio/jre/", jre_zip_win)]

    for p in ctx.attr.plugins:
        zips_linux += [("android-studio/", p.linux)]
        zips_mac += [("Android Studio.app/Contents/", p.mac)]
        zips_win += [("android-studio/", p.win)]

    res, res_linux, res_mac, res_win = _resource_deps(ctx.attr.resources_dirs, ctx.attr.resources)
    files_linux += [("android-studio/" + d, f) for (d, f) in res + res_linux]
    files_mac += [("Android Studio.app/Contents/" + d, f) for (d, f) in res + res_mac]
    files_win += [("android-studio/" + d, f) for (d, f) in res + res_win]

    module_deps = _module_deps(ctx, ctx.attr.jars, ctx.attr.modules)
    files_linux += [("android-studio/lib/" + d, f) for (d, f) in module_deps]
    files_mac += [("Android Studio.app/Contents/lib/" + d, f) for (d, f) in module_deps]
    files_win += [("android-studio/lib/" + d, f) for (d, f) in module_deps]

    _zip_merger(ctx, zips_linux, files_linux, ctx.outputs.linux)
    _zip_merger(ctx, zips_mac, files_mac, ctx.outputs.mac)
    _zip_merger(ctx, zips_win, files_win, ctx.outputs.win)

_android_studio = rule(
    attrs = {
        "platform": attr.label(),
        "jre": attr.label(),
        "modules": attr.label_list(),
        "jars": attr.string_list(),
        "resources": attr.label_list(),
        "resources_dirs": attr.string_list(),
        "plugins": attr.label_list(),
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
        "_zipper": attr.label(
            default = Label("@bazel_tools//tools/zip:zipper"),
            cfg = "host",
            executable = True,
        ),
    },
    outputs = {
        "linux": "%{name}.linux.zip",
        "mac": "%{name}.mac.zip",
        "win": "%{name}.win.zip",
    },
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
        modules = {},
        resources = {},
        **kwargs):
    jars, modules_list = _dict_to_lists(modules)
    resources_dirs, resources_list = _dict_to_lists(resources)

    _android_studio(
        name = name,
        modules = modules_list,
        jars = jars,
        resources = resources_list,
        resources_dirs = resources_dirs,
        **kwargs
    )
