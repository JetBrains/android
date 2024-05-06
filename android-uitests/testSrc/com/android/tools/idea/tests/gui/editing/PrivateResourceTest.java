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
package com.android.tools.idea.tests.gui.editing;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.AddLibraryDependencyDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.DependenciesPerspectiveConfigurableFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.DependenciesPerspectiveConfigurableFixtureKt;
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.ProjectStructureDialogFixture;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import org.fest.swing.core.KeyPressInfo;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class PrivateResourceTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  /**
   * Verifies that private resources from libraries are not suggested to the
   * layout editor.
   *
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   *
   * <p>TT ID: 749615f9-4a70-4d97-a9cf-35e9a2af2452
   *
   * <pre>
   *   Test:
   *   1. Import SimpleApplication project.
   *   2. Open the module settings for the app module.
   *   3. Add in the design library as a dependency for the module.
   *   4. Close the module settings window.
   *   5. Open the activity_my.xml layout file.
   *   6. Set the lone TextView's android:text value to "@string/" (Verify 1)
   *   7. Invoke code completion and wait for the autocompletion window to appear
   *   8. Set the lone TextView's android:text value to
   *      "@string/abc_action_bar_home_description". (Verify 2)
   *   Verify:
   *   1. The autocompletion window does not contain references to internal
   *      resources from the design library.
   *   2. There should be a Lint error stating that the layout file makes a reference
   *      to an internal resources from the design library.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void verifyNoPrivateResourcesSuggested() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("PrivateResource");

    ideFrame.invokeMenuPath("File", "Project Structure...");

    ProjectStructureDialogFixture dialogFixture = ProjectStructureDialogFixture.Companion.find(ideFrame);
    DependenciesPerspectiveConfigurableFixture dependenciesFixture =
      DependenciesPerspectiveConfigurableFixtureKt.selectDependenciesConfigurable(dialogFixture);
    dependenciesFixture.findModuleSelector().selectModule("app");
    AddLibraryDependencyDialogFixture addLibraryDependencyFixture = dependenciesFixture.findDependenciesPanel().clickAddLibraryDependency();
    addLibraryDependencyFixture.findSearchQueryTextBox().setText("com.android.support:design");
    addLibraryDependencyFixture.findSearchButton().click();
    addLibraryDependencyFixture.findVersionsView(true); // Wait for search to complete.
    addLibraryDependencyFixture.clickOk();
    dialogFixture.clickOk();

    EditorFixture editor = ideFrame.getEditor();
    editor.open("app/src/main/res/layout/activity_main.xml", EditorFixture.Tab.EDITOR, Wait.seconds(30));

    guiTest.waitForBackgroundTasks();

    String[] autoCompleteSuggestions = editor.waitUntilErrorAnalysisFinishes()
      // I think the collapsing of string references messes with the select. Hence this moveBetween.
      .moveBetween("android:text=\"", "@string/app_name\"")
      .select("(@string/app_name)")
      .pressAndReleaseKey(KeyPressInfo.keyCode(KeyEvent.VK_BACK_SPACE))
      .enterText("@string/")
      .getAutoCompleteWindow()
      .contents();

    // Since the IDE shouldn't show private resource identifiers, the number of
    // suggested string resources should be small. At the time of writing, this
    // PrivateResource project has 6 suggestions in the autocomplete list
    assertThat(autoCompleteSuggestions).hasLength(6);
    assertThat(autoCompleteSuggestions).asList().contains("LookupElementBuilder: string=@string/app_name; handler=null");

    editor.enterText("abc_action_bar_home_description");

    Collection<String> lintWarnings = editor.waitForCodeAnalysisHighlightCount(HighlightSeverity.WARNING, 1)
      .getHighlights(HighlightSeverity.WARNING);
    assertThat(lintWarnings).contains("The resource `@string/abc_action_bar_home_description` is marked as private in com.android.support:design:28.0.0");
  }
}
