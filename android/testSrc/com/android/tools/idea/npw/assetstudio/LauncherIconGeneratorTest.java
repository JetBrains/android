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

import com.android.tools.idea.npw.assetstudio.assets.BaseAsset;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.npw.assetstudio.assets.VectorAsset;
import com.android.tools.idea.projectsystem.AndroidModuleTemplate;
import com.google.common.base.Joiner;
import com.google.common.io.Files;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.ThreadTracker;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

import static com.android.tools.adtui.imagediff.ImageDiffUtil.assertImageSimilar;

/**
 * Tests for {@link LauncherIconGenerator}.
 */
public class LauncherIconGeneratorTest extends AndroidTestCase {
  private static final double MAX_PERCENT_DIFFERENT = 0.005;

  private List<String> myWarnings = new ArrayList<>();
  private LauncherIconGenerator myIconGenerator;

  private AndroidModuleTemplate myProjectPaths = new AndroidModuleTemplate() {
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
    @Nullable
    public File getResDirectory() {
      return new File(getModuleRoot(), "res");
    }

    @Override
    @Nullable
    public File getAidlDirectory(@Nullable String packageName) {
      return new File(getModuleRoot(), "aidl");
    }

    @Override
    @Nullable
    public File getManifestDirectory() {
      return getModuleRoot();
    }
  };

  @Override
  public void setUp() throws Exception {
    super.setUp();
    makeSureThatProjectVirtualFileIsNotNull();

    myIconGenerator = new LauncherIconGenerator(myFacet, 15);
    myIconGenerator.outputName().set("ic_launcher");
    myIconGenerator.foregroundLayerName().set("ic_launcher_foreground");
    myIconGenerator.backgroundLayerName().set("ic_launcher_background");

    myWarnings.clear();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      Disposer.dispose(myIconGenerator);
      // The RenderTask dispose thread may still be running.
      ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication(), "RenderTask dispose");
      assertTrue(String.join("\n", myWarnings), myWarnings.isEmpty());
    } finally {
      super.tearDown();
    }
  }

  @NotNull
  private ImageAsset createImageAsset(@NotNull String filename) {
    ImageAsset asset = new ImageAsset();
    filename = FileUtil.join(getTestDataPath(), getTestName(true), filename);
    asset.imagePath().setNullableValue(new File(filename));
    return asset;
  }

  @NotNull
  private BaseAsset createClipartAsset(@NotNull String filename) {
    VectorAsset asset = new VectorAsset();
    filename = FileUtil.join(getTestDataPath(), getTestName(true), filename);
    asset.path().set(new File(filename));
    asset.outputWidth().set(432);
    asset.outputHeight().set(432);
    return asset;
  }

  private void checkGeneratedIcons(String[] expectedFilenames) throws IOException {
    Map<File, GeneratedIcon> pathIconMap = myIconGenerator.generateIntoIconMap(myProjectPaths);
    Set<File> unexpectedFiles = new HashSet<>(pathIconMap.keySet());
    File goldenDir = new File(FileUtil.join(getTestDataPath(), getTestName(true), "golden"));
    for (String filename : expectedFilenames) {
      File file = new File(myProjectPaths.getModuleRoot(), filename);
      GeneratedIcon icon = pathIconMap.get(file);
      assertTrue(filename + " icon is not generated.", icon != null);
      File goldenFile = new File(goldenDir, filename);
      if (!goldenFile.exists()) {
        createGolden(goldenFile, icon);
      }
      if (filename.endsWith(".xml")) {
        assertEquals("File " + filename + " does not match",
                     Files.toString(goldenFile, StandardCharsets.UTF_8), ((GeneratedXmlResource)icon).getXmlText());
      } else {
        BufferedImage goldenImage = ImageIO.read(goldenFile);
        assertImageSimilar(filename, goldenImage, ((GeneratedImageIcon)icon).getImage(), MAX_PERCENT_DIFFERENT);
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

  private void createGolden(@NotNull File file, @NotNull GeneratedIcon icon) throws IOException {
    myWarnings.add("Golden file " + file.getAbsolutePath() + " didn't exist, created by the test.");
    Files.createParentDirs(file);
    if (icon instanceof GeneratedXmlResource) {
      try (BufferedWriter writer = Files.newWriter(file, StandardCharsets.UTF_8)) {
        writer.write(((GeneratedXmlResource)icon).getXmlText());
      }
    } else {
      BufferedImage image = ((GeneratedImageIcon)icon).getImage();
      ImageIO.write(image, "PNG", file);
    }
  }

  public void testDrawableBackgroundAndForeground() throws Exception {
    String[] expectedFilenames = {
        "res/mipmap-anydpi-v26/ic_launcher.xml",
        "res/mipmap-anydpi-v26/ic_launcher_round.xml",
        "res/drawable/ic_launcher_background.xml",
        "res/drawable-v24/ic_launcher_foreground.xml",
        "res/mipmap-xxxhdpi/ic_launcher.png",
        "res/mipmap-xxxhdpi/ic_launcher_round.png",
        "res/mipmap-xxhdpi/ic_launcher.png",
        "res/mipmap-xxhdpi/ic_launcher_round.png",
        "res/mipmap-xhdpi/ic_launcher.png",
        "res/mipmap-xhdpi/ic_launcher_round.png",
        "res/mipmap-hdpi/ic_launcher.png",
        "res/mipmap-hdpi/ic_launcher_round.png",
        "res/mipmap-mdpi/ic_launcher.png",
        "res/mipmap-mdpi/ic_launcher_round.png",
        "ic_launcher-web.png"};
    myIconGenerator.sourceAsset().setNullableValue(createImageAsset("foreground.xml"));
    myIconGenerator.backgroundImageAsset().setNullableValue(createImageAsset("background.xml"));
    checkGeneratedIcons(expectedFilenames);
  }

  public void testImageBackgroundAndForeground() throws Exception {
    String[] expectedFilenames = {
      "res/mipmap-anydpi-v26/ic_launcher.xml",
      "res/mipmap-anydpi-v26/ic_launcher_round.xml",
      "res/mipmap-xxxhdpi/ic_launcher.png",
      "res/mipmap-xxxhdpi/ic_launcher_background.png",
      "res/mipmap-xxxhdpi/ic_launcher_foreground.png",
      "res/mipmap-xxxhdpi/ic_launcher_round.png",
      "res/mipmap-xxhdpi/ic_launcher.png",
      "res/mipmap-xxhdpi/ic_launcher_background.png",
      "res/mipmap-xxhdpi/ic_launcher_foreground.png",
      "res/mipmap-xxhdpi/ic_launcher_round.png",
      "res/mipmap-xhdpi/ic_launcher.png",
      "res/mipmap-xhdpi/ic_launcher_background.png",
      "res/mipmap-xhdpi/ic_launcher_foreground.png",
      "res/mipmap-xhdpi/ic_launcher_round.png",
      "res/mipmap-hdpi/ic_launcher.png",
      "res/mipmap-hdpi/ic_launcher_background.png",
      "res/mipmap-hdpi/ic_launcher_foreground.png",
      "res/mipmap-hdpi/ic_launcher_round.png",
      "res/mipmap-mdpi/ic_launcher.png",
      "res/mipmap-mdpi/ic_launcher_background.png",
      "res/mipmap-mdpi/ic_launcher_foreground.png",
      "res/mipmap-mdpi/ic_launcher_round.png",
      "ic_launcher-web.png"};
    myIconGenerator.sourceAsset().setNullableValue(createImageAsset("foreground.png"));
    myIconGenerator.backgroundImageAsset().setNullableValue(createImageAsset("background.png"));
    checkGeneratedIcons(expectedFilenames);
  }

  public void testClipart() throws Exception {
    String[] expectedFilenames = {
        "res/mipmap-anydpi-v26/ic_launcher.xml",
        "res/mipmap-anydpi-v26/ic_launcher_round.xml",
        "res/mipmap-xxxhdpi/ic_launcher.png",
        "res/mipmap-xxxhdpi/ic_launcher_foreground.png",
        "res/mipmap-xxhdpi/ic_launcher.png",
        "res/mipmap-xxhdpi/ic_launcher_foreground.png",
        "res/mipmap-xhdpi/ic_launcher.png",
        "res/mipmap-xhdpi/ic_launcher_foreground.png",
        "res/mipmap-hdpi/ic_launcher.png",
        "res/mipmap-hdpi/ic_launcher_foreground.png",
        "res/mipmap-mdpi/ic_launcher.png",
        "res/mipmap-mdpi/ic_launcher_foreground.png",
        "res/values/ic_launcher_background.xml",
        "ic_launcher-web.png"};
    myIconGenerator.sourceAsset().setNullableValue(createClipartAsset("ic_android_black_24dp.xml"));
    myIconGenerator.backgroundImageAsset().setNullableValue(null);
    //noinspection UseJBColor
    myIconGenerator.backgroundColor().set(new Color(0x26A69A));
    myIconGenerator.generateRoundIcon().set(false);
    myIconGenerator.legacyIconShape().set(IconGenerator.Shape.VRECT);
    myIconGenerator.webIconShape().set(IconGenerator.Shape.CIRCLE);
    checkGeneratedIcons(expectedFilenames);
  }
}
