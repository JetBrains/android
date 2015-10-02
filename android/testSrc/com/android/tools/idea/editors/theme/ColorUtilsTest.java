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
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

import java.awt.*;
import java.util.Collection;

import static org.fest.assertions.Assertions.assertThat;

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
    context.setCurrentTheme(context.getThemeResolver().getTheme("Theme.MyTheme"));

    assertEquals("<html>Not enough contrast with <b>colorPrimary</b>", ColorUtils
      .getContrastWarningMessage(ColorUtils.getContrastColorsWithWarning(context, "textColor"), Color.WHITE));
    assertEquals("", ColorUtils.getContrastWarningMessage(ColorUtils.getContrastColorsWithWarning(context, "textColor"), Color.BLACK));
    assertEquals("<html>Not enough contrast with <b>textColor</b> and <b>textColorPrimary</b>",
                 ColorUtils.getContrastWarningMessage(ColorUtils.getContrastColorsWithWarning(context, "colorPrimary"), Color.WHITE));
    assertEquals("", ColorUtils.getContrastWarningMessage(ColorUtils.getContrastColorsWithWarning(context, "colorPrimary"), Color.BLACK));

    // Test non existing attribute names
    assertEquals("", ColorUtils.getContrastWarningMessage(ColorUtils.getContrastColorsWithWarning(context, ""), Color.WHITE));
    assertEquals("", ColorUtils.getContrastWarningMessage(ColorUtils.getContrastColorsWithWarning(context, "invented"), Color.WHITE));
  }

  public void testContrastColors() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_low_contrast.xml", "res/values/styles.xml");
    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);
    ThemeEditorContext context = new ThemeEditorContext(configuration);
    context.setCurrentTheme(context.getThemeResolver().getTheme("Theme.MyTheme"));

    Collection<Color>
      color = ColorUtils.getContrastColorsWithWarning(context, "colorPrimary").values();
    assertThat(color)
      .containsOnly(Color.decode("#EEEEEE"), Color.decode("#DDDDDD"));
    color = ColorUtils.getContrastColorsWithWarning(context, "colorPrimary").values();
    assertThat(color)
      .containsOnly(Color.decode("#EEEEEE"), Color.decode("#DDDDDD"));

    color = ColorUtils.getContrastColorsWithWarning(context, "").values();
    assertThat(color).isEmpty();
    color = ColorUtils.getContrastColorsWithWarning(context, "notExistent").values();
    assertThat(color).isEmpty();
  }
}
