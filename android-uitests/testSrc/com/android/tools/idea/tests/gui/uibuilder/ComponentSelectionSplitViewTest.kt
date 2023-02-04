/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.uibuilder

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.timing.Wait
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * To verify component selection reflects from design view to xml in split view
 * <p>
 * This is run to qualify releases. Please involve the test team in substantial
 * changes.
 * <p>
 * TT ID: a35f03d0-5763-40f9-a129-5023ad8a21b2
 * <pre>
 *   Test Steps:
 *   1. Launch Android Studio.
 *   2. Create a new project using the Empty Activity template.
 *   3. Under the layout resource directory, open the activity_main.xml file and wait for sync to finish.
 *   4. Click the "Split" mode button from the top-right corner (middle button).
 *   5. In the Palette panel, select Buttons and drag a Button to the surface, so the layout contains a TextView and a Button.
 *   6. On the Text view of the editor, select &lt;TextView /&gt; tag. (Verify 1)
 *   7. On the Text view of the editor, select &lt;Button /&gt; tag. (Verify 2)
 *   8. On the Visual view of the editor, click the TextView. (Verify 3)
 *   9. On the Visual view of the editor, click the Button. (Verify 4)
 *   Verification:
 *   1. The TextView is selected in the Visual view editor.
 *   2. The Button is selected in the Visual view editor.
 *   3. The "TextView" tag gets highlighted in the Text view editor.
 *   4. The  "Button" tag gets highlighted in the Text view editor.
 * </pre>
 *
 */
@RunWith(GuiTestRemoteRunner::class)
class ComponentSelectionSplitViewTest {
  @JvmField
  @Rule
  val guiTest = GuiTestRule().withTimeout(5, TimeUnit.MINUTES)

  @JvmField
  @Rule
  val renderTaskLeakCheckRule = RenderTaskLeakCheckRule()

  @Test
  @Throws(Exception::class)
  fun selectionInSplitModeWorksBothWays() {
    val editorFixture = guiTest
      .importProjectAndWaitForProjectSyncToFinish("SimpleApplication", Wait.seconds(120))
      .editor

    val nlEditorFixture: NlEditorFixture = editorFixture
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
      .layoutEditor
      .waitForSurfaceToLoad()

    assertThat(nlEditorFixture.canInteractWithSurface()).isTrue()
    nlEditorFixture
      .dragComponentToSurface("Buttons", "Button")
      .waitForRenderToFinish()
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    editorFixture.selectEditorTab(EditorFixture.Tab.SPLIT)
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    // The components list is expected to have 3 elements in the following order:
    // * A RelativeLayout containing all the other components
    // * A TextView, which was already present in activity_my.xml
    // * A Button, added in this test
    val components = nlEditorFixture.allComponents
    assertThat(components).hasSize(3)

    // Check the current selection.
    var currentSelection = nlEditorFixture.selection
    assertThat(currentSelection).hasSize(1)
    val button = currentSelection[0]
    // Button should be selected because we have just dragged it to the surface
    assertThat(button.tagName).isEqualTo("Button")
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    // Select the TextView tag in the XML portion of the split editor
    editorFixture.select("(TextView)")
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    currentSelection = nlEditorFixture.selection
    assertThat(currentSelection).hasSize(1)
    // The TextView should now be selected in the design surface
    assertThat(currentSelection[0].tagName).isEqualTo("TextView")

    // Click on the Button in the design surface
    nlEditorFixture.findView("Button", 0).sceneComponent!!.click()
    guiTest.waitForAllBackgroundTasksToBeCompleted()

    // We should then navigate to the line containing the Button tag in the XML
    assertThat(editorFixture.currentLine).contains("<Button")
  }
}