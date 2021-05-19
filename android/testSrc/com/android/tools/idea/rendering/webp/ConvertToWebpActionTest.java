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
package com.android.tools.idea.rendering.webp;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

import java.util.Arrays;

import static com.google.common.truth.Truth.assertThat;

public class ConvertToWebpActionTest extends AndroidTestCase {
  public void testConvert() throws Exception {
    // Regression test for issue 226893
    // Ensure that images that are too large to encode are encoded anyway if the user asked for it
    WebpConversionSettings settings = new WebpConversionSettings();
    settings.skipTransparentImages = false;
    settings.skipLargerImages = true;
    settings.quality = 75;

    VirtualFile mdpi = myFixture.copyFileToProject("webp/ic_action_name-mdpi.png", "res/drawable-mdpi/ic_action_name.png");
    VirtualFile xhdpi = myFixture.copyFileToProject("webp/ic_action_name-xhdpi.png", "res/drawable-xhdpi/ic_action_name.png");
    VirtualFile mdpiFolder = mdpi.getParent();
    VirtualFile xhdpiFolder = xhdpi.getParent();

    ConvertToWebpAction action = new ConvertToWebpAction();
    action.convert(getProject(), settings, true, Arrays.asList(mdpi, xhdpi));

    // Check that we only converted the xhdpi image (the mdpi image encodes to a larger image)
    assertThat(xhdpiFolder.findChild("ic_action_name.png")).isNull();
    assertThat(xhdpiFolder.findChild("ic_action_name.webp")).isNotNull();
    assertThat(mdpiFolder.findChild("ic_action_name.png")).isNotNull();
    assertThat(mdpiFolder.findChild("ic_action_name.webp")).isNull();
  }

  public void testIncludeLargerImages() throws Exception {
    // Regression test for issue 226893
    // Ensure that images that are too large to encode are encoded anyway if the user asked for it
    WebpConversionSettings settings = new WebpConversionSettings();
    settings.skipTransparentImages = false;
    settings.skipLargerImages = false;
    settings.quality = 75;

    VirtualFile mdpi = myFixture.copyFileToProject("webp/ic_action_name-mdpi.png", "res/drawable-mdpi/ic_action_name.png");
    // test conversion of a transparent gray issue
    VirtualFile gray = myFixture.copyFileToProject("webp/ic_arrow_back.png", "res/drawable-mdpi/ic_arrow_back.png");
    VirtualFile xhdpi = myFixture.copyFileToProject("webp/ic_action_name-xhdpi.png", "res/drawable-xhdpi/ic_action_name.png");
    VirtualFile mdpiFolder = mdpi.getParent();
    VirtualFile xhdpiFolder = xhdpi.getParent();

    ConvertToWebpAction action = new ConvertToWebpAction();
    action.convert(getProject(), settings, true, Arrays.asList(mdpi, xhdpi, gray));

    // Check that we converted both images
    assertThat(xhdpiFolder.findChild("ic_action_name.png")).isNull();
    assertThat(xhdpiFolder.findChild("ic_action_name.webp")).isNotNull();
    assertThat(mdpiFolder.findChild("ic_action_name.png")).isNull();
    assertThat(mdpiFolder.findChild("ic_action_name.webp")).isNotNull();
    assertThat(mdpiFolder.findChild("ic_arrow_back.webp")).isNotNull();
    assertThat(mdpiFolder.findChild("ic_arrow_back.png")).isNull();
  }
}