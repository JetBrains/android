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
import com.android.tools.idea.npw.assetstudio.LauncherIconGenerator.LauncherOptions;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LauncherIconGeneratorTest {

    @SuppressWarnings("SameParameterValue")
    private static void checkGraphic(
            @NonNull String baseName,
            @NonNull GraphicGenerator.Shape shape,
            @NonNull GraphicGenerator.Style style,
            boolean crop,
            int background,
            boolean isWebGraphic)
            throws IOException {
        LauncherOptions options = new LauncherOptions();
        options.shape = shape;
        options.crop = crop;
        options.style = style;
        options.backgroundColor = background;
        options.isWebGraphic = isWebGraphic;

        LauncherIconGenerator generator = new LauncherIconGenerator();
        BitmapGeneratorTests.checkGraphic(
                5 + (isWebGraphic ? 1 : 0), "launcher", baseName, generator, options);
    }

    @Test
    public void testLauncher_simpleCircle() throws Exception {
        checkGraphic("red_simple_circle", GraphicGenerator.Shape.CIRCLE,
                GraphicGenerator.Style.SIMPLE, true, 0xFF0000, true);
    }
}
