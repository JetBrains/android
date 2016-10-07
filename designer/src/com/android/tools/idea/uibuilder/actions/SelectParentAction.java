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

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * Action which selects the parent, if possible
 */
public class SelectParentAction extends AnAction {
  private final DesignSurface mySurface;

  public SelectParentAction(@NotNull DesignSurface surface) {
    super("Select Parent", "Select Parent", null);
    mySurface = surface;
  }

  @Override
  public void update(AnActionEvent e) {
    boolean enabled;
    ScreenView screenView = mySurface.getCurrentScreenView();
    if (screenView != null) {
      List<NlComponent> selection = screenView.getSelectionModel().getSelection();
      enabled = selection.size() == 1 && !selection.get(0).isRoot();
    }
    else {
      enabled = false;
    }
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ScreenView screenView = mySurface.getCurrentScreenView();
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
      mySurface.repaint();
    }
  }
}
