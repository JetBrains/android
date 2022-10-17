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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.AssetStudioWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.fest.swing.core.MouseButton;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AndroidVectorDrawableToolTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

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
   *   6. Modify the width to be something like 48 (Verify 2)
   *   7. Modify the Opacity slider to a small value, like 50. (Verify 3)
   *   8. Check the checkbox of “Enable auto mirroring… " and click Next (Verify 4)
   *   9. Hit “Finish” button
   *   10. In the folder, find the XML file just created, double click it (Verify 5)
   *   Verify:
   *   1. Vector Asset Studio is displayed. A vector drawable preview is displayed (initially blank) with option
   *   to choose an icon and Resource name
   *   2. The image become scale within the bound accordingly
   *   3. The image become translucent
   *   4. An XML file is created under /res/drawable. The preview should match the previous dialog
   *   5. The file content should have some details And if preview pane is enabled, then a similar preview image should show up.
   *   there are android:alpha attributes and android:autoMirrored created
   *   </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void androidVectorDrawableTool() throws Exception {
    guiTest.importSimpleApplication()
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app")
      .openFromMenu(AssetStudioWizardFixture::find, "File", "New", "Vector Asset")
      .chooseIcon()
      .filterByNameAndSelect("360")
      .clickOk()
      .setWidth(48)
      .setOpacity(50)
      .enableAutoMirror()
      .clickNext()
      .selectResFolder("main")
      .clickFinish();

    guiTest.robot().waitForIdle();

    String contents = guiTest.getProjectFileText("app/src/main/res/drawable/baseline_360_24.xml");

    assertThat(contents).contains("android:width=\"48dp\"");
    assertThat(contents).contains("android:height=\"48dp\"");
    assertThat(contents).contains("android:autoMirrored=\"true\"");
    assertThat(contents).contains("android:alpha=\"0.5\"");
    assertThat(contents).contains("xmlns:android=\"http://schemas.android.com/apk/res/android\"");
  }
}
