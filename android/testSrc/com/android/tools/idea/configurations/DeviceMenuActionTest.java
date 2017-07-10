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
package com.android.tools.idea.configurations;

import com.google.common.truth.Truth;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import org.jetbrains.android.AndroidTestCase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DeviceMenuActionTest extends AndroidTestCase {

  public void testDummy() {
    // placeholder to make PSQ happy until the test below can be re-enabled.
  }

  // https://code.google.com/p/android/issues/detail?id=227931
  public void /*test*/Actions() {
    ConfigurationHolder holder = mock(ConfigurationHolder.class);
    Configuration configuration = mock(Configuration.class);
    when(holder.getConfiguration()).thenReturn(configuration);
    when(configuration.getConfigurationManager()).thenReturn(ConfigurationManager.getOrCreateInstance(myModule));

    DefaultActionGroup actions = new DeviceMenuAction(holder);
    StringBuilder sb = new StringBuilder();
    prettyPrintActions(actions, sb, 0);
    String actual = sb.toString();
    String expected =
      "    3.7, 480 \u00d7 800, hdpi (Nexus One)\n" +
      "    4.0, 480 \u00d7 800, hdpi (Nexus S)\n" +
      "    4.7, 720 \u00d7 1280, xhdpi (Galaxy Nexus)\n" +
      "    4.7, 768 \u00d7 1280, xhdpi (Nexus 4)\n" +
      "    5.0, 1080 \u00d7 1920, xxhdpi (Nexus 5)\n" +
      "    ------------------------------------------------------\n" +
      "    5.0, 1080 \u00d7 1920, xxhdpi (Pixel)\n" +
      "    5.2, 1080 \u00d7 1920, 420dpi (Nexus 5X)\n" +
      "    5.5, 1440 \u00d7 2560, 560dpi (Pixel XL)\n" +
      "    5.7, 1440 \u00d7 2560, 560dpi (Nexus 6P)\n" +
      "    6.0, 1440 \u00d7 2560, 560dpi (Nexus 6)\n" +
      "    ------------------------------------------------------\n" +
      "    7.0, 800 \u00d7 1280, tvdpi (Nexus 7 2012)\n" +
      "    7.0, 1200 \u00d7 1920, xhdpi (Nexus 7)\n" +
      "    8.9, 2048 \u00d7 1536, xhdpi (Nexus 9)\n" +
      "    9.9, 2560 \u00d7 1800, xhdpi (Pixel C)\n" +
      "    10.1, 2560 \u00d7 1600, xhdpi (Nexus 10)\n" +
      "    ------------------------------------------------------\n" +
      "    320 \u00d7 290, tvdpi (Round Chin)\n" +
      "    280 \u00d7 280, hdpi (Square)\n" +
      "    320 \u00d7 320, hdpi (Round)\n" +
      "    ------------------------------------------------------\n" +
      "    1080p, 1920 \u00d7 1080, xhdpi (TV)\n" +
      "    720p, 1280 \u00d7 720, tvdpi (TV)\n" +
      "    ------------------------------------------------------\n" +
      "    Generic Phones and Tablets\n" +
      "        10.1\" WXGA (Tablet) (1280 \u00d7 800, mdpi)\n" +
      "         7.0\" WSVGA (Tablet) (1024 \u00d7 600, mdpi)\n" +
      "         5.4\" FWVGA (480 \u00d7 854, mdpi)\n" +
      "         5.1\" WVGA (480 \u00d7 800, mdpi)\n" +
      "         4.7\" WXGA (1280 \u00d7 720, xhdpi)\n" +
      "         4.65\" 720p (720 \u00d7 1280, xhdpi)\n" +
      "         4.0\" WVGA (480 \u00d7 800, hdpi)\n" +
      "         3.7\" FWVGA slider (480 \u00d7 854, hdpi)\n" +
      "         3.4\" WQVGA (240 \u00d7 432, ldpi)\n" +
      "         3.7\" WVGA (480 \u00d7 800, hdpi)\n" +
      "         3.3\" WQVGA (240 \u00d7 400, ldpi)\n" +
      "         3.2\" HVGA slider (ADP1) (320 \u00d7 480, mdpi)\n" +
      "         3.2\" QVGA (ADP2) (320 \u00d7 480, mdpi)\n" +
      "         2.7\" QVGA slider (240 \u00d7 320, ldpi)\n" +
      "         2.7\" QVGA (240 \u00d7 320, ldpi)\n" +
      "    Add Device Definition...\n";
    Truth.assertThat(actual).isEqualTo(expected);
  }

  private static void prettyPrintActions(AnAction action, StringBuilder sb, int depth) {
    String text;
    if (action instanceof Separator) {
      text = "------------------------------------------------------";
    }
    else {
      text = action.getTemplatePresentation().getText();
      if (text != null && text.startsWith("AVD:")) {
        // Skip AVD items in tests - these tend to vary from build environment to
        // build environment
        return;
      }
    }
    if (text != null) {
      for (int i = 0; i < depth; i++) {
        sb.append("    ");
      }
      sb.append(text).append("\n");
    }
    DefaultActionGroup group = action instanceof DefaultActionGroup ? (DefaultActionGroup)action : null;
    if (group != null) {
      for (AnAction child : group.getChildActionsOrStubs()) {
        prettyPrintActions(child, sb, depth + 1);
      }
    }
  }
}