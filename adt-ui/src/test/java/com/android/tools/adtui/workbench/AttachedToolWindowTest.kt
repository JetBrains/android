/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.adtui.workbench

import com.android.flags.junit.FlagRule
import org.mockito.kotlin.mock
import com.android.tools.adtui.common.AdtUiUtils.getActionMask
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.popup.FakeJBPopup
import com.android.tools.adtui.swing.popup.JBPopupRule
import com.android.tools.adtui.workbench.AttachedToolWindow.ButtonDragListener
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.DumbModeTestUtils.runInDumbModeSynchronously
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.SearchTextField
import com.intellij.util.ThrowableRunnable
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito
import java.awt.Container
import java.awt.event.FocusEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.Arrays
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.KeyStroke

@RunsInEdt
class AttachedToolWindowTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val popupRule = JBPopupRule()
  private val edtRule = EdtRule()

  @get:Rule
  val chain = RuleChain(edtRule, projectRule, disposableRule, popupRule, FlagRule(StudioFlags.DETACHABLE_ATTACHED_TOOLWINDOWS, true))

  private val project: Project
    get() = projectRule.project
  private val disposable: Disposable
    get() = disposableRule.disposable

  private val dragListener: ButtonDragListener<String> = mock()
  private val model: SideModel<String> = mock()
  private val definition = PalettePanelToolContent.getDefinition()
  private val propertiesComponent: PropertiesComponent = PropertiesComponentMock()
  private lateinit var workBench: WorkBench<String>
  private lateinit var toolWindow: AttachedToolWindow<String>

  @Before
  fun setUp() {
    ApplicationManager.getApplication().replaceService(PropertiesComponent::class.java, propertiesComponent, disposable)
    workBench = WorkBench(project, "DESIGNER", null, disposable, 0)
    toolWindow = AttachedToolWindow(definition, dragListener, workBench, model, false)
    FakeUi(toolWindow.component, createFakeWindow = true, parentDisposable = disposable)
  }

  @Test
  fun testDefault() {
    assertThat(toolWindow.toolName).isEqualTo("PALETTE")
    assertThat(toolWindow.definition).isSameAs(definition)
  }

  @Test
  fun testToolOrder() {
    toolWindow.toolOrder = 77
    assertThat(toolWindow.toolOrder).isEqualTo(77)
    toolWindow.toolOrder = 3
    assertThat(toolWindow.toolOrder).isEqualTo(3)
  }

  @Test
  fun testContext() {
    Mockito.`when`(model.context).thenReturn("Studio")
    assertThat(toolWindow.context).isEqualTo("Studio")
  }

  @Test
  fun testContentContext() {
    val panel = toolWindow.content as PalettePanelToolContent
    assertThat(panel).isNotNull()
    toolWindow.context = "Android"
    assertThat(panel.toolContext).isEqualTo("Android")
    toolWindow.context = "Google"
    assertThat(panel.toolContext).isEqualTo("Google")
  }

  @Test
  fun testDefaultPropertyValues() {
    assertThat(toolWindow.isLeft).isTrue()
    assertThat(toolWindow.isMinimized).isFalse()
    assertThat(toolWindow.isSplit).isFalse()
    assertThat(toolWindow.isAutoHide).isFalse()
    assertThat(toolWindow.isFloating).isFalse()

    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.LEFT")).isTrue()
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED"))
      .isFalse()
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT"))
      .isFalse()
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.AUTO_HIDE"))
      .isFalse()
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.FLOATING"))
      .isFalse()
  }

  @Test
  fun testFloatingAndDetachedIgnoredWhenFlagIsOff() {
    propertiesComponent.setValue(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.FLOATING", true)
    propertiesComponent.setValue(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.DETACHED", true)
    StudioFlags.DETACHABLE_ATTACHED_TOOLWINDOWS.override(false)

    val window = toolWindow
    assertThat(window.isDetached).isFalse()
    assertThat(window.isFloating).isFalse()
  }

  @Test
  fun testGettersAndSetters() {
    toolWindow.isLeft = false
    assertThat(toolWindow.isLeft).isFalse()
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.LEFT")).isFalse()

    toolWindow.isMinimized = true
    assertThat(toolWindow.isMinimized).isTrue()
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED"))
      .isTrue()

    toolWindow.isSplit = true
    assertThat(toolWindow.isSplit).isTrue()
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT")).isTrue()

    toolWindow.isAutoHide = true
    assertThat(toolWindow.isAutoHide).isTrue()
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.AUTO_HIDE"))
      .isTrue()

    toolWindow.isFloating = true
    assertThat(toolWindow.isFloating).isTrue()
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.FLOATING"))
      .isTrue()
  }

  @Test
  fun testDefinitionContext() {
    toolWindow.isMinimized = true
    assertThat(toolWindow.isMinimized).isTrue()
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED"))
      .isTrue()

    workBench.context = "SPLIT"
    toolWindow.isMinimized = true
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT.MINIMIZED"))
      .isTrue()
    // Changes to the MINIMIZED property in the SPLIT context should only affect this context
    toolWindow.isMinimized = false
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT.MINIMIZED"))
      .isFalse()
    assertThat(toolWindow.isMinimized).isFalse()

    // MINIMIZED property in the DEFAULT context remains true
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED"))
      .isTrue()
    workBench.context = ""
    assertThat(toolWindow.isMinimized).isTrue()
  }

  @Test
  fun testSetPropertyAndUpdateWillNotifyModelAndChangeContent() {
    toolWindow.setPropertyAndUpdate(AttachedToolWindow.PropertyType.LEFT, false)
    assertThat(toolWindow.isLeft).isFalse()
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.LEFT")).isFalse()
    assertThat(toolWindow.content).isNotNull()
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.LEFT))

    toolWindow.setPropertyAndUpdate(AttachedToolWindow.PropertyType.MINIMIZED, true)
    assertThat(toolWindow.isMinimized).isTrue()
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED"))
      .isTrue()
    assertThat(toolWindow.content).isNotNull()
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.MINIMIZED))

    toolWindow.setPropertyAndUpdate(AttachedToolWindow.PropertyType.SPLIT, true)
    assertThat(toolWindow.isSplit).isTrue()
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT")).isTrue()
    assertThat(toolWindow.content).isNotNull()
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.SPLIT))

    toolWindow.setPropertyAndUpdate(AttachedToolWindow.PropertyType.AUTO_HIDE, true)
    assertThat(toolWindow.isAutoHide).isTrue()
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.AUTO_HIDE"))
      .isTrue()
    assertThat(toolWindow.content).isNotNull()
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.AUTO_HIDE))

    toolWindow.setPropertyAndUpdate(AttachedToolWindow.PropertyType.FLOATING, true)
    assertThat(toolWindow.isFloating).isTrue()
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.FLOATING"))
      .isTrue()
    assertThat(toolWindow.content).isNull()
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.DETACHED))
  }

  @Test
  fun testMinimizeDefaultSetInConstructor() {
    assertThat(toolWindow.isMinimized).isFalse()

    // Change the workbench context to ensure we're getting a different property, and reset the tool window
    workBench.context = "testMinimizeDefaultSetInConstructor"
    val toolWindow = runInEdtAndGet { AttachedToolWindow(definition, dragListener, workBench, model, true) }
    assertThat(toolWindow.isMinimized).isTrue()
  }

  @Test
  fun testMinimizeAutoHideIsNotGlobal() {
    toolWindow.isAutoHide = true
    toolWindow.isMinimized = true
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED"))
      .isFalse()
    toolWindow.isMinimized = false
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED"))
      .isFalse()
    toolWindow.isMinimized = true
    assertThat(propertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED"))
      .isFalse()
  }

  @Test
  fun testAutoClose() {
    toolWindow.isAutoHide = true
    toolWindow.isMinimized = false
    val panel = toolWindow.content as PalettePanelToolContent
    panel.closeAutoHideWindow()
    assertThat(toolWindow.isMinimized).isTrue()
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.MINIMIZED))
  }

  @Test
  fun testRestore() {
    toolWindow.isMinimized = true
    val panel = toolWindow.content as PalettePanelToolContent
    panel.restore()
    assertThat(toolWindow.isMinimized).isFalse()
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.MINIMIZED))
  }

  @Test
  fun testRestoreDefaultLayout() {
    toolWindow.isMinimized = true
    toolWindow.isLeft = false
    toolWindow.isSplit = true
    toolWindow.isAutoHide = true

    toolWindow.restoreDefaultLayout()

    assertThat(toolWindow.isMinimized).isFalse()
    assertThat(toolWindow.isLeft).isTrue()
    assertThat(toolWindow.isSplit).isFalse()
    assertThat(toolWindow.isAutoHide).isFalse()
  }

  @Test
  fun testStoreAndRestoreDefaultLayout() {
    toolWindow.isMinimized = true
    toolWindow.isLeft = false
    toolWindow.isSplit = true
    toolWindow.isAutoHide = true

    toolWindow.storeDefaultLayout()

    toolWindow.isFloating = true
    toolWindow.isLeft = true
    toolWindow.isSplit = false
    toolWindow.isAutoHide = false

    toolWindow.restoreDefaultLayout()

    assertThat(toolWindow.isMinimized).isTrue()
    assertThat(toolWindow.isLeft).isFalse()
    assertThat(toolWindow.isSplit).isTrue()
    assertThat(toolWindow.isAutoHide).isTrue()
  }

  @Test
  fun testDraggedEvent() {
    val button = toolWindow.minimizedButton
    button.setSize(20, 50)
    val event = MouseEvent(button, MouseEvent.MOUSE_DRAGGED, 1, InputEvent.BUTTON1_DOWN_MASK, 20, 150, 1, false)
    fireMouseDragged(button, event)
    val dragEventArgumentCaptor = ArgumentCaptor.forClass(
      AttachedToolWindow.DragEvent::class.java
    )
    Mockito.verify(dragListener).buttonDragged(eq(toolWindow), dragEventArgumentCaptor.capture())
    val dragEvent = dragEventArgumentCaptor.value
    assertThat(dragEvent.dragPoint.x).isEqualTo(20)
    assertThat(dragEvent.dragPoint.y).isEqualTo(150)
    assertThat(dragEvent.mousePoint.x).isEqualTo(20)
    assertThat(dragEvent.mousePoint.y).isEqualTo(150)
    assertThat(dragEvent.dragImage).isInstanceOf(JLabel::class.java)
    assertThat((dragEvent.dragImage as JLabel).icon).isNotNull()
  }

  @Test
  fun testDropEvent() {
    val button = toolWindow.minimizedButton
    button.setSize(20, 50)
    val event1 = MouseEvent(button, MouseEvent.MOUSE_DRAGGED, 1, InputEvent.BUTTON1_DOWN_MASK, 20, 150, 1, false)
    fireMouseDragged(button, event1)
    val event2 = MouseEvent(button, MouseEvent.MOUSE_RELEASED, 1, InputEvent.BUTTON1_DOWN_MASK, 800, 450, 1, false)
    fireMouseReleased(button, event2)
    val dragEventArgumentCaptor = ArgumentCaptor.forClass(
      AttachedToolWindow.DragEvent::class.java
    )
    Mockito.verify(dragListener).buttonDropped(eq(toolWindow), dragEventArgumentCaptor.capture())
    val dragEvent = dragEventArgumentCaptor.value
    assertThat(dragEvent.dragPoint.x).isEqualTo(20)
    assertThat(dragEvent.dragPoint.y).isEqualTo(150)
    assertThat(dragEvent.mousePoint.x).isEqualTo(800)
    assertThat(dragEvent.mousePoint.y).isEqualTo(450)
    assertThat(dragEvent.dragImage).isInstanceOf(JLabel::class.java)
    assertThat((dragEvent.dragImage as JLabel).icon).isNotNull()
  }

  @Test
  fun testButtonClickTogglesMinimizedState() {
    toolWindow.isMinimized = false
    val button = toolWindow.minimizedButton

    val event1 = MouseEvent(button, MouseEvent.MOUSE_CLICKED, 1, InputEvent.BUTTON1_DOWN_MASK, 20, 150, 1, false)
    fireMouseClicked(button, event1)
    assertThat(toolWindow.isMinimized).isTrue()
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.MINIMIZED))

    val event2 = MouseEvent(button, MouseEvent.MOUSE_CLICKED, 1, InputEvent.BUTTON1_DOWN_MASK, 20, 150, 1, false)
    fireMouseClicked(button, event2)
    assertThat(toolWindow.isMinimized).isFalse()
    Mockito.verify(model, Mockito.times(2))
      .update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.MINIMIZED))
  }

  @Test
  fun testAddedGearActionFromButtonRightClick() {
    val panel = toolWindow.content as PalettePanelToolContent
    val action = findActionWithName(popupMenuFromButtonRightClick, "GearAction")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(panel.isGearActionPerformed).isTrue()
  }

  @Test
  fun testSelectLeftTopFromButtonRightClick() {
    testSetLocationFromButtonRightClick(AttachedLocation.LeftTop)
  }

  @Test
  fun testSelectLeftBottomFromButtonRightClick() {
    testSetLocationFromButtonRightClick(AttachedLocation.LeftBottom)
  }

  @Test
  fun testSelectRightTopFromButtonRightClick() {
    testSetLocationFromButtonRightClick(AttachedLocation.RightTop)
  }

  @Test
  fun testSelectRightBottomFromButtonRightClick() {
    testSetLocationFromButtonRightClick(AttachedLocation.RightBottom)
  }

  private fun testSetLocationFromButtonRightClick(location: AttachedLocation) {
    toolWindow.isLeft = !location.isLeft
    toolWindow.isSplit = !location.isBottom

    val action = findActionWithName(popupMenuFromButtonRightClick, location.title)!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(toolWindow.isLeft).isEqualTo(location.isLeft)
    assertThat(toolWindow.isSplit).isEqualTo(location.isBottom)
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.SPLIT))
  }

  @Test
  fun testSelectLeftFromButtonRightClick() {
    toolWindow.isLeft = false
    val action = findActionWithName(popupMenuFromButtonRightClick, "Left Top")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(toolWindow.isLeft).isTrue()
    assertThat(toolWindow.isSplit).isFalse()
  }

  @Test
  fun testSelectRightFromButtonRightClick() {
    toolWindow.isLeft = true
    val action = findActionWithName(popupMenuFromButtonRightClick, "Right Top")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(toolWindow.isLeft).isFalse()
    assertThat(toolWindow.isSplit).isFalse()
  }

  @Test
  fun testSelectSwapFromButtonRightClick() {
    toolWindow.isLeft = true

    val action = findActionWithName(popupMenuFromButtonRightClick, "Swap")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    Mockito.verify(model).swap()
  }

  @Test
  fun testToggleDockModeFromButtonRightClick() {
    toolWindow.isAutoHide = false

    val action = findActionWithName(popupMenuFromButtonRightClick, "Docked Mode")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(toolWindow.isAutoHide).isTrue()
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.AUTO_HIDE))

    action.actionPerformed(createActionEvent(action))

    assertThat(toolWindow.isAutoHide).isFalse()
    Mockito.verify(model, Mockito.times(2))
      .update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.AUTO_HIDE))
  }

  @Test
  fun testToggleFloatingModeFromButtonRightClick() {
    toolWindow.isFloating = false

    val action = findActionWithName(popupMenuFromButtonRightClick, "Floating Mode")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(toolWindow.isFloating).isTrue()
    assertThat(toolWindow.isDetached).isTrue()
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.DETACHED))

    action.actionPerformed(createActionEvent(action))

    assertThat(toolWindow.isFloating).isFalse()
    assertThat(toolWindow.isDetached).isTrue()
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.DETACHED))
  }

  @Test
  fun testToggleAttachedModeFromButtonRightClick() {
    val action = findActionWithName(popupMenuFromButtonRightClick, "None")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(toolWindow.isDetached).isTrue()
    assertThat(toolWindow.isFloating).isFalse()
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.DETACHED))
  }

  @Test
  fun testHideFromButtonInHeader() {
    toolWindow.isFloating = false

    val button = findButtonByName(toolWindow.component, "Hide")
    assertThat(button).isNotNull()
    runInEdtAndWait { button!!.click() }

    assertThat(toolWindow.isMinimized).isTrue()
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.MINIMIZED))
  }

  @Test
  fun testAdditionalActionFromButtonInHeader() {
    val panel = toolWindow.content as PalettePanelToolContent
    val button = findButtonByName(toolWindow.component, "AdditionalAction")
    assertThat(button).isNotNull()
    runInEdtAndWait { button!!.click() }

    assertThat(panel.isAdditionalActionPerformed).isTrue()
  }

  @Test
  fun testSelectLeftTopFromGearButtonInHeader() {
    testSetLocationFromGearButtonInHeader(AttachedLocation.LeftTop)
  }

  @Test
  fun testSelectLeftBottomFromGearButtonInHeader() {
    testSetLocationFromGearButtonInHeader(AttachedLocation.LeftBottom)
  }

  @Test
  fun testSelectRightTopFromGearButtonInHeader() {
    testSetLocationFromGearButtonInHeader(AttachedLocation.RightTop)
  }

  @Test
  fun testSelectRightBottomFromGearButtonInHeader() {
    testSetLocationFromGearButtonInHeader(AttachedLocation.RightBottom)
  }

  private fun testSetLocationFromGearButtonInHeader(location: AttachedLocation) {
    toolWindow.isLeft = !location.isLeft
    toolWindow.isSplit = !location.isBottom

    val action = findActionWithName(popupMenuFromGearButtonInHeader, location.title)!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(toolWindow.isLeft).isEqualTo(location.isLeft)
    assertThat(toolWindow.isSplit).isEqualTo(location.isBottom)
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.SPLIT))
  }

  @Test
  fun testSelectLeftFromGearButtonInHeader() {
    toolWindow.isLeft = false
    val action = findActionWithName(popupMenuFromGearButtonInHeader, "Left Top")
    assertThat(action).isNotNull()
    action!!.actionPerformed(createActionEvent(action))

    assertThat(toolWindow.isLeft).isTrue()
    assertThat(toolWindow.isSplit).isFalse()
  }

  @Test
  fun testSelectRightFromGearButtonInHeader() {
    toolWindow.isLeft = true
    val action = findActionWithName(popupMenuFromGearButtonInHeader, "Right Top")!!
    action.actionPerformed(createActionEvent(action))

    assertThat(toolWindow.isLeft).isFalse()
    assertThat(toolWindow.isSplit).isFalse()
  }

  @Test
  fun testSelectSwapFromGearButtonInHeader() {
    toolWindow.isLeft = true

    val action = findActionWithName(popupMenuFromGearButtonInHeader, "Swap")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    Mockito.verify(model).swap()
  }

  @Test
  fun testToggleDockModeFromGearButtonInHeader() {
    toolWindow.isAutoHide = false

    val action = findActionWithName(popupMenuFromGearButtonInHeader, "Docked Mode")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(toolWindow.isAutoHide).isTrue()
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.AUTO_HIDE))

    action.actionPerformed(createActionEvent(action))

    assertThat(toolWindow.isAutoHide).isFalse()
    Mockito.verify(model, Mockito.times(2))
      .update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.AUTO_HIDE))
  }

  @Test
  fun testToggleFloatingModeFromGearButtonInHeader() {
    toolWindow.isFloating = false

    val action = findActionWithName(popupMenuFromGearButtonInHeader, "Floating Mode")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(toolWindow.isFloating).isTrue()
    assertThat(toolWindow.isDetached).isTrue()
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.DETACHED))

    action.actionPerformed(createActionEvent(action))

    assertThat(toolWindow.isFloating).isFalse()
    assertThat(toolWindow.isDetached).isTrue()
    Mockito.verify(model).update(eq(toolWindow), eq(AttachedToolWindow.PropertyType.FLOATING))
  }

  @Test
  fun testSearchButtonInHeader() {
    val header = findHeaderLabel(toolWindow.component)
    assertThat(header.isVisible).isTrue()
    val searchField = findHeaderSearchField(toolWindow.component)
    assertThat(searchField.isVisible).isFalse()

    val button = findRequiredButtonByName(
      toolWindow.component, "Search"
    )
    val panel = toolWindow.content as PalettePanelToolContent?
    assertThat(panel).isNotNull()
    panel!!.isFilteringActive = false
    val toolbar = ActionToolbar.findToolbarBy(button)
    if (toolbar != null) {
      runInEdtAndWait { toolbar.updateActionsAsync() }
    }
    assertThat(button.isEnabled).isFalse()
    panel.isFilteringActive = true
    if (toolbar != null) {
      runInEdtAndWait { toolbar.updateActionsAsync() }
    }
    assertThat(button.isEnabled).isTrue()

    runInEdtAndWait { button.click() }

    assertThat(header.isVisible).isFalse()
    assertThat(searchField.isVisible).isTrue()

    runInEdtAndWait { fireFocusLost(searchField.textEditor) }

    assertThat(header.isVisible).isTrue()
    assertThat(searchField.isVisible).isFalse()
  }

  @Test
  fun testSearchTextChangesAreSentToContent() {
    val panel = toolWindow.content as PalettePanelToolContent?
    assertThat(panel).isNotNull()
    runInEdtAndWait { findRequiredButtonByName(toolWindow.component, "Search").click() }

    val searchField = findHeaderSearchField(toolWindow.component)
    searchField.text = "el"

    assertThat(panel!!.filter).isEqualTo("el")
  }

  @Test
  fun testAcceptedSearchesAreStoredInHistory() {
    val panel = toolWindow.content as PalettePanelToolContent?
    assertThat(panel).isNotNull()
    runInEdtAndWait { findRequiredButtonByName(toolWindow.component, "Search").click() }
    val searchField = findHeaderSearchField(toolWindow.component)
    searchField.text = "ele"
    searchField.text = "eleva"
    fireFocusLost(searchField.textEditor)
    searchField.text = "vi"
    searchField.text = "visible"
    fireFocusLost(searchField.textEditor)
    searchField.text = "con"
    searchField.text = "contex"
    fireFocusLost(searchField.textEditor)

    assertThat(propertiesComponent.getValue(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.TEXT_SEARCH_HISTORY"))
      .isEqualTo("contex\nvisible\neleva")
  }

  @Test
  fun testStartSearching() {
    val panel = toolWindow.content as PalettePanelToolContent
    runInEdtAndWait { panel.startFiltering('b') }

    val searchField = findHeaderSearchField(toolWindow.component)
    assertThat(searchField.isVisible).isTrue()
    assertThat(searchField.text).isEqualTo("b")
  }

  @Test
  fun testStopSearching() {
    runInEdtAndWait { findRequiredButtonByName(toolWindow.component, "Search").click() }
    val searchField = findHeaderSearchField(toolWindow.component)
    searchField.text = "width"

    val panel = toolWindow.content as PalettePanelToolContent
    runInEdtAndWait { panel.stopFiltering() }
    assertThat(searchField.isVisible).isFalse()
    assertThat(searchField.text).isEqualTo("")
  }

  @Test
  fun testEscapeClosesSearchFieldIfTextIsEmpty() {
    runInEdtAndWait { findRequiredButtonByName(toolWindow.component, "Search").click() }
    val searchField = findHeaderSearchField(toolWindow.component)
    searchField.text = ""
    runInEdtAndWait { fireKey(searchField.textEditor, KeyEvent.VK_ESCAPE) }
    assertThat(searchField.isVisible).isFalse()
  }

  @Test
  fun testContentIsDisposed() {
    val panel = toolWindow.content as PalettePanelToolContent
    Disposer.dispose(toolWindow)
    assertThat(panel.isDisposed).isTrue()
  }

  @Test
  fun testDefaultValueDoesNotOverrideActualValue() {
    toolWindow.setDefaultProperty(AttachedToolWindow.PropertyType.SPLIT, false)
    toolWindow.setProperty(AttachedToolWindow.PropertyType.SPLIT, true)
    toolWindow.setDefaultProperty(AttachedToolWindow.PropertyType.SPLIT, false)
    assertThat(toolWindow.getProperty(AttachedToolWindow.PropertyType.SPLIT)).isTrue()

    toolWindow.setDefaultProperty(AttachedToolWindow.PropertyType.SPLIT, true)
    toolWindow.setProperty(AttachedToolWindow.PropertyType.SPLIT, false)
    toolWindow.setDefaultProperty(AttachedToolWindow.PropertyType.SPLIT, true)
    assertThat(toolWindow.getProperty(AttachedToolWindow.PropertyType.SPLIT)).isFalse()
  }

  @Test
  fun testCommandFStartsFiltering() {
    val panel = toolWindow.content as PalettePanelToolContent
    assertThat(panel).isNotNull()
    val component = panel.component
    val focusManager = FakeKeyboardFocusManager(disposableRule.disposable)
    focusManager.focusOwner = component

    val dispatcher = IdeKeyEventDispatcher(null)
    runInEdtAndWait {
      dispatcher.dispatchKeyEvent(
        KeyEvent(component, KeyEvent.KEY_PRESSED, 0, ourCommandF.modifiers, ourCommandF.keyCode, 'F')
      )
    }
    assertThat(toolWindow.searchField!!.isVisible).isTrue()
  }

  @Test
  fun testActionsEnabledAtStartup() {
    runInDumbModeSynchronously(projectRule.project, ThrowableRunnable {
      val button = findRequiredButtonByName(
        toolWindow.component, "More Options"
      )
      runInEdtAndWait { toolWindow.updateActions() }
      assertThat(button.isEnabled).isTrue()
    })
  }

  private val popupMenuFromButtonRightClick: List<AnAction>
    get() {
      val button = toolWindow.minimizedButton

      val event1 = MouseEvent(button, MouseEvent.MOUSE_CLICKED, 1, InputEvent.META_DOWN_MASK, 20, 150, 1, false)
      fireMouseClicked(button, event1)

      return popupRule.fakePopupFactory.getNextPopup<Any, FakeJBPopup<Any>>().actions
    }

  private val popupMenuFromGearButtonInHeader: List<AnAction>
    get() {
      val button = findRequiredButtonByName(
        toolWindow.component, "More Options"
      )
      runInEdtAndWait { button.click() }

      return popupRule.fakePopupFactory.getNextPopup<Any, FakeJBPopup<Any>>().actions
    }

  private class SomeAction private constructor(title: String) : AnAction(title) {
    init {
      if (title == IdeActions.ACTION_FIND) {
        registerCustomShortcutSet(CustomShortcutSet(ourCommandF), null)
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
    }
  }

  companion object {
    private val ourCommandF: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_F, getActionMask())
    private fun fireFocusLost(component: JComponent) {
      for (listener in component.focusListeners) {
        listener.focusLost(FocusEvent(component, FocusEvent.FOCUS_LOST))
      }
    }

    private fun fireMouseDragged(component: JComponent, event: MouseEvent) {
      for (listener in component.mouseMotionListeners) {
        listener.mouseDragged(event)
      }
    }

    private fun fireMouseReleased(component: JComponent, event: MouseEvent) {
      for (listener in component.mouseListeners) {
        listener.mouseReleased(event)
      }
    }

    private fun fireMouseClicked(component: JComponent, event: MouseEvent) {
      for (listener in component.mouseListeners) {
        runInEdtAndWait { listener.mouseClicked(event) }
      }
    }

    private fun fireKey(component: JComponent, keyCode: Int) {
      val event = KeyEvent(component, 0, 0, 0, keyCode, '\u0000')
      for (listener in component.keyListeners) {
        listener.keyPressed(event)
      }
      for (listener in component.keyListeners) {
        listener.keyTyped(event)
      }
      for (listener in component.keyListeners) {
        listener.keyReleased(event)
      }
    }

    private fun findActionWithName(actions: List<AnAction>, name: String): AnAction? {
      for (action in actions) {
        if (name == action.templatePresentation.text) {
          return action
        }
        if (action is DefaultActionGroup) {
          val childAction = findActionWithName(Arrays.asList(*action.childActionsOrStubs), name)
          if (childAction != null) {
            return childAction
          }
        }
      }
      return null
    }

    private fun findHeaderLabel(container: Container): JLabel {
      return findFirstComponentOfClass(container, JLabel::class.java)!!
    }

    private fun findHeaderSearchField(container: Container): SearchTextField {
      return findFirstComponentOfClass(container, SearchTextField::class.java)!!
    }

    private fun <T> findFirstComponentOfClass(container: Container, klass: Class<T>): T? {
      for (component in container.components) {
        if (klass.isInstance(component)) {
          return component as T
        }
        if (component is Container) {
          val t = findFirstComponentOfClass(component, klass)
          if (t != null) {
            return t
          }
        }
      }
      return null
    }

    private fun findRequiredButtonByName(container: Container, name: String): ActionButton =
       findButtonByName(container, name)!!

    private fun findButtonByName(container: Container, name: String): ActionButton? {
      for (component in container.components) {
        if (component is ActionButton) {
          val button = component
          val action = button.action
          if (name == action.templatePresentation.text) {
            return button
          }
        }
        if (component is Container) {
          val button = findButtonByName(component, name)
          if (button != null) {
            return button
          }
        }
      }
      return null
    }

    private fun createActionEvent(action: AnAction): AnActionEvent {
      val dataContext = Mockito.mock(DataContext::class.java)
      return AnActionEvent(
        null, dataContext, AttachedToolWindow.TOOL_WINDOW_TOOLBAR_PLACE, action.templatePresentation.clone(),
        ActionManager.getInstance(), 0
      )
    }
  }
}
