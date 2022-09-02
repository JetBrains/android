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

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.util.ui.SwingHelper.ELLIPSIS;
import static org.junit.Assert.assertEquals;

import com.intellij.openapi.util.text.StringUtil;
import java.awt.Color;
import java.awt.Component;
import java.awt.FontMetrics;
import javax.swing.JLabel;
import javax.swing.JPanel;
import kotlin.sequences.SequencesKt;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AdtUiUtilsTest {

  @Test
  public void testShrinkToFit() {
    JLabel testLabel = new JLabel("Test");
    FontMetrics testMetrics = testLabel.getFontMetrics(AdtUiUtils.DEFAULT_FONT);

    String testString = StringUtil.repeat("A", 100);
    int stringWidth = testMetrics.stringWidth(testString);
    int ellipsisWidth = testMetrics.stringWidth(ELLIPSIS);
    int perCharacterWidth = testMetrics.stringWidth("A");

    // Enough space to render the whole string so no truncation occurs
    assertEquals(testString, AdtUiUtils.shrinkToFit(testString, testMetrics, stringWidth));

    // Not enough space for ellipsis so an empty string should be returned
    assertEquals("", AdtUiUtils.shrinkToFit(testString, testMetrics, ellipsisWidth - 1));

    // Just enough space for ellipsis, but we should still return an empty string (since "..." on
    // its own is useless)
    assertEquals("", AdtUiUtils.shrinkToFit(testString, testMetrics, ellipsisWidth));

    // Provide a threshold to avoid font width computation (performance optimization)
    assertEquals("", AdtUiUtils.shrinkToFit(testString, testMetrics, 1.0f, 1.0f));

    for (int i = 5; i < 80; ++i) {
      int spaceAvailable = i * perCharacterWidth;

      String shrunk = AdtUiUtils.shrinkToFit(testString, s -> testMetrics.stringWidth(s) <= spaceAvailable + ellipsisWidth);
      assertEquals(StringUtil.repeat("A", i) + "...", shrunk);
    }
  }

  @Test
  public void testOverlayColor() {
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

  @Test
  public void testAllComponents() {
    JPanel root = new JPanel();
    root.setToolTipText("root");

    JPanel childGroup = new JPanel();
    childGroup.setToolTipText("childGroup");
    childGroup.add(new JLabel("text1"));
    childGroup.add(new JLabel("text2"));

    root.add(childGroup);
    childGroup.add(new JLabel("text3"));
    childGroup.add(new JLabel("text4"));

    StringBuilder output = new StringBuilder();
    for (Component component : SequencesKt.asIterable(AdtUiUtils.allComponents(root))) {
      output
        .append(
          StringUtil.notNullize(component.getAccessibleContext().getAccessibleName())
        )
        .append(
          StringUtil.notNullize(component.getAccessibleContext().getAccessibleDescription())
        )
        .append("\n");
    }
    assertEquals(
      "childGroup\n" +
      "text1\n" +
      "text2\n" +
      "text3\n" +
      "text4\n",
      output.toString());
  }
}