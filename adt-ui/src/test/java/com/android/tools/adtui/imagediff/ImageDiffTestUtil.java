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

import com.android.testutils.TestResources;
import com.android.tools.adtui.common.AdtUiUtils;
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

/**
 * Utility methods to be used by the tests of {@link com.android.tools.adtui.imagediff} package.
 */
public final class ImageDiffTestUtil {
  /**
   * Default threshold to be used when comparing two images.
   * If the calculated difference between the images is greater than this value (in %), the test should fail.
   */
  public static final double DEFAULT_IMAGE_DIFF_THRESHOLD_PERCENT = 0.5;

  private static final String DEFAULT_FONT_PATH = "/fonts/OpenSans-Regular.ttf";

  private static final float DEFAULT_FONT_SIZE = 12f;

  private static final String IMG_DIFF_TEMP_DIR = getTempDir() + "/imagediff";

  static {
    // Create tmpDir in case it doesn't exist
    new File(IMG_DIFF_TEMP_DIR).mkdirs();
  }

  /**
   * This font can be used in image comparison tests that generate images containing a lot of text.
   * As logical fonts might differ considerably depending on the OS and JDK, using a TrueType Font is safer.
   */
  public static Font getDefaultFont() {
    try {
      Font font = Font.createFont(Font.TRUETYPE_FONT, TestResources.getFile(ImageDiffTestUtil.class, DEFAULT_FONT_PATH));
      // Font is created with 1pt, so deriveFont can be used to resize it.
      return font.deriveFont(DEFAULT_FONT_SIZE);

    } catch (IOException | FontFormatException e) {
      System.err.println("Couldn't load default TrueType Font. Using a logical font instead.");
      return AdtUiUtils.DEFAULT_FONT;
    }
  }

  @NotNull
  // TODO move this function to a common location for all our tests
  public static File getTempDir() {
    if (System.getProperty("os.name").equals("Mac OS X")) {
      return new File("/tmp"); //$NON-NLS-1$
    }

    return new File(System.getProperty("java.io.tmpdir")); //$NON-NLS-1$
  }
}
