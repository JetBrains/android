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

import com.android.tools.idea.common.surface.DesignSurface;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.codeHighlighting.HighlightingPass;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.TextEditorWithPreview;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.util.ArrayUtil;
import java.util.Arrays;
import java.util.stream.Stream;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/**
 * {@link TextEditorWithPreview} in which {@link #myPreview} is a {@link DesignerEditor} and {@link #myEditor} contains the corresponding
 * XML file being displayed in the preview.
 */
public class SplitEditor extends TextEditorWithPreview implements TextEditor {

  @NotNull
  private final Project myProject;

  @NotNull
  private final DesignerEditor myDesignerEditor;

  @NotNull
  private final BackgroundEditorHighlighter myBackgroundEditorHighlighter = new CompoundBackgroundHighlighter();

  private final MyToolBarAction myTextViewAction =
    new MyToolBarAction("Text", AllIcons.General.LayoutEditorOnly, super.getShowEditorAction(), DesignSurface.State.DEACTIVATED);

  private final MyToolBarAction myDesignViewAction =
    new MyToolBarAction("Design", AllIcons.General.LayoutPreviewOnly, super.getShowPreviewAction(), DesignSurface.State.FULL);

  private final MyToolBarAction mySplitViewAction =
    new MyToolBarAction("Split", AllIcons.General.LayoutEditorPreview, super.getShowEditorAndPreviewAction(), DesignSurface.State.SPLIT);

  public SplitEditor(@NotNull TextEditor textEditor,
                     @NotNull DesignerEditor designerEditor,
                     @NotNull String editorName,
                     @NotNull Project project) {
    super(textEditor, designerEditor, editorName);
    myProject = project;
    myDesignerEditor = designerEditor;
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
      // TODO(b/136174865): hide editor tool windows (e.g. palette) depending on the mode selected.
    }
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
