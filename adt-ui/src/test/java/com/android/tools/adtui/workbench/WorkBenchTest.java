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
import static com.android.tools.adtui.workbench.PalettePanelToolContent.MIN_TOOL_WIDTH;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.workbench.AttachedToolWindow.DragEvent;
import com.android.tools.adtui.workbench.AttachedToolWindow.PropertyType;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

public class WorkBenchTest extends WorkBenchTestCase {
  @Mock
  private WorkBenchManager myWorkBenchManager;
  @Mock
  private FileEditorManagerEx myFileEditorManager;
  @Mock
  private DetachedToolWindowManager myFloatingToolWindowManager;
  @Mock
  private FileEditor myFileEditor;
  @Mock
  private FileEditor myFileEditor2;

  private JComponent myContent;
  private ThreeComponentsSplitter mySplitter;
  private PropertiesComponent myPropertiesComponent;
  private WorkBench<String> myWorkBench;
  private SideModel<String> myModel;
  private MinimizedPanel<String> myLeftMinimizePanel;
  private MinimizedPanel<String> myRightMinimizePanel;
  private AttachedToolWindow<String> myToolWindow1;
  private AttachedToolWindow<String> myToolWindow2;
  private AttachedToolWindow<String> myToolWindow3;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    registerApplicationService(WorkBenchManager.class, myWorkBenchManager);
    registerApplicationService(PropertiesComponent.class, new PropertiesComponentMock());
    when(myFileEditorManager.getSelectedEditors()).thenReturn(FileEditor.EMPTY_ARRAY);
    when(myFileEditorManager.getOpenFiles()).thenReturn(VirtualFile.EMPTY_ARRAY);
    //noinspection UnstableApiUsage
    when(myFileEditorManager.getOpenFilesWithRemotes()).thenReturn(Collections.emptyList());
    when(myFileEditorManager.getAllEditors()).thenReturn(FileEditor.EMPTY_ARRAY);
    registerProjectService(FileEditorManager.class, myFileEditorManager);
    myContent = new JPanel();
    myContent.setPreferredSize(new Dimension(500, 400));
    mySplitter = new ThreeComponentsSplitter(getTestRootDisposable());
    myPropertiesComponent = PropertiesComponent.getInstance();
    myModel = new SideModel<>(getProject());
    myLeftMinimizePanel = spy(new MinimizedPanel<>(Side.RIGHT, myModel));
    myLeftMinimizePanel.setLayout(new BoxLayout(myLeftMinimizePanel, BoxLayout.Y_AXIS));
    myRightMinimizePanel = spy(new MinimizedPanel<>(Side.RIGHT, myModel));
    myRightMinimizePanel.setLayout(new BoxLayout(myRightMinimizePanel, BoxLayout.Y_AXIS));
    WorkBench.InitParams<String> initParams = new WorkBench.InitParams<>(myModel, mySplitter, myLeftMinimizePanel, myRightMinimizePanel);
    mySplitter.setSize(1000, 600);
    myWorkBench = new WorkBench<>(getProject(), "BENCH", myFileEditor, initParams, myFloatingToolWindowManager, 1000);
    JRootPane rootPane = new JRootPane();
    rootPane.add(myWorkBench);
    List<ToolWindowDefinition<String>> definitions = ImmutableList.of(PalettePanelToolContent.getDefinition(),
                                                                      PalettePanelToolContent.getOtherDefinition(),
                                                                      PalettePanelToolContent.getThirdDefinition());
    when(myFileEditorManager.getSelectedEditors()).thenReturn(new FileEditor[]{myFileEditor, myFileEditor2});
    myWorkBench.init(myContent, "CONTEXT", definitions, false);
    myToolWindow1 = myModel.getAllTools().get(0);
    myToolWindow2 = myModel.getAllTools().get(1);
    myToolWindow3 = myModel.getAllTools().get(2);
    verify(myWorkBenchManager).register(eq(myWorkBench));
    reset(myWorkBenchManager, myFloatingToolWindowManager);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      Disposer.dispose(myWorkBench);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testUpdateEditor() {
    myWorkBench.setFileEditor(myFileEditor2);
    verify(myFloatingToolWindowManager).unregister(eq(myFileEditor));
    verify(myFloatingToolWindowManager).register(eq(myFileEditor2), eq(myWorkBench));
    verify(myFloatingToolWindowManager).updateToolWindowsForWorkBench(myWorkBench);
  }

  public void testSetContext() {
    assertThat(myModel.getContext()).isEqualTo("CONTEXT");
    myWorkBench.setToolContext("Google");
    assertThat(myModel.getContext()).isEqualTo("Google");
  }

  public void testToolWindowDefaultUpdatesOnContextChange() {
    // Default context was set before initializing the workbench, so tool window property is properly defined.
    assertThat(myToolWindow1.isLeft()).isTrue();
    myWorkBench.setContext("NewContext");

    // Defaults are not automatically updated when setting a new workbench context, so all properties are false by default.
    assertThat(myToolWindow1.isLeft()).isFalse();

    myWorkBench.setDefaultPropertiesForContext(false);
    // After updating defaults, tool window properties should work properly.
    assertThat(myToolWindow1.isLeft()).isTrue();
  }

  public void testWindowsMinimizedByDefault() {
    // Workbench was initialized with MINIMIZED default set to false.
    assertThat(myToolWindow1.isMinimized()).isFalse();

    myWorkBench.setContext("testWindowsMinimizedByDefault1");
    myWorkBench.setDefaultPropertiesForContext(true);
    // After updating defaults, MINIMIZED property should have been set to true.
    assertThat(myToolWindow1.isMinimized()).isTrue();

    myWorkBench.setContext("testWindowsMinimizedByDefault2");
    myWorkBench.setDefaultPropertiesForContext(false);
    assertThat(myToolWindow1.isMinimized()).isFalse();
  }


  public void testAutoHide() {
    myToolWindow1.setAutoHide(true);
    myToolWindow1.setMinimized(false);
    myModel.update(myToolWindow1, PropertyType.AUTO_HIDE);

    fireFocusOwnerChange(myContent);
    assertThat(myToolWindow1.isMinimized()).isTrue();
  }

  private static void fireFocusOwnerChange(@NotNull JComponent component) {
    for (PropertyChangeListener changeListener : KeyboardFocusManager.getCurrentKeyboardFocusManager().getPropertyChangeListeners()) {
      changeListener.propertyChange(new PropertyChangeEvent(component, "focusOwner", null, component));
    }
  }

  public void testComponentResize() throws Exception {
    assertThat(myPropertiesComponent.getInt(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.LEFT.UNSCALED.WIDTH", -1))
      .isEqualTo(-1);
    assertThat(myPropertiesComponent.getInt(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.RIGHT.UNSCALED.WIDTH", -1))
      .isEqualTo(-1);
    mySplitter.setFirstSize(400);
    fireComponentResize();
    assertThat(myPropertiesComponent.getInt(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.LEFT.UNSCALED.WIDTH", -1))
      .isEqualTo(AdtUiUtils.unscale(400));
    assertThat(myPropertiesComponent.getInt(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.RIGHT.UNSCALED.WIDTH", -1))
      .isEqualTo(AdtUiUtils.unscale(MIN_TOOL_WIDTH));
  }

  public void testComponentDownSizeWithToolAdjustments() throws Exception {
    myContent.setMinimumSize(new Dimension(310, 400));
    mySplitter.setSize(1000, 600);
    mySplitter.setFirstSize(1000);
    mySplitter.setLastSize(2800);
    fireComponentResize();
    assertThat(mySplitter.getFirstSize()).isEqualTo(325);
    assertThat(mySplitter.getLastSize()).isEqualTo(364);
    assertThat(myPropertiesComponent.getInt(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.LEFT.UNSCALED.WIDTH", -1))
      .isEqualTo(AdtUiUtils.unscale(325));
    assertThat(myPropertiesComponent.getInt(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.RIGHT.UNSCALED.WIDTH", -1))
      .isEqualTo(AdtUiUtils.unscale(364));
  }

  public void testComponentDownSizeToSmallerThanMinimum() throws Exception {
    // Content and tool windows are all specified with a minimum width of 310.
    // When the total width is only 900 the space should be divided equally.
    myContent.setMinimumSize(new Dimension(310, 400));
    mySplitter.setSize(900, 600);
    mySplitter.setFirstSize(1000);
    mySplitter.setLastSize(2800);
    fireComponentResize();
    assertThat(mySplitter.getFirstSize()).isEqualTo(300);
    assertThat(mySplitter.getLastSize()).isEqualTo(300);
    assertThat(myPropertiesComponent.getInt(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.LEFT.UNSCALED.WIDTH", -1))
      .isEqualTo(AdtUiUtils.unscale(MIN_TOOL_WIDTH));
    assertThat(myPropertiesComponent.getInt(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.RIGHT.UNSCALED.WIDTH", -1))
      .isEqualTo(AdtUiUtils.unscale(MIN_TOOL_WIDTH));
  }

  private void fireComponentResize() throws Exception {
    Field dispatcherField = mySplitter.getClass().getDeclaredField("myDividerDispatcher");
    dispatcherField.setAccessible(true);
    @SuppressWarnings("unchecked")
    EventDispatcher<ComponentListener> dispatcher = (EventDispatcher<ComponentListener>)dispatcherField.get(mySplitter);
    dispatcher.getMulticaster().componentResized(new ComponentEvent(mySplitter, ComponentEvent.COMPONENT_RESIZED));
  }

  public void testRestoreDefaultLayout() throws Exception {
    myToolWindow1.setAutoHide(true);
    myModel.changeToolSettingsAfterDragAndDrop(myToolWindow2, Side.LEFT, Split.TOP, 0);
    myToolWindow2.setMinimized(true);
    assertThat(myModel.getAllTools()).containsExactly(myToolWindow2, myToolWindow1, myToolWindow3).inOrder();
    mySplitter.setFirstSize(2000);
    mySplitter.setLastSize(4000);
    fireComponentResize();

    myWorkBench.restoreDefaultLayout();

    assertThat(myModel.getAllTools()).containsExactly(myToolWindow1, myToolWindow2, myToolWindow3).inOrder();
    assertThat(mySplitter.getFirstSize()).isEqualTo(MIN_TOOL_WIDTH);
    assertThat(mySplitter.getLastSize()).isEqualTo(MIN_TOOL_WIDTH);
    assertThat(myToolWindow1.isAutoHide()).isFalse();
    assertThat(myToolWindow2.isLeft()).isFalse();
    assertThat(myToolWindow2.isMinimized()).isFalse();
  }

  public void testStoreAndRestoreDefaultLayout() throws Exception {
    myToolWindow1.setAutoHide(true);
    myModel.changeToolSettingsAfterDragAndDrop(myToolWindow2, Side.LEFT, Split.TOP, 0);
    assertThat(myModel.getAllTools()).containsExactly(myToolWindow2, myToolWindow1, myToolWindow3).inOrder();
    mySplitter.setFirstSize(325);
    mySplitter.setLastSize(400);
    fireComponentResize();

    myWorkBench.storeDefaultLayout();

    myToolWindow1.setAutoHide(false);
    myModel.changeToolSettingsAfterDragAndDrop(myToolWindow2, Side.LEFT, Split.TOP, 1);
    myToolWindow2.setSplit(true);
    assertThat(myModel.getAllTools()).containsExactly(myToolWindow1, myToolWindow2, myToolWindow3).inOrder();
    myToolWindow2.setLeft(false);
    myToolWindow2.setMinimized(false);
    mySplitter.setFirstSize(111);
    mySplitter.setLastSize(444);
    fireComponentResize();

    myWorkBench.restoreDefaultLayout();

    assertThat(myModel.getAllTools()).containsExactly(myToolWindow2, myToolWindow1, myToolWindow3).inOrder();
    assertThat(mySplitter.getFirstSize()).isEqualTo(325);
    assertThat(mySplitter.getLastSize()).isEqualTo(400);
    assertThat(myToolWindow1.isAutoHide()).isTrue();
    assertThat(myToolWindow2.isLeft()).isTrue();
    assertThat(myToolWindow2.isSplit()).isFalse();
  }

  public void testModelSwap() throws Exception {
    mySplitter.setFirstSize(400);
    fireComponentResize();
    assertThat(myToolWindow1.isLeft()).isTrue();
    assertThat(myToolWindow2.isLeft()).isFalse();
    assertThat(myToolWindow3.isLeft()).isFalse();

    myModel.swap();
    assertThat(myToolWindow1.isLeft()).isFalse();
    assertThat(myToolWindow2.isLeft()).isTrue();
    assertThat(myToolWindow3.isLeft()).isTrue();
    assertThat(mySplitter.getFirstSize()).isEqualTo(MIN_TOOL_WIDTH);
    assertThat(mySplitter.getLastSize()).isEqualTo(400);
    verify(myWorkBenchManager).updateOtherWorkBenches(eq(myWorkBench));
  }

  public void testModelUpdateFloatingStatus() {
    myToolWindow1.setFloating(true);
    myToolWindow1.setDetached(true);
    myModel.update(myToolWindow1, PropertyType.DETACHED);

    verify(myWorkBenchManager).updateOtherWorkBenches(eq(myWorkBench));
    verify(myFloatingToolWindowManager).updateToolWindowsForWorkBench(eq(myWorkBench));
  }

  public void testModelLocalUpdate() {
    myModel.updateLocally();

    verifyNoMoreInteractions(myWorkBenchManager);
    verifyNoMoreInteractions(myFloatingToolWindowManager);
  }

  public void testModelToolOrderChange() {
    myModel.changeToolSettingsAfterDragAndDrop(myToolWindow2, Side.LEFT, Split.TOP, 0);

    assertThat(myPropertiesComponent.getValue(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.TOOL_ORDER")).isEqualTo("OTHER,PALETTE,THIRD");
    verify(myWorkBenchManager).updateOtherWorkBenches(eq(myWorkBench));
  }

  public void testModelNormalChange() {
    myToolWindow1.setSplit(true);
    myModel.update(myToolWindow1, PropertyType.SPLIT);

    verify(myWorkBenchManager).updateOtherWorkBenches(eq(myWorkBench));
  }

  public void testUpdateModel() {
    assertThat(myModel.getAllTools()).containsExactly(myToolWindow1, myToolWindow2, myToolWindow3).inOrder();
    myPropertiesComponent.setValue(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.TOOL_ORDER", "OTHER,THIRD,PALETTE");

    myWorkBench.updateModel();
    assertThat(myModel.getAllTools()).containsExactly(myToolWindow2, myToolWindow3, myToolWindow1).inOrder();
  }

  public void testButtonDraggedFromLeftSideToRightSideAndBack() {
    myWorkBench.setSize(1055, 400);
    JComponent dragImage = new JLabel();

    fireButtonDragged(dragImage, 10, 20, 11, 90);
    assertThat(dragImage.getParent()).isNotNull();
    assertThat(dragImage.getX()).isEqualTo(1);
    assertThat(dragImage.getY()).isEqualTo(70);
    verify(myLeftMinimizePanel).drag(eq(myToolWindow1), eq(70));

    fireButtonDragged(dragImage, 10, 20, 100, 100);
    assertThat(dragImage.getParent()).isNotNull();
    assertThat(dragImage.getX()).isEqualTo(90);
    assertThat(dragImage.getY()).isEqualTo(80);
    verify(myLeftMinimizePanel).dragExit(eq(myToolWindow1));

    fireButtonDragged(dragImage, 10, 20, 1052, 40);
    assertThat(dragImage.getParent()).isNotNull();
    assertThat(dragImage.getX()).isEqualTo(1055 - myToolWindow1.getMinimizedButton().getPreferredSize().width);
    assertThat(dragImage.getY()).isEqualTo(20);
    verify(myRightMinimizePanel).drag(eq(myToolWindow1), eq(20));

    fireButtonDragged(dragImage, 10, 20, 11, 95);
    assertThat(dragImage.getParent()).isNotNull();
    assertThat(dragImage.getX()).isEqualTo(1);
    assertThat(dragImage.getY()).isEqualTo(75);
    verify(myRightMinimizePanel).dragExit(eq(myToolWindow1));
    verify(myLeftMinimizePanel).drag(eq(myToolWindow1), eq(75));
  }

  @SuppressWarnings("SameParameterValue")
  private void fireButtonDragged(@NotNull JComponent dragImage, int xStart, int yStart, int x, int y) {
    AbstractButton button = myToolWindow1.getMinimizedButton();
    MouseEvent mouseEvent = new MouseEvent(button, MouseEvent.MOUSE_DRAGGED, 1, InputEvent.BUTTON1_MASK, x, y, 1, false);
    DragEvent event = new DragEvent(mouseEvent, dragImage, new Point(xStart, yStart));
    myToolWindow1.fireButtonDragged(event);
  }

  public void testButtonDraggedFromLeftSideAndDroppedOnRightSide() {
    myWorkBench.setSize(1055, 400);
    JComponent dragImage = new JLabel();

    fireButtonDragged(dragImage, 10, 20, 11, 90);
    assertThat(dragImage.getParent()).isNotNull();
    assertThat(dragImage.getX()).isEqualTo(1);
    assertThat(dragImage.getY()).isEqualTo(70);
    verify(myLeftMinimizePanel).drag(eq(myToolWindow1), eq(70));

    fireButtonDropped(dragImage, 10, 20, 1052, 40);
    assertThat(dragImage.getParent()).isNull();
    verify(myLeftMinimizePanel).dragExit(eq(myToolWindow1));
    verify(myRightMinimizePanel).dragDrop(eq(myToolWindow1), eq(20));
  }

  @SuppressWarnings("SameParameterValue")
  private void fireButtonDropped(@NotNull JComponent dragImage, int xStart, int yStart, int x, int y) {
    AbstractButton button = myToolWindow1.getMinimizedButton();
    MouseEvent mouseEvent = new MouseEvent(button, MouseEvent.MOUSE_RELEASED, 1, InputEvent.BUTTON1_MASK, x, y, 1, false);
    DragEvent event = new DragEvent(mouseEvent, dragImage, new Point(xStart, yStart));
    myToolWindow1.fireButtonDropped(event);
  }
}
