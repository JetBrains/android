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
import com.android.tools.idea.tests.gui.framework.fixture.FindPopupPanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.bleak.UseBleak;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.fest.swing.fixture.DialogFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Checks for leaks in the Find in Path popup dialog.
 */
@RunWith(GuiTestRemoteRunner.class)
@RunIn(TestGroup.PERFORMANCE)
public class FindInPathMemoryLeakTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(6, TimeUnit.MINUTES);

  @Test
  @UseBleak
  public void openAndClosePopup() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importSimpleApplication();
    guiTest.runWithBleak(() -> {
      ideFrame.focus()
        .invokeMenuPath("Edit", "Find", "Find in Files...");
      FindPopupPanelFixture.find(ideFrame)
        .dialog()
        .close();
    });
  }
}
