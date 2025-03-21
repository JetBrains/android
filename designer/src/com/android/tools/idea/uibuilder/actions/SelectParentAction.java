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
package com.android.tools.idea.uibuilder.actions;

import static com.android.tools.idea.actions.DesignerDataKeys.DESIGN_SURFACE;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Action which selects the parent, if possible
 */
public class SelectParentAction extends AnAction {
  public SelectParentAction() {
    super("Select Parent", "Select Parent", null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DesignSurface<?> surface = e.getData(DESIGN_SURFACE);
    if (surface == null) {
      return;
    }
    boolean enabled;
    if (surface.getGuiInputHandler().isInteractionInProgress()) {
      // Interaction should consume escape event first
      enabled = false;
    }
    else {
      SceneView screenView = surface.getFocusedSceneView();
      if (screenView != null) {
        List<NlComponent> selection = screenView.getSelectionModel().getSelection();
        enabled = selection.size() == 1 && !selection.get(0).isRoot();
      }
      else {
        enabled = false;
      }
    }
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DesignSurface<?> surface = e.getData(DESIGN_SURFACE);
    if (surface == null) {
      return;
    }
    SceneView screenView = surface.getFocusedSceneView();
    if (screenView != null) {
      SelectionModel selectionModel = screenView.getSelectionModel();
      List<NlComponent> selection = selectionModel.getSelection();
      if (selection.size() == 1) {
        NlComponent first = selection.get(0);
        NlComponent parent = first.getParent();

        if (parent != null) {
          selectionModel.setSelection(Collections.singletonList(parent));
        }
      }
      surface.repaint();
    }
  }
}
