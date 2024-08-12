/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.run.binary;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.activity.ActivityLocator;
import com.google.idea.blaze.android.manifest.ManifestParser;
import org.jetbrains.annotations.NotNull;

/**
 * An activity launcher which extracts the default launch activity from a generated APK and starts
 * it.
 */
public class BlazeDefaultActivityLocator extends ActivityLocator {
  private final ManifestParser.ParsedManifest manifest;

  public BlazeDefaultActivityLocator(ManifestParser.ParsedManifest mergedManifestParsedManifest) {
    this.manifest = mergedManifestParsedManifest;
  }

  @Override
  public void validate() throws ActivityLocatorException {}

  @Override
  public String getQualifiedActivityName(@NotNull IDevice device) throws ActivityLocatorException {
    if (manifest == null) {
      throw new ActivityLocatorException("Could not locate merged manifest");
    }
    if (manifest.defaultActivityClassName == null) {
      throw new ActivityLocatorException("Could not locate default activity to launch.");
    }
    return manifest.defaultActivityClassName;
  }
}
