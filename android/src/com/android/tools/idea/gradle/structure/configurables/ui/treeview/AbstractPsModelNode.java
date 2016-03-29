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

import com.android.tools.idea.gradle.structure.model.PsModel;
import com.google.common.collect.Lists;
import com.intellij.ide.projectView.PresentationData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES;

public abstract class AbstractPsModelNode<T extends PsModel> extends AbstractPsNode {
  @NotNull private final List<T> myModels;

  protected AbstractPsModelNode(@NotNull AbstractPsModelNode<?> parent, @NotNull T...models) {
    super(parent);
    myModels = Lists.newArrayList(models);
    updateNameAndIcon();
  }

  protected AbstractPsModelNode(@NotNull T...models) {
    myModels = Lists.newArrayList(models);
    updateNameAndIcon();
  }

  protected AbstractPsModelNode(@NotNull AbstractPsModelNode<?> parent, @NotNull List<T> models) {
    super(parent);
    myModels = models;
    updateNameAndIcon();
  }

  protected AbstractPsModelNode(@NotNull List<T> models) {
    myModels = models;
    updateNameAndIcon();
  }

  protected void updateNameAndIcon() {
    int modelCount = myModels.size();
    if (modelCount != 0) {
      T first = myModels.get(0);
      if (modelCount == 1) {
        myName = nameOf(first);
      }
      else {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < modelCount; i++) {
          T model = myModels.get(i);
          buffer.append(nameOf(model));
          if (i < modelCount - 1) {
            buffer.append(", ");
          }
        }
        myName = buffer.toString();
      }
      setIcon(first.getIcon());
    }
  }

  @NotNull
  protected String nameOf(T model) {
    return model.getName();
  }

  @NotNull
  public List<T> getModels() {
    return myModels;
  }

  public boolean matches(@NotNull PsModel model) {
    for (T m : myModels) {
      if (m.equals(model)) {
        return true;
      }
    }

    return false;
  }

  @Override
  protected void doUpdate() {
    PresentationData presentation = getTemplatePresentation();
    presentation.clearText();
    presentation.addText(myName, REGULAR_ATTRIBUTES);
  }
}
