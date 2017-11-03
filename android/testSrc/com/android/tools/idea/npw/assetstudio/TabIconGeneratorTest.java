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

import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TabIconGeneratorTest {

    @SuppressWarnings("SameParameterValue")
    private static void checkGraphic(
            String folderName, String baseName, int minSdk, int expectedFileCount)
            throws IOException {
        TabIconGenerator generator = new TabIconGenerator();
        TabIconGenerator.TabOptions options = new TabIconGenerator.TabOptions();
        options.minSdk = minSdk;
        BitmapGeneratorTests.checkGraphic(
                expectedFileCount, folderName, baseName, generator, options);
    }

    @Test
    public void testTabs1() throws Exception {
        checkGraphic("tabs", "ic_tab_1", 1 /* minSdk */, 16 /* expectedFileCount */);
    }

    @Test
    public void testTabs2() throws Exception {
        checkGraphic("tabs-v5+", "ic_tab_1", 5, 8);
    }
}
