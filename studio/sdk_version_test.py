"""Tests the spec.bzl version values match the resources.jar contents."""

import os
import unittest
from tools.adt.idea.studio import intellij


def resource_jar_map():
    """ Returns a map of platform to jar path. """
    jar_map = {}
    for item in os.environ['intellij_resource_jars'].split(','):
        platform, jar_path = item.split('=')
        jar_map[platform] = jar_path
    return jar_map


class SdkVersionTest(unittest.TestCase):

    def test_version_consistent_between_platforms(self):
        jar_map = resource_jar_map()

        version_map = {platform: intellij.extract_sdk_version(
            res_jar) for platform, res_jar in jar_map.items()}

        if len(set(version_map.values())) > 1:
            self.fail(
                f'intellij versions differ between platforms! {version_map}')

    def test_version_matches_resources_jar(self):
        expected_version = intellij.SdkVersion(
            major=os.environ['expected_major_version'],
            minor=os.environ['expected_minor_version'],
        )
        jar_map = resource_jar_map()

        for platform, res_jar in resource_jar_map().items():
            with self.subTest(msg=platform):
                self.assertEqual(
                    expected_version,
                    intellij.extract_sdk_version(res_jar),
                    f'expected version does not match version in {res_jar}')


if __name__ == "__main__":
    unittest.main()
