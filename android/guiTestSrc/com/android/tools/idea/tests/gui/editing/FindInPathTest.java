/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.editing;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.FindDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FindToolWindowFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class FindInPathTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testResultsOnlyInGeneratedCode() throws Exception {
    guiTest.importSimpleApplication();

    FindDialogFixture findDialog = guiTest.ideFrame().invokeFindInPathDialog();
    findDialog.setTextToFind("ActionBarDivider")
              .clickFind();

    guiTest.waitForBackgroundTasks();

    FindToolWindowFixture findToolWindow = guiTest.ideFrame().getFindToolWindow();
    FindToolWindowFixture.ContentFixture selectedContext = findToolWindow.getSelectedContext();

    selectedContext.findUsagesInGeneratedCodeGroup();
  }

  @Test
  public void testResultsInBothProductionAndGeneratedCode() throws Exception {
    guiTest.importSimpleApplication();

    FindDialogFixture findDialog = guiTest.ideFrame().invokeFindInPathDialog();
    findDialog.setTextToFind("DarkActionBar")
              .clickFind();

    guiTest.waitForBackgroundTasks();

    FindToolWindowFixture findToolWindow = guiTest.ideFrame().getFindToolWindow();
    FindToolWindowFixture.ContentFixture selectedContext = findToolWindow.getSelectedContext();

    selectedContext.findUsagesInGeneratedCodeGroup();
    selectedContext.findUsageGroup("Code usages");
  }
}
