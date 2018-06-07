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
import com.android.tools.idea.tests.gui.framework.fixture.RefactorToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RefactoringDialogFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRunner.class)
public class UnusedResourceEvaluationTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  /**
   * Verifies no resources are removed from NoUnusedResourceApp since all
   * resources are used.
   *
   * <p>TT ID: 6ae2a813-6430-452c-81ee-7fa0a282fa7d
   *
   * <pre>
   *   Test steps:
   *   1. Import the UnusedResourceApp
   *   2. Go through the menu to select the "Remove unused resources" action
   *   3. Wait for the refactor window to show up.
   *   Verify:
   *   1. Check that the refactor window show no resources to be removed.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE) // b/70635124
  public void removeUnusedResources() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("NoUnusedResourceApp");
    ideFrame.invokeMenuPath("Refactor", "Remove Unused Resources...");

    RefactoringDialogFixture removeUnusedRes = RefactoringDialogFixture.find(ideFrame.robot(), "Remove Unused Resources");
    removeUnusedRes.getPreviewButton()
      .click();

    RefactorToolWindowFixture refactoringWindow = new RefactorToolWindowFixture(ideFrame);
    org.junit.Assert.assertTrue(refactoringWindow.isRefactorListEmpty());
  }
}
