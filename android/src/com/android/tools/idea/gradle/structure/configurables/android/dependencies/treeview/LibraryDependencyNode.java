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

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.structure.configurables.ui.dependencies.PsDependencyComparator;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode;
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsDependency;
import com.android.tools.idea.gradle.structure.model.PsModel;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependencyCollection;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryAndroidDependency;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.Lists;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class LibraryDependencyNode extends AbstractDependencyNode<PsLibraryAndroidDependency> {
  @NotNull private final List<AbstractDependencyNode> myChildren = Lists.newArrayList();
  @NotNull private final DependencyNodeComparator myDependencyNodeComparator;

  public LibraryDependencyNode(
    @NotNull AbstractPsNode parent,
    @Nullable PsAndroidDependencyCollection collection,
    @NotNull PsLibraryAndroidDependency dependency) {
    super(parent, dependency);
    myDependencyNodeComparator = new DependencyNodeComparator(new PsDependencyComparator(getUiSettings()));
    setUp(dependency, collection);
  }

  public LibraryDependencyNode(@NotNull AbstractPsNode parent,
                               @Nullable PsAndroidDependencyCollection collection,
                               @NotNull List<PsLibraryAndroidDependency> dependencies) {
    super(parent, dependencies);
    myDependencyNodeComparator = new DependencyNodeComparator(new PsDependencyComparator(getUiSettings()));
    setUp(dependencies.get(0), collection);
  }

  private void setUp(@NotNull PsLibraryAndroidDependency dependency, @Nullable PsAndroidDependencyCollection collection) {
    myName = getText(dependency);
    // TODO(b/74380202): Setup children from Pom dependencies without a PsAndroidDependencyCollection.
    if (collection != null) {
      ImmutableCollection<PsLibraryAndroidDependency> transitiveDependencies = dependency.getTransitiveDependencies(collection);

      transitiveDependencies.stream().filter(transitive -> transitive instanceof PsLibraryAndroidDependency)
        .forEach(transitive -> {
          PsLibraryAndroidDependency transitiveLibrary = (PsLibraryAndroidDependency)transitive;
          LibraryDependencyNode child = new LibraryDependencyNode(this, collection, transitiveLibrary);
          myChildren.add(child);
        });

      Collections.sort(myChildren, myDependencyNodeComparator);
    }
  }

  @NotNull
  private String getText(@NotNull PsLibraryAndroidDependency dependency) {
    PsArtifactDependencySpec resolvedSpec = dependency.getSpec();
    if (dependency.hasPromotedVersion() && !(getParent() instanceof LibraryDependencyNode)) {
      // Show only "promoted" version for declared nodes.
      // TODO(b/74424544): Find a better representation for multiple versions here.
      String declaredSpecs =
        Joiner.on(",")
          .join(dependency.getParsedModels().stream().filter(m -> m instanceof ArtifactDependencyModel)
                  .map(m -> ((ArtifactDependencyModel)m).version().value())
                  .collect(Collectors.toList()));
      String version = declaredSpecs + "→" + resolvedSpec.getVersion();
      return getTextForSpec(resolvedSpec.getName(), version, resolvedSpec.getGroup(),
                            getUiSettings().DECLARED_DEPENDENCIES_SHOW_GROUP_ID);
    }
    return resolvedSpec.getDisplayText(getUiSettings());
  }

  @NotNull
  private static String getTextForSpec(@NotNull String name, @NotNull String version, @Nullable String group, boolean showGroupId) {
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
    if (model instanceof PsLibraryAndroidDependency) {
      PsLibraryAndroidDependency other = (PsLibraryAndroidDependency)model;

      List<PsLibraryAndroidDependency> models = getModels();
      for (PsLibraryAndroidDependency dependency : models) {
        if (dependency.getSpec().equals(other.getSpec())) {
          return true;
        }
      }
    }
    return false;
  }
}
