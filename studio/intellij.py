"""A module containing a representation of an intellij IDE installation."""

import zipfile
import json
import xml.etree.ElementTree as ET
import os
import re

LINUX = "linux"
WIN = "windows"
MAC = "darwin"
MAC_ARM = "darwin_aarch64"

_idea_home = {
  LINUX: "",
  WIN: "",
  MAC: "/Contents",
  MAC_ARM: "/Contents",
}

_idea_resources = {
  LINUX: "",
  WIN: "",
  MAC: "/Contents/Resources",
  MAC_ARM: "/Contents/Resources",
}

# TODO(b/265207847) Use dataclasses to remove boilerplate methods
class IntelliJ:
  platform: str
  major: str
  minor: str
  platform_jars: set[str]
  plugin_jars: dict[str,set[str]]

  def __init__(self, major: str, minor: str, platform_jars: set[str] = [], plugin_jars: dict[str,set[str]] = {}):
    self.major = major
    self.minor = minor
    self.platform_jars = platform_jars
    self.plugin_jars = plugin_jars

  def version(self):
    return self.major, self.minor

  def create(platform: str, path: str):
    major, minor = read_version(path + _idea_home[platform] + "/lib/resources.jar")
    product_info = read_product_info(path + _idea_resources[platform] + "/product-info.json")
    jars = read_platform_jars(product_info)
    plugin_jars = _read_plugin_jars(path + _idea_home[platform])
    return IntelliJ(major, minor, jars, plugin_jars)

def read_product_info(path):
  with open(path) as f:
    return json.load(f)

def read_version(resources_jar: str) -> (str, str):
  contents = ""
  with zipfile.ZipFile(resources_jar) as zip:
    data = zip.read('idea/AndroidStudioApplicationInfo.xml')
    contents = data.decode("utf-8")
  m = re.search(r'<version.*major="(\d+)".*minor="(\d+)".*>', contents)
  major = m.group(1)
  minor = m.group(2)
  return major, minor

def read_platform_jars(product_info):
  # Extract the runtime classpath from product-info.json.
  launch_config = product_info["launch"][0]
  jars = ["/lib/" + jar for jar in launch_config["bootClassPathJarNames"]]
  return set(jars)

def _read_zip_entry(zip_path, entry):
  with zipfile.ZipFile(zip_path) as zip:
    if entry not in zip.namelist():
      return None
    data = zip.read(entry)
  return data.decode("utf-8")

def _read_plugin_id(path):
  for jar in os.listdir(path + "/lib"):
    if jar.endswith(".jar"):
      entry = _read_zip_entry(path + "/lib/" + jar, "META-INF/plugin.xml")
      if entry:
        xml = ET.fromstring(entry)
        for id in xml.findall("id"):
          return id.text
  sys.exit("Failed to find plugin id in " + path)

def _read_plugin_jars(idea_home):
  plugins = {}
  for plugin in os.listdir(idea_home + "/plugins"):
    plugin_id = _read_plugin_id(idea_home + "/plugins/" + plugin)
    path = "/plugins/" + plugin + "/lib/"
    jars = [path + jar for jar in os.listdir(idea_home + path) if jar.endswith(".jar")]
    plugins[plugin_id] = set(jars)

  return plugins
