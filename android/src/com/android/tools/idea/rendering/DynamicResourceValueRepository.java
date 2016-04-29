/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.annotations.NonNull;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.ClassField;
import com.android.builder.model.Variant;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.project.GradleSyncListener;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Resource repository which merges in dynamically registered resource items from the model
 */
public class DynamicResourceValueRepository extends LocalResourceRepository
  implements GradleSyncListener, BuildVariantView.BuildVariantSelectionChangeListener {
  private final AndroidFacet myFacet;
  private final Map<ResourceType, ListMultimap<String, ResourceItem>> mItems = Maps.newEnumMap(ResourceType.class);

  private DynamicResourceValueRepository(@NotNull AndroidFacet facet) {
    super("Gradle Dynamic");
    myFacet = facet;
    assert facet.requiresAndroidModel();
    facet.addListener(this);
    BuildVariantView.getInstance(myFacet.getModule().getProject()).addListener(this);
  }

  @NotNull
  public static DynamicResourceValueRepository create(@NotNull AndroidFacet facet) {
    return new DynamicResourceValueRepository(facet);
  }

  @NotNull
  @VisibleForTesting
  public static DynamicResourceValueRepository createForTest(@NotNull AndroidFacet facet, @NotNull Map<String, ClassField> values) {
    DynamicResourceValueRepository repository = new DynamicResourceValueRepository(facet);
    repository.addValues(values);

    return repository;
  }

  @Override
  @NonNull
  protected Map<ResourceType, ListMultimap<String, ResourceItem>> getMap() {
    if (mItems.isEmpty()) {
      // TODO: b/23032391
      AndroidGradleModel androidModel = AndroidGradleModel.get(myFacet);
      if (androidModel == null) {
        return mItems;
      }

      Variant selectedVariant = androidModel.getSelectedVariant();

      // Reverse overlay order because when processing lower order ones, we ignore keys already processed
      BuildTypeContainer buildType = androidModel.findBuildType(selectedVariant.getBuildType());
      if (buildType != null) {
        addValues(buildType.getBuildType().getResValues());
      }
      // flavors and default config:
      addValues(selectedVariant.getMergedFlavor().getResValues());
    }

    return mItems;
  }

  private void notifyGradleSynced() {
    mItems.clear(); // compute lazily in getMap
    super.invalidateItemCaches();
  }

  private void addValues(Map<String, ClassField> resValues) {
    for (Map.Entry<String, ClassField> entry : resValues.entrySet()) {
      ClassField field = entry.getValue();
      String name = field.getName();
      assert entry.getKey().equals(name) : entry.getKey() + " vs " + name;

      ResourceType type = ResourceType.getEnum(field.getType());
      if (type == null) {
        LOG.warn("Ignoring field " + name + "(" + field + "): unknown type " + field.getType());
        continue;
      }
      ListMultimap<String, ResourceItem> map = mItems.get(type);
      if (map == null) {
        map = ArrayListMultimap.create();
        mItems.put(type, map);
      }
      else if (map.containsKey(name)) {
        // Masked by higher priority source provider
        continue;
      }
      ResourceItem item = new DynamicResourceValueItem(type, field);
      map.put(name, item);
    }
  }

  @Override
  @Nullable
  protected ListMultimap<String, ResourceItem> getMap(ResourceType type, boolean create) {
    if (mItems.isEmpty()) {
      // Force lazy initialization
      getMap();
    }
    ListMultimap<String, ResourceItem> multimap = mItems.get(type);
    if (multimap == null && create) {
      multimap = ArrayListMultimap.create();
      mItems.put(type, multimap);
    }
    return multimap;
  }

  // ---- Implements GradleSyncListener ----

  @Override
  public void syncStarted(@NotNull Project project) {
  }

  @Override
  public void syncSucceeded(@NotNull Project project) {
    notifyGradleSynced();
  }

  @Override
  public void syncFailed(@NotNull Project project, @NotNull String errorMessage) {
  }

  @Override
  public void syncSkipped(@NotNull Project project) {
  }

  // ---- Implements BuildVariantView.BuildVariantSelectionChangeListener ----

  @Override
  public void buildVariantsConfigChanged() {
    notifyGradleSynced();
  }
}
