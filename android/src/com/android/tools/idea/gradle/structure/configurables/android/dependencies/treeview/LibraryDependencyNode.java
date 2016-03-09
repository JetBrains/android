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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.PsdAndroidDependencyModelComparator;
import com.android.tools.idea.gradle.structure.configurables.ui.PsdUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsdNode;
import com.android.tools.idea.gradle.structure.model.PsdArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsdModel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdLibraryDependencyModel;
import com.google.common.collect.Lists;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

class LibraryDependencyNode extends AbstractDependencyNode<PsdLibraryDependencyModel> {
  @NotNull private final List<SimpleNode> myChildren = Lists.newArrayList();

  LibraryDependencyNode(@NotNull AbstractPsdNode parent, @NotNull PsdLibraryDependencyModel model) {
    super(parent, model);
    myName = getText(model);

    List<PsdAndroidDependencyModel> transitiveDependencies = Lists.newArrayList(model.getTransitiveDependencies());
    Collections.sort(transitiveDependencies, PsdAndroidDependencyModelComparator.INSTANCE);

    for (PsdAndroidDependencyModel transitive : model.getTransitiveDependencies()) {
      if (transitive instanceof PsdLibraryDependencyModel) {
        PsdLibraryDependencyModel transitiveLibrary = (PsdLibraryDependencyModel)transitive;
        LibraryDependencyNode child = new LibraryDependencyNode(this, transitiveLibrary);
        myChildren.add(child);
      }
    }
  }

  @NotNull
  private static String getText(@NotNull PsdLibraryDependencyModel model) {
    PsdArtifactDependencySpec resolvedSpec = model.getResolvedSpec();
    if (model.hasPromotedVersion()) {
      PsdArtifactDependencySpec declaredSpec = model.getDeclaredSpec();
      assert declaredSpec != null;
      String version = declaredSpec.version + "→" + resolvedSpec.version;
      return getTextForSpec(declaredSpec.name, version, declaredSpec.group);
    }
    return resolvedSpec.getDisplayText();
  }

  @NotNull
  private static String getTextForSpec(@NotNull String name, @NotNull String version, @Nullable String group) {
    boolean showGroupId = PsdUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID;
    StringBuilder text = new StringBuilder();
    if (showGroupId && isNotEmpty(group)) {
      text.append(group).append(GRADLE_PATH_SEPARATOR);
    }
    text.append(name).append(GRADLE_PATH_SEPARATOR).append(version);
    return text.toString();
  }

  @Override
  public SimpleNode[] getChildren() {
    return myChildren.toArray(new SimpleNode[myChildren.size()]);
  }

  @Override
  public boolean matches(@NotNull PsdModel model) {
    if (model instanceof PsdLibraryDependencyModel) {
      PsdLibraryDependencyModel other = (PsdLibraryDependencyModel)model;

      List<PsdLibraryDependencyModel> models = getModels();
      int modelCount = models.size();
      if (modelCount == 1) {
        PsdLibraryDependencyModel myModel = models.get(0);
        return myModel.getResolvedSpec().equals(other.getResolvedSpec());
      }
    }
    return false;
  }
}
