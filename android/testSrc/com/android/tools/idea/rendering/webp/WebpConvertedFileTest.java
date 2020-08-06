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

import static com.android.tools.adtui.imagediff.ImageDiffUtil.assertImageSimilar;
import static com.google.common.truth.Truth.assertThat;

public class WebpConvertedFileTest extends AndroidTestCase {
  public void testLossless() throws Exception {
    WebpConversionSettings settings = new WebpConversionSettings();
    settings.skipTransparentImages = false;
    settings.lossless = true;

    VirtualFile file = myFixture.copyFileToProject("projects/basic/src/main/res/drawable/icon.png", "res/drawable/icon1.png");
    WebpConvertedFile convertedFile = WebpConvertedFile.create(file, settings);
    assertThat(convertedFile).isNotNull();
    assertThat(convertedFile.encoded).isNull();

    assertThat(convertedFile.sourceFile).isSameAs(file);
    assertThat(convertedFile.sourceFileSize).isEqualTo(2574);

    boolean converted = convertedFile.convert(settings);
    assertThat(converted).isTrue();
    assertThat(convertedFile.encoded).isNotNull();
    assertThat(convertedFile.saved).isGreaterThan(0L);

    assertImageSimilar(getName(), convertedFile.getSourceImage(), convertedFile.getEncodedImage(), 3);
  }

  public void testSkipTransparent() throws Exception {
    WebpConversionSettings settings = new WebpConversionSettings();
    settings.skipTransparentImages = true;
    settings.lossless = true;

    VirtualFile file = myFixture.copyFileToProject("projects/basic/src/main/res/drawable/icon.png", "res/drawable/icon2.png");
    WebpConvertedFile convertedFile = WebpConvertedFile.create(file, settings);
    assertThat(convertedFile).isNull();
  }

  public void testSkipLauncherIcons() throws Exception {
    WebpConversionSettings settings = new WebpConversionSettings();
    settings.skipTransparentImages = true;
    settings.lossless = true;

    VirtualFile file = myFixture.copyFileToProject("projects/basic/src/main/res/drawable/icon.png", "res/drawable-mdpi/ic_launcher.png");
    WebpConvertedFile convertedFile = WebpConvertedFile.create(file, settings);
    assertThat(convertedFile).isNull();
  }

  public void testSkipNinePatches() throws Exception {
    WebpConversionSettings settings = new WebpConversionSettings();
    settings.skipNinePatches = true;
    settings.lossless = true;

    VirtualFile file = myFixture.copyFileToProject("projects/basic/src/main/res/drawable/icon.png", "res/drawable/icon3.9.png");
    WebpConvertedFile convertedFile = WebpConvertedFile.create(file, settings);
    assertThat(convertedFile).isNull();
  }

  public void testLossy() throws Exception {
    WebpConversionSettings settings = new WebpConversionSettings();
    settings.skipTransparentImages = false;
    settings.lossless = false;
    settings.quality = 80;

    VirtualFile file = myFixture.copyFileToProject("projects/basic/src/main/res/drawable/icon.png", "res/drawable/icon4.png");
    WebpConvertedFile convertedFile = WebpConvertedFile.create(file, settings);
    assertThat(convertedFile).isNotNull();
    assertThat(convertedFile.encoded).isNull();

    assertThat(convertedFile.sourceFile).isSameAs(file);
    assertThat(convertedFile.sourceFileSize).isEqualTo(2574);

    boolean converted = convertedFile.convert(settings);
    assertThat(converted).isTrue();
    assertThat(convertedFile.encoded).isNotNull();
    assertThat(convertedFile.saved).isGreaterThan(0L);

    long prevSaved = convertedFile.saved;

    assertImageSimilar(getName(), convertedFile.getSourceImage(), convertedFile.getEncodedImage(), 3);

    // Re-encode at lower quality and check that we have savings
    settings.quality = 20;
    converted = convertedFile.convert(settings);
    assertThat(converted).isTrue();
    assertThat(convertedFile.encoded).isNotNull();
    assertThat(convertedFile.saved).isGreaterThan(prevSaved);
  }
}