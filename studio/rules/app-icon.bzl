"""Provides a rule for setting the IntelliJ/Studio app icon."""

AppIconInfo = provider(
    doc = "Defines the application icon artwork for the Studio app.",
    fields = {
        "png": "The linux app icon.",
        "ico": "The Windows app icon.",
        "icns": "The MacOS app icon.",
        "svg": "A svg file used on all platforms.",
        "svg_small": "A smaller svg icon file.",
        "splash": "The splash screen.",
        "splash2x": "The splash screen at 2x resolution for Retina displays.",
    },
)

AppIconPathsInfo = provider(
    doc = "Defines the output paths for the application icons.",
    fields = {
        "png": "Path to the linux app icon.",
        "icns": "Path to the MacOS app icon.",
        "ico": "Path to the Windows app icon.",
        "svg": "Path to the svg file.",
        "svg_macos": "Path to the svg file on MacOS.",
        "windows_exe": "Path to the Windows launcher exe.",
    },
)

_STUDIO_PATHS = AppIconPathsInfo(
    png = "bin/studio.png",
    icns = "Contents/Resources/studio.icns",
    ico = "bin/studio.ico",
    svg = "bin/studio.svg",
    svg_macos = "Contents/bin/studio.svg",
    windows_exe = "bin/studio64.exe",
)

_APA_PATHS = AppIconPathsInfo(
    png = "bin/apa.png",
    icns = "Contents/Resources/apa.icns",
    ico = "bin/apa.ico",
    svg = "bin/apa.svg",
    svg_macos = "Contents/bin/apa.svg",
    windows_exe = "bin/apa64.exe",
)

def _app_icon_impl(ctx):
    return AppIconInfo(
        png = ctx.file.png,
        ico = ctx.file.ico,
        icns = ctx.file.icns,
        svg = ctx.file.svg,
        svg_small = ctx.file.svg_small,
        splash = ctx.file.splash,
        splash2x = ctx.file.splash2x,
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
        "splash": attr.label(
            doc = "The splash screen.",
            allow_single_file = True,
            mandatory = True,
        ),
        "splash2x": attr.label(
            doc = "The splash screen at 2x resolution for Retina displays.",
            allow_single_file = True,
            mandatory = True,
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

def replace_app_icon(ctx, platform_name, file_map, icon_info, branding):
    """Returns a new file map with application icon files replaced.

    Args:
      ctx: The bazel context.
      platform_name: One of linux, win, mac, or mac_arm.
      file_map: A map of relative studio paths to files.
      icon_info: The AppIconInfo provider.
      branding: Tool/IDE branding that determines the icons to replace and their layouts.

    Returns:
      An updated file mapping.
  """
    if platform_name not in ["linux", "win", "mac", "mac_arm"]:
        fail("Unexpected platform name: '%s'" % platform_name)

    resources_jar = "lib/resources.jar"

    paths = _APA_PATHS if branding == "android-performance-analyzer" else _STUDIO_PATHS

    new_file_map = {k: v for k, v in file_map.items()}
    if platform_name == "linux":
        if icon_info.png:
            new_file_map[paths.png] = icon_info.png
        if icon_info.svg:
            new_file_map[paths.svg] = icon_info.svg
    if platform_name in ["mac", "mac_arm"]:
        resources_jar = "Contents/%s" % resources_jar
        if icon_info.icns:
            new_file_map[paths.icns] = icon_info.icns
        if icon_info.svg:
            new_file_map[paths.svg_macos] = icon_info.svg
    if platform_name == "win":
        if icon_info.ico:
            new_file_map[paths.ico] = icon_info.ico
            new_win_exe = ctx.actions.declare_file(ctx.attr.name + ".windows-launcher.exe")
            win_exe = new_file_map[paths.windows_exe]
            _modify_exe_launcher(ctx, new_win_exe, win_exe, icon_info.ico)
            new_file_map[paths.windows_exe] = new_win_exe
        if icon_info.svg:
            new_file_map[paths.svg] = icon_info.svg

    new_res_jar = ctx.actions.declare_file(ctx.attr.name + ".%s.updated-app-icon-resources.jar" % platform_name)
    ctx.actions.run(
        inputs = [file_map[resources_jar], icon_info.svg, icon_info.svg_small, icon_info.splash, icon_info.splash2x],
        outputs = [new_res_jar],
        arguments = [
            "--resources_jar",
            file_map[resources_jar].path,
            "--svg",
            icon_info.svg.path,
            "--svg_small",
            icon_info.svg_small.path,
            "--splash",
            icon_info.splash.path,
            "--splash2x",
            icon_info.splash2x.path,
            "--out",
            new_res_jar.path,
            "--branding",
            branding,
        ],
        executable = ctx.executable._update_resources_jar,
        mnemonic = "UpdateIntellijResourceJar",
    )
    new_file_map[resources_jar] = new_res_jar

    if len(new_file_map) > len(file_map):
        extra = list(set(new_file_map) - set(file_map))
        fail("Some icon files were missing from the platform. Did the icon locations change? " + str(extra))

    return new_file_map
