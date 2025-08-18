#!/usr/bin/env python3

import intellij
import mkspec
import os
from pathlib import Path

def main(workspace, sdk):
  ides = {}
  for platform in [intellij.LINUX, intellij.WIN, intellij.MAC, intellij.MAC_ARM]:
      ides[platform] = intellij.IntelliJ.create(
          platform,
          Path(f'{workspace}{sdk}/{platform}/sherlock'))
  mkspec.write_spec_file(f'{workspace}{sdk}/spec.bzl', ides)


if __name__ == "__main__":
  workspace = os.path.join(os.path.dirname(os.path.realpath(__file__)), "../../../../")
  sdk = "/prebuilts/studio/intellij-sdk/IC/"
  main(workspace, sdk)


