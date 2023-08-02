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
    new_entries = []
    new_entries.append(write_entry(out, args.svg, 'artwork/androidstudio.svg'))
    new_entries.append(write_entry(out, args.svg_small, 'artwork/androidstudio-small.svg'))
    new_entries.append(write_entry(out, args.svg, 'artwork/preview/androidstudio.svg'))
    new_entries.append(write_entry(out, args.svg_small, 'artwork/preview/androidstudio-small.svg'))

    with zipfile.ZipFile(args.resources_jar) as res_jar:
      entries = filter(lambda info: not info.filename in new_entries, res_jar.infolist())
      for e in entries:
        out.writestr(e.filename, res_jar.read(e.filename))

if __name__ == "__main__":
  main(sys.argv[1:])