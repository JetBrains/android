/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.adtui.imagediff;

import java.io.File;

/**
 * Generates the baseline images for the {@link com.android.tools.adtui.imagediff} package.
 */
final class BaselineImagesGenerator {

  public static void main(String[] args) {
    String resourcesDir;

    try {
      resourcesDir = args[0];
    } catch (IndexOutOfBoundsException e) {
      System.err.println("Usage: BaselineImagesGenerator <path>\n" +
                         "where <path> is a full path to the imagediff test resources on your machine\n" +
                         "e.g. [...]/tools/adt/idea/adt-ui/src/test/resources/imagediff");
      return;
    }

    for (ImageDiffEntry entry : ImageDiffTestUtil.IMAGE_DIFF_ENTRIES) {
      File destinationFile = new File(resourcesDir, entry.getBaselineFilename());
      ImageDiffTestUtil.exportBaselineImage(destinationFile, entry.generateComponentImage());
    }

    // The program hangs unless I call System.exit(0) explicitly. The AWT event thread seems to keep running.
    // TODO: investigate why it's happening and how to proper handle the workflow to avoid calling System.exit explicitly.
    System.exit(0);
  }
}
