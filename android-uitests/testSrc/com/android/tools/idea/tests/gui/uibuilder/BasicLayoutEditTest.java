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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.event.KeyEvent;
import java.util.List;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

@RunWith(GuiTestRemoteRunner.class)
public class BasicLayoutEditTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  /**
   * Verifies addition of components to designer screen
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: 78baed4b-32be-4d72-b558-ba9f6c727334
   * <pre>
   *   1. Create a new project
   *   2. Open the layout xml file
   *   3. Switch to design view
   *   4. Drag and drop components TextView, Button
   *   5. Select the TextView added and Open Attributes panel
   *   6. Change the text attribute to `@string/app_name` (Verify 2) (Verify 3)
   *   7. Switch back to Text view (Verify 1)
   *   Verification:
   *   1. The added component shows up in the xml
   *   2. The text render correctly with the app name
   *   3. Make sure TextView xml tag has attribute "android:text:="@string/app_name"
   * </pre>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void basicLayoutEdit() throws Exception {
    NlEditorFixture editorFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleApplication", Wait.seconds(120))
                                           .getEditor()
                                           .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
                                           .getLayoutEditor()
                                           .waitForSurfaceToLoad();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    assertThat(editorFixture.canInteractWithSurface()).isTrue();

    editorFixture.dragComponentToSurface("Buttons", "Button")
      .waitForRenderToFinish();

    editorFixture
      .dragComponentToSurface("Common", "TextView")
      .waitForRenderToFinish()
      .getAttributesPanel()
      .waitForId("textView")
      .findSectionByName("Common Attributes")
      .findEditorOf("text")
      .getTextField()
      .selectAll()
      .enterText("@string/app_name");

    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);

    List<NlComponentFixture>  components = editorFixture.getAllComponents();

    assertEquals("Hello world!", components.get(components.size() - 3).getText());



    String layoutFileContents = guiTest.ideFrame()
                                       .getEditor()
                                       .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
                                       .getCurrentFileContents();
    assertThat(layoutFileContents).contains("<TextView");
    assertThat(layoutFileContents).contains("android:text=\"@string/app_name\"");
    assertThat(layoutFileContents).contains("<Button");
  }
}
