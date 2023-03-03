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
import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.CreateResourceValueDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.ResourceExplorerFixture
import com.android.tools.idea.tests.gui.framework.fixture.ResourcePickerDialogFixture
import com.android.tools.idea.tests.gui.framework.fixture.assetstudio.AssetStudioWizardFixture
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.properties.PTableFixture
import com.android.tools.idea.tests.gui.framework.fixture.properties.SectionFixture
import com.android.tools.idea.uibuilder.assistant.AssistantPopupPanel
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.property.panel.impl.table.EditorPanel
import com.android.tools.property.ptable.impl.PTableImpl
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.core.KeyPressInfo
import org.fest.swing.fixture.JButtonFixture
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit
import javax.swing.JPanel

/**
 * UI test to cover functionality of the Resource Explorer. For both the Resource Manager and the Resource Picker.
 */
@RunWith(GuiTestRemoteRunner::class)
class ResourceExplorerTest {
  @Rule
  @JvmField
  val guiTest: GuiTestRule = GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

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
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 578e2691-b25d-4241-8717-3165c19b3c70
   * <p>
   *
   * 1. On a resource file, press "ctrl + shift + t" to open and select the resource in the resource manager.
   * 2. Add a Drawable resource through the VectorAsset action.
   * 3. Drag the created Drawable in to the Layout Editor, should create a new ImageView component.
   * 4. Invoke the Resource Picker to set SampleData for the previously created ImageView.
   * 5. Switch the Layout Editor to Text mode, and drag a string resource into the text attribute value of an existing TextView.
   *
   *  Searching in the search bar should show an option to change module if there are resources in it.
   * TODO(b/144576310): Cover filter usage. Eg: Look for a framework resource by enabling its filter.
   * TODO(b/144576310): Cover usage of the Import dialog (DesignAssetImporter).
   * TODO(b/144576737): Create Bleak test.
   */
  @Test
  fun resourceExplorerUiTest() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("ResourceManagerApp")
    val layoutEditor = ide.editor.open("app/src/main/res/layout/activity_main.xml", EditorFixture.Tab.DESIGN).layoutEditor

    // 1. On a resource file, press "ctrl + shift + t" to open and select the resource in the resource manager.
    val showInResourceManagerKeyInfo =
      if(SystemInfo.isMac) {
        KeyPressInfo.keyCode(KeyEvent.VK_T).apply { modifiers(InputEvent.META_MASK, InputEvent.SHIFT_MASK) }
        } else {
          KeyPressInfo.keyCode(KeyEvent.VK_T).apply { modifiers(InputEvent.CTRL_MASK, InputEvent.SHIFT_MASK) }
      }
    layoutEditor.waitForRenderToFinish().pressAndReleaseKey(showInResourceManagerKeyInfo).findResourceExplorer()

    // 2. Add a Drawable resource through the VectorAsset action.
    layoutEditor.findResourceExplorer()
      .selectTab("Drawable")
      .clickAddButton()

    guiTest.robot().waitForIdle()
    ide.openFromContextualMenu({ AssetStudioWizardFixture.find(it) }, "Vector Asset")
      .chooseIcon()
      .filterByNameAndSelect("android")
      .clickOk()
      .clickNext()
      .selectResFolder("main")
      .clickFinish()

    // 3. Drag the created Drawable in to the Layout Editor, should create a new ImageView component.
    layoutEditor.findResourceExplorer().dragResourceToLayoutEditor("baseline_android_24")
    ide.closeResourceManager()

    // 4. Invoke the Resource Picker to set SampleData for the previously created ImageView.
    layoutEditor.findView("ImageView", 0).sceneComponent!!.rightClick()
    // Open the 'Set Sample Data' panel, then click 'Browse' to open the Resource Picker.
    ide.invokeContextualMenuPath("Set Sample Data")
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

    // 6. Switch to Design mode, add and select a new resource through the ResourcePicker
    ide.closeResourceManager().editor.selectEditorTab(EditorFixture.Tab.DESIGN)
    layoutEditor.findView("TextView", 0).sceneComponent!!.rightClick()
    layoutEditor.attributesPanel.waitForId("text").findSectionByName("Declared Attributes")!!.apply {
      title!!.expand()
      invokeButtonInAttribute("text")
    }

    ResourcePickerDialogFixture.find(guiTest.robot()).apply {
      val previousCount = resourceExplorer.resourcesCount
      resourceExplorer.clickAddButton()
      ide.openFromContextualMenu( { CreateResourceValueDialogFixture.find(guiTest.robot()) }, "String Value" )
        .setResourceName("new_text")
        .setResourceValue("my value")
        .clickOk()

      waitForCondition(5L, TimeUnit.SECONDS) {
        // Wait for new resource in picker
        resourceExplorer.resourcesCount > previousCount
      }
      // Select resource
      clickOk()
    }

    ide.editor.selectEditorTab(EditorFixture.Tab.EDITOR)
    val updatedTextView = ide.editor.currentFileContents.substringAfter("<TextView").substringBefore("/>")
    require(updatedTextView.contains("android:text=\"@string/new_text\""))
  }
}

private fun NlEditorFixture.findResourceExplorer(): ResourceExplorerFixture = ResourceExplorerFixture.find(robot())

private fun SectionFixture.invokeButtonInAttribute(attributeName: String) {
  val table = components.firstIsInstanceOrNull<PTableFixture>()!!
  val tableComp = table.target() as PTableImpl
  val row = table.findRowOf(attributeName)
  val item = table.item(row) as NlPropertyItem
  val component = runInEdtAndGet { (tableComp.getCellEditor(row, 1).editor.editorComponent!! as EditorPanel).editor }
  invokeAction(item.browseButton?.action!!, component)
}

private fun invokeAction(anAction: AnAction, component: Component) {
  runInEdt {
    val context = DataManager.getInstance().getDataContext(component)
    val event = AnActionEvent.createFromAnAction(anAction, null, "menu", context)
    anAction.actionPerformed(event)
  }
}