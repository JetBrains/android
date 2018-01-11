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
package com.android.tools.adtui.common;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import javax.swing.*;
import java.awt.*;

import static com.intellij.util.ui.SwingHelper.ELLIPSIS;
import static org.junit.Assert.assertEquals;

import static com.google.common.truth.Truth.assertThat;

@RunWith(JUnit4.class)
public class AdtUiUtilsTest {

  @Test
  public void testShrinkToFit() throws Exception {
    JLabel testLabel = new JLabel("Test");
    FontMetrics testMetrics = testLabel.getFontMetrics(AdtUiUtils.DEFAULT_FONT);

    String testString = "AAAA";
    int stringWidth = testMetrics.stringWidth(testString);
    int ellipsysWidth = testMetrics.stringWidth(ELLIPSIS);
    int perCharacterWidth = testMetrics.stringWidth("A");

    // Enough space to render the whole string so no truncation occurs
    assertEquals(testString, AdtUiUtils.shrinkToFit(testString, testMetrics, stringWidth, 1));

    // Not enough space for ellipsys so an empty string should be returned
    assertEquals("", AdtUiUtils.shrinkToFit(testString, testMetrics, ellipsysWidth - 1, 1));

    if (ellipsysWidth <= perCharacterWidth) {
      assertEquals("AA...", AdtUiUtils.shrinkToFit(testString, testMetrics, stringWidth - perCharacterWidth, 1));
      assertEquals("...", AdtUiUtils.shrinkToFit(testString, testMetrics, stringWidth - perCharacterWidth * 3, 1));
    } else {
      // The "..." width is greater than the character "A" width, so the function needs to truncate an additional "A" to fit "..."
      assertEquals("A...", AdtUiUtils.shrinkToFit(testString, testMetrics, stringWidth - perCharacterWidth, 1));
      assertEquals("", AdtUiUtils.shrinkToFit(testString, testMetrics, stringWidth - perCharacterWidth * 3, 1));
    }
  }

  @Test
  public void testOverlayColor() throws Exception {
    float opacity = 0.8f;
    Color grey_0_8 = AdtUiUtils.overlayColor(Color.BLACK.getRGB(), Color.WHITE.getRGB(), opacity);
    long expected = Math.round(255 * opacity);
    assertThat(grey_0_8.getRed()).isEqualTo(expected);
    assertThat(grey_0_8.getGreen()).isEqualTo(expected);
    assertThat(grey_0_8.getBlue()).isEqualTo(expected);

    int background = 0xF000A0;
    int foreground = 0x00CC0B;
    Color mix = AdtUiUtils.overlayColor(background, foreground, 0.6f);
    assertThat(mix.getRed()).isEqualTo(Math.round(0xF0 * 0.4f));
    assertThat(mix.getGreen()).isEqualTo(Math.round(0xCC * 0.6f));
    assertThat(mix.getBlue()).isEqualTo(Math.round(0xA0 * 0.4f + 0x0B * 0.6f));
  }
}