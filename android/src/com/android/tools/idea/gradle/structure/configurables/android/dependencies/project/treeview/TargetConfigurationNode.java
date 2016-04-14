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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.project.treeview;

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode;
import com.google.common.base.Joiner;
import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.ui.HtmlListCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;

public class TargetConfigurationNode extends AbstractPsNode implements CellAppearanceEx {
  private final List<String> myTypes;

  public TargetConfigurationNode(Configuration configuration) {
    myName = configuration.getName();
    setIcon(configuration.getIcon());
    myTypes = configuration.getTypes();
  }

  @Override
  public SimpleNode[] getChildren() {
    return NO_CHILDREN;
  }

  @Override
  @NotNull
  public String getText() {
    return myName;
  }

  @Override
  public void customize(@NotNull HtmlListCellRenderer renderer) {

  }

  @Override
  public void customize(@NotNull SimpleColoredComponent component) {
    component.append(" ");
    String text;
    if (myTypes.isEmpty()) {
      text = "";
    }
    else if (myTypes.size() == 1) {
      text = myTypes.get(0);
    }
    else {
      text = Joiner.on(", ").join(myTypes);
    }
    component.append("(" + text + ")", GRAY_ATTRIBUTES);
  }
}
