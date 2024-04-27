"""Provides a rule for setting the IntelliJ/Studio app icon."""

AppIconInfo = provider(
    doc = "Defines the application icon artwork for the Studio app.",
    fields = {
        "png": "The linux app icon.",
        "ico": "The Windows app icon.",
        "icns": "The MacOS app icon.",
        "svg": "A svg file used on all platforms.",
        "svg_small": "A smaller svg icon file.",
    },
)

_STUDIO_PATH_PNG = "bin/studio.png"
_STUDIO_PATH_ICNS = "Contents/Resources/studio.icns"
_STUDIO_PATH_ICO = "bin/studio.ico"
_STUDIO_PATH_SVG = "bin/studio.svg"
_STUDIO_PATH_SVG_MACOS = "Contents/bin/studio.svg"
_STUDIO_PATH_WINDOWS_EXE = "bin/studio64.exe"

def _app_icon_impl(ctx):
    return AppIconInfo(
        png = ctx.file.png,
        ico = ctx.file.ico,
        icns = ctx.file.icns,
        svg = ctx.file.svg,
        svg_small = ctx.file.svg_small,
    )

app_icon = rule(
    attrs = {
        "png": attr.label(
            doc = "The png file used on linux.",
            allow_single_file = True,
        ),
        "ico": attr.label(
            doc = "The ico file used on windows.",
            allow_single_file = True,
        ),
        "icns": attr.label(
            doc = "The icns file used on macOS.",
            allow_single_file = True,
        ),
        "svg": attr.label(
            doc = "The svg file used on all platforms.",
            allow_single_file = True,
        ),
        "svg_small": attr.label(
            doc = "A smaller svg file.",
            allow_single_file = True,
        ),
    },
    implementation = _app_icon_impl,
    provides = [AppIconInfo],
)

def _modify_exe_launcher(ctx, out, windows_exe, ico_file):
    # This number refers to IDI_WINLAUNCHER, or IDI_ICON in openjdk.
    icon_id = "2000"
    ctx.actions.run(
        inputs = [ico_file, windows_exe],
        outputs = [out],
        arguments = [windows_exe.path, out.path, "--replace_icon", icon_id, ico_file.path],
        executable = ctx.executable._patch_exe,
        mnemonic = "ModifyExeIcon",
    )

def replace_app_icon(ctx, platform_name, file_map, icon_info):
    """Returns a new file map with application icon files replaced.

    Args:
      ctx: The bazel context.
      platform_name: One of linux, win, mac, or mac_arm.
      file_map: A map of relative studio paths to files.
      icon_info: The AppIconInfo provider.

    Returns:
      An updated file mapping.
  """
    if platform_name not in ["linux", "win", "mac", "mac_arm"]:
        fail("Unexpected platform name: '%s'" % platform_name)

    resources_jar = "lib/resources.jar"

    new_file_map = {k: v for k, v in file_map.items()}
    if platform_name == "linux":
        if icon_info.png:
            new_file_map[_STUDIO_PATH_PNG] = icon_info.png
        if icon_info.svg:
            new_file_map[_STUDIO_PATH_SVG] = icon_info.svg
    if platform_name in ["mac", "mac_arm"]:
        resources_jar = "Contents/%s" % resources_jar
        if icon_info.icns:
            new_file_map[_STUDIO_PATH_ICNS] = icon_info.icns
        if icon_info.svg:
            new_file_map[_STUDIO_PATH_SVG_MACOS] = icon_info.svg
    if platform_name == "win":
        if icon_info.ico:
            new_file_map[_STUDIO_PATH_ICO] = icon_info.ico
            new_win_exe = ctx.actions.declare_file(ctx.attr.name + ".windows-launcher.exe")
            win_exe = new_file_map[_STUDIO_PATH_WINDOWS_EXE]
            _modify_exe_launcher(ctx, new_win_exe, win_exe, icon_info.ico)
            new_file_map[_STUDIO_PATH_WINDOWS_EXE] = new_win_exe
        if icon_info.svg:
            new_file_map[_STUDIO_PATH_SVG] = icon_info.svg

    new_res_jar = ctx.actions.declare_file(ctx.attr.name + ".%s.updated-app-icon-resources.jar" % platform_name)
    ctx.actions.run(
        inputs = [file_map[resources_jar], icon_info.svg, icon_info.svg_small],
        outputs = [new_res_jar],
        arguments = [
            "--resources_jar",
            file_map[resources_jar].path,
            "--svg",
            icon_info.svg.path,
            "--svg_small",
            icon_info.svg_small.path,
            "--out",
            new_res_jar.path,
        ],
        executable = ctx.executable._update_resources_jar,
        mnemonic = "UpdateIntellijResourceJar",
    )
    new_file_map[resources_jar] = new_res_jar

    return new_file_map
