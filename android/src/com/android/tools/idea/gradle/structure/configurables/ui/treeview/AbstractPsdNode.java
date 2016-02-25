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
package com.android.tools.idea.gradle.structure.configurables.ui.treeview;

import com.android.tools.idea.gradle.structure.model.PsdModel;
import com.android.tools.idea.gradle.structure.model.PsdProblem;
import com.google.common.collect.Lists;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.List;

import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.STYLE_WAVED;

public abstract class AbstractPsdNode<T extends PsdModel> extends SimpleNode {
  @NotNull private final List<T> myModels;

  private boolean myAutoExpandNode;

  protected AbstractPsdNode(@NotNull AbstractPsdNode<?> parent, @NotNull T...models) {
    super(parent);
    myModels = Lists.newArrayList(models);
    updateIcon();
  }

  protected AbstractPsdNode(@NotNull T...models) {
    myModels = Lists.newArrayList(models);
    updateIcon();
  }

  protected AbstractPsdNode(@NotNull AbstractPsdNode<?> parent, @NotNull List<T> models) {
    super(parent);
    myModels = models;
    updateIcon();
  }

  protected AbstractPsdNode(@NotNull List<T> models) {
    myModels = models;
    updateIcon();
  }

  private void updateIcon() {
    if (!myModels.isEmpty()) {
      setIcon(myModels.get(0).getIcon());
    }
  }

  @NotNull
  public List<T> getModels() {
    return myModels;
  }

  @Override
  public boolean isAutoExpandNode() {
    return myAutoExpandNode;
  }

  public void setAutoExpandNode(boolean autoExpandNode) {
    myAutoExpandNode = autoExpandNode;
  }

  public boolean matches(@NotNull PsdModel model) {
    int modelCount = myModels.size();
    if (modelCount == 1) {
      return myModels.get(0).equals(model);
    }
    return false;
  }

  @Override
  protected void doUpdate() {
    PresentationData presentation = getTemplatePresentation();
    presentation.clearText();

    SimpleTextAttributes textAttributes = REGULAR_ATTRIBUTES;
    if (myModels.size() == 1) {
      T model = myModels.get(0);
      PsdProblem problem = model.getProblem();
      if (problem != null) {
        Color waveColor = problem.getSeverity().getColor();
        textAttributes = textAttributes.derive(STYLE_WAVED, null, null, waveColor);
        presentation.setTooltip(problem.getText());
      }
    }

    presentation.addText(myName, textAttributes);
  }
}
