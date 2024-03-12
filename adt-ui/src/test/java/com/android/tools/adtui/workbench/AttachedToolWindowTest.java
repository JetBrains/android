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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.android.annotations.Nullable;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.swing.FakeKeyboardFocusManager;
import com.android.tools.adtui.swing.popup.JBPopupRule;
import com.android.tools.adtui.workbench.AttachedToolWindow.PropertyType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.DisposableRule;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.ProjectRule;
import com.intellij.testFramework.RuleChain;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.ui.SearchTextField;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

public class AttachedToolWindowTest {
  private final ProjectRule myProjectRule = new ProjectRule();
  private final DisposableRule myDisposableRule = new DisposableRule();
  private final JBPopupRule myPopupRule = new JBPopupRule();

  @Rule
  public RuleChain chain = new RuleChain(myProjectRule, myDisposableRule, myPopupRule);

  @Mock private AttachedToolWindow.ButtonDragListener<String> myDragListener;
  @Mock private SideModel<String> myModel;

  private static final KeyStroke ourCommandF = KeyStroke.getKeyStroke(KeyEvent.VK_F, AdtUiUtils.getActionMask());
  private WorkBench<String> myWorkBench;
  private PropertiesComponent myPropertiesComponent;
  private ToolWindowDefinition<String> myDefinition;
  private AttachedToolWindow<String> myToolWindow;
  private AutoCloseable myMocks;

  @Before
  public void setUp() throws Exception {
    myMocks = openMocks(this);
    Project project = myProjectRule.getProject();
    Application application = ApplicationManager.getApplication();
    Disposable disposable = myDisposableRule.getDisposable();
    myPropertiesComponent = new PropertiesComponentMock();
    ServiceContainerUtil.replaceService(application, PropertiesComponent.class, myPropertiesComponent, disposable);
    myDefinition = PalettePanelToolContent.getDefinition();
    EdtTestUtil.runInEdtAndWait(() -> {
      myWorkBench = new WorkBench<>(project, "DESIGNER", null, disposable, 0);
      myToolWindow = new AttachedToolWindow<>(myDefinition, myDragListener, myWorkBench, myModel, false);
    });
  }

  @After
  public void tearDown() throws Exception {
    myMocks.close();
  }

  @Test
  public void testDefault() {
    assertThat(myToolWindow.getToolName()).isEqualTo("PALETTE");
    assertThat(myToolWindow.getDefinition()).isSameAs(myDefinition);
  }

  @Test
  public void testToolOrder() {
    myToolWindow.setToolOrder(77);
    assertThat(myToolWindow.getToolOrder()).isEqualTo(77);
    myToolWindow.setToolOrder(3);
    assertThat(myToolWindow.getToolOrder()).isEqualTo(3);
  }

  @Test
  public void testContext() {
    when(myModel.getContext()).thenReturn("Studio");
    assertThat(myToolWindow.getContext()).isEqualTo("Studio");
  }

  @Test
  public void testContentContext() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assertThat(panel).isNotNull();
    myToolWindow.setContext("Android");
    assertThat(panel.getToolContext()).isEqualTo("Android");
    myToolWindow.setContext("Google");
    assertThat(panel.getToolContext()).isEqualTo("Google");
  }

  @Test
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

  @Test
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

  @Test
  public void testDefinitionContext() {
    myToolWindow.setMinimized(true);
    assertThat(myToolWindow.isMinimized()).isTrue();
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isTrue();

    myWorkBench.setContext("SPLIT");
    myToolWindow.setMinimized(true);
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT.MINIMIZED")).isTrue();
    // Changes to the MINIMIZED property in the SPLIT context should only affect this context
    myToolWindow.setMinimized(false);
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.SPLIT.MINIMIZED")).isFalse();
    assertThat(myToolWindow.isMinimized()).isFalse();

    // MINIMIZED property in the DEFAULT context remains true
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isTrue();
    myWorkBench.setContext("");
    assertThat(myToolWindow.isMinimized()).isTrue();
  }

  @Test
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

  @Test
  public void testMinimizeDefaultSetInConstructor() {
    assertThat(myToolWindow.isMinimized()).isFalse();

    // Change the workbench context to ensure we're getting a different property, and reset the tool window
    myWorkBench.setContext("testMinimizeDefaultSetInConstructor");
    myToolWindow = new AttachedToolWindow<>(myDefinition, myDragListener, myWorkBench, myModel, true);
    assertThat(myToolWindow.isMinimized()).isTrue();
  }

  @Test
  public void testMinimizeAutoHideIsNotGlobal() {
    myToolWindow.setAutoHide(true);
    myToolWindow.setMinimized(true);
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isFalse();
    myToolWindow.setMinimized(false);
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isFalse();
    myToolWindow.setMinimized(true);
    assertThat(myPropertiesComponent.getBoolean(TOOL_WINDOW_PROPERTY_PREFIX + "DESIGNER.PALETTE.MINIMIZED")).isFalse();
  }

  @Test
  public void testAutoClose() {
    myToolWindow.setAutoHide(true);
    myToolWindow.setMinimized(false);
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assert panel != null;
    panel.closeAutoHideWindow();
    assertThat(myToolWindow.isMinimized()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.MINIMIZED));
  }

  @Test
  public void testRestore() {
    myToolWindow.setMinimized(true);
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assert panel != null;
    panel.restore();
    assertThat(myToolWindow.isMinimized()).isFalse();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.MINIMIZED));
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testAddedGearActionFromButtonRightClick() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assert panel != null;

    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), "GearAction");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(panel.isGearActionPerformed()).isTrue();
  }

  @Test
  public void testSelectLeftTopFromButtonRightClick() {
    testSetLocationFromButtonRightClick(AttachedLocation.LeftTop);
  }

  @Test
  public void testSelectLeftBottomFromButtonRightClick() {
    testSetLocationFromButtonRightClick(AttachedLocation.LeftBottom);
  }

  @Test
  public void testSelectRightTopFromButtonRightClick() {
    testSetLocationFromButtonRightClick(AttachedLocation.RightTop);
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testSelectSwapFromButtonRightClick() {
    myToolWindow.setLeft(true);

    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), "Swap");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    verify(myModel).swap();
  }

  @Test
  public void testToggleDockModeFromButtonRightClick() {
    myToolWindow.setAutoHide(false);

    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), "Docked Mode");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isAutoHide()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.AUTO_HIDE));

    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isAutoHide()).isFalse();
    verify(myModel, times(2)).update(eq(myToolWindow), eq(PropertyType.AUTO_HIDE));
  }

  @Test
  public void testToggleFloatingModeFromButtonRightClick() {
    myToolWindow.setFloating(false);

    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), "Floating Mode");
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

  @Test
  public void testToggleAttachedModeFromButtonRightClick() {
    AnAction action = findActionWithName(getPopupMenuFromButtonRightClick(), "None");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isDetached()).isTrue();
    assertThat(myToolWindow.isFloating()).isFalse();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.DETACHED));
  }

  @Test
  public void testHideFromButtonInHeader() {
    myToolWindow.setFloating(false);

    ActionButton button = findButtonByName(myToolWindow.getComponent(), "Hide");
    assertThat(button).isNotNull();
    button.click();

    assertThat(myToolWindow.isMinimized()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.MINIMIZED));
  }

  @Test
  public void testAdditionalActionFromButtonInHeader() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assert panel != null;

    ActionButton button = findButtonByName(myToolWindow.getComponent(), "AdditionalAction");
    assertThat(button).isNotNull();
    button.click();

    assertThat(panel.isAdditionalActionPerformed()).isTrue();
  }

  @Test
  public void testSelectLeftTopFromGearButtonInHeader() {
    testSetLocationFromGearButtonInHeader(AttachedLocation.LeftTop);
  }

  @Test
  public void testSelectLeftBottomFromGearButtonInHeader() {
    testSetLocationFromGearButtonInHeader(AttachedLocation.LeftBottom);
  }

  @Test
  public void testSelectRightTopFromGearButtonInHeader() {
    testSetLocationFromGearButtonInHeader(AttachedLocation.RightTop);
  }

  @Test
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

  @Test
  public void testSelectLeftFromGearButtonInHeader() {
    myDefinition = PalettePanelToolContent.getBasicDefinition();
    myToolWindow = EdtTestUtil.runInEdtAndGet(() -> new AttachedToolWindow<>(myDefinition, myDragListener, myWorkBench, myModel, false));

    myToolWindow.setLeft(false);
    AnAction action = findActionWithName(getPopupMenuFromGearButtonInHeader(), "Left");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isLeft()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.LEFT));
  }

  @Test
  public void testSelectRightFromGearButtonInHeader() {
    myDefinition = PalettePanelToolContent.getBasicDefinition();
    myToolWindow = EdtTestUtil.runInEdtAndGet(() -> new AttachedToolWindow<>(myDefinition, myDragListener, myWorkBench, myModel, false));
    myToolWindow.setLeft(true);
    AnAction action = findActionWithName(getPopupMenuFromGearButtonInHeader(), "Right");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isLeft()).isFalse();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.LEFT));
  }

  @Test
  public void testSelectSwapFromGearButtonInHeader() {
    myToolWindow.setLeft(true);

    AnAction action = findActionWithName(getPopupMenuFromGearButtonInHeader(), "Swap");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    verify(myModel).swap();
  }

  @Test
  public void testToggleDockModeFromGearButtonInHeader() {
    myToolWindow.setAutoHide(false);

    AnAction action = findActionWithName(getPopupMenuFromGearButtonInHeader(), "Docked Mode");
    assertThat(action).isNotNull();
    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isAutoHide()).isTrue();
    verify(myModel).update(eq(myToolWindow), eq(PropertyType.AUTO_HIDE));

    action.actionPerformed(createActionEvent(action));

    assertThat(myToolWindow.isAutoHide()).isFalse();
    verify(myModel, times(2)).update(eq(myToolWindow), eq(PropertyType.AUTO_HIDE));
  }

  @Test
  public void testToggleFloatingModeFromGearButtonInHeader() {
    myToolWindow.setFloating(false);

    AnAction action = findActionWithName(getPopupMenuFromGearButtonInHeader(), "Floating Mode");
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

  @Test
  public void testSearchButtonInHeader() {
    JLabel header = findHeaderLabel(myToolWindow.getComponent());
    assertThat(header.isVisible()).isTrue();
    SearchTextField searchField = findHeaderSearchField(myToolWindow.getComponent());
    assertThat(searchField.isVisible()).isFalse();

    ActionButton button = findRequiredButtonByName(myToolWindow.getComponent(), "Search");
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assertThat(panel).isNotNull();
    panel.setFilteringActive(false);
    ActionToolbar toolbar = ActionToolbar.findToolbarBy(button);
    if (toolbar != null) {
      EdtTestUtil.runInEdtAndWait(toolbar::updateActionsImmediately);
    }
    assertThat(button.isEnabled()).isFalse();
    panel.setFilteringActive(true);
    if (toolbar != null) {
      EdtTestUtil.runInEdtAndWait(toolbar::updateActionsImmediately);
    }
    assertThat(button.isEnabled()).isTrue();

    button.click();

    assertThat(header.isVisible()).isFalse();
    assertThat(searchField.isVisible()).isTrue();

    fireFocusLost(searchField.getTextEditor());

    assertThat(header.isVisible()).isTrue();
    assertThat(searchField.isVisible()).isFalse();
  }

  @Test
  public void testSearchTextChangesAreSentToContent() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assertThat(panel).isNotNull();
    findRequiredButtonByName(myToolWindow.getComponent(), "Search").click();

    SearchTextField searchField = findHeaderSearchField(myToolWindow.getComponent());
    searchField.setText("el");

    assertThat(panel.getFilter()).isEqualTo("el");
  }

  @Test
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

  @Test
  public void testStartSearching() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assert panel != null;
    panel.startFiltering('b');

    SearchTextField searchField = findHeaderSearchField(myToolWindow.getComponent());
    assertThat(searchField.isVisible()).isTrue();
    assertThat(searchField.getText()).isEqualTo("b");
  }

  @Test
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

  @Test
  public void testEscapeClosesSearchFieldIfTextIsEmpty() {
    findRequiredButtonByName(myToolWindow.getComponent(), "Search").click();
    SearchTextField searchField = findHeaderSearchField(myToolWindow.getComponent());
    searchField.setText("");
    fireKey(searchField.getTextEditor(), KeyEvent.VK_ESCAPE);
    assertThat(searchField.isVisible()).isFalse();
  }

  @Test
  public void testContentIsDisposed() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assert panel != null;
    Disposer.dispose(myToolWindow);
    myToolWindow = null;
    assertThat(panel.isDisposed()).isTrue();
  }

  @Test
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

  @Test
  public void testCommandFStartsFiltering() {
    PalettePanelToolContent panel = (PalettePanelToolContent)myToolWindow.getContent();
    assertThat(panel).isNotNull();
    JComponent component = panel.getComponent();
    FakeKeyboardFocusManager focusManager = new FakeKeyboardFocusManager(myDisposableRule.getDisposable());
    focusManager.setFocusOwner(component);

    IdeKeyEventDispatcher dispatcher = new IdeKeyEventDispatcher(null);
    EdtTestUtil.runInEdtAndWait(() -> dispatcher.dispatchKeyEvent(
      new KeyEvent(component, KeyEvent.KEY_PRESSED, 0, ourCommandF.getModifiers(), ourCommandF.getKeyCode(), 'F')
    ));
    assertThat(Objects.requireNonNull(myToolWindow.getSearchField()).isVisible()).isTrue();
  }

  @Test
  public void testActionsEnabledAtStartup() {
    DumbModeTestUtils.runInDumbModeSynchronously(myProjectRule.getProject(), () -> {
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

  private List<AnAction> getPopupMenuFromButtonRightClick() {
    AbstractButton button = myToolWindow.getMinimizedButton();

    MouseEvent event1 = new MouseEvent(button, MouseEvent.MOUSE_CLICKED, 1, InputEvent.META_DOWN_MASK, 20, 150, 1, false);
    fireMouseClicked(button, event1);

    return myPopupRule.getFakePopupFactory().getNextPopup().getActions();
  }

  @Nullable
  private static AnAction findActionWithName(@NotNull List<AnAction> actions, @NotNull String name) {
    for (AnAction action : actions) {
      if (name.equals(action.getTemplatePresentation().getText())) {
        return action;
      }
      if (action instanceof DefaultActionGroup) {
        DefaultActionGroup group = (DefaultActionGroup)action;
        AnAction childAction = findActionWithName(Arrays.asList(group.getChildActionsOrStubs()), name);
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

  private List<AnAction> getPopupMenuFromGearButtonInHeader() {
    ActionButton button = findRequiredButtonByName(myToolWindow.getComponent(), "More Options");
    EdtTestUtil.runInEdtAndWait(button::click);

    return myPopupRule.getFakePopupFactory().getNextPopup().getActions();
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
