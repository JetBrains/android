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

import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.FindDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.FindToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import org.junit.Test;

import static com.android.tools.idea.tests.gui.framework.TestGroup.PROJECT_SUPPORT;

@BelongsToTestGroups({PROJECT_SUPPORT})
public class FindInPathTest extends GuiTestCase {

  @Test @IdeGuiTest
  public void testResultsOnlyInGeneratedCode() throws Exception {
    IdeFrameFixture projectFrame = importSimpleApplication();

    FindDialogFixture findDialog = projectFrame.invokeFindInPathDialog();
    findDialog.setTextToFind("ActionBarDivider")
              .clickFind();

    projectFrame.waitForBackgroundTasksToFinish();

    FindToolWindowFixture findToolWindow = projectFrame.getFindToolWindow();
    FindToolWindowFixture.ContentFixture selectedContext = findToolWindow.getSelectedContext();

    selectedContext.findUsagesInGeneratedCodeGroup();
  }

  @Test @IdeGuiTest
  public void testResultsInBothProductionAndGeneratedCode() throws Exception {
    IdeFrameFixture projectFrame = importSimpleApplication();

    FindDialogFixture findDialog = projectFrame.invokeFindInPathDialog();
    findDialog.setTextToFind("DarkActionBar")
              .clickFind();

    projectFrame.waitForBackgroundTasksToFinish();

    FindToolWindowFixture findToolWindow = projectFrame.getFindToolWindow();
    FindToolWindowFixture.ContentFixture selectedContext = findToolWindow.getSelectedContext();

    selectedContext.findUsagesInGeneratedCodeGroup();
    selectedContext.findUsageGroup("Code usages");
  }
}
