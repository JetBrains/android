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

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.psi.PsiElement;
import com.intellij.util.PsiNavigateUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Action which navigates to the primary selected XML element
 */
public class GotoComponentAction extends DumbAwareAction {
  private final DesignSurface mySurface;

  public GotoComponentAction(DesignSurface surface) {
    super("Go to XML");
    mySurface = surface;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    SelectionModel selectionModel = mySurface.getSelectionModel();
    NlComponent primary = selectionModel.getPrimary();
    PsiElement psiElement = null;
    NlModel model = mySurface.getModel();

    // Use the selected component or fallback to the root if none is selected
    if (primary != null) {
      psiElement = primary.getTag();
    }
    else if (model != null) {
      ImmutableList<NlComponent> components = model.getComponents();
      psiElement = !components.isEmpty() ? components.get(0).getTag() : null;
    }

    mySurface.deactivate();
    if (psiElement != null && psiElement.isValid()) {
      switchTabAndNavigate(psiElement);
    }
    else if (model != null) {
      switchTab(model);
    }
  }

  /**
   * Navigate to the given {@link PsiElement}
   */
  private static void switchTabAndNavigate(@NotNull PsiElement psiElement) {
    PsiNavigateUtil.navigate(psiElement);
  }

  /**
   * Just switch to the text tab without navigating to a particular node.
   *
   * This is to ensure that we can switch tab even if the model is empty
   */
  private void switchTab(@NotNull NlModel model) {
    // If the xml file is empty, just open the text editor
    FileEditorManager editorManager = FileEditorManager.getInstance(mySurface.getProject());
    editorManager.openTextEditor(
      new OpenFileDescriptor(model.getProject(), model.getVirtualFile(), 0), true);
  }
}

