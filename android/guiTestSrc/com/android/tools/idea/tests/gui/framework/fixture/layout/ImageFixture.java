/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.android.tools.idea.rendering.ImageUtils;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static com.android.tools.idea.rendering.RenderTestBase.assertImageSimilar;
import static com.android.tools.idea.rendering.RenderTestBase.getTempDir;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.tests.gui.framework.GuiTests.RELATIVE_DATA_PATH;
import static com.android.tools.idea.tests.gui.framework.GuiTests.getTestDataDir;
import static com.google.common.truth.Truth.assertAbout;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@SuppressWarnings("UndesirableClassUsage")
public class ImageFixture {
  /**
   * Normally, this test will fail when there is a missing thumbnail. However, when
   * you create creating a new test, it's useful to be able to turn this off such that
   * you can generate all the missing thumbnails in one go, rather than having to run
   * the test repeatedly to get to each new render assertion generating its thumbnail.
   */
  private static final boolean FAIL_ON_MISSING_THUMBNAIL = true;

  private int myThumbnailSize = 250;
  private double myMaxPercentDifference = 5.0;

  /** Creates a new image fixture with the default thumbnail sizes and default image delta percentage */
  public ImageFixture() {
  }

  /**
   * Sets the maximum dimension for thumbnails
   */
  public ImageFixture withThumbnailSize(int size) {
    myThumbnailSize = size;
    return this;
  }

  /**
   * Sets the maximum difference percentage (by default, 5%) in image comparisons
   */
  public ImageFixture withMaxDifference(double percent) {
    myMaxPercentDifference = percent;
    return this;
  }

  /**
   * Requires the given image (which can be any size) to match the thumbnail stored in the
   * given relative path in the GUI test data folder. The thumbnail may not exist yet,
   * in which case one is created.
   *
   * @param relativePath the relative path to the thumbnail (allowed to use either / or File.separator)
   * @param image the image to compare
   * @throws IOException if there is a problem reading/writing the thumbnails
   */
  public void requireSimilar(@NotNull String relativePath, @NotNull BufferedImage image) throws IOException {
    int maxDimension = Math.max(image.getWidth(), image.getHeight());
    double scale = myThumbnailSize / (double)maxDimension;
    BufferedImage thumbnail = ImageUtils.scale(image, scale, scale);

    InputStream is = ImageFixture.class.getResourceAsStream(relativePath);
    if (is == null) {
      File sourceDir = getTestDataDir();
      File thumbnailDir = sourceDir;
      if (thumbnailDir == null) {
        thumbnailDir = getTempDir();
      }
      assertAbout(file()).that(thumbnailDir).isDirectory();
      File file = new File(thumbnailDir, relativePath.replace('/', File.separatorChar));
      if (file.exists()) {
        BufferedImage goldenImage = ImageIO.read(file);
        assertImageSimilar(relativePath, goldenImage, thumbnail, myMaxPercentDifference);
      } else {
        File parent = file.getParentFile();
        if (!parent.exists()) {
          boolean ok = parent.mkdirs();
          assertTrue("Could not create directory " + parent.getPath(), ok);
        }
        ImageIO.write(thumbnail, "PNG", file);
        if (sourceDir == null) {
          String message = "Thumbnail did not exist. You should copy the following generated thumbnail file into $AOSP" + File.separator +
                           RELATIVE_DATA_PATH + " : " + file.getCanonicalPath();
          if (FAIL_ON_MISSING_THUMBNAIL) {
            fail(message);
          }
          else {
            System.out.println(message);
          }
        }
        else {
          String message = "File did not exist, created " + file.getCanonicalPath();
          if (FAIL_ON_MISSING_THUMBNAIL) {
            fail(message);
          }
          else {
            System.out.println(message);
          }
        }
      }
    }
    else {
      BufferedImage goldenImage = ImageIO.read(is);
      assertImageSimilar(relativePath, goldenImage, thumbnail, myMaxPercentDifference);
    }
  }
}
