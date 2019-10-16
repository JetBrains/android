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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlConfigurationToolbarFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.Point;
import java.io.IOException;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AdaptiveIconTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  @Test
  public void densitySelector() throws IOException {
    NlEditorFixture editor =
      guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
        .getEditor()
        .open("app/src/main/res/mipmap-anydpi-v26/ic_launcher_adaptive.xml")
        .getLayoutEditor(true)
        .waitForRenderToFinish();
    Point adaptiveIconTopLeftCorner = editor.getAdaptiveIconTopLeftCorner();
    NlConfigurationToolbarFixture<NlEditorFixture> toolbar = editor.getConfigToolbar();
    toolbar.requireDensity("xxxhdpi")
      .chooseDensity("mdpi")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    // noinspection SpellCheckingInspection
    assertThat(editor.getPixelColor(adaptiveIconTopLeftCorner)).isEqualTo("ffff0000");
    toolbar.chooseDensity("hdpi")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(editor.getPixelColor(adaptiveIconTopLeftCorner)).isEqualTo("ff0000ff");
    toolbar.chooseDensity("xhdpi")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(editor.getPixelColor(adaptiveIconTopLeftCorner)).isEqualTo("ff0000ff");
    toolbar.chooseDensity("xxhdpi")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(editor.getPixelColor(adaptiveIconTopLeftCorner)).isEqualTo("ff00ff00");
    toolbar.chooseDensity("xxxhdpi")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(editor.getPixelColor(adaptiveIconTopLeftCorner)).isEqualTo("ff00ff00");
  }

  @Test
  public void shapeSelector() throws IOException {
    NlEditorFixture editor =
      guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
        .getEditor()
        .open("app/src/main/res/mipmap-anydpi-v26/ic_launcher_adaptive.xml")
        .getLayoutEditor(true)
        .waitForRenderToFinish();
    Point adaptiveIconTopLeftCorner = editor.getAdaptiveIconTopLeftCorner();
    NlConfigurationToolbarFixture<NlEditorFixture> toolbar = editor.getConfigToolbar();
    toolbar.chooseShape("Circle")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(editor.getAdaptiveIconPathDescription())
      .isEqualTo("M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 22.4 22.4 0 50 0Z");
    Wait.seconds(5).expecting("the icon to be updated")
      .until(() -> "fff5f5f5".equals(editor.getPixelColor(adaptiveIconTopLeftCorner)));
    toolbar.chooseShape("Rounded Corners")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(editor.getAdaptiveIconPathDescription()).isEqualTo(
      "M50,0L92,0C96.42,0 100,4.58 100 8L100,92C100, 96.42 96.42 100 92 100L8 100C4.58, 100 0 96.42 0 92L0 8 C 0 4.42 4.42 0 8 0L50 0Z");
    toolbar.chooseShape("Squircle")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(editor.getAdaptiveIconPathDescription())
      .isEqualTo("M50,0 C10,0 0,10 0,50 0,90 10,100 50,100 90,100 100,90 100,50 100,10 90,0 50,0 Z");
    toolbar.chooseShape("Square")
      .leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(editor.getAdaptiveIconPathDescription()).isEqualTo("M50,0L100,0 100,100 0,100 0,0z");
    Wait.seconds(5).expecting("the icon to be updated")
      .until(() -> "ff00ff00".equals(editor.getPixelColor(adaptiveIconTopLeftCorner)));
  }

  @Test
  public void themeSelector() throws IOException {
    NlEditorFixture editor =
      guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
        .getEditor()
        .open("app/src/main/res/mipmap-anydpi-v26/ic_theme_adaptive.xml")
        .getLayoutEditor(true)
        .waitForRenderToFinish();
    NlConfigurationToolbarFixture<NlEditorFixture> toolbar = editor.getConfigToolbar();
    toolbar.openThemeSelectionDialog()
      .selectTheme("Holo Light", "android:Theme.Holo.Light")
      .clickOk();
    toolbar.leaveConfigToolbar()
      .waitForRenderToFinish();
    Point adaptiveIconTopLeftCorner = editor.getAdaptiveIconTopLeftCorner();
    assertThat(editor.getPixelColor(adaptiveIconTopLeftCorner)).isEqualTo("ffe6e6e6");
    toolbar.openThemeSelectionDialog()
      .selectTheme("Material Dark", "android:Theme.Material")
      .clickOk();
    toolbar.leaveConfigToolbar()
      .waitForRenderToFinish();
    assertThat(editor.getPixelColor(adaptiveIconTopLeftCorner)).isEqualTo("ff212121");
  }
}
