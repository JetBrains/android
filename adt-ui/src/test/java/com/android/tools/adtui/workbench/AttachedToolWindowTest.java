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
package com.android.tools.adtui.workbench;

import static com.android.tools.adtui.workbench.AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX;
import static com.android.tools.adtui.workbench.AttachedToolWindow.TOOL_WINDOW_TOOLBAR_PLACE;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.android.annotations.Nullable;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.workbench.AttachedToolWindow.PropertyType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.impl.InternalDecorator;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.ui.SearchTextField;
import java.awt.Component;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Objects;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import org.jetbrains.annotations.NotNull;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class AttachedToolWindowTest extends WorkBenchTestCase {
  @Mock private AttachedToolWindow.ButtonDragListener<String> myDragListener;
  @Mock private SideModel<String> myModel;
  @Mock private ActionManagerImpl myActionManager;
  @Mock private ActionPopupMenu myActionPopupMenu;
  @Mock private JPopupMenu myPopupMenu;
  @Mock private WorkBench<String> myWorkBench;
  @Mock private KeyboardFocusManager myKeyboardFocusManager;

  private static final KeyStroke ourCommandF = KeyStroke.getKeyStroke(KeyEvent.VK_F, AdtUiUtils.getActionMask());
  private PropertiesComponent myPropertiesComponent;
  private ToolWindowDefinition<String> myDefinition;
  private AttachedToolWindow<String> myToolWindow;
  private AutoCloseable myMocks;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myMocks = openMocks(this);
    registerApplicationService(ActionManager.class, myActionManager);
    registerApplicationService(PropertiesComponent.class, new PropertiesComponentMock());
    doAnswer(invocation -> new SomeAction(invocation.getArgument(0))).when(myActionManager).getAction(anyString());
    when(myActionManager.createActionPopupMenu(anyString(), any(ActionGroup.class), any(PresentationFactory.class))).thenReturn(myActionPopupMenu);
    when(myActionManager.createActionPopupMenu(anyString(), any(ActionGroup.class))).thenReturn(myActionPopupMenu);
    when(myActionManager.getRegistrationOrderComparator()).thenReturn(String.CASE_INSENSITIVE_ORDER);
    when(myActionManager.createActionToolbar(anyString(), any(ActionGroup.class), anyBoolean())).thenCallRealMethod();
    when(myActionManager.createActionToolbar(anyString(), any(ActionGroup.class), anyBoolean(), anyBoolean())).thenCallRealMethod();
    when(myActionManager.createActionToolbar(anyString(), any(ActionGroup.class), anyBoolean(), anyBoolean(), anyBoolean())).thenCallRealMethod();
    when(myActionManager.createActionToolbar(anyString(), any(ActionGroup.class), anyBoolean(), any(Function.class))).thenCallRealMethod();
    doCallRealMethod().when(myActionManager).performWithActionCallbacks(any(AnAction.class), any(AnActionEvent.class), any(Runnable.class));
    when(myActionPopupMenu.getComponent()).thenReturn(myPopupMenu);
    when(myModel.getProject()).thenReturn(getProject());
    myPropertiesComponent = PropertiesComponent.getInstance();
    myDefinition = PalettePanelToolContent.getDefinition();
    when(myWorkBench.getName()).thenReturn("DESIGNER");
    when(myWorkBench.getContext()).thenReturn("");

    myToolWindow = new AttachedToolWindow<>(myDefinition, myDragListener, myWorkBench, myModel, false);
    Disposer.register(getTestRootDisposable(), myWorkBench);

    KeyboardFocusManager.setCurrentKeyboardFocusManager(myKeyboardFocusManager);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      KeyboardFocusManager.setCurrentKeyboardFocusManager(null);
      myMocks.close();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    } finally {
      super.tearDown();
    }
  }

  public void testDefault() {
    assertThat(myToolWindow.getToolName()).isEqualTo("PALETTE");
    assertThat(myToolWindow.getDefinition()).isSameAs(myDefinition);
  }

  public void testToolOrder() {
    myToolWindow.setToolOrder(77);
    assertThat(myToolWindow.getToolOrder()).isEqualTo(77);
    myToolWindow.setToolOrder(3);
    assertThat(myToolWindow.getToolOrder()).isEqualTo(3);
  }

  public void testContext() {
    when(myModel.getContext()).thenReturn("Studio");
    assertThat(myToolWindow.getContext()).isEqualTo("Studio");
  }

  public void testContentContext() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assertThat(panel).isNotNull();
    myToolWindow.setContext("Android");
    assertThat(panel.getToolContext()).isEqualTo("Android");
    myToolWindow.setContext("Google");
    assertThat(panel.getToolContext()).isEqualTo("Google");
  }

  public void testDefaultPropertyValues() {
    assertThat(myToolWindow.isLeft()).isTrue();
    assertThat(myToolWindow.isMinimized()).isFalse();
    assertThat(myToolWindow.isSplit()).isFalse();
    assertThat(myToolWindow.isAutoHide()).isFalse();
    assertThat(myToolWindow.isFloating()).isFalse();

    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.LEFT")).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isFalse();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT")).isFalse();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.AUTO_HIDE")).isFalse();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.FLOATING")).isFalse();
  }

  public void testGettersAndSetters() {
    myToolWindow.setLeft(false);
    assertThat(myToolWindow.isLeft()).isFalse();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.LEFT")).isFalse();

    myToolWindow.setMinimized(true);
    assertThat(myToolWindow.isMinimized()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isTrue();

    myToolWindow.setSplit(true);
    assertThat(myToolWindow.isSplit()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT")).isTrue();

    myToolWindow.setAutoHide(true);
    assertThat(myToolWindow.isAutoHide()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.AUTO_HIDE")).isTrue();

    myToolWindow.setFloating(true);
    assertThat(myToolWindow.isFloating()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.FLOATING")).isTrue();
  }

  public void testDefinitionContext() {
    myToolWindow.setMinimized(true);
    assertThat(myToolWindow.isMinimized()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isTrue();

    when(myWorkBench.getContext()).thenReturn("SPLIT");
    myToolWindow.setMinimized(true);
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT.MINIMIZED")).isTrue();
    // Changes to the MINIMIZED property in the SPLIT context should only affect this context
    myToolWindow.setMinimized(false);
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT.MINIMIZED")).isFalse();
    assertThat(myToolWindow.isMinimized()).isFalse();

    // MINIMIZED property in the DEFAULT context remains true
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isTrue();
    when(myWorkBench.getContext()).thenReturn("");
    assertThat(myToolWindow.isMinimized()).isTrue();
  }

  public void testSetPropertyAndUpdateWillNotifyModelAndChangeContent() {
    myToolWindow.setPropertyAndUpdate(PropertyType.LEFT, false);
    assertThat(myToolWindow.isLeft()).isFalse();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.LEFT")).isFalse();
    assertThat(myToolWindow.getContent()).isNotNull();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.LEFT));

    myToolWindow.setPropertyAndUpdate(PropertyType.MINIMIZED, true);
    assertThat(myToolWindow.isMinimized()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isTrue();
    assertThat(myToolWindow.getContent()).isNotNull();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.MINIMIZED));

    myToolWindow.setPropertyAndUpdate(PropertyType.SPLIT, true);
    assertThat(myToolWindow.isSplit()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT")).isTrue();
    assertThat(myToolWindow.getContent()).isNotNull();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.SPLIT));

    myToolWindow.setPropertyAndUpdate(PropertyType.AUTO_HIDE, true);
    assertThat(myToolWindow.isAutoHide()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.AUTO_HIDE")).isTrue();
    assertThat(myToolWindow.getContent()).isNotNull();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.AUTO_HIDE));

    myToolWindow.setPropertyAndUpdate(PropertyType.FLOATING, true);
    assertThat(myToolWindow.isFloating()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.FLOATING")).isTrue();
    assertThat(myToolWindow.getContent()).isNull();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.DETACHED));
  }

  public void testMinimizeDefaultSetInConstructor() {
    assertThat(myToolWindow.isMinimized()).isFalse();

    // Change the workbench context to ensure we're getting a different property, and reset the tool window
    when(myWorkBench.getContext()).thenReturn("testMinimizeDefaultSetInConstructor");
    myToolWindow = new AttachedToolWindow<>(myDefinition, myDragListener, myWorkBench, myModel, true);

    assertThat(myToolWindow.isMinimized()).isTrue();
  }

  public void testMinimizeAutoHideIsNotGlobal() {
    myToolWindow.setAutoHide(true);
    myToolWindow.setMinimized(true);
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isFalse();
    myToolWindow.setMinimized(false);
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isFalse();
    myToolWindow.setMinimized(true);
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isFalse();
  }

  public void testAutoClose() {
    myToolWindow.setAutoHide(true);
    myToolWindow.setMinimized(false);
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assert panel != null;
    panel.closeAutoHideWindow();
    assertThat(myToolWindow.isMinimized()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.MINIMIZED));
  }

  public void testRestore() {
    myToolWindow.setMinimized(true);
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assert panel != null;
    panel.restore();
    assertThat(myToolWindow.isMinimized()).isFalse();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.MINIMIZED));
  }

  public void testRestoreDefaultLayout() {
    myToolWindow.setMinimized(true);
    myToolWindow.setLeft(false);
    myToolWindow.setSplit(true);
    myToolWindow.setAutoHide(true);

    myToolWindow.restoreDefaultLayout();

    assertThat(myToolWindow.isMinimized()).isFalse();
    assertThat(myToolWindow.isLeft()).isTrue();
    assertThat(myToolWindow.isSplit()).isFalse();
    assertThat(myToolWindow.isAutoHide()).isFalse();
  }

  public void testStoreAndRestoreDefaultLayout() {
    myToolWindow.setMinimized(true);
    myToolWindow.setLeft(false);
    myToolWindow.setSplit(true);
    myToolWindow.setAutoHide(true);

    myToolWindow.storeDefaultLayout();

    myToolWindow.setFloating(true);
    myToolWindow.setLeft(true);
    myToolWindow.setSplit(false);
    myToolWindow.setAutoHide(false);

    myToolWindow.restoreDefaultLayout();

    assertThat(myToolWindow.isMinimized()).isTrue();
    assertThat(myToolWindow.isLeft()).isFalse();
    assertThat(myToolWindow.isSplit()).isTrue();
    assertThat(myToolWindow.isAutoHide()).isTrue();
  }

  public void testDraggedEvent() {
    AbstractButton button = myToolWindow.getMinimizedButton();
    button.setSize(20, 50);
    MouseEvent event = new MouseEvent(button, MouseEvent.MOUSE_DRAGGED, 1, InputEvent.BUTTON1_DOWN_MASK, 20, 150, 1, false);
    fireMouseDragged(button, event);
    ArgumentCaptor<AttachedToolWindow.DragEvent> dragEventArgumentCaptor = ArgumentCaptor.forClass(AttachedToolWindow.DragEvent.class);
    verify(myDragListener).buttonDragged(eq(myToolWindow), dragEventArgumentCaptor.capture());
    AttachedToolWindow.DragEvent dragEvent = dragEventArgumentCaptor.getValue();
    assertThat(dragEvent.getDragPoint().x).isEqualTo(20);
    assertThat(dragEvent.getDragPoint().y).isEqualTo(150);
    assertThat(dragEvent.getMousePoint().x).isEqualTo(20);
    assertThat(dragEvent.getMousePoint().y).isEqualTo(150);
    assertThat(dragEvent.getDragImage()).isInstanceOf(JLabel.class);
    assertThat(((JLabel)dragEvent.getDragImage()).getIcon()).isNotNull();
  }

  public void testDropEvent() {
    AbstractButton button = myToolWindow.getMinimizedButton();
    button.setSize(20, 50);
    MouseEvent event1 = new MouseEvent(button, MouseEvent.MOUSE_DRAGGED, 1, InputEvent.BUTTON1_DOWN_MASK, 20, 150, 1, false);
    fireMouseDragged(button, event1);
    MouseEvent event2 = new MouseEvent(button, MouseEvent.MOUSE_RELEASED, 1, InputEvent.BUTTON1_DOWN_MASK, 800, 450, 1, false);
    fireMouseReleased(button, event2);
    ArgumentCaptor<AttachedToolWindow.DragEvent> dragEventArgumentCaptor = ArgumentCaptor.forClass(AttachedToolWindow.DragEvent.class);
    verify(myDragListener).buttonDropped(eq(myToolWindow), dragEventArgumentCaptor.capture());
    AttachedToolWindow.DragEvent dragEvent = dragEventArgumentCaptor.getValue();
    assertThat(dragEvent.getDragPoint().x).isEqualTo(20);
    assertThat(dragEvent.getDragPoint().y).isEqualTo(150);
    assertThat(dragEvent.getMousePoint().x).isEqualTo(800);
    assertThat(dragEvent.getMousePoint().y).isEqualTo(450);
    assertThat(dragEvent.getDragImage()).isInstanceOf(JLabel.class);
    assertThat(((JLabel)dragEvent.getDragImage()).getIcon()).isNotNull();
  }

  public void testButtonClickTogglesMinimizedState() {
    myToolWindow.setMinimized(false);
    AbstractButton button = myToolWindow.getMinimizedButton();

    MouseEvent event1 = new MouseEvent(button, MouseEvent.MOUSE_CLICKED, 1, InputEvent.BUTTON1_DOWN_MASK, 20, 150, 1, false);
    fireMouseClicked(button, event1);
    assertThat(myToolWindow.isMinimized()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.MINIMIZED));

    MouseEvent event2 = new MouseEvent(button, MouseEvent.MOUSE_CLICKED, 1, InputEvent.BUTTON1_DOWN_MASK, 20, 150, 1, false);
    fireMouseClicked(button, event2);
    assertThat(myToolWindow.isMinimized()).isFalse();
    verify(myModel, times(2)).update(eq(myToolWindow), eq(PropertyType.MINIMIZED));
  }

  public void testAddedGearActionFromButtonRightClick() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assert panel != null;

    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), "GearAction");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(panel.isGearActionPerformed()).isTrue();
  }

  public void testSelectLeftTopFromButtonRightClick() {
    testSetLocationFromButtonRightClick(AttachedLocation.LeftTop);
  }

  public void testSelectLeftBottomFromButtonRightClick() {
    testSetLocationFromButtonRightClick(AttachedLocation.LeftBottom);
  }

  public void testSelectRightTopFromButtonRightClick() {
    testSetLocationFromButtonRightClick(AttachedLocation.RightTop);
  }

  public void testSelectRightBottomFromButtonRightClick() {
    testSetLocationFromButtonRightClick(AttachedLocation.RightBottom);
  }

  private void testSetLocationFromButtonRightClick(@NotNull AttachedLocation location) {
    myToolWindow.setLeft(!location.isLeft());
    myToolWindow.setSplit(!location.isBottom());

    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), location.getTitle());
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isLeft()).isEqualTo(location.isLeft());
    assertThat(myToolWindow.isSplit()).isEqualTo(location.isBottom());
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.SPLIT));
  }

  public void testSelectLeftFromButtonRightClick() {
    myDefinition = PalettePanelToolContent.getBasicDefinition();
    myToolWindow = new AttachedToolWindow<>(myDefinition, myDragListener, myWorkBench, myModel, false);

    myToolWindow.setLeft(false);
    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), "Left");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isLeft()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.LEFT));
  }

  public void testSelectRightFromButtonRightClick() {
    myDefinition = PalettePanelToolContent.getBasicDefinition();
    myToolWindow = new AttachedToolWindow<>(myDefinition, myDragListener, myWorkBench, myModel, false);

    myToolWindow.setLeft(true);
    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), "Right");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isLeft()).isFalse();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.LEFT));
  }

  public void testSelectSwapFromButtonRightClick() {
    myToolWindow.setLeft(true);

    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), "Swap");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    verify(myModel).swap();
  }

  public void testToggleDockModeFromButtonRightClick() {
    myToolWindow.setAutoHide(false);

    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), InternalDecorator.TOGGLE_DOCK_MODE_ACTION_ID);
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isAutoHide()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.AUTO_HIDE));

    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isAutoHide()).isFalse();
    verify(myModel, times(2)).update(eq(myToolWindow), eq(PropertyType.AUTO_HIDE));
  }

  public void testToggleFloatingModeFromButtonRightClick() {
    myToolWindow.setFloating(false);

    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), InternalDecorator.TOGGLE_FLOATING_MODE_ACTION_ID);
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isFloating()).isTrue();
    assertThat(myToolWindow.isDetached()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.DETACHED));

    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isFloating()).isFalse();
    assertThat(myToolWindow.isDetached()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.DETACHED));
  }

  public void testToggleAttachedModeFromButtonRightClick() {
    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), "None");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isDetached()).isTrue();
    assertThat(myToolWindow.isFloating()).isFalse();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.DETACHED));
  }

  public void testHideFromButtonInHeader() {
    myToolWindow.setFloating(false);

    ActionButton button = findButtonByName(myToolWindow.getComponent(), "Hide");
    assertThat(button).isNotNull();
    button.click();

    assertThat(myToolWindow.isMinimized()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.MINIMIZED));
  }

  public void testAdditionalActionFromButtonInHeader() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assert panel != null;

    ActionButton button = findButtonByName(myToolWindow.getComponent(), "AdditionalAction");
    assertThat(button).isNotNull();
    button.click();

    assertThat(panel.isAdditionalActionPerformed()).isTrue();
  }

  public void testSelectLeftTopFromGearButtonInHeader() {
    testSetLocationFromGearButtonInHeader(AttachedLocation.LeftTop);
  }

  public void testSelectLeftBottomFromGearButtonInHeader() {
    testSetLocationFromGearButtonInHeader(AttachedLocation.LeftBottom);
  }

  public void testSelectRightTopFromGearButtonInHeader() {
    testSetLocationFromGearButtonInHeader(AttachedLocation.RightTop);
  }

  public void testSelectRightBottomFromGearButtonInHeader() {
    testSetLocationFromGearButtonInHeader(AttachedLocation.RightBottom);
  }

  private void testSetLocationFromGearButtonInHeader(@NotNull AttachedLocation location) {
    myToolWindow.setLeft(!location.isLeft());
    myToolWindow.setSplit(!location.isBottom());

    AnAction action = findActionWithName(getPopupMenuFromGearButtonInHeader(), location.getTitle());
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isLeft()).isEqualTo(location.isLeft());
    assertThat(myToolWindow.isSplit()).isEqualTo(location.isBottom());
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.SPLIT));
  }

  public void testSelectLeftFromGearButtonInHeader() {
    myDefinition = PalettePanelToolContent.getBasicDefinition();
    myToolWindow = new AttachedToolWindow<>(myDefinition, myDragListener, myWorkBench, myModel, false);

    myToolWindow.setLeft(false);
    AnAction action = findActionWithName(getPopupMenuFromGearButtonInHeader(), "Left");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isLeft()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.LEFT));
  }

  public void testSelectRightFromGearButtonInHeader() {
    myDefinition = PalettePanelToolContent.getBasicDefinition();
    myToolWindow = new AttachedToolWindow<>(myDefinition, myDragListener, myWorkBench, myModel, false);

    myToolWindow.setLeft(true);
    AnAction action = findActionWithName(getPopupMenuFromGearButtonInHeader(), "Right");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isLeft()).isFalse();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.LEFT));
  }

  public void testSelectSwapFromGearButtonInHeader() {
    myToolWindow.setLeft(true);

    AnAction action = findActionWithName(getPopupMenuFromGearButtonInHeader(), "Swap");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    verify(myModel).swap();
  }

  public void testToggleDockModeFromGearButtonInHeader() {
    myToolWindow.setAutoHide(false);

    AnAction action = findActionWithName(getPopupMenuFromGearButtonInHeader(), InternalDecorator.TOGGLE_DOCK_MODE_ACTION_ID);
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isAutoHide()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.AUTO_HIDE));

    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isAutoHide()).isFalse();
    verify(myModel, times(2)).update(eq(myToolWindow), eq(PropertyType.AUTO_HIDE));
  }

  public void testToggleFloatingModeFromGearButtonInHeader() {
    myToolWindow.setFloating(false);

    AnAction action = findActionWithName(getPopupMenuFromGearButtonInHeader(), InternalDecorator.TOGGLE_FLOATING_MODE_ACTION_ID);
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isFloating()).isTrue();
    assertThat(myToolWindow.isDetached()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.DETACHED));

    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isFloating()).isFalse();
    assertThat(myToolWindow.isDetached()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.FLOATING));
  }

  public void testSearchButtonInHeader() {
    JLabel header = findHeaderLabel(myToolWindow.getComponent());
    assertThat(header.isVisible()).isTrue();
    SearchTextField searchField = findHeaderSearchField(myToolWindow.getComponent());
    assertThat(searchField.isVisible()).isFalse();

    ActionButton button = findRequiredButtonByName(myToolWindow.getComponent(), "Search");
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assertThat(panel).isNotNull();
    panel.setFilteringActive(false);
    button.update();
    assertThat(button.isEnabled()).isFalse();
    panel.setFilteringActive(true);
    button.update();
    assertThat(button.isEnabled()).isTrue();

    button.click();

    assertThat(header.isVisible()).isFalse();
    assertThat(searchField.isVisible()).isTrue();

    fireFocusLost(searchField.getTextEditor());

    assertThat(header.isVisible()).isTrue();
    assertThat(searchField.isVisible()).isFalse();
  }

  public void testSearchTextChangesAreSentToContent() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assertThat(panel).isNotNull();
    findRequiredButtonByName(myToolWindow.getComponent(), "Search").click();

    SearchTextField searchField = findHeaderSearchField(myToolWindow.getComponent());
    searchField.setText("el");

    assertThat(panel.getFilter()).isEqualTo("el");
  }

  public void testAcceptedSearchesAreStoredInHistory() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assertThat(panel).isNotNull();
    findRequiredButtonByName(myToolWindow.getComponent(), "Search").click();
    SearchTextField searchField = findHeaderSearchField(myToolWindow.getComponent());
    searchField.setText("ele");
    searchField.setText("eleva");
    fireFocusLost(searchField.getTextEditor());
    searchField.setText("vi");
    searchField.setText("visible");
    fireFocusLost(searchField.getTextEditor());
    searchField.setText("con");
    searchField.setText("contex");
    fireFocusLost(searchField.getTextEditor());

    assertThat(myPropertiesComponent.getValue(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.TEXT_SEARCH_HISTORY"))
      .isEqualTo("contex\nvisible\neleva");
  }

  public void testStartSearching() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assert panel != null;
    panel.startFiltering('b');

    SearchTextField searchField = findHeaderSearchField(myToolWindow.getComponent());
    assertThat(searchField.isVisible()).isTrue();
    assertThat(searchField.getText()).isEqualTo("b");
  }

  public void testStopSearching() {
    findRequiredButtonByName(myToolWindow.getComponent(), "Search").click();
    SearchTextField searchField = findHeaderSearchField(myToolWindow.getComponent());
    searchField.setText("width");

    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assert panel != null;
    panel.stopFiltering();
    assertThat(searchField.isVisible()).isFalse();
    assertThat(searchField.getText()).isEqualTo("");
  }

  public void testEscapeClosesSearchFieldIfTextIsEmpty() {
    findRequiredButtonByName(myToolWindow.getComponent(), "Search").click();
    SearchTextField searchField = findHeaderSearchField(myToolWindow.getComponent());
    searchField.setText("");
    fireKey(searchField.getTextEditor(), KeyEvent.VK_ESCAPE);
    assertThat(searchField.isVisible()).isFalse();
  }

  public void testContentIsDisposed() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assert panel != null;
    Disposer.dispose(myToolWindow);
    myToolWindow = null;
    assertThat(panel.isDisposed()).isTrue();
  }

  public void testDefaultValueDoesNotOverrideActualValue() {
    myToolWindow.setDefaultProperty(PropertyType.SPLIT, false);
    myToolWindow.setProperty(PropertyType.SPLIT, true);
    myToolWindow.setDefaultProperty(PropertyType.SPLIT, false);
    assertThat(myToolWindow.getProperty(PropertyType.SPLIT)).isTrue();

    myToolWindow.setDefaultProperty(PropertyType.SPLIT, true);
    myToolWindow.setProperty(PropertyType.SPLIT, false);
    myToolWindow.setDefaultProperty(PropertyType.SPLIT, true);
    assertThat(myToolWindow.getProperty(PropertyType.SPLIT)).isFalse();
  }

  public void testCommandFStartsFiltering() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assertThat(panel).isNotNull();
    when(myKeyboardFocusManager.getFocusOwner()).thenReturn(panel.getComponent());
    IdeKeyEventDispatcher dispatcher = new IdeKeyEventDispatcher(null);
    dispatcher.dispatchKeyEvent(
      new KeyEvent(panel.getComponent(), KeyEvent.KEY_PRESSED, 0, ourCommandF.getModifiers(), ourCommandF.getKeyCode(), 'F'));
    assertThat(Objects.requireNonNull(myToolWindow.getSearchField()).isVisible()).isTrue();
  }

  public void testActionsEnabledAtStartup() {
    DumbModeTestUtils.runInDumbModeSynchronously(getProject(), () -> {
      ActionButton button = findRequiredButtonByName(myToolWindow.getComponent(), "More Options");
      myToolWindow.updateActions();
      assertThat(button.isEnabled()).isTrue();
    });
  }

  private static void fireFocusLost(@NotNull JComponent component) {
    for (FocusListener listener : component.getFocusListeners()) {
      listener.focusLost(new FocusEvent(component, FocusEvent.FOCUS_LOST));
    }
  }

  private static void fireMouseDragged(@NotNull JComponent component, @NotNull MouseEvent event) {
    for (MouseMotionListener listener : component.getMouseMotionListeners()) {
      listener.mouseDragged(event);
    }
  }

  private static void fireMouseReleased(@NotNull JComponent component, @NotNull MouseEvent event) {
    for (MouseListener listener : component.getMouseListeners()) {
      listener.mouseReleased(event);
    }
  }

  private static void fireMouseClicked(@NotNull JComponent component, @NotNull MouseEvent event) {
    for (MouseListener listener : component.getMouseListeners()) {
      listener.mouseClicked(event);
    }
  }

  @SuppressWarnings("SameParameterValue")
  private static void fireKey(@NotNull JComponent component, int keyCode) {
    KeyEvent event = new KeyEvent(component, 0, 0, 0, keyCode, '\0');
    for (KeyListener listener : component.getKeyListeners()) {
      listener.keyPressed(event);
    }
    for (KeyListener listener : component.getKeyListeners()) {
      listener.keyTyped(event);
    }
    for (KeyListener listener : component.getKeyListeners()) {
      listener.keyReleased(event);
    }
  }

  private DefaultActionGroup getPopupMenuFromButtonRightClick() {
    AbstractButton button = myToolWindow.getMinimizedButton();

    MouseEvent event1 = new MouseEvent(button, MouseEvent.MOUSE_CLICKED, 1, InputEvent.META_DOWN_MASK, 20, 150, 1, false);
    fireMouseClicked(button, event1);

    ArgumentCaptor<ActionGroup> menuCaptor = ArgumentCaptor.forClass(ActionGroup.class);
    verify(myActionManager).createActionPopupMenu(eq(ActionPlaces.TOOLWINDOW_POPUP), menuCaptor.capture());
    return (DefaultActionGroup)menuCaptor.getValue();
  }

  @Nullable
  private static AnAction findActionWithName(@NotNull DefaultActionGroup group, @NotNull String name) {
    for (AnAction action : group.getChildActionsOrStubs()) {
      if (name.equals(action.getTemplatePresentation().getText())) {
        return action;
      }
      if (action instanceof DefaultActionGroup) {
        AnAction childAction = findActionWithName((DefaultActionGroup)action, name);
        if (childAction != null) {
          return childAction;
        }
      }
    }
    return null;
  }

  private static JLabel findHeaderLabel(@NotNull Container container) {
    return findFirstComponentOfClass(container, JLabel.class);
  }

  private static SearchTextField findHeaderSearchField(@NotNull Container container) {
    return findFirstComponentOfClass(container, SearchTextField.class);
  }

  private static <T> T findFirstComponentOfClass(@NotNull Container container, @NotNull Class<T> klass) {
    for (Component component : container.getComponents()) {
      if (klass.isInstance(component)) {
        //noinspection unchecked
        return (T)component;
      }
      if (component instanceof Container) {
        T t = findFirstComponentOfClass((Container)component, klass);
        if (t != null) {
          return t;
        }
      }
    }
    return null;
  }

  private DefaultActionGroup getPopupMenuFromGearButtonInHeader() {
    ActionButton button = findRequiredButtonByName(myToolWindow.getComponent(), "More Options");
    button.click();

    ArgumentCaptor<ActionGroup> menuCaptor = ArgumentCaptor.forClass(ActionGroup.class);
    verify(myActionManager).createActionPopupMenu(eq(ActionPlaces.TOOLWINDOW_POPUP), menuCaptor.capture());
    return (DefaultActionGroup)menuCaptor.getValue();
  }

  @NotNull
  private static ActionButton findRequiredButtonByName(@NotNull Container container, @NotNull String name) {
    ActionButton button = findButtonByName(container, name);
    assertThat(button).isNotNull();
    return button;
  }

  @Nullable
  private static ActionButton findButtonByName(@NotNull Container container, @NotNull String name) {
    for (Component component : container.getComponents()) {
      if (component instanceof ActionButton) {
        ActionButton button = (ActionButton)component;
        AnAction action = button.getAction();
        if (name.equals(action.getTemplatePresentation().getText())) {
          return button;
        }
      }

      if (component instanceof ActionToolbar) {
        ((ActionToolbar)component).updateActionsImmediately();
      }

      if (component instanceof Container) {
        ActionButton button = findButtonByName((Container)component, name);
        if (button != null) {
          return button;
        }
      }
    }
    return null;
  }

  private static AnActionEvent createActionEvent(@NotNull AnAction action) {
    DataContext dataContext = mock(DataContext.class);
    return new AnActionEvent(null, dataContext, TOOL_WINDOW_TOOLBAR_PLACE, action.getTemplatePresentation().clone(),
                             ActionManager.getInstance(), 0);
  }

  private static class SomeAction extends AnAction {
    private SomeAction(@NotNull String title) {
      super(title);
      if (title.equals(IdeActions.ACTION_FIND)) {
        registerCustomShortcutSet(new CustomShortcutSet(ourCommandF), null);
      }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
    }
  }
}
