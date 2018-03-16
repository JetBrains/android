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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview.AndroidArtifactNode;
import com.android.tools.idea.gradle.structure.configurables.ui.PsUISettings;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsResettableNode;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidArtifact;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsVariant;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class ResolvedDependenciesTreeRootNode extends AbstractPsResettableNode<PsAndroidModule> {

  ResolvedDependenciesTreeRootNode(@NotNull PsAndroidModule module, @NotNull PsUISettings uiSettings) {
    super(module, uiSettings);
  }

  @Override
  @NotNull
  protected List<? extends AbstractPsModelNode> createChildren() {
    Map<String, PsVariant> variantsByName = Maps.newHashMap();
    for (PsAndroidModule module : getModels()) {
      module.forEachVariant(variant -> variantsByName.put(variant.getName(), variant));
    }
    return createChildren(variantsByName);
  }

  @NotNull
  private List<? extends AndroidArtifactNode> createChildren(@NotNull Map<String, PsVariant> variantsByName) {
    List<AndroidArtifactNode> childrenNodes = Lists.newArrayList();
    List<String> variantNames = new ArrayList<>(variantsByName.keySet());
    Collections.sort(variantNames);

    for (String variantName : variantNames) {
      PsVariant variant = variantsByName.get(variantName);

      Map<String, PsAndroidArtifact> artifacts = Maps.newLinkedHashMap();
      variant.forEachArtifact(artifact -> artifacts.put(artifact.getResolvedName(), artifact));
      List<String> artifactNames = new ArrayList<>(artifacts.keySet());

      for (String artifactName : artifactNames) {
        PsAndroidArtifact artifact = variant.findArtifact(artifactName);
        assert artifact != null;
        AndroidArtifactNode artifactNode = new AndroidArtifactNode(this, artifact);
          childrenNodes.add(artifactNode);
      }
    }

    return childrenNodes;
  }
}
