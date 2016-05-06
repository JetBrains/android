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
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

import java.util.List;

/**
 * Select all components of the same type
 */
public class SelectSameTypeAction extends AnAction {
  private final SelectionModel myModel;

  public SelectSameTypeAction(SelectionModel model) {
    super("Select Same Type", "Select Same Type", null);
    myModel = model;
  }

  private static void addSameType(NlComponent root, List<NlComponent> all, List<NlComponent> selection) {
    for (NlComponent selected : selection) {
      if (root.getTagName().equals(selected.getTagName())) {
        all.add(root);
      }
    }

    for (NlComponent child : root.getChildren()) {
      addSameType(child, all, selection);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(!myModel.isEmpty());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    List<NlComponent> selection = myModel.getSelection();
    List<NlComponent> all = Lists.newArrayList();
    NlComponent first = selection.get(0);
    for (NlComponent root : first.getModel().getComponents()) {
      addSameType(root, all, selection);
    }
    if (selection.size() == 1) {
      // Leave the old selected component as the primary
      myModel.setSelection(all, first);
    }
    else {
      myModel.setSelection(all);
    }
  }
}
