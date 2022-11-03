/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.android.SdkConstants.FD_ML_MODELS;
import static com.android.SdkConstants.FD_TEST;
import static com.android.SdkConstants.FD_UNIT_TEST;
import static com.android.testutils.ImageDiffUtil.assertImageSimilar;
import static com.android.tools.adtui.imagediff.ImageDiffTestUtil.DEFAULT_IMAGE_DIFF_THRESHOLD_PERCENT;

import com.android.io.Images;
import com.android.tools.idea.npw.assetstudio.assets.ImageAsset;
import com.android.tools.idea.projectsystem.AndroidModulePaths;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.common.ThreadLeakTracker;
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
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class AdaptiveIconGeneratorTest extends AndroidTestCase {
  private final List<String> myWarnings = new ArrayList<>();

  protected final AndroidModulePaths myProjectPaths = new AndroidModulePaths() {
    @Override
    @NotNull
    public File getModuleRoot() {
      return new File("/fictitious/root");
    }

    @Override
    @NotNull
    public File getSrcDirectory(@Nullable String packageName) {
      return new File(getModuleRoot(), "src");
    }

    @Override
    @NotNull
    public File getTestDirectory(@Nullable String packageName) {
      return new File(getModuleRoot(), FD_TEST);
    }

    @Override
    @NotNull
    public File getUnitTestDirectory(@Nullable String packageName) {
      return new File(getModuleRoot(), FD_UNIT_TEST);
    }

    @Override
    @NotNull
    public List<File> getResDirectories() {
      return ImmutableList.of(new File("/other/root"),
                              new File(getModuleRoot(), "resources"),
                              new File(getModuleRoot(), "generated/res"));
    }

    @Override
    @NotNull
    public File getAidlDirectory(@Nullable String packageName) {
      return new File(getModuleRoot(), "aidl");
    }

    @Override
    @NotNull
    public File getManifestDirectory() {
      return new File(getModuleRoot(), "manifests");
    }

    @Override
    @NotNull
    public List<File> getMlModelsDirectories() {
      return ImmutableList.of(new File(getModuleRoot(), FD_ML_MODELS));
    }
  };

  @NotNull
  protected ImageAsset createImageAsset(@NotNull String filename) {
    return createImageAsset(filename, false);
  }

  @NotNull
  protected ImageAsset createClipartAsset(@NotNull String filename) {
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

  protected void checkGeneratedIcons(@NotNull String[] expectedFilenames, @NotNull String... excludedFromContentComparison)
      throws IOException {
    checkGeneratedIcons(expectedFilenames, DEFAULT_IMAGE_DIFF_THRESHOLD_PERCENT, excludedFromContentComparison);
  }

  protected void checkGeneratedIcons(@NotNull String[] expectedFilenames, double imageDiffThresholdPercent,
                                     @NotNull String... excludedFromContentComparison) throws IOException {
    File resDir = getResDirectory(myProjectPaths);
    assertNotNull(resDir);
    Map<File, GeneratedIcon> pathIconMap = getIconGenerator().generateIntoIconMap(myProjectPaths, resDir);
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
                       Files.readString(goldenFile).replaceAll("(\r\n|\n)", CodeStyle.getSettings(getProject()).getLineSeparator()),
                       ((GeneratedXmlResource)icon).getXmlText());
        }
        else {
          BufferedImage goldenImage = Images.readImage(goldenFile);
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
        path = StringUtil.trimStart(path, commonPrefix);
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
      Images.writeImage(image, "PNG", file);
    }
  }

  protected abstract AdaptiveIconGenerator getIconGenerator();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    makeSureThatProjectVirtualFileIsNotNull();
    myWarnings.clear();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      // The RenderTask dispose thread may still be running.
      ThreadLeakTracker.longRunningThreadCreated(ApplicationManager.getApplication(), "RenderTask dispose");
      assertTrue(String.join("\n", myWarnings), myWarnings.isEmpty());
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Nullable
  public static File getResDirectory(@NotNull AndroidModulePaths paths) {
    List<File> directories = paths.getResDirectories();
    for (int i = directories.size(); --i >= 0;) {
      File dir = directories.get(i);
      File parent = dir.getParentFile();
      if (parent == null || !"generated".equals(parent.getName())) {
        return dir;
      }
    }

    return Iterables.getLast(directories, null);
  }
}
