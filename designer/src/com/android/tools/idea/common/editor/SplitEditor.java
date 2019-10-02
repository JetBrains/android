/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.common.editor;

import static java.awt.event.InputEvent.ALT_DOWN_MASK;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.InputEvent.SHIFT_DOWN_MASK;
import static javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;

import com.android.tools.idea.common.surface.DesignSurface;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.ArrayUtil;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.KeyStroke;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link TextEditorWithPreview} in which {@link #myPreview} is a {@link DesignerEditor} and {@link #myEditor} contains the corresponding
 * XML file being displayed in the preview.
 */
public class SplitEditor extends TextEditorWithPreview implements TextEditor {

  @VisibleForTesting
  static final int ACTION_SHORTCUT_MODIFIERS = (SystemInfo.isMac ? CTRL_DOWN_MASK : ALT_DOWN_MASK) | SHIFT_DOWN_MASK;

  private static final String SPLIT_MODE_PROPERTY_PREFIX = "SPLIT_EDITOR_MODE";

  @NotNull
  private final Project myProject;

  @NotNull
  private final PropertiesComponent myPropertiesComponent = PropertiesComponent.getInstance();

  @NotNull
  private final DesignerEditor myDesignerEditor;

  @NotNull
  private final BackgroundEditorHighlighter myBackgroundEditorHighlighter = new CompoundBackgroundHighlighter();

  private final MyToolBarAction myTextViewAction =
    new MyToolBarAction("Code", AllIcons.General.LayoutEditorOnly, super.getShowEditorAction(), DesignSurface.State.DEACTIVATED);

  private final MyToolBarAction myDesignViewAction =
    new MyToolBarAction("Design", AllIcons.General.LayoutPreviewOnly, super.getShowPreviewAction(), DesignSurface.State.FULL);

  private final MyToolBarAction mySplitViewAction =
    new MyToolBarAction("Split", AllIcons.General.LayoutEditorPreview, super.getShowEditorAndPreviewAction(), DesignSurface.State.SPLIT);

  private final List<MyToolBarAction> myActions = ImmutableList.of(myTextViewAction, mySplitViewAction, myDesignViewAction);

  public SplitEditor(@NotNull TextEditor textEditor,
                     @NotNull DesignerEditor designerEditor,
                     @NotNull String editorName,
                     @NotNull Project project) {
    super(textEditor, designerEditor, editorName, Layout.SHOW_PREVIEW);
    myProject = project;
    myDesignerEditor = designerEditor;
    registerModeNavigationShortcuts();
    restoreSurfaceState();
  }

  private void restoreSurfaceState() {
    myActions.forEach((action -> {
      if (action.isSelected(getDummyActionEvent())) {
        myDesignerEditor.getComponent().getSurface().setState(action.mySurfaceState);
      }
    }));
  }

  @NotNull
  public DesignerEditor getDesignerEditor() {
    return myDesignerEditor;
  }

  @NotNull
  @Override
  protected ToggleAction getShowPreviewAction() {
    return myDesignViewAction;
  }

  @NotNull
  @Override
  protected ToggleAction getShowEditorAction() {
    return myTextViewAction;
  }

  @NotNull
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return myBackgroundEditorHighlighter;
  }

  @NotNull
  @Override
  protected ToggleAction getShowEditorAndPreviewAction() {
    return mySplitViewAction;
  }

  public boolean isSplitMode() {
    return mySplitViewAction.isSelected(getDummyActionEvent());
  }

  public boolean isDesignMode() {
    return myDesignViewAction.isSelected(getDummyActionEvent());
  }

  public boolean isTextMode() {
    return myTextViewAction.isSelected(getDummyActionEvent());
  }

  public void selectTextMode(boolean userExplicitlyTriggered) {
    selectAction(myTextViewAction, userExplicitlyTriggered);
  }

  public void selectDesignMode(boolean userExplicitlyTriggered) {
    selectAction(myDesignViewAction, userExplicitlyTriggered);
  }

  public void selectSplitMode(boolean userExplicitlyTriggered) {
    selectAction(mySplitViewAction, userExplicitlyTriggered);
  }

  private void selectAction(@NotNull MyToolBarAction action, boolean userExplicitlyTriggered) {
    action.setSelected(getDummyActionEvent(), true, userExplicitlyTriggered);
  }

  @NotNull
  private AnActionEvent getDummyActionEvent() {
    return new AnActionEvent(null, DataManager.getInstance().getDataContext(getComponent()), "", new Presentation(),
                             ActionManager.getInstance(),
                             0);
  }

  @NotNull
  private MyToolBarAction getSelectedAction() {
    for (MyToolBarAction action : myActions) {
      if (action.isSelected(getDummyActionEvent())) {
        return action;
      }
    }
    throw new IllegalStateException("No mode selected in SplitEditor.");
  }

  private void registerModeNavigationShortcuts() {
    myTextViewAction.setRightAction(mySplitViewAction);
    mySplitViewAction.setRightAction(myDesignViewAction);
    myDesignViewAction.setRightAction(myTextViewAction);

    Action navigateLeftAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        MyToolBarAction action = getSelectedAction();
        if (action.myLeftAction != null) {
          selectAction(action.myLeftAction, true);
        }
      }
    };
    final String navLeftInputKey = "navigate_split_editor_mode_left";
    getComponent().getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, ACTION_SHORTCUT_MODIFIERS), navLeftInputKey);
    getComponent().getActionMap().put(navLeftInputKey, navigateLeftAction);

    Action navigateRightAction = new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        MyToolBarAction action = getSelectedAction();
        if (action.myRightAction != null) {
          selectAction(action.myRightAction, true);
        }
      }
    };
    final String navRightInputKey = "navigate_split_editor_mode_right";
    getComponent().getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
      .put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, ACTION_SHORTCUT_MODIFIERS), navRightInputKey);
    getComponent().getActionMap().put(navRightInputKey, navigateRightAction);
  }

  @Override
  public void selectNotify() {
    super.selectNotify();
    // select/deselectNotify will be called when the user selects (clicks) or opens a new editor. However, in some cases, the editor might
    // be deselected but still visible. We first check whether we should pay attention to the select/deselect so we only do something if we
    // are visible.
    if (ArrayUtil.contains(this, FileEditorManager.getInstance(myProject).getSelectedEditors())) {
      myDesignerEditor.getComponent().activate();
    }
  }

  @Override
  public void deselectNotify() {
    super.deselectNotify();
    // If we are still visible but the user deselected us, do not deactivate the model since we still need to receive updates.
    if (!ArrayUtil.contains(this, FileEditorManager.getInstance(myProject).getSelectedEditors())) {
      myDesignerEditor.getComponent().deactivate();
    }
  }

  @NotNull
  @Override
  public Editor getEditor() {
    return myEditor.getEditor();
  }

  @Nullable
  @Override
  public VirtualFile getFile() {
    return myEditor.getFile();
  }

  @Override
  public boolean canNavigateTo(@NotNull Navigatable navigatable) {
    return myEditor.canNavigateTo(navigatable);
  }

  @Override
  public void navigateTo(@NotNull Navigatable navigatable) {
    myEditor.navigateTo(navigatable);
  }

  private class MyToolBarAction extends ToggleAction {
    @NotNull private final ToggleAction myDelegate;
    @NotNull private final DesignSurface.State mySurfaceState;
    @Nullable private MyToolBarAction myRightAction;
    @Nullable private MyToolBarAction myLeftAction;

    MyToolBarAction(@NotNull String name, @NotNull Icon icon, @NotNull ToggleAction delegate, @NotNull DesignSurface.State surfaceState) {
      super(name, name, icon);
      myDelegate = delegate;
      mySurfaceState = surfaceState;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myDelegate.isSelected(e);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      setSelected(e, state, true);
    }

    public void setSelected(@NotNull AnActionEvent e, boolean state, boolean userExplicitlySelected) {
      myDelegate.setSelected(e, state);
      DesignSurface surface = myDesignerEditor.getComponent().getSurface();
      surface.setState(mySurfaceState);
      if (userExplicitlySelected) {
        // We only want to track actions when users explicitly trigger them, i.e. when they click on the action to change the mode. An
        // example of indirectly changing the mode is triggering "Go to XML" when in design-only mode, as we change the mode to text-only.
        surface.getAnalyticsManager().trackSelectEditorMode();
      }
      getComponent().requestFocus();
      setModeProperty(mySurfaceState);
    }

    /**
     * Sets the action that's on the right of the current action. Also sets the current action as its left action to keep things consistent.
     */
    public void setRightAction(@NotNull MyToolBarAction action) {
      myRightAction = action;
      action.myLeftAction = this;
    }
  }

  /**
   * Persist the mode in order to restore it next time we open the editor.
   */
  private void setModeProperty(@NotNull DesignSurface.State state) {
    String propertyName = getModePropertyName();
    if (propertyName != null) {
      myPropertiesComponent.setValue(propertyName, state.name());
    }
  }

  @Nullable
  private String getModePropertyName() {
    VirtualFile file = getFile();
    if (file == null) {
      return null;
    }
    return String.format("%s_%s", SPLIT_MODE_PROPERTY_PREFIX, file.getPath());
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    // Override getState to make sure getState(FileEditorStateLevel.UNDO) works properly, otherwise we'd be defaulting to the implementation
    // of TextEditorWithPreview#getState, which returns a new instance every time, causing issues in undoing the editor's state because it
    // will return a different state even if nothing relevant has changed. Consequently, we need to implement setState below to make sure we
    // restore the selected action when reopening this editor, which was previously taken care by TextEditorWithPreview#setState, but with a
    // logic too tied to its getState implementation.
    return myEditor.getState(level);
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
    // Restore the surface mode persisted.
    final String propertyName = getModePropertyName();
    String propertyValue = null;
    if (propertyName != null) {
      propertyValue = myPropertiesComponent.getValue(propertyName);
    }

    if (propertyValue == null) {
      return;
    }
    // Select the action saved if the mode saved is different than the current one.
    DesignSurface.State surfaceState = DesignSurface.State.valueOf(propertyValue);
    if (surfaceState == myDesignerEditor.getComponent().getSurface().getState()) {
      return;
    }
    myActions.stream()
      .filter((action) -> action.mySurfaceState == surfaceState)
      .findFirst()
      .ifPresent(action -> selectAction(action, false));
  }

  private class CompoundBackgroundHighlighter implements BackgroundEditorHighlighter {
    @NotNull
    @Override
    public HighlightingPass[] createPassesForEditor() {
      HighlightingPass[] designEditorPasses = myDesignerEditor.getBackgroundHighlighter().createPassesForEditor();
      BackgroundEditorHighlighter textEditorHighlighter = myEditor.getBackgroundHighlighter();
      HighlightingPass[] textEditorPasses =
        textEditorHighlighter == null ? HighlightingPass.EMPTY_ARRAY : textEditorHighlighter.createPassesForEditor();
      return Stream.concat(Arrays.stream(designEditorPasses), Arrays.stream(textEditorPasses)).toArray(HighlightingPass[]::new);
    }

    @NotNull
    @Override
    public HighlightingPass[] createPassesForVisibleArea() {
      // BackgroundEditorHighlighter#createPassesForVisibleArea is deprecated and not used, so we can safely return an empty array here.
      return HighlightingPass.EMPTY_ARRAY;
    }
  }
}
