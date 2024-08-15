/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.idea.blaze.android;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static java.io.File.separatorChar;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.utils.PathUtils;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;

/**
 * Image diffing utilities for render tests. These utility methods are cloned from
 * java/com/android/tools/adtui/imagediff/ImageDiffUtil.java that was deleted in change
 * I4e3d166b173be6eea9d0462b19e87a61c8f85b50 when it got converted to kotlin. (See
 * https://cs.android.com/android-studio/platform/tools/base/+/ea7ca7985f34cb7dfeb0e1f29ae5c0e49f78117b)
 */
public final class AswbImageDiffUtil {
  public static void assertImageSimilar(
      String imageName, BufferedImage goldenImage, BufferedImage image, double maxPercentDifferent)
      throws IOException {
    // If we get exactly the same object, no need to check--and they might be mocks anyway.
    if (goldenImage == image) {
      return;
    }

    int imageWidth = Math.min(goldenImage.getWidth(), image.getWidth());
    int imageHeight = Math.min(goldenImage.getHeight(), image.getHeight());

    int width = 3 * imageWidth;
    @SuppressWarnings("UnnecessaryLocalVariable")
    int height = imageHeight; // makes code more readable
    @SuppressWarnings("UndesirableClassUsage") // Don't want Retina images in unit tests
    BufferedImage deltaImage = new BufferedImage(width, height, TYPE_INT_ARGB);
    Graphics g = deltaImage.getGraphics();

    // Compute delta map
    double delta = 0;
    for (int y = 0; y < imageHeight; y++) {
      for (int x = 0; x < imageWidth; x++) {
        int goldenRgb = goldenImage.getRGB(x, y);
        int rgb = image.getRGB(x, y);
        if (goldenRgb == rgb) {
          deltaImage.setRGB(imageWidth + x, y, 0x00808080);
          continue;
        }

        // If the pixels have no opacity, don't delta colors at all
        if (((goldenRgb & 0xFF000000) == 0) && (rgb & 0xFF000000) == 0) {
          deltaImage.setRGB(imageWidth + x, y, 0x00808080);
          continue;
        }

        int deltaA = ((rgb & 0xFF000000) >>> 24) - ((goldenRgb & 0xFF000000) >>> 24);
        int newA = 128 + deltaA & 0xFF;
        int deltaR = ((rgb & 0xFF0000) >>> 16) - ((goldenRgb & 0xFF0000) >>> 16);
        int newR = 128 + deltaR & 0xFF;
        int deltaG = ((rgb & 0x00FF00) >>> 8) - ((goldenRgb & 0x00FF00) >>> 8);
        int newG = 128 + deltaG & 0xFF;
        int deltaB = (rgb & 0x0000FF) - (goldenRgb & 0x0000FF);
        int newB = 128 + deltaB & 0xFF;

        int newRGB = newA << 24 | newR << 16 | newG << 8 | newB;
        deltaImage.setRGB(imageWidth + x, y, newRGB);

        double dA = deltaA / 255.;
        double dR = deltaR / 255.;
        double dG = deltaG / 255.;
        double dB = deltaB / 255.;
        // Notice that maximum difference per pixel is 1, which is realized for completely opaque
        // black and white colors.
        delta += Math.sqrt((dA * dA + dR * dR + dG * dG + dB * dB) / 4.);
      }
    }

    double maxDiff = imageHeight * imageWidth;
    double percentDifference = (delta / maxDiff) * 100;

    String error = null;
    if (percentDifference > maxPercentDifferent) {
      error = String.format("Images differ (by %.2g%%)", percentDifference);
    } else if (Math.abs(goldenImage.getWidth() - image.getWidth()) >= 2) {
      error =
          "Widths differ too much for "
              + imageName
              + ": "
              + goldenImage.getWidth()
              + "x"
              + goldenImage.getHeight()
              + "vs"
              + image.getWidth()
              + "x"
              + image.getHeight();
    } else if (Math.abs(goldenImage.getHeight() - image.getHeight()) >= 2) {
      error =
          "Heights differ too much for "
              + imageName
              + ": "
              + goldenImage.getWidth()
              + "x"
              + goldenImage.getHeight()
              + "vs"
              + image.getWidth()
              + "x"
              + image.getHeight();
    }

    if (error != null) {
      // Expected on the left
      // Golden on the right
      g.drawImage(goldenImage, 0, 0, null);
      g.drawImage(image, 2 * imageWidth, 0, null);

      // Labels
      if (imageWidth > 80) {
        g.setColor(Color.RED);
        g.drawString("Expected", 10, 20);
        g.drawString("Actual", 2 * imageWidth + 10, 20);
      }

      // Write image diff to undeclared outputs dir so ResultStore archives.
      File output =
          new File(getTestOutputDir().toFile(), "delta-" + imageName.replace(separatorChar, '_'));
      if (output.exists()) {
        boolean deleted = output.delete();
        assertTrue(deleted);
      }
      output.mkdirs();
      ImageIO.write(deltaImage, "PNG", output);
      error += " - see details in archived file " + output.getPath();
      System.out.println(error);
      fail(error);
    }

    g.dispose();
  }

  public static Path getTestOutputDir() {
    String testOutputDir = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR");
    if (testOutputDir != null) {
      return Paths.get(testOutputDir);
    }
    try {
      Path tempDir = Files.createTempDirectory("");
      PathUtils.addRemovePathHook(tempDir);
      return tempDir;
    } catch (IOException e) {
      fail(e.getMessage());
    }
    return null;
  }

  private AswbImageDiffUtil() {}
}
