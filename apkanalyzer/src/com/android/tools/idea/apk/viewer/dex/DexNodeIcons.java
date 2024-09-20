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
package com.android.tools.idea.apk.viewer.dex;

import com.android.tools.apk.analyzer.dex.tree.*;
import com.intellij.icons.AllIcons;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;

import javax.swing.*;

public class DexNodeIcons {
  @SuppressWarnings("UnstableApiUsage")
  public static Icon forNode(DexElementNode node) {
    IconManager iconManager = IconManager.getInstance();
    if (node instanceof DexClassNode) {
      return iconManager.getPlatformIcon(PlatformIcons.Class);
    }
    else if (node instanceof DexFieldNode) {
      return iconManager.getPlatformIcon(PlatformIcons.Field);
    }
    else if (node instanceof DexMethodNode) {
      return iconManager.getPlatformIcon(node.isDefined() ? PlatformIcons.Method : PlatformIcons.MethodReference);
    }
    else if (node instanceof DexPackageNode) {
      return AllIcons.Nodes.Package;
    }
    else if (node instanceof DexReferencesNode) {
      return AllIcons.Nodes.Folder;
    }
    else {
      throw new IllegalArgumentException("No icon defined for this node type.");
    }
  }
}
