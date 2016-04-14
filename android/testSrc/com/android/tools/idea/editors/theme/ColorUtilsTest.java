/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import com.android.tools.idea.configurations.Configuration;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

import java.awt.*;
import java.util.Collection;
import java.util.LinkedHashMap;

import static org.fest.assertions.Assertions.assertThat;

@SuppressWarnings("InspectionUsingGrayColors")
public class ColorUtilsTest extends AndroidTestCase {
  public void testCalculateContrastRatio() {
    assertEquals(21.0, ColorUtils.calculateContrastRatio(Color.BLACK, Color.WHITE), 0.01);
    assertEquals(1.87, ColorUtils.calculateContrastRatio(Color.decode("#80CBC4"), Color.WHITE), 0.01);
    assertEquals(ColorUtils.calculateContrastRatio(Color.decode("#80CBC4"), Color.WHITE),
                 ColorUtils.calculateContrastRatio(Color.WHITE, Color.decode("#80CBC4")));
    assertEquals(1.96, ColorUtils.calculateContrastRatio(Color.WHITE, Color.decode("#CBBC06")), 0.01);
    assertEquals(1.24, ColorUtils.calculateContrastRatio(Color.BLACK, Color.decode("#2E054A")), 0.01);
    assertEquals(16.98, ColorUtils.calculateContrastRatio(Color.WHITE, Color.decode("#2E054A")), 0.01);
    assertEquals(5.25, ColorUtils.calculateContrastRatio(Color.BLACK, Color.RED), 0.01);
    assertEquals(ColorUtils.calculateContrastRatio(Color.BLACK, Color.RED), ColorUtils.calculateContrastRatio(Color.BLACK, Color.RED));
    assertEquals(9.10, ColorUtils.calculateContrastRatio(Color.decode("#2E054A"), Color.decode("#80CBC4")), 0.01);
  }

  public void testContrastWarning() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_low_contrast.xml", "res/values/styles.xml");
    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);
    ThemeEditorContext context = new ThemeEditorContext(configuration);
    context.setCurrentTheme(context.getThemeResolver().getTheme("MyTheme"));

    ImmutableMap<String, Color> textColorContrastColors = ColorUtils.getContrastColorsWithDescription(context, "textColor");
    ImmutableMap<String, Color> colorPrimaryContrastColors = ColorUtils.getContrastColorsWithDescription(context, "colorPrimary");

    assertEquals("<html>Not enough contrast with <b>colorPrimary</b>", ColorUtils
      .getContrastWarningMessage(textColorContrastColors, Color.WHITE, ColorUtils.isBackgroundAttribute("textColor")));
    assertEquals("", ColorUtils.getContrastWarningMessage(textColorContrastColors, Color.BLACK, ColorUtils.isBackgroundAttribute("textColor")));
    assertEquals("<html>Not enough contrast with <b>textColor</b> and <b>textColorPrimary</b>",
                 ColorUtils.getContrastWarningMessage(colorPrimaryContrastColors, Color.WHITE, ColorUtils.isBackgroundAttribute("colorPrimary")));
    assertEquals("", ColorUtils.getContrastWarningMessage(colorPrimaryContrastColors, Color.BLACK, ColorUtils.isBackgroundAttribute("colorPrimary")));

    // Test non existing attribute names
    assertEquals("", ColorUtils.getContrastWarningMessage(ColorUtils.getContrastColorsWithDescription(context, ""), Color.WHITE, false));
    assertEquals("", ColorUtils.getContrastWarningMessage(ColorUtils.getContrastColorsWithDescription(context, "invented"), Color.WHITE, true));

    // Test transparent colors
    assertEquals("<html>Not enough contrast with <b>colorPrimary</b>", ColorUtils
      .getContrastWarningMessage(textColorContrastColors, new Color(0, 0, 0, 50), ColorUtils.isBackgroundAttribute("textColor")));
    assertEquals("", ColorUtils
      .getContrastWarningMessage(textColorContrastColors, new Color(0, 0, 0, 250), ColorUtils.isBackgroundAttribute("textColor")));

    LinkedHashMap<String, Color> colorsWithDescription = new LinkedHashMap<String, Color>();
    colorsWithDescription.put("color very transparent", new Color(0, 0, 0, 50));
    colorsWithDescription.put("color a little transparent", new Color(0, 0, 0, 200));
    assertEquals("<html>Not enough contrast with color very transparent",
                 ColorUtils.getContrastWarningMessage(colorsWithDescription, new Color(255, 255, 255, 200), false));
    assertEquals("<html>Not enough contrast with color a little transparent and color very transparent",
                 ColorUtils.getContrastWarningMessage(colorsWithDescription, new Color(255, 0, 0, 200), false));
    assertEquals("<html>Not enough contrast with color very transparent",
                 ColorUtils.getContrastWarningMessage(colorsWithDescription, new Color(255, 0, 0), false));
    assertEquals("<html>Not enough contrast with color very transparent",
                 ColorUtils.getContrastWarningMessage(colorsWithDescription, new Color(0, 255, 0, 200), false));
  }

  public void testContrastColors() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_low_contrast.xml", "res/values/styles.xml");
    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);
    ThemeEditorContext context = new ThemeEditorContext(configuration);
    context.setCurrentTheme(context.getThemeResolver().getTheme("MyTheme"));

    Collection<Color>
      color = ColorUtils.getContrastColorsWithDescription(context, "colorPrimary").values();
    assertThat(color)
      .containsOnly(Color.decode("#EEEEEE"), Color.decode("#DDDDDD"));
    color = ColorUtils.getContrastColorsWithDescription(context, "colorPrimary").values();
    assertThat(color)
      .containsOnly(Color.decode("#EEEEEE"), Color.decode("#DDDDDD"));

    color = ColorUtils.getContrastColorsWithDescription(context, "").values();
    assertThat(color).isEmpty();
    color = ColorUtils.getContrastColorsWithDescription(context, "notExistent").values();
    assertThat(color).isEmpty();
  }

  public void testWorstContrastColor() {
    assertEquals(new Color(48, 78, 241), ColorUtils.worstContrastColor(new Color(48, 78, 241, 15), new Color(120, 46, 97, 0)));
    assertEquals(new Color(0, 0, 0), ColorUtils.worstContrastColor(new Color(48, 78, 241, 15), new Color(120, 46, 97, 255)));
    assertEquals(new Color(0, 255, 177), ColorUtils.worstContrastColor(new Color(10, 150, 100, 15), new Color(100, 10, 50, 155)));
    assertEquals(new Color(0, 255, 177), ColorUtils.worstContrastColor(new Color(10, 150, 100, 200), new Color(100, 10, 50, 155)));
  }

  public void testAlphaBlending() {
    assertEquals(new Color(48, 78, 241, 15), ColorUtils.alphaBlending(new Color(48, 78, 241, 15), new Color(120, 46, 97, 0)));
    assertEquals(new Color(255, 255, 255, 0), ColorUtils.alphaBlending(new Color(48, 78, 241, 0), new Color(120, 46, 97, 0)));
    assertEquals(new Color(120, 46, 97, 24), ColorUtils.alphaBlending(new Color(48, 78, 241, 0), new Color(120, 46, 97, 24)));
    assertEquals(new Color(48, 78, 241, 255), ColorUtils.alphaBlending(new Color(48, 78, 241, 255), new Color(120, 46, 97, 24)));
    assertEquals(new Color(116, 48, 105, 255), ColorUtils.alphaBlending(new Color(48, 78, 241, 15), new Color(120, 46, 97, 255)));
    assertEquals(new Color(91, 59, 154, 38), ColorUtils.alphaBlending(new Color(48, 78, 241, 15), new Color(120, 46, 97, 24)));
  }
}
