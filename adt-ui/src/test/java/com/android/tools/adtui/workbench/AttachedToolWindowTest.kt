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
import com.android.testutils.MockitoKt.mock
import com.android.tools.adtui.common.AdtUiUtils.getActionMask
import com.android.tools.adtui.swing.FakeKeyboardFocusManager
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
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
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

class AttachedToolWindowTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val popupRule = JBPopupRule()

  @get:Rule
  val chain = RuleChain(projectRule, disposableRule, popupRule, FlagRule(StudioFlags.DETACHABLE_ATTACHED_TOOLWINDOWS, true))

  private val project: Project
    get() = projectRule.project
  private val disposable: Disposable
    get() = disposableRule.disposable

  private val myDragListener: ButtonDragListener<String> = mock()
  private val myModel: SideModel<String> = mock()
  private val myDefinition = PalettePanelToolContent.getDefinition()
  private val myPropertiesComponent: PropertiesComponent = PropertiesComponentMock()
  private val myWorkBench: WorkBench<String> by lazy {
    runInEdtAndGet {
      WorkBench(project, "DESIGNER", null, disposable, 0)
    }
  }
  private val myToolWindow: AttachedToolWindow<String> by lazy {
    runInEdtAndGet {
      AttachedToolWindow(myDefinition, myDragListener, myWorkBench, myModel, false)
    }
  }

  @Before
  fun setUp() {
    ApplicationManager.getApplication().replaceService(PropertiesComponent::class.java, myPropertiesComponent, disposable)
  }
  
  @Test
  fun testDefault() {
    assertThat(myToolWindow.toolName).isEqualTo("PALETTE")
    assertThat(myToolWindow.definition).isSameAs(myDefinition)
  }

  @Test
  fun testToolOrder() {
    myToolWindow.toolOrder = 77
    assertThat(myToolWindow.toolOrder).isEqualTo(77)
    myToolWindow.toolOrder = 3
    assertThat(myToolWindow.toolOrder).isEqualTo(3)
  }

  @Test
  fun testContext() {
    Mockito.`when`(myModel.context).thenReturn("Studio")
    assertThat(myToolWindow.context).isEqualTo("Studio")
  }

  @Test
  fun testContentContext() {
    val panel = myToolWindow.content as PalettePanelToolContent
    assertThat(panel).isNotNull()
    myToolWindow.context = "Android"
    assertThat(panel.toolContext).isEqualTo("Android")
    myToolWindow.context = "Google"
    assertThat(panel.toolContext).isEqualTo("Google")
  }

  @Test
  fun testDefaultPropertyValues() {
    assertThat(myToolWindow.isLeft).isTrue()
    assertThat(myToolWindow.isMinimized).isFalse()
    assertThat(myToolWindow.isSplit).isFalse()
    assertThat(myToolWindow.isAutoHide).isFalse()
    assertThat(myToolWindow.isFloating).isFalse()

    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.LEFT")).isTrue()
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED"))
      .isFalse()
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT"))
      .isFalse()
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.AUTO_HIDE"))
      .isFalse()
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.FLOATING"))
      .isFalse()
  }

  @Test
  fun testFloatingAndDetachedIgnoredWhenFlagIsOff() {
    myPropertiesComponent.setValue(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.FLOATING", true)
    myPropertiesComponent.setValue(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.DETACHED", true)
    StudioFlags.DETACHABLE_ATTACHED_TOOLWINDOWS.override(false)

    val window = myToolWindow
    assertThat(window.isDetached).isFalse()
    assertThat(window.isFloating).isFalse()
  }

  @Test
  fun testGettersAndSetters() {
    myToolWindow.isLeft = false
    assertThat(myToolWindow.isLeft).isFalse()
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.LEFT")).isFalse()

    myToolWindow.isMinimized = true
    assertThat(myToolWindow.isMinimized).isTrue()
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED"))
      .isTrue()

    myToolWindow.isSplit = true
    assertThat(myToolWindow.isSplit).isTrue()
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT")).isTrue()

    myToolWindow.isAutoHide = true
    assertThat(myToolWindow.isAutoHide).isTrue()
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.AUTO_HIDE"))
      .isTrue()

    myToolWindow.isFloating = true
    assertThat(myToolWindow.isFloating).isTrue()
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.FLOATING"))
      .isTrue()
  }

  @Test
  fun testDefinitionContext() {
    myToolWindow.isMinimized = true
    assertThat(myToolWindow.isMinimized).isTrue()
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED"))
      .isTrue()

    myWorkBench.context = "SPLIT"
    myToolWindow.isMinimized = true
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT.MINIMIZED"))
      .isTrue()
    // Changes to the MINIMIZED property in the SPLIT context should only affect this context
    myToolWindow.isMinimized = false
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT.MINIMIZED"))
      .isFalse()
    assertThat(myToolWindow.isMinimized).isFalse()

    // MINIMIZED property in the DEFAULT context remains true
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED"))
      .isTrue()
    myWorkBench.context = ""
    assertThat(myToolWindow.isMinimized).isTrue()
  }

  @Test
  fun testSetPropertyAndUpdateWillNotifyModelAndChangeContent() {
    myToolWindow.setPropertyAndUpdate(AttachedToolWindow.PropertyType.LEFT, false)
    assertThat(myToolWindow.isLeft).isFalse()
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.LEFT")).isFalse()
    assertThat(myToolWindow.content).isNotNull()
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.LEFT))

    myToolWindow.setPropertyAndUpdate(AttachedToolWindow.PropertyType.MINIMIZED, true)
    assertThat(myToolWindow.isMinimized).isTrue()
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED"))
      .isTrue()
    assertThat(myToolWindow.content).isNotNull()
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.MINIMIZED))

    myToolWindow.setPropertyAndUpdate(AttachedToolWindow.PropertyType.SPLIT, true)
    assertThat(myToolWindow.isSplit).isTrue()
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT")).isTrue()
    assertThat(myToolWindow.content).isNotNull()
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.SPLIT))

    myToolWindow.setPropertyAndUpdate(AttachedToolWindow.PropertyType.AUTO_HIDE, true)
    assertThat(myToolWindow.isAutoHide).isTrue()
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.AUTO_HIDE"))
      .isTrue()
    assertThat(myToolWindow.content).isNotNull()
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.AUTO_HIDE))

    myToolWindow.setPropertyAndUpdate(AttachedToolWindow.PropertyType.FLOATING, true)
    assertThat(myToolWindow.isFloating).isTrue()
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.FLOATING"))
      .isTrue()
    assertThat(myToolWindow.content).isNull()
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.DETACHED))
  }

  @Test
  fun testMinimizeDefaultSetInConstructor() {
    assertThat(myToolWindow.isMinimized).isFalse()

    // Change the workbench context to ensure we're getting a different property, and reset the tool window
    myWorkBench.context = "testMinimizeDefaultSetInConstructor"
    val toolWindow = runInEdtAndGet { AttachedToolWindow(myDefinition, myDragListener, myWorkBench, myModel, true) }
    assertThat(toolWindow.isMinimized).isTrue()
  }

  @Test
  fun testMinimizeAutoHideIsNotGlobal() {
    myToolWindow.isAutoHide = true
    myToolWindow.isMinimized = true
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED"))
      .isFalse()
    myToolWindow.isMinimized = false
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED"))
      .isFalse()
    myToolWindow.isMinimized = true
    assertThat(myPropertiesComponent.getBoolean(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED"))
      .isFalse()
  }

  @Test
  fun testAutoClose() {
    myToolWindow.isAutoHide = true
    myToolWindow.isMinimized = false
    val panel = myToolWindow.content as PalettePanelToolContent
    panel.closeAutoHideWindow()
    assertThat(myToolWindow.isMinimized).isTrue()
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.MINIMIZED))
  }

  @Test
  fun testRestore() {
    myToolWindow.isMinimized = true
    val panel = myToolWindow.content as PalettePanelToolContent
    panel.restore()
    assertThat(myToolWindow.isMinimized).isFalse()
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.MINIMIZED))
  }

  @Test
  fun testRestoreDefaultLayout() {
    myToolWindow.isMinimized = true
    myToolWindow.isLeft = false
    myToolWindow.isSplit = true
    myToolWindow.isAutoHide = true

    myToolWindow.restoreDefaultLayout()

    assertThat(myToolWindow.isMinimized).isFalse()
    assertThat(myToolWindow.isLeft).isTrue()
    assertThat(myToolWindow.isSplit).isFalse()
    assertThat(myToolWindow.isAutoHide).isFalse()
  }

  @Test
  fun testStoreAndRestoreDefaultLayout() {
    myToolWindow.isMinimized = true
    myToolWindow.isLeft = false
    myToolWindow.isSplit = true
    myToolWindow.isAutoHide = true

    myToolWindow.storeDefaultLayout()

    myToolWindow.isFloating = true
    myToolWindow.isLeft = true
    myToolWindow.isSplit = false
    myToolWindow.isAutoHide = false

    myToolWindow.restoreDefaultLayout()

    assertThat(myToolWindow.isMinimized).isTrue()
    assertThat(myToolWindow.isLeft).isFalse()
    assertThat(myToolWindow.isSplit).isTrue()
    assertThat(myToolWindow.isAutoHide).isTrue()
  }

  @Test
  fun testDraggedEvent() {
    val button = myToolWindow.minimizedButton
    button.setSize(20, 50)
    val event = MouseEvent(button, MouseEvent.MOUSE_DRAGGED, 1, InputEvent.BUTTON1_DOWN_MASK, 20, 150, 1, false)
    fireMouseDragged(button, event)
    val dragEventArgumentCaptor = ArgumentCaptor.forClass(
      AttachedToolWindow.DragEvent::class.java
    )
    Mockito.verify(myDragListener).buttonDragged(eq(myToolWindow), dragEventArgumentCaptor.capture())
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
    val button = myToolWindow.minimizedButton
    button.setSize(20, 50)
    val event1 = MouseEvent(button, MouseEvent.MOUSE_DRAGGED, 1, InputEvent.BUTTON1_DOWN_MASK, 20, 150, 1, false)
    fireMouseDragged(button, event1)
    val event2 = MouseEvent(button, MouseEvent.MOUSE_RELEASED, 1, InputEvent.BUTTON1_DOWN_MASK, 800, 450, 1, false)
    fireMouseReleased(button, event2)
    val dragEventArgumentCaptor = ArgumentCaptor.forClass(
      AttachedToolWindow.DragEvent::class.java
    )
    Mockito.verify(myDragListener).buttonDropped(eq(myToolWindow), dragEventArgumentCaptor.capture())
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
    myToolWindow.isMinimized = false
    val button = myToolWindow.minimizedButton

    val event1 = MouseEvent(button, MouseEvent.MOUSE_CLICKED, 1, InputEvent.BUTTON1_DOWN_MASK, 20, 150, 1, false)
    fireMouseClicked(button, event1)
    assertThat(myToolWindow.isMinimized).isTrue()
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.MINIMIZED))

    val event2 = MouseEvent(button, MouseEvent.MOUSE_CLICKED, 1, InputEvent.BUTTON1_DOWN_MASK, 20, 150, 1, false)
    fireMouseClicked(button, event2)
    assertThat(myToolWindow.isMinimized).isFalse()
    Mockito.verify(myModel, Mockito.times(2))
      .update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.MINIMIZED))
  }

  @Test
  fun testAddedGearActionFromButtonRightClick() {
    val panel = myToolWindow.content as PalettePanelToolContent
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
    myToolWindow.isLeft = !location.isLeft
    myToolWindow.isSplit = !location.isBottom

    val action = findActionWithName(popupMenuFromButtonRightClick, location.title)!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(myToolWindow.isLeft).isEqualTo(location.isLeft)
    assertThat(myToolWindow.isSplit).isEqualTo(location.isBottom)
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.SPLIT))
  }

  @Test
  fun testSelectLeftFromButtonRightClick() {
    myToolWindow.isLeft = false
    val action = findActionWithName(popupMenuFromButtonRightClick, "Left Top")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(myToolWindow.isLeft).isTrue()
    assertThat(myToolWindow.isSplit).isFalse()
  }

  @Test
  fun testSelectRightFromButtonRightClick() {
    myToolWindow.isLeft = true
    val action = findActionWithName(popupMenuFromButtonRightClick, "Right Top")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(myToolWindow.isLeft).isFalse()
    assertThat(myToolWindow.isSplit).isFalse()
  }

  @Test
  fun testSelectSwapFromButtonRightClick() {
    myToolWindow.isLeft = true

    val action = findActionWithName(popupMenuFromButtonRightClick, "Swap")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    Mockito.verify(myModel).swap()
  }

  @Test
  fun testToggleDockModeFromButtonRightClick() {
    myToolWindow.isAutoHide = false

    val action = findActionWithName(popupMenuFromButtonRightClick, "Docked Mode")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(myToolWindow.isAutoHide).isTrue()
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.AUTO_HIDE))

    action.actionPerformed(createActionEvent(action))

    assertThat(myToolWindow.isAutoHide).isFalse()
    Mockito.verify(myModel, Mockito.times(2))
      .update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.AUTO_HIDE))
  }

  @Test
  fun testToggleFloatingModeFromButtonRightClick() {
    myToolWindow.isFloating = false

    val action = findActionWithName(popupMenuFromButtonRightClick, "Floating Mode")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(myToolWindow.isFloating).isTrue()
    assertThat(myToolWindow.isDetached).isTrue()
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.DETACHED))

    action.actionPerformed(createActionEvent(action))

    assertThat(myToolWindow.isFloating).isFalse()
    assertThat(myToolWindow.isDetached).isTrue()
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.DETACHED))
  }

  @Test
  fun testToggleAttachedModeFromButtonRightClick() {
    val action = findActionWithName(popupMenuFromButtonRightClick, "None")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(myToolWindow.isDetached).isTrue()
    assertThat(myToolWindow.isFloating).isFalse()
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.DETACHED))
  }

  @Test
  fun testHideFromButtonInHeader() {
    myToolWindow.isFloating = false

    val button = findButtonByName(myToolWindow.component, "Hide")
    assertThat(button).isNotNull()
    runInEdtAndWait { button!!.click() }

    assertThat(myToolWindow.isMinimized).isTrue()
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.MINIMIZED))
  }

  @Test
  fun testAdditionalActionFromButtonInHeader() {
    val panel = myToolWindow.content as PalettePanelToolContent
    val button = findButtonByName(myToolWindow.component, "AdditionalAction")
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
    myToolWindow.isLeft = !location.isLeft
    myToolWindow.isSplit = !location.isBottom

    val action = findActionWithName(popupMenuFromGearButtonInHeader, location.title)!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(myToolWindow.isLeft).isEqualTo(location.isLeft)
    assertThat(myToolWindow.isSplit).isEqualTo(location.isBottom)
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.SPLIT))
  }

  @Test
  fun testSelectLeftFromGearButtonInHeader() {
    myToolWindow.isLeft = false
    val action = findActionWithName(popupMenuFromGearButtonInHeader, "Left Top")
    assertThat(action).isNotNull()
    action!!.actionPerformed(createActionEvent(action))

    assertThat(myToolWindow.isLeft).isTrue()
    assertThat(myToolWindow.isSplit).isFalse()
  }

  @Test
  fun testSelectRightFromGearButtonInHeader() {
    myToolWindow.isLeft = true
    val action = findActionWithName(popupMenuFromGearButtonInHeader, "Right Top")!!
    action.actionPerformed(createActionEvent(action))

    assertThat(myToolWindow.isLeft).isFalse()
    assertThat(myToolWindow.isSplit).isFalse()
  }

  @Test
  fun testSelectSwapFromGearButtonInHeader() {
    myToolWindow.isLeft = true

    val action = findActionWithName(popupMenuFromGearButtonInHeader, "Swap")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    Mockito.verify(myModel).swap()
  }

  @Test
  fun testToggleDockModeFromGearButtonInHeader() {
    myToolWindow.isAutoHide = false

    val action = findActionWithName(popupMenuFromGearButtonInHeader, "Docked Mode")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(myToolWindow.isAutoHide).isTrue()
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.AUTO_HIDE))

    action.actionPerformed(createActionEvent(action))

    assertThat(myToolWindow.isAutoHide).isFalse()
    Mockito.verify(myModel, Mockito.times(2))
      .update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.AUTO_HIDE))
  }

  @Test
  fun testToggleFloatingModeFromGearButtonInHeader() {
    myToolWindow.isFloating = false

    val action = findActionWithName(popupMenuFromGearButtonInHeader, "Floating Mode")!!
    assertThat(action).isNotNull()
    action.actionPerformed(createActionEvent(action))

    assertThat(myToolWindow.isFloating).isTrue()
    assertThat(myToolWindow.isDetached).isTrue()
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.DETACHED))

    action.actionPerformed(createActionEvent(action))

    assertThat(myToolWindow.isFloating).isFalse()
    assertThat(myToolWindow.isDetached).isTrue()
    Mockito.verify(myModel).update(eq(myToolWindow), eq(AttachedToolWindow.PropertyType.FLOATING))
  }

  @Test
  fun testSearchButtonInHeader() {
    val header = findHeaderLabel(myToolWindow.component)
    assertThat(header.isVisible).isTrue()
    val searchField = findHeaderSearchField(myToolWindow.component)
    assertThat(searchField.isVisible).isFalse()

    val button = findRequiredButtonByName(
      myToolWindow.component, "Search"
    )
    val panel = myToolWindow.content as PalettePanelToolContent?
    assertThat(panel).isNotNull()
    panel!!.isFilteringActive = false
    val toolbar = ActionToolbar.findToolbarBy(button)
    if (toolbar != null) {
      runInEdtAndWait { toolbar.updateActionsImmediately() }
    }
    assertThat(button.isEnabled).isFalse()
    panel.isFilteringActive = true
    if (toolbar != null) {
      runInEdtAndWait { toolbar.updateActionsImmediately() }
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
    val panel = myToolWindow.content as PalettePanelToolContent?
    assertThat(panel).isNotNull()
    runInEdtAndWait { findRequiredButtonByName(myToolWindow.component, "Search").click() }

    val searchField = findHeaderSearchField(myToolWindow.component)
    searchField.text = "el"

    assertThat(panel!!.filter).isEqualTo("el")
  }

  @Test
  fun testAcceptedSearchesAreStoredInHistory() {
    val panel = myToolWindow.content as PalettePanelToolContent?
    assertThat(panel).isNotNull()
    runInEdtAndWait { findRequiredButtonByName(myToolWindow.component, "Search").click() }
    val searchField = findHeaderSearchField(myToolWindow.component)
    searchField.text = "ele"
    searchField.text = "eleva"
    fireFocusLost(searchField.textEditor)
    searchField.text = "vi"
    searchField.text = "visible"
    fireFocusLost(searchField.textEditor)
    searchField.text = "con"
    searchField.text = "contex"
    fireFocusLost(searchField.textEditor)

    assertThat(myPropertiesComponent.getValue(AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.TEXT_SEARCH_HISTORY"))
      .isEqualTo("contex\nvisible\neleva")
  }

  @Test
  fun testStartSearching() {
    val panel = myToolWindow.content as PalettePanelToolContent
    runInEdtAndWait { panel.startFiltering('b') }

    val searchField = findHeaderSearchField(myToolWindow.component)
    assertThat(searchField.isVisible).isTrue()
    assertThat(searchField.text).isEqualTo("b")
  }

  @Test
  fun testStopSearching() {
    runInEdtAndWait { findRequiredButtonByName(myToolWindow.component, "Search").click() }
    val searchField = findHeaderSearchField(myToolWindow.component)
    searchField.text = "width"

    val panel = myToolWindow.content as PalettePanelToolContent
    runInEdtAndWait { panel.stopFiltering() }
    assertThat(searchField.isVisible).isFalse()
    assertThat(searchField.text).isEqualTo("")
  }

  @Test
  fun testEscapeClosesSearchFieldIfTextIsEmpty() {
    runInEdtAndWait { findRequiredButtonByName(myToolWindow.component, "Search").click() }
    val searchField = findHeaderSearchField(myToolWindow.component)
    searchField.text = ""
    runInEdtAndWait { fireKey(searchField.textEditor, KeyEvent.VK_ESCAPE) }
    assertThat(searchField.isVisible).isFalse()
  }

  @Test
  fun testContentIsDisposed() {
    val panel = myToolWindow.content as PalettePanelToolContent
    Disposer.dispose(myToolWindow)
    assertThat(panel.isDisposed).isTrue()
  }

  @Test
  fun testDefaultValueDoesNotOverrideActualValue() {
    myToolWindow.setDefaultProperty(AttachedToolWindow.PropertyType.SPLIT, false)
    myToolWindow.setProperty(AttachedToolWindow.PropertyType.SPLIT, true)
    myToolWindow.setDefaultProperty(AttachedToolWindow.PropertyType.SPLIT, false)
    assertThat(myToolWindow.getProperty(AttachedToolWindow.PropertyType.SPLIT)).isTrue()

    myToolWindow.setDefaultProperty(AttachedToolWindow.PropertyType.SPLIT, true)
    myToolWindow.setProperty(AttachedToolWindow.PropertyType.SPLIT, false)
    myToolWindow.setDefaultProperty(AttachedToolWindow.PropertyType.SPLIT, true)
    assertThat(myToolWindow.getProperty(AttachedToolWindow.PropertyType.SPLIT)).isFalse()
  }

  @Test
  fun testCommandFStartsFiltering() {
    val panel = myToolWindow.content as PalettePanelToolContent
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
    assertThat(myToolWindow.searchField!!.isVisible).isTrue()
  }

  @Test
  fun testActionsEnabledAtStartup() {
    runInDumbModeSynchronously(projectRule.project, ThrowableRunnable {
      val button = findRequiredButtonByName(
        myToolWindow.component, "More Options"
      )
      runInEdtAndWait { myToolWindow.updateActions() }
      assertThat(button.isEnabled).isTrue()
    })
  }

  private val popupMenuFromButtonRightClick: List<AnAction>
    get() {
      val button = myToolWindow.minimizedButton

      val event1 = MouseEvent(button, MouseEvent.MOUSE_CLICKED, 1, InputEvent.META_DOWN_MASK, 20, 150, 1, false)
      fireMouseClicked(button, event1)

      return popupRule.fakePopupFactory.getNextPopup<Any, FakeJBPopup<Any>>().actions
    }

  private val popupMenuFromGearButtonInHeader: List<AnAction>
    get() {
      val button = findRequiredButtonByName(
        myToolWindow.component, "More Options"
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
