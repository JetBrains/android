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
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Select all siblings of the selected components
 */
public class SelectSiblingsAction extends AnAction {
  private final SelectionModel myModel;

  public SelectSiblingsAction(@NotNull SelectionModel model) {
    super("Select Siblings", "Select Siblings", null);
    myModel = model;
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(!myModel.isEmpty());
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    if (myModel.isEmpty()) {
      return;
    }
    Map<NlComponent, List<NlComponent>> map = NlModel.groupSiblings(myModel.getSelection());
    List<NlComponent> allSiblings = Lists.newArrayList();
    for (NlComponent parent : map.keySet()) {
      Iterables.addAll(allSiblings, parent.getChildren());
    }
    myModel.setSelection(allSiblings);
  }
}
