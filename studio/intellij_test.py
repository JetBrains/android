"""Tests the spec.bzl version values match the resources.jar contents."""

from collections import defaultdict
from pathlib import Path
import json
import os
import unittest
from tools.adt.idea.studio import intellij


def ides_map():
  """Returns a map of platform to jar path."""
  ides = {}
  for item in os.environ["intellij_paths"].split(","):
    platform, path_str = item.split("=")
    ides[platform] = Path(path_str)
  return ides


class IntelliJTest(unittest.TestCase):

  def test_version_matches_resources_jar(self):
    spec = json.loads(os.environ["spec"])
    expected_version = intellij.IntelliJ(
        major=spec["major_version"], minor=spec["minor_version"]
    ).version()

    # This also tests that all the versions are the same
    for platform, path in ides_map().items():
      ide = intellij.IntelliJ.create(platform, path)
      with self.subTest(msg=platform):
        self.assertEqual(spec["major_version"], ide.major)
        self.assertEqual(spec["minor_version"], ide.minor)
        self.assertEqual(
            set(spec["jars"] + spec["jars_" + platform]), set(ide.platform_jars)
        )
        plugins = defaultdict(set)
        for id, jars in list(spec["plugin_jars"].items()) + list(
            spec["plugin_jars_" + platform].items()
        ):
          if jars:
            plugins[id] |= set(jars)
        for id, jars in plugins.items():
          self.assertTrue(id in ide.plugin_jars)
          # Don't know why plugin jars don't start with leading / but platform jars do.
          self.assertEquals(jars, set([jar[1:] for jar in ide.plugin_jars[id]]))


if __name__ == "__main__":
  unittest.main()
