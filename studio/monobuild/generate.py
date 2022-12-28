#!/usr/bin/env python3

from pathlib import Path
from typing import Iterator, NoReturn
import argparse
import copy
import json
import posixpath
import shlex
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET


def main():
    if sys.version_info < (3,9):
        sys.exit("ERROR: Python version should be at least 3.9")

    # Parse args.
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "-r", "--recycle-intellij-source-map", action="store_true",
        help="reuse previous intellij-source-map.json file to reduce iteration times")
    args = parser.parse_args()

    # Find relevant paths.
    monobuild_dir = Path(__file__).parent
    workspace = monobuild_dir.joinpath("../../../../..").resolve()
    print("Workspace:", workspace)
    assert monobuild_dir == workspace.joinpath("tools/adt/idea/studio/monobuild"), monobuild_dir
    studio_root = workspace.joinpath("tools/adt/idea")
    intellij_root = workspace.joinpath("tools/idea")

    # Prepare temp dir.
    outdir = monobuild_dir.joinpath("out/monobuild.temp")
    shutil.rmtree(outdir.joinpath(".idea"), ignore_errors=True)
    outdir.mkdir(parents=True, exist_ok=True)
    outdir.joinpath(".idea").mkdir()
    outdir.joinpath(".idea/libraries").mkdir()
    outdir.joinpath(".idea/modules").mkdir()
    print("Tempdir:", outdir)

    # Compute IntelliJ source map (a map from distro jars to source modules/libraries).
    source_map_file = outdir.joinpath("intellij-source-map.json")
    if args.recycle_intellij_source_map and source_map_file.exists():
        print(f"Reusing previous IntelliJ source map: {source_map_file.relative_to(monobuild_dir)}")
    else:
        source_map_file.unlink(missing_ok=True)
        generate_intellij_source_map(intellij_root, source_map_file)

    # Load and patch the projects.
    print("Scanning projects")
    studio = load_project("Studio", studio_root)
    intellij = load_project("IntelliJ", intellij_root)
    convert_intellij_sdk_libs(studio, intellij, source_map_file)
    rename_libraries_using_prefix(studio, "studio-lib")
    move_project_kotlinc_opts_into_modules(studio)

    # Merge the projects and write out the result.
    merged = JpsProject(
        "monobuild", outdir,
        intellij.modules + studio.modules,
        intellij.libs + studio.libs,
    )
    write_project(merged, outdir)
    transfer_config_files(intellij, studio, outdir)
    transfer_workspace_xml(monobuild_dir, outdir)

    # Assert no remaining references to intellij-sdk.
    print("Searching for residual references to prebuilts/studio/intellij-sdk")
    for f in sorted(outdir.glob(".idea/**/*")):
        if f.suffix not in [".xml", ".iml", ".txt", ".json"]:
            continue
        text = f.read_text("UTF-8", errors="ignore")
        assert "prebuilts/studio/intellij-sdk" not in text, f"intellij-sdk still referenced by {f}"

    # Move generated output.
    final_project_dir = monobuild_dir.joinpath(".idea")
    shutil.rmtree(final_project_dir, ignore_errors=True)
    shutil.move(outdir.joinpath(".idea"), final_project_dir)
    print(f"\nSuccessfully created monobuild IDEA project at:\n\n\t{final_project_dir}\n")


# Represents a JPS module, corresponding to a .iml module file.
class JpsModule:
    def __init__(self, name: str, xml: ET.ElementTree):
        self.name = name
        self.xml = xml


# Represents a JPS library, corresponding to a .xml library file.
class JpsLibrary:
    def __init__(self, xml: ET.ElementTree):
        self.xml = xml

    @property
    def name(self):
        (lib_tag,) = self.xml.findall("./library")
        return lib_tag.get("name") or fail()

    @name.setter
    def name(self, value: str):
        (lib_tag,) = self.xml.findall("./library")
        lib_tag.set("name", value)


# Represents a JPS project, containing a .idea/ subdirectory.
class JpsProject:
    def __init__(self, name: str, root: Path, modules: list[JpsModule], libs: list[JpsLibrary]):
        self.name = name
        self.root = root
        self.modules = modules
        self.libs = libs


# Loads a JpsProject from the given project path.
def load_project(name: str, root: Path) -> JpsProject:
    iml_files = sorted(set(collect_iml_files(root)))
    lib_files = sorted(root.glob(".idea/libraries/*.xml"))
    print(f"Loading {name}:", len(iml_files), "modules and", len(lib_files), "libraries")
    modules = [load_module(iml) for iml in iml_files]
    libs = [load_library(root, lib_xml) for lib_xml in lib_files]
    return JpsProject(name, root, modules, libs)


# Loads a JpsModule from the given .iml file.
def load_module(iml_file: Path) -> JpsModule:
    name = iml_file.stem
    xml = parse_jps_file(iml_file, {"MODULE_DIR": iml_file.parent})
    return JpsModule(name, xml)


# Loads a JpsLibrary from the given library .xml file.
def load_library(project_dir: Path, xml_file: Path) -> JpsLibrary:
    xml = parse_jps_file(xml_file, {"PROJECT_DIR": project_dir})
    return JpsLibrary(xml)


# Converts intellij-sdk libraries into JPS modules which export their corresponding source
# module/libraries. For example, the intellij-sdk library "studio-plugin-devkit" would
# be converted to a module (with the same name) which exports IntelliJ modules
# "intellij.devkit.core", "intellij.devkit.git", etc.
def convert_intellij_sdk_libs(studio: JpsProject, intellij: JpsProject, source_map_file: Path):
    def is_intellij_sdk_lib(lib: str) -> bool:
        if lib.startswith("studio-plugin-"): return True
        if lib in ["studio-sdk", "intellij-updater", "intellij-test-framework"]: return True
        return False

    # Remove the old intellij-sdk libs.
    studio.libs = [lib for lib in studio.libs if not is_intellij_sdk_lib(lib.name)]

    # Parse the IntelliJ source map.
    print("Parsing", source_map_file.name)
    plugin_contents = parse_intellij_source_map(intellij, source_map_file)

    # Generate new intellij-sdk libs, in the form of modules which export corresponding IJ sources.
    print("Rewriting", len(plugin_contents), "intellij-sdk libraries")
    for plugin, packaged_contents in plugin_contents.items():
        new_module = create_empty_jps_module(plugin)
        (new_deps,) = new_module.xml.findall('./component[@name="NewModuleRootManager"]')
        new_deps.extend(packaged_contents)
        for dep in new_deps:
            dep.set("exported", "")
        studio.modules.append(new_module)

    # Rewrite intellij-sdk library references among module .iml files.
    for module in studio.modules:
        lib_deps = module.xml.findall("./component/orderEntry[@type='library']")
        for dep in lib_deps:
            lib_name = dep.get("name") or fail()
            if is_intellij_sdk_lib(lib_name):
                del dep.attrib["name"], dep.attrib["level"]
                dep.set("type", "module")
                dep.set("module-name", lib_name)


# Renames all project libraries to avoid name clashes.
def rename_libraries_using_prefix(project: JpsProject, prefix: str):
    print(f"Prepending prefix '{prefix}' to all", len(project.libs), "libraries in", project.name)
    defined_lib_set = set(lib.name for lib in project.libs)
    # Rewrite definitions.
    for lib in project.libs:
        lib.name = f"{prefix}-{lib.name}"
    # Rewrite references.
    for module in project.modules:
        lib_deps = module.xml.findall("./component/orderEntry[@type='library']")
        for dep in lib_deps:
            lib_name = dep.get("name") or fail()
            if lib_name in defined_lib_set:
                dep.set("name", f"{prefix}-{lib_name}")


# Finds project-level Kotlinc opts, and moves them into modules instead.
# This helps avoid configuration clashes between the two projects.
def move_project_kotlinc_opts_into_modules(project: JpsProject):
    print("Moving", project.name, "project-level Kotlinc opts into modules")
    kotlin_facet = create_kotlin_facet_from_project_settings(project)
    for module in project.modules:
        facet_manager = get_or_create_child(module.xml.getroot(), "component", name="FacetManager")
        if not facet_manager.find(f"./facet[@type='kotlin-language']"):
            facet_manager.append(copy.deepcopy(kotlin_facet))


def write_project(project: JpsProject, outdir: Path):
    print("Writing", project.name, "project to disk")
    # Write libraries.
    for lib in project.libs:
        filename = lib.name
        for char in ['-', '.', ':', ' ']:
            filename = filename.replace(char, "_")
        outfile = outdir.joinpath(f".idea/libraries/{filename}.xml")
        assert not outfile.exists()
        write_xml_file(lib.xml, outfile)
    # Write modules.
    # Note: in theory there could be module name clashes between the Studio/IntelliJ
    # projects. In practice this is rare, and we should strive to avoid it anyway because
    # name clashes would be a problem for JetBrains/android too.
    module_paths: list[Path] = []
    for module in project.modules:
        outfile = outdir.joinpath(f".idea/modules/{module.name}.iml")
        assert not outfile.exists(), f"Name clash for module {module.name}"
        write_xml_file(module.xml, outfile)
        module_paths.append(outfile)
    # Write .idea/modules.xml.
    write_module_list(module_paths, outdir)


# Merges and transfers JPS config files from both projects.
def transfer_config_files(intellij: JpsProject, studio: JpsProject, outdir: Path):
    # Config files that should not be transferred.
    ignored_paths = [
        ".idea/.name",
        ".idea/icon.png",
        ".idea/libraries",
        ".idea/modules.xml",
        ".idea/OWNERS",
        ".idea/vcs.xml",
        ".idea/workspace.xml",
    ]
    # Config files that should be transfered from Studio, but not from IntelliJ.
    studio_override_paths = [
        ".idea/ant.xml",  # Contains our bazel-dependencies build step.
        ".idea/codeInsightSettings.xml",
        ".idea/codeStyles/codeStyleConfig.xml",
        ".idea/codeStyles/Project.xml",
        ".idea/copyright/profiles_settings.xml",
    ]
    print("Ignoring", len(ignored_paths), "unnecessary config files")
    print("Overriding", len(studio_override_paths), "IntelliJ config files with Studio contents")

    # Copy IntelliJ files first, then any non-conflicting Studio files.
    copy_config_files(intellij, ignored_paths + studio_override_paths, outdir)
    copy_config_files(studio, ignored_paths, outdir)

    # Special case: .idea/compiler.xml needs to be merged from both projects.
    print("Merging parts of compiler.xml from Studio")
    base_compiler_config = parse_jps_project_file(intellij, ".idea/compiler.xml")
    extra_compiler_config = parse_jps_project_file(studio, ".idea/compiler.xml")
    concat_xml_elements(base_compiler_config, extra_compiler_config, xpaths=[
        "./component[@name='JavacSettings']/option[@name='ADDITIONAL_OPTIONS_OVERRIDE']/module",
        "./component[@name='CompilerConfiguration']/annotationProcessing/profile",
        "./component[@name='CompilerConfiguration']/excludeFromCompile/file",
        "./component[@name='CompilerConfiguration']/wildcardResourcePatterns/entry",
    ])
    write_xml_file(base_compiler_config, outdir.joinpath(".idea/compiler.xml"))

    # Special case: .idea/vcs.xml needs to merged so that git blame works for all files.
    base_vcs_config = parse_jps_project_file(intellij, ".idea/vcs.xml")
    extra_vcs_config = parse_jps_project_file(studio, ".idea/vcs.xml")
    concat_xml_elements(base_vcs_config, extra_vcs_config, xpaths=[
        "./component[@name='VcsDirectoryMappings']/mapping",
    ])
    write_xml_file(base_vcs_config, outdir.joinpath(".idea/vcs.xml"))

    # Special case: we need to set idea.home.path in all run configurations, because
    # IntelliJ's home-path heuristics do not work for our project location.
    print("Setting idea.home.path in all run configurations")
    for run_config_file in sorted(outdir.glob(".idea/runConfigurations/*.xml")):
        run_config = parse_jps_file(run_config_file, {})
        for vm_args_tag in run_config.findall("./configuration/option[@name='VM_PARAMETERS']"):
            vm_args = vm_args_tag.get("value") or fail()
            vm_args_tag.set("value", f"-Didea.home.path={intellij.root} {vm_args}")
        write_xml_file(run_config, run_config_file)


# Copies non-conflicting JPS config files from the given project.
def copy_config_files(project: JpsProject, ignored_paths: list[str], outdir: Path):
    print("Copying config files from", project.name)
    config_files = sorted(project.root.glob(".idea/**/*"))
    for f in config_files:
        relpath = f.relative_to(project.root)
        should_ignore = any(relpath.is_relative_to(ignored) for ignored in ignored_paths)
        if should_ignore or f.is_dir():
            continue
        outfile = outdir.joinpath(relpath)
        if outfile.exists():
            print("Warning: dropping conflicting", project.name, "config", relpath)
            continue
        outfile.parent.mkdir(parents=True, exist_ok=True)
        if f.suffix == ".xml":
            # Substitute path vars.
            text = read_jps_file(f, {"PROJECT_DIR": project.root})
            outfile.write_text(text, encoding="UTF-8")
        else:
            shutil.copy(f, outfile)


# Preserves .idea/workspace.xml, which contains user settings.
def transfer_workspace_xml(prev_project: Path, next_project: Path):
    prev_workspace_xml = prev_project.joinpath(".idea/workspace.xml")
    next_workspace_xml = next_project.joinpath(".idea/workspace.xml")
    if prev_workspace_xml.exists():
        print("Preserving .idea/workspace.xml")
        shutil.copy(prev_workspace_xml, next_workspace_xml)
    else:
        # Init with sensible defaults to avoid very slow builds.
        print("Seeding .idea/workspace.xml")
        with open(next_workspace_xml, 'w', encoding="UTF-8") as f:
            f.write('<project version="4">\n')
            f.write('  <component name="CompilerWorkspaceConfiguration">\n')
            f.write('    <option name="PARALLEL_COMPILATION" value="true" />\n')
            f.write('    <option name="COMPILER_PROCESS_HEAP_SIZE" value="3072" />\n')
            f.write('  </component>\n')
            f.write('</project>')


# Runs AndroidStudioSourceMapBuildTarget from platform/tools/idea to generate a
# JSON map from IDE distribution JARs to their corresponding source modules/libraries.
def generate_intellij_source_map(intellij_root: Path, outfile: Path):
    command = [
        str(intellij_root.joinpath('platform/jps-bootstrap/jps-bootstrap.sh')),
        "-Dcompile.parallel=true",
        str(intellij_root),
        "intellij.idea.community.build",
        "AndroidStudioSourceMapBuildTarget",
        str(outfile),
    ]
    # Mention where to find the logs.
    log_path = outfile.parent.joinpath("intellij-source-map-build-log.txt")
    cmd_quoted = ' '.join([shlex.quote(arg) for arg in command])
    print((
        f"\nBuilding IntelliJ source map using command:\n\n\t{cmd_quoted}\n\n"
        f"...and writing build log to:\n\n\t{log_path}\n\n"
        f"This may take a while if IntelliJ needs to download dependencies.\n"
    ))
    # Run it.
    with open(log_path, 'w') as log:
        status = subprocess.run(command, stderr=subprocess.STDOUT, stdout=log)
    if status.returncode != 0:
        sys.exit(f"ERROR: failed to build {outfile.name}. See build log at:\n\n\t{log_path}\n")
    print("Successfully built", outfile.name)


# Parses intellij-source-map.json, and returns a map from intellij-sdk library names
# to the corresponding sources in IntelliJ, in the form of <orderEntry> XML elements.
def parse_intellij_source_map(intellij: JpsProject, source_map_file) -> dict[str,list[ET.Element]]:
    source_map_text = read_jps_file(source_map_file, {"PROJECT_DIR": intellij.root})
    source_map = json.loads(source_map_text)

    res: dict[str,list[ET.Element]] = {}
    for mapping in source_map:
        # Find the intellij-sdk lib that contains this jar.
        path = Path(mapping["path"])
        if path.match("**/dist.all/lib/testFramework.jar"):
            intellij_sdk_lib = "intellij-test-framework"
        elif path.match("**/dist.all/lib/*.jar") or path.match("**/dist.all/plugins/java/lib/*.jar"):
            intellij_sdk_lib = "studio-sdk"
        elif path.match("**/dist.all/plugins/*/lib/*.jar"):
            plugin = path.parent.parent.name
            intellij_sdk_lib = f"studio-plugin-{plugin}"
        else:
            continue  # Can happen for non-classpath artifacts, e.g. java/lib/rt/debugger-agent.jar.

        # Synthesize a <orderEntry> element referring to the source module/library.
        src: ET.Element
        type = mapping["type"]
        if type == "module-output":
            module: str = mapping["module"]
            src = ET.Element("orderEntry", {"type": "module", "module-name": module})
        elif type == "project-library":
            lib: str = mapping["library"]
            jar = Path(mapping["libraryFile"])
            src = create_module_library(name=f"{lib}:{jar.stem}", jars=[jar])
        elif type == "module-library-file":
            module: str = mapping["module"]
            jar = Path(mapping["libraryFile"])
            src = create_module_library(name=f"{module}:{jar.stem}", jars=[jar])
        else:
            fail(f"Unknown source type found in the IntelliJ source map: {type}")
        srcs = res.setdefault(intellij_sdk_lib, [])
        srcs.append(src)

    # Special case: updater-full.jar is missing from the IntelliJ source map.
    # Fixme: we only add the main updater module, not its bundled dependencies.
    res["intellij-updater"] = [
        ET.Element("orderEntry", {"type": "module", "module-name": "intellij.platform.updater"})
    ]

    return res


# Creates a new JPS module library, in the form of an <orderEntry> XML element.
def create_module_library(name: str, jars: list[Path]) -> ET.Element:
    lib = ''
    lib += f'<orderEntry type="module-library">\n'
    lib += f'  <library name="{name}">\n'
    lib += f'    <CLASSES>\n'
    for jar in jars:
        lib += f'      <root url="jar://{jar}!/" />\n'
    lib += f'    </CLASSES>\n'
    lib += f'  </library>\n'
    lib += f'</orderEntry>'
    return ET.fromstring(lib, ET.XMLParser(encoding="UTF-8"))


# Parses the project Kotlinc opts in kotlinc.xml and return an equivalent Kotlin module facet.
def create_kotlin_facet_from_project_settings(project: JpsProject) -> ET.Element:
    kotlinc_xml = parse_jps_project_file(project, ".idea/kotlinc.xml")
    option_tags = kotlinc_xml.findall("./component/option[@name][@value]")
    options = dict([(opt.get("name"), opt.get("value")) for opt in option_tags])

    # Remove the 'version' option, since modules cannot use their own compiler version.
    options.pop("version", None)

    # The 'additionalArguments' option needs to be stored separately.
    additional_arguments = options.pop("additionalArguments", None)

    facet = ''
    facet += f'<facet type="kotlin-language" name="Kotlin">\n'
    facet += f'  <configuration version="3" useProjectSettings="false">\n'
    facet += f'    <compilerSettings>\n'
    if additional_arguments:
        facet += f'      <option name="additionalArguments" value="{additional_arguments}" />\n'
    facet += f'    </compilerSettings>\n'
    facet += f'    <compilerArguments>\n'
    for opt, value in options.items():
        facet += f'      <option name="{opt}" value="{value}" />\n'
    facet += f'    </compilerArguments>\n'
    facet += f'  </configuration>\n'
    facet += f'</facet>'

    return ET.fromstring(facet, ET.XMLParser(encoding="UTF-8"))


# Returns all the .iml files contained in an IDEA project.
def collect_iml_files(project_dir: Path) -> Iterator[Path]:
    modules_xml_path = project_dir.joinpath(".idea/modules.xml")
    modules_xml = parse_jps_file(modules_xml_path, {"PROJECT_DIR": project_dir})
    module_tags = modules_xml.findall("./component[@name='ProjectModuleManager']/modules/module")
    for module_tag in module_tags:
        iml_file = module_tag.get("filepath") or fail()
        iml_file = Path(iml_file).resolve()
        if not iml_file.exists():
            continue  # Most of these are unresolved refs to JetBrains/android modules.
        yield iml_file


# Reads a JPS file, with path variables substituted.
def read_jps_file(f: Path, path_vars: dict[str,Path]) -> str:
    text = f.read_text("UTF-8")
    for var, path in path_vars.items():
        # N.B. paths to output directories should stay relative.
        text = text.replace(f"${var}$/out", f"${var}_OUTDIR$")
        text = text.replace(f"${var}$", str(path))
        text = text.replace(f"${var}_OUTDIR$", f"${var}$/out")
    return text


# Parses a JPS XML file, with path variables substituted.
def parse_jps_file(f: Path, path_vars: dict[str,Path]) -> ET.ElementTree:
    text = read_jps_file(f, path_vars)
    return ET.ElementTree(ET.fromstring(text, ET.XMLParser(encoding="UTF-8")))


# Parses a project-level JPS XML file, with path variables substituted.
def parse_jps_project_file(project: JpsProject, relpath: str) -> ET.ElementTree:
    return parse_jps_file(project.root.joinpath(relpath), {"PROJECT_DIR": project.root})


# Writes an XML tree to disk, with default encoding and indentation.
def write_xml_file(xml: ET.ElementTree, outfile: Path):
    ET.indent(xml)
    xml.write(outfile, encoding="UTF-8")


# Synthesizes an empty JPS module.
def create_empty_jps_module(name: str) -> JpsModule:
    iml = (
        '<module type="JAVA_MODULE" version="4">\n'
        '  <component name="NewModuleRootManager" inherit-compiler-output="true">\n'
        '  </component>\n'
        '</module>'
    )
    xml = ET.ElementTree(ET.fromstring(iml, ET.XMLParser(encoding="UTF-8")))
    return JpsModule(name, xml)


# Synthesizes .idea/modules.xml from the given list of module files.
def write_module_list(iml_files: list[Path], outdir: Path):
    # Use relative paths so that the monobuild project can be moved without issue.
    iml_relpaths = [f"$PROJECT_DIR$/{posixpath.relpath(iml, outdir)}" for iml in iml_files]
    outfile = outdir.joinpath(".idea/modules.xml")
    assert not outfile.exists(), outfile
    with open(outfile, 'w', encoding="UTF-8") as f:
        f.write('<project version="4">\n')
        f.write('  <component name="ProjectModuleManager">\n')
        f.write('    <modules>\n')
        for iml_path in iml_relpaths:
            f.write(f'      <module fileurl="file://{iml_path}" filepath="{iml_path}" />\n')
        f.write('    </modules>\n')
        f.write('  </component>\n')
        f.write('</project>')


# Concatenates repeatable XML tags from two XML documents.
def concat_xml_elements(into_xml: ET.ElementTree, from_xml: ET.ElementTree, xpaths: list[str]):
    for xpath in xpaths:
        elements_to_copy = from_xml.findall(xpath)
        if not elements_to_copy:
            continue
        # Find insertion point, creating parent elements if needed.
        ancestors = get_ancestors(from_xml, xpath)
        target = into_xml.getroot()
        for ancestor in ancestors[1:-1]:
            target = get_or_create_child(target, ancestor.tag, **ancestor.attrib)
        # Insert the non-duplicates.
        for to_copy in elements_to_copy:
            if not any(structurally_equal(to_copy, existing) for existing in target):
                target.append(copy.deepcopy(to_copy))


# Creates an XML child element if it does not exist already.
def get_or_create_child(parent: ET.Element, tag: str, **attrs: str) -> ET.Element:
    for existing in parent.findall(f"./{tag}"):
        if existing.attrib == attrs:
            return existing
    return ET.SubElement(parent, tag, **attrs)


# Returns all XML elements along the path from the root to the selected XML element.
def get_ancestors(root: ET.ElementTree, xpath: str) -> list[ET.Element]:
    res: list[ET.Element] = []
    while True:
        ancestor = root.find(xpath)  # N.B. assumes at most one path exists.
        if ancestor is None:
            break
        res.append(ancestor)
        xpath += "/.."
    res.reverse()
    return res


# Returns whether two XML elements have the same tags, attributes, and children.
def structurally_equal(a: ET.Element, b: ET.Element) -> bool:
    if a.tag != b.tag: return False
    if a.attrib != b.attrib: return False
    return len(a) == len(b) and all(structurally_equal(*children) for children in zip(a, b))


def fail(msg: str = "unreachable") -> NoReturn:
    raise AssertionError(msg)


if __name__ == "__main__":
    main()
