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

  public void testActions() {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getModule()).thenReturn(myModule);
    when(configuration.getConfigurationManager()).thenReturn(ConfigurationManager.getOrCreateInstance(myModule));

    ConfigurationHolder holder = () -> configuration;

    DeviceMenuAction menuAction = new DeviceMenuAction(holder);
    menuAction.updateActions();
    StringBuilder sb = new StringBuilder();
    prettyPrintActions(menuAction, sb, 0);
    String actual = sb.toString();
    String expected =
      "\n" + // The selected device is empty because we use the mock configuration for testing.
      "    Phone\n" +
      "    5.0, 1080 \u00d7 1920, 420dpi (Pixel)\n" +
      "    5.0, 1080 \u00d7 1920, 420dpi (Pixel 2)\n" +
      "    5.2, 1080 \u00d7 1920, 420dpi (Nexus 5X)\n" +
      "    5.5, 1080 \u00d7 2160, 440dpi (Pixel 3)\n" +
      "    5.5, 1440 \u00d7 2560, 560dpi (Pixel XL)\n" +
      "    5.6, 1080 \u00d7 2220, 440dpi (Pixel 3a)\n" +
      "    5.6, 1080 \u00d7 2160, 400dpi (Pixel 3a XL)\n" +
      "    5.7, 1440 \u00d7 2560, 560dpi (Nexus 6P)\n" +
      "    6.0, 1440 \u00d7 2560, 560dpi (Nexus 6)\n" +
      "    6.0, 1440 \u00d7 2880, 560dpi (Pixel 2 XL)\n" +
      "    6.3, 1440 \u00d7 2960, 560dpi (Pixel 3 XL)\n" +
      "    ------------------------------------------------------\n" +
      "    Tablet\n" +
      "    7.0, 800 \u00d7 1280, tvdpi (Nexus 7 2012)\n" +
      "    7.0, 1200 \u00d7 1920, xhdpi (Nexus 7)\n" +
      "    8.9, 2048 \u00d7 1536, xhdpi (Nexus 9)\n" +
      "    9.9, 2560 \u00d7 1800, xhdpi (Pixel C)\n" +
      "    10.1, 2560 \u00d7 1600, xhdpi (Nexus 10)\n" +
      "    ------------------------------------------------------\n" +
      "    Wear\n" +
      "    280 \u00d7 280, hdpi (Square)\n" +
      "    320 \u00d7 320, hdpi (Round)\n" +
      "    320 \u00d7 290, tvdpi (Round Chin)\n" +
      "    ------------------------------------------------------\n" +
      "    TV\n" +
      "    1080p, 1920 \u00d7 1080, xhdpi (TV)\n" +
      "    720p, 1280 \u00d7 720, tvdpi (TV)\n" +
      "    ------------------------------------------------------\n" +
      "    Custom\n" +
      "    ------------------------------------------------------\n" +
      "    Generic Phones and Tablets\n" +
      "         2.7\" QVGA (240 \u00d7 320, ldpi)\n" +
      "         2.7\" QVGA slider (240 \u00d7 320, ldpi)\n" +
      "         3.2\" HVGA slider (ADP1) (320 \u00d7 480, mdpi)\n" +
      "         3.2\" QVGA (ADP2) (320 \u00d7 480, mdpi)\n" +
      "         3.3\" WQVGA (240 \u00d7 400, ldpi)\n" +
      "         3.4\" WQVGA (240 \u00d7 432, ldpi)\n" +
      "         3.7\" WVGA (480 \u00d7 800, hdpi)\n" +
      "         3.7\" FWVGA slider (480 \u00d7 854, hdpi)\n" +
      "         4.0\" WVGA (480 \u00d7 800, hdpi)\n" +
      "         4.65\" 720p (720 \u00d7 1280, xhdpi)\n" +
      "         4.7\" WXGA (1280 \u00d7 720, xhdpi)\n" +
      "         5.1\" WVGA (480 \u00d7 800, mdpi)\n" +
      "         5.4\" FWVGA (480 \u00d7 854, mdpi)\n" +
      "         7.0\" WSVGA (Tablet) (1024 \u00d7 600, mdpi)\n" +
      "         7.3\" Foldable (1536 \u00d7 2152, 420dpi)\n" +
      "         8.0\" Foldable (2200 \u00d7 2480, 420dpi)\n" +
      "        10.1\" WXGA (Tablet) (1280 \u00d7 800, mdpi)\n" +
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
    }
    if (text != null) {
      for (int i = 0; i < depth; i++) {
        sb.append("    ");
      }
      sb.append(text).append("\n");
    }
    DefaultActionGroup group = action instanceof DefaultActionGroup ? (DefaultActionGroup)action : null;
    if (group != null) {
      // for skipping the Separator of AVD section.
      boolean skipNext = false;

      for (AnAction child : group.getChildActionsOrStubs()) {
        if (isAvdAction(child)) {
          // Skip AVD items in tests - these tend to vary from build environment to build environment
          skipNext = true;
          continue;
        }
        if (skipNext) {
          skipNext = false;
          continue;
        }
        assert !skipNext;
        prettyPrintActions(child, sb, depth + 1);
      }
    }
  }

  private static boolean isAvdAction(AnAction action) {
    String text = action.getTemplatePresentation().getText();
    return text != null && text.startsWith("AVD:");
  }
}
