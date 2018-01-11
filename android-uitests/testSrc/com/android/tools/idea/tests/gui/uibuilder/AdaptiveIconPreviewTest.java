/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.uibuilder;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlConfigurationToolbarFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlPreviewFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;

@RunIn(TestGroup.UNRELIABLE)  // b/71749347
@RunWith(GuiTestRunner.class)
public class AdaptiveIconPreviewTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void densitySelector() throws IOException {
    NlPreviewFixture preview =
      guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
        .getEditor()
        .open("app/src/main/res/mipmap-anydpi-v26/ic_launcher_adaptive.xml")
        .getLayoutPreview(true);
    Point adaptiveIconTopLeftCorner = preview.getAdaptiveIconTopLeftCorner();
    NlConfigurationToolbarFixture<NlPreviewFixture> toolbar = preview.getConfigToolbar();
    toolbar.requireDensity("xxxhdpi")
      .chooseDensity("mdpi")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    // noinspection SpellCheckingInspection
    assertThat(preview.getPixelColor(adaptiveIconTopLeftCorner)).isEqualTo("ffff0000");
    toolbar.chooseDensity("hdpi")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(preview.getPixelColor(adaptiveIconTopLeftCorner)).isEqualTo("ff0000ff");
    toolbar.chooseDensity("xhdpi")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(preview.getPixelColor(adaptiveIconTopLeftCorner)).isEqualTo("ff0000ff");
    toolbar.chooseDensity("xxhdpi")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(preview.getPixelColor(adaptiveIconTopLeftCorner)).isEqualTo("ff00ff00");
    toolbar.chooseDensity("xxxhdpi")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(preview.getPixelColor(adaptiveIconTopLeftCorner)).isEqualTo("ff00ff00");
  }

  @Test
  public void shapeSelector() throws IOException {
    NlPreviewFixture preview =
      guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
        .getEditor()
        .open("app/src/main/res/mipmap-anydpi-v26/ic_launcher_adaptive.xml")
        .getLayoutPreview(true);
    Point adaptiveIconTopLeftCorner = preview.getAdaptiveIconTopLeftCorner();
    NlConfigurationToolbarFixture<NlPreviewFixture> toolbar = preview.getConfigToolbar();
    toolbar.chooseShape("Circle")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(preview.getAdaptiveIconPathDescription())
      .isEqualTo("M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 22.4 22.4 0 50 0Z");
    assertThat(preview.getPixelColor(adaptiveIconTopLeftCorner)).isEqualTo("fff5f5f5");
    toolbar.chooseShape("Rounded Corners")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(preview.getAdaptiveIconPathDescription()).isEqualTo(
      "M50,0L92,0C96.42,0 100,4.58 100 8L100,92C100, 96.42 96.42 100 92 100L8 100C4.58, 100 0 96.42 0 92L0 8 C 0 4.42 4.42 0 8 0L50 0Z");
    toolbar.chooseShape("Squircle")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(preview.getAdaptiveIconPathDescription())
      .isEqualTo("M50,0 C10,0 0,10 0,50 0,90 10,100 50,100 90,100 100,90 100,50 100,10 90,0 50,0 Z");
    toolbar.chooseShape("Square")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(preview.getAdaptiveIconPathDescription()).isEqualTo("M50,0L100,0 100,100 0,100 0,0z");
    assertThat(preview.getPixelColor(adaptiveIconTopLeftCorner)).isEqualTo("ff00ff00");
  }

  @Test
  public void themeSelector() throws IOException {
    NlPreviewFixture preview =
      guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
        .getEditor()
        .open("app/src/main/res/mipmap-anydpi-v26/ic_theme_adaptive.xml")
        .getLayoutPreview(true);
    Point adaptiveIconTopLeftCorner = preview.getAdaptiveIconTopLeftCorner();
    NlConfigurationToolbarFixture<NlPreviewFixture> toolbar = preview.getConfigToolbar();
    toolbar.openThemeSelectionDialog()
      .selectsTheme("Holo Light", "android:Theme.Holo.Light")
      .clickOk();
    toolbar.leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(preview.getPixelColor(adaptiveIconTopLeftCorner)).isEqualTo("ffe6e6e6");
    toolbar.openThemeSelectionDialog()
      .selectsTheme("Material Dark", "android:Theme.Material")
      .clickOk();
    toolbar.leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(preview.getPixelColor(adaptiveIconTopLeftCorner)).isEqualTo("ff212121");
  }
}
