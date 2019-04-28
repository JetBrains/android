/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.assetstudio;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jetbrains.android.AndroidTestBase.getTestDataPath;
import static org.junit.Assert.fail;

import com.android.ide.common.util.AssetUtil;
import com.android.ide.common.util.PathString;
import com.android.ide.common.vectordrawable.VdIcon;
import com.android.resources.Density;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharStreams;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.imageio.ImageIO;
import org.jetbrains.annotations.NotNull;

/**
 * Shared test infrastructure for bitmap generator.
 */
public final class IconGeneratorTestUtil {
  public enum SourceType { CLIPART, PNG, SVG }

  static void checkGraphic(@NotNull IconGenerator generator,
                           @NotNull SourceType sourceType,
                           @NotNull String baseName,
                           int paddingPercent,
                           @NotNull List<String> expectedFolders,
                           @NotNull String goldenFileFolderName) throws IOException {
    ImageAsset imageAsset = new ImageAsset();
    if (sourceType == SourceType.CLIPART) {
      imageAsset.setClipart(true);
      imageAsset.color().setValue(new Color(0xA4C639));  // Android green.
    }
    File sourceFile = getSourceFile(sourceType);
    imageAsset.imagePath().setValue(sourceFile);
    imageAsset.paddingPercent().set(paddingPercent);

    try {
      checkGraphic(generator, imageAsset, baseName, expectedFolders, goldenFileFolderName);
    } finally {
      if (sourceType != SourceType.SVG) {
        // Delete the temporary PNG file created by the test.
        //noinspection ResultOfMethodCallIgnored
        sourceFile.delete();
      }
    }
  }

  private static void checkGraphic(@NotNull IconGenerator generator,
                                   @NotNull ImageAsset imageAsset,
                                   @NotNull String baseName,
                                   @NotNull List<String> expectedFolders,
                                   @NotNull String goldenFileFolderName) throws IOException {
    generator.sourceAsset().setValue(imageAsset);
    generator.outputName().set(baseName);
    IconGenerator.Options options = generator.createOptions(false);
    Collection<GeneratedIcon> icons = generator.generateIcons(options).getIcons();

    List<String> errors = new ArrayList<>();
    List<String> actualFolders = new ArrayList<>(icons.size());
    String testDataDir = getTestDataPath();
    Path goldenRoot = Paths.get(testDataDir, "images", goldenFileFolderName);
    for (GeneratedIcon generatedIcon : icons) {
      PathString relativePath = generatedIcon.getOutputPath();
      PathString folder = relativePath.getParent();
      actualFolders.add(folder == null ? "" : folder.getFileName());

      Path goldenBase = generatedIcon.getCategory() == IconCategory.WEB ? goldenRoot : goldenRoot.resolve("res");
      File goldenFile = goldenBase.resolve(relativePath.getNativePath()).toFile();
      try (InputStream is = new BufferedInputStream(new FileInputStream(goldenFile))) {
        if (generatedIcon instanceof GeneratedImageIcon) {
          BufferedImage image = ((GeneratedImageIcon)generatedIcon).getImage();
          BufferedImage goldenImage = ImageIO.read(is);
          Density density = ((GeneratedImageIcon)generatedIcon).getDensity();
          double maxDiffPercent = density == Density.NODPI ? 0.5 : 2.5 * Density.XXXHIGH.getDpiValue() / density.getDpiValue();
          assertImageSimilar(relativePath, goldenImage, image, maxDiffPercent);
        }
        else if (generatedIcon instanceof GeneratedXmlResource) {
          String text = ((GeneratedXmlResource)generatedIcon).getXmlText();
          String goldenText = CharStreams.toString(new InputStreamReader(is, UTF_8));
          assertThat(text.replace("\r\n", "\n")).isEqualTo(goldenText.replace("\r\n", "\n"));
        }

      } catch (FileNotFoundException e) {
        if (generatedIcon instanceof GeneratedImageIcon) {
          BufferedImage image = ((GeneratedImageIcon)generatedIcon).getImage();
          generateGoldenImage(image, goldenFile);
          errors.add("File did not exist, created " + goldenFile);
        }
        else if (generatedIcon instanceof GeneratedXmlResource) {
          String text = ((GeneratedXmlResource)generatedIcon).getXmlText();
          generateGoldenText(text, goldenFile);
          errors.add("File did not exist, created " + goldenFile);
        }
      }
    }
    assertThat(errors).isEmpty();

    assertThat(actualFolders).containsAllIn(expectedFolders);
  }

  @NotNull
  private static File getSourceFile(@NotNull SourceType sourceType) throws IOException {
    switch (sourceType) {
      case CLIPART:
        VirtualFile inputFile = VfsUtil.findFileByURL(MaterialDesignIcons.getDefaultIcon());
        File file = FileUtil.createTempFile("clipart", ".xml");
        try (InputStream input = inputFile.getInputStream(); OutputStream output = new BufferedOutputStream(new FileOutputStream(file))) {
          StreamUtil.copyStreamContent(input, output);
        }
        return file;

      case PNG: {
        VdIcon androidIcon = new VdIcon(MaterialDesignIcons.getDefaultIcon());
        BufferedImage sourceImage = androidIcon.renderIcon(512, 512);
        File pngFile = FileUtil.createTempFile("android", ".png");
        BufferedImage coloredImage = AssetUtil.filledImage(sourceImage, new Color(0xA4C639));
        ImageIO.write(coloredImage, "PNG", pngFile);
        return pngFile;
      }

      case SVG:
        return new File(getTestDataPath(), "images/svg/android.svg");

      default:
        throw new IllegalArgumentException("Unrecognized source type: " + sourceType.toString());
    }
  }

  private static void generateGoldenImage(@NotNull BufferedImage goldenImage, @NotNull File goldenFile) throws IOException {
    assert !goldenFile.exists();
    //noinspection ResultOfMethodCallIgnored
    goldenFile.getParentFile().mkdirs();
    ImageIO.write(goldenImage, "PNG", goldenFile);
  }

  private static void generateGoldenText(@NotNull String goldenText, @NotNull File goldenFile) throws IOException {
    assert !goldenFile.exists();
    //noinspection ResultOfMethodCallIgnored
    goldenFile.getParentFile().mkdirs();
    Files.write(goldenFile.toPath(), ImmutableList.of(goldenText), UTF_8);
  }

  @SuppressWarnings("SameParameterValue")
  private static void assertImageSimilar(@NotNull PathString imagePath,
                                         @NotNull BufferedImage goldenImage,
                                         @NotNull BufferedImage image,
                                         double maxPercentDifferent) throws IOException {
    assertThat(Math.abs(goldenImage.getWidth() - image.getWidth()))
        .named("difference in " + imagePath + " width")
        .isLessThan(2);
    assertThat(Math.abs(goldenImage.getHeight() - image.getHeight()))
        .named("difference in " + imagePath + " height")
        .isLessThan(2);

    assertThat(image.getType()).isEqualTo(BufferedImage.TYPE_INT_ARGB);

    if (goldenImage.getType() != BufferedImage.TYPE_INT_ARGB) {
      BufferedImage temp = AssetUtil.newArgbBufferedImage(goldenImage.getWidth(), goldenImage.getHeight());
      temp.getGraphics().drawImage(goldenImage, 0, 0, null);
      goldenImage = temp;
    }
    assertThat(goldenImage.getType()).isEqualTo(BufferedImage.TYPE_INT_ARGB);

    int imageWidth = Math.min(goldenImage.getWidth(), image.getWidth());
    int imageHeight = Math.min(goldenImage.getHeight(), image.getHeight());

    // Blur the images to account for the scenarios where there are pixel differences
    // in where a sharp edge occurs.
    // goldenImage = blur(goldenImage, 6);
    // image = blur(image, 6);

    BufferedImage deltaImage = AssetUtil.newArgbBufferedImage(3 * imageWidth, imageHeight);
    Graphics g = deltaImage.getGraphics();

    // Compute delta map
    long delta = 0;
    for (int y = 0; y < imageHeight; y++) {
      for (int x = 0; x < imageWidth; x++) {
        int goldenRgb = goldenImage.getRGB(x, y);
        int rgb = image.getRGB(x, y);
        if (goldenRgb == rgb) {
          deltaImage.setRGB(imageWidth + x, y, 0x00808080);
          continue;
        }

        // If the pixels have no opacity, don't delta colors at all.
        if (((goldenRgb & 0xFF000000) == 0) && (rgb & 0xFF000000) == 0) {
          deltaImage.setRGB(imageWidth + x, y, 0x00808080);
          continue;
        }

        int deltaR = ((rgb & 0xFF0000) >>> 16) - ((goldenRgb & 0xFF0000) >>> 16);
        int newR = 128 + deltaR & 0xFF;
        int deltaG = ((rgb & 0x00FF00) >>> 8) - ((goldenRgb & 0x00FF00) >>> 8);
        int newG = 128 + deltaG & 0xFF;
        int deltaB = (rgb & 0x0000FF) - (goldenRgb & 0x0000FF);
        int newB = 128 + deltaB & 0xFF;

        int avgAlpha = ((((goldenRgb & 0xFF000000) >>> 24) + ((rgb & 0xFF000000) >>> 24)) / 2) << 24;

        int newRGB = avgAlpha | newR << 16 | newG << 8 | newB;
        deltaImage.setRGB(imageWidth + x, y, newRGB);

        delta += Math.abs(deltaR);
        delta += Math.abs(deltaG);
        delta += Math.abs(deltaB);
      }
    }

    // 3 different colors, 256 color levels.
    long total = imageHeight * imageWidth * 3L * 256L;
    float percentDifference = (float) (delta * 100 / (double) total);

    if (percentDifference > maxPercentDifferent) {
      // Expected on the left, golden on the right.
      g.drawImage(goldenImage, 0, 0, null);
      g.drawImage(image, 2 * imageWidth, 0, null);

      // Labels
      if (imageWidth > 80) {
        g.setColor(Color.RED);
        g.drawString("Expected", 10, 20);
        g.drawString("Actual", 2 * imageWidth + 10, 20);
      }

      File output = new File(getTempDir(), "delta-" + imagePath.getRawPath().replace(File.separatorChar, '_'));
      if (output.exists()) {
        //noinspection ResultOfMethodCallIgnored
        output.delete();
      }
      ImageIO.write(deltaImage, "PNG", output);
      String message = String.format("Images differ (by %.1f%%) - see details in %s", percentDifference, output);
      fail(message);
    }

    g.dispose();
  }

  private static File getTempDir() {
    if (System.getProperty("os.name").equals("Mac OS X")) {
      return new File("/tmp");
    }

    return new File(System.getProperty("java.io.tmpdir"));
  }

  private IconGeneratorTestUtil() {}
}
