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

import com.android.tools.adtui.actions.ActionTestUtils;
import com.android.tools.idea.flags.StudioFlags;
import com.google.common.truth.Truth;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import org.jetbrains.android.AndroidTestCase;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DeviceMenuActionTest extends AndroidTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    StudioFlags.NELE_NEW_DEVICE_MENU.override(false);
  }

  @Override
  protected void tearDown() throws Exception {
    StudioFlags.NELE_NEW_DEVICE_MENU.clearOverride();
    super.tearDown();
  }

  public void testActions() {
    Configuration configuration = mock(Configuration.class);
    ConfigurationModelModule configurationModelModule = new StudioConfigurationModelModule(myModule);
    when(configuration.getModule()).thenReturn(myModule);
    when(configuration.getConfigurationManager()).thenReturn(ConfigurationManager.getOrCreateInstance(myModule));
    when(configuration.getConfigModule()).thenReturn(configurationModelModule);

    ConfigurationHolder holder = () -> configuration;

    DeviceMenuAction menuAction = new DeviceMenuAction(holder, (oldDevice, newDevice) -> {});
    menuAction.updateActions(DataContext.EMPTY_CONTEXT);
    PresentationFactory presentationFactory = new PresentationFactory();
    //noinspection UnstableApiUsage
    Utils.expandActionGroup(menuAction, presentationFactory, DataContext.EMPTY_CONTEXT, ActionPlaces.TOOLBAR);
    String actual = ActionTestUtils.prettyPrintActions(menuAction, action -> !isAvdAction(action), presentationFactory);
    String expected =
      "\n" + // The selected device is empty because we use the mock configuration for testing.
      "    Phone\n" +
      "    5.0, 1080 \u00d7 1920, 420dpi (Pixel)\n" +
      "    5.0, 1080 \u00d7 1920, 420dpi (Pixel 2)\n" +
      "    5.2, 1080 \u00d7 1920, 420dpi (Nexus 5X)\n" +
      "    5.5, 1080 \u00d7 2160, 440dpi (Pixel 3)\n" +
      "    5.5, 1440 \u00d7 2560, 560dpi (Pixel XL)\n" +
      "    5.6, 1080 \u00d7 2220, 440dpi (Pixel 3a)\n" +
      "    5.7, 1440 \u00d7 2560, 560dpi (Nexus 6P)\n" +
      "    5.7, 1080 \u00d7 2280, 440dpi (Pixel 4)\n" +
      "    5.8, 1080 \u00d7 2340, 440dpi (Pixel 4a)\n" +
      "    6.0, 1440 \u00d7 2560, 560dpi (Nexus 6)\n" +
      "    6.0, 1440 \u00d7 2880, 560dpi (Pixel 2 XL)\n" +
      "    6.0, 1080 \u00d7 2160, 400dpi (Pixel 3a XL)\n" +
      "    6.0, 1080 \u00d7 2340, 440dpi (Pixel 5)\n" +
      "    6.1, 1080 \u00d7 2400, 420dpi (Pixel 6a)\n" +
      "    6.3, 1440 \u00d7 2960, 560dpi (Pixel 3 XL)\n" +
      "    6.3, 1440 \u00d7 3040, 560dpi (Pixel 4 XL)\n" +
      "    6.3, 1080 \u00d7 2400, 420dpi (Pixel 7)\n" +
      "    6.4, 1080 \u00d7 2400, 420dpi (Pixel 6)\n" +
      "    6.7, 1440 \u00d7 3120, 560dpi (Pixel 6 Pro)\n" +
      "    6.7, 1440 \u00d7 3120, 560dpi (Pixel 7 Pro)\n" +
      "    ------------------------------------------------------\n" +
      "    Tablet\n" +
      "    7.0, 800 \u00d7 1280, tvdpi (Nexus 7 2012)\n" +
      "    7.0, 1200 \u00d7 1920, xhdpi (Nexus 7)\n" +
      "    8.9, 2048 \u00d7 1536, xhdpi (Nexus 9)\n" +
      "    9.9, 2560 \u00d7 1800, xhdpi (Pixel C)\n" +
      "    10.1, 2560 \u00d7 1600, xhdpi (Nexus 10)\n" +
      "    ------------------------------------------------------\n" +
      "    Desktop\n" +
      "    14.0, 1366 × 768, mdpi (Small Desktop)\n" +
      "    15.0, 3840 × 2160, xhdpi (Medium Desktop)\n" +
      "    17.0, 1920 × 1080, mdpi (Large Desktop)\n" +
      "    ------------------------------------------------------\n" +
      "    Wear\n" +
      "    384 \u00d7 384, xhdpi (Small Round)\n" +
      "    402 \u00d7 476, xhdpi (Rectangular)\n" +
      "    360 \u00d7 360, xhdpi (Square)\n" +
      "    454 \u00d7 454, xhdpi (Large Round)\n" +
      "    ------------------------------------------------------\n" +
      "    TV\n" +
      "    4K, 3840 \u00d7 2160, xxxhdpi (TV)\n" +
      "    1080p, 1920 \u00d7 1080, xhdpi (TV)\n" +
      "    720p, 1280 \u00d7 720, tvdpi (TV)\n" +
      "    ------------------------------------------------------\n" +
      "    Automotive\n" +
      "    8.4, 1024 \u00d7 768, mdpi (Automotive 1024p landscape)\n" +
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
      "        Resizable (Experimental) (1080 \u00d7 2340, 420dpi)\n" +
      "         6.7\" Horizontal Fold-in (1080 \u00d7 2636, xxhdpi)\n" +
      "         7.0\" WSVGA (Tablet) (1024 \u00d7 600, mdpi)\n" +
      "         7.4\" Rollable (1600 \u00d7 2428, 420dpi)\n" +
      "         7.6\" Fold-in with outer display (1768 \u00d7 2208, 420dpi)\n" +
      "         8.0\" Fold-out (2200 \u00d7 2480, 420dpi)\n" +
      "        10.1\" WXGA (Tablet) (1280 \u00d7 800, mdpi)\n" +
      "        13.5\" Freeform (2560 \u00d7 1440, hdpi)\n" +
      "    Add Device Definition\n";
    Truth.assertThat(actual).isEqualTo(expected);
  }

  private static boolean isAvdAction(AnAction action) {
    String text = action.getTemplatePresentation().getText();
    return text != null && text.startsWith("AVD:");
  }
}
