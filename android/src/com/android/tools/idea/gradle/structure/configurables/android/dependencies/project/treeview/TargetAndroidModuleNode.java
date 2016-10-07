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

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.ui.HtmlListCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES;

public class TargetAndroidModuleNode extends AbstractPsModelNode<PsAndroidModule> implements CellAppearanceEx {
  @Nullable private final String myVersion;

  @NotNull private List<TargetConfigurationNode> myChildren = Collections.emptyList();

  TargetAndroidModuleNode(@NotNull AbstractPsNode parent, @NotNull PsAndroidModule module, @Nullable String version) {
    super(parent, module);
    myVersion = version;
    setAutoExpandNode(true);
  }

  @Override
  public SimpleNode[] getChildren() {
    return myChildren.toArray(new SimpleNode[myChildren.size()]);
  }

  void setChildren(@NotNull List<TargetConfigurationNode> children) {
    myChildren = children;
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
    if (!isEmpty(myVersion)) {
      component.append(" ");
      component.append("(" + myVersion + ")", GRAY_ATTRIBUTES);
    }
  }
}
