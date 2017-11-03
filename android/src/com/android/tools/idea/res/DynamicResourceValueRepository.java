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
package com.android.tools.idea.res;

import com.android.annotations.NonNull;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.ClassField;
import com.android.builder.model.Variant;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.res2.ResourceNamespaces;
import com.android.ide.common.res2.ResourceTable;
import com.android.resources.ResourceType;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncListener;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.variant.view.BuildVariantView;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Resource repository which contains dynamically registered resource items from the model.
 *
 * <p>The Gradle plugin allows resources to be created on the fly (e.g. you can create a resource called build_time of type string with a
 * value set to a Groovy variable computed at build time). These dynamically created resources are computed at Gradle sync time and provided
 * via the Gradle model.
 *
 * <p>Users expect the resources to "exist" too, when using code completion. The {@link DynamicResourceValueRepository} makes this happen:
 * the repository contents are fetched from the Gradle model rather than by analyzing XML files as is done by the other resource
 * repositories.
 */
public class DynamicResourceValueRepository extends LocalResourceRepository
  implements GradleSyncListener, BuildVariantView.BuildVariantSelectionChangeListener {
  private final AndroidFacet myFacet;
  private final ResourceTable myFullTable = new ResourceTable();
  private final String myNamespace;

  private DynamicResourceValueRepository(@NotNull AndroidFacet facet, @Nullable String namespace) {
    super("Gradle Dynamic");
    myFacet = facet;
    myNamespace = namespace;
    assert facet.requiresAndroidModel();
    Module module = facet.getModule();
    MessageBusConnection parent = GradleSyncState.subscribe(module.getProject(), this, module);
    Disposer.register(parent, this);
    BuildVariantView.getInstance(myFacet.getModule().getProject()).addListener(this);
  }

  @Override
  public void dispose() {
    super.dispose();

    Project project = myFacet.getModule().getProject();
    if (!project.isDisposed()) {
      BuildVariantView.getInstance(project).removeListener(this);
    }
  }

  @NotNull
  // TODO: namespaces
  public static DynamicResourceValueRepository create(@NotNull AndroidFacet facet) {
    return new DynamicResourceValueRepository(facet, null);
  }

  @NotNull
  @VisibleForTesting
  public static DynamicResourceValueRepository createForTest(@NotNull AndroidFacet facet,
                                                             @Nullable String namespace,
                                                             @NotNull Map<String, ClassField> values) {
    DynamicResourceValueRepository repository = new DynamicResourceValueRepository(facet, namespace);
    repository.addValues(values);

    return repository;
  }

  @Override
  @NonNull
  protected ResourceTable getFullTable() {
    if (myFullTable.isEmpty()) {
      // TODO: b/23032391
      AndroidModuleModel androidModel = AndroidModuleModel.get(myFacet);
      if (androidModel == null) {
        return myFullTable;
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

    return myFullTable;
  }

  private void notifyGradleSynced() {
    myFullTable.clear(); // compute lazily in getMap
    super.invalidateParentCaches();
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
      ListMultimap<String, ResourceItem> map = myFullTable.get(myNamespace, type);
      if (map == null) {
        map = ArrayListMultimap.create();
        myFullTable.put(myNamespace, type, map);
      }
      else if (map.containsKey(name)) {
        // Masked by higher priority source provider
        continue;
      }
      ResourceItem item = new DynamicResourceValueItem(myNamespace, type, field);
      map.put(name, item);
    }
  }

  @Override
  @Nullable
  protected ListMultimap<String, ResourceItem> getMap(@Nullable String namespace, @NotNull ResourceType type, boolean create) {
    if (!ResourceNamespaces.isSameNamespace(namespace, myNamespace)) {
      return create ? ArrayListMultimap.create() : null;
    }

    if (myFullTable.isEmpty()) {
      // Force lazy initialization
      getFullTable();
    }
    ListMultimap<String, ResourceItem> multimap = myFullTable.get(namespace, type);
    if (multimap == null && create) {
      multimap = ArrayListMultimap.create();
      myFullTable.put(namespace, type, multimap);
    }
    return multimap;
  }

  @Override
  @NotNull
  public Set<String> getNamespaces() {
    return Collections.singleton(myNamespace);
  }

  // ---- Implements GradleSyncListener ----

  @Override
  public void syncStarted(@NotNull Project project) {
  }

  @Override
  public void setupStarted(@NotNull Project project) {
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
  public void selectionChanged() {
    notifyGradleSynced();
  }

  @NotNull
  @Override
  protected Set<VirtualFile> computeResourceDirs() {
    return ImmutableSet.of();
  }
}
