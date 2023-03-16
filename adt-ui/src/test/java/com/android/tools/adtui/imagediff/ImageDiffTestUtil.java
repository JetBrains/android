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

import static com.android.testutils.ImageDiffUtil.assertImageSimilar;
import static com.android.testutils.ImageDiffUtil.convertToARGB;
import static java.awt.image.BufferedImage.TYPE_INT_ARGB;
import static org.junit.Assert.fail;

import com.android.testutils.TestResources;
import com.android.tools.adtui.TreeWalker;
import com.android.tools.adtui.common.AdtUiUtils;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
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

  private static final String TEST_DATA_DIR = "/imagediff/";

  private static final String DEFAULT_FONT_PATH = "/fonts/OpenSans-Regular.ttf";

  private static final float DEFAULT_FONT_SIZE = 12f;

  private static final String IMG_DIFF_TEMP_DIR = getTempDir() + "/imagediff";

  /**
   * Unmodifiable list containing all the {@link ImageDiffEntry} of the imagediff package.
   * They are used for running the tests of {@link com.android.tools.adtui.imagediff} package as well as exporting its baseline images.
   * When a generator is implemented, its entries should be included in this list.
   */
  public static final List<ImageDiffEntry> IMAGE_DIFF_ENTRIES = Collections.unmodifiableList(new ArrayList<>() {{
    addAll(new EventEntriesRegistrar().getImageDiffEntries());
    addAll(new HTreeChartEntriesRegistrar().getImageDiffEntries());
    addAll(new LegendComponentRegistrar().getImageDiffEntries());
    addAll(new LineChartEntriesRegistrar().getImageDiffEntries());
    addAll(new RangeEntriesRegistrar().getImageDiffEntries());
    addAll(new StateChartEntriesRegistrar().getImageDiffEntries());
    addAll(new CommonTabbedPaneEntriesRegistrar().getImageDiffEntries());
  }});

  static {
    // Create tmpDir in case it doesn't exist
    new File(IMG_DIFF_TEMP_DIR).mkdirs();
  }

  private ImageDiffTestUtil() {
  }

  public static void assertImagesSimilar(String baselineImageFilename, Component component, double scaleFactor,
                                         double similarityThreshold) {
    int scaledWidth = (int)Math.round(component.getWidth() * scaleFactor);
    int scaledHeight = (int)Math.round(component.getHeight() * scaleFactor);
    //noinspection UndesirableClassUsage
    BufferedImage buffer = new BufferedImage(scaledWidth, scaledHeight, TYPE_INT_ARGB);
    Graphics2D g = buffer.createGraphics();
    try {
      g.scale(scaleFactor, scaleFactor);
      component.paint(g);
    }
    finally {
      g.dispose();
    }
    File dir = TestResources.getDirectory(ImageDiffTestUtil.class, TEST_DATA_DIR);
    File goldenFile = new File(dir, baselineImageFilename);
    if (goldenFile.exists()) {
      assertImagesSimilar(baselineImageFilename, buffer, similarityThreshold);
    }
    else {
      //noinspection ResultOfMethodCallIgnored
      goldenFile.getParentFile().mkdirs();
      exportBaselineImage(goldenFile, buffer);
      fail("File did not exist, created here:" + goldenFile);
    }
  }

  /**
   * Creates a {@link BufferedImage} from a Swing component.
   */
  public static BufferedImage getImageFromComponent(Component component) {
    // Call doLayout in the content pane and its children
    synchronized (component.getTreeLock()) {
      TreeWalker walker = new TreeWalker(component);
      walker.descendantStream().forEach(Component::doLayout);
    }

    @SuppressWarnings("UndesirableClassUsage") // Don't want Retina images in unit tests
    BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    component.printAll(g);
    g.dispose();

    return image;
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

  /**
   * Exports an image as a baseline.
   *
   * @param destinationFile where the image should be exported to
   * @param image image to be exported
   */
  public static void exportBaselineImage(File destinationFile, BufferedImage image) {
    try {
      ImageIO.write(image, "PNG", destinationFile);
    } catch (IOException e) {
      System.err.println("Caught IOException while trying to export a baseline image: " + destinationFile.getName());
    }
  }

  /**
   * Compares a generated image with a baseline one. If the images differ by more than a determined percentage (similarityThreshold),
   * an image containing the expected, actual and diff images is generated and the test that calls this method fails.
   *
   * @param baselineImageFilename filename of the baseline image
   * @param generatedImage image generated by a test
   * @param similarityThreshold how much (in percent) the baseline and the generated images can differ to still be considered similar
   */
  public static void assertImagesSimilar(String baselineImageFilename, BufferedImage generatedImage, double similarityThreshold) {
    File baselineImageFile = TestResources.getFile(ImageDiffTestUtil.class, TEST_DATA_DIR + baselineImageFilename);
    BufferedImage baselineImage;

    try {
      baselineImage = convertToARGB(ImageIO.read(baselineImageFile));
      assertImageSimilar(baselineImageFilename, baselineImage, generatedImage, similarityThreshold);
    } catch (IOException e) {
      fail(e.getMessage());
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
