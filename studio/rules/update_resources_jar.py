"""A tool to replace files in the intelliJ resources.jar."""
import argparse
import sys
import zipfile

def write_entry(zip: zipfile.ZipFile, filename: str, arcname: str) -> str:
  """Writes the filename at arcname, returns the arcname."""
  zip.write(filename, arcname)
  return arcname

def main(argv):
  parser = argparse.ArgumentParser()
  parser.add_argument("--resources_jar", help="The resources jar being modified")
  parser.add_argument("--out", help="The updated resources jar")
  parser.add_argument("--svg", help="The icon as a SVG file")
  parser.add_argument("--svg_small", help="The icon as a smaller SVG")

  args = parser.parse_args(argv)

  with zipfile.ZipFile(args.out, 'w') as out:
    replaced = []
    replaced.append(write_entry(out, args.svg, 'artwork/androidstudio.svg'))
    replaced.append(write_entry(out, args.svg_small, 'artwork/androidstudio-small.svg'))
    replaced.append(write_entry(out, args.svg, 'artwork/preview/androidstudio.svg'))
    replaced.append(write_entry(out, args.svg_small, 'artwork/preview/androidstudio-small.svg'))

    with zipfile.ZipFile(args.resources_jar) as res_jar:
      for f in replaced:
        if f not in res_jar.namelist():
          sys.exit(f"ERROR: file '{f}' not found in the original resource jar. Did the icon locations change?")
      for e in res_jar.infolist():
        if e.filename not in replaced:
          out.writestr(e.filename, res_jar.read(e.filename))

if __name__ == "__main__":
  main(sys.argv[1:])