/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.projectsystem;

import com.android.manifmerger.ManifestSystemProperty;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

class ManifestValueProcessor {
  /**
   * Puts the key-value pair from a target's manifest_values map into either {@code directOverrides}
   * if the key corresponds to a manifest attribute that Blaze allows you to override directly, or
   * {@code placeholders} otherwise.
   *
   * @see <a
   *     href="https://docs.bazel.build/versions/master/be/android.html#android_binary.manifest_values">manifest_values</a>
   */
  static void processManifestValue(
      String key,
      String value,
      ImmutableMap.Builder<ManifestSystemProperty, String> directOverrides,
      ImmutableMap.Builder<String, String> placeholders) {
    switch (key) {
      case "applicationId":
        directOverrides.put(ManifestSystemProperty.Document.PACKAGE, value);
        break;
      case "versionCode":
        directOverrides.put(ManifestSystemProperty.Manifest.VERSION_CODE, value);
        break;
      case "versionName":
        directOverrides.put(ManifestSystemProperty.Manifest.VERSION_NAME, value);
        break;
      case "minSdkVersion":
        directOverrides.put(ManifestSystemProperty.UsesSdk.MIN_SDK_VERSION, value);
        break;
      case "targetSdkVersion":
        directOverrides.put(ManifestSystemProperty.UsesSdk.TARGET_SDK_VERSION, value);
        break;
      case "maxSdkVersion":
        directOverrides.put(ManifestSystemProperty.UsesSdk.MAX_SDK_VERSION, value);
        break;
      case "packageName":
        // From the doc: "packageName will be ignored and will be set from either applicationId if
        // specified or the package in manifest"
        break;
      default:
        placeholders.put(key, value);
    }
  }

  static String getPackageOverride(Map<ManifestSystemProperty, String> overrides) {
    return overrides.get(ManifestSystemProperty.Document.PACKAGE);
  }

  private ManifestValueProcessor() {}
}
