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

import com.android.tools.idea.npw.assetstudio.IconGeneratorTestUtil.SourceType;
import com.google.common.collect.ImmutableList;
import java.awt.Color;
import java.io.IOException;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Unit tests for the {@link LauncherLegacyIconGenerator} class.
 */
public class LauncherLegacyIconGeneratorTest extends AndroidTestCase {
  private void checkGraphic(@NotNull SourceType sourceType, int paddingPercent) throws IOException {
    LauncherLegacyIconGenerator generator = new LauncherLegacyIconGenerator(getProject(), 15, null);
    disposeOnTearDown(generator);
    generator.shape().set(IconGenerator.Shape.CIRCLE);
    generator.backgroundColor().set(new Color(0xFFFF00));
    List<String> expectedFolders = ImmutableList.of("", "mipmap-xxxhdpi", "mipmap-xxhdpi", "mipmap-xhdpi", "mipmap-hdpi", "mipmap-mdpi");
    IconGeneratorTestUtil.checkGraphic(generator, sourceType, "android_in_circle", paddingPercent, expectedFolders,
                                       paddingPercent >= 0 ? "launcher" : "launcher_cropped");
  }

  public void testPngPadded() throws Exception {
    checkGraphic(SourceType.PNG, 5);
  }

  public void testPngCropped() throws Exception {
    checkGraphic(SourceType.PNG, -10);
  }

  public void testSvgPadded() throws Exception {
    checkGraphic(SourceType.SVG, 5);
  }

  public void testSvgCropped() throws Exception {
    checkGraphic(SourceType.SVG, -10);
  }

  public void testClipartPadded() throws Exception {
    checkGraphic(SourceType.CLIPART, 5);
  }

  public void testClipartCropped() throws Exception {
    checkGraphic(SourceType.CLIPART, -10);
  }
}
