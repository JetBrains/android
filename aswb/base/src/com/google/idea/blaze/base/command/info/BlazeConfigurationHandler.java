/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command.info;

import java.io.File;
import javax.annotation.Nullable;

/**
 * Encodes the default configuration for a given Blaze sync, and provides configuration-related
 * helper methods.
 */
public class BlazeConfigurationHandler {

  public final String defaultConfigurationPathComponent;
  private final String blazeOutPath;

  public BlazeConfigurationHandler(BlazeInfo blazeInfo) {
    // Would be simpler to use 'output_path' instead, but there's a Bazel-side bug causing that to
    // point to the wrong place. Instead derive 'output_path' from 'blaze-out'.
    // TODO: check whether github.com/bazelbuild/bazel/issues/3055 has actually been fixed
    File blazeOutDir = blazeInfo.getBlazeBinDirectory().getParentFile().getParentFile();
    blazeOutPath = blazeOutDir + File.separator;
    defaultConfigurationPathComponent = getConfigurationMnemonic(blazeInfo.getBlazeBinDirectory());
    assert (defaultConfigurationPathComponent != null);
  }

  @Nullable
  public String getConfigurationMnemonic(File artifact) {
    if (!artifact.getPath().startsWith(blazeOutPath)) {
      return null;
    }
    return getConfigurationMnemonic(artifact.getPath().substring(blazeOutPath.length()));
  }

  public static String getConfigurationMnemonic(String blazeOutRelativePath) {
    int endIndex = blazeOutRelativePath.indexOf(File.separatorChar);
    return endIndex == -1 ? blazeOutRelativePath : blazeOutRelativePath.substring(0, endIndex);
  }
}
