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

import com.android.tools.idea.npw.assetstudio.ActionBarIconGenerator.ActionBarOptions;
import com.android.tools.idea.npw.assetstudio.ActionBarIconGenerator.Theme;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;

@RunWith(JUnit4.class)
public class ActionBarIconGeneratorTest {

    private static void checkGraphic(String baseName, Theme theme) throws IOException {
        ActionBarOptions options = new ActionBarOptions();
        options.theme = theme;

        ActionBarIconGenerator generator = new ActionBarIconGenerator();
        BitmapGeneratorTests.checkGraphic(4, "actions", baseName, generator, options);
    }

    @Test
    public void testDark() throws Exception {
        checkGraphic("ic_action_dark", Theme.HOLO_DARK);
    }

    @Test
    public void testLight() throws Exception {
        checkGraphic("ic_action_light", Theme.HOLO_LIGHT);
    }
}
