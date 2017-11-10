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

import com.android.annotations.NonNull;
import com.android.tools.idea.npw.assetstudio.LauncherLegacyIconGenerator.LauncherLegacyOptions;
import com.intellij.openapi.util.Disposer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

/**
 * Unit tests for {@link LauncherLegacyIconGenerator} class.
 */
@RunWith(JUnit4.class)
public class LauncherLegacyIconGeneratorTest {
  @SuppressWarnings("SameParameterValue")
  private static void checkGraphic(
      @NonNull String baseName,
      @NonNull IconGenerator.Shape shape,
      @NonNull IconGenerator.Style style,
      boolean crop,
      int background,
      boolean generateWebIcon)
      throws IOException {
    LauncherLegacyOptions options = new LauncherLegacyOptions();
    options.shape = shape;
    options.crop = crop;
    options.style = style;
    options.backgroundColor = background;
    options.generateWebIcon = generateWebIcon;

    LauncherLegacyIconGenerator generator = new LauncherLegacyIconGenerator(15);
    BitmapGeneratorTests.checkGraphic(5 + (generateWebIcon ? 1 : 0), "launcher", baseName, generator, options);
    Disposer.dispose(generator);
  }

  @Test
  public void testLauncher_simpleCircle() throws Exception {
    checkGraphic("red_simple_circle", IconGenerator.Shape.CIRCLE, IconGenerator.Style.SIMPLE, true, 0xFF0000, true);
  }
}
