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

import static com.android.tools.adtui.imagediff.ImageDiffUtil.DEFAULT_IMAGE_DIFF_THRESHOLD_PERCENT;
import static com.android.tools.adtui.imagediff.ImageDiffUtil.assertImageSimilar;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.npw.assetstudio.assets.TextAsset;
import com.android.tools.idea.projectsystem.AndroidModulePaths;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.ThreadTracker;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.imageio.ImageIO;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link LauncherIconGenerator}.
 */
public class LauncherIconGeneratorTest extends AndroidTestCase {
  private final List<String> myWarnings = new ArrayList<>();
  private LauncherIconGenerator myIconGenerator;

  private final AndroidModulePaths myProjectPaths = new AndroidModulePaths() {
    @Override
    @Nullable
    public File getModuleRoot() {
      return new File("/fictitious/root");
    }

    @Override
    @Nullable
    public File getSrcDirectory(@Nullable String packageName) {
      return new File(getModuleRoot(), "src");
    }

    @Override
    @Nullable
    public File getTestDirectory(@Nullable String packageName) {
      return new File(getModuleRoot(), "test");
    }

    @Override
    @NotNull
    public List<File> getResDirectories() {
      return ImmutableList.of(new File("/other/root"), new File(getModuleRoot(), "resources"));
    }

    @Override
    @Nullable
    public File getAidlDirectory(@Nullable String packageName) {
      return new File(getModuleRoot(), "aidl");
    }

    @Override
    @Nullable
    public File getManifestDirectory() {
      return new File(getModuleRoot(), "manifests");
    }
  };

  @Override
  public void setUp() throws Exception {
    super.setUp();
    makeSureThatProjectVirtualFileIsNotNull();

    DrawableRenderer renderer = new DrawableRenderer(myFacet);
    myIconGenerator = new LauncherIconGenerator(getProject(), 15, renderer);
    disposeOnTearDown(myIconGenerator);
    disposeOnTearDown(renderer);
    myIconGenerator.outputName().set("ic_launcher");
    myIconGenerator.foregroundLayerName().set("ic_launcher_foreground");
    myIconGenerator.backgroundLayerName().set("ic_launcher_background");

    myWarnings.clear();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      // The RenderTask dispose thread may still be running.
      ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication(), "RenderTask dispose");
      assertTrue(String.join("\n", myWarnings), myWarnings.isEmpty());
    } finally {
      super.tearDown();
    }
  }

  @NotNull
  private ImageAsset createImageAsset(@NotNull String filename) {
    return createImageAsset(filename, false);
  }

  @NotNull
  private ImageAsset createClipartAsset(@NotNull String filename) {
    return createImageAsset(filename, true);
  }

  @NotNull
  private ImageAsset createImageAsset(@NotNull String filename, boolean isClipart) {
    ImageAsset asset = new ImageAsset();
    filename = FileUtil.join(getTestDataPath(), getTestName(true), filename);
    asset.imagePath().setValue(new File(filename));
    asset.setClipart(isClipart);
    return asset;
  }

  private void checkGeneratedIcons(@NotNull String[] expectedFilenames, @NotNull String... excludedFromContentComparison)
      throws IOException {
    checkGeneratedIcons(expectedFilenames, DEFAULT_IMAGE_DIFF_THRESHOLD_PERCENT, excludedFromContentComparison);
  }

  private void checkGeneratedIcons(@NotNull String[] expectedFilenames, double imageDiffThresholdPercent,
                                   @NotNull String... excludedFromContentComparison) throws IOException {
    Map<File, GeneratedIcon> pathIconMap = myIconGenerator.generateIntoIconMap(myProjectPaths);
    Set<File> unexpectedFiles = new HashSet<>(pathIconMap.keySet());
    Path goldenDir = Paths.get(getTestDataPath(), getTestName(true), "golden");
    for (String filename : expectedFilenames) {
      File file = new File(myProjectPaths.getModuleRoot(), filename);
      GeneratedIcon icon = pathIconMap.get(file);
      assertNotNull(filename + " icon is not generated.", icon);
      Path goldenFile = goldenDir.resolve(filename);
      if (Files.notExists(goldenFile)) {
        createGolden(goldenFile, icon);
      }
      if (!Arrays.asList(excludedFromContentComparison).contains(filename)) {
        if (filename.endsWith(".xml")) {
          assertEquals("File " + filename + " does not match",
                       new String(Files.readAllBytes(goldenFile), UTF_8), ((GeneratedXmlResource)icon).getXmlText());
        }
        else {
          BufferedImage goldenImage = ImageIO.read(goldenFile.toFile());
          assertImageSimilar(filename, goldenImage, ((GeneratedImageIcon)icon).getImage(), imageDiffThresholdPercent);
        }
      }
      unexpectedFiles.remove(file);
    }
    if (!unexpectedFiles.isEmpty()) {
      String commonPrefix = getTestDataPath() + '/';
      Set<String> unexpected = new TreeSet<>();
      for (File file : unexpectedFiles) {
        String path = file.getAbsolutePath();
        if (path.startsWith(commonPrefix)) {
          path = path.substring(commonPrefix.length());
        }
        unexpected.add(path);
      }
      fail("Generated unexpected files: " + Joiner.on(", ").join(unexpected));
    }
  }

  private void createGolden(@NotNull Path file, @NotNull GeneratedIcon icon) throws IOException {
    myWarnings.add("Golden file " + file + " didn't exist, created by the test.");
    Files.createDirectories(file.getParent());
    if (icon instanceof GeneratedXmlResource) {
      try (BufferedWriter writer = Files.newBufferedWriter(file)) {
        writer.write(((GeneratedXmlResource)icon).getXmlText());
      }
    } else {
      BufferedImage image = ((GeneratedImageIcon)icon).getImage();
      ImageIO.write(image, "PNG", file.toFile());
    }
  }

  public void testDrawableBackgroundAndForeground() throws Exception {
    String[] expectedFilenames = {
        "resources/mipmap-anydpi-v26/ic_launcher.xml",
        "resources/mipmap-anydpi-v26/ic_launcher_round.xml",
        "resources/drawable/ic_launcher_background.xml",
        "resources/drawable-v24/ic_launcher_foreground.xml",
        "resources/mipmap-xxxhdpi/ic_launcher.png",
        "resources/mipmap-xxxhdpi/ic_launcher_round.png",
        "resources/mipmap-xxhdpi/ic_launcher.png",
        "resources/mipmap-xxhdpi/ic_launcher_round.png",
        "resources/mipmap-xhdpi/ic_launcher.png",
        "resources/mipmap-xhdpi/ic_launcher_round.png",
        "resources/mipmap-hdpi/ic_launcher.png",
        "resources/mipmap-hdpi/ic_launcher_round.png",
        "resources/mipmap-mdpi/ic_launcher.png",
        "resources/mipmap-mdpi/ic_launcher_round.png",
        "manifests/ic_launcher-playstore.png"};
    myIconGenerator.sourceAsset().setValue(createImageAsset("foreground.xml"));
    myIconGenerator.backgroundImageAsset().setValue(createImageAsset("background.xml"));
    checkGeneratedIcons(expectedFilenames);
  }

  public void testSingleLineText() throws Exception {
    String[] expectedFilenames = {
        "resources/mipmap-anydpi-v26/ic_launcher.xml",
        "resources/drawable/ic_launcher_foreground.xml",
        "resources/mipmap-xxxhdpi/ic_launcher.png",
        "resources/mipmap-xxhdpi/ic_launcher.png",
        "resources/mipmap-xhdpi/ic_launcher.png",
        "resources/mipmap-hdpi/ic_launcher.png",
        "resources/mipmap-mdpi/ic_launcher.png",
        "resources/values/ic_launcher_background.xml",
        "manifests/ic_launcher-playstore.png"};
    TextAsset textAsset = new TextAsset();
    textAsset.text().set("AL");
    textAsset.color().setValue(new Color(0x0000FF));
    myIconGenerator.sourceAsset().setValue(textAsset);
    myIconGenerator.backgroundImageAsset().setNullableValue(null);
    myIconGenerator.backgroundColor().set(new Color(0xFFFFFF));
    myIconGenerator.generateRoundIcon().set(false);
    // Don't compare context of ic_launcher_foreground.xml because it is slightly platform dependent.
    checkGeneratedIcons(expectedFilenames, 1.5, "resources/drawable/ic_launcher_foreground.xml");
  }

  public void testMultiLineText() throws Exception {
    String[] expectedFilenames = {
      "resources/mipmap-anydpi-v26/ic_launcher.xml",
      "resources/drawable/ic_launcher_foreground.xml",
      "resources/mipmap-xxxhdpi/ic_launcher.png",
      "resources/mipmap-xxhdpi/ic_launcher.png",
      "resources/mipmap-xhdpi/ic_launcher.png",
      "resources/mipmap-hdpi/ic_launcher.png",
      "resources/mipmap-mdpi/ic_launcher.png",
      "resources/values/ic_launcher_background.xml",
      "manifests/ic_launcher-playstore.png"};
    TextAsset textAsset = new TextAsset();
    textAsset.text().set("A\nW");
    textAsset.color().setValue(new Color(0x0000FF));
    myIconGenerator.sourceAsset().setValue(textAsset);
    myIconGenerator.backgroundImageAsset().setNullableValue(null);
    myIconGenerator.backgroundColor().set(new Color(0xFFFFFF));
    myIconGenerator.generateRoundIcon().set(false);
    // Don't compare context of ic_launcher_foreground.xml because it is slightly platform dependent.
    checkGeneratedIcons(expectedFilenames, 1.5, "resources/drawable/ic_launcher_foreground.xml");
  }

  public void testImageBackgroundAndForeground() throws Exception {
    String[] expectedFilenames = {
        "resources/mipmap-anydpi-v26/ic_launcher.xml",
        "resources/mipmap-anydpi-v26/ic_launcher_round.xml",
        "resources/mipmap-xxxhdpi/ic_launcher.png",
        "resources/mipmap-xxxhdpi/ic_launcher_background.png",
        "resources/mipmap-xxxhdpi/ic_launcher_foreground.png",
        "resources/mipmap-xxxhdpi/ic_launcher_round.png",
        "resources/mipmap-xxhdpi/ic_launcher.png",
        "resources/mipmap-xxhdpi/ic_launcher_background.png",
        "resources/mipmap-xxhdpi/ic_launcher_foreground.png",
        "resources/mipmap-xxhdpi/ic_launcher_round.png",
        "resources/mipmap-xhdpi/ic_launcher.png",
        "resources/mipmap-xhdpi/ic_launcher_background.png",
        "resources/mipmap-xhdpi/ic_launcher_foreground.png",
        "resources/mipmap-xhdpi/ic_launcher_round.png",
        "resources/mipmap-hdpi/ic_launcher.png",
        "resources/mipmap-hdpi/ic_launcher_background.png",
        "resources/mipmap-hdpi/ic_launcher_foreground.png",
        "resources/mipmap-hdpi/ic_launcher_round.png",
        "resources/mipmap-mdpi/ic_launcher.png",
        "resources/mipmap-mdpi/ic_launcher_background.png",
        "resources/mipmap-mdpi/ic_launcher_foreground.png",
        "resources/mipmap-mdpi/ic_launcher_round.png",
        "manifests/ic_launcher-playstore.png"};
    myIconGenerator.sourceAsset().setValue(createImageAsset("foreground.png"));
    myIconGenerator.backgroundImageAsset().setValue(createImageAsset("background.png"));
    checkGeneratedIcons(expectedFilenames);
  }

  public void testClipart() throws Exception {
    String[] expectedFilenames = {
        "resources/mipmap-anydpi-v26/ic_launcher.xml",
        "resources/drawable/ic_launcher_foreground.xml",
        "resources/mipmap-xxxhdpi/ic_launcher.png",
        "resources/mipmap-xxhdpi/ic_launcher.png",
        "resources/mipmap-xhdpi/ic_launcher.png",
        "resources/mipmap-hdpi/ic_launcher.png",
        "resources/mipmap-mdpi/ic_launcher.png",
        "resources/values/ic_launcher_background.xml",
        "manifests/ic_launcher-playstore.png"};
    ImageAsset asset = createClipartAsset("ic_android_black_24dp.xml");
    asset.color().setValue(new Color(0x00FF00));
    myIconGenerator.sourceAsset().setValue(asset);
    myIconGenerator.backgroundImageAsset().setNullableValue(null);
    //noinspection UseJBColor
    myIconGenerator.backgroundColor().set(new Color(0xFFFFFF));
    myIconGenerator.generateRoundIcon().set(false);
    myIconGenerator.legacyIconShape().set(IconGenerator.Shape.VRECT);
    checkGeneratedIcons(expectedFilenames);
  }
}
