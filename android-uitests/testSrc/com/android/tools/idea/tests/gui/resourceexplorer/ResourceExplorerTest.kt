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
package com.android.tools.idea.tests.gui.resourceexplorer

import com.android.tools.adtui.ui.ClickableLabel
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.ResourceExplorerFixture
import com.android.tools.idea.tests.gui.framework.fixture.ResourcePickerDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.AssetStudioWizardFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture
import com.android.tools.idea.uibuilder.property.assistant.AssistantPopupPanel
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.core.KeyPressInfo
import org.fest.swing.fixture.JButtonFixture
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JPanel

/**
 * UI test to cover functionality of the Resource Explorer. For both the Resource Manager and the Resource Picker.
 */
@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRemoteRunner::class)
class ResourceExplorerTest {
  @Rule
  @JvmField
  val guiTest: GuiTestRule = GuiTestRule()

  @Before
  fun setUp() {
    StudioFlags.NELE_SOURCE_CODE_EDITOR.override(false)
  }

  @After
  fun tearDown() {
    StudioFlags.NELE_SOURCE_CODE_EDITOR.clearOverride()
  }

  /**
   * This test covers several interactions with the IDE and the Resource Explorer:
   *
   * 1. On a resource file, press "ctrl + shift + t" to open and select the resource in the resource manager.
   * 2. Add a Drawable resource through the VectorAsset action.
   * 3. Drag the created Drawable in to the Layout Editor, should create a new ImageView component.
   * 4. Invoke the Resource Picker to set SampleData for the previously created ImageView.
   * 5. Switch the Layout Editor to Text mode, and drag a string resource into the text attribute value of an existing TextView.
   *
   * TODO(b/144576310): Cover multi-module search.
   *  Searching in the search bar should show an option to change module if there are resources in it.
   * TODO(b/144576310): Cover filter usage. Eg: Look for a framework resource by enabling its filter.
   * TODO(b/144576310): Cover usage of the Import dialog (DesignAssetImporter).
   * TODO(b/144576737): Create Bleak test.
   */
  @Test
  fun resourceExplorerUiTest() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("ResourceManagerApp")

    val goToDeclarationKeyInfo = KeyPressInfo.keyCode(KeyEvent.VK_B).apply { modifiers(InputEvent.CTRL_MASK) }
    ide.editor.open("app/src/main/java/com/example/resourcemanagerapp/MainActivity.kt")
      .moveBetween("R.layout.activity", "_main")
      .pressAndReleaseKey(goToDeclarationKeyInfo)

    // 1. On a resource file, press "ctrl + shift + t" to open and select the resource in the resource manager.
    val showInResourceManagerKeyInfo = KeyPressInfo.keyCode(KeyEvent.VK_T).apply { modifiers(InputEvent.CTRL_MASK, InputEvent.SHIFT_MASK) }
    val layoutEditor = ide.editor.getLayoutEditor(false)
    layoutEditor.waitForRenderToFinish().pressAndReleaseKey(showInResourceManagerKeyInfo).findResourceExplorer()

    // 2. Add a Drawable resource through the VectorAsset action.
    layoutEditor.findResourceExplorer()
      .selectTab("Drawable")
      .clickAddButton()

    ide.openFromContextualMenu({ AssetStudioWizardFixture.find(it) }, arrayOf("Vector Asset"))
      .switchToClipArt()
      .chooseIcon()
      .filterByNameAndSelect("android")
      .clickOk()
      .clickNext()
      .clickFinish()

    // 3. Drag the created Drawable in to the Layout Editor, should create a new ImageView component.
    layoutEditor.findResourceExplorer().dragResourceToLayoutEditor("ic_baseline_android_24")
    ide.closeResourceManager()

    // 4. Invoke the Resource Picker to set SampleData for the previously created ImageView.
    layoutEditor.findView("ImageView", 0).rightClick()
    // Open the 'Set Sample Data' panel, then click 'Browse' to open the Resource Picker.
    ide.invokeMenuPath("Set Sample Data")
    val finder = ide.robot().finder()
    finder.findByType(AssistantPopupPanel::class.java).also { assistantPopupPanel ->
      JButtonFixture(ide.robot(), finder.findByType(assistantPopupPanel.content as JPanel, ClickableLabel::class.java)).click()
    }

    ResourcePickerDialogFixture.find(ide.robot()).run {
      resourceExplorer
        .selectTab("Drawable")
        .selectResource("avatars")
      clickOk()
    }

    // 5. Switch the Layout Editor to Text mode, and drag a string resource into the text attribute value of an existing TextView.
    ide.editor.selectEditorTab(EditorFixture.Tab.EDITOR).ideFrame.openResourceManager()
    layoutEditor.findResourceExplorer()
      .selectTab("String")
      .dragResourceToXmlEditor("app_name", "android:text=\"Hello", " World!\"")
    val textViewContents = ide.editor.currentFileContents.substringAfter("<TextView").substringBefore("/>")
    require(textViewContents.contains("android:text=\"@string/app_name\""))
  }
}

private fun NlEditorFixture.findResourceExplorer(): ResourceExplorerFixture = ResourceExplorerFixture.find(robot())