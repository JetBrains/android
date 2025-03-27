/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.actions;

import static com.android.tools.idea.actions.DesignerDataKeys.DESIGN_SURFACE;

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.idea.common.editor.DesignToolsSplitEditor;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.PsiNavigateUtil;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Action which navigates to the primary selected XML element
 */
public class GotoComponentAction extends DumbAwareAction {
  private final Function<Project, FileEditor> mySelectedEditorProvider;

  /**
   * Creates a new {@link GotoComponentAction}.
   * @param selectedEditorProvider Returns the selected editor for the given project.
   */
  @VisibleForTesting
  GotoComponentAction(@NotNull Function<Project, FileEditor> selectedEditorProvider) {
    super("Go to XML");
    mySelectedEditorProvider = selectedEditorProvider;
  }

  public GotoComponentAction() {
    this((project) -> FileEditorManager.getInstance(project).getSelectedEditor());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DesignSurface<?> surface = e.getData(DESIGN_SURFACE);
    if (surface == null) {
      return;
    }
    InputEvent inputEvent = e.getInputEvent();
    if (inputEvent instanceof MouseEvent) {
      if (surface.getGuiInputHandler().interceptPanInteraction((MouseEvent)inputEvent) || AdtUiUtils.isActionKeyDown(inputEvent)) {
        // We don't want to perform navigation while holding some modifiers on mouse event.
        return;
      }
    }

    FileEditor selectedEditor = mySelectedEditorProvider.apply(surface.getProject());
    if (selectedEditor instanceof DesignToolsSplitEditor splitEditor) {
      if (splitEditor.isDesignMode()) {
        // If we're in design mode, we want to change the split editor mode to split mode before navigating to the element.
        splitEditor.selectSplitMode(false);
      }
    }

    SelectionModel selectionModel = surface.getSelectionModel();
    NlComponent primary = selectionModel.getPrimary();
    NlModel model = Iterables.getFirst(surface.getModels(), null);

    NlComponent componentToNavigate = null;
    if (primary != null) {
      componentToNavigate = primary;
    } else if (!selectionModel.getSelection().isEmpty()) {
      componentToNavigate = selectionModel.getSelection().get(0);
    } else if (model != null) {
      ImmutableList<NlComponent> components = model.getTreeReader().getComponents();
      if (!components.isEmpty()) {
        componentToNavigate = components.get(0);
      }
    }

    if (model != null && !navigateToXml(componentToNavigate)) {
      switchTab(surface, model);
    }
  }

  private static boolean navigateToXml(@Nullable NlComponent component) {
    if (component == null) {
      return false;
    }
    XmlTag tag = component.getTag();
    if (tag == null) {
      return false;
    }
    PsiNavigateUtil.navigate(tag);
    return true;
  }

  /**
   * Just switch to the text tab without navigating to a particular node.
   *
   * This is to ensure that we can switch tab even if the model is empty
   */
  private void switchTab(@NotNull DesignSurface<?> surface, @NotNull NlModel model) {
    // If the xml file is empty, just open the text editor
    FileEditorManager editorManager = FileEditorManager.getInstance(surface.getProject());
    editorManager.openTextEditor(
      new OpenFileDescriptor(model.getProject(), model.getVirtualFile(), 0), true);
  }
}

