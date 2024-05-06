import os
import zipfile

def change_zip_entry(zip_path, to_replace, content, output_zip_path):
  with zipfile.ZipFile(zip_path, "r") as original_zip:
    with zipfile.ZipFile(output_zip_path, "w") as output_zip:
      for entry in original_zip.infolist():
        if entry.filename != to_replace:
          output_zip.writestr(entry, original_zip.read(entry))
        else:
          output_zip.writestr(entry, content)

def read_zip_entry(zip_path, entry, none_if_missing=False):
  with zipfile.ZipFile(zip_path) as zip:
    if none_if_missing and entry not in zip.namelist():
      return None
    data = zip.read(entry)
  return data.decode("utf-8")

def read_file(file_path):
  with open(file_path, "r", newline="") as f:
    return f.read()

def write_file(file_path, data):
  with open(file_path, "w", newline="") as f:
    f.write(data)