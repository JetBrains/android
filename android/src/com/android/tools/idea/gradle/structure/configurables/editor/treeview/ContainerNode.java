/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.editor.treeview;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static com.intellij.icons.AllIcons.Nodes.Folder;

public class ContainerNode extends GradleNode {
  public ContainerNode(@NotNull String name, @Nullable GradleNode parent) {
    super(parent);
    myName = name;
    myClosedIcon = Folder;
  }

  public static class Variants extends ContainerNode {
    public Variants(@NotNull AndroidProject androidProject, boolean autoExpandVariants) {
      this(androidProject, null, autoExpandVariants);
    }

    public Variants(@NotNull AndroidProject androidProject, @Nullable GradleNode parent, boolean autoExpandVariants) {
      super("Variants", parent);
      Collection<Variant> variants = androidProject.getVariants();
      int variantCount = variants.size();
      List<GradleNode> children = Lists.newArrayListWithExpectedSize(variantCount);
      for (Variant variant : variants) {
        VariantNode child = new VariantNode(variant, this);
        child.setAutoExpand(autoExpandVariants);
        children.add(child);
      }
      setChildren(children);
    }
  }
}
