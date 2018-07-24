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

import com.android.SdkConstants;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.AssetStudioWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.core.MouseButton;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class ImageAssetGradleTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * Verifies that vector drawable project can be deployed successfully
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 28407c0e-1415-4470-90f3-75cf9d330d03
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleLocalApplication
   *   2. Create a vector drawable under directory /res/drawable and name it “local_library.xml”
   *   3. Modify the existing main content view, then add one button, set its background as the Vector Drawable
   *   <button android:background="@drawable/local_library" android:layout_width="wrap_content" android:layout_height="wrap_content"/>
   *   Verify:
   *   Make sure the “preview” pane displays the layout with the drawable correctly
   *   </pre>
   */
  @RunIn(TestGroup.QA_BAZEL)
  @Test
  public void imageAssetGradleTest() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleLocalApplication();
    ideFrameFixture
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app")
      .openFromMenu(AssetStudioWizardFixture::find, "File", "New", "Vector Asset")
      .setName("local_library")
      .clickNext()
      .clickFinish()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(true)
      .waitForRenderToFinish()
      .dragComponentToSurface("Buttons", "Button");

    NlComponentFixture button = ideFrameFixture.getEditor()
      .selectEditorTab(EditorFixture.Tab.EDITOR)
      .moveBetween("<Button", "")
      .enterText("\nandroid:background=\"@drawable/local_library\"")
      .getLayoutEditor(true)
      .waitForRenderToFinish()
      .findView("Button", 0);
    assertThat(button.getComponent().getAttribute(SdkConstants.ANDROID_URI, "background"))
      .isEqualTo("@drawable/local_library");
  }
}
