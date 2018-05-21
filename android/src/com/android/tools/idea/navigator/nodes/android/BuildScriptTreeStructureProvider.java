/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.navigator.nodes.android;

import com.intellij.ide.projectView.TreeStructureProvider;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * This provider is a workaround for the ResourceBundleGrouper overriding .properties nodes in the Android project view.
 */
public class BuildScriptTreeStructureProvider implements TreeStructureProvider {
  @NotNull
  @Override
  public Collection<AbstractTreeNode> modify(@NotNull AbstractTreeNode parent,
                                             @NotNull Collection<AbstractTreeNode> children,
                                             ViewSettings settings) {
    // We only want to modify children under the "Gradle Scripts" node.
    if (!(parent instanceof AndroidBuildScriptsGroupNode)) {
      return children;
    }

    final List<AbstractTreeNode> results = new ArrayList<>();

    for (AbstractTreeNode child : children) {
      if (child instanceof AndroidBuildScriptNode) {
        results.add(child);
        continue;
      }

      final Object obj = child.getValue();
      if (!(obj instanceof PsiFile)) {
        results.add(child);
        continue;
      }

      final Project project = parent.getProject();
      if (project == null) {
        results.add(child);
        continue;
      }

      final PsiFile file = (PsiFile)obj;
      String qualifier;
      // These values are taken from and should be consistent with what is set in AndroidBuildScriptsGroupNode.
      if (FN_GRADLE_PROPERTIES.equals(file.getName())) {
        qualifier = "Project Properties";
      }
      else if (FN_LOCAL_PROPERTIES.equals(file.getName())) {
        qualifier = "SDK Location";
      }
      else if (FN_GRADLE_WRAPPER_PROPERTIES.equals(file.getName())) {
        qualifier = "Gradle Version";
      }
      else {
        qualifier = "Global Properties";
      }

      results.add(new AndroidBuildScriptNode(project, file, settings, qualifier));
    }

    return results;
  }
}
