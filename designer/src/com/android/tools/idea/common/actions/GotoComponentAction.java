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

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.SystemInfo;
import java.awt.event.MouseEvent;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;

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
  public void actionPerformed(@NotNull AnActionEvent e) {
    InputEvent inputEvent = e.getInputEvent();
    if (inputEvent instanceof MouseEvent) {
      if (mySurface.getInteractionManager().interceptPanInteraction((MouseEvent)inputEvent) || AdtUiUtils.isActionKeyDown(inputEvent)) {
        // We don't want to perform navigation while holding some modifiers on mouse event.
        return;
      }
    }
    SelectionModel selectionModel = mySurface.getSelectionModel();
    NlComponent primary = selectionModel.getPrimary();
    NlModel model = mySurface.getModel();

    NlComponent componentToNavigate = null;
    if (primary != null) {
      componentToNavigate = primary;
    } else if (model != null) {
      ImmutableList<NlComponent> components = model.getComponents();
      if (!components.isEmpty()) {
        componentToNavigate = components.get(0);
      }
    }

    mySurface.deactivate();
    if (componentToNavigate == null || !NlComponentHelperKt.navigateTo(componentToNavigate)) {
      switchTab(model);
    }
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

