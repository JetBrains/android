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

import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsModel;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryDependency;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Lists;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class LibraryDependencyNode extends AbstractDependencyNode<PsLibraryDependency> {
  @NotNull private final List<AbstractDependencyNode> myChildren = Lists.newArrayList();

  public LibraryDependencyNode(@NotNull AbstractPsModelNode parent, @NotNull PsLibraryDependency dependency) {
    super(parent, dependency);
    setUp(dependency);
  }

  public LibraryDependencyNode(@NotNull AbstractPsModelNode parent, @NotNull List<PsLibraryDependency> dependencies) {
    super(parent, dependencies);
    assert !dependencies.isEmpty();
    setUp(dependencies.get(0));
  }

  private void setUp(@NotNull PsLibraryDependency dependency) {
    myName = getText(dependency);

    ImmutableCollection<PsAndroidDependency> transitiveDependencies = dependency.getTransitiveDependencies();

    for (PsAndroidDependency transitive : transitiveDependencies) {
      if (transitive instanceof PsLibraryDependency) {
        PsLibraryDependency transitiveLibrary = (PsLibraryDependency)transitive;
        LibraryDependencyNode child = new LibraryDependencyNode(this, transitiveLibrary);
        myChildren.add(child);
      }
    }

    Collections.sort(myChildren, DependencyNodeComparator.INSTANCE);
  }

  @NotNull
  private static String getText(@NotNull PsLibraryDependency dependency) {
    PsArtifactDependencySpec resolvedSpec = dependency.getResolvedSpec();
    if (dependency.hasPromotedVersion()) {
      PsArtifactDependencySpec declaredSpec = dependency.getDeclaredSpec();
      assert declaredSpec != null;
      String version = declaredSpec.version + "→" + resolvedSpec.version;
      return getTextForSpec(declaredSpec.name, version, declaredSpec.group);
    }
    return resolvedSpec.getDisplayText();
  }

  @NotNull
  private static String getTextForSpec(@NotNull String name, @NotNull String version, @Nullable String group) {
    boolean showGroupId = PsUISettings.getInstance().DECLARED_DEPENDENCIES_SHOW_GROUP_ID;
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
  public boolean matches(@NotNull PsModel model) {
    if (model instanceof PsLibraryDependency) {
      PsLibraryDependency other = (PsLibraryDependency)model;

      List<PsLibraryDependency> models = getModels();
      for (PsLibraryDependency dependency : models) {
        if (dependency.getResolvedSpec().equals(other.getResolvedSpec())) {
          return true;
        }
      }
    }
    return false;
  }
}
