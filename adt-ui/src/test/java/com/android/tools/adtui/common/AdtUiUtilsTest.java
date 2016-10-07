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

import junit.framework.Assert;
import junit.framework.TestCase;

import javax.swing.*;
import java.awt.*;

import static com.intellij.util.ui.SwingHelper.ELLIPSIS;

public class AdtUiUtilsTest extends TestCase {
  public void testGetFittedString() throws Exception {
    JLabel testLabel = new JLabel("Test");
    FontMetrics testMetrics = testLabel.getFontMetrics(AdtUiUtils.DEFAULT_FONT);

    String testString = "AAAA";
    int stringWidth = testMetrics.stringWidth(testString);
    int ellipsysWidth = testMetrics.stringWidth(ELLIPSIS);
    int perCharacterWidth = testMetrics.stringWidth("A");

    // Enough space to render the whole string so no truncation occurs
    Assert.assertEquals(testString, AdtUiUtils.getFittedString(testMetrics, testString, stringWidth, 1));

    // Not enough space for ellipsys so an empty string should be returned
    Assert.assertEquals("", AdtUiUtils.getFittedString(testMetrics, testString, ellipsysWidth - 1, 1));

    if (ellipsysWidth <= perCharacterWidth) {
      Assert.assertEquals("AA...", AdtUiUtils.getFittedString(testMetrics, testString, stringWidth - perCharacterWidth, 1));
      Assert.assertEquals("...", AdtUiUtils.getFittedString(testMetrics, testString, stringWidth - perCharacterWidth * 3, 1));
    } else {
      // The "..." width is greater than the character "A" width, so the function needs to truncate an additional "A" to fit "..."
      Assert.assertEquals("A...", AdtUiUtils.getFittedString(testMetrics, testString, stringWidth - perCharacterWidth, 1));
      Assert.assertEquals("", AdtUiUtils.getFittedString(testMetrics, testString, stringWidth - perCharacterWidth * 3, 1));
    }
  }
}