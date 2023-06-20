"""A module containing intellij utils."""

import zipfile
import re

# TODO(b/265207847) Use dataclasses to remove boilerplate methods
class SdkVersion:
  major: str
  minor: str

  def __init__(self, major: str, minor: str):
    self.major = major
    self.minor = minor

  def __hash__(self):
    return hash((self.major, self.minor))

  def __eq__(self, other):
    return (self.major, self.minor) == (other.major, other.minor)

  def __repr__(self):
    return f'SdkVersion("{self.major}", "{self.minor}")'

def extract_sdk_version(resources_jar: str) -> SdkVersion:
  """Returns the SdkVersion from a resources.jar."""
  contents = ""
  with zipfile.ZipFile(resources_jar) as zip:
    data = zip.read('idea/AndroidStudioApplicationInfo.xml')
    contents = data.decode("utf-8")
  m = re.search(r'<version.*major="(\d+)".*>', contents)
  major = m.group(1)

  m = re.search(r'<version.*minor="(\d+)".*>', contents)
  minor = m.group(1)
  return SdkVersion(major=major, minor=minor)