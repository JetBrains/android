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
package com.android.tools.idea.refactoring.modularize;

import com.android.resources.ResourceUrl;
import com.intellij.ui.ColoredTreeCellRenderer;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;

public class ResourceUrlTreeNode extends DependencyTreeNode {

  public ResourceUrlTreeNode(@NotNull ResourceUrl resourceUrl) {
    super(resourceUrl);
  }

  @Override
  public void render(@NotNull ColoredTreeCellRenderer renderer) {
    renderer.setIcon(AndroidIcons.AndroidFile);
    renderer.append((getUserObject()).toString(), getTextAttributes());
  }
}
