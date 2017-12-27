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

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.workbench.AttachedToolWindow.DragEvent;
import com.android.tools.adtui.workbench.AttachedToolWindow.PropertyType;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

import static com.android.tools.adtui.workbench.AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX;
import static com.android.tools.adtui.workbench.ToolWindowDefinition.DEFAULT_SIDE_WIDTH;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

@RunWith(JUnit4.class)
public class WorkBenchTest {
  @Rule
  public FrameworkRule myFrameworkRule = new FrameworkRule();
  @Mock
  private FileEditor myFileEditor;
  @Mock
  private FileEditor myFileEditor2;

  private WorkBenchManager myWorkBenchManager;
  private DetachedToolWindowManager myFloatingToolWindowManager;
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

  @Before
  public void before() {
    initMocks(this);
    myContent = new JPanel();
    myContent.setPreferredSize(new Dimension(500, 400));
    mySplitter = new ThreeComponentsSplitter();
    Project project = ProjectManager.getInstance().getDefaultProject();
    myPropertiesComponent = PropertiesComponent.getInstance();
    myWorkBenchManager = WorkBenchManager.getInstance();
    myFloatingToolWindowManager = DetachedToolWindowManager.getInstance(project);
    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
    myModel = new SideModel<>(project);
    myLeftMinimizePanel = spy(new MinimizedPanel<>(Side.RIGHT, myModel));
    myLeftMinimizePanel.setLayout(new BoxLayout(myLeftMinimizePanel, BoxLayout.Y_AXIS));
    myRightMinimizePanel = spy(new MinimizedPanel<>(Side.RIGHT, myModel));
    myRightMinimizePanel.setLayout(new BoxLayout(myRightMinimizePanel, BoxLayout.Y_AXIS));
    WorkBench.InitParams<String> initParams = new WorkBench.InitParams<>(myModel, mySplitter, myLeftMinimizePanel, myRightMinimizePanel);
    mySplitter.setSize(1000, 600);
    myWorkBench = new WorkBench<>(project, "BENCH", myFileEditor, initParams);
    JRootPane rootPane = new JRootPane();
    rootPane.add(myWorkBench);
    List<ToolWindowDefinition<String>> definitions = ImmutableList.of(PalettePanelToolContent.getDefinition(),
                                                                      PalettePanelToolContent.getOtherDefinition(),
                                                                      PalettePanelToolContent.getThirdDefinition());
    myWorkBench.init(myContent, "CONTEXT", definitions);
    myToolWindow1 = myModel.getAllTools().get(0);
    myToolWindow2 = myModel.getAllTools().get(1);
    myToolWindow3 = myModel.getAllTools().get(2);
    when(fileEditorManager.getSelectedEditors()).thenReturn(new FileEditor[]{myFileEditor, myFileEditor2});
    verify(myWorkBenchManager).register(eq(myWorkBench));
    verify(myFloatingToolWindowManager).register(eq(myFileEditor), eq(myWorkBench));
    reset(myWorkBenchManager, myFloatingToolWindowManager);
  }

  @After
  public void after() {
    Disposer.dispose(myWorkBench);
  }

  @Test
  public void testUpdateEditor() {
    myWorkBench.setFileEditor(myFileEditor2);
    verify(myFloatingToolWindowManager).unregister(eq(myFileEditor));
    verify(myFloatingToolWindowManager).register(eq(myFileEditor2), eq(myWorkBench));
    verify(myFloatingToolWindowManager).updateToolWindowsForWorkBench(myWorkBench);
  }

  @Test
  public void testSetContext() {
    assertThat(myModel.getContext()).isEqualTo("CONTEXT");
    myWorkBench.setToolContext("Google");
    assertThat(myModel.getContext()).isEqualTo("Google");
  }

  @Test
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

  @Test
  public void testComponentResize() {
    assertThat(myPropertiesComponent.getInt(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.LEFT.UNSCALED.WIDTH", -1))
      .isEqualTo(-1);
    assertThat(myPropertiesComponent.getInt(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.RIGHT.UNSCALED.WIDTH", -1))
      .isEqualTo(-1);
    mySplitter.setFirstSize(400);
    fireComponentResize(myContent);
    assertThat(myPropertiesComponent.getInt(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.LEFT.UNSCALED.WIDTH", -1))
      .isEqualTo(AdtUiUtils.unscale(400));
    assertThat(myPropertiesComponent.getInt(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.RIGHT.UNSCALED.WIDTH", -1))
      .isEqualTo(AdtUiUtils.unscale(DEFAULT_SIDE_WIDTH));
  }

  @Test
  public void testComponentDownSizeWithToolAdjustments() {
    mySplitter.setSize(1000, 600);
    mySplitter.setFirstSize(1000);
    mySplitter.setLastSize(2800);
    fireComponentResize(myContent);
    assertThat(mySplitter.getFirstSize()).isEqualTo(300);
    assertThat(mySplitter.getLastSize()).isEqualTo(474);
    assertThat(myPropertiesComponent.getInt(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.LEFT.UNSCALED.WIDTH", -1))
      .isEqualTo(AdtUiUtils.unscale(300));
    assertThat(myPropertiesComponent.getInt(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.RIGHT.UNSCALED.WIDTH", -1))
      .isEqualTo(AdtUiUtils.unscale(474));
  }

  @Test
  public void testComponentDownSizeToSmallerThanMinimum() {
    mySplitter.setSize(500, 600);
    mySplitter.setFirstSize(1000);
    mySplitter.setLastSize(2800);
    fireComponentResize(myContent);
    assertThat(mySplitter.getFirstSize()).isEqualTo(166);
    assertThat(mySplitter.getLastSize()).isEqualTo(166);
    assertThat(myPropertiesComponent.getInt(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.LEFT.UNSCALED.WIDTH", -1))
      .isEqualTo(AdtUiUtils.unscale(DEFAULT_SIDE_WIDTH));
    assertThat(myPropertiesComponent.getInt(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.RIGHT.UNSCALED.WIDTH", -1))
      .isEqualTo(AdtUiUtils.unscale(DEFAULT_SIDE_WIDTH));
  }

  private static void fireComponentResize(@NotNull JComponent component) {
    ComponentEvent event = mock(ComponentEvent.class);
    for (ComponentListener listener : component.getComponentListeners()) {
      listener.componentResized(event);
    }
  }

  @Test
  public void testRestoreDefaultLayout() {
    myToolWindow1.setAutoHide(true);
    myModel.changeToolSettingsAfterDragAndDrop(myToolWindow2, Side.LEFT, Split.TOP, 0);
    myToolWindow2.setMinimized(true);
    assertThat(myModel.getAllTools()).containsExactly(myToolWindow2, myToolWindow1, myToolWindow3).inOrder();
    mySplitter.setFirstSize(2000);
    mySplitter.setLastSize(4000);
    fireComponentResize(myContent);

    myWorkBench.restoreDefaultLayout();

    assertThat(myModel.getAllTools()).containsExactly(myToolWindow1, myToolWindow2, myToolWindow3).inOrder();
    assertThat(mySplitter.getFirstSize()).isEqualTo(DEFAULT_SIDE_WIDTH);
    assertThat(mySplitter.getLastSize()).isEqualTo(DEFAULT_SIDE_WIDTH);
    assertThat(myToolWindow1.isAutoHide()).isFalse();
    assertThat(myToolWindow2.isLeft()).isFalse();
    assertThat(myToolWindow2.isMinimized()).isFalse();
  }

  @Test
  public void testStoreAndRestoreDefaultLayout() {
    myToolWindow1.setAutoHide(true);
    myModel.changeToolSettingsAfterDragAndDrop(myToolWindow2, Side.LEFT, Split.TOP, 0);
    assertThat(myModel.getAllTools()).containsExactly(myToolWindow2, myToolWindow1, myToolWindow3).inOrder();
    mySplitter.setFirstSize(300);
    mySplitter.setLastSize(400);
    fireComponentResize(myContent);

    myWorkBench.storeDefaultLayout();

    myToolWindow1.setAutoHide(false);
    myModel.changeToolSettingsAfterDragAndDrop(myToolWindow2, Side.LEFT, Split.TOP, 1);
    myToolWindow2.setSplit(true);
    assertThat(myModel.getAllTools()).containsExactly(myToolWindow1, myToolWindow2, myToolWindow3).inOrder();
    myToolWindow2.setLeft(false);
    myToolWindow2.setMinimized(false);
    mySplitter.setFirstSize(111);
    mySplitter.setLastSize(444);
    fireComponentResize(myContent);

    myWorkBench.restoreDefaultLayout();

    assertThat(myModel.getAllTools()).containsExactly(myToolWindow2, myToolWindow1, myToolWindow3).inOrder();
    assertThat(mySplitter.getFirstSize()).isEqualTo(300);
    assertThat(mySplitter.getLastSize()).isEqualTo(400);
    assertThat(myToolWindow1.isAutoHide()).isTrue();
    assertThat(myToolWindow2.isLeft()).isTrue();
    assertThat(myToolWindow2.isSplit()).isFalse();
  }

  @Test
  public void testModelSwap() {
    mySplitter.setFirstSize(400);
    fireComponentResize(myContent);
    assertThat(myToolWindow1.isLeft()).isTrue();
    assertThat(myToolWindow2.isLeft()).isFalse();
    assertThat(myToolWindow3.isLeft()).isFalse();

    myModel.swap();
    assertThat(myToolWindow1.isLeft()).isFalse();
    assertThat(myToolWindow2.isLeft()).isTrue();
    assertThat(myToolWindow3.isLeft()).isTrue();
    assertThat(mySplitter.getFirstSize()).isEqualTo(DEFAULT_SIDE_WIDTH);
    assertThat(mySplitter.getLastSize()).isEqualTo(400);
    verify(myWorkBenchManager).updateOtherWorkBenches(eq(myWorkBench));
  }

  @Test
  public void testModelUpdateFloatingStatus() {
    myToolWindow1.setFloating(true);
    myToolWindow1.setDetached(true);
    myModel.update(myToolWindow1, PropertyType.DETACHED);

    verify(myWorkBenchManager).updateOtherWorkBenches(eq(myWorkBench));
    verify(myFloatingToolWindowManager).updateToolWindowsForWorkBench(eq(myWorkBench));
  }

  @Test
  public void testModelLocalUpdate() {
    myModel.updateLocally();

    verifyZeroInteractions(myWorkBenchManager);
    verifyZeroInteractions(myFloatingToolWindowManager);
  }

  @Test
  public void testModelToolOrderChange() {
    myModel.changeToolSettingsAfterDragAndDrop(myToolWindow2, Side.LEFT, Split.TOP, 0);

    assertThat(myPropertiesComponent.getValue(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.TOOL_ORDER")).isEqualTo("OTHER,PALETTE,THIRD");
    verify(myWorkBenchManager).updateOtherWorkBenches(eq(myWorkBench));
  }

  @Test
  public void testModelNormalChange() {
    myToolWindow1.setSplit(true);
    myModel.update(myToolWindow1, PropertyType.SPLIT);

    verify(myWorkBenchManager).updateOtherWorkBenches(eq(myWorkBench));
  }

  @Test
  public void testUpdateModel() {
    assertThat(myModel.getAllTools()).containsExactly(myToolWindow1, myToolWindow2, myToolWindow3).inOrder();
    myPropertiesComponent.setValue(TOOL_WINDOW_PROPERTY_PREFIX + "BENCH.TOOL_ORDER", "OTHER,THIRD,PALETTE");

    myWorkBench.updateModel();
    assertThat(myModel.getAllTools()).containsExactly(myToolWindow2, myToolWindow3, myToolWindow1).inOrder();
  }

  @Test
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

  @Test
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
