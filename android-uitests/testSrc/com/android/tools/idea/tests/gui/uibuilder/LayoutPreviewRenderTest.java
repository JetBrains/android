/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.NlPreviewFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(GuiTestRemoteRunner.class)
public class LayoutPreviewRenderTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * To verify that the layout preview renders appropriately with different themes and API selections
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: d45e0fa5-82d5-4d9a-9046-0437210741f0
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open layout.xml design view for the MainActivity
   *   2. Select the rendering device and switch to another device, say N5 and repeat with N6 (Verify 1)
   *   3. Click on the Orientation and repeat (Verify 2)
   *   4. Select the Android version option and choose older API levels one by one (Verify 3)
   *   5. Select the Theme option and choose a different theme to render the preview (Verify 4)
   *   6. Select the activity option and choose a different activity (Verify 5)
   *   Verify:
   *   1. Preview render device changes to the newly selected device.
   *   2. The preview layout orientation switches to landscape and then back to Portrait.
   *   3. Preview layout renders fine on compatible API levels.
   *   4. The selected theme is applied on the preview layout.
   *   5. Preview layout is rendered for the selected activity.
   *   </pre>
   */
  @RunIn(TestGroup.QA_BAZEL)
  @Test
  public void layoutPreviewRendering() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutLocalTest");

    EditorFixture editorFixture = ideFrameFixture.getEditor()
      .open("app/src/main/res/layout/layout2.xml", EditorFixture.Tab.DESIGN);

    NlPreviewFixture preview = editorFixture
      .getLayoutPreview(true)
      .waitForRenderToFinish();

    preview.getConfigToolbar()
      .chooseDevice("Nexus 5")
      .requireDevice("Nexus 5")
      .chooseDevice("Nexus 6")
      .requireDevice("Nexus 6");

    preview.getConfigToolbar()
      .setOrientationAsLandscape()
      .leaveConfigToolbar()
      .waitForRenderToFinish()
      .getConfigToolbar()
      .requireOrientation("Landscape");

    preview.getConfigToolbar()
      .setOrientationAsPortrait()
      .leaveConfigToolbar()
      .waitForRenderToFinish()
      .getConfigToolbar()
      .requireOrientation("Portrait");

    preview.getConfigToolbar()
      .chooseApiLevel("23")
      .requireApiLevel("23")
      .chooseApiLevel("24")
      .requireApiLevel("24")
      .chooseApiLevel("25")
      .requireApiLevel("25");

    preview.getConfigToolbar()
      .openThemeSelectionDialog()
      .selectTheme("Material Light", "android:Theme.Material.Light")
      .clickOk();
    preview.getConfigToolbar()
      .requireTheme("Light");
    preview.getConfigToolbar()
      .openThemeSelectionDialog()
      .selectTheme("Material Dark", "android:Theme.Material")
      .clickOk();
    preview.getConfigToolbar()
      .requireTheme("Material");

    editorFixture = ideFrameFixture.getEditor()
      .open("app/src/main/res/layout/layout1.xml", EditorFixture.Tab.DESIGN);

    preview = editorFixture
      .getLayoutPreview(true)
      .waitForRenderToFinish();

    preview.getConfigToolbar()
      .requireDevice("Nexus 6")
      .requireOrientation("Portrait")
      .requireApiLevel("25")
      .requireTheme("AppTheme");
  }
}
