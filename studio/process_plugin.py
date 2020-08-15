"""A utility to process platform plugins."""
import argparse
import os
import sys
import zipfile


def process_platform_plugin(jars, output, jar_file):
  """Process a plugin's jar and extracts its plugin.xml."""
  data = None
  jar_name = None
  for jar in jars:
    with zipfile.ZipFile(jar) as zip_file:
      names = zip_file.namelist()
      if "META-INF/plugin.xml" in names:
        if jar_name:
          print("Multiple plugin.xml were found (in %s and %s)" % (jar, jar_name))
          sys.exit(1)
        data = zip_file.read("META-INF/plugin.xml")
        jar_name = jar
  if not jar_name:
    print("Cannot find plugin.xml")
    sys.exit(1)

  with open(output, "wb") as out:
    out.write(data)
  with open(jar_file, "w") as txt:
    txt.write(os.path.basename(jar_name) + "\n")
  return

if __name__ == "__main__":
  parser = argparse.ArgumentParser()
  parser.add_argument(
      "--jar",
      dest="jars",
      action="append",
      required=True,
      help="The jars to be processed")
  parser.add_argument(
      "--plugin_xml",
      dest="xml",
      required=True,
      help="The path to the file where to save the plugin.xml contents")
  parser.add_argument(
      "--txt_path",
      dest="txt",
      required=True,
      help="Where to save the basename if the jar containing the plugin.xml")
  args = parser.parse_args()
  process_platform_plugin(args.jars, args.xml, args.txt)
