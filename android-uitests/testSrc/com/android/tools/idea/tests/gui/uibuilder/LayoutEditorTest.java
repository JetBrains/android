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
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.AssetStudioWizardFixture;
import org.fest.swing.core.MouseButton;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

/**
 * UI tests for the layout editor
 */
@RunWith(GuiTestRunner.class)
public class LayoutEditorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Verifies that Asset Studio features work
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: e5a42dd1-c55c-460e-b8e5-62f715e70f03
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open Android Studio
   *   2. Import SimpleApplication
   *   3. Right click the default app module (or any manually created module) and select New > Vector Asset (Verify 1)
   *   4. Click on "Choose Icon"
   *   5. Select an Icon and click OK
   *   6. Check the check box of “Override default size …” and modify the size to be something like 48 x 24 (Verify 2)
   *   7. Modify the Opacity slider to a small value, like 50. (Verify 3)
   *   8. Check the checkbox of “Enable auto mirroring… " and click Next (Verify 4)
   *   9. Hit “Finish” button
   *   10. In the folder, find the XML file just created, double click it. (Verify 5)
   *   Verify:
   *   1. Vector Asset Studio is displayed. A vector drawable preview is displayed (initially blank) with option
   *   to choose an icon and Resource name
   *   2. The image become scale within the bound accordingly
   *   3. The image become translucent.
   *   4. An XML file is created under /res/drawable. The preview should match the previous dialog.
   *   5. The file content should have some details And if preview pane is enabled, then a similar preview image should show up.
   *   there are android:alpha attributes and android:autoMirrored created.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void androidVectorDrawableTool() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    AssetStudioWizardFixture assetStudioWizardFixture = guiTest.ideFrame()
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app")
      .openFromMenu(AssetStudioWizardFixture::find, "File", "New", "Vector Asset");
    assetStudioWizardFixture.chooseIcon(ideFrameFixture)
      .clickOk();
    assetStudioWizardFixture
      .enableOverrideDefaultSize()
      .setSize(48, 24)
      .setOpacity(50)
      .enableAutoMirror()
      .clickNext()
      .clickFinish();

    String contents = ideFrameFixture.getEditor()
      .open("app/src/main/res/drawable/ic_android_black_24dp.xml")
      .getCurrentFileContents();
    assertThat(contents).contains("android:width=\"48dp\"");
    assertThat(contents).contains("android:height=\"24dp\"");
    assertThat(contents).contains("xmlns:android=\"http://schemas.android.com/apk/res/android\"");
  }
}

