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
import com.android.tools.idea.gradle.structure.configurables.android.treeview.AbstractRootNode;
import com.android.tools.idea.gradle.structure.configurables.android.treeview.AbstractVariantNode;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.android.tools.idea.gradle.structure.model.android.PsdAndroidModuleModel;
import com.android.tools.idea.gradle.structure.model.android.PsdVariantModel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.*;
import com.intellij.util.containers.SortedList;
import org.jetbrains.annotations.NotNull;

import java.util.*;

class RootNode extends AbstractRootNode {
  RootNode(@NotNull PsdAndroidModuleModel moduleModel) {
    super(moduleModel);
  }

  @Override
  @NotNull
  protected List<? extends AbstractVariantNode> createVariantNodes(@NotNull Collection<PsdVariantModel> variantModels) {
    Map<String, PsdVariantModel> variantsByName = Maps.newHashMap();
    for (PsdVariantModel variantModel : variantModels) {
      variantsByName.put(variantModel.getName(), variantModel);
    }

    List<VariantNode> variantNodes = Lists.newArrayList();

    List<PsdAndroidDependencyModel> dependencies = getModels().get(0).getDependencies();
    Map<List<String>, List<PsdAndroidDependencyModel>> groups = groupVariants(dependencies);

    for (List<String> variantNames : groups.keySet()) {
      List<PsdVariantModel> groupVariants = Lists.newArrayList();
      for (String variantName : variantNames) {
        PsdVariantModel found = variantsByName.get(variantName);
        assert found != null;
        groupVariants.add(found);
      }
      VariantNode variantNode = new VariantNode(groupVariants);
      List<PsdAndroidDependencyModel> variantDependencies = groups.get(variantNames);
      variantNode.setChildren(variantDependencies);

      variantNodes.add(variantNode);
    }

    return variantNodes;
  }

  @VisibleForTesting
  @NotNull
  static Map<List<String>, List<PsdAndroidDependencyModel>> groupVariants(List<PsdAndroidDependencyModel> dependencies) {
    Comparator<PsdAndroidDependencyModel> comparator = new PsdAndroidDependencyModelComparator(true);

    Map<String, List<PsdAndroidDependencyModel>> dependenciesByVariant = Maps.newHashMap();
    for (PsdAndroidDependencyModel dependency : dependencies) {
      List<String> variants = dependency.getVariants();
      for (String variant : variants) {
        List<PsdAndroidDependencyModel> sorted = dependenciesByVariant.get(variant);
        if (sorted == null) {
          sorted = new SortedList<PsdAndroidDependencyModel>(comparator);
          dependenciesByVariant.put(variant, sorted);
        }
        sorted.add(dependency);
      }
    }

    List<List<String>> variantGroups = Lists.newArrayList();
    List<String> variants = Lists.newArrayList(dependenciesByVariant.keySet());

    List<String> currentGroup = Lists.newArrayList();
    while (!variants.isEmpty()) {
      String variant = variants.get(0);
      currentGroup.add(variant);

      if (variants.size() > 1) {
        List<PsdAndroidDependencyModel> variantDependencies = dependenciesByVariant.get(variant);
        for (int j = 1; j < variants.size(); j++) {
          String otherVariant = variants.get(j);
          List<PsdAndroidDependencyModel> otherVariantDependencies = dependenciesByVariant.get(otherVariant);
          if (variantDependencies.equals(otherVariantDependencies)) {
            currentGroup.add(otherVariant);
          }
        }
      }
      variantGroups.add(currentGroup);

      variants.removeAll(currentGroup);
      currentGroup = Lists.newArrayList();
    }

    Map<List<String>, List<PsdAndroidDependencyModel>> dependenciesByVariants = Maps.newHashMap();
    for (List<String> group : variantGroups) {
      String variant = group.get(0);
      dependenciesByVariants.put(group, dependenciesByVariant.get(variant));
    }
    return dependenciesByVariants;
  }
}
