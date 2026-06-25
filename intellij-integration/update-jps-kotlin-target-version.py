#!/usr/bin/env python3
from pathlib import Path
from typing import Iterator, NoReturn
import argparse
import io
import sys
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description="Updates the Kotlin target version in the Studio JPS project")
    parser.add_argument("version", help="Desired Kotlin target version, e.g. 2.0")
    args = parser.parse_args()

    project_dir = Path(__file__).resolve().parent.parent
    assert (project_dir/".idea/modules.xml").exists(), f"Expected to find a JPS project at {project_dir}"

    iml_paths = list(collect_iml_files(project_dir))
    print(f"Updating Kotlin target version to {args.version} across {len(iml_paths)} modules in {project_dir}")

    # Update kotlinc.xml
    kotlinc_xml_path = project_dir.joinpath(".idea/kotlinc.xml")
    kotlinc_xml = parse_xml(kotlinc_xml_path)
    for opt in kotlinc_xml.findall("./component[@name='KotlinCommonCompilerArguments']/option"):
        if opt.get("name") in ("apiVersion", "languageVersion"):
            opt.set("value", args.version)
    write_xml(kotlinc_xml, kotlinc_xml_path)

    # Update module .iml files. We assume all modules should match the project-level defaults,
    # even for modules that set their own Kotlinc opts for other reasons.
    for iml_path in iml_paths:
        iml_file = parse_xml(iml_path)
        for compiler_args in iml_file.findall("./component[@name='FacetManager']/facet[@type='kotlin-language']/configuration/compilerArguments"):
            # Format 1.
            for string_arg in compiler_args.findall("./stringArguments/stringArg"):
                if string_arg.get("name") in ("apiVersion", "languageVersion"):
                    string_arg.set("arg", args.version)
            # Format 2.
            for option in compiler_args.findall("./option"):
                if option.get("name") in ("apiVersion", "languageVersion"):
                    option.set("value", args.version)
        write_xml(iml_file, iml_path)


# Returns all the .iml files contained in a JPS project.
def collect_iml_files(project_dir: Path) -> Iterator[Path]:
    modules_xml_path = project_dir.joinpath(".idea/modules.xml")
    modules_xml = parse_xml(modules_xml_path)
    module_tags = modules_xml.findall("./component[@name='ProjectModuleManager']/modules/module")
    for module_tag in module_tags:
        iml_file = module_tag.get("filepath") or fail()
        iml_file = iml_file.replace("$PROJECT_DIR$", str(project_dir))
        iml_file = Path(iml_file).resolve()
        if not iml_file.exists():
            print(f"Ignoring nonexistent module file: {iml_file}")
            continue
        yield iml_file


# Parses an XML file with UTF-8 encoding.
def parse_xml(f: Path) -> ET.ElementTree:
    return ET.parse(f, ET.XMLParser(encoding="UTF-8"))


# Writes an XML tree to disk, with appropriate encoding and indentation for JPS files.
def write_xml(xml: ET.ElementTree, outfile: Path):
    ET.indent(xml)
    buffer = io.BytesIO()
    buffer.write(b'<?xml version="1.0" encoding="UTF-8"?>\n')
    xml.write(buffer, encoding="UTF-8")
    new_content = buffer.getvalue()

    # Avoid writing out whitespace-only changes.
    if outfile.exists():
        old_content = outfile.read_bytes()
        if new_content.translate(None, b' \t\n\r') == old_content.translate(None, b' \t\n\r'):
            return

    print(f"Updating {outfile}")
    outfile.write_bytes(new_content)


def fail(msg: str = "unreachable") -> NoReturn:
    raise AssertionError(msg)


if __name__ == "__main__":
    main()
