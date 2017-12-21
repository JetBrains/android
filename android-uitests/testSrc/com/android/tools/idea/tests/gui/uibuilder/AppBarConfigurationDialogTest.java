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
import com.android.tools.idea.tests.gui.framework.fixture.AppBarConfigurationDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

/**
 * UI tests for {@link com.android.tools.idea.uibuilder.handlers.ui.AppBarConfigurationDialog}
 */
@RunWith(GuiTestRunner.class)
public class AppBarConfigurationDialogTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testDependencyDialog() throws Exception {
    EditorFixture editor = guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);

    editor.getLayoutEditor(true)
      .dragComponentToSurface("Containers", "AppBarLayout");

    MessagesFixture dependencyDialog = MessagesFixture.findByTitle(guiTest.robot(), "Add Project Dependency");
    dependencyDialog.clickCancel();

    String layoutFileContents = editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .getCurrentFileContents();
    assertThat(layoutFileContents).doesNotContain("<android.support.design.widget.AppBarLayout");
    String gradleContents = editor.open("app/build.gradle")
      .getCurrentFileContents();
    assertThat(gradleContents).doesNotContain("com.android.support:design:");

    // Now repeat the same process but ADD the dependency.
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(true)
      .dragComponentToSurface("Containers", "AppBarLayout");
    dependencyDialog = MessagesFixture.findByTitle(guiTest.robot(), "Add Project Dependency");
    dependencyDialog.clickOk();

    AppBarConfigurationDialogFixture configDialog = AppBarConfigurationDialogFixture.find(guiTest.robot());
    configDialog.waitForPreview();
    configDialog.clickCancel();

    layoutFileContents = editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .getCurrentFileContents();
    // The component was not added (we canceled the AppBarConfigurationDialog
    assertThat(layoutFileContents).doesNotContain("<android.support.design.widget.AppBarLayout");
    gradleContents = editor.open("app/build.gradle")
      .getCurrentFileContents();
    assertThat(gradleContents).contains("com.android.support:design:");
  }

  @Test
  public void testAddComponent() throws Exception {
    EditorFixture editor = guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);

    editor.getLayoutEditor(true)
      .dragComponentToSurface("Containers", "AppBarLayout");

    MessagesFixture dependencyDialog = MessagesFixture.findByTitle(guiTest.robot(), "Add Project Dependency");
    dependencyDialog.clickOk();

    AppBarConfigurationDialogFixture configDialog = AppBarConfigurationDialogFixture.find(guiTest.robot());
    configDialog.waitForPreview();
    configDialog.clickOk();

    String layoutFileContents = editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .getCurrentFileContents();
    assertThat(layoutFileContents).contains("<android.support.design.widget.AppBarLayout");
    String gradleContents = editor.open("app/build.gradle")
      .getCurrentFileContents();
    assertThat(gradleContents).contains("com.android.support:design:");
  }

  @Test
  public void testSyncFailsAfterAddingNonExistentDependency() throws Exception {
    EditorFixture editor = guiTest.importSimpleLocalApplication()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);

    // Sync should fail since we've added a dependency that doesn't exist.
    editor.open("app/build.gradle", EditorFixture.Tab.EDITOR)
      .select("dependencies \\{()")
      .enterText("\ncompile 'something:not:exists'");

    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(true)
      .dragComponentToSurface("Containers", "AppBarLayout");

    MessagesFixture dependencyDialog = MessagesFixture.findByTitle(guiTest.robot(), "Add Project Dependency");
    dependencyDialog.clickOk();

    guiTest.ideFrame().waitForGradleProjectSyncToFail();

    AppBarConfigurationDialogFixture configDialog = AppBarConfigurationDialogFixture.find(guiTest.robot());
    configDialog.waitForSyncFailedPreviewMessage();
    configDialog.clickCancel();
  }
}
