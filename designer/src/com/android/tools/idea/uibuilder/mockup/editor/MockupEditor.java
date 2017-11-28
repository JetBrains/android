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
package com.android.tools.idea.uibuilder.mockup.editor;

import com.android.tools.adtui.workbench.ToolContent;
import com.android.tools.idea.actions.MockupEditAction;
import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.editor.tools.CropTool;
import com.android.tools.idea.uibuilder.mockup.editor.tools.ExtractWidgetTool;
import com.android.tools.idea.uibuilder.mockup.editor.tools.SelectionEditors;
import com.android.tools.idea.uibuilder.scene.RenderListener;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.*;
import java.util.List;

/**
 * <p>
 * The mockup editor is the core of the user interaction with the mockup.
 * </p>
 *
 * <p>
 * By default, it allows to do a selection on the image contained in the current Mockup set with {@link #setMockup(Mockup)}.
 * The behavior of the the editor can be changed by implementing {@link Tool}.
 * </p>
 */
public class MockupEditor extends JPanel implements ToolContent<DesignSurface>, RenderListener {

  private static final String TITLE = "Mockup Editor";
  private static final String NO_MOCKUP_TEXT = "<html>No mockup available for this View.<br/>Click to add mockup</html>";
  private static final Dimension MINIMUM_SIZE = new Dimension(100, 100);

  private static final String CARD_MOCKUP_VIEW_PANEL = "mockupViewPanel";
  private static final String CARD_NO_MOCKUP = "noMockup";

  private final MyModelListener myModelListener;
  private final SelectionEditors mySelectionEditors;
  private final List<MockupEditorListener> myEditorListeners = new ArrayList<>();
  private final Set<Tool> myActiveTools = new HashSet<>();
  private final ExtractWidgetTool myExtractWidgetTool;
  private final Mockup.MockupModelListener myMockupListener;
  private final DesignSurfaceListener myDesignSurfaceListener;
  private final JPanel myCenterPanel;
  private final CardLayout myCenterCardLayout;

  @Nullable private NlDesignSurface mySurface;
  @Nullable private NlModel myModel;
  @Nullable private Mockup myMockup;
  // UI
  private final MockupViewPanel myMockupViewPanel;
  private final MyTopBar myTopBar;
  private final JPanel myBottomPanel;

  /**
   * Create a new mockup editor associated with the provided NlDesignSurface.
   * If a model is available at creation time, it can be provided as a parameter, otherwise
   * the design surface will notify the {@link MockupEditor} when the model is changed. ({@link DesignSurfaceListener}).
   */
  public MockupEditor() {
    super(new BorderLayout());
    JLabel addMockupIcon = createNoMockupIcon(createAddMockupMouseAdapter());
    myMockupListener = (mockup, changedFlags) -> notifyListeners(mockup);
    myBottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    myModelListener = new MyModelListener(this);
    myMockupViewPanel = new MockupViewPanel(this);
    myExtractWidgetTool = new ExtractWidgetTool(this);
    mySelectionEditors = new SelectionEditors(myMockupViewPanel);
    myTopBar = new MyTopBar(new CropTool(this), mySelectionEditors);

    myCenterCardLayout = new CardLayout();
    myCenterPanel = new JPanel(myCenterCardLayout);
    myCenterPanel.add(myMockupViewPanel, CARD_MOCKUP_VIEW_PANEL);
    myCenterPanel.add(addMockupIcon, CARD_NO_MOCKUP);
    add(myTopBar, BorderLayout.NORTH);
    add(myCenterPanel, BorderLayout.CENTER);

    add(myBottomPanel, BorderLayout.SOUTH);

    myDesignSurfaceListener = new MyDesignSurfaceListener(this);

    myExtractWidgetTool.enable(this);
    setMinimumSize(MINIMUM_SIZE);
    initSelection();
  }

  /**
   * Get the first selected component if there is one, else the root component of the model
   */
  private void initSelection() {
    if (mySurface != null && myModel != null) {
      List<NlComponent> selection = mySurface.getSelectionModel().getSelection();
      if (selection.isEmpty()) {
        selection = myModel.getComponents();
      }
      selectionUpdated(myModel, selection);
    }
  }

  /**
   * Create a text and Icon wo display when no mockup is available
   *
   * @param listener
   */
  private static JLabel createNoMockupIcon(MouseListener listener) {
    // TODO: add new Icons to StudioIcons and replace this.
    JLabel addMockupIcon = new JBLabel(NO_MOCKUP_TEXT, AndroidIcons.Mockup.NoMockup, SwingConstants.CENTER);
    addMockupIcon.setHorizontalTextPosition(SwingConstants.CENTER);
    addMockupIcon.setVerticalTextPosition(SwingConstants.BOTTOM);
    addMockupIcon.setIconTextGap(15);
    addMockupIcon.addMouseListener(listener);
    return addMockupIcon;
  }

  /**
   * Update the currently displayed mockup with the new selection
   *
   * @param model     The model where the selection has been made
   * @param selection The selected component
   */
  private void selectionUpdated(@Nullable NlModel model, @NotNull List<NlComponent> selection) {
    NlComponent selectedComponent = null;
    if (!selection.isEmpty()) {
      selectedComponent = selection.get(0);
    }
    else if (model != null) {
      selection = model.getComponents();
      if (!selection.isEmpty()) {
        selectedComponent = selection.get(0);
      }
    }
    if (myMockup == null || selectedComponent != myMockup.getComponent()) {
      showMockupInEditor(selectedComponent != null
                         ? Mockup.create(selectedComponent, false)
                         : null);
    }
    else if (!Mockup.hasMockupAttribute(myMockup.getComponent())) {
      showNoMockup(true);
    }
    else {
      showNoMockup(false);
    }
  }

  /**
   * Reset the editor as it was just opened, but with a new mockup
   *
   * @param mockup the new mockup to display in the editor
   */
  private void showMockupInEditor(@Nullable Mockup mockup) {
    showNoMockup(mockup == null);
    if (mockup != myMockup) {
      setMockup(mockup);
    }
    resetTools();
    notifyListeners(mockup);
  }

  /**
   * Set the mockup and attach a listener to it.
   *
   * @param mockup The new mockup
   */
  private void setMockup(@Nullable Mockup mockup) {
    if (myMockup != null) {
      myMockup.removeMockupListener(myMockupListener);
    }
    myMockup = mockup;
    if (myMockup != null) {
      myMockup.addMockupListener(myMockupListener);
    }
  }

  private void showNoMockup(boolean show) {
    myCenterCardLayout.show(myCenterPanel, show ? CARD_NO_MOCKUP : CARD_MOCKUP_VIEW_PANEL);
  }

  /**
   * Notify every {@link MockupEditorListener} attached to this instance that there has been
   * an update regarding the mockup.
   *
   * @param mockup The new or updated mockup.
   */
  private void notifyListeners(Mockup mockup) {
    myEditorListeners.forEach(listener -> listener.editorUpdated(mockup));
  }

  /**
   * Add a {@link MockupEditorListener} to get notification about any change regarding the mockup.
   *
   * @param listener The listener to attach to the mockup.
   */
  public void addListener(@NotNull MockupEditorListener listener) {
    if (!myEditorListeners.contains(listener)) {
      myEditorListeners.add(listener);
    }
  }

  /**
   * Remove a previously added listener
   *
   * @param listener the listener to remove
   */
  public void removeListener(@NotNull MockupEditorListener listener) {
    myEditorListeners.remove(listener);
  }

  @NotNull
  private MouseAdapter createAddMockupMouseAdapter() {
    return new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        if (myModel != null && mySurface != null) {
          List<NlComponent> selection = mySurface.getSelectionModel().getSelection();
          if (selection.isEmpty()) {
            mySurface.getSelectionModel().setSelection(myModel.getComponents());
          }
          if (!selection.isEmpty()) {
            MockupEditAction action = new MockupEditAction(mySurface);
            AnActionEvent event = new AnActionEvent(
              null, DataContext.EMPTY_CONTEXT, "", action.getTemplatePresentation().clone(), ActionManager.getInstance(), 0);
            action.update(event);
            action.actionPerformed(event);
          }
        }
      }
    };
  }

  /**
   * Get the {@link MockupViewPanel} displayed in the editor.
   *
   * @return the {@link MockupViewPanel} displayed in the editor.
   */
  @NotNull
  public MockupViewPanel getMockupViewPanel() {
    return myMockupViewPanel;
  }

  /**
   * Disable every currently active tools and
   * enable only the default one ({@link ExtractWidgetTool})
   */
  private void resetTools() {
    for (Tool activeTool : myActiveTools) {
      activeTool.disable(this);
    }
    myActiveTools.clear();
    if (myMockup != null) {
      myExtractWidgetTool.enable(this);
    }
    myMockupViewPanel.getSelectionLayer().clearSelection();
    mySelectionEditors.setVisible(false);
  }

  /**
   * Disable tool and enable default tool if no other tool is enabled
   *
   * @param tool the tool to disable
   */
  public void disableTool(@NotNull Tool tool) {
    tool.disable(this);
    myActiveTools.remove(tool);
    if (myActiveTools.isEmpty()) {
      myActiveTools.add(myExtractWidgetTool);
      myExtractWidgetTool.enable(this);
    }
    myBottomPanel.removeAll();
    validate();
  }

  /**
   * Disable default tool and enable tool
   *
   * @param tool the tool to enable
   */
  public void enableTool(@NotNull Tool tool) {
    myActiveTools.remove(myExtractWidgetTool);
    myExtractWidgetTool.disable(this);
    tool.enable(this);
    myActiveTools.add(tool);
  }

  /**
   * Get the current mockup displayed in the editor if any.
   *
   * @return the current mockup displayed in the editor if any.
   */
  @Nullable
  public Mockup getMockup() {
    return myMockup;
  }

  /**
   * Set the current model associated with the editor. Typically, this is the model
   * retrieve from the Design surface screen view{@link NlDesignSurface#getCurrentSceneView()}
   * or from the {@link NlComponent} of the {@link Mockup#getComponent()}
   *
   * @param model The model to set on this instance
   */
  private void setModel(@Nullable NlModel model) {
    if (model == myModel) {
      return;
    }
    if (myModel != null) {
      myModel.removeListener(myModelListener);
    }
    myModel = model;
    if (myModel != null) {
      myModel.addListener(myModelListener);
    }
  }

  /**
   * Displays an error in the mockup editor for {@value MyTopBar#ERROR_MESSAGE_DISPLAY_DURATION}ms
   *
   * @param message The message to display
   */
  public void showError(String message) {
    if (myTopBar != null) {
      myTopBar.showError(message);
    }
  }

  public void setSelectionText(Rectangle selection) {
    mySelectionEditors.setSelection(selection);
  }

  public void addBottomControls(JComponent component) {
    myBottomPanel.add(component);
    validate();
  }

  @Override
  public void dispose() {
  }

  @Override
  public void setToolContext(@Nullable DesignSurface newDesignSurface) {
    assert newDesignSurface == null || newDesignSurface instanceof NlDesignSurface;
    if (mySurface != null) {
      SceneManager manager = mySurface.getSceneManager();
      if (manager != null) {
        manager.removeRenderListener(this);
      }
      mySurface.removeListener(myDesignSurfaceListener);
      mySurface = null;
      myExtractWidgetTool.setDesignSurface(null);
    }
    SceneView sceneView = newDesignSurface != null ? newDesignSurface.getCurrentSceneView() : null;
    if (sceneView != null) {
      mySurface = (NlDesignSurface)newDesignSurface;
      SceneManager manager = mySurface.getSceneManager();
      if (manager != null) {
        manager.addRenderListener(this);
      }
      mySurface.addListener(myDesignSurfaceListener);
      setModel(sceneView.getModel());
      myExtractWidgetTool.setDesignSurface(mySurface);
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return this;
  }

  @Override
  public void onRenderCompleted() {
    UIUtil.invokeLaterIfNeeded(
      () -> {
        if (mySurface != null) {
          selectionUpdated(myModel, mySurface.getSelectionModel().getSelection());
        }
      });
  }

  /**
   * A tool is an extension to the {@link MockupEditor}. Each tool is responsible to set
   * the desired state of the MockupEditor when enabled and reset it when disabled.
   *
   * A tool can disabled itself using {@link MockupEditor#disableTool(Tool)}.
   *
   * If needed, a tool can add a {@link MockupViewLayer} to display information in the editor
   */
  public interface Tool {

    /**
     * The implementing class should set mockupViewPanel to the
     * needed state for itself
     *
     * @param mockupEditor The {@link MockupEditor} on which the {@link Tool} behave
     */
    void enable(@NotNull MockupEditor mockupEditor);

    /**
     * The implementing class should reset mockupViewPanel to the state it was before {@link #enable(MockupEditor)}.
     * Can use {@link MockupViewPanel#resetState()}.
     * needed state for itself
     *
     * @param mockupEditor The {@link MockupEditor} on which the {@link Tool} behave
     */
    void disable(@NotNull MockupEditor mockupEditor);
  }

  /**
   * Listener to update the editor when the selection or model has changed
   */
  private static class MyDesignSurfaceListener implements DesignSurfaceListener {
    MockupEditor myEditor;

    public MyDesignSurfaceListener(@NotNull MockupEditor editor) {
      myEditor = editor;
    }

    @Override
    public void componentSelectionChanged(@NotNull DesignSurface surface, @NotNull List<NlComponent> newSelection) {
      myEditor.selectionUpdated(myEditor.myModel, newSelection);
    }

    @Override
    public void sceneChanged(@NotNull DesignSurface surface, @Nullable SceneView sceneView) {
    }

    @Override
    public void modelChanged(@NotNull DesignSurface surface, @Nullable NlModel model) {
      myEditor.setModel(model);
    }


    @Override
    public boolean activatePreferredEditor(@NotNull DesignSurface surface, @NotNull NlComponent component) {
      return false;
    }
  }

  /**
   * Bar on top showing the title and actions
   */
  private static class MyTopBar extends JPanel {
    public static final int ERROR_MESSAGE_DISPLAY_DURATION = 2000;
    private JLabel myErrorLabel;
    private Timer myErrorTimer;

    MyTopBar(@NotNull CropTool cropTool, @NotNull SelectionEditors selectionEditors) {
      super(new BorderLayout());
      add(createTitleBar(cropTool), BorderLayout.NORTH);
      add(createActionBar(selectionEditors), BorderLayout.SOUTH);
      myErrorTimer = new Timer(ERROR_MESSAGE_DISPLAY_DURATION, e -> showError(""));
      myErrorTimer.setRepeats(false);
      setPreferredSize(new Dimension(100, 70));
      setMinimumSize(getPreferredSize());
    }

    @NotNull
    private JPanel createActionBar(SelectionEditors selectionEditors) {
      JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
      actionBar.add(selectionEditors);
      myErrorLabel = new JLabel();
      myErrorLabel.setForeground(JBColor.RED);
      actionBar.add(myErrorLabel, BorderLayout.WEST);
      actionBar.setBorder(JBUI.Borders.empty(0, 10, 0, 5));
      return actionBar;
    }

    @NotNull
    private static JPanel createTitleBar(CropTool cropTool) {
      JPanel titleBar = new JPanel(new BorderLayout());
      titleBar.add(new JBLabel(TITLE), BorderLayout.WEST);
      titleBar.add(cropTool, BorderLayout.EAST);
      titleBar.setBorder(JBUI.Borders.empty(0, 5, 0, 10));
      return titleBar;
    }

    private void showError(String message) {
      UIUtil.invokeLaterIfNeeded(() -> myErrorLabel.setText(message));
      if (!message.isEmpty()) {
        myErrorTimer.restart();
      }
      else {
        myErrorTimer.stop();
      }
    }
  }

  /**
   * Notify when the currently displayed mockup has been changed
   */
  public interface MockupEditorListener {
    void editorUpdated(@Nullable Mockup mockup);
  }

  private static class MyModelListener implements ModelListener {
    private final MockupEditor myMockupEditor;

    public MyModelListener(MockupEditor mockupEditor) {
      myMockupEditor = mockupEditor;
    }

    @Override
    public void modelDerivedDataChanged(@NotNull NlModel model) {
      processModelChange(model);
    }

    @Override
    public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
      // Do nothing
    }

    private void processModelChange(@NotNull NlModel model) {
    }
  }
}
