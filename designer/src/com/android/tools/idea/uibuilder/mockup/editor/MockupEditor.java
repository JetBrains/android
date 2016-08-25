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

import com.android.tools.idea.uibuilder.mockup.Mockup;
import com.android.tools.idea.uibuilder.mockup.editor.tools.CropTool;
import com.android.tools.idea.uibuilder.mockup.editor.tools.ExtractWidgetTool;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DesignSurfaceListener;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBColor;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Panel displaying the mockup and allowing actions like
 * cropping and widget extraction
 */
public class MockupEditor extends JPanel {

  public static final Logger LOG = Logger.getInstance(MockupEditor.class);
  private static final String TITLE = "Mockup Editor";
  private static final Dimension MINIMUM_SIZE = new Dimension(100, 100);
  private final myModelListener myModelListener;

  @Nullable private NlModel myModel;
  @Nullable private Mockup myMockup;

  private final List<MockupEditorListener> myEditorListeners = new ArrayList<>();
  private final Set<Tool> myActiveTools = new HashSet<>();
  private final ExtractWidgetTool myExtractWidgetTool;
  private final Mockup.MockupModelListener myMockupListener;

  // UI
  private final MockupViewPanel myMockupViewPanel;
  private final MyTopBar myTopBar;

  public MockupEditor(@NotNull DesignSurface surface, @Nullable NlModel model) {
    super(new BorderLayout());
    myMockupListener = this::notifyListeners;
    myModelListener = new myModelListener(this);
    myMockupViewPanel = new MockupViewPanel(this);
    myExtractWidgetTool = new ExtractWidgetTool(surface, this);
    setModel(model);
    surface.addListener(new MyDesignSurfaceListener(this));

    add(myMockupViewPanel, BorderLayout.CENTER);
    myTopBar = new MyTopBar(new CropTool(this));
    add(myTopBar, BorderLayout.NORTH);
    myExtractWidgetTool.enable(this);
    setMinimumSize(MINIMUM_SIZE);
    initSelection();
  }

  private void initSelection() {
    if (myModel != null) {
      List<NlComponent> selection = myModel.getSelectionModel().getSelection();
      if (selection.isEmpty()) {
        selection = myModel.getComponents();
      }
      selectionUpdated(myModel, selection);
    }
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
    if(myMockup == null || selectedComponent != myMockup.getComponent()) {
      showMockupInEditor(selectedComponent != null
                         ? Mockup.create(selectedComponent, false)
                         : null);
    }
  }

  /**
   * Reset the editor as it was just opened, but with a new mockup
   *
   * @param mockup the new mockup to display in the editor
   */
  private void showMockupInEditor(@Nullable Mockup mockup) {
    if (mockup != myMockup) {
      setMockup(mockup);
    }
    resetTools();
    notifyListeners(mockup);
  }

  private void setMockup(@Nullable Mockup mockup) {
    if (myMockup != null) {
      myMockup.removeMockupListener(myMockupListener);
    }
    myMockup = mockup;
    if (myMockup != null) {
      myMockup.addMockupListener(myMockupListener);
    }
  }

  private void notifyListeners(Mockup mockup) {
    myEditorListeners.forEach(listener -> listener.editorUpdated(mockup));
  }

  public void addListener(@NotNull MockupEditorListener listener) {
    if (!myEditorListeners.contains(listener)) {
      myEditorListeners.add(listener);
    }
  }

  public void removeListener(@NotNull MockupEditorListener listener) {
    myEditorListeners.remove(listener);
  }

  @NotNull
  public MockupViewPanel getMockupViewPanel() {
    return myMockupViewPanel;
  }

  /**
   * Disable every currently active tool and
   * enable only the default one
   */
  private void resetTools() {
    for (Tool activeTool : myActiveTools) {
      activeTool.disable(this);
    }
    myActiveTools.clear();
    if (myMockup != null) {
      myExtractWidgetTool.enable(this);
    }
  }

  /**
   * Disable tool and enable default tool
   *
   * @param tool the tool to disable
   */
  public void disableTool(@NotNull Tool tool) {
    tool.disable(this);
    myActiveTools.remove(tool);
    if (myActiveTools.isEmpty()) {
      myExtractWidgetTool.enable(this);
    }
  }

  /**
   * Disable default tool and enable tool
   *
   * @param tool the tool to enable
   */
  public void enableTool(@NotNull Tool tool) {
    myExtractWidgetTool.disable(this);
    tool.enable(this);
    myActiveTools.add(tool);
  }

  @Nullable
  public Mockup getMockup() {
    return myMockup;
  }

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
    List<NlComponent> selection = myModel != null
                                  ? myModel.getSelectionModel().getSelection()
                                  : Collections.emptyList();

    selectionUpdated(myModel, selection);
  }

  public void showError(String message) {
    if(myTopBar != null) {
      myTopBar.showError(message);
    }
  }

  /**
   * Tool used in the mockup editor
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
    public void screenChanged(@NotNull DesignSurface surface, @Nullable ScreenView screenView) {
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

    MyTopBar(CropTool cropTool) {
      super(new BorderLayout());
      add(createTitleBar(), BorderLayout.NORTH);
      add(createActionBar(cropTool), BorderLayout.SOUTH);
      myErrorTimer = new Timer(ERROR_MESSAGE_DISPLAY_DURATION, e -> showError(""));
      myErrorTimer.setRepeats(false);
    }

    @NotNull
    private JPanel createActionBar(CropTool cropTool) {
      JPanel actionBar = new JPanel(new BorderLayout());
      actionBar.add(cropTool, BorderLayout.EAST);
      myErrorLabel = new JLabel();
      myErrorLabel.setForeground(JBColor.RED);
      actionBar.add(myErrorLabel, BorderLayout.WEST);
      actionBar.setBorder(new CompoundBorder(
        IdeBorderFactory.createBorder(SideBorder.BOTTOM),
        IdeBorderFactory.createEmptyBorder(0, 10, 0, 5)));
      return actionBar;
    }

    @NotNull
    private static JPanel createTitleBar() {
      JPanel titleBar = new JPanel(new BorderLayout());

      titleBar.add(new JBLabel(TITLE), BorderLayout.WEST);
      titleBar.setBorder(new CompoundBorder(
        IdeBorderFactory.createBorder(SideBorder.BOTTOM),
        IdeBorderFactory.createEmptyBorder(4, 5, 4, 10)));
      return titleBar;
    }

    private void showError(String message) {
      UIUtil.invokeLaterIfNeeded(() -> myErrorLabel.setText(message));
      if(!message.isEmpty()) {
        myErrorTimer.restart();
      } else {
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

  private static class myModelListener implements ModelListener {
    private final MockupEditor myMockupEditor;

    public myModelListener(MockupEditor mockupEditor) {
      myMockupEditor = mockupEditor;
    }

    @Override
    public void modelChanged(@NotNull NlModel model) {
      myMockupEditor.selectionUpdated(model, model.getSelectionModel().getSelection());
    }

    @Override
    public void modelRendered(@NotNull NlModel model) {
      myMockupEditor.selectionUpdated(model, model.getSelectionModel().getSelection());
    }
  }
}
