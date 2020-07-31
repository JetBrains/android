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
            for dep in m.module.bundled_deps:
                if dep in bundled:
                    continue
                res_files += [(dep.basename, dep)]
                bundled[dep] = True
        run_singlejar(ctx, modules_jars, jar_file)
        res_files += [(j, jar_file)]
    return res_files, plugin_jar, plugin_xml

def _get_linux(dep):
    return dep.files.to_list() + dep.files_linux.to_list()

LINUX = struct(
    name = "linux",
    jre = "jre/",
    get = _get_linux,
    base_path = "android-studio/",
    resource_path = "android-studio/",
)

def _get_mac(dep):
    return dep.files.to_list() + dep.files_mac.to_list()

MAC = struct(
    name = "mac",
    jre = "jre/jdk/",
    get = _get_mac,
    base_path = "Android Studio.app/Contents/",
    resource_path = "Android Studio.app/Contents/Resources/",
)

def _get_win(dep):
    return dep.files.to_list() + dep.files_win.to_list()

WIN = struct(
    name = "win",
    jre = "jre/",
    get = _get_win,
    base_path = "android-studio/",
    resource_path = "android-studio/",
)

def _resource_deps(res_dirs, res, platform):
    files = []
    for dir, dep in zip(res_dirs, res):
        if hasattr(dep, "mappings"):
            files += [(dir + "/" + dep.mappings[f], f) for f in platform.get(dep)]
        else:
            files += [(dir + "/" + f.basename, f) for f in dep.files.to_list()]
    return files

def _studio_plugin_os(ctx, platform, module_deps, plugin_dir, out):
    files = [(plugin_dir + "/lib/" + d, f) for (d, f) in module_deps]

    res = _resource_deps(ctx.attr.resources_dirs, ctx.attr.resources, platform)
    files += [(plugin_dir + "/" + d, f) for (d, f) in res]

    _zipper(ctx, "%s plugin" % platform.name, files, out)

def _studio_plugin_impl(ctx):
    plugin_dir = "plugins/" + ctx.attr.directory
    module_deps, plugin_jar, plugin_xml = _module_deps(ctx, ctx.attr.jars, ctx.attr.modules)
    _studio_plugin_os(ctx, LINUX, module_deps, plugin_dir, ctx.outputs.plugin_linux)
    _studio_plugin_os(ctx, MAC, module_deps, plugin_dir, ctx.outputs.plugin_mac)
    _studio_plugin_os(ctx, WIN, module_deps, plugin_dir, ctx.outputs.plugin_win)

    return struct(
        xml = plugin_xml,
        xml_jar = plugin_dir + "/lib/" + plugin_jar,
        files = depset(),
        files_linux = depset([ctx.outputs.plugin_linux]),
        files_mac = depset([ctx.outputs.plugin_mac]),
        files_win = depset([ctx.outputs.plugin_win]),
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

def _stamp_plugin(ctx, build_txt, in_xml, stamped_xml):
    args = ["--build_file", build_txt.path]
    args += ["--info_file", ctx.info_file.path]
    args += ["--stamp_plugin", in_xml.path, stamped_xml.path]
    ctx.actions.run(
        inputs = [in_xml, build_txt, ctx.info_file],
        outputs = [stamped_xml],
        executable = ctx.executable._stamper,
        arguments = args,
        progress_message = "Stamping plugin xml...",
        mnemonic = "stamper",
    )

def _extract(ctx, zip, file, target):
    ctx.actions.run(
        inputs = [zip],
        outputs = [target],
        executable = ctx.executable._unzipper,
        arguments = [zip.path, file + ":" + target.path],
        progress_message = "Extracting file from zip...",
        mnemonic = "extract",
    )

def _stamp_app_info(ctx, platform, platform_files):
    build_txt = None
    app_info_xml = None
    for rel_path, file in platform_files:
        if rel_path == platform.resource_path + "build.txt":
            build_txt = file
        if rel_path == platform.base_path + "lib/resources.jar":
            app_info_xml = ctx.actions.declare_file(ctx.attr.name + ".%s.app_info.xml" % platform.name)
            _extract(ctx, file, "idea/AndroidStudioApplicationInfo.xml", app_info_xml)

    if not build_txt:
        fail("build.txt not found in %s platform distribution" % platform.name)
    if not app_info_xml:
        fail("lib/resources.jar!idea/AndroidStudioApplicationInfo.xml not found")

    stamped_app_info_xml = ctx.actions.declare_file(ctx.attr.name + "stamped.%s.app_info.xml" % platform.name)
    args = ["--build_file", build_txt.path]
    args += ["--info_file", ctx.info_file.path]
    args += ["--stamp_build", app_info_xml.path, stamped_app_info_xml.path]
    ctx.actions.run(
        inputs = [app_info_xml, build_txt, ctx.info_file],
        outputs = [stamped_app_info_xml],
        executable = ctx.executable._stamper,
        arguments = args,
        progress_message = "Stamping plugin xml...",
        mnemonic = "stamper",
    )
    return stamped_app_info_xml, build_txt

def _zip_merger(ctx, zips, files, overrides, out):
    data = [f for (d, f) in zips + files + overrides]
    zipper_files = [r + "=+" + f.path + "\n" for r, f in zips]
    zipper_files += [r + "=" + f.path + "\n" for r, f in files]
    zipper_files += [r + "=" + f.path + "\n" for r, f in overrides]
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

def _android_studio_os(ctx, platform, out):
    zips = []
    files = []

    platform_zip = ctx.actions.declare_file(ctx.attr.name + ".platform.%s.zip" % platform.name)
    platform_files = [(ctx.attr.platform.mappings[f], f) for f in platform.get(ctx.attr.platform)]

    stamped_app_info_xml, build_txt = _stamp_app_info(ctx, platform, platform_files)
    overrides = [("#%slib/resources.jar!idea/AndroidStudioApplicationInfo.xml" % platform.base_path, stamped_app_info_xml)]

    _zipper(ctx, "%s platform" % platform.name, platform_files, platform_zip)

    zips += [("", platform_zip)]
    if ctx.attr.jre:
        jre_zip = ctx.actions.declare_file(ctx.attr.name + ".jre.%s.zip" % platform.name)
        jre_files = [(ctx.attr.jre.mappings[f], f) for f in platform.get(ctx.attr.jre)]
        _zipper(ctx, "%s jre" % platform.name, jre_files, jre_zip)
        zips += [(platform.base_path + platform.jre, jre_zip)]

    res = _resource_deps(ctx.attr.resources_dirs, ctx.attr.resources, platform)
    files += [(platform.base_path + d, f) for (d, f) in res]

    for p in ctx.attr.plugins:
        stamped_xml = ctx.actions.declare_file("stamped.%s.%s.xml" % (p.label.name, platform.name))
        _stamp_plugin(ctx, build_txt, p.xml, stamped_xml)
        overrides += [("#%s%s!META-INF/plugin.xml" % (platform.base_path, p.xml_jar), stamped_xml)]
        zips += [(platform.base_path, platform.get(p)[0])]

    module_deps, _, _ = _module_deps(ctx, ctx.attr.jars, ctx.attr.modules)
    files += [(platform.base_path + "lib/" + d, f) for (d, f) in module_deps]

    _zip_merger(ctx, zips, files, overrides, out)

def _android_studio_impl(ctx):
    _android_studio_os(ctx, LINUX, ctx.outputs.linux)
    _android_studio_os(ctx, MAC, ctx.outputs.mac)
    _android_studio_os(ctx, WIN, ctx.outputs.win)

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
