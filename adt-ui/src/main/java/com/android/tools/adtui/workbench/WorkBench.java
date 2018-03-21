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
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.workbench.AttachedToolWindow.ButtonDragListener;
import com.android.tools.adtui.workbench.AttachedToolWindow.DragEvent;
import com.google.common.base.Splitter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;
import java.util.List;

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
public class WorkBench<T> extends JBLayeredPane implements Disposable {
  private final String myName;
  private final PropertiesComponent myPropertiesComponent;
  private final WorkBenchManager myWorkBenchManager;
  private final DetachedToolWindowManager myDetachedToolWindowManager;
  private final FileEditorManager myFileEditorManager;
  private final List<ToolWindowDefinition<T>> myToolDefinitions;
  private final SideModel<T> myModel;
  private final ThreeComponentsSplitter mySplitter;
  private final MyLoadingPanel myLoadingPanel;
  private final JPanel myMainPanel;
  private final MinimizedPanel<T> myLeftMinimizePanel;
  private final MinimizedPanel<T> myRightMinimizePanel;
  private final ButtonDragListener<T> myButtonDragListener;
  private final PropertyChangeListener myMyPropertyChangeListener = this::autoHide;
  private FileEditor myFileEditor;

  /**
   * Creates a work space with associated tool windows, which can be attached.
   *
   * @param project the project associated with this work space.
   * @param name a name used to identify this type of {@link WorkBench}. Also used for associating properties.
   * @param fileEditor the file editor this work space is associated with.
   */
  public WorkBench(@NotNull Project project, @NotNull String name, @Nullable FileEditor fileEditor) {
    this(project, name, fileEditor, InitParams.createParams(project), DetachedToolWindowManager.getInstance(project));
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
    myLoadingPanel.stopLoading();
    myMainPanel.setVisible(true);
    content.addComponentListener(createWidthUpdater());
    mySplitter.setInnerComponent(content);
    mySplitter.setFirstSize(getInitialSideWidth(Side.LEFT));
    mySplitter.setLastSize(getInitialSideWidth(Side.RIGHT));
    myToolDefinitions.addAll(definitions);
    myModel.setContext(context);
    addToolsToModel();
    myWorkBenchManager.register(this);
    myDetachedToolWindowManager.register(myFileEditor, this);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", myMyPropertyChangeListener);
  }

  public void setLoadingText(@NotNull String loadingText) {
    myLoadingPanel.setLoadingText(loadingText);
  }

  public void loadingStopped(@NotNull String message) {
    myLoadingPanel.abortLoading(message, AllIcons.General.Warning);
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
    myDetachedToolWindowManager.unregister(myFileEditor);
    myDetachedToolWindowManager.register(fileEditor, this);
    myFileEditor = fileEditor;
    if (fileEditor != null && isCurrentEditor(fileEditor)) {
      myDetachedToolWindowManager.updateToolWindowsForWorkBench(this);
    }
  }

  @Override
  public void dispose() {
    myWorkBenchManager.unregister(this);
    myDetachedToolWindowManager.unregister(myFileEditor);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", myMyPropertyChangeListener);
  }

  // ----------------------------------- Implementation --------------------------------------------------------------- //

  @VisibleForTesting
  WorkBench(@NotNull Project project,
            @NotNull String name,
            @Nullable FileEditor fileEditor,
            @NotNull InitParams<T> params,
            @NotNull DetachedToolWindowManager detachedToolWindowManager) {
    myName = name;
    myFileEditor = fileEditor;
    myPropertiesComponent = PropertiesComponent.getInstance();
    myWorkBenchManager = WorkBenchManager.getInstance();
    myDetachedToolWindowManager = detachedToolWindowManager;
    myFileEditorManager = FileEditorManager.getInstance(project);
    myToolDefinitions = new ArrayList<>(4);
    myModel = params.myModel;
    myModel.addListener(this::modelChanged);
    myButtonDragListener = new MyButtonDragListener();
    mySplitter = initSplitter(params.mySplitter);
    myLeftMinimizePanel = params.myLeftMinimizePanel;
    myRightMinimizePanel = params.myRightMinimizePanel;
    LayeredPanel<T> layeredPanel = new LayeredPanel<>(myName, mySplitter, myModel);
    myMainPanel = new JPanel(new BorderLayout());
    myMainPanel.add(myLeftMinimizePanel, BorderLayout.WEST);
    myMainPanel.add(layeredPanel, BorderLayout.CENTER);
    myMainPanel.add(myRightMinimizePanel, BorderLayout.EAST);
    myLoadingPanel = new MyLoadingPanel(new BorderLayout(), this, 1000);
    myLoadingPanel.add(myMainPanel);
    Disposer.register(this, mySplitter);
    Disposer.register(this, layeredPanel);
    add(myLoadingPanel, JLayeredPane.DEFAULT_LAYER);
    myMainPanel.setVisible(false);
    myLoadingPanel.startLoading();
  }

  private boolean isCurrentEditor(@NotNull FileEditor fileEditor) {
    for (FileEditor editor : myFileEditorManager.getSelectedEditors()) {
      if (fileEditor == editor) {
        return true;
      }
    }
    return false;
  }

  private void autoHide(@NotNull PropertyChangeEvent event) {
    AttachedToolWindow<T> autoToolWindow = myModel.getVisibleAutoHideTool();
    if (autoToolWindow == null) {
      return;
    }
    Object newValue = event.getNewValue();
    if (newValue instanceof JComponent) {
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

  public void setMinimizePanelsVisible(boolean visible) {
    myLeftMinimizePanel.setVisible(visible);
    myRightMinimizePanel.setVisible(visible);
  }

  @NotNull
  private String getUnscaledWidthPropertyName(@NotNull Layout layout, @NotNull Side side) {
    return TOOL_WINDOW_PROPERTY_PREFIX + layout.getPrefix() + myName + "." + side.name() + ".UNSCALED.WIDTH";
  }

  @NotNull
  private String getScaledWidthPropertyName(@NotNull Layout layout, @NotNull Side side) {
    return TOOL_WINDOW_PROPERTY_PREFIX + layout.getPrefix() + myName + "." + side.name() + ".WIDTH";
  }

  private int getSideWidth(@NotNull Layout layout, @NotNull Side side) {
    int width = myPropertiesComponent.getInt(getUnscaledWidthPropertyName(layout, side), -1);
    if (width != -1) {
      return JBUI.scale(width);
    }
    int scaledWidth = myPropertiesComponent.getInt(getScaledWidthPropertyName(layout, side), -1);
    if (scaledWidth != -1) {
      return -1;
    }
    myPropertiesComponent.unsetValue(getScaledWidthPropertyName(layout, side));
    setSideWidth(layout, side, scaledWidth);
    return scaledWidth;
  }

  private void setSideWidth(@NotNull Layout layout, @NotNull Side side, int value) {
    myPropertiesComponent.setValue(getUnscaledWidthPropertyName(layout, side), AdtUiUtils.unscale(value), -1);
  }

  private int getInitialSideWidth(@NotNull Side side) {
    int minimalWidth = getMinimumWidth(side);
    int width = getSideWidth(Layout.CURRENT, side);
    if (width == -1) {
      setSideWidth(Layout.DEFAULT, side, width);
      setSideWidth(Layout.CURRENT, side, width);
    }
    return Math.max(width, minimalWidth);
  }

  private int getMinimumWidth(@NotNull Side side) {
    Optional<Integer> initialMinimumWidth = myToolDefinitions.stream()
      .filter(tool -> tool.getSide() == side)
      .map(ToolWindowDefinition::getInitialMinimumWidth)
      .max(Comparator.comparing(size -> size));
    return initialMinimumWidth.orElse(ToolWindowDefinition.DEFAULT_SIDE_WIDTH);
  }

  @NotNull
  private ComponentListener createWidthUpdater() {
    return new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        adjustSplitterForInsufficentSpace();
        updateBothWidths();
      }
    };
  }

  // TODO: Consider modifying ThreeComponentsSplitter.doLayout since this code will create some resize flickering.
  private void adjustSplitterForInsufficentSpace() {
    if (mySplitter.getWidth() <= 0 || !mySplitter.isVisible()) {
      // No adjustment required
      return;
    }
    JComponent content = mySplitter.getInnerComponent();
    int actualCenterWidth = mySplitter.getWidth() - (mySplitter.getFirstSize() + mySplitter.getLastSize());
    int minCenterWidth = Math.max((content != null ? content.getMinimumSize().width : 0), ToolWindowDefinition.DEFAULT_SIDE_WIDTH);
    int minLeftWidth = myModel.getVisibleTools(Side.LEFT).isEmpty() ? 0 : getMinimumWidth(Side.LEFT);
    int minRightWidth = myModel.getVisibleTools(Side.RIGHT).isEmpty() ? 0 : getMinimumWidth(Side.RIGHT);
    if (mySplitter.getFirstSize() >= minLeftWidth && mySplitter.getLastSize() >= minRightWidth && actualCenterWidth >= minCenterWidth) {
      // No adjustment required
      return;
    }
    if (mySplitter.getWidth() >= minLeftWidth + minCenterWidth + minRightWidth) {
      int excess = mySplitter.getWidth() - (minLeftWidth + minCenterWidth + minRightWidth);
      int leftExcess = Math.max(0, mySplitter.getFirstSize() - minLeftWidth);
      int rightExcess = Math.max(0, mySplitter.getLastSize() - minRightWidth);
      if (leftExcess + rightExcess > excess) {
        double reduction = 1.0 * excess / (leftExcess + rightExcess);
        mySplitter.setFirstSize(minLeftWidth + (int)(leftExcess * reduction));
        mySplitter.setLastSize(minRightWidth + (int)(rightExcess * reduction));
      }
    }
    else {
      int sections = 1 + (minLeftWidth > 0 ? 1 : 0) + (minRightWidth > 0 ? 1 : 0);
      int sectionWidth = mySplitter.getWidth() / sections;
      if (minLeftWidth > 0) {
        mySplitter.setFirstSize(sectionWidth);
      }
      if (minRightWidth > 0) {
        mySplitter.setLastSize(sectionWidth);
      }
    }
  }

  private void updateBothWidths() {
    updateWidth(Side.LEFT);
    updateWidth(Side.RIGHT);
  }

  private void restoreBothWidths() {
    mySplitter.setFirstSize(getInitialSideWidth(Side.LEFT));
    mySplitter.setLastSize(getInitialSideWidth(Side.RIGHT));
  }

  private void updateWidth(@NotNull Side side) {
    int minimalWidth = getMinimumWidth(side);
    int width = side.isLeft() ? mySplitter.getFirstSize() : mySplitter.getLastSize();
    width = Math.max(minimalWidth, width);
    if (width != 0 && width != getSideWidth(Layout.CURRENT, side)) {
      setSideWidth(Layout.CURRENT, side, width);
    }
  }

  @NotNull
  private String getToolOrderPropertyName(@NotNull Layout layout) {
    return TOOL_WINDOW_PROPERTY_PREFIX + layout.getPrefix() + myName + ".TOOL_ORDER";
  }

  private void restoreToolOrder(@NotNull List<AttachedToolWindow<T>> tools) {
    String orderAsString = myPropertiesComponent.getValue(getToolOrderPropertyName(Layout.CURRENT));
    if (orderAsString == null) {
      return;
    }
    Map<String, Integer> order = new HashMap<>(8);
    int number = 1;
    for (String toolName : Splitter.on(",").omitEmptyStrings().trimResults().split(orderAsString)) {
      order.put(toolName, number++);
    }
    for (AttachedToolWindow<T> tool : tools) {
      Integer placement = order.get(tool.getToolName());
      if (placement == null) {
        placement = number++;
      }
      tool.setToolOrder(placement);
    }
    tools.sort(Comparator.comparingInt(AttachedToolWindow::getToolOrder));
  }

  private void storeToolOrder(@NotNull Layout layout, @NotNull List<AttachedToolWindow<T>> tools) {
    StringBuilder builder = new StringBuilder();
    for (AttachedToolWindow tool : tools) {
      if (builder.length() > 0) {
        builder.append(",");
      }
      builder.append(tool.getToolName());
    }
    myPropertiesComponent.setValue(getToolOrderPropertyName(layout), builder.toString());
  }

  private void setDefaultOrderIfMissing(@NotNull List<AttachedToolWindow<T>> tools) {
    if (!myPropertiesComponent.isValueSet(getToolOrderPropertyName(Layout.CURRENT))) {
      storeToolOrder(Layout.DEFAULT, tools);
      storeToolOrder(Layout.CURRENT, tools);
    }
  }

  private void modelChanged(@SuppressWarnings("unused") @NotNull SideModel model, @NotNull SideModel.EventType type) {
    switch (type) {
      case SWAP:
        mySplitter.setFirstSize(getSideWidth(Layout.CURRENT, Side.RIGHT));
        mySplitter.setLastSize(getSideWidth(Layout.CURRENT, Side.LEFT));
        updateBothWidths();
        myWorkBenchManager.updateOtherWorkBenches(this);
        break;

      case UPDATE_DETACHED_WINDOW:
        myWorkBenchManager.updateOtherWorkBenches(this);
        myDetachedToolWindowManager.updateToolWindowsForWorkBench(this);
        break;

      case LOCAL_UPDATE:
        break;

      case UPDATE_TOOL_ORDER:
        storeToolOrder(Layout.CURRENT, myModel.getAllTools());
        myWorkBenchManager.updateOtherWorkBenches(this);
        break;

      default:
        myWorkBenchManager.updateOtherWorkBenches(this);
        break;
    }
  }

  private void addToolsToModel() {
    List<AttachedToolWindow<T>> tools = new ArrayList<>(myToolDefinitions.size());
    for (ToolWindowDefinition<T> definition : myToolDefinitions) {
      AttachedToolWindow<T> toolWindow = new AttachedToolWindow<>(definition, myButtonDragListener, myName, myModel);
      Disposer.register(this, toolWindow);
      tools.add(toolWindow);
    }
    setDefaultOrderIfMissing(tools);
    restoreToolOrder(tools);
    myModel.setTools(tools);
  }

  public List<AttachedToolWindow<T>> getDetachedToolWindows() {
    return myModel.getDetachedTools();
  }

  public void storeDefaultLayout() {
    String orderAsString = myPropertiesComponent.getValue(getToolOrderPropertyName(Layout.CURRENT));
    myPropertiesComponent.setValue(getToolOrderPropertyName(Layout.DEFAULT), orderAsString);
    setSideWidth(Layout.DEFAULT, Side.LEFT, getSideWidth(Layout.CURRENT, Side.LEFT));
    setSideWidth(Layout.DEFAULT, Side.RIGHT, getSideWidth(Layout.CURRENT, Side.RIGHT));
    for (AttachedToolWindow<T> tool : myModel.getAllTools()) {
      tool.storeDefaultLayout();
    }
  }

  public void restoreDefaultLayout() {
    String orderAsString = myPropertiesComponent.getValue(getToolOrderPropertyName(Layout.DEFAULT));
    myPropertiesComponent.setValue(getToolOrderPropertyName(Layout.CURRENT), orderAsString);
    setSideWidth(Layout.CURRENT, Side.LEFT, getSideWidth(Layout.DEFAULT, Side.LEFT));
    setSideWidth(Layout.CURRENT, Side.RIGHT, getSideWidth(Layout.DEFAULT, Side.RIGHT));
    for (AttachedToolWindow<T> tool : myModel.getAllTools()) {
      tool.restoreDefaultLayout();
    }
    updateModel();
  }

  public void updateModel() {
    restoreBothWidths();
    restoreToolOrder(myModel.getAllTools());
    myModel.updateLocally();
  }

  @Override
  public void doLayout() {
    myLoadingPanel.setBounds(0, 0, getWidth(), getHeight());
  }

  private class MyButtonDragListener implements ButtonDragListener<T> {
    private final int BUTTON_PANEL_WIDTH = JBUI.scale(21);

    private boolean myIsDragging;
    private MinimizedPanel<T> myPreviousButtonPanel;

    @Override
    public void buttonDragged(@NotNull AttachedToolWindow<T> toolWindow, @NotNull DragEvent event) {
      if (!myIsDragging) {
        startDragging(event);
      }
      moveDragImage(toolWindow, event);
      notifyButtonPanel(toolWindow, event, false);
    }

    @Override
    public void buttonDropped(@NotNull AttachedToolWindow<T> toolWindow, @NotNull DragEvent event) {
      if (myIsDragging) {
        notifyButtonPanel(toolWindow, event, true);
        stopDragging(toolWindow, event);
      }
    }

    private void startDragging(@NotNull DragEvent event) {
      add(event.getDragImage(), JLayeredPane.DRAG_LAYER);
      myIsDragging = true;
    }

    private void stopDragging(@NotNull AttachedToolWindow<T> tool, @NotNull DragEvent event) {
      AbstractButton button = tool.getMinimizedButton();
      button.setVisible(true);
      remove(event.getDragImage());
      revalidate();
      repaint();
      myPreviousButtonPanel = null;
      myIsDragging = false;
    }

    private void moveDragImage(@NotNull AttachedToolWindow<T> tool, @NotNull DragEvent event) {
      AbstractButton button = tool.getMinimizedButton();
      Point position = SwingUtilities.convertPoint(button, event.getMousePoint(), WorkBench.this);
      Dimension buttonSize = button.getPreferredSize();
      Point dragPosition = event.getDragPoint();
      position.x = translate(position.x, dragPosition.x, 0, getWidth() - buttonSize.width);
      position.y = translate(position.y, dragPosition.y, 0, getHeight() - buttonSize.height);

      Component dragImage = event.getDragImage();
      Dimension size = dragImage.getPreferredSize();
      dragImage.setBounds(position.x, position.y, size.width, size.height);
      dragImage.revalidate();
      dragImage.repaint();
    }

    private void notifyButtonPanel(@NotNull AttachedToolWindow<T> tool, @NotNull DragEvent event, boolean doDrop) {
      AbstractButton button = tool.getMinimizedButton();
      Point position = SwingUtilities.convertPoint(button, event.getMousePoint(), WorkBench.this);
      int yMidOfButton = position.y - event.getDragPoint().y + button.getHeight() / 2;
      if (position.x < BUTTON_PANEL_WIDTH) {
        notifyButtonPanel(tool, yMidOfButton, myLeftMinimizePanel, doDrop);
      }
      else if (position.x > getWidth() - BUTTON_PANEL_WIDTH) {
        notifyButtonPanel(tool, yMidOfButton, myRightMinimizePanel, doDrop);
      }
      else if (myPreviousButtonPanel != null) {
        myPreviousButtonPanel.dragExit(tool);
        myPreviousButtonPanel = null;
      }
    }

    private void notifyButtonPanel(@NotNull AttachedToolWindow<T> tool, int y, @NotNull MinimizedPanel<T> buttonPanel, boolean doDrop) {
      if (myPreviousButtonPanel != null && myPreviousButtonPanel != buttonPanel) {
        myPreviousButtonPanel.dragExit(tool);
      }
      myPreviousButtonPanel = buttonPanel;
      if (doDrop) {
        buttonPanel.dragDrop(tool, y);
      }
      else {
        buttonPanel.drag(tool, y);
      }
    }

    @SuppressWarnings("SameParameterValue")
    private int translate(int pos, int offset, int min, int max) {
      return Math.min(Math.max(pos - offset, min), max);
    }
  }

  /**
   * Drop-in replacement for JBLoadingPanel with ability to display a message when loading
   * of the widget failed.
   */
  private static class MyLoadingPanel extends JPanel {
    private final JBLoadingPanel myLoadingPanel;
    private final MyMessagePanel myMessagePanel;
    private boolean myShowingMessagePanel;

    MyLoadingPanel(@Nullable LayoutManager manager, @NotNull Disposable parent, @SuppressWarnings("SameParameterValue") int startDelayMs) {
      super(new BorderLayout());
      myMessagePanel = new MyMessagePanel();
      myLoadingPanel = new JBLoadingPanel(manager, parent, startDelayMs);
      super.add(myLoadingPanel);
    }

    public void startLoading() {
      resumeLoading();
      myLoadingPanel.startLoading();
    }

    public void stopLoading() {
      resumeLoading();
      myLoadingPanel.stopLoading();
    }

    public void setLoadingText(String text) {
      myLoadingPanel.setLoadingText(text);
    }

    @Override
    public Component add(Component comp) {
      return myLoadingPanel.add(comp);
    }

    @Override
    public Component add(Component comp, int index) {
      return myLoadingPanel.add(comp, index);
    }

    @Override
    public void add(Component comp, Object constraints) {
      myLoadingPanel.add(comp, constraints);
    }

    @Override
    public Dimension getPreferredSize() {
      return myLoadingPanel.getPreferredSize();
    }

    /**
     * Replaces loading animation with the given message.
     */
    void abortLoading(String message, @SuppressWarnings("SameParameterValue") Icon icon) {
      myMessagePanel.setText(message);
      myMessagePanel.setIcon(icon);
      if (!myShowingMessagePanel) {
        super.remove(myLoadingPanel);
        super.add(myMessagePanel);
        myShowingMessagePanel = true;
      }
    }

    private void resumeLoading() {
      if (myShowingMessagePanel) {
        super.remove(myMessagePanel);
        super.add(myLoadingPanel);
        myShowingMessagePanel = false;
      }
    }
  }

  private static class MyMessagePanel extends JPanel {
    private final JLabel myText = new JLabel("", SwingConstants.CENTER);

    MyMessagePanel() {
      super(new BorderLayout());
      setOpaque(false);

      // Similar to JBLoadingPanel.customizeStatusText but with a smaller font.
      Font font = myText.getFont();
      myText.setFont(font.deriveFont(font.getStyle(), font.getSize() + 4));
      myText.setForeground(ColorUtil.toAlpha(UIUtil.getLabelForeground(), 150));

      add(myText);
    }

    public void setText(String text) {
      myText.setText(text);
    }

    public void setIcon(Icon icon) {
      myText.setIcon(icon);
    }
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
