/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.performance;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.CreateResourceFileDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.bleak.UseBleak;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.core.MouseButton;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The test execute a basic scenario of add Android Resource File making use of BLeak - memory leak checker.
 * BLeak repeatedly runs the test and capture memory state of each run.
 * At the end, BLeak outputs result based on memory usage collected from each run.
 */

@RunWith(GuiTestRemoteRunner.class)
@RunIn(TestGroup.PERFORMANCE)
public class AddResourceMemoryUseTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  @UseBleak
  public void addNavGraphResource() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    guiTest.runWithBleak(() -> {
      ideFrameFixture.getProjectView()
        .selectAndroidPane()
        .clickPath(MouseButton.RIGHT_BUTTON, "app")
        .openFromContextualMenu(CreateResourceFileDialogFixture::find, "New", "Android Resource File")
        .setFilename("menu_g")
        .setType("menu")
        .clickOk()
        .getProjectView()
        .selectAndroidPane()
        .deletePath("app","res","menu", "menu_g.xml");
    });
  }

  @Test
  @UseBleak
  public void addLayoutResource() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    guiTest.runWithBleak(() -> {
      ideFrameFixture.getProjectView()
        .selectAndroidPane()
        .clickPath(MouseButton.RIGHT_BUTTON, "app")
        .openFromContextualMenu(CreateResourceFileDialogFixture::find, "New", "Android Resource File")
        .setFilename("layout_g")
        .setType("layout")
        .clickOk()
        .getProjectView()
        .selectAndroidPane()
        .deletePath("app","res","layout", "layout_g.xml");
    });
  }
}
