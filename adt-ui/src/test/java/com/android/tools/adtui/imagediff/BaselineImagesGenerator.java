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
class BaselineImagesGenerator {

  /**
   * This class has ".../tools/idea" as their working directory, while {@link ImageDiffUtil} has ".../tools/adt/idea".
   * Therefore, a relative path needs to be passed to {@link ImageDiffUtil#exportBaselineImage}.
   * TODO: figure out a better way of getting the resources directory.
   */
  private static final String TEST_RESOURCES_RELATIVE_DIR = "../adt/idea/adt-ui/src/test/resources/imagediff";

  public static void main(String[] args) {
    for (ImageDiffEntry entry : ImageDiffUtil.IMAGE_DIFF_ENTRIES) {
      // TODO: use TestResources.getFile to get/create the destination file
      File destinationFile = new File(TEST_RESOURCES_RELATIVE_DIR, entry.getBaselineFilename());
      ImageDiffUtil.exportBaselineImage(destinationFile, entry.generateComponentImage());
    }
    // TODO: investigate why this explicitly exit call is necessary.
    System.exit(0);
  }
}
