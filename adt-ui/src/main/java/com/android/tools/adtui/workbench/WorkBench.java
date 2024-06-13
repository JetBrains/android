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

import com.android.annotations.Nullable;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.stdui.ActionData;
import com.android.tools.adtui.stdui.UrlData;
import com.android.tools.adtui.workbench.AttachedToolWindow.ButtonDragListener;
import com.android.tools.adtui.workbench.AttachedToolWindow.DragEvent;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.components.JBLayeredPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.accessibility.ScreenReader;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.LayoutFocusTraversalPolicy;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

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
  private static final Logger LOG = Logger.getInstance(WorkBench.class);

  private final String myName;
  private final PropertiesComponent myPropertiesComponent;
  private final WorkBenchManager myWorkBenchManager;
  private final DetachedToolWindowManager myDetachedToolWindowManager;
  private final FileEditorManager myFileEditorManager;
  private final List<ToolWindowDefinition<T>> myToolDefinitions;
  private final SideModel<T> myModel;
  private final ThreeComponentsSplitter mySplitter;
  private final WorkBenchLoadingPanel myLoadingPanel;
  private final JPanel myMainPanel;
  private final MinimizedPanel<T> myLeftMinimizePanel;
  private final MinimizedPanel<T> myRightMinimizePanel;
  private final ButtonDragListener<T> myButtonDragListener;
  private final PropertyChangeListener myMyPropertyChangeListener = this::handlePropertyEvent;
  private FileEditor myFileEditor;
  private boolean isDisposed = false;

  @NotNull private String myContext = "";

  /**
   * Creates a work space with associated tool windows, which can be attached.
   *
   * @param project the project associated with this work space.
   * @param name a name used to identify this type of {@link WorkBench}. Also used for associating properties.
   * @param fileEditor the file editor this work space is associated with.
   * @param parentDisposable the parent {@link Disposable} this WorkBench will be attached to.
   * @param delayTimeMs milliseconds to wait before switching to the loading mode of the {@link WorkBench}.
   */
  public WorkBench(@NotNull Project project, @NotNull String name, @Nullable FileEditor fileEditor, @NotNull Disposable parentDisposable, int delayTimeMs) {
    this(project, name, fileEditor, InitParams.createParams(project), DetachedToolWindowManager.getInstance(project), delayTimeMs);

    Disposer.register(parentDisposable, this);
  }

  /**
   * Creates a work space with associated tool windows, which can be attached.
   *
   * @param project the project associated with this work space.
   * @param name a name used to identify this type of {@link WorkBench}. Also used for associating properties.
   * @param fileEditor the file editor this work space is associated with.
   * @param parentDisposable the parent {@link Disposable} this WorkBench will be attached to.
   */
  public WorkBench(@NotNull Project project, @NotNull String name, @Nullable FileEditor fileEditor, @NotNull Disposable parentDisposable) {
    this(project, name, fileEditor, parentDisposable, 1000);
  }

  /**
   * Initializes a {@link WorkBench} with content, context and tool windows.
   *
   * @param content the content of the main area of the {@link WorkBench}
   * @param context an instance identifying the data the {@link WorkBench} is manipulating
   * @param definitions a list of tool windows associated with this {@link WorkBench}
   * @param minimizedWindows whether the tool windows should be minimized by default.
   */
  public void init(@NotNull JComponent content,
                   @NotNull T context,
                   @NotNull List<ToolWindowDefinition<T>> definitions,
                   boolean minimizedWindows) {
    mySplitter.setInnerComponent(content);
    init(context, definitions, minimizedWindows);
  }

  /**
   * Initializes a {@link WorkBench} with context and tool windows.
   * The main content is not provided, so only the tool windows will be added.
   *
   * @param context an instance identifying the data the {@link WorkBench} is manipulating
   * @param definitions a list of tool windows associated with this {@link WorkBench}
   * @param minimizedWindows whether the tool windows should be minimized by default.
   */
  public void init(@NotNull T context,
                   @NotNull List<ToolWindowDefinition<T>> definitions,
                   boolean minimizedWindows) {
    LOG.debug("init");
    if (ScreenReader.isActive()) {
      setFocusCycleRoot(true);
      setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
    }
    myLoadingPanel.stopLoading();
    myMainPanel.setVisible(true);
    mySplitter.addDividerResizeListener(createWidthUpdater());
    myToolDefinitions.addAll(definitions);
    mySplitter.setFirstSize(getInitialSideWidth(Side.LEFT));
    if (mySplitter.getInnerComponent() != null) {
      mySplitter.setLastSize(getInitialSideWidth(Side.RIGHT));
    }
    myModel.setContext(context);
    addToolsToModel(minimizedWindows);
    if (!isDisposed) {
      myWorkBenchManager.register(this);
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusOwner", myMyPropertyChangeListener);
    }
    myDetachedToolWindowManager.updateToolWindowsForWorkBench(this);
    if (mySplitter.getInnerComponent() == null) {
      // Remove the borders from one of the side panels if there is no center component:
      Objects.requireNonNull(mySplitter.getFirstComponent()).setBorder(JBUI.Borders.empty());
    }
  }

  @NotNull
  public JComponent getComponent() {
    return myMainPanel;
  }

  @Override
  public void updateUI() {
    // The following components do not receive "updateUI" when the swing theme is changed.
    // This will fix color issues in the MinimizePanel such as: b/196026112
    myLoadingPanel.updateUI();
    myMainPanel.updateUI();
    myLeftMinimizePanel.updateUI();
    myRightMinimizePanel.updateUI();
    for (AttachedToolWindow<T> tool : myModel.getAllTools()) {
      tool.getMinimizedButton().updateUI();
    }
  }

  public void setLoadingText(@NotNull String loadingText) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("setLoadingText: " + loadingText);
    }
    myLoadingPanel.setLoadingText(loadingText);
  }

  public void showLoading(@NotNull String message) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("showLoading: " + message);
    }
    setLoadingText(message);
    myLoadingPanel.startLoading();
  }

  public void hideLoading() {
    LOG.debug("hideLoading");
    myLoadingPanel.stopLoading();
  }

  /**
   * Shows the default empty content panel with the given message and a warning icon.
   * The message can contain multiple lines.
   */
  public void loadingStopped(@NotNull String message) {
    loadingStopped(message, null);
  }

  /**
   * Shows the default empty content panel with the given message, a warning icon and the optional {@link ActionData}.
   * The message can contain multiple lines.
   */
  public void loadingStopped(@NotNull String message, @Nullable ActionData actionData) {
    loadingStopped(message, AllIcons.General.Warning, null, actionData);
  }

  /**
   * Shows the default empty content panel with the given message and optionals icon and {@link ActionData}.
   * The message can contain multiple lines.
   */
  public void loadingStopped(@NotNull String message, @Nullable Icon icon, @Nullable UrlData urlData, @Nullable ActionData actionData) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("loadingStopped " + message);
    }
    myLoadingPanel.abortLoading(message, icon, urlData, actionData);
  }

  /**
   * Returns true if the WorkBench is displaying a message. The WorkBench will display a message when in one of two states:
   * <ul>
   *   <li>Loading state initiated by calling {@link WorkBench#showLoading}</li>
   *   <li>Error state initiated by calling {@link WorkBench#loadingStopped(String)}</li>
   * </ul>
   */
  public boolean isMessageVisible() {
    return myLoadingPanel.isLoadingOrHasError();
  }

  @TestOnly
  public WorkBenchLoadingPanel getLoadingPanel() {
    return myLoadingPanel;
  }

  /**
   * Normally the context is constant.
   * Currently needed for the designer preview pane.
   */
  public void setToolContext(@Nullable T context) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("setToolContext " + context);
    }
    myModel.setContext(context);
  }

  /**
   * Normally the {@link FileEditor} is constant.
   * Currently needed for the designer preview pane.
   */
  public void setFileEditor(@Nullable FileEditor fileEditor) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("setFileEditor " + fileEditor);
    }
    myDetachedToolWindowManager.unregister(myFileEditor);
    myDetachedToolWindowManager.register(fileEditor, this);
    myFileEditor = fileEditor;
    if (fileEditor != null && isCurrentEditor(fileEditor)) {
      myDetachedToolWindowManager.updateToolWindowsForWorkBench(this);
    }
  }

  @Override
  public void dispose() {
    LOG.debug("dispose");
    isDisposed = true;
    myWorkBenchManager.unregister(this);
    myDetachedToolWindowManager.unregister(myFileEditor);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", myMyPropertyChangeListener);
    setToolContext(null);

    // Clean up all the children panels to avoid accidental memory leaks.
    myMainPanel.removeAll();
    mySplitter.setInnerComponent(null);
    mySplitter.setFirstComponent(null);
    mySplitter.setLastComponent(null);
    mySplitter.removeAll();
    myLoadingPanel.removeAll();
  }

  // ----------------------------------- Implementation --------------------------------------------------------------- //

  @VisibleForTesting
  WorkBench(@NotNull Project project,
            @NotNull String name,
            @Nullable FileEditor fileEditor,
            @NotNull InitParams<T> params,
            @NotNull DetachedToolWindowManager detachedToolWindowManager,
            int startDelayMs) {
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
    myLoadingPanel = new WorkBenchLoadingPanel(new BorderLayout(), this, startDelayMs);
    myLoadingPanel.add(myMainPanel);
    Disposer.register(this, layeredPanel);
    add(myLoadingPanel, JLayeredPane.DEFAULT_LAYER);
    myMainPanel.setVisible(false);
    myLoadingPanel.startLoading();
    setFocusCycleRoot(true);
    setFocusTraversalPolicyProvider(true);
    setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
  }

  private boolean isCurrentEditor(@NotNull FileEditor fileEditor) {
    for (FileEditor editor : myFileEditorManager.getSelectedEditors()) {
      if (fileEditor == editor) {
        return true;
      }
    }
    return false;
  }

  private void handlePropertyEvent(@NotNull PropertyChangeEvent event) {
    updateDetachedToolWindows(event);
    autoHide(event);
  }

  private void updateDetachedToolWindows(@NotNull PropertyChangeEvent event) {
    Object newValue = event.getNewValue();
    if (!(newValue instanceof JComponent)) {
      return;
    }
    JComponent newComponent = (JComponent)newValue;
    JComponent oldComponent = event.getOldValue() instanceof JComponent ? (JComponent)event.getOldValue() : null;
    // If a component inside this WorkBench got the focus and it didn't already have the focus, we want to show
    // our detached (floating) tool windows and hide detached tool windows from other workbenches.
    if (SwingUtilities.isDescendingFrom(newComponent, this) &&
        (oldComponent == null || !SwingUtilities.isDescendingFrom(oldComponent, this))) {
      myDetachedToolWindowManager.updateToolWindowsForWorkBench(this);
    }
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
    splitter.setFocusCycleRoot(false);
    splitter.setFocusTraversalPolicyProvider(true);
    splitter.setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
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
    int minCenterWidth = content != null ? Math.max(content.getMinimumSize().width, ToolWindowDefinition.DEFAULT_SIDE_WIDTH) : 0;
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

  private void addToolsToModel(boolean minimizedWindows) {
    List<AttachedToolWindow<T>> tools = new ArrayList<>(myToolDefinitions.size());
    for (ToolWindowDefinition<T> definition : myToolDefinitions) {
      AttachedToolWindow<T> toolWindow =
        new AttachedToolWindow<>(definition, myButtonDragListener, this, myModel, minimizedWindows);
      tools.add(toolWindow);
    }
    setDefaultOrderIfMissing(tools);
    restoreToolOrder(tools);
    myModel.setTools(tools);
  }

  /**
   * Hide the content of WorkBench so only loading icon and loading message are displayable.
   */
  public void hideContent() {
    myMainPanel.setVisible(false);
  }

  /**
   * Show the content in WorkBench. This also hide the loading icon and message if they are showing.
   *
   * @return true if the content panel was not visible before.
   */
  public boolean showContent() {
    hideLoading();
    boolean wasVisible = myMainPanel.isVisible();
    myMainPanel.setVisible(true);

    return !wasVisible;
  }

  @TestOnly
  public boolean isShowingContent() {
    return myMainPanel.isShowing();
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

  public void setContext(@NotNull String context) {
    myContext = context;
  }

  /**
   * Sets default properties for the context in case they're not set yet and updates the model to reflect the changes.
   */
  public void setDefaultPropertiesForContext(boolean minimizedByDefault) {
    List<AttachedToolWindow<T>> tools = myModel.getAllTools();
    if (tools.isEmpty()) {
      return;
    }
    tools.forEach((tool) -> {
      tool.setDefaultProperty(AttachedToolWindow.PropertyType.LEFT, tool.getDefinition().getSide().isLeft());
      tool.setDefaultProperty(AttachedToolWindow.PropertyType.SPLIT, tool.getDefinition().getSplit().isBottom());
      tool.setDefaultProperty(AttachedToolWindow.PropertyType.AUTO_HIDE, tool.getDefinition().getAutoHide().isAutoHide());
      tool.setDefaultProperty(AttachedToolWindow.PropertyType.MINIMIZED, minimizedByDefault);
    });
    updateModel();
  }

  /**
   * The same {@link WorkBench} can be used in different contexts. We need to store the context in order to (re)store different properties
   * accordingly. For example, in the split editor we might have a tool window being hidden in design mode but shown in split mode.
   */
  @NotNull
  public String getContext() {
    return myContext;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @TestOnly
  @Nullable
  public List<JComponent> getTopComponents(Side side) {
    return myModel.getTopTools(side).stream().map(AttachedToolWindow::getComponent).toList();
  }

  @TestOnly
  @Nullable
  public List<JComponent> getBottomComponents(Side side) {
    return myModel.getBottomTools(side).stream().map(AttachedToolWindow::getComponent).toList();
  }

  @TestOnly
  public void minimizeAllAttachedToolWindows() {
    myModel.getAllTools().forEach((window) -> window.setMinimized(true));
    updateModel();
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
