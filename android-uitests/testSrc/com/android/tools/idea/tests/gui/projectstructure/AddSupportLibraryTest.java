/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.projectstructure;

import static org.junit.Assert.assertTrue;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.AddProjectDependencyDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.SplitEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.SplitEditorFixtureKt;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.Dimension;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AddSupportLibraryTest {
  @Rule
  public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  private NlEditorFixture nlEditor;

  /**
   * To verify studio adds latest support library while Drag'n Drop layouts
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial
   * changes.
   * <p>
   * TT ID: c4fafea8-9560-4c40-92c1-58b72b2caaa0
   * <p>
   *
   * <pre>
   * Procedure:
   * 1. Create a new project or open a project with minSdk 28
   * 2. Drag and Drop RecyclerView or FloatingActionButton. (Verify)
   *
   * Verification:
   * 1. Dependency should be added to build.gradle with latest version from maven.
   * 2. For RecyclerView -- implementation 'androidx.recyclerview:recyclerview:1.0.0-alpha3'
   * 3. For FloatingActionButton -- implementation 'com.google.android.material:material:1.0.0-alpha3'
   * </pre>
   * <p>
   */
  @Test
  public void testNonVersionCatalogProject () throws Exception {
    File projectDir = guiTest.setUpProject("ConvertFrom9Patch");
    guiTest.openProjectAndWaitForProjectSyncToFinish(projectDir);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor();

    ideFrame.clearNotificationsPresentOnIdeFrame();

    editor.open("app/src/main/res/layout/activity_main.xml", EditorFixture.Tab.DESIGN);

    nlEditor = editor.getLayoutEditor();
    assertTrue(nlEditor.canInteractWithSurface());

    nlEditor.dragComponentToSurface("Google", "AdView");

    AddProjectDependencyDialogFixture.find(ideFrame)
      .clickOk();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    guiTest.waitForAllBackgroundTasksToBeCompleted();
    String gradleContents = editor.open("app/build.gradle")
      .getCurrentFileContents();
    assertTrue(gradleContents.contains("com.google.android.gms:play-services-ads"));
  }

  /**
   * To verify studio adds latest support library while Drag'n Drop layouts
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial
   * changes.
   * <p>
   * TT ID: c4fafea8-9560-4c40-92c1-58b72b2caaa0
   * <p>
   *
   * <pre>
   * Procedure:
   * 1. Create a new project or open a project with version catalog.
   * 2. Drag and Drop RecyclerView or FloatingActionButton. (Verify)
   *
   * Verification:
   * 1. Dependency should be added to build.gradle with latest version from maven.
   * 2. For RecyclerView -- implementation 'androidx.recyclerview:recyclerview:1.0.0-alpha3'
   * 3. For FloatingActionButton -- implementation 'com.google.android.material:material:1.0.0-alpha3'
   * </pre>
   * <p>
   */
  @Test
  public void testUsingVersionCatalogProject() throws Exception {
    File projectDir = guiTest.setUpProject("VersionCatalogProject");
    guiTest.openProjectAndWaitForProjectSyncToFinish(projectDir);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor();

    ideFrame.clearNotificationsPresentOnIdeFrame();

    editor.open("app/src/main/res/layout/activity_main.xml");
    SplitEditorFixture splitEditorFixture = SplitEditorFixtureKt.getSplitEditorFixture(editor);
    splitEditorFixture.setSplitMode();
    editor.replaceText("<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                       "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
                       "    android:layout_width=\"match_parent\"\n" +
                       "    android:layout_height=\"match_parent\"\n" +
                       "    android:paddingLeft=\"@dimen/activity_horizontal_margin\"\n" +
                       "    android:paddingRight=\"@dimen/activity_horizontal_margin\"\n" +
                       "    android:paddingTop=\"@dimen/activity_vertical_margin\"\n" +
                       "    android:paddingBottom=\"@dimen/activity_vertical_margin\"\n" +
                       "    tools:context=\".MyActivity\">\n" +
                       "\n" +
                       "</RelativeLayout>");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    editor.switchToTab("Design");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    NlEditorFixture nlEditor = editor.getLayoutEditor()
        .waitForRenderToFinish(Wait.seconds(30));
    assertTrue(nlEditor.canInteractWithSurface());
    nlEditor.getPalette();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    nlEditor.dragComponentToSurface("Google", "MapView")
        .waitForRenderToFinish();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    AddProjectDependencyDialogFixture.find(ideFrame)
      .clickOk();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    String gradleContents = editor.open("app/build.gradle")
      .getCurrentFileContents();
    assertTrue(gradleContents.contains("libs.play.services.maps"));

    String versionCatalogContents = editor.open("gradle/libs.versions.toml")
      .getCurrentFileContents();
    assertTrue(versionCatalogContents.contains("com.google.android.gms"));
    assertTrue(versionCatalogContents.contains("play-services-maps"));
  }
}