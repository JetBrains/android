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

import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.android.tools.adtui.workbench.AttachedToolWindow.TOOL_WINDOW_PROPERTY_PREFIX;

/**
 * Provides a work area with 1 or more {@link ToolWindowDefinition}s.
 * Each {@link ToolContent} represents a tool that can manipulate the data in the {@link WorkBench}.
 * There can be up to 4 visible {@link ToolWindowDefinition}s at any given time:<br/>
 * <pre>
 *     +-+-----+---------------+-----+-+
 *     | |  A  |               |  C  |F|
 *     | +-----+   WorkBench   +-----+ |
 *     |E|  B  |               |  D  | |
 *     +-+-----+---------------+-----+-+
 * </pre>
 * In the diagram the {@link WorkBench} has 4 visible {@link ToolWindowDefinition}s: A & B on the left side and
 * C & D on the right side. The {@link ToolWindowDefinition} on the bottom are referred to as split windows.<br/><br/>
 *
 * When a {@link ToolWindowDefinition} is not visible a button with its name is shown in narrow side panel. The
 * buttons will restore the tool in a visible state. In the diagram E & F represent such buttons.
 *
 * @param <T> Specifies the type of data controlled by this {@link WorkBench}.
 */
public class WorkBench<T> extends JPanel implements Disposable {
  private final String myName;
  private final PropertiesComponent myPropertiesComponent;
  private final WorkBenchManager myWorkBenchManager;
  private final FloatingToolWindowManager myFloatingToolWindowManager;
  private final FileEditorManager myFileEditorManager;
  private final List<ToolWindowDefinition<T>> myToolDefinitions;
  private final SideModel<T> myModel;
  private final ThreeComponentsSplitter mySplitter;
  private FileEditor myFileEditor;

  /**
   * Creates a work space with associated tool windows, which can be attached.
   *
   * @param project the project associated with this work space.
   * @param name a name used to identify this type of {@link WorkBench}. Also used for associating properties.
   * @param fileEditor the file editor this work space is associated with.
   */
  public WorkBench(@NotNull Project project, @NotNull String name, @Nullable FileEditor fileEditor) {
    this(project, name, fileEditor, InitParams.createParams(project));
  }

  /**
   * Initializes a {@link WorkBench} with content, context and tool windows.
   *
   * @param content the content of the main area of the {@link WorkBench}
   * @param context an instance identifying the data the {@link WorkBench} is manipulating
   * @param definitions a list of tool windows associated with this {@link WorkBench}
   */
  public void init(@NotNull JComponent content,
                   @NotNull T context,
                   @NotNull List<ToolWindowDefinition<T>> definitions) {
    content.addComponentListener(createWidthUpdater());
    mySplitter.setInnerComponent(content);
    mySplitter.setFirstSize(getInitialSideWidth(Side.LEFT));
    mySplitter.setLastSize(getInitialSideWidth(Side.RIGHT));
    myToolDefinitions.addAll(definitions);
    myModel.setContext(context);
    addToolsToModel();
    myWorkBenchManager.register(this);
    myFloatingToolWindowManager.register(myFileEditor, this);
  }

  /**
   * Normally the context is constant.
   * Currently needed for the designer preview pane.
   */
  public void setToolContext(@Nullable T context) {
    myModel.setContext(context);
  }

  /**
   * Normally the {@link FileEditor} is constant.
   * Currently needed for the designer preview pane.
   */
  public void setFileEditor(@Nullable FileEditor fileEditor) {
    myFloatingToolWindowManager.unregister(myFileEditor);
    myFloatingToolWindowManager.register(fileEditor, this);
    myFileEditor = fileEditor;
    if (fileEditor != null && isCurrentEditor(fileEditor)) {
      myFloatingToolWindowManager.updateToolWindowsForWorkBench(this);
    }
  }

  @Override
  public void dispose() {
    myWorkBenchManager.unregister(this);
    myFloatingToolWindowManager.unregister(myFileEditor);
  }

  // ----------------------------------- Implementation --------------------------------------------------------------- //

  @VisibleForTesting
  WorkBench(@NotNull Project project,
            @NotNull String name,
            @Nullable FileEditor fileEditor,
            @NotNull InitParams<T> params) {
    super(new BorderLayout());
    myName = name;
    myFileEditor = fileEditor;
    myPropertiesComponent = PropertiesComponent.getInstance();
    myWorkBenchManager = WorkBenchManager.getInstance();
    myFloatingToolWindowManager = FloatingToolWindowManager.getInstance(project);
    myFileEditorManager = FileEditorManager.getInstance(project);
    myToolDefinitions = new ArrayList<>(4);
    myModel = params.myModel;
    myModel.addListener(this::modelChanged);
    mySplitter = initSplitter(params.mySplitter);
    LayeredPanel<T> layeredPanel = new LayeredPanel<>(myName, mySplitter, myModel);
    add(params.myLeftMinimizePanel, BorderLayout.WEST);
    add(layeredPanel, BorderLayout.CENTER);
    add(params.myRightMinimizePanel, BorderLayout.EAST);
    Disposer.register(this, mySplitter);
    Disposer.register(this, layeredPanel);
  }

  private boolean isCurrentEditor(@NotNull FileEditor fileEditor) {
    for (FileEditor editor : myFileEditorManager.getSelectedEditors()) {
      if (fileEditor == editor) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", this::autoHide);
  }

  @Override
  public void removeNotify() {
    super.removeNotify();
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", this::autoHide);
  }

  private void autoHide(@NotNull PropertyChangeEvent event) {
    AttachedToolWindow<T> autoToolWindow = myModel.getVisibleAutoHideTool();
    if (autoToolWindow == null) {
      return;
    }
    Object newValue = event.getNewValue();
    if (newValue instanceof JComponent) {
      myFileEditor.getPreferredFocusedComponent();
      JComponent newComponent = (JComponent)newValue;
      // Note: We sometimes get a focusOwner notification for a parent of the current tool editor.
      // This has been seen when the Component tree has focus and the palette is opened with AutoHide on.
      if (!SwingUtilities.isDescendingFrom(newComponent, autoToolWindow.getComponent()) &&
          !SwingUtilities.isDescendingFrom(autoToolWindow.getComponent(), newComponent)) {
        autoToolWindow.setPropertyAndUpdate(AttachedToolWindow.PropertyType.MINIMIZED, true);
      }
    }
  }

  @NotNull
  private ThreeComponentsSplitter initSplitter(@NotNull ThreeComponentsSplitter splitter) {
    splitter.setDividerWidth(0);
    splitter.setDividerMouseZoneSize(Registry.intValue("ide.splitter.mouseZone"));
    splitter.setHonorComponentsMinimumSize(true);
    splitter.setFirstComponent(new SidePanel<>(Side.LEFT, myModel));
    splitter.setLastComponent(new SidePanel<>(Side.RIGHT, myModel));
    splitter.setShowDividerControls(true);
    return splitter;
  }

  @NotNull
  private String getWidthPropertyName(@NotNull Side side) {
    return TOOL_WINDOW_PROPERTY_PREFIX + myName + "." + side.name() + ".WIDTH";
  }

  private int getSideWidth(@NotNull Side side) {
    return myPropertiesComponent.getInt(getWidthPropertyName(side), -1);
  }

  private void setSideWidth(@NotNull Side side, int value) {
    myPropertiesComponent.setValue(getWidthPropertyName(side), value, ToolWindowDefinition.DEFAULT_SIDE_WIDTH);
  }

  private int getInitialSideWidth(@NotNull Side side) {
    int width = getSideWidth(side);
    if (width != -1) {
      return width;
    }
    Optional<Integer> minimumWidth = myToolDefinitions.stream()
      .filter(tool -> tool.getSide() == side)
      .map(ToolWindowDefinition::getInitialMinimumWidth)
      .max(Comparator.comparing(size -> size));
    return minimumWidth.orElse(ToolWindowDefinition.DEFAULT_SIDE_WIDTH);
  }

  @NotNull
  private ComponentListener createWidthUpdater() {
    return new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        updateBothWidths();
      }
    };
  }

  private void updateBothWidths() {
    updateWidth(Side.LEFT);
    updateWidth(Side.RIGHT);
  }

  private void updateWidth(@NotNull Side side) {
    int width = side.isLeft() ? mySplitter.getFirstSize() : mySplitter.getLastSize();
    if (width != 0 && width != getSideWidth(side)) {
      setSideWidth(side, width);
    }
  }

  private void modelChanged(@NotNull SideModel model, @NotNull SideModel.EventType type) {
    switch (type) {
      case SWAP:
        mySplitter.setFirstSize(getSideWidth(Side.RIGHT));
        mySplitter.setLastSize(getSideWidth(Side.LEFT));
        updateBothWidths();
        myWorkBenchManager.updateOtherWorkBenches(this);
        break;

      case UPDATE_FLOATING_WINDOW:
        myWorkBenchManager.updateOtherWorkBenches(this);
        myFloatingToolWindowManager.updateToolWindowsForWorkBench(this);
        break;

      case LOCAL_UPDATE:
        break;

      default:
        myWorkBenchManager.updateOtherWorkBenches(this);
        break;
    }
  }

  private void addToolsToModel() {
    for (ToolWindowDefinition<T> definition : myToolDefinitions) {
      AttachedToolWindow<T> toolWindow = new AttachedToolWindow<>(definition, myName, myModel);
      Disposer.register(this, toolWindow);
      myModel.add(toolWindow);
    }
    myModel.updateLocally();
  }

  public List<AttachedToolWindow<T>> getFloatingToolWindows() {
    return myModel.getFloatingTools();
  }

  public void updateModel() {
    myModel.updateLocally();
  }

  @VisibleForTesting
  static class InitParams<T> {
    private final SideModel<T> myModel;
    private final ThreeComponentsSplitter mySplitter;
    private final MinimizedPanel<T> myLeftMinimizePanel;
    private final MinimizedPanel<T> myRightMinimizePanel;

    @VisibleForTesting
    InitParams(@NotNull SideModel<T> model,
               @NotNull ThreeComponentsSplitter splitter,
               @NotNull MinimizedPanel<T> leftMinimizePanel,
               @NotNull MinimizedPanel<T> rightMinimizePanel) {
      myModel = model;
      mySplitter = splitter;
      myLeftMinimizePanel = leftMinimizePanel;
      myRightMinimizePanel = rightMinimizePanel;
    }

    private static <T> InitParams<T> createParams(@NotNull Project project) {
      SideModel<T> model = new SideModel<>(project);
      return new InitParams<>(model,
                              new ThreeComponentsSplitter(),
                              new MinimizedPanel<>(Side.LEFT, model),
                              new MinimizedPanel<>(Side.RIGHT, model));
    }
  }
}
